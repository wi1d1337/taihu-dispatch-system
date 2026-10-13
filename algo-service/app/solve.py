"""派船推荐 v2（CP-SAT 保留改造）：只产 recommend_boats 建议列表，不写调度单。

v1 的 tz_dispatch_order 落单逻辑已下线；建议列表挂 tz_work_order.suggest_boats
（[{boat_id, zone_id, tons}]），片长 H5 接单时预填可改。
模型同 v1（技术实施方案式 9）：max Σ(藻价值·收藻量 − 油价·油耗)·x，
每船至多 1 分区、分区收藻 ≤ 需求量、总量 ≤ 分离站产能、重点分区保底 1 艘；
超时/失败降级贪心（纪律：贪心必须存在）。
"""

from __future__ import annotations

import datetime as dt
import json
import logging
import math

from sqlalchemy.orm import Session

from .models import TzBoat, TzStation, TzZone
from .zones import KEY_ZONE

log = logging.getLogger("algo.solve")

ALGAE_VALUE = 260.0      # 元/吨（演示权重）
FUEL_PRICE = 8.0         # 元/L
DAY_HOURS = 8.0          # 单船单日有效作业小时
SOLVE_TIME_LIMIT_S = 10  # 建议场景时限（演示口径）
SOLVER_NAME_CPSAT = "CP_SAT"
SOLVER_NAME_GREEDY = "GREEDY"


def _zone_dist_km(zone: TzZone) -> float:
    """分区代表点离母港（马山，示意 120.06/31.42）的距离（km，示意口径）。"""
    ring = json.loads(zone.boundary or "[]")
    if not ring:
        return 20.0
    lng = sum(p[0] for p in ring) / len(ring)
    lat = sum(p[1] for p in ring) / len(ring)
    d_lat = (lat - 31.42) * 111.0
    d_lng = (lng - 120.06) * 111.0 * 0.86
    return round(math.hypot(d_lat, d_lng), 1)


def _effective_tons(boat: TzBoat, dist_km: float, avail: float) -> tuple[float, float]:
    """（有效收藻量, 油耗 L）——容量 × 全天作业时长折减，受分区需求量限制。"""
    sail_h = 2 * dist_km / float(boat.speed_kmh or 12)
    eff = max(DAY_HOURS - sail_h, 0.5) / DAY_HOURS
    tons = min(float(boat.capacity_t or 0) * eff, avail)
    fuel = float(boat.fuel_l_km or 0.3) * 2 * dist_km
    return tons, fuel


def recommend_boats(db: Session, demand: dict[int, float],
                    time_limit_s: float = SOLVE_TIME_LIMIT_S) -> dict:
    """对 demand（zone_id -> 需求吨）求推荐派船方案。不写库。

    返回 {solver, objective, boats: [{boat_id, boat_name, zone_id, tons}]}。
    """
    if not demand:
        return {"solver": "SKIP", "objective": 0.0, "boats": [],
                "reason": "无派船需求（无达标分区或需求量为0）"}

    boats = db.query(TzBoat).filter(TzBoat.status == "0").all()
    zones = {z.zone_id: z for z in db.query(TzZone).filter(TzZone.zone_id.in_(list(demand))).all()}
    stations = db.query(TzStation).filter(TzStation.status == "0").all()
    if not boats or not zones:
        return {"solver": "SKIP", "objective": 0.0, "boats": [], "reason": "船只或分区数据缺失"}

    avail = {zid: float(demand.get(zid, 0.0)) for zid in zones}
    dist = {zid: _zone_dist_km(z) for zid, z in zones.items()}
    cap_total = sum(float(s.capacity_t_day or 0) for s in stations)
    key_zone_id = next((zid for zid, z in zones.items() if z.zone_name == KEY_ZONE), None)

    chosen: list[tuple[int, int]] = []
    objective = 0.0
    solver = SOLVER_NAME_CPSAT
    try:
        from ortools.sat.python import cp_model

        m = cp_model.CpModel()
        x, gain, tons_of = {}, {}, {}
        for b in boats:
            for zid in zones:
                t, f = _effective_tons(b, dist[zid], avail[zid])
                g = ALGAE_VALUE * t - FUEL_PRICE * f
                if t <= 0 or g <= 0:
                    continue
                key = (b.boat_id, zid)
                x[key] = m.NewBoolVar(f"x_{b.boat_id}_{zid}")
                gain[key], tons_of[key] = g, t
        if not x:
            raise RuntimeError("无正收益候选")

        keys = list(x)
        for b in boats:
            bks = [k for k in keys if k[0] == b.boat_id]
            if bks:
                m.AddAtMostOne(x[k] for k in bks)
        for zid in zones:
            zks = [k for k in keys if k[1] == zid]
            if zks:
                m.Add(sum(int(round(tons_of[k] * 100)) * x[k] for k in zks)
                      <= int(round(avail[zid] * 100)))
        if cap_total > 0:
            m.Add(sum(int(round(tons_of[k] * 100)) * x[k] for k in keys) <= int(round(cap_total * 100)))
        if key_zone_id is not None:
            kzs = [k for k in keys if k[1] == key_zone_id]
            if kzs:
                m.AddAtLeastOne(x[k] for k in kzs)   # 水源地重点分区保底
        m.Maximize(sum(int(round(gain[k])) * x[k] for k in keys))

        ep = cp_model.CpSolver()
        ep.parameters.max_time_in_seconds = time_limit_s
        status = ep.Solve(m)
        if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
            raise RuntimeError(f"CP-SAT无可行解 status={status}")
        objective = float(sum(ep.Value(x[k]) * gain[k] for k in keys))
        chosen = [k for k in keys if ep.Value(x[k])]
    except Exception as e:  # 降级贪心（超时/失败退启发式）
        log.warning("CP-SAT 降级贪心: %s", e)
        solver = SOLVER_NAME_GREEDY
        zone_left, cap_left, used_key = dict(avail), cap_total, False
        cands = []
        for b in boats:
            for zid in zones:
                t, f = _effective_tons(b, dist[zid], zone_left[zid])
                g = ALGAE_VALUE * t - FUEL_PRICE * f
                if g > 0 and t >= 2.0:
                    cands.append((g, t, b.boat_id, zid, zid == key_zone_id))
        cands.sort(key=lambda c: (-c[4], -c[0]))
        assigned = set()
        for g, t, bid, zid, is_key in cands:
            if bid in assigned or zone_left[zid] < 2.0 or cap_left < 2.0:
                continue
            if is_key and used_key:
                continue
            assigned.add(bid)
            zone_left[zid] -= t
            cap_left -= t
            used_key = used_key or is_key
            chosen.append((bid, zid))
            objective += g

    boat_name = {b.boat_id: b.boat_name for b in boats}
    out = []
    for bid, zid in chosen:
        t, _ = _effective_tons(next(b for b in boats if b.boat_id == bid), dist[zid], avail[zid])
        out.append({"boat_id": bid, "boat_name": boat_name.get(bid), "zone_id": zid,
                    "tons": round(t, 1)})
    return {"solver": solver, "objective": round(objective, 1),
            "generated_at": dt.datetime.now().isoformat(timespec="seconds"), "boats": out}
