# be-ruoyi 实现底稿：taihu 业务模块（2026-10-13）

> 委派范围：ruoyi-backend/ + ruoyi-backend/sql/taihu.sql。未动 ruoyi-ui/、algo-service/，未引入新依赖，未 git commit。
> 契约依据：docs/contracts.md §2/§4。底座：RuoYi-Vue3 3.9.2（Spring Boot 4.1.0 / Spring Framework 7 / Java 17 target，本机 JDK 21 编译）。

## 1. 模块形态决策

新建 maven module `ruoyi-taihu`（包 com.ruoyi.taihu），仅依赖 ruoyi-common。理由：
- 若依惯例（quartz/generator 均独立模块）；
- 对 vendored 上游 diff 最小：仅根 pom `<modules>` + `dependencyManagement` 各加一段、ruoyi-admin pom 加一个依赖块，其余全是新增文件；
- 框架的 `@MapperScan("com.ruoyi.**.mapper")`、`classpath*:mapper/**/*Mapper.xml`、`typeAliasesPackage: com.ruoyi.**.domain` 通配已覆盖新模块，零配置接线。

## 2. 模块结构

```
ruoyi-backend/ruoyi-taihu/
├── pom.xml                                   仅依赖 ruoyi-common（无新第三方依赖）
└── src/main/
    ├── java/com/ruoyi/taihu/
    │   ├── config/TaihuAlgoConfig.java       RestTemplate（connect 5s / read 180s，读超时为 CP-SAT 30s 求解留余量）
    │   ├── domain/                           TzZone/TzBoat/TzStation/TzDispatchOrder/TzSalvageLog（BaseEntity 审计字段）
    │   ├── mapper/                           同名 5 Mapper 接口
    │   ├── service/                          ITz*Service + impl；TzAlgoProxyService（algo 代理，非接口风格）
    │   └── controller/                       Tz{Zone,Boat,Station,DispatchOrder,SalvageLog,Algo}Controller
    └── resources/mapper/taihu/               5 个 MyBatis XML（RuoYi 风格 resultMap + 动态 SQL）
```

## 3. 接口清单（挂 /taihu，BaseController + TableDataInfo/AjaxResult + @Log）

| 接口 | 说明 |
|---|---|
| GET/POST/PUT/DELETE /taihu/{zones,boats,stations,logs}… | 标准 CRUD，权限 taihu:{zone,boat,station,log}:{list,query,add,edit,remove} |
| GET /taihu/orders/list | 过滤 date（orderDate 精确）+ params.beginOrderDate/endOrderDate（区间）+ status + halfDay + boatId/zoneId；联表 boat_name/zone_name/station_name |
| PUT /taihu/orders/{id}/confirm | 0→1，权限 taihu:order:confirm |
| PUT /taihu/orders/{id}/reject | 0→2，body.remark 必填（服务端校验），权限 taihu:order:reject |
| PUT /taihu/orders/{id}/done | 1→3，body.actualTons 选填，缺省 sum(tz_salvage_log.actual_tons where order_id)，无台账保持 null（区别于实收 0），权限 taihu:order:done |
| GET /taihu/geojson、/taihu/dashboard?days= | 代理 algo /zones/geojson、/dashboard/eval+/history/series（合并为 {eval,history}），权限 taihu:order:list |
| POST /taihu/pipeline/run?date=、/taihu/seed?days= | 代理 algo，权限 taihu:pipeline:run + @Log 审计 |

## 4. 状态机与代理实现要点

- 状态机集中在 `TzDispatchOrderServiceImpl`：先 `requireOrder` 校验存在，再校验当前状态合法流转，非法抛 `ServiceException`（全局异常处理器转 AjaxResult 错误提示，提示文案含当前状态名）；状态流转用稀疏 update（只 set status/remark/actual_tons/update_by/update_time），不覆盖 solver/run_id 等算法字段。
- 台账 `insertSalvageLog`：orderId 非空 → 校验调度单存在 → 强制以调度单口径覆盖 boat_id/zone_id/log_date/half_day（契约"自动带出"语义：带出值优先于手填）。
- 代理 `TzAlgoProxyService`：地址 `${taihu.algo-base}`（默认 `${ALGO_BASE:http://algo:9100}`）；RestClientException 统一转 ServiceException("算法服务不可达…")；返回体用 fastjson2（ruoyi-common 已带）解析为 JSONObject 装进 AjaxResult.data。
- 坑（已修）：Spring Framework 7 移除了 `UriComponentsBuilder.fromHttpUrl`，改用 `fromUriString`。

