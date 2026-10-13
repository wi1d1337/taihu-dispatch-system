# 太湖蓝藻水华预报与打捞调度优化系统（演示版）

> **仅供个人研究学习，禁止商业使用**（PolyForm Noncommercial 1.0.0，见 [LICENSE](LICENSE)）
> 本仓库为演示版：内置数据为**合成演示数据**（非真实太湖观测值），分区边界为手工简化示意多边形（非测绘数据）。

面向湖库蓝藻打捞业务的"预报 → 调度 → 执行回填 → 复盘"全链路演示系统：

- **调度求解**：OR-Tools CP-SAT 精确求解（超时自动降级贪心启发式）——船×分区×时段派遣，约束含分区可收量上限、藻水分离站产能、重点水域保底；
- **预报引擎**（演示级）：历史序列持续性+分位数口径，输出分区 72h 水华概率与三分位 Chl-a（真实部署按技术方案替换为 CNN-LSTM，接口不变）；
- **管理底座**：RuoYi-Vue（Java，MIT）组织/权限/审计 + RuoYi-Vue3（Vue3 + Element Plus）；
- **调度员工作台**：Leaflet 湖面分区地图（GeoJSON 零瓦片、离线可用）+ 调度单确认/驳回/完成；
- **反形式主义**：调度单即台账草稿——现场只补"实收吨数"一个数字，全系统唯一人工录入点，报表全部只读派生。

技术调研底稿见各子代理产出（求解器/管理台/自托管三路选型），数学建模见《太湖蓝藻水华预报与打捞调度优化系统技术实施方案》（无锡天垣视界科技有限公司）。

## 架构

```
浏览器 ── nginx:8090 ──┬─ RuoYi-Vue3 静态资源（ruoyi-ui/dist）
                       └─ /prod-api/ → ruoyi-admin:8080（若依 Java + taihu 业务模块）
                                        └─ http://algo:9100（Python FastAPI 算法服务）
                                             ├─ OR-Tools CP-SAT 求解
                                             ├─ 演示预报/评测（taihu_toolkit，MIT）
                                             └─ MySQL 8（ry-vue 库：若依表 + tz_* 业务表）
                                        └─ Redis 7（若依缓存）
```

## 快速开始

```bash
docker compose up -d --build
# 首次启动后初始化演示数据（也可登录后在"太湖调度→调度工作台"点【重置演示数据】）：
curl -X POST "http://localhost:8090/prod-api/taihu/seed" -H "Authorization: Bearer <token>"
# 或直接在算法服务容器内：docker compose exec algo python -c "from app.db import SessionLocal; from app.seed import seed; print(seed(SessionLocal()))"
```

访问 http://localhost:8090 （默认账号 `admin / admin123`，若依默认）→ 菜单「太湖调度」：
1. **调度工作台**：左侧分区地图（色阶=72h 水华概率），右侧待确认调度单；【生成今日调度单】触发每日管线（ingest→forecast→solve→report）；
2. **打捞台账**：回填对话框从调度单自动带出船/分区，只填实收吨数；
3. **运行看板**：POD/FAR/CSI、计划 vs 实收偏差、分区收藻、Chl-a 趋势。

## 目录结构

| 目录 | 说明 | 许可证 |
|---|---|---|
| `ruoyi-backend/` | RuoYi-Vue（Java Spring Boot，vendored）+ taihu 业务模块（调度单状态机/台账/代理接口） | MIT（若依）+ 本仓库 NC |
| `ruoyi-ui/` | RuoYi-Vue3（Vue3 + Element Plus，vendored）+ taihu 页面（工作台/台账/看板） | MIT（若依）+ 本仓库 NC |
| `algo-service/` | Python FastAPI 算法微服务：CP-SAT 求解、演示预报、每日管线、评测 | 本仓库 NC |
| `algo-service/taihu_toolkit/` | 算法基础库（来自 [taihu-bloom-toolkit](https://github.com/wi1d1337/taihu-bloom-toolkit)） | MIT |
| `docs/contracts.md` | 接口与数据契约（三端开发单一事实源） | — |

## 设计红线（反形式主义）

- 无大屏驾驶舱、无流程引擎、无表单设计器、无微服务拆分（除算法服务）、无独立 BI；
- 台账事实单表：一行=一次打捞作业，看板/日报/导出全部只读派生；
- 求解器旁路：AI 只出建议（调度单），确认权在调度员；
- 断网可用：地图零瓦片、依赖全部本地。

## 致谢

- [RuoYi-Vue](https://github.com/yangzongzhuan/RuoYi-Vue) / [RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3)（MIT）
- [Google OR-Tools](https://developers.google.com/optimization)（Apache-2.0）
- [taihu-bloom-toolkit](https://github.com/wi1d1337/taihu-bloom-toolkit)（MIT，无锡天垣视界科技有限公司）
- [Leaflet](https://leafletjs.com)（BSD-2）、[ECharts](https://echarts.apache.org)（Apache-2.0）

---

© 2026 无锡天垣视界科技有限公司 · PolyForm Noncommercial 1.0.0
