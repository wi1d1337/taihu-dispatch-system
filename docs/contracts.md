# 太湖调度系统 · 接口契约 v2（工单工作流版，2026-10-14）

> v1（调度单优化版）已被 v2 取代。v2 依据工作流重调研与用户确认的四项决策：
> ①算法自动派单+人工可撤 ②两级派发（算法/调度→片长→船/人） ③研判=同比外推+省预案阈值规则 ④打捞员端=H5。
> 三端单一事实源：be-ruoyi（Java 工单后端）、fe-admin（管理台+H5）、be-python（研判引擎与算法服务）。变更先改本文件。

## 1. 工作流总图

```
数据接入(遥感/浮标预留/巡测/气象)
  → 研判引擎 = 同比外推+阈值规则(定干预等级,决策主线) ⊕ 概率预报(72h,并列展示第二意见) → tz_assessment
  → 自动派单：level≥dispatch.auto_level → 自动建工单(status=0 待接单, 派给片长)
    并附 CP-SAT 推荐派船方案(suggest_boats 预填,片长接单可改)；level=关注 → 只进研判面板
  → 片长(H5)接单 → 确认/调整派船+打捞员 → status=1 已接单
  → 打捞员(H5)到场打卡 → status=2 进行中
  → 提交完成(藻水吨+照片) → status=3 待核查
  → 调度/核查员核查 → status=4 已办结（不合格 → status=5 已退回，整改重报回 3）
  → 全程可撤销：status=6 已撤销（待接单/已接单状态，人工可撤，必填原因）
  → 超时督办（不是状态，是计时器动作）：催办→改派→升级督办，记 tz_order_log
```

## 2. 数据表（ry-vue 库；新增/改造，前缀 tz_）

**保留 v1**：tz_zone（去掉预报字段含义变化：prob_72h 改为研判等级冗余，可保留）、tz_boat、tz_station、tz_run。
**废弃 v1**：tz_dispatch_order、tz_salvage_log、tz_forecast_record（数据清空，模块下线）。
**algo 内部表（v2 新增，algo-service 建表自管，RuoYi 侧无需建表/不读写）**：tz_chla_obs（zone_id+obs_date 唯一，chla DECIMAL）——逐日分区 Chl-a 实测/合成序列，是同比外推、回验准确率与 autotune 回测的**事实底座**；seed 写入两年合成史（以昨日结尾），pipeline ingest 逐日追加。另：tz_assessment.basis 内同步携带概率预报第二意见（prob/q25/q50/q75 与其 model_version）。

