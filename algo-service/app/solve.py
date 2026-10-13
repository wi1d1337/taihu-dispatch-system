"""调度求解：CP-SAT 精确求解 + 贪心降级（对应技术实施方案式 9 与 5.3 节）。

模型（与 taihu_toolkit.dispatch 同构，适配数据库实体）：
  max  Σ (p·q_ij·eff_ij − c·fuel_ij) · x_ij
  s.t. 每船至多 1 分区；分区收藻量 ≤ 预报可收量；总收藻 ≤ 分离站产能；
       重点分区贡湖保底 1 艘；维护中船不参与。
演示口径时限 30 秒；超时/失败自动降级贪心。
"""

from __future__ import annotations

import datetime as dt
import logging

from sqlalchemy.orm import Session

from .models import TzBoat, TzDispatchOrder, TzRun, TzStation, TzZone
from .zones import KEY_ZONE

log = logging.getLogger("algo.solve")

ALGAE_VALUE = 260.0     # 元/吨（演示权重）
FUEL_PRICE = 8.0        # 元/L
DAY_HOURS = 8.0         # 单船单日有效作业小时
HALF_DAY_HOURS = 4.0
SOLVE_TIME_LIMIT_S = 30  # 演示口径；生产 300s
SOLVER_NAME_CPSAT = "CP_SAT"
SOLVER_NAME_GREEDY = "GREEDY"


def _zone_dist_km(zone: TzZone) -> float:
    """分区代表点离母港（马山，示意 120.06/31.42）的距离（km，示意口径）。"""
    import json
    import math

    ring = json.loads(zone.boundary or "[]")
    if not ring:
        return 20.0
    lng = sum(p[0] for p in ring) / len(ring)
    lat = sum(p[1] for p in ring) / len(ring)
    d_lat = (lat - 31.42) * 111.0
    d_lng = (lng - 120.06) * 111.0 * 0.86
    return round(math.hypot(d_lat, d_lng), 1)


def _effective_tons(boat: TzBoat, dist_km: float, half_day: bool, avail: float) -> tuple[float, float]:
    """返回（有效收藻量, 油耗 L）——容量 × 作业时长折减，受分区可收量限制。"""
    hours = HALF_DAY_HOURS if half_day else DAY_HOURS
    sail_h = 2 * dist_km / float(boat.speed_kmh or 12)
    eff = max(hours - sail_h, 0.5) / hours
    tons = min(float(boat.capacity_t or 0) * (0.5 if half_day else 1.0) * eff, avail)
    fuel = float(boat.fuel_l_km or 0.3) * 2 * dist_km
    return tons, fuel


def _load_inputs(db: Session, quantile: str):
    boats = db.query(TzBoat).filter(TzBoat.status == "0").all()
    zones = db.query(TzZone).all()
    stations = db.query(TzStation).filter(TzStation.status == "0").all()
    tons_attr = TzZone.tons_q25 if quantile == "q25" else TzZone.tons_q50
    return boats, zones, stations, tons_attr


