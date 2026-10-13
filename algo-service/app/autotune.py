"""自动调参（模型自我递归改进）v2：研判 → 回验差距 → 有界调参 → 明日研判生效。

事实口径（写进每次 evidence）：实际 = 次日该分区 Chl-a 实测（tz_chla_obs，seed 合成事实）
按同套候选阈值定级；工单吨数口径作辅证（演示环境两口径由同一合成序列派生）。

流程（契约 §3 /autotune/run，兼作 pipeline 末步）：
  1) 取近 autotune.window_days 天 (features, 次日实测) 对；
  2) 指标：等级命中率 / 事件 POD/FAR（level≥2 为事件）/ 预测值 MAE；
  3) 候选网格：{yoy_weight, level1/2/3_chla} 各取 当前/±max_step_pct%（clip 到 min/max），
     其余参数固定当前值；全组合回测（阈值组合须保持 level1<level2<level3）；
  4) 最优组合命中率提升 ≥ min_gain(pp) → mode=2 直接生效（apply_param version+1 +
     tz_param_log trigger=2）；mode=1 只写 trigger=3 建议；无改进 → 记证据跳过（正常路径）。

窗口特征与参数解耦（assess.extract_features 复用），网格回测只重放 decide()。
"""

from __future__ import annotations

import datetime as dt
import itertools
import json
import logging

from sqlalchemy.orm import Session

from .assess import decide, extract_features, level_of_value, load_series
from .models import TzAlgoParam, TzParamLog, TzRun, TzZone
from .params import (TRIGGER_AUTO, TRIGGER_SUGGEST, apply_param, get_params,
                     param_history)

log = logging.getLogger("algo.autotune")

TUNED_KEYS = ["assess.yoy_weight", "assess.level1_chla", "assess.level2_chla", "assess.level3_chla"]
OPERATOR = "autotune"
MIN_PAIRS = 10


def _collect_pairs(db: Session, window_days: int, end: dt.date) -> tuple[list, int]:
    """近 window_days 天 (features, 当日实测 Chl-a) 对（end 含当天）。

    特征只允许用 d 之前的数据（与线上研判同口径：研判发生在当日实况入库前）。
    """
    p = get_params(db)
    zone_ids = [z.zone_id for z in db.query(TzZone).all()]
    series = load_series(db, end + dt.timedelta(days=1), lookback_days=500)
    d0 = end - dt.timedelta(days=window_days - 1)
    pairs = []
    for zid in zone_ids:
        s = series.get(zid, {})
        for n in range(window_days):
            d = d0 + dt.timedelta(days=n)
            if d not in s:            # 当日实测缺失（实况未入库）不计样本
                continue
            actual = s.pop(d)
            pairs.append((extract_features(s, d, p), actual, zid, d))
            s[d] = actual
    return pairs, len(zone_ids)


def _metrics(pairs: list, p: dict) -> dict:
    """回测指标（纯函数）：等级命中率 / 事件 POD/FAR/CSI / 预测 MAE。"""
    hits = ev_hit = ev_fa = ev_miss = 0
    mae = 0.0
    for f, actual, _, _ in pairs:
        r = decide(f, p)
        act_level = level_of_value(actual, p)
        hits += int(r["level"] == act_level)
        pe, ae = r["level"] >= 2, act_level >= 2
        ev_hit += int(pe and ae)
        ev_fa += int(pe and not ae)
        ev_miss += int(not pe and ae)
        mae += abs(r["predicted"] - actual)
    n = len(pairs)

    def rr(v):
        return round(v, 3) if v is not None else None
    return {"samples": n, "hit_rate": round(hits / n * 100, 1) if n else None,
            "hits": hits,
            "pod": rr(ev_hit / (ev_hit + ev_miss)) if ev_hit + ev_miss else None,
            "far": rr(ev_fa / (ev_hit + ev_fa)) if ev_hit + ev_fa else None,
            "csi": rr(ev_hit / (ev_hit + ev_miss + ev_fa)) if ev_hit + ev_miss + ev_fa else None,
            "mae": round(mae / n, 2) if n else None}


def _candidate_grid(rows: dict[str, TzAlgoParam], p: dict, step_pct: float) -> dict[str, list[float]]:
    """每个调参键的候选值：当前 / ±step%（clip 到注册表 min/max，去重排序）。"""
    grid = {}
    for key in TUNED_KEYS:
        cur = float(p[key])
        lo, hi = float(rows[key].min_value), float(rows[key].max_value)
        step = cur * step_pct / 100.0
        cands = {round(min(max(v, lo), hi), 4) for v in (cur - step, cur, cur + step)}
        grid[key] = sorted(cands)
    return grid


def _fmt_change(c: dict) -> str:
    return "%s %g→%g" % (c["param_key"], c["old"], c["new"])