```sql
-- 人员（片长/打捞员）
CREATE TABLE tz_worker (
  worker_id BIGINT PK AI, worker_name VARCHAR(50) NOT NULL,
  role CHAR(1) NOT NULL COMMENT '1片长 2打捞员(船长)',
  phone VARCHAR(20), zone_id BIGINT COMMENT '负责分区(片长)',
  team_name VARCHAR(50) COMMENT '所属队伍/班组',
  username VARCHAR(64) COMMENT '关联sys_user登录名(H5登录)',
  status CHAR(1) DEFAULT '0', 审计字段
) COMMENT='作业人员';

-- 工单（核心表，两级派发）
CREATE TABLE tz_work_order (
  order_id BIGINT PK AI, order_no VARCHAR(32) NOT NULL UNIQUE COMMENT 'TZ20261014-0001',
  zone_id BIGINT NOT NULL, level CHAR(1) NOT NULL COMMENT '藻情等级 1关注 2较大 3重大(对齐省预案一般/较大/重大)',
  source CHAR(1) DEFAULT '1' COMMENT '1算法自动 2人工创建',
  assessment_id BIGINT COMMENT '来源研判记录',
  status CHAR(1) DEFAULT '0' COMMENT '0待接单 1已接单 2进行中 3待核查 4已办结 5已退回 6已撤销',
  leader_id BIGINT COMMENT '片长(首派对象)',
  boat_id BIGINT COMMENT '片长接单时指定',
  crew_ids VARCHAR(200) COMMENT '打捞员工号逗号分隔',
  deadline DATETIME COMMENT '处置时限(自动派单时按 level 定)',
  dispatched_at DATETIME, accepted_at DATETIME, arrived_at DATETIME,
  submitted_at DATETIME, checked_at DATETIME,
  actual_tons DECIMAL(8,2) COMMENT '藻水湿重吨(完成时报)',
  photo_urls VARCHAR(500), arrive_lng DECIMAL(10,6), arrive_lat DECIMAL(10,6),
  suggest_boats JSON COMMENT 'CP-SAT推荐派船方案[{boat_id,zone_id,tons}]，片长接单预填可改',
  revoke_reason VARCHAR(200) COMMENT '撤销原因', check_comment VARCHAR(200),
  model_version VARCHAR(40), param_version INT,
  审计字段
) COMMENT='打捞工单';

-- 工单流转日志（督办动作也记这：action=催办/改派/升级督办）
CREATE TABLE tz_order_log (
  log_id BIGINT PK AI, order_id BIGINT NOT NULL,
  action VARCHAR(30) COMMENT '派单/接单/派船/到场/提交/核查/办结/退回/撤销/催办/改派/升级督办',
  operator VARCHAR(64), from_status CHAR(1), to_status CHAR(1),
  note VARCHAR(300), created_at DATETIME
) COMMENT='工单流转日志';

-- 研判记录（算法结论与依据）
CREATE TABLE tz_assessment (
  assessment_id BIGINT PK AI, assess_date DATE NOT NULL, zone_id BIGINT NOT NULL,
  level CHAR(1) COMMENT '0无需干预 1关注 2较大(建议打捞) 3重大(应急)',
  predicted DECIMAL(7,2) COMMENT '同比外推预测Chl-a', yoy DECIMAL(7,2) COMMENT '去年同期值',
  recent7 DECIMAL(7,2) COMMENT '近7日均值', trend DECIMAL(6,2) COMMENT '近7日趋势%',
  basis JSON COMMENT '依据明细{阈值引用/超阈天数/同比偏差/参数快照key值}',
  confidence DECIMAL(4,2), model_version VARCHAR(40), param_version INT,
  created_at DATETIME
) COMMENT='研判记录';

-- 算法参数注册表（AI 动态调参的接口位）
CREATE TABLE tz_algo_param (
  param_id BIGINT PK AI, param_key VARCHAR(64) NOT NULL UNIQUE,
  param_value DECIMAL(12,4) NOT NULL, min_value DECIMAL(12,4), max_value DECIMAL(12,4),
  unit VARCHAR(20), meaning VARCHAR(200) COMMENT '业务含义',
  updated_by VARCHAR(64), updated_at DATETIME, version INT DEFAULT 1
) COMMENT='算法参数注册表(调整走API+审计+版本递增)';
```

**初始参数集**（seed 写入，key 区分大小写）：
| param_key | 默认值 | 含义 |
|---|---|---|
| assess.level1_chla | 20 | 关注阈值 μg/L（省预案一般档叶绿素a） |
| assess.level2_chla | 50 | 较大阈值（省预案较大档） |
| assess.level3_chla | 80 | 重大阈值（省预案重大档） |
| assess.yoy_halfwin | 7 | 同比窗口：去年同期±N 天 |
| assess.yoy_weight | 0.6 | 同比基线权重（近 7 日趋势占 0.4） |
| assess.exceed_days | 3 | 近 N 日超阈天数计入升级 |
| dispatch.auto_level | 2 | 达到该等级及以上自动派单（2=较大） |
| dispatch.deadline_h_l2 | 24 | 较大等级处置时限（小时） |
| dispatch.deadline_h_l3 | 4 | 重大等级处置时限（小时） |
| supervise.remind_h | 2 | 超时多久开始催办（小时） |
| supervise.escalate_h | 6 | 催办多久未动升级督办（小时） |
| autotune.enabled | 1 | 自动调参开关（1开 0关） |
| autotune.mode | 2 | 1=建议(人工确认后生效) 2=自动(区间内直接生效) |
| autotune.window_days | 30 | 回看窗口（天） |
| autotune.max_step_pct | 10 | 单次调整最大幅度（当前值百分比） |
| autotune.min_gain | 2 | 最小改进阈值（命中率百分点，低于不调） |

