"""收敛交叉映射（CCM）简化实现（方案式 8）。

Sugihara et al. 2012, Science 338:496-500（Detecting causality in complex ecosystems）。
思路：若 y⇒x，则用 y 的延迟坐标流形可以预测 x（影子流形），且预测技能随库长度 L 增长。
本实现为教学级简化（ simplex 投影 + 均匀库采样），生产使用建议 rEDM/pyEDM。
"""

from __future__ import annotations

import numpy as np


def _embed(x: np.ndarray, E: int, tau: int) -> np.ndarray:
    n = len(x) - (E - 1) * tau
    idx = np.arange(E) * tau
    return x[np.arange(n)[:, None] + idx[None, :]]


def _spearman(a: np.ndarray, b: np.ndarray) -> float:
    ra = np.argsort(np.argsort(a)).astype(float)
    rb = np.argsort(np.argsort(b)).astype(float)
    ra -= ra.mean()
    rb -= rb.mean()
    d = np.sqrt((ra * ra).sum() * (rb * rb).sum())
    return float((ra * rb).sum() / d) if d > 0 else np.nan


def simplex_predict(manifold_y: np.ndarray, target_x: np.ndarray, query: np.ndarray, E: int) -> float:
    """以 y 流形上距 query 最近的 E+1 个点加权预测 x。"""
    dist = np.linalg.norm(manifold_y - query, axis=1)
    nn = np.argsort(dist)[: E + 1]
    d = np.clip(dist[nn], 1e-9, None)
    w = 1.0 / d
    w /= w.sum()
    return float((w * target_x[nn]).sum())


def ccm_skill(x, y, E: int = 3, tau: int = 1, lib_fracs=None, seed: int = 0,
              repeats: int = 8) -> tuple[np.ndarray, np.ndarray]:
    """检验 y ⇒ x：返回 (库长度序列, 预测技能 rho 序列)。

    rho 随 L 单调上升 ⇒ 支持 y ⇒ x（式 8）。
    """
    x = np.asarray(x, float)
    y = np.asarray(y, float)
    n = min(len(x), len(y))
    x, y = x[:n], y[:n]
    My = _embed(y, E, tau)
    xt = x[(E - 1) * tau:]
    m = len(My)
    rng = np.random.default_rng(seed)
    fr = lib_fracs if lib_fracs is not None else np.linspace(0.1, 0.95, 9)
    Ls, rhos = [], []
    for f in fr:
        L = max(int(m * f), E + 2)
        rs = []
        for _ in range(repeats):
            lib = rng.choice(m, size=L, replace=False)
            pred, true = [], []
            for q in rng.choice(m, size=min(60, m), replace=False):
                pred.append(simplex_predict(My[lib], xt[lib], My[q], E))
                true.append(xt[q])
            rs.append(_spearman(np.array(pred), np.array(true)))
        Ls.append(L)
        rhos.append(float(np.nanmean(rs)))
    return np.array(Ls), np.array(rhos)


def causal_direction(a, b, **kw) -> dict:
    """双向检验并给出方向判定。

    判据：库长充分时的终值技能 rho_end（技能随库长饱和时增幅不可靠，先比终值、
    终值接近再比增幅 rise）。方向记号与 Sugihara 2012 一致：M_b 能恢复 a ⇒ b 驱动 a。
    """
    La, ra = ccm_skill(a, b, **kw)   # b ⇒ a?
    Lb, rb = ccm_skill(b, a, **kw)   # a ⇒ b?
    rise_a = ra[-1] - ra[0]
    rise_b = rb[-1] - rb[0]
    if abs(ra[-1] - rb[-1]) > 0.05:
        direction = "b=>a" if ra[-1] > rb[-1] else "a=>b"
    else:
        direction = "b=>a" if rise_a > rise_b else "a=>b"
    return {
        "b=>a": {"rho_start": float(ra[0]), "rho_end": float(ra[-1]), "rise": float(rise_a)},
        "a=>b": {"rho_start": float(rb[0]), "rho_end": float(rb[-1]), "rise": float(rise_b)},
        "direction": direction,
    }
