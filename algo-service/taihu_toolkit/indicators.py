"""临界慢化指标与复杂性度量（方案式 7 与 4.3 节）。

滚动 AR(1)：x_t = alpha·x_{t-1} + eps_t，系统趋近状态转换时 alpha→1、方差上升
（Carpenter et al. 2011, Science）。排列熵：Bandt & Pompe 2002 (Phys. Rev. Lett.)。

定位提醒（同方案 4.1）：研究性监测量，不进入运行决策。
"""

from __future__ import annotations

import math

import numpy as np


def rolling_ar1(x, window: int = 365) -> np.ndarray:
    """滚动窗口一阶自相关系数 alpha（式 7）。"""
    x = np.asarray(x, float)
    n = len(x)
    out = np.full(n, np.nan)
    for i in range(window, n + 1):
        w = x[i - window:i]
        a, b = w[:-1], w[1:]
        a = a - a.mean()
        b = b - b.mean()
        denom = (a * a).sum()
        out[i - 1] = (a * b).sum() / denom if denom > 0 else np.nan
    return out


def rolling_var(x, window: int = 365) -> np.ndarray:
    x = np.asarray(x, float)
    n = len(x)
    out = np.full(n, np.nan)
    for i in range(window, n + 1):
        out[i - 1] = x[i - window:i].var()
    return out


def permutation_entropy(x, order: int = 3, delay: int = 1, normalize: bool = True) -> float:
    """排列熵（Bandt–Pompe）。返回 [0,1]（normalize）或 ln(order!) 口径。"""
    x = np.asarray(x, float)
    n = len(x) - (order - 1) * delay
    if n <= order:
        return np.nan
    idx = np.arange(order) * delay
    perms = np.argsort(x[np.arange(n)[:, None] + idx[None, :]], axis=1)
    counts = {}
    for p in map(tuple, perms):
        counts[p] = counts.get(p, 0) + 1
    p = np.array(list(counts.values())) / n
    h = -(p * np.log(p)).sum()
    return h / math.log(math.factorial(order)) if normalize else h