-- 参数变更审计（人工调整与自动调参共用）
CREATE TABLE tz_param_log (
  log_id BIGINT PK AI, param_key VARCHAR(64) NOT NULL,
  old_value DECIMAL(12,4), new_value DECIMAL(12,4),
  old_version INT, new_version INT,
  trigger CHAR(1) COMMENT '1人工 2自动调参 3自动建议待确认',
  evidence JSON COMMENT '证据{窗口命中率/POD/FAR/MAE, 候选对比, 调整理由}',
  operator VARCHAR(64), created_at DATETIME
) COMMENT='参数变更审计';

## 3. algo-service API（http://algo:9100/api/v1，无鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /healthz | 存活 |
| POST | /seed?days=730 | 重置演示数据：两年合成史（保证"去年同期"存在）、人员/船/站/分区、历史工单与研判、今日研判+按 dispatch.auto_level 自动建单 |
| POST | /pipeline/run-daily?date= | ingest→assess（同比外推定级+概率预报并列）→auto-dispatch（达标自动建工单+CP-SAT推荐派船方案）→report→**autotune**（自我改进步：回测→有界调参→明日研判生效），各步写 tz_run |
| GET | /assess/today | 今日六分区研判结论：同比外推定级+basis明细 与 概率预报(prob/q25/q50/q75) 并列返回，含参数与模型版本 |
| GET | /assess/accuracy?days=30 | 研判回验：预测等级 vs 次日实际（合成事实），输出命中/误报/漏报 |
| GET | /params ｜ PUT /params/{key} | 参数注册表读取/修改（body: value, operator）→ 写库+版本递增+tz_param_log(trigger=1)；修改后下一轮研判生效 |
| GET | /autotune/history ｜ POST /autotune/run | 自动调参：run=对近 autotune.window_days 天研判 vs 实际做回测，对 {yoy_weight, level1/2/3_chla} 在 min/max 与 max_step_pct 约束内做有界候选搜索，改进≥min_gain 则按 mode 生效（写 tz_param_log trigger=2/3+evidence）；history=参数值与命中率随时间曲线（含每次调整证据） |
| GET | /dashboard/eval?days=30 | 考核看板：响应时长分布（派单→接单/到场/办结）、办结率、超时督办次数、日生日清率、各分区吨数 |
| GET | /daily/report?date= | 日报数据：当日工单汇总+吨数+未办结清单（对齐水利局逐日上报口径） |
| GET | /zones/geojson | 同 v1，properties 增加 level（当日研判等级，地图色阶改用等级） |

**同比外推引擎口径**（be-python 实现）：
预测值 = yoy_weight × 去年同期均值(±yoy_halfwin) + (1−yoy_weight) × 近7日均值 × (1+trend/100 修正)；等级按 predicted 对照 level1/2/3_chla，且近 exceed_days 日超阈则升级一档；无去年数据时回落为"近7日均值+阈值"并标注 basis.degraded=true。所有用到的参数值快照进 basis。

## 4. RuoYi 后端业务接口（/taihu/**，JWT）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /taihu/orders/list | 工单分页（date/zone/level/status 过滤，联表 zone_name/leader_name/boat_name） |
| PUT | /taihu/orders/{id}/revoke | 撤销（status∈{0,1}，body.revokeReason 必填）→ 6 |
| PUT | /taihu/orders/{id}/reassign | 改派（body: leaderId）→ 回 0，记日志 |
| PUT | /taihu/orders/{id}/check | 核查（status=3 → body.pass=true→4 / false→5+checkComment） |
| POST | /taihu/orders | 人工创建工单（source=2） |
| GET/POST/PUT/DELETE | /taihu/workers… | 人员管理（片长/打捞员，绑定分区） |
| 保留 | /taihu/zones /boats /stations | 台账 CRUD 同 v1 |
| GET | /taihu/assess/today ｜ /taihu/assess/accuracy | 代理 algo |
| GET/PUT | /taihu/params ｜ /taihu/params/{key} | 代理 algo（PUT 挂管理员权限 taihu:param:edit） |
| GET/POST | /taihu/autotune/history ｜ /taihu/autotune/run | 代理 algo（run 挂 taihu:param:edit） |
| GET | /taihu/dashboard ｜ /taihu/daily/report | 代理 algo |
| POST | /taihu/pipeline/run ｜ /taihu/seed | 代理 algo（taihu:pipeline:run） |
| **H5 接口（免动态菜单，仍走 JWT；演示账号用 taihu/admin123）** | | |
| POST | /h5/login | H5 登录（username/password → 复用若依 login，返回 token+角色） |
| GET | /h5/my/orders?status= | 我的工单（片长=派给我的；打捞员=crew_ids 含我） |
| PUT | /h5/orders/{id}/accept | 片长接单（body: boatId, crewIds 必填）→ 1 |
| PUT | /h5/orders/{id}/arrive | 到场打卡（body: lng, lat）→ 2 |
| PUT | /h5/orders/{id}/submit | 提交完成（body: actualTons 必填, photoUrls 选填）→ 3 |
| GET | /h5/me | 当前用户信息+角色 |

