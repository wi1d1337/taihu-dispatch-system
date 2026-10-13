# 管理台 v2 + H5 实现底稿（fe-admin，2026-10-13）

> 契约：docs/contracts.md v2（工单工作流版）。改动仅限 ruoyi-ui/，未 commit。
> 后端 v2 控制器尚未合入（ruoyi-taihu 仍是 v1），前端按契约先行对接，字段做了 snake/camel 双口径容错。

## 1. 交付清单

**API 层（src/api/taihu/）**
| 文件 | 状态 | 内容 |
|---|---|---|
| orders.js | 重写 | list/get/add/revoke/reassign/check（PUT 带 body） |
| workers.js | 新建 | 人员 CRUD（/taihu/workers…） |
| assess.js | 新建 | today / accuracy |
| params.js | 新建 | list / updateParam / runAutotune / getAutotuneHistory |
| dashboard.js | 重写 | getDashboard({days})（考核口径） |
| daily.js | 新建 | getDailyReport({date}) |
| h5.js | 新建 | login / me / my orders / accept / arrive / submit / uploadPhoto |
| pipeline.js | 保留 | run / seed（run+seed 加 timeout 120s 前者沿用 request 默认，后端长耗时注意） |
| zones/boats/stations.js | 保留 | v1 台账不变 |
| logs.js | 删除 | v1 打捞台账下线 |

**管理台页面（src/views/taihu/）**
| 页面 | 状态 | 要点 |
|---|---|---|
| workbench/index.vue | 重写 | 六分区研判卡（等级色块/预测vs同比/近7日趋势/超阈天数/prob+q50并列/degraded标注）+ 回验摘要行 + 版本徽标「vX（含N次自动调参）」；今日工单四状态 tab（撤销弹原因/改派选片长/核查通过退回）；顶栏运行研判+重置演示数据（taihu:pipeline:run）；Leaflet 地图色阶改 level（0灰#b9bfc9/1黄#f2c14e/2橙#e6772e/3红#d93025） |
| orders/index.vue | 重写 | date/zone/level/status 筛选 + 人工创建 + 详情抽屉（基本信息/时间节点/照片/CP-SAT suggest_boats 表/流转时间线 el-timeline） |
| params/index.vue | 新建 | 参数表（key/值/区间/单位/含义/更新人/版本，行内调整弹窗 min/max 校验→PUT）+ 自动调参区块（开关/模式徽标、立即调参→结果弹窗或"无改进跳过"）+ 调参历史（参数阶梯折线+命中率副轴+证据表：时间/参数/旧值→新值/触发[人工·自动·建议]/窗口命中率前后/理由） |
| assess/index.vue | 新建 | 回验 KPI 卡 + 预测vs实际等级阶梯折线 + 分区×日期热力（无分区明细时隐藏）+ 3×3 命中矩阵（对角线高亮） |
| workers/index.vue | 新建 | 人员 CRUD（角色/电话/负责分区/班组/登录名/状态） |
| dashboard/index.vue | 重写 | 办结率/日生日清率/超时督办/平均办结时长 KPI + 响应三段均值 + 直方 + 分区吨数柱状 |
| daily/index.vue | 新建 | 日期查询 + 汇总卡 + 工单明细 + 未办结清单 + 导出（前端 CSV/BOM，见待核项3） |
| logs/index.vue | 删除 | v1 模块下线 |

**H5（src/views/h5/ + 路由）**
- h5/login.vue：账号密码 → POST /taihu/h5/login → setToken('Admin-Token')（与若依同 key，后续自动带 Bearer）→ 跳 /h5/orders。
- h5/orders.vue：GET /taihu/h5/me 定角色（role 直取；roles 数组关键字兜底，缺省按片长）；胶囊 tab（全部/待接单/进行中/待核查/已办结）；片长卡=接单底部弹层（船下拉+打捞员 chips 多选，suggest_boats[0] 预填船）；打捞员卡=到场打卡（geolocation，失败手填经纬度）/提交完成（吨数必填+多图 /common/upload 拼 photoUrls）；30s 定时刷新；无 token 进页自动跳 /h5/login。

