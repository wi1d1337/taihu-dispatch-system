"""看板评测：POD/FAR/CSI（口径同 taihu_toolkit.forecast.event_metrics / 方案表3）、
计划 vs 实收偏差、分区统计、Chl-a 趋势。"""

from __future__ import annotations

import datetime as dt

import numpy as np
from sqlalchemy import func
from sqlalchemy.orm import Session

from .models import TzForecastRecord, TzSalvageLog, TzZone


def _event_metrics(y_true, y_pred) -> dict:
    hit = int(((y_pred >= 0.5) & (y_true >= 0.5)).sum())
    miss = int(((y_pred < 0.5) & (y_true >= 0.5)).sum())
    fa = int(((y_pred >= 0.5) & (y_true < 0.5)).sum())
    pod = hit / (hit + miss) if hit + miss else None
    far = fa / (hit + fa) if hit + fa else None
    csi = hit / (hit + miss + fa) if hit + miss + fa else None
    def r(v):
        return round(float(v), 3) if v is not None else None
    return {"POD": r(pod), "FAR": r(far), "CSI": r(csi),
            "hits": hit, "misses": miss, "false_alarms": fa}


def evaluate(db: Session, days: int = 30) -> dict:
    today = dt.date.today()
    since = today - dt.timedelta(days=days)

    zones = {z.zone_id: z.zone_name for z in db.query(TzZone).all()}

    # 预报 vs 实况（事件口径：预报 prob≥0.5 vs 实况当日台账吨数≥10，演示口径）
    fcs = db.query(TzForecastRecord).filter(TzForecastRecord.fc_date >= since).all()
    logs = db.query(TzSalvageLog).filter(TzSalvageLog.log_date >= since).all()
    actual_by_zone_day = {}
    for lg in logs:
        actual_by_zone_day[(lg.zone_id, lg.log_date)] = \
            actual_by_zone_day.get((lg.zone_id, lg.log_date), 0.0) + float(lg.actual_tons or 0)
    y_true, y_pred = [], []
    for f in fcs:
        y_pred.append(float(f.prob or 0))
        actual = actual_by_zone_day.get((f.zone_id, f.fc_date), 0.0)
        y_true.append(1.0 if actual >= 10.0 else 0.0)
    kpi = _event_metrics(np.array(y_true), np.array(y_pred)) if y_true else {"POD": None, "FAR": None, "CSI": None}

    # 计划 vs 实收（日聚合）
    plan_day = dict(
        db.query(TzSalvageLog.log_date, func.sum(TzSalvageLog.actual_tons))
        .filter(TzSalvageLog.log_date >= since).group_by(TzSalvageLog.log_date).all()
    )
    from .models import TzDispatchOrder
    plan_q = dict(
        db.query(TzDispatchOrder.order_date, func.sum(TzDispatchOrder.plan_tons))
        .filter(TzDispatchOrder.order_date >= since).group_by(TzDispatchOrder.order_date).all()
    )
    daily = []
    d = since
    while d <= today:
        p = float(plan_q.get(d) or 0.0)
        a = float(plan_day.get(d) or 0.0)
        daily.append({"date": d.isoformat(), "plan_tons": round(p, 1), "actual_tons": round(a, 1),
                      "abs_dev": round(abs(p - a), 1)})
        d += dt.timedelta(days=1)

    # 分区统计与趋势
    zone_stats = []
    for zid, zname in zones.items():
        t = sum(v for (zz, _), v in actual_by_zone_day.items() if zz == zid)
        zone_stats.append({"zone_id": zid, "zone_name": zname, "actual_tons": round(t, 1)})
    series_dates = sorted({f.fc_date.isoformat() for f in fcs})
    zseries = {zname: [] for zname in zones.values()}
    by_zd = {(f.zone_id, f.fc_date.isoformat()): float(f.chla_q50 or 0) for f in fcs}
    for ds in series_dates:
        for zid, zname in zones.items():
            zseries[zname].append(by_zd.get((zid, ds), 0.0))

    return {"kpi": kpi, "daily": daily[-days:], "zone_stats": zone_stats,
            "history": {"dates": series_dates, "zones": zseries}}
