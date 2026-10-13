# taihu 业务模块（ruoyi-taihu）

太湖调度系统的 Java 业务层：在 vendored 若依（RuoYi-Vue3 3.9.2，Spring Boot 4.1.0 / Java 17）之上新增的独立 maven 模块，未改动若依任何既有文件（除两处接线：根 `pom.xml` 的 `<modules>` / `dependencyManagement`，`ruoyi-admin/pom.xml` 的依赖），保持与上游 diff 最小。

## 模块结构

```
ruoyi-taihu/
├── pom.xml                       仅依赖 ruoyi-common，无新增第三方依赖
└── src/main/
    ├── java/com/ruoyi/taihu/
    │   ├── config/TaihuAlgoConfig.java        RestTemplate（连接 5s / 读 180s）
    │   ├── domain/                            TzZone/TzBoat/TzStation/TzDispatchOrder/TzSalvageLog
    │   ├── mapper/                            同名 5 个 Mapper 接口
    │   ├── service/                           接口 + impl；TzAlgoProxyService（algo 代理）
    │   └── controller/                        6 个 Controller（/taihu/**）
    └── resources/mapper/taihu/                5 个 MyBatis XML（mapperLocations 通配已覆盖）
```

## 接口一览（挂 `/taihu`，若依 JWT + @PreAuthorize）

| 方法 | 路径 | 权限字符 |
|---|---|---|
| GET | /taihu/zones/list、/{id}；POST/PUT/DELETE | taihu:zone:list/query/add/edit/remove |
| GET | /taihu/boats/… 同上 | taihu:boat:* |
| GET | /taihu/stations/… 同上 | taihu:station:* |
| GET | /taihu/orders/list（date/status/halfDay 过滤，联表 boat_name/zone_name/station_name）、/{id}；POST/PUT/DELETE | taihu:order:list/query/add/edit/remove |
| PUT | /taihu/orders/{id}/confirm | taihu:order:confirm（0→1） |
| PUT | /taihu/orders/{id}/reject（body.remark 必填） | taihu:order:reject（0→2） |
| PUT | /taihu/orders/{id}/done（body.actualTons 选填，缺省取台账汇总） | taihu:order:done（1→3） |
| GET/POST/PUT/DELETE | /taihu/logs/…（POST 带 orderId 自动带出船/分区/日期/半天） | taihu:log:* |
| GET | /taihu/geojson | taihu:order:list |
| GET | /taihu/dashboard?days= | taihu:order:list |
| POST | /taihu/pipeline/run?date= | taihu:pipeline:run |
| POST | /taihu/seed?days= | taihu:pipeline:run |

## 构建

```bash
# JDK 17+（本仓库按 JDK 21 验证）、Maven 3.9+
mvn -pl ruoyi-admin -am package -DskipTests
# 产物：ruoyi-admin/target/ruoyi-admin.jar
```

## ALGO_BASE（算法服务地址）

- 配置项 `taihu.algo-base`，默认 `${ALGO_BASE:http://algo:9100}`（docker-compose 内网服务名）。
- 环境变量 `ALGO_BASE` 覆盖，例如本地联调：`ALGO_BASE=http://127.0.0.1:9100`。
- 前端不直连 algo，统一经本模块代理（`TzAlgoProxyService`），algo 无鉴权、仅 compose 内网可达。

## 数据库与演示数据

1. 先执行 `sql/ry_20260417.sql`（若依系统表）；
2. 再执行 `sql/taihu.sql`：tz_ 业务表（含 algo 写入的 tz_forecast_record / tz_run）、菜单（menu_id 2000+）、演示角色 `taihu_manager`（role_id=10）与演示账号 `taihu / admin123`（user_id=3）；
3. 业务种子数据（分区边界/船只/历史台账/预报/今日调度单）由 `POST /taihu/seed` 调 algo 生成，不在 SQL 里。

## Docker

```bash
mvn -pl ruoyi-admin -am package -DskipTests
docker build -t taihu-ruoyi-backend .   # 构建上下文 = ruoyi-backend/
```

容器内需注入 MySQL/Redis 连接（RuoYi druid 配置项），例如：
`SPRING_DATASOURCE_DRUID_MASTER_URL=jdbc:mysql://mysql:3306/ry-vue?...`、`SPRING_DATA_REDIS_HOST=redis`。
