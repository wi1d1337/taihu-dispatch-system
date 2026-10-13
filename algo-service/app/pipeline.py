"""每日管线：ingest → forecast → solve → report（tz_run 记录审计，run_id 贯穿调度单）。

对应技术方案第六章运行节律；生产由 cron 触发（compose 内 supercronic 或宿主 cron
curl 本服务 /api/v1/pipeline/run-daily），演示由管理台按钮触发。
"""

from __future__ import annotations

import datetime as dt
import logging

import numpy as np
from sqlalchemy.orm import Session

from .forecast import MODEL_VERSION, run_forecast
from .models import TzForecastRecord, TzRun, TzZone
from .solve import solve_cp_sat

log = logging.getLogger("algo.pipeline")


def _ingest(db: Session, date: dt.date) -> dict:
    """演示口径"数据接入"：以小噪声推进各分区 Chl-a（模拟浮标/遥感日更新）。"""
    rng = np.random.default_rng(int(date.strftime("%Y%m%d")))
    touched = 0
    for z in db.query(TzZone).all():
        cur = float(z.chla or 8.0)
        z.chla = round(float(np.clip(cur * rng.normal(1.03, 0.10), 0.5, 350.0)), 1)
        z.update_time = dt.datetime.now()
        touched += 1
    db.commit()
    return {"zones_touched": touched}


def _history(db: Session, zone_ids: list[int], end: dt.date, days: int = 120) -> dict[int, list]:
    since = end - dt.timedelta(days=days)
    rows = (db.query(TzForecastRecord)
            .filter(TzForecastRecord.fc_date >= since, TzForecastRecord.fc_date <= end).all())
    seq = {zid: {} for zid in zone_ids}
    for r in rows:
        seq.setdefault(r.zone_id, {})[r.fc_date] = float(r.chla_q50 or 0)
    return {zid: [v for _, v in sorted(d.items())] for zid, d in seq.items()}


def run_daily(db: Session, date: dt.date | None = None) -> dict:
    date = date or dt.date.today()
    steps = {}

    def step(name, fn):
        run = TzRun(run_date=date, step=name, status="0", started_at=dt.datetime.now(),
                    model_version=MODEL_VERSION)
        db.add(run)
        db.flush()
        try:
            detail = fn(run)
            run.status = "1"
            run.ended_at = dt.datetime.now()
            run.detail = str(detail)[:2000]
            db.commit()
            steps[name] = {"run_id": run.run_id, "status": "1", "detail": detail}
        except Exception as e:
            db.rollback()
            log.exception("step %s failed", name)
            steps[name] = {"status": "2", "error": str(e)}
        return steps[name]

    step("ingest", lambda run: _ingest(db, date))

    zone_ids = [z.zone_id for z in db.query(TzZone).all()]
    if not zone_ids:
        return {"date": date.isoformat(), "steps": steps, "error": "无分区数据（先 seed）"}

    def _fc(run):
        hist = _history(db, zone_ids, date - dt.timedelta(days=1))
        out = run_forecast(db, date, hist)
        return {"zones": len(out)}
    step("forecast", _fc)

    def _solve(run):
        s0 = solve_cp_sat(db, date, "0", "q50", run_id=run.run_id)
        s1 = solve_cp_sat(db, date, "1", "q50", run_id=run.run_id)
        return {"am": s0, "pm": s1}
    step("solve", _solve)

    def _report(run):
        from .evalx import evaluate
        ev = evaluate(db, days=30)
        return {"kpi": ev["kpi"]}
    step("report", _report)

    return {"date": date.isoformat(), "steps": steps}