def solve_cp_sat(db: Session, order_date: dt.date, half_day: str, quantile: str = "q50",
                 run_id: int | None = None) -> dict:
    """求解并写入 tz_dispatch_order（先清除该日该时段旧单）。返回摘要。"""
    boats, zones, stations, tons_attr = _load_inputs(db, quantile)
    if not boats or not zones:
        return {"orders": 0, "objective": 0.0, "solver": "SKIP", "reason": "船只或分区数据缺失"}

    key_zone_id = next((z.zone_id for z in zones if z.zone_name == KEY_ZONE), zones[0].zone_id)
    avail = {z.zone_id: float(getattr(z, "tons_" + quantile) or 0) for z in zones}
    dist = {z.zone_id: _zone_dist_km(z) for z in zones}
    cap_total = sum(float(s.capacity_t_day or 0) for s in stations) * (0.5 if half_day == "1" else 1.0)

    # 清旧单（同日同时段重新求解幂等）
    db.query(TzDispatchOrder).filter(
        TzDispatchOrder.order_date == order_date, TzDispatchOrder.half_day == half_day
    ).delete(synchronize_session=False)

    assign: list[tuple[int, int, int, float]] = []  # boat, zone, station, tons
    objective = 0.0
    solver = SOLVER_NAME_CPSAT
    try:
        from ortools.sat.python import cp_model

        m = cp_model.CpModel()
        x = {}
        gain = {}
        tons_of = {}
        fuel_of = {}
        for b in boats:
            for z in zones:
                t, f = _effective_tons(b, dist[z.zone_id], half_day == "1", avail[z.zone_id])
                if t <= 0:
                    continue
                g = ALGAE_VALUE * t - FUEL_PRICE * f
                if g <= 0:
                    continue
                key = (b.boat_id, z.zone_id)
                x[key] = m.NewBoolVar(f"x_{b.boat_id}_{z.zone_id}")
                gain[key] = g
                tons_of[key] = t
                fuel_of[key] = f
        if not x:
            raise RuntimeError("无正收益候选")

        keys = list(x)
        for b in boats:
            bks = [k for k in keys if k[0] == b.boat_id]
            if bks:
                m.AddAtMostOne(x[k] for k in bks)
        for z in zones:
            zks = [k for k in keys if k[1] == z.zone_id]
            if zks:
                m.Add(sum(int(round(tons_of[k] * 100)) * x[k] for k in zks)
                      <= int(round(avail[z.zone_id] * 100)))
        m.Add(sum(int(round(tons_of[k] * 100)) * x[k] for k in keys) <= int(round(cap_total * 100)))
        kzs = [k for k in keys if k[1] == key_zone_id]
        if kzs:
            m.AddAtLeastOne(x[k] for k in kzs)  # 重点水域保底
        m.Maximize(sum(int(round(gain[k])) * x[k] for k in keys))

        solver_ep = cp_model.CpSolver()
        solver_ep.parameters.max_time_in_seconds = SOLVE_TIME_LIMIT_S
        status = solver_ep.Solve(m)
        if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
            raise RuntimeError(f"CP-SAT无可行解 status={status}")
        objective = float(sum(solver_ep.Value(x[k]) * gain[k] for k in keys))
        chosen = [k for k in keys if solver_ep.Value(x[k])]
    except Exception as e:  # 降级贪心（技术方案 5.3：超时/失败退启发式）
        log.warning("CP-SAT 降级贪心: %s", e)
        solver = SOLVER_NAME_GREEDY
        chosen = []
        zone_left = dict(avail)
        cap_left = cap_total
        used_key_zone = False
        cands = []
        for b in boats:
            for z in zones:
                t, f = _effective_tons(b, dist[z.zone_id], half_day == "1", zone_left[z.zone_id])
                g = ALGAE_VALUE * t - FUEL_PRICE * f
                if g > 0 and t >= 2.0:
                    cands.append((g, t, f, b.boat_id, z.zone_id, z.zone_id == key_zone_id))
        cands.sort(key=lambda c: (-c[5], -c[0]))
        assigned_boats = set()
        for g, t, f, bid, zid, is_key in cands:
            if bid in assigned_boats or zone_left[zid] < 2.0 or cap_left < 2.0:
                continue
            if is_key and used_key_zone:
                continue
            assigned_boats.add(bid)
            zone_left[zid] -= t
            cap_left -= t
            used_key_zone = used_key_zone or is_key
            chosen.append((bid, zid))
            objective += g

    # 分离站就近分配（演示：负载均衡）
    station_ids = [s.station_id for s in stations] or [None]
    zone_load = {s.station_id: 0.0 for s in stations}
    for bid, zid in chosen:
        t, _ = _effective_tons(next(b for b in boats if b.boat_id == bid), dist[zid],
                               half_day == "1", avail[zid])
        if station_ids != [None]:
            st = min(zone_load, key=lambda k: zone_load[k])
            zone_load[st] += t
            sid = st
        else:
            sid = None
        db.add(TzDispatchOrder(order_date=order_date, half_day=half_day, boat_id=bid, zone_id=zid,
                               station_id=sid, plan_tons=round(t, 1), status="0", solver=solver,
                               objective=round(objective, 1), model_version="cp-sat-taihu-v1",
                               run_id=run_id))
        assign.append((bid, zid, sid or 0, round(t, 1)))

    db.commit()
    return {"orders": len(assign), "objective": round(objective, 1), "solver": solver,
            "detail": [{"boat": b, "zone": z, "station": s, "tons": t} for b, z, s, t in assign]}
