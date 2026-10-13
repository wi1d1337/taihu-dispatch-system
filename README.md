# 太湖蓝藻水华预报与打捞调度优化系统（演示版 v2 · 工单工作流版）

> **仅供个人研究学习，禁止商业使用**（PolyForm Noncommercial 1.0.0，见 [LICENSE](LICENSE)）
> 内置数据为**合成演示数据**（非真实太湖观测值）；分区边界为手工简化示意多边形（非测绘数据）。

按真实打捞业务流重建的"研判 → 工单 → 执行 → 督办"闭环系统（v1 的"算法排班"形态已被 v2 取代，工作流依据见 `docs/` 与调研底稿）：

## 工作流（七步闭环）

```
数据接入（遥感/浮标IoT预留/人工巡测/气象）
  ① 研判引擎：同比外推 + 省预案阈值规则（定干预等级）
     ⊕ 概率预报（72h，第二意见并列展示）
     ⊕ CP-SAT 推荐派船方案（自动建单时预填给片长，可改）
  ② 自动派单：等级≥阈值 → 自动建工单派给事发分区片长（人工可撤销/改派）
  ③ 片长 H5 接单 → 确认/调整派船与打捞员
  ④ 打捞员 H5 到场定位打卡 → 作业
  ⑤ 提交完成（藻水湿重吨 + 照片）
  ⑥ 调度核查办结 / 退回整改；超时三段式督办（催办→改派→升级）
  ⑦ 日报（对齐逐日上报口径）+ 考核看板（响应时长/办结率/日生日清）
  ★ 自动调参（自我改进）：每日管线末段回测近 30 天"研判 vs 实际"，
    对同比权重与分级阈值做有界候选搜索（幅度≤10%、改进≥2pp 才动），
    全部变更（人工/自动）进参数审计日志，模式可切"建议/自动"
```

**算法透明化**：16 个算法参数全部落"参数注册表"（key/当前值/区间/含义/更新人/版本），管理台"算法参数"页可视化调整并展示调参历史与证据；每条研判/工单携带 model_version 与 param_version，结论可回溯到当日的参数与数据——为"AI 动态调参"预留的标准接口。

## 快速开始

```bash
docker compose up -d --build
# 关闭验证码（演示环境）：
docker compose exec -T mysql sh -c "mysql -uroot -proot123 ry-vue -e \"update sys_config set config_value='false' where config_key='sys.account.captchaEnabled';\""
docker compose restart ruoyi-admin
# 端到端验证（十一步：研判→建单→接单→打卡→提交→核查→调参→自动调参闭环）：
bash scripts/e2e.sh http://localhost:8090
```

| 入口 | 地址 | 账号 |
|---|---|---|
| 管理台（研判面板/工单/算法参数/考核看板/日报） | http://localhost:8090 | admin / admin123 |
| H5 作业端（片长接单·打捞员打卡提交） | http://localhost:8090/h5/login | leader01-06（片长）/ worker01-08（打捞员），密码均 admin123 |

## 架构

```
浏览器 ─ nginx:8090 ─┬─ RuoYi-Vue3 管理台 + H5（ruoyi-ui）
                     └─ /prod-api/ → ruoyi-admin:8080（若依 Spring Boot 4 + ruoyi-taihu：
                        工单状态机/H5接口/超时督办任务/algo代理）
                         └─ algo:9100（FastAPI：同比外推+概率预报+CP-SAT+自动调参）
                              └─ MySQL 8（tz_* 业务表+参数注册表+审计）+ Redis 7
```

## 目录

| 目录 | 说明 | 许可证 |
|---|---|---|
| `ruoyi-backend/` | 若依（vendored）+ ruoyi-taihu v2 模块（工单/H5/督办/代理） | MIT（若依）+ 本仓库 NC |
| `ruoyi-ui/` | RuoYi-Vue3（vendored）+ 管理台 v2 页面 + H5 作业端 | MIT（若依）+ 本仓库 NC |
| `algo-service/` | 研判引擎/自动派单/自动调参/管线（FastAPI） | 本仓库 NC |
| `algo-service/taihu_toolkit/` | 算法基础库（[taihu-bloom-toolkit](https://github.com/wi1d1337/taihu-bloom-toolkit)） | MIT |
| `docs/contracts.md` | v2 接口与数据契约（三端并行开发单一事实源） | — |

## 设计红线

- 算法与派单：自动派单但人工可撤，两级派发（片长→船/人），决策与责任在人；
- 状态机七态：待接单/已接单/进行中/待核查/已办结/已退回/已撤销；督办是计时器动作不是状态；
- 自动调参有界：区间+单次幅度+最小改进三重约束，建议/自动两种模式，全部留痕；
- 无大屏驾驶舱、无流程引擎、无表单设计器、无 BI 套件；地图 GeoJSON 零瓦片离线可用。

## 致谢

[RuoYi-Vue](https://github.com/yangzongzhuan/RuoYi-Vue) / [RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3)（MIT）· [OR-Tools](https://developers.google.com/optimization)（Apache-2.0）· [taihu-bloom-toolkit](https://github.com/wi1d1337/taihu-bloom-toolkit)（MIT）· [Leaflet](https://leafletjs.com)（BSD-2）· [ECharts](https://echarts.apache.org)（Apache-2.0）

---

© 2026 无锡天垣视界科技有限公司 · PolyForm Noncommercial 1.0.0
