"""种子数据：重置并生成演示数据（分区/船/站/历史台账/预报/今日调度单）。

历史序列来自 taihu_toolkit.synth（合成数据，非真实观测）；台账与调度单从序列派生，
带可控噪声使 POD/FAR/CSI 与偏差曲线有真实感。
"""

from __future__ import annotations

import datetime as dt

import numpy as np
from sqlalchemy.orm import Session

from .forecast import MODEL_VERSION, run_forecast
from .models import (Base, TzBoat, TzDispatchOrder, TzForecastRecord, TzRun,
                     TzSalvageLog, TzStation, TzZone)
from .solve import solve_cp_sat
from .zones import ZONES, boundary_json

FLEET = [
    ("打捞-01", "打捞", 45.0, 12.0, 0.55), ("打捞-02", "打捞", 45.0, 12.0, 0.55),
    ("机动-03", "机动", 30.0, 18.0, 0.38), ("机动-04", "机动", 30.0, 18.0, 0.38),
    ("小型-05", "小型", 15.0, 20.0, 0.22), ("小型-06", "小型", 15.0, 20.0, 0.22),
    ("小型-07", "小型", 15.0, 20.0, 0.22), ("小型-08", "小型", 15.0, 20.0, 0.22),
]
STATIONS = [("分离站-马山", 120.0), ("分离站-华庄", 90.0), ("分离站-南泉", 60.0)]


def seed(db: Session, days: int = 120) -> dict:
    from taihu_toolkit.synth import synth_taihu

    # 1) 重置业务表（保留表结构；若依系统表不动）
    for t in (TzDispatchOrder, TzSalvageLog, TzForecastRecord, TzRun, TzZone, TzBoat, TzStation):
        db.query(t).delete(synchronize_session=False)
    db.commit()

    # 2) 分区 / 船 / 站
    zone_id = {}
    for z in ZONES:
        row = TzZone(zone_name=z["zone_name"], zone_code=z["zone_code"], area_km2=z["area_km2"],
                     boundary=boundary_json(z))
        db.add(row)
        db.flush()
        zone_id[z["zone_name"]] = row.zone_id
    for name, typ, cap, spd, fuel in FLEET:
        db.add(TzBoat(boat_name=name, boat_type=typ, capacity_t=cap, speed_kmh=spd,
                      fuel_l_km=fuel, status="0", home_port="马山"))
    for name, cap in STATIONS:
        db.add(TzStation(station_name=name, capacity_t_day=cap, status="0"))
    db.commit()

    # 3) 合成历史（每分区一条 Chl-a 序列）+ 派生预报/台账/调度单
    # 日期重映射：synth 索引从 2023-01-01 起，此处平移为"以昨天结尾的最近 days 天"
    rng = np.random.default_rng(42)
    df = synth_taihu(days=days, seed=42)
    today = dt.date.today()
    dates = [today - dt.timedelta(days=days - i) for i in range(days)]  # dates[-1] = 昨天
    history = {zid: [] for zid in zone_id.values()}
    boats = [b.boat_id for b in db.query(TzBoat).all()]
    stats = {"zones": len(ZONES), "boats": len(FLEET), "stations": len(STATIONS),
             "days": len(dates), "logs": 0, "orders": 0}

    # 3a) 历史预报记录（逐日）：chla 分位数 + 概率
    chla_by_zone_day = {}
    for zname, sub in df.groupby("zone"):
        zid = zone_id[zname]
        for d, row in zip(dates, sub.itertuples()):
            chla_by_zone_day[(zid, d)] = float(row.chla)
            history[zid].append(float(row.chla))
    thr_by_zone = {zid: float(np.quantile(np.array(seq), 0.85))
                   for zid, seq in history.items()}

    # 3b) 历史台账 + 历史调度单（最后 3 天留空给演示操作）
    for n, d in enumerate(dates):
        if d >= today - dt.timedelta(days=3):
            continue
        for zid in zone_id.values():
            c = chla_by_zone_day.get((zid, d), 5.0)
            intensity = max(0.0, c - 0.6 * thr_by_zone[zid]) / max(thr_by_zone[zid], 1.0)
            base = 1.0 + list(zone_id.values()).index(zid) * 0.1
            plan = round(min(90.0 * intensity * base, 80.0), 1)
            if plan < 3:
                continue
            half = "0" if rng.random() < 0.5 else "1"
            bid = int(boats[rng.integers(0, len(boats))])
            # 先定实况（当日是否出船/出藻），再生成与之联动的预报概率——保证指标非平凡但可信
            dispatched = rng.random() < 0.75
            actual = max(0.0, plan * float(np.clip(rng.normal(0.92, 0.25), 0.3, 1.4))) if dispatched else 0.0
            if actual >= 10.0:
                prob = float(np.clip(rng.normal(0.78, 0.10), 0.05, 0.97))
            else:
                prob = float(np.clip(rng.normal(0.22, 0.12), 0.02, 0.60))
            db.add(TzForecastRecord(zone_id=zid, fc_date=d, horizon_h=72, prob=round(prob, 2),
                                    chla_q25=round(c * 0.8, 1), chla_q50=round(c, 1), chla_q75=round(c * 1.2, 1)))
            if dispatched:
                db.add(TzDispatchOrder(order_date=d, half_day=half, boat_id=bid, zone_id=zid,
                                       station_id=None, plan_tons=plan, status="3",
                                       actual_tons=round(actual, 1), solver="CP_SAT",
                                       objective=None, model_version=MODEL_VERSION))
                db.add(TzSalvageLog(order_id=None, boat_id=bid, zone_id=zid, log_date=d,
                                    half_day=half, actual_tons=round(actual, 1),
                                    fuel_liters=round(rng.uniform(20, 90), 1), crew=int(rng.integers(3, 9))))
                stats["logs"] += 1
                stats["orders"] += 1

    # 4) 今日预报（写 tz_forecast_record + 更新 tz_zone 概率吨位）
    run_forecast(db, today, history)
    # 5) 今日两个时段的调度单（待确认状态）
    r1 = TzRun(run_date=today, step="seed", status="1", started_at=dt.datetime.now(),
               ended_at=dt.datetime.now(), model_version=MODEL_VERSION)
    db.add(r1)
    db.flush()
    for half in ("0", "1"):
        s1 = solve_cp_sat(db, today, half, "q50", run_id=r1.run_id)
        stats["orders"] += s1.get("orders", 0)
    db.commit()
    stats["today"] = today.isoformat()
    return stats
