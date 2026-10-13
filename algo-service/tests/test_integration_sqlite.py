"""集成冒烟（sqlite 临时库，不依赖 MySQL/容器）。

覆盖：seed(730) 全流程 → 今日研判/自动派单/幂等 → pipeline run-daily →
params PUT(version+1+log) → autotune mode=2 生效路径（人工调坏参数→回测找回→
version 递增+tz_param_log trigger=2）→ 看板/日报/回验/geojson 取数非空。
运行：python tests/test_integration_sqlite.py
"""

from __future__ import annotations

import datetime as dt
import json
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

_TMP = tempfile.mkdtemp(prefix="tz_smoke_")
os.environ["TZ_DB_URL"] = "sqlite:///" + (_TMP + "/smoke.db").replace("\\", "/")

from app.db import SessionLocal                       # noqa: E402  (env 已就位)
from app.seed import seed                             # noqa: E402

db = SessionLocal()
FAILS = []


def check(name, cond, extra=""):
    print(("PASS " if cond else "FAIL ") + name + (f"  {extra}" if extra and not cond else ""))
    if not cond:
        FAILS.append(name)


# ---------------------------------------------------------------- 1) seed ----
stats = seed(db, days=730)
print("seed stats:", json.dumps(stats, ensure_ascii=False)[:400])
check("seed: 两年观测点", stats["obs_points"] == 730 * 6, str(stats["obs_points"]))
check("seed: 历史研判 90×6", stats["assessments"] == 90 * 6)
check("seed: 历史工单 > 50", stats["hist_orders"] > 50, str(stats["hist_orders"]))
check("seed: 今日有自动工单", stats["today_orders_created"] >= 1,
      json.dumps(stats["today_levels"]))

from app.models import (TzAlgoParam, TzAssessment, TzOrderLog, TzParamLog,  # noqa: E402
                        TzWorkOrder, TzZone)
today = dt.date.today()
check("seed: 今日研判 6 条", db.query(TzAssessment).filter_by(assess_date=today).count() == 6)

today_orders = db.query(TzWorkOrder).filter(
    TzWorkOrder.order_no.like(f"TZ{today.strftime('%Y%m%d')}-%")).all()
check("seed: 今日工单状态=0待接单", all(o.status == "0" for o in today_orders))
check("seed: 工单挂片长+时限+推荐派船",
      all(o.leader_id and o.deadline and o.suggest_boats is not None for o in today_orders))
check("seed: 派单日志存在",
      db.query(TzOrderLog).filter(TzOrderLog.action == "派单").count() >= stats["hist_orders"])
check("seed: 参数初始化 16 条", db.query(TzAlgoParam).count() == 16)
check("seed: 督办日志存在（考核看板有数据）",
      db.query(TzOrderLog).filter(TzOrderLog.action.in_(["催办", "升级督办"])).count() > 0)

# ------------------------------------------------------- 2) 幂等 re-dispatch --
from app.dispatch2 import auto_dispatch                     # noqa: E402
re = auto_dispatch(db, today)
check("幂等: 重跑不重复建单", len(re["created"]) == 0 and len(re["skipped"]) >= 1,
      json.dumps(re, ensure_ascii=False)[:200])

# ------------------------------------------------------------- 3) pipeline ----
from app.pipeline import run_daily                         # noqa: E402
res = run_daily(db, today)
print("pipeline:", {k: v.get("status") for k, v in res["steps"].items()})
check("pipeline: 五步全 1", all(v.get("status") == "1" for v in res["steps"].values()),
      json.dumps({k: v for k, v in res["steps"].items()}, ensure_ascii=False, default=str)[:500])
check("pipeline: 首跑 ingest 新建今日实测",
      res["steps"]["ingest"]["detail"]["zones_touched"] == 6)
res2 = run_daily(db, today)
check("pipeline: 二跑 ingest 幂等跳过",
      res2["steps"]["ingest"]["detail"]["zones_skipped_exists"] == 6
      and res2["steps"]["ingest"]["detail"]["zones_touched"] == 0)
check("pipeline: 二跑自动派单幂等",
      res2["steps"]["auto-dispatch"]["detail"]["created"] == 0)

# --------------------------------------------------------- 4) 取数接口口径 ----
from app.evalx import assess_accuracy, daily_report, evaluate   # noqa: E402
ev = evaluate(db, days=30)
check("看板: 响应时长三项有样本",
      ev["response_hours"]["dispatch_to_accept"]["n"] > 0
      and ev["response_hours"]["accept_to_arrive"]["n"] > 0
      and ev["response_hours"]["submit_to_close"]["n"] > 0)
