"""考核看板 / 日报 / 研判回验（v2 重写）。

- evaluate()          → GET /dashboard/eval：响应时长分布、办结率、超时督办、日生日清、分区吨数
- daily_report()      → GET /daily/report：当日工单汇总+吨数+未办结清单
- assess_accuracy()   → GET /assess/accuracy：历史研判等级 vs 实际等级（合成事实口径）

回验事实口径：实际 = 研判当日分区实测 Chl-a（tz_chla_obs，seed 合成事实）；
研判计算只用当日之前的数据（等效"提前一天"预报），阈值取该条研判 basis 内的参数快照。
"""

from __future__ import annotations

import datetime as dt
import json

import numpy as np
from sqlalchemy.orm import Session

from .assess import level_of_value
from .models import TzAssessment, TzChlaObs, TzOrderLog, TzWorkOrder, TzZone, TzWorker
from .params import get_params

STATUS_NAMES = {"0": "待接单", "1": "已接单", "2": "进行中", "3": "待核查",
                "4": "已办结", "5": "已退回", "6": "已撤销"}


def _hours(a: dt.datetime, b: dt.datetime) -> float | None:
    if a is None or b is None:
        return None
    delta = (b - a).total_seconds() / 3600.0
    return round(delta, 2) if delta >= 0 else None


def _dur_stats(samples: list[float]) -> dict:
    if not samples:
        return {"n": 0, "avg": None, "p50": None, "p90": None, "max": None, "samples": []}
    arr = np.array(samples)
    return {"n": len(samples), "avg": round(float(arr.mean()), 2),
            "p50": round(float(np.percentile(arr, 50)), 2),
            "p90": round(float(np.percentile(arr, 90)), 2),
            "max": round(float(arr.max()), 2), "samples": samples}


def evaluate(db: Session, days: int = 30) -> dict:
    today = dt.date.today()
    since = today - dt.timedelta(days=days - 1)
    zones = {z.zone_id: z.zone_name for z in db.query(TzZone).all()}
    workers = {w.worker_id: w.worker_name for w in db.query(TzWorker).all()}

    orders = [o for o in db.query(TzWorkOrder).all()
              if o.dispatched_at and o.dispatched_at.date() >= since]
    active = [o for o in orders if o.status != "6"]

    # 响应时长分布：派单→接单 / 接单→到场 / 提交→办结
    d_accept = [h for o in orders if (h := _hours(o.dispatched_at, o.accepted_at)) is not None]
    d_arrive = [h for o in orders if (h := _hours(o.accepted_at, o.arrived_at)) is not None]
    d_close = [h for o in orders if (h := _hours(o.submitted_at, o.checked_at)) is not None]

    # 办结率（剔除已撤销）
    done = sum(1 for o in active if o.status == "4")

    # 超时督办次数（窗口内工单的 催办/升级督办 日志）
    order_ids = [o.order_id for o in orders]
    supervise_cnt = 0
    if order_ids:
        supervise_cnt = (db.query(TzOrderLog)
                         .filter(TzOrderLog.order_id.in_(order_ids),
                                 TzOrderLog.action.in_(["催办", "升级督办"])).count())

    # 日生日清率：当日派单当日办结
    daily = []
    by_day: dict[dt.date, list] = {}
    for o in orders:
        if o.status == "6":
            continue
        by_day.setdefault(o.dispatched_at.date(), []).append(o)
    for d in sorted(by_day):
        rows = by_day[d]
        cleared = sum(1 for o in rows if o.status == "4" and o.checked_at
                      and o.checked_at.date() == d)
        daily.append({"date": d.isoformat(), "dispatched": len(rows),
                      "cleared_same_day": cleared,
                      "rate": round(cleared / len(rows) * 100, 1) if rows else None})
    tot_disp = sum(x["dispatched"] for x in daily)
    tot_clear = sum(x["cleared_same_day"] for x in daily)

    # 分区吨数（已提交的实捞吨数）
    zone_tons = {zid: 0.0 for zid in zones}
    for o in active:
        if o.actual_tons:
            zone_tons[o.zone_id] = zone_tons.get(o.zone_id, 0.0) + float(o.actual_tons)

    return {
        "window_days": days, "since": since.isoformat(), "until": today.isoformat(),
        "orders_total": len(orders),
        "response_hours": {
            "dispatch_to_accept": _dur_stats(d_accept),
            "accept_to_arrive": _dur_stats(d_arrive),
            "submit_to_close": _dur_stats(d_close),
        },
        "close_rate": round(done / len(active) * 100, 1) if active else None,
        "supervise_count": supervise_cnt,
        "same_day_clear": {
            "overall_rate": round(tot_clear / tot_disp * 100, 1) if tot_disp else None,
            "daily": daily,
        },
        "zone_tons": [{"zone_id": zid, "zone_name": zones.get(zid, str(zid)),
                       "tons": round(v, 1)} for zid, v in sorted(zone_tons.items())],
    }


