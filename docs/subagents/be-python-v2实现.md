# be-python · algo-service v2 实现底稿（研判→工单 + autotune）

> 2026-10-14。契约：docs/contracts.md v2。范围：algo-service/ 全部重写/改造，未动 ruoyi-backend / ruoyi-ui。
> 纪律落实：参数全在 tz_algo_param 注册表；研判/工单带 model_version + param_version(=全表 max version)；合成事实与结论同源联动。

## 1. 模块清单（app/）

| 模块 | 职责 | 要点 |
|---|---|---|
| models.py | v2 ORM | 新：tz_worker/tz_work_order/tz_order_log/tz_assessment/tz_algo_param/tz_param_log；保留 tz_zone/boat/station/run；**新增 algo 内部表 tz_chla_obs**（zone_id+obs_date 唯一，两年 Chl-a 事实底座；契约 §2 已补注，RuoYi 侧无需建表）；v1 三表不建 ORM。PK 用 BigInteger().with_variant(Integer,"sqlite") 便于本地冒烟 |
| params.py | 参数注册表 | DEFAULT_PARAMS 16 键（契约 §2 全集）；apply_param 统一入口：min/max 校验→写库→version+1→tz_param_log(trigger 1/2/3)；ensure_defaults 幂等自愈 |
| assess.py | 研判主线 | extract_features(参数无关窗口统计)+decide(定级)拆分，autotune 回测只重放 decide；assess_one=研判+概率预报第二意见+吨位；assess_day upsert tz_assessment（assessment_id 稳定供派单关联）+zone 缓存 |
| forecast.py | 第二意见 | 纯函数 forecast_zone 保留 v1 口径（synth-persist-v1）；去落库；est_tons 供 CP-SAT 需求量 |
| solve.py | CP-SAT 推荐派船 | recommend_boats(demand)：只产 [{boat_id,zone_id,tons}] 建议列表不落单；贪心降级保留；10s 时限 |
| dispatch2.py | 自动派单 | level≥dispatch.auto_level 建单（status=0/片长/时限/CP-SAT suggest_boats/派单日志）；同日同分区未撤销单幂等跳过；build_order_no=TZyyyymmdd-序号 |
| autotune.py | 自我递归改进 | 见 §4 |
| seed.py | 演示数据 | 730 天史（synth→**演示校准** chla'=k_z·chla^0.62→tz_chla_obs，昨日结尾）；16 参数初始化；人员 6 片长+8 打捞员（username=leader01…/worker01…）；90 天历史研判+425 张左右历史工单（完整流转/催办/升级督办/退回重报/在途单、吨数与 Chl-a 联动、照片占位）；今日研判+自动派单 |
| evalx.py | 取数 | evaluate(响应时长三项/办结率/超时督办/日生日清/分区吨数)；daily_report(汇总+吨数+未办结含超时)；assess_accuracy(命中矩阵4×4/POD/FAR/CSI/误报漏报，阈值取该条 basis 快照) |
| pipeline.py | 管线 | ingest(幂等追加当日 obs)→assess→auto-dispatch→report→autotune，各步写 tz_run，单步失败不阻断 |
| main.py | 路由 | 契约 §3 全量 12 端点；geojson properties 加 level/level_name/predicted，prob 保留 |

## 2. 接口清单（/api/v1）

healthz｜POST /seed?days=730(365-1095)｜POST /pipeline/run-daily?date=｜GET /assess/today（定级+basis+forecast 并列+版本）｜GET /assess/accuracy?days=｜GET /params｜PUT /params/{key}(body: value/operator)｜POST /autotune/run｜GET /autotune/history｜GET /dashboard/eval?days=｜GET /daily/report?date=｜GET /zones/geojson。v1 的 /solve、/history/series 下线。

## 3. 算法口径落点（与契约 §3 逐条对齐）