超时督办：后端 @Scheduled 每 10 分钟扫 status∈{0,1,2,3} 且超 supervise 阈值的工单 → 写 tz_order_log（催办/升级督办）+ 状态不變；催办超 escalate_h 仍无动作且 status=0 → 自动改派（轮转到同分区其他片长，无则升级督办）。

菜单调整：太湖调度目录下改为——调度工作台(研判+工单总览)、工单管理、研判面板、算法参数、作业人员、分区、船只、分离站、考核看板、日报；v1 的"调度单/打捞台账"菜单删除。

## 5. 前端页面（RuoYi-Vue3 + H5）

**管理台**（src/views/taihu/）：
1. workbench/index.vue：上半部研判面板（六分区卡片：等级色块/预测值/同比/趋势/超阈天数/参数版本+今日准确度），下半部今日工单列表（状态 tab、撤销/改派/核查操作）；顶栏【运行今日研判】（pipeline/run）【重置演示数据】。
2. orders/index.vue：全量工单管理（筛选+详情抽屉含流转时间线 tz_order_log+核查/撤销/改派）。
3. assess/index.vue：研判历史与回验准确度（趋势图+命中矩阵）。
4. params/index.vue：算法参数表（key/当前值/区间/含义/更新人/版本，行内编辑弹窗，保存调 PUT）——"算法痕迹"核心页；页内"自动调参"区块：开关/模式（建议·自动）展示、【立即调参】按钮（POST /taihu/autotune/run）、参数值与命中率随时间曲线（/taihu/autotune/history）、每次调整的证据列表（前后值/窗口指标/理由，人工调整与自动调整同列）。
5. workers/boats/zones/stations：CRUD（workers 新增）。
6. dashboard/index.vue：考核看板（响应时长分布/办结率/日生日清/超时督办数/分区吨数柱状）。
7. daily/index.vue：日报页（按日期查询，汇总表+未办结清单，导出 Excel 按钮用若依 download）。

**H5**（src/views/h5/，独立路由 /h5 不走管理台布局，移动端样式）：
- h5/login.vue（账号密码）
- h5/orders.vue：我的工单卡片列表（状态筛选；片长卡=接单按钮→弹层选船+选打捞员；打捞员卡=到场打卡/提交完成表单[吨数+照片上传]）
- H5 判定：登录后 GET /h5/me 按角色渲染（片长看到接单操作；打捞员看到打卡/提交）
- 照片上传复用若依 /common/upload，多图逗号拼 photo_urls

## 6. e2e v2 场景（scripts/e2e.sh 重写）

登录管理台 → seed(730天) → pipeline/run（研判+自动派单）→ 查今日研判（/taihu/assess/today）→ 若有 level≥2 工单则：H5 登录片长 → 接单(派船派人) → H5 登录打捞员 → 到场打卡 → 提交完成(吨数+照片占位) → 管理台核查办结 → PUT 参数（把 level2 阈值改 60）→ 再跑一次研判验证参数生效（等级变化）→ 看板与日报取数。全程断言状态流转与考核指标非空。

## 7. 兼容与迁移

- sql/taihu.sql 重写（v2 表结构+菜单+演示账号 taihu 片长/打捞员各一名：leader01/worker01 密码 admin123）。
- algo-service：v1 的 forecast/solve/seed 模块下线或改造；新增 assess.py(同比外推)、params.py、workorder 桥（直接写 tz_work_order）。
- 前端 v1 的 orders(调度单)/logs 页面删除替换。
