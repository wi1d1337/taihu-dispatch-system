# be-ruoyi v2 实现（工单+H5）底稿 · 2026-10-13

> 范围：ruoyi-backend/ruoyi-taihu v1（调度单）→ v2（工单+H5），契约 docs/contracts.md v2。
> 验收：`mvn -pl ruoyi-admin -am package -DskipTests` BUILD SUCCESS（7.2s）；SQL 已在 taihu-mysql(8.0) 实测执行通过。

## 1. 接口清单

**工单（/taihu/orders，TzWorkOrderController）**
- GET /list（date/zone/level/status/source/orderNo 过滤，联表 zone_name/leader_name/boat_name）`taihu:order:list`
- GET /{id}、GET /{id}/logs（流转时间线）`taihu:order:query`
- POST /（人工创建 source=2）`taihu:order:add`；DELETE /{ids}（仅终态）`taihu:order:remove`
- PUT /{id}/revoke（0/1→6，body.revokeReason 必填）`taihu:order:revoke`
- PUT /{id}/reassign（0/1→0，body.leaderId，清船/人）`taihu:order:reassign`
- PUT /{id}/check（3→4/5，body.pass+checkComment）`taihu:order:check`

**人员（/taihu/workers，TzWorkerController）**：list/export/{id}/POST/PUT/DELETE，`taihu:worker:*`；H5 角色授予 list+query（接单选人）。

**研判（/taihu/assessments，TzAssessmentController）**：GET /list、/{id}（本地 tz_assessment 历史），`taihu:assess:list`。

**H5（/taihu/h5，TzH5Controller，走若依 JWT）**
- POST /login `@Anonymous`：body{username,password,code?,uuid?} 复用 SysLoginService（含验证码开关/锁定/黑名单），返回 token+workerRole
- GET /me `taihu:h5:me`：sys_user→tz_worker 映射（workerId/role/zone/team/permissions）
- GET /my/orders `taihu:h5:order:list`：片长=leader_id 我；打捞员=find_in_set(我workerId, crew_ids)（worker_id 口径，username 仅做账号映射）
- PUT /orders/{id}/accept `taihu:h5:order:accept`：body{boatId?,crewIds}，boatId 缺省 suggest_boats[0].boat_id；校验"仅指派片长可接单"
- PUT /orders/{id}/arrive `taihu:h5:order:arrive`：body{lng,lat} 必填；校验 crew 成员
- PUT /orders/{id}/submit `taihu:h5:order:submit`：body{actualTons 必填,photoUrls}；2→3、5→3（整改重报）
- 选船/选人复用 GET /taihu/boats、GET /taihu/workers（taihu_h5 已授 taihu:boat:list/taihu:worker:list）

**algo 代理（/taihu，TzAlgoController + TzAlgoProxyService v2 重写）**
- GET /geojson、/assess/today、/assess/accuracy?days、/dashboard?days、/daily/report?date
- GET /params `taihu:param:list`；PUT /params/{key} `taihu:param:edit`（body{value,operator}→algo 写库+版本+tz_param_log）
- GET /autotune/history；POST /autotune/run `taihu:param:edit`
- POST /pipeline/run、/seed `taihu:pipeline:run`
- v1 的 /history/series 合并逻辑已删（v2 契约 dashboard 只对 /dashboard/eval）；PUT 用 RestTemplate.exchange+JSON body

## 2. 工单状态机实现（TzWorkOrderServiceImpl，校验全在 service 层）

- 常量：TzWorkOrder.STATUS_PENDING(0)~STATUS_REVOKED(6)；非法流转抛 ServiceException（若依全局异常→业务错误 JSON）
- 人工创建：order_no=TZyyyyMMdd-NNNN（selectMaxOrderNo 续号），status=0，source=2，deadline 按 level 直读 tz_algo_param（3级4h/2级24h/1级48h 缺省），leaderId 缺省取分区首位片长，记「派单」
- revoke：仅 0/1；reason 必填；记「撤销」
- reassign：仅 0/1；换 leader、status 回 0、dispatched_at 重置、clearAssignment（boat/crew/accepted_at 清空，独立 SQL 语句避免 update 的 <if> 语义冲突），记「改派」
- check：仅 3；pass→4（记「核查」+「办结」，checked_at）／false→5（checkComment 必填，记「退回」）
- accept：仅 0；片长本人；crewIds 必填且逐个校验存在在岗；记「接单」+「派船」（含船号与打捞员清单）
- arrive：仅 1；crew 成员；lng/lat 必填；记「到场」
- submit：2→3 或 5→3（整改重报）；actualTons>0；记「提交」
- delete：仅 4/6，连带删 tz_order_log
- 全部 @Transactional：主表更新+日志写入同事务