def daily_report(db: Session, date: dt.date) -> dict:
    prefix = f"TZ{date.strftime('%Y%m%d')}-"
    zones = {z.zone_id: z.zone_name for z in db.query(TzZone).all()}
    workers = {w.worker_id: w.worker_name for w in db.query(TzWorker).all()}
    orders = db.query(TzWorkOrder).filter(TzWorkOrder.order_no.like(prefix + "%")).all()
    now = dt.datetime.now()

    status_counts = {}
    for o in orders:
        status_counts[o.status] = status_counts.get(o.status, 0) + 1
    tons_total = round(sum(float(o.actual_tons or 0) for o in orders), 1)
    tons_by_zone = {}
    for o in orders:
        if o.actual_tons:
            tons_by_zone[zones.get(o.zone_id, str(o.zone_id))] = \
                round(tons_by_zone.get(zones.get(o.zone_id, str(o.zone_id)), 0.0)
                      + float(o.actual_tons), 1)

    open_orders = []
    for o in orders:
        if o.status in ("4", "6"):
            continue
        overdue = None
        if o.deadline:
            overdue = round((now - o.deadline).total_seconds() / 3600.0, 1)
            if overdue < 0:
                overdue = None
        open_orders.append({
            "order_id": o.order_id, "order_no": o.order_no,
            "zone_name": zones.get(o.zone_id, str(o.zone_id)), "level": int(o.level),
            "status": o.status, "status_name": STATUS_NAMES.get(o.status, o.status),
            "leader_name": workers.get(o.leader_id),
            "deadline": o.deadline.isoformat() if o.deadline else None,
            "overdue_hours": overdue,
            "dispatched_at": o.dispatched_at.isoformat() if o.dispatched_at else None,
        })

    return {"date": date.isoformat(), "orders_total": len(orders),
            "status_counts": status_counts,
            "status_names": {k: STATUS_NAMES.get(k, k) for k in status_counts},
            "tons_total": tons_total, "tons_by_zone": tons_by_zone,
            "open_count": len(open_orders), "open_orders": open_orders}


def assess_accuracy(db: Session, days: int = 30) -> dict:
    today = dt.date.today()
    since = today - dt.timedelta(days=days)
    zones = {z.zone_id: z.zone_name for z in db.query(TzZone).all()}
    p_now = get_params(db)

    rows = (db.query(TzAssessment)
            .filter(TzAssessment.assess_date >= since,
                    TzAssessment.assess_date < today)   # 今日实际未入库，不计样本
            .order_by(TzAssessment.assess_date).all())
    obs = {(o.zone_id, o.obs_date): float(o.chla) for o in db.query(TzChlaObs).all()
           if o.obs_date >= since}

    matrix = {(i, j): 0 for i in range(4) for j in range(4)}   # [预测][实际]
    hit = over = under = 0
    ev_hit = ev_fa = ev_miss = 0
    zone_stat = {}
    for a in rows:
        actual = obs.get((a.zone_id, a.assess_date))
        if actual is None:
            continue
        p = p_now
        try:    # 阈值用该条研判当时的参数快照（口径可回溯）
            snap = json.loads(a.basis or "{}").get("params")
            if snap and "assess.level1_chla" in snap:
                p = {k: float(v) for k, v in snap.items()}
        except Exception:
            pass
        pred_lv, act_lv = int(a.level or 0), level_of_value(actual, p)
        matrix[(pred_lv, act_lv)] += 1
        hit += int(pred_lv == act_lv)
        over += int(pred_lv > act_lv)     # 误报（报重了）
        under += int(pred_lv < act_lv)    # 漏报（报轻了）
        pe, ae = pred_lv >= 2, act_lv >= 2
        ev_hit += int(pe and ae)
        ev_fa += int(pe and not ae)
        ev_miss += int(not pe and ae)
        z = zone_stat.setdefault(zones.get(a.zone_id, str(a.zone_id)),
                                 {"samples": 0, "hits": 0})
        z["samples"] += 1
        z["hits"] += int(pred_lv == act_lv)

    n = hit + over + under
    lvl_names = ["0无需干预", "1关注", "2较大", "3重大"]

    def rr(v):
        return round(v, 3) if v is not None else None
    return {
        "days": days, "samples": n,
        "fact_basis": "实际=研判当日分区实测Chl-a(tz_chla_obs合成事实)；研判仅用当日之前数据(等效提前一天)，阈值取研判当时参数快照",
        "hit_rate": round(hit / n * 100, 1) if n else None,
        "hits": hit, "false_alarms": over, "misses": under,
        "event_metrics": {   # 事件口径：level≥2（达到自动派单档）
            "pod": rr(ev_hit / (ev_hit + ev_miss)) if ev_hit + ev_miss else None,
            "far": rr(ev_fa / (ev_hit + ev_fa)) if ev_hit + ev_fa else None,
            "csi": rr(ev_hit / (ev_hit + ev_miss + ev_fa)) if ev_hit + ev_miss + ev_fa else None,
            "event_hits": ev_hit, "event_false_alarms": ev_fa, "event_misses": ev_miss,
        },
        "matrix": {"levels": lvl_names,
                   "counts": [[matrix[(i, j)] for j in range(4)] for i in range(4)],
                   "row": "预测等级", "col": "实际等级"},
        "by_zone": [{"zone_name": k, "samples": v["samples"],
                     "hit_rate": round(v["hits"] / v["samples"] * 100, 1)}
                    for k, v in sorted(zone_stat.items())],
    }