- 预测值 = yoy_weight×去年同期均值(±yoy_halfwin) + (1−yoy_weight)×近7日均值×(1+trend/100)；trend=近7日 vs 前7日环比。**实测公式测试**：0.6×40+0.4×60×1.2=52.8→L2。
- 等级对照 level1/2/3_chla（20/50/80）。**升级规则实现为"持续性确认"**：近 exceed_days 日实测已持续达到**上一档**阈值才升一档（封顶3），basis 记 exceed_against/exceed_threshold——比"超 level1 即升"更稳（否则富营养背景全域 L3）；此为契约未细化的实现口径，已在 basis 可解释。
- 无去年同期 → predicted 回落近7日均值，basis.degraded=true；近7日也缺 → 仅同比；全缺 → level 0。
- basis JSON：params 全量快照/thresholds/超阈天数与窗口/upgraded/degraded/reason/yoy_deviation_pct/trend_pct/**forecast 第二意见**。
- 回验事实口径：实际=研判当日 tz_chla_obs 实测按**该条研判当时的参数快照**定级（等效提前一天，因研判只用当日之前数据）；autotune 同源。
- param_version = tz_algo_param 全表 max(version)；model_version：assess=yoy-threshold-v2，forecast=synth-persist-v1（basis 内分开携带），autotune=autotune-v1。

## 4. autotune 设计

- 数据：近 window_days 天 (features, 当日实测) 对（特征与参数解耦，提前用当前 halfwin/exceed_days 提取一次）。
- 指标：等级命中率(主)/事件 POD/FAR/CSI(level≥2)/预测 MAE。
- 候选：{yoy_weight, level1/2/3_chla} 各取 当前/±max_step_pct%（clip 到注册表 min/max）→ 全组合网格（≤3^4=81），约束 level1<level2<level3；并列取"改动数少"者。
- 决策：命中率提升≥min_gain(pp)→mode=2 逐键 apply_param(trigger=2, evidence)；mode=1 仅写 tz_param_log(trigger=3)；无改进→tz_run 留证据跳过（正常路径）。evidence 含 fact_basis/前后指标/top5 候选/中文理由一句。
- /autotune/history：4 键参数值曲线（tz_param_log 重建）+全部调整证据（人工/自动同列）+最近一次运行+当日回测指标。
- 每次运行（含 pipeline 末步）写 tz_run(step=autotune)；失败不阻断管线。

## 5. 冒烟结果（2026-10-14，本机 Python 3.11）

- tests/test_v2.py（纯函数，14 项）：定级边界/公式精确值/升级(持续性、3选2不升)/降级/空序列/仅同比/params 校验/order_no/forecast 单调/_metrics/_candidate_grid 有界 —— 全 PASS。
- `python -c "from app.main import app"` 通过（16 路由）。
- tests/test_integration_sqlite.py（sqlite 临时库全流程，29 项）：seed 4380 观测+540 历史研判+425 历史工单+今日 2 分区 L3 自动建单；派单/ingest 幂等；pipeline 五步全绿；看板/日报/回验（180 样本，命中 97.8%、误报 4、漏报 0）非空；PUT 参数 version 1→2+trigger=1 日志；**autotune mode=2**：人工调坏 level2=55 后回测 95.6%→98.9%(+3.3pp) 自动生效 55→60.5，version 递增+trigger=2 日志+有界 ±10% —— 全 PASS。
- requirements/Dockerfile 未改（无新增依赖）。

## 6. 集成注意项（给 be-ruoyi / fe-admin / 运维）

1. **tz_chla_obs 为 algo 内部表**：algo seed 时 create_all 自建；sql/taihu.sql 不用建（契约 §2 已补注）。tz_param_log/tz_algo_param 等业务表仍由 taihu.sql 建，字段以契约 §2 为准（basis/suggest_boats/evidence 为 JSON 列，algo 以 Text 写 JSON 字符串，MySQL JSON 列兼容）。
2. 工单"同日"判定用 order_no 前缀 TZyyyymmdd-（历史单 dispatched_at 同步落当日 08:30）；撤销(status=6)后重跑 pipeline 会重建该分区当日单——符合"人工可撤+算法补位"预期，前端注意列表刷新。
3. /assess/today 在无研判时返回空 zones+note（先 /seed 或 /pipeline/run-daily）；geojson 的 level 在无当日研判时为 null（前端色阶需处理 null→灰色）。
4. pipeline 首跑会为"今日"新建 tz_chla_obs（seed 只到昨日）；重复跑幂等。
5. PUT /params 修改后下一轮 assess 生效（e2e：level2 改 60 → 等级变化可验证）；autotune 与人工调参共用 tz_param_log，前端参数页证据列表直接取 /autotune/history.adjustments（trigger 1/2/3 同列）。
6. 演示数据全部合成：seed 校准系数记录在 tz_run(detail.calibration)；照片为 /profile/photos/demo/ 占位路径。
