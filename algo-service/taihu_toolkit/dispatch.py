"""打捞调度启发式（方案式 9 的降级可运行版，5.3 节）。

目标与式 9 一致：max sum(价值·收藻量 − 成本·航行成本)。
约束：单船同时段单任务；分离站单日产能上限；重点水域（水源地）最小覆盖。
求解：贪心构造（单位净收益排序）+ 局部交换改进。精确 MIP（PuLP/CBC）接口位见
`solve_mip`（安装 pulp 后可用）。
"""

from __future__ import annotations

ALGAE_VALUE = 260.0  # 式 9 藻量价值权重 p（元/吨，演示值：打捞处置的财政价值）
FUEL_COST = 8.0      # 燃油单价（元/L）
MIN_DAY_HOURS = 8.0  # 单船单日有效作业小时（含往返折减）


def _net_gain(boat: dict, zone: dict, quantile: str) -> float:
    tons = min(zone[f"tons_{quantile}"], boat["capacity"])
    sail_h = 2 * zone["dist_km"] / boat["speed"]
    work_h = max(MIN_DAY_HOURS - sail_h, 0.5)
    eff = min(1.0, work_h / MIN_DAY_HOURS)
    gain = ALGAE_VALUE * tons * eff - FUEL_COST * boat["fuel_per_km"] * 2 * zone["dist_km"]
    return gain, tons * eff


def greedy_dispatch(boats: list, zones: dict, stations: dict, quantile: str = "q25",
                    key_zone: str | None = None) -> dict:
    """贪心 + 交换改进。返回 {boat: zone}、收藻量、油耗、分离站占用。

    约束：单船单任务；各分区收藻量不超过预报可收量（tons 按分位数口径扣减）；
    分离站产能上限；重点水域保底。
    """
    station_cap = dict(stations)
    station_load = {k: 0.0 for k in stations}
    zone_left = {z: float(zd[f"tons_{quantile}"]) for z, zd in zones.items()}
    free = [dict(b) for b in boats]
    assign: dict[str, str] = {}
    total_tons = 0.0
    total_fuel = 0.0
    MIN_WORTHWHILE = 5.0  # 低于 5 吨不再派船

    order = []
    if key_zone and key_zone in zones:
        order.append(key_zone)
    order += [z for z in zones if z != (key_zone or "")]

    def candidates():
        cands = []
        for b in free:
            if b["name"] in assign:
                continue
            for z in order:
                if zone_left[z] < MIN_WORTHWHILE:
                    continue
                g, t = _net_gain(b, zones[z], quantile)
                t = min(t, zone_left[z])
                if g > 0 and t >= MIN_WORTHWHILE:
                    cands.append((g, t, b, z))
        cands.sort(key=lambda c: -c[0])
        return cands

    for g, t, b, z in candidates():
        if b["name"] in assign or zone_left[z] < MIN_WORTHWHILE:
            continue
        ok = [k for k, v in station_cap.items() if v - station_load[k] >= t]
        if not ok:
            continue
        st = min(ok, key=lambda k: station_load[k] / station_cap[k])
        station_load[st] += t
        zone_left[z] -= t
        assign[b["name"]] = z
        total_tons += t
        total_fuel += b["fuel_per_km"] * 2 * zones[z]["dist_km"]
        if len(assign) == len(boats):
            break

    return {
        "assignment": assign,
        "collected_tons": round(total_tons, 1),
        "fuel_liters": round(total_fuel, 1),
        "station_load": {k: round(v, 1) for k, v in station_load.items()},
        "zone_left": {k: round(v, 1) for k, v in zone_left.items()},
    }


def solve_mip(boats: list, zones: dict, stations: dict, quantile: str = "q25"):
    """精确 MIP 求解（可选依赖 pulp + CBC）。未安装时抛 RuntimeError。"""
    try:
        import pulp
    except ImportError as e:
        raise RuntimeError("pulp 未安装：pip install pulp") from e
    B = [b["name"] for b in boats]
    Z = list(zones)
    x = pulp.LpVariable.dicts("x", (B, Z), cat="Binary")
    prob = pulp.LpProblem("dispatch", pulp.LpMaximize)
    prob += pulp.lpSum(
        _net_gain(next(b for b in boats if b["name"] == i), zones[j], quantile)[0] * x[i][j]
        for i in B for j in Z
    )
    for i in B:
        prob += pulp.lpSum(x[i][j] for j in Z) <= 1
    cap = sum(stations.values())
    prob += pulp.lpSum(
        _net_gain(next(b for b in boats if b["name"] == i), zones[j], quantile)[1] * x[i][j]
        for i in B for j in Z
    ) <= cap
    for j in Z:  # 分区可收量上限（式 9 约束）
        prob += pulp.lpSum(
            _net_gain(next(b for b in boats if b["name"] == i), zones[j], quantile)[1] * x[i][j]
            for i in B
        ) <= zones[j][f"tons_{quantile}"]
    prob.solve(pulp.PULP_CBC_CMD(msg=0))
    return {
        "assignment": {i: j for i in B for j in Z if x[i][j].value() == 1},
        "collected_tons": round(sum(
            _net_gain(next(b for b in boats if b["name"] == i), zones[j], quantile)[1]
            for i in B for j in Z if x[i][j].value() == 1), 1),
    }


def print_dispatch(result: dict) -> None:
    print("《建议调度单》（演示）")
    print("-" * 46)
    for boat, zone in result["assignment"].items():
        print(f"  {boat}  →  {zone}")
    print("-" * 46)
    print(f"预计收藻 {result['collected_tons']} 吨 ｜ 预计油耗 {result['fuel_liters']} L")
    if "station_load" in result:
        for k, v in result["station_load"].items():
            print(f"  {k} 占用 {v} 吨")
