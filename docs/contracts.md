# 太湖调度系统 · 接口契约与数据契约（v1，2026-10-13）

> 本文件是三路并行开发的单一事实源：be-ruoyi（Java 业务模块）、fe-admin（RuoYi-Vue3 前端）、主对话（algo-service Python 算法微服务）。任何契约变更须先改本文件并通知其他两方。
> 拍板决策：若依 Java 底座 + Python 算法微服务 ｜ 单机 docker-compose 演示版 ｜ GeoJSON 零瓦片底图 ｜ PolyForm Noncommercial 1.0.0

## 1. 部署拓扑（docker-compose 服务）

| 服务 | 端口 | 说明 |
|---|---|---|
| mysql | 3306（内部） | 库名 `ry-vue`，字符集 utf8mb4；若依自带表 + 业务表（§2） |
| redis | 6379（内部） | 若依缓存 |
| ruoyi-admin | 8080（内部） | Java 后端（若依 + taihu 业务模块） |
| algo | 9100（内部） | Python FastAPI 算法服务（无鉴权，仅 compose 内网可达） |
| nginx | 8090 → 宿主 | 托管 RuoYi-Vue3 构建产物 + 反代 `/prod-api/` → ruoyi-admin |

前端所有业务请求统一走若依后端（JWT 鉴权），**前端不直连 algo**；若依后端代理调用 algo（`ALGO_BASE=http://algo:9100` 环境变量）。

## 2. 业务表（建在 `ry-vue` 库，前缀 tz_；均带若依审计字段 create_by/create_time/update_by/update_time/remark）

```sql
-- 分区（太湖六分区，boundary 为 GeoJSON Polygon 坐标数组的 JSON 串）
CREATE TABLE tz_zone (
  zone_id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  zone_name VARCHAR(50) NOT NULL UNIQUE COMMENT '分区名',
  zone_code VARCHAR(20) COMMENT '代码 MLW/ZSH/GH/HX/XBAY/NBAY',
  area_km2  DECIMAL(8,2),
  boundary  JSON COMMENT '[[lng,lat],...] 外环',
  prob_72h  DECIMAL(5,2) COMMENT '72h水华概率0-1',
  tons_q25  DECIMAL(10,2), tons_q50 DECIMAL(10,2) COMMENT '预计可收藻(吨/日)',
  chla      DECIMAL(7,2) COMMENT '当前Chl-a ug/L',
  update_time DATETIME
) COMMENT='打捞分区';

-- 船只
CREATE TABLE tz_boat (
  boat_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  boat_name VARCHAR(50) NOT NULL, boat_type VARCHAR(20) COMMENT '打捞/机动/小型',
  capacity_t DECIMAL(8,2) COMMENT '吨/日', speed_kmh DECIMAL(5,1), fuel_l_km DECIMAL(5,2),
  status CHAR(1) DEFAULT '0' COMMENT '0可用 1维护', home_port VARCHAR(50)
) COMMENT='打捞船只';

-- 藻水分离站
CREATE TABLE tz_station (
  station_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  station_name VARCHAR(50) NOT NULL, capacity_t_day DECIMAL(8,2), status CHAR(1) DEFAULT '0'
) COMMENT='藻水分离站';

-- 调度单（状态机：0待确认 →1已确认 →3已完成；0→2已驳回；1→0可改派回退）
CREATE TABLE tz_dispatch_order (
  order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_date DATE NOT NULL, half_day CHAR(1) NOT NULL COMMENT '0上午 1下午',
  boat_id BIGINT NOT NULL, zone_id BIGINT NOT NULL, station_id BIGINT,
  plan_tons DECIMAL(8,2) COMMENT '建议预计收藻',
  actual_tons DECIMAL(8,2) COMMENT '完成时回填',
  status CHAR(1) DEFAULT '0',
  solver VARCHAR(20) COMMENT 'CP_SAT/GREEDY',
  objective DECIMAL(12,2), model_version VARCHAR(40), run_id BIGINT,
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME, remark VARCHAR(200)
) COMMENT='调度单';

-- 打捞台账（全系统唯一人工录入点：从调度单带出，现场只补实收数）
CREATE TABLE tz_salvage_log (
  log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT COMMENT '关联调度单(可空=计划外)',
  boat_id BIGINT NOT NULL, zone_id BIGINT NOT NULL,
  log_date DATE NOT NULL, half_day CHAR(1),
  actual_tons DECIMAL(8,2) NOT NULL, fuel_liters DECIMAL(8,1), crew INT,
  photo_url VARCHAR(200), lng DECIMAL(10,6), lat DECIMAL(10,6),
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME, remark VARCHAR(200)
) COMMENT='打捞台账';

-- 预报记录（供复盘与看板）
CREATE TABLE tz_forecast_record (
  fc_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  zone_id BIGINT NOT NULL, fc_date DATE NOT NULL, horizon_h INT DEFAULT 72,
  prob DECIMAL(5,2), chla_q25 DECIMAL(7,2), chla_q50 DECIMAL(7,2), chla_q75 DECIMAL(7,2),
  create_time DATETIME
) COMMENT='预报记录';

-- 管线运行（审计：run_id 贯穿调度单）
CREATE TABLE tz_run (
  run_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_date DATE NOT NULL, step VARCHAR(30) COMMENT 'ingest/forecast/solve/report',
  status CHAR(1) COMMENT '0运行 1成功 2失败',
  started_at DATETIME, ended_at DATETIME, model_version VARCHAR(40), detail TEXT
) COMMENT='管线运行';
```

