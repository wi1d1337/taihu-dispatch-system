"""v2 冒烟测试（不连库的纯函数）。运行：python tests/test_v2.py（或 pytest tests/test_v2.py）。"""

from __future__ import annotations

import datetime as dt
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.assess import (LEVEL_NAMES, compute_assessment, decide, extract_features,
                        level_of_value)
from app.autotune import _candidate_grid, _metrics
from app.dispatch2 import build_order_no
from app.forecast import est_tons, forecast_zone
from app.models import TzAlgoParam
from app.params import DEFAULT_PARAMS, validate_value

P = {k: v["value"] for k, v in DEFAULT_PARAMS.items()}
D = dt.date(2026, 10, 13)


def _series(yoy_val=40.0, recent=60.0, prev7=50.0, exceed_vals=None, with_yoy=True,
            with_recent=True, n_yoy_days=15):
    """构造分段常值序列：去年同期窗口 / 前7日 / 近7日（近 exceed_days 日可单独覆盖）。"""
    s = {}
    if with_yoy:
        for k in range(365 - 7, 365 + 8):          # ±7 天（yoy_halfwin=7）→ 15 天
            if k <= 7 or (not with_recent and k > 7):
                pass
            s[D - dt.timedelta(days=k)] = yoy_val
    if with_recent:
        for i in range(1, 15):
            s[D - dt.timedelta(days=i)] = recent if i <= 7 else prev7
        for i, v in enumerate(exceed_vals or [], start=1):
            s[D - dt.timedelta(days=i)] = v
    return s


def approx(a, b, tol=0.01):
    return abs(a - b) <= tol


def test_level_of_value_boundaries():
    assert level_of_value(80, P) == 3
    assert level_of_value(79.99, P) == 2
    assert level_of_value(50, P) == 2
    assert level_of_value(49.99, P) == 1
    assert level_of_value(20, P) == 1
    assert level_of_value(19.99, P) == 0


def test_blend_formula_and_level():
    # predicted = 0.6×40 + 0.4×60×(1+0.20) = 24 + 28.8 = 52.8 → L2；近3日60<80(上一档阈) 不升级
    r = compute_assessment(_series(), D, P)
    assert approx(r["predicted"], 52.8), r["predicted"]
    assert r["level"] == 2
    assert r["basis"]["degraded"] is False
    assert r["basis"]["yoy_deviation_pct"] is not None
    assert "forecast" not in r["basis"]  # forecast 由 assess_one 附加


def test_upgrade_persistence_rule():
    # 近3日 85 ≥ 上一档阈(80) 持续 exceed_days → L2 基档升 L3
    r = compute_assessment(_series(exceed_vals=[85.0, 85.0, 85.0]), D, P)
    assert r["basis"]["upgraded"] is True
    assert r["level"] == 3
    assert r["basis"]["exceed_against"] == "assess.level3_chla"
    # 近3日只超当前档阈(50)未达上一档(80) → 不升级（持续性确认口径）
    r2 = compute_assessment(_series(exceed_vals=[70.0, 70.0, 70.0]), D, P)
    assert r2["basis"]["upgraded"] is False


def test_upgrade_only_2_of_3_days():
    r = compute_assessment(_series(exceed_vals=[85.0, 85.0, 10.0]), D, P)
    assert r["basis"]["exceed_days_count"] == 2
    assert r["basis"]["upgraded"] is False   # 不满足 exceed_days=3


def test_degraded_no_yoy():
    r = compute_assessment(_series(with_yoy=False), D, P)
    assert r["basis"]["degraded"] is True
    assert r["yoy"] is None
    assert approx(r["predicted"], 60.0)      # 回落近7日均值
    assert r["level"] == 2


def test_empty_series():
    r = compute_assessment({}, D, P)
    assert r["level"] == 0 and r["basis"]["degraded"] is True


def test_no_recent_only_yoy():
    r = compute_assessment(_series(with_recent=False), D, P)
    assert approx(r["predicted"], 40.0)
    assert r["basis"]["reason"].startswith("近7日数据缺失")


def test_zero_level_and_l1():
    r = compute_assessment(_series(yoy_val=10.0, recent=12.0, prev7=11.0), D, P)
    assert r["level"] == 0
    r1 = compute_assessment(_series(yoy_val=18.0, recent=22.0, prev7=20.0), D, P)
    assert r1["level"] == 1


def test_validate_value():
    spec = {"min_value": 5, "max_value": 60, "meaning": "关注阈值"}
    assert validate_value(spec, 20.0) == (True, "")
    ok, msg = validate_value(spec, 4.9)
    assert not ok and "低于下限" in msg
    ok, msg = validate_value(spec, 61.0)
    assert not ok and "高于上限" in msg


def test_build_order_no():
    assert build_order_no(dt.date(2026, 10, 14), 1) == "TZ20261014-0001"
    assert build_order_no(dt.date(2026, 1, 2), 123) == "TZ20260102-0123"
    assert len(build_order_no(dt.date(2026, 12, 31), 9999)) == 15


def test_forecast_zone_sane():
    f = forecast_zone([10.0 + i * 0.5 for i in range(60)])
    assert 0.0 <= f["prob"] <= 1.0
    assert f["q25"] <= f["q50"] <= f["q75"]
    t25, t50 = est_tons(f, 129.0)
    assert 0 <= t25 <= t50


def test_metrics_pure():
    f = extract_features(_series(), D, P)
    pairs = [(f, 55.0, 1, D), (f, 51.0, 1, D), (f, 10.0, 1, D)]   # 实际 L2,L2,L0 vs 预测 L2 → 2 hit 1 over
    m = _metrics(pairs, P)
    assert m["samples"] == 3 and m["hits"] == 2
    assert approx(m["hit_rate"], 66.7, 0.1)
    assert m["mae"] is not None and m["pod"] is not None


def test_candidate_grid_bounds():
    rows = {k: TzAlgoParam(param_key=k, param_value=v["value"], min_value=v["min"],
                           max_value=v["max"]) for k, v in DEFAULT_PARAMS.items()}
    grid = _candidate_grid(rows, P, step_pct=10.0)
    for k, cands in grid.items():
        assert len(cands) >= 1 and len(cands) <= 3
        for c in cands:
            assert DEFAULT_PARAMS[k]["min"] - 1e-9 <= c <= DEFAULT_PARAMS[k]["max"] + 1e-9
    assert 0.6 in grid["assess.yoy_weight"]
    assert 0.54 in [round(x, 4) for x in grid["assess.yoy_weight"]]


def test_decide_basis_param_snapshot():
    r = compute_assessment(_series(), D, P)
    assert r["basis"]["params"]["assess.yoy_weight"] == 0.6
    assert r["basis"]["thresholds"]["level2"] == 50.0
    assert set(r["basis"]) >= {"params", "thresholds", "exceed_days_count", "yoy_deviation_pct",
                               "degraded", "reason"}
    assert LEVEL_NAMES[3] == "重大(应急)"


if __name__ == "__main__":
    fails = 0
    for name, fn in sorted([(k, v) for k, v in globals().items()
                            if k.startswith("test_") and callable(v)]):
        try:
            fn()
            print(f"PASS {name}")
        except AssertionError as e:
            fails += 1
            print(f"FAIL {name}: {e}")
    sys.exit(1 if fails else 0)