def autotune_run(db: Session, date: dt.date | None = None) -> dict:
    """执行一轮自动调参。结果写 tz_run(step=autotune) 留痕（含无改进路径）。"""
    date = date or dt.date.today()
    now = dt.datetime.now()
    p = get_params(db)
    rows = {r.param_key: r for r in db.query(TzAlgoParam).all()}

    def finish(summary: dict) -> dict:
        db.add(TzRun(run_date=date, step="autotune", status="1", started_at=now,
                     ended_at=dt.datetime.now(), model_version="autotune-v1",
                     detail=json.dumps(summary, ensure_ascii=False)[:2000]))
        db.commit()
        return summary

    if int(p.get("autotune.enabled", 0)) != 1:
        return finish({"skipped": True, "reason": "autotune.enabled=0，自动调参关闭"})

    window = int(p["autotune.window_days"])
    min_gain = float(p["autotune.min_gain"])
    step_pct = float(p["autotune.max_step_pct"])
    mode = int(p["autotune.mode"])
    pairs, zone_count = _collect_pairs(db, window, date - dt.timedelta(days=1))
    if len(pairs) < MIN_PAIRS:
        return finish({"skipped": True,
                       "reason": "窗口内样本不足（%d<%d，需先积累研判与实况）" % (len(pairs), MIN_PAIRS)})

    baseline = _metrics(pairs, p)
    grid = _candidate_grid(rows, p, step_pct)

    # 全组合回测；阈值须保持 level1<level2<level3
    best = {"gain": -1.0, "params": None, "metrics": None, "n_changed": 99}
    top = []
    for combo in itertools.product(*(grid[k] for k in TUNED_KEYS)):
        cand = dict(p)
        cand.update(dict(zip(TUNED_KEYS, combo)))
        if not (cand["assess.level1_chla"] < cand["assess.level2_chla"] < cand["assess.level3_chla"]):
            continue
        m = _metrics(pairs, cand)
        top.append({"params": dict(zip(TUNED_KEYS, combo)),
                    "hit_rate": m["hit_rate"], "mae": m["mae"]})
        gain = round((m["hit_rate"] or 0.0) - (baseline["hit_rate"] or 0.0), 2)
        n_changed = sum(1 for k in TUNED_KEYS if abs(cand[k] - float(p[k])) > 1e-9)
        if gain >= 0 and (gain > best["gain"] + 1e-9
                          or (abs(gain - best["gain"]) <= 1e-9 and n_changed < best["n_changed"])):
            best = {"gain": gain, "params": cand, "metrics": m, "n_changed": n_changed}
    top.sort(key=lambda t: -(t["hit_rate"] or 0))

    common_ev = {
        "window_days": window, "samples": len(pairs), "zones": zone_count,
        "fact_basis": "实际=次日该分区Chl-a实测(tz_chla_obs合成事实)按同套候选阈值定级；工单吨数口径由同一合成序列派生",
        "baseline": baseline, "top_candidates": top[:5], "min_gain_pp": min_gain,
    }

    if best["params"] is None or best["gain"] < min_gain:
        gain_txt = best["gain"] if best["params"] is not None else 0
        reason = ("近%d天%d样本回测：最优候选命中率提升 %spp < min_gain %gpp，不调整（正常路径）"
                  % (window, len(pairs), gain_txt, min_gain))
        return finish({"skipped": True, "reason": reason, **common_ev})

    changes = [{"param_key": k, "old": float(p[k]), "new": float(best["params"][k])}
               for k in TUNED_KEYS if abs(best["params"][k] - float(p[k])) > 1e-9]
    reason = ("回测显示 %s 后%d天等级命中率 %s%%→%s%%（+%gpp≥%gpp），MAE %s→%s"
              % ("，".join(_fmt_change(c) for c in changes), window,
                 baseline["hit_rate"], best["metrics"]["hit_rate"],
                 best["gain"], min_gain, baseline["mae"], best["metrics"]["mae"]))
    evidence = {**common_ev, "candidate": best["metrics"], "gain_pp": best["gain"],
                "changes": changes, "reason": reason, "mode": mode}

    if mode == 2:   # 自动生效：逐键 apply_param（min/max 校验 + version+1 + trigger=2 日志）
        applied = [apply_param(db, c["param_key"], c["new"], OPERATOR, TRIGGER_AUTO, evidence)
                   for c in changes]
        return finish({"applied": True, "gain_pp": best["gain"], "changes": changes,
                       "applied_params": applied, "reason": reason, **common_ev})

    # mode=1 建议：只写 tz_param_log(trigger=3)，不生效（版本不动）
    for c in changes:
        row = rows[c["param_key"]]
        db.add(TzParamLog(param_key=c["param_key"], old_value=float(row.param_value),
                          new_value=c["new"], old_version=int(row.version or 1),
                          new_version=int(row.version or 1), trigger=TRIGGER_SUGGEST,
                          evidence=json.dumps(evidence, ensure_ascii=False),
                          operator=OPERATOR, created_at=dt.datetime.now()))
    db.commit()
    return finish({"applied": False, "suggested": True, "gain_pp": best["gain"],
                   "changes": changes,
                   "reason": reason + "（mode=1 建议模式，待人工确认后生效）", **common_ev})


def autotune_history(db: Session) -> dict:
    """参数值随时间曲线（tz_param_log 重建）+ 调整证据 + 当日回测指标。"""
    p = get_params(db)
    logs = param_history(db, limit=500)
    asc = sorted([l for l in logs if l["old_value"] is not None and l["param_key"] in TUNED_KEYS],
                 key=lambda l: (l["created_at"] or "", l["log_id"]))

    curves: dict[str, list[dict]] = {}
    for k in TUNED_KEYS:
        pts = [{"ts": l["created_at"], "value": l["old_value"], "trigger": l["trigger"]}
               for l in asc if l["param_key"] == k]
        pts.append({"ts": dt.datetime.now().isoformat(timespec="seconds"),
                    "value": float(p[k]), "trigger": "now"})
        curves[k] = pts

    last_run = (db.query(TzRun).filter(TzRun.step == "autotune")
                .order_by(TzRun.run_id.desc()).first())
    last_detail = None
    if last_run and last_run.detail:
        try:
            last_detail = json.loads(last_run.detail)
        except Exception:
            last_detail = {"raw": last_run.detail}

    pairs, _zones = _collect_pairs(db, int(p["autotune.window_days"]),
                                   dt.date.today() - dt.timedelta(days=1))
    today_metrics = _metrics(pairs, p) if len(pairs) >= MIN_PAIRS else None
    return {"tuned_keys": TUNED_KEYS,
            "param_curves": curves,
            "adjustments": logs[:100],
            "last_run": last_detail,
            "today_backtest": today_metrics}
