"""藻类生长率：温度 × 光（Steele）× 营养盐（Monod）联合限制（方案式 2）。

文献：Steele 1962 (Limnol. Oceanogr.)；Monod 1949 (Annu. Rev. Microbiol.)。
参数 DEFAULT 为方案 3.1 节给出的文献典型区间中值，真实使用须以太湖数据率定。
"""

from __future__ import annotations

import numpy as np

DEFAULT = {
    "mu_max": 1.6,   # d^-1，微囊藻 25℃（文献区间 1.2-2.0）
    "theta": 1.07,   # 温度系数（1.06-1.08）
    "I_s": 220.0,    # 饱和光强 uE/m^2/s（由水面辐射与消光系数估算）
    "K_P": 0.02,     # mg-P/L（0.005-0.05）
    "K_N": 0.10,     # mg-N/L（0.05-0.25）
}


def growth_rate(temp, rad, N, P, params: dict | None = None) -> np.ndarray:
    """联合限制生长率 mu（d^-1）。

    mu = mu_max · theta^(T-25) · (I/Is)·exp(1-I/Is) · min(N/(N+K_N), P/(P+K_P))
    """
    p = {**DEFAULT, **(params or {})}
    T = np.asarray(temp, float)
    fT = p["theta"] ** (T - 25.0)
    I = np.clip(np.asarray(rad, float), 1e-6, None)
    fI = (I / p["I_s"]) * np.exp(1.0 - I / p["I_s"])
    N = np.clip(np.asarray(N, float), 0.0, None)
    P = np.clip(np.asarray(P, float), 0.0, None)
    fN = N / (N + p["K_N"])
    fP = P / (P + p["K_P"])
    return p["mu_max"] * fT * fI * np.minimum(fN, fP)
