# fe-admin 实现底稿（RuoYi-Vue3 太湖调度前端，2026-10-13）

> 契约依据 docs/contracts.md §4/§5；只改 ruoyi-ui\，未触碰 ruoyi-backend\ 与 algo-service\；未 git commit（由主对话统一提交）。

## 1. 页面清单（全部完成）

| 页面 | 路径 | 要点 |
|---|---|---|
| 调度工作台 | src/views/taihu/workbench/index.vue | 顶栏「生成今日调度单/重置演示数据」+ 左 Leaflet 零瓦片地图 + 右状态 tab 调度单卡片（确认/驳回/完成） |
| 调度单 | src/views/taihu/orders/index.vue | 日期/状态/时段筛选 + 分页 + 行内操作（与工作台同一套 handler 逻辑） |
| 打捞台账 | src/views/taihu/logs/index.vue | 台账列表 + 回填对话框（选日期→未回填已确认单下拉→带出船/分区，仅填实收/油耗/人数/备注）+ 修改/删除 |
| 船只 | src/views/taihu/boats/index.vue | 标准 CRUD（类型/容量/航速/油耗/母港/状态） |
| 分区 | src/views/taihu/zones/index.vue | 标准 CRUD；boundary 不进表单，列表只读显示「N 个坐标点」 |
| 分离站 | src/views/taihu/stations/index.vue | 标准 CRUD（处理能力/状态） |
| 运行看板 | src/views/taihu/dashboard/index.vue | POD/FAR/CSI 三卡片 + 计划vs实收双线含偏差柱 + 分区收藻柱状 + Chl-a 多分区折线；7/30/90 天切换 |

API 层（若依 request 封装风格）：src/api/taihu/{orders,logs,boats,zones,stations,dashboard,pipeline}.js；geojson 归 zones.js（getGeojson）。

## 2. 接口对接表

| 前端函数 | 方法+路径 | 备注 |
|---|---|---|
| listOrder | GET /taihu/orders | params: pageNum/pageSize/date/status/halfDay |
| confirmOrder | PUT /taihu/orders/{id}/confirm | 无 body |
| rejectOrder | PUT /taihu/orders/{id}/reject | body {remark} 必填（前端 inputPattern /\S/ 校验） |
| doneOrder | PUT /taihu/orders/{id}/done | body {actualTons} 选填（留空传 undefined，后端取台账汇总） |
| listLog/addLog/updateLog/delLog | GET/POST/PUT/DELETE /taihu/logs(/{ids}) | 按 contracts §4 字面路径（无 /list 后缀） |
| listBoat/listZone/listStation 系列 | GET/POST/PUT/DELETE /taihu/boats|zones|stations(/{ids}) | 同上，四个 CRUD 端点同构 |
| getGeojson | GET /taihu/geojson | 防御取 res.data ?? res，要求 FeatureCollection |
| getDashboard | GET /taihu/dashboard | params {days}，归一化兼容见 §5 |
| runPipeline | POST /taihu/pipeline/run | body {date:今日}，loading 态 + 成功消息 |
| seedDemoData | POST /taihu/seed | $modal.confirm 二次确认后才发 |

## 3. Leaflet 用法要点

- `npm install leaflet`（^1.9.4，BSD-2，已入 package.json dependencies）。
- 引入：`import L from 'leaflet'` + `import 'leaflet/dist/leaflet.css'`（组件内）。
- 零瓦片：创建 `L.map(el, { attributionControl: true })` 后**不加载任何 tileLayer**；容器 CSS 背景纯色 `#dcecec` 作水面底色。
- attribution：`map.attributionControl.setPrefix('太湖分区示意')`，无外部版权串。
- 色阶（prob_72h 五级浅青→深青）：<0.2 `#e8f5f4` / <0.4 `#bfe5e2` / <0.6 `#8ad2cd` / <0.8 `#4aa9a2` / ≥0.8 `#1d726c`；空值灰 `#cfd8dc`。地图右下角自带图例（absolute div，非 Leaflet control）。
- `L.geoJSON(fc, { style, onEachFeature })`：弹窗展示 zone_name/prob_72h(%)/tons_q50/chla；mouseover 加 weight 高亮；加载后 `fitBounds(bounds, {padding:[16,16]})`。
- 不用 marker，规避 Leaflet 默认图标在打包器的 404 问题；组件卸载 `map.remove()` 防泄漏。

## 4. 构建验证（2026-10-13，node v22.22.2 / npm 10.9.7）

- `npm install leaflet --save`：added 528 packages in 22s，exit 0。
- `npm run build:prod`：`✓ built in 19.17s`，EXIT=0，0 条 warn；dist 产物含 taihu 工作台代码（grep "zone-popup|水华概率" 命中 chunk）。

## 5. 集成注意项

1. **CRUD 列表路径无 /list 后缀**：contracts §4 写 `GET /taihu/zones` 等，前端按字面实现；若后端按若依生成器输出 `/taihu/zones/list`，只需改 src/api/taihu/ 四个文件里的 url 一行。
2. **logs 列表联表字段**：前端按 boatName/zoneName 渲染，后端需联表返回（同 orders 口径）。
3. **dashboard 合并结构**：前端兼容 `{kpi, daily, zone_stats|zoneStats, history:{dates,zones}}` 及平铺 dates/zones；daily/zone_stats 元素同时兼容下划线与驼峰键。**zone_stats 元素字段契约未细化**（contracts §3 只写 zone_stats:[...]），若 algo 实际字段不同需改 renderZone 的 pick 键名。
4. **回填候选逻辑**：契约 orders 接口无「未回填」过滤参数，前端拉当日 status=1 单再排除当日台账已关联的 orderId（纯前端比对）；并发场景可能出现重复回填，后端应对 POST /taihu/logs 带 orderId 做唯一性兜底。
5. **权限字符**：使用 taihu:order:confirm/reject/done/list、taihu:log:add/edit/remove/list、taihu:boat:*、taihu:zone:*、taihu:station:*、taihu:pipeline:run；seed 按钮复用 taihu:pipeline:run（契约未定义 seed 权限字符），后端菜单 SQL 需对齐。
6. **菜单 component 路径**：taihu/workbench/index、taihu/orders/index、taihu/logs/index、taihu/boats/index、taihu/zones/index、taihu/stations/index、taihu/dashboard/index（相对 src/views/）。
7. 页面均 `name="TaihuXxx"`（配合 keep-alive），构建 0 新增依赖（仅 leaflet）。