## 2. 接口对接表（前端调用 → 契约 §4）
| 页面 | 接口 |
|---|---|
| workbench | GET /taihu/orders/list、/taihu/assess/today、/taihu/assess/accuracy、/taihu/autotune/history（计数）、PUT orders/{id}/revoke·reassign·check、POST /taihu/pipeline/run、/taihu/seed、GET /taihu/geojson（properties.level 着色） |
| orders | GET /taihu/orders/{id}（logs 取 data.logs→order.logs→orderLogs 逐级兜底；suggest_boats 四键名兜底） |
| params | GET/PUT /taihu/params(/{key})（PUT body {value, operator=当前用户}）、POST /taihu/autotune/run、GET /taihu/autotune/history |
| assess | GET /taihu/assess/accuracy?days= |
| dashboard | GET /taihu/dashboard?days= |
| daily | GET /taihu/daily/report?date= |
| H5 | POST /taihu/h5/login、GET /taihu/h5/me、GET /taihu/h5/my/orders、PUT /taihu/h5/orders/{id}/accept·arrive·submit、POST /common/upload |

## 3. H5 路由与白名单改法
- router/index.js constantRoutes 增加 `/h5` 分支（children: login、orders，hidden，不挂 Layout）。
- permission.js：`whiteList = ['/login', '/register', '/h5/login', '/h5/orders']`——H5 完全绕过动态菜单与 getInfo。

## 4. 构建输出
`npm run build:prod`：**通过**，`✓ built in 20.66s`，EXIT_CODE=0（2026-10-13，node22+vite6.4.1）。产物在 ruoyi-ui/dist/。

## 5. 集成注意项（给 be-ruoyi / be-python）
1. **H5 前缀**：契约 §4 表内写 /h5/*（节标题为 /taihu/**），前端按任务指令实现为 `/taihu/h5/*`，集中在 api/taihu/h5.js 一处，若后端挂载不同改 4 个 URL 即可。
2. **H5 token**：与若依同用 Admin-Token cookie；H5 登录会覆盖管理台登录态（同浏览器互踢），演示可接受，生产建议独立 key+自定义 header。
3. **日报导出**：契约写"若依 download"，后端暂无接口，本期前端生成 CSV（UTF-8 BOM，Excel 可开）；后端补 /taihu/daily/export 后可切换。
4. **响应字段**：_algo 代理与 orders 详情做了 snake/camel 双兜底；若后端最终字段为下划线则零改动，自定义包裹（如 data.order）也已兜底。**accuracy 明细若无分区维度，assess 热力图自动隐藏**。
5. **权限标识**：页面仅挂契约点名的 taihu:pipeline:run、taihu:param:edit、taihu:worker:*；工单撤销/改派/核查按钮未挂 v-hasPermi（按状态显示），菜单 SQL 定权限时后端接口侧兜底。
6. **超时**：autotune/run 前端 120s；pipeline/run、seed 仍是全局 10s——730 天 seed 若超 10s 需后端异步化或前端再加 timeout。
7. **菜单**：需按契约 §4 建菜单（工作台/工单管理/研判面板/算法参数/作业人员/分区/船只/分离站/考核看板/日报），组件路径对应 views/taihu/{workbench,orders,assess,params,workers,zones,boats,stations,dashboard,daily}/index。

## 待核项
- [T1] /taihu/autotune/history 返回结构（series/logs/hitSeries 键名）未定，前端已多键名兜底，联调时对齐。
- [T2] GET /taihu/orders/{id} 是否内嵌 logs 未定（任务允许按后端实现对接）。
- [T3] /taihu/h5/me 角色字段口径（role=1/2 还是 roles 数组）未定，已双兜底。