## 3. algo-service API（FastAPI，http://algo:9100，前缀 /api/v1，无鉴权）

| 方法 | 路径 | 入参 | 出参 |
|---|---|---|---|
| GET | /healthz | - | {"status":"ok"} |
| POST | /seed | ?days=120 | 重置并生成演示数据（分区/船/站/历史台账/预报/今日调度单），返回统计 |
| POST | /pipeline/run-daily | ?date=YYYY-MM-DD（缺省今天） | 跑当日管线 ingest→forecast→solve→report，返回各步 run 记录 |
| POST | /solve | ?date&half_day=0&quantile=q50 | CP-SAT（超时 30s 演示口径）求解并写入调度单，返回 {orders, objective, solver} |
| GET | /zones/geojson | - | FeatureCollection：geometry=Polygon(boundary)，properties={zone_id,zone_name,prob_72h,tons_q50,chla} |
| GET | /dashboard/eval | ?days=30 | {kpi:{POD,FAR,CSI}, daily:[{date,plan_tons,actual_tons,abs_dev}], zone_stats:[...]} |
| GET | /history/series | ?days=90 | {dates[], zones{zone_name: chla[]}}（看板趋势用） |

- 数据库连接：环境变量 `TZ_DB_URL=mysql+pymysql://root:password@mysql:3306/ry-vue?charset=utf8mb4`
- 算法底座：vendored `algo-service/taihu_toolkit/`（来自 MIT 库 taihu-bloom-toolkit，保留其 LICENSE 注记）；求解：CP-SAT（ortools）+ 贪心降级
- 调度求解口径：与 taihu-bloom-toolkit/dispatch.py 同构（船容量×作业时长折减 − 航行油成本；分区可收量扣减；分离站产能；重点分区贡湖保底一艘）

## 4. RuoYi 后端业务接口（Java，挂 `/taihu`，若依 JWT/权限体系）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /taihu/orders | 分页：date/status/halfDay 过滤；联表返回 boat_name/zone_name/station_name |
| PUT | /taihu/orders/{id}/confirm | 待确认→已确认（记操作人） |
| PUT | /taihu/orders/{id}/reject | 待确认→已驳回（remark 必填驳回原因） |
| PUT | /taihu/orders/{id}/done | 已确认→已完成（body.actualTons 选填，缺省取台账汇总） |
| GET | /taihu/zones ｜ /taihu/boats ｜ /taihu/stations | 台账 CRUD（列表+新增+改+删，若依标准风格） |
| GET/POST/PUT/DELETE | /taihu/logs… | 打捞台账 CRUD；POST 支持带 orderId（自动带出船/分区/日期） |
| GET | /taihu/geojson | 代理 algo /zones/geojson |
| GET | /taihu/dashboard | 代理 algo /dashboard/eval 与 /history/series 合并返回 |
| POST | /taihu/pipeline/run | 代理 algo /pipeline/run-daily（仅管理员，@PreAuthorize hasPermi('taihu:pipeline:run')） |
| POST | /taihu/seed | 代理 algo /seed（仅管理员，演示重置用） |

菜单 SQL：一级目录「太湖调度」(taihu)：调度工作台(workbench 页面)、调度单(orders)、打捞台账(logs)、船只(boats)、分区(zones)、分离站(stations)、运行看板(dashboard)；权限字符 taihu:order:list/confirm/reject、taihu:log:*、taihu:pipeline:run 等；演示种子给 admin 全量权限。

## 5. 前端页面（RuoYi-Vue3，src/views/taihu/）

1. **workbench/index.vue 调度工作台**：左 Leaflet 地图（GeoJSON 分区面，色阶=prob_72h，弹窗=分区信息）+ 右调度单卡片列表（按状态 tab：待确认/已确认/已完成/已驳回；卡片操作：确认/驳回/完成）；顶栏「生成今日调度单」（调 /taihu/pipeline/run）与「重置演示数据」（/taihu/seed，二次确认）。地图零瓦片：纯色底+分区面+岸线示意（boundary 自带），不用任何外部瓦片。
2. **orders/index.vue**：若依标准表格页（日期/状态筛选+行操作），可并入工作台 tab，但独立保留便于管理。
3. **logs/index.vue**：台账列表 + 「回填」对话框：选日期→未回填调度单下拉（选中带出船/分区），只填 实收吨数(必填)/油耗/人数/备注 → 提交一次完成。
4. **dashboard/index.vue**：ECharts——POD/FAR/CSI 三卡片、计划vs实收与偏差折线、分区收藻柱状、Chl-a 趋势（/taihu/dashboard）。
5. 样式遵循 RuoYi-Vue3 现有 Element Plus 风格；不新增重型依赖（Leaflet 与 echarts 若依已带 echarts；Leaflet 需 npm add，BSD-2）。

## 6. 许可证与署名

- 本仓库根 LICENSE = PolyForm Noncommercial 1.0.0；README 附"仅供个人研究学习，禁止商业使用"声明。
- vendored 若依（RuoYi-Vue / RuoYi-Vue3，MIT）：保留其 LICENSE 文件于 ruoyi-backend/LICENSE、ruoyi-ui/LICENSE，README 致谢。
- algo-service vendored taihu_toolkit（MIT，来源 wi1d1337/taihu-bloom-toolkit）：保留源仓库注记。