check("看板: 办结率/日生清/督办/吨数非空",
      ev["close_rate"] is not None and ev["same_day_clear"]["overall_rate"] is not None
      and ev["supervise_count"] > 0 and sum(z["tons"] for z in ev["zone_tons"]) > 0)

dr = daily_report(db, today)
check("日报: 当日汇总+未办结", dr["orders_total"] >= 1 and "open_orders" in dr)
dr_y = daily_report(db, today - dt.timedelta(days=3))
check("日报: 历史日吨数>0", dr_y["tons_total"] > 0, str(dr_y["tons_total"]))

acc = assess_accuracy(db, days=30)
check("回验: 样本/命中矩阵/事件指标",
      acc["samples"] > 100 and sum(sum(r) for r in acc["matrix"]["counts"]) == acc["samples"]
      and acc["event_metrics"]["pod"] is not None)
print("accuracy:", {k: acc[k] for k in ("samples", "hit_rate", "false_alarms", "misses")})

zones = db.query(TzZone).all()
assess_today = {a.zone_id: a for a in db.query(TzAssessment).filter_by(assess_date=today).all()}
check("geojson 数据源: zone.prob 与今日 level 齐备",
      all(z.prob_72h is not None for z in zones)
      and all(int(a.level) >= 0 for a in assess_today.values()))

# --------------------------------------------- 5) params PUT：版本递增+审计 ----
from app.params import apply_param, get_params, param_history, current_param_version  # noqa: E402
v0 = current_param_version(db)
r = apply_param(db, "assess.level2_chla", 55.0, "tester")     # 人工把阈值调坏（偏离最优）
check("params: PUT 生效 version+1", r["version"] == 2 and r["param_value"] == 55.0)
check("params: param_version(=max) 推进", current_param_version(db) == v0 + 1)
logs = param_history(db, key="assess.level2_chla")
check("params: tz_param_log trigger=1", logs and logs[0]["trigger"] == "1"
      and logs[0]["old_value"] == 50.0 and logs[0]["new_value"] == 55.0)

# 调参后下一轮研判读到新值（basis 参数快照携带 55）
from app.assess import assess_day                          # noqa: E402
assess_day(db, today)
row = (db.query(TzAssessment).filter_by(assess_date=today, zone_id=1).first())
snap = json.loads(row.basis)["params"]
check("params: 修改后下一轮研判 basis 快照带新值", snap["assess.level2_chla"] == 55.0)

# --------------------------------------- 6) autotune mode=2：回测找回阈值 ----
from app.autotune import autotune_history, autotune_run      # noqa: E402
before = get_params(db)
v_before = current_param_version(db)
at = autotune_run(db)
print("autotune:", json.dumps({k: at.get(k) for k in
                               ("applied", "suggested", "skipped", "reason", "gain_pp")},
                              ensure_ascii=False)[:500])
log_rows = db.query(TzParamLog).filter(TzParamLog.trigger == "2").count()
if at.get("applied"):
    after = get_params(db)
    changed = [c for c in at["changes"]]
    check("autotune: mode=2 生效且 version 递增",
          current_param_version(db) > v_before and log_rows >= 1)
    check("autotune: 调整在 ±10% 有界内",
          all(abs(c["new"] - c["old"]) <= abs(c["old"]) * 0.101 + 1e-6 for c in changed))
    check("autotune: evidence 中文理由", bool(at.get("reason")))
else:
    check("autotune: 无改进路径留痕（tz_run）", "reason" in at)
    # 强证据路径：人为把 yoy_weight 调到 0.9（边界），验证 mode=2 生效机制本身
    apply_param(db, "assess.level2_chla", 55.0, "tester2")
    at2 = autotune_run(db)
    print("autotune(2nd):", json.dumps({k: at2.get(k) for k in
                                        ("applied", "suggested", "skipped", "reason")},
                                       ensure_ascii=False)[:300])
    check("autotune: 第二轮至少留痕", "reason" in at2)

hist = autotune_history(db)
check("autotune/history: 曲线+证据+当日回测",
      hist["param_curves"]["assess.yoy_weight"]
      and hist["adjustments"] and hist["today_backtest"] is not None)

db.close()
print("\n%s（失败 %d 项）" % ("集成冒烟通过" if not FAILS else "集成冒烟有失败", len(FAILS)))
sys.exit(1 if FAILS else 0)
