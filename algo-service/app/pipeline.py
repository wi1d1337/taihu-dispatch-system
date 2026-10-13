"""每日管线 v2：ingest → assess → auto-dispatch → report → autotune。

各步写 tz_run（step/status/started/ended/model_version/detail）；
单步失败不阻断后续步（autotune 失败尤其只记不阻断）。
生产由 cron 触发（容器内 supercronic curl /api/v1/pipeline/run-daily），
演示由管理台按钮触发。
"""

from __future__ import annotations

import datetime as dt
import logging

import numpy as np
from sqlalchemy.orm import Session

from .assess import MODEL_VERSION, assess_day
from .dispatch2 import auto_dispatch
from .models import TzChlaObs, TzRun, TzZone

log = logging.getLogger("algo.pipeline")


def _ingest(db: Session, date: dt.date) -> dict:
    """演示口径"数据接入"：以小噪声从最近实测推进各分区 Chl-a（幂等：当日已有则跳过）。"""
    rng = np.random.default_rng(int(date.strftime("%Y%m%d")))
    touched = skipped = 0
    for z in db.query(TzZone).all():
        exists = (db.query(TzChlaObs)
                  .filter(TzChlaObs.zone_id == z.zone_id, TzChlaObs.obs_date == date).count())
        if exists:
            skipped += 1
            continue
        last = (db.query(TzChlaObs)
                .filter(TzChlaObs.zone_id == z.zone_id, TzChlaObs.obs_date < date)
                .order_by(TzChlaObs.obs_date.desc()).first())
        cur = float(last.chla) if last else float(z.chla or 8.0)
        db.add(TzChlaObs(zone_id=z.zone_id, obs_date=date,
                         chla=round(float(np.clip(cur * rng.normal(1.02, 0.08), 0.5, 350.0)), 1)))
        z.chla = round(float(np.clip(cur * rng.normal(1.02, 0.08), 0.5, 350.0)), 1)
        z.update_time = dt.datetime.now()
        touched += 1
    db.commit()
    return {"zones_touched": touched, "zones_skipped_exists": skipped}


def run_daily(db: Session, date: dt.date | None = None) -> dict:
    date = date or dt.date.today()
    steps: dict[str, dict] = {}

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

    if not db.query(TzZone).count():
        return {"date": date.isoformat(), "steps": steps, "error": "无分区数据（先 seed）"}

    def _assess(run):
        out = assess_day(db, date)
        return {"zones": len(out),
                "levels": {a["zone_name"]: a["level"] for a in out}}
    step("assess", _assess)

    def _dispatch(run):
        out = auto_dispatch(db, date)
        return {"created": len(out.get("created", [])),
                "skipped": len(out.get("skipped", [])), "solver": out.get("solver")}
    step("auto-dispatch", _dispatch)

    def _report(run):
        from .evalx import daily_report
        rep = daily_report(db, date)
        return {"orders_total": rep["orders_total"], "tons_total": rep["tons_total"],
                "open_count": rep["open_count"]}
    step("report", _report)

    def _autotune(run):
        from .autotune import autotune_run
        out = autotune_run(db, date)   # 失败抛错只记本步，不阻断
        keys = ("applied", "suggested", "skipped", "reason", "changes")
        return {k: out[k] for k in keys if k in out}
    step("autotune", _autotune)

    return {"date": date.isoformat(), "steps": steps}