## 5. SQL（sql/taihu.sql，先跑 ry_20260417.sql 再跑本文件）

- 7 张表：契约 §2 的 5 张业务表 + tz_forecast_record/tz_run（algo 写入，Java 侧只建表）——全部补齐若依审计五字段；tz_dispatch_order/tz_salvage_log 带 remark VARCHAR(200)，驳回原因写 remark。
- 菜单 menu_id 2000-2060：目录「太湖调度」+ 工作台/调度单/台账/船只/分区/分离站/看板 7 个 C 菜单（component 对应 taihu/*/index）+ F 按钮（list/query/add/edit/remove + confirm/reject/done + pipeline:run）；工作台与看板 C 菜单 perms 留空（页面可见性由角色-菜单控制，读接口挂 taihu:order:list）。
- 演示授权：角色 taihu_manager（role_id=10）授全量 taihu 菜单；演示账号 taihu（user_id=3，密码 admin123，bcrypt 串与 admin 同源）绑该角色。admin 超管免授权。
- 脚本可重复执行（drop/delete 先行）；重跑会清掉 tz_ 业务数据（init 语义）。

## 6. 配置与镜像

- application.yml 新增：`taihu.algo-base: ${ALGO_BASE:http://algo:9100}`；日志配置未动。
- Dockerfile（eclipse-temurin:17-jre，拷 ruoyi-admin.jar，EXPOSE 8080，TZ=Asia/Shanghai）；构建上下文 = ruoyi-backend/。

## 7. 构建验证（2026-10-14 00:32，JDK 21.0.10 + Maven 3.9.14）

命令：`mvn -pl ruoyi-admin -am package -DskipTests`
```
ruoyi-common SUCCESS [1.460s]  ruoyi-system SUCCESS [0.239s]  ruoyi-framework SUCCESS [0.284s]
ruoyi-quartz  SUCCESS [1.483s] ruoyi-generator SUCCESS [0.205s] ruoyi-taihu SUCCESS [0.143s]
ruoyi-admin   SUCCESS [2.114s]  BUILD SUCCESS  Total time: 6.241s
```
产物核验：ruoyi-admin/target/ruoyi-admin.jar（约 90MB）内 BOOT-INF/lib/ruoyi-taihu-3.9.2.jar（41KB）含 6 个 Controller、TzAlgoProxyService 与 5 个 mapper XML。

## 8. 留给集成方（主对话 compose / 前端 / algo）的注意项

1. 容器内需注入 druid/redis 环境变量（如 SPRING_DATASOURCE_DRUID_MASTER_URL、SPRING_DATA_REDIS_HOST）+ ALGO_BASE；详见 ruoyi-backend/README.taihu.md。
2. 前端路径约定：geojson/dashboard 均要求 taihu:order:list，taihu_manager 已具备；订单卡操作按钮用 v-hasPermi 对应 confirm/reject/done。
3. reject/done 为 PUT + JSON body：reject 必须带 {"remark":"..."}；done 可带 {"actualTons":12.5} 或空 body。
4. contracts §2 提到的 1→0 可改派回退未在本期接口内（任务只列 confirm/reject/done）——如需，走 orders/edit 修改或后续加 /reassign 接口。【待核 1】
5. tz_zone.boundary 为 JSON 列，Java 侧按 String 读写，GeoJSON 解析在前端/algo 侧完成。
6. algo /solve 未单独代理（契约 §4 未要求，pipeline/run 内含 solve）——若前端要"仅重求解"再加接口。【待核 2】