## 3. 超时督办（TzOrderSuperviseTask，@Scheduled cron "0 */10 * * * ?"）

- @EnableScheduling 由模块内 TaihuScheduleConfig 开启（若依基座默认未开；基座定时任务走 quartz，督办是代码内计时器故不走 quartz）
- 阈值直读库：supervise.remind_h（缺省2）/supervise.escalate_h（缺省6）
- 动作基线：0=dispatched_at、1=accepted_at、2=arrived_at、3=submitted_at（缺省 create_time）
- 阶段一：无「催办」且超 remind → 记「催办」（状态不变）
- 阶段二：有「催办」且超 escalate 且本轮未升级：
  - status=0 → 同分区轮转选片长（selectCandidateLeader：worker_id>当前者优先，实现轮转）自动改派（复用 reassignOrder，operator=督办系统，基线重置进入新一轮催办）；无候选 → 记「升级督办」
  - status=2/3 → 记「升级督办」；status=1 契约未定不动作
- 防重：基线之前的催办/升级视为上一轮残留作废；本轮催办后已升级则跳过；单工单异常 try/catch 不影响整批

## 4. SQL 要点（sql/taihu.sql 全量重写，实测可重复执行）

- 保留 v1：tz_zone/tz_boat/tz_station/tz_run；删除 v1：tz_dispatch_order/tz_salvage_log/tz_forecast_record（drop）
- 新表 6 张：tz_worker、tz_work_order（uk_order_no+status/leader/zone/create 四索引）、tz_order_log（order_id+action+created_at 复合索引供督办 max(created_at)）、tz_assessment、tz_algo_param（uk_param_key）、tz_param_log
- 坑：tz_param_log.`trigger` 是 MySQL 保留字，DDL 与查询均须反引号（实测首跑报 1064 后修复）
- 菜单（menu_id 2000-2099，45 条）：调度工作台/工单管理/研判面板/算法参数/作业人员/分区/船只/分离站/考核看板/日报 + 隐藏目录"H5移动端"挂 taihu:h5:* 五个权限字符
- 角色：taihu_manager(10, 39 menus)；taihu_h5(11, 7 menus：h5×5+boat/worker 查询)
- 账号：taihu(3)/leader01(4)/worker01(5)，密码均 admin123（同 admin bcrypt 串）；tz_worker 两行以 username 关联，zone_id=1

## 5. 构建输出摘录

```
mvn -pl ruoyi-admin -am package -DskipTests
[INFO] Building jar: ruoyi-admin\target\ruoyi-admin.jar（repackaged，90.4MB）
[INFO] Building jar: ruoyi-taihu\target\ruoyi-taihu-3.9.2.jar（77KB）
[INFO] BUILD SUCCESS
[INFO] Total time: 7.187 s
```
- 一次编译错误已修：TzWorkerController.export 补 jakarta.servlet.http.HttpServletResponse 参数（Boot4/jakarta 口径）
- SQL 实测（taihu-mysql 8.0，ry-vue 库）：执行通过；tz_ 表 10 张；tz_algo_param 16 条；sys_menu 2000-2099 共 45 条；角色/账号/工号绑定核验通过；中文 utf8mb4 正常

## 6. 集成注意项

1. **ruoyi-taihu pom 新增 ruoyi-framework 依赖**（H5 登录复用 SysLoginService）；无循环依赖（framework 不依赖 taihu）
2. **taihu-mysql 已被本脚本重置为 v2 schema**，而 taihu-admin 容器仍跑 v1 jar——需 `docker compose build ruoyi-admin && docker compose up -d ruoyi-admin` 重建后才一致
3. H5 登录验证码：若 sys_config 的验证码开关为开，/taihu/h5/login 须带 code/uuid（与 /captchaImage 配套）；关则直接用户名密码。演示建议关验证码
4. crew_ids 存 worker_id CSV（契约 §2 注释口径）；"打捞员=crew_ids 含我"实现为按 /h5/me 映射出的 workerId 做 find_in_set——若 algo seed 重建 tz_worker 未保留 username 列映射，H5 账号会失联，需重跑 taihu.sql 的 tz_worker 段（seed 侧已约定保留人员表，待 be-python 对齐）
5. tz_assessment/tz_algo_param/tz_param_log 的写入方为 algo；Java 侧只读（tz_param_log 仅 domain/mapper，无 service/controller，符合委派）
6. algo PUT /params/{key} 的 body 由后端代填 operator=当前登录名（body 未传时）；审计（tz_param_log）由 algo 写
7. 督办自动改派的 operator="督办系统"，tz_order_log.operator 与 tz_worker.username 联表查 operator_name 时该行显示空（无此账号），前端按原文展示即可
8. 若依 e2e（scripts/e2e.sh）重写归编排侧；后端已具备契约 §6 全部接口
