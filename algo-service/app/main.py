"""algo-service：太湖蓝藻预报调度算法微服务（FastAPI，无鉴权，仅 compose 内网）。"""

from __future__ import annotations

import datetime as dt
import json

from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from .db import get_db
from .evalx import evaluate
from .models import TzZone
from .pipeline import run_daily
from .seed import seed
from .solve import solve_cp_sat

app = FastAPI(title="taihu algo-service", version="1.0.0",
              description="太湖蓝藻预报与打捞调度算法服务（演示版；仅供个人研究学习，禁止商业使用）")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


@app.get("/api/v1/healthz")
def healthz():
    return {"status": "ok"}


@app.post("/api/v1/seed")
def api_seed(days: int = Query(120, ge=30, le=365), db: Session = Depends(get_db)):
    try:
        return {"ok": True, "stats": seed(db, days=days)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"seed failed: {e}") from e


@app.post("/api/v1/pipeline/run-daily")
def api_run_daily(date: str | None = None, db: Session = Depends(get_db)):
    d = dt.date.fromisoformat(date) if date else dt.date.today()
    return run_daily(db, d)


@app.post("/api/v1/solve")
def api_solve(date: str | None = None, half_day: str = "0", quantile: str = "q50",
              db: Session = Depends(get_db)):
    d = dt.date.fromisoformat(date) if date else dt.date.today()
    if quantile not in ("q25", "q50"):
        raise HTTPException(status_code=400, detail="quantile must be q25|q50")
    if half_day not in ("0", "1"):
        raise HTTPException(status_code=400, detail="half_day must be 0|1")
    return solve_cp_sat(db, d, half_day, quantile)


@app.get("/api/v1/zones/geojson")
def zones_geojson(db: Session = Depends(get_db)):
    features = []
    for z in db.query(TzZone).all():
        ring = json.loads(z.boundary or "[]")
        if not ring:
            continue
        ring_closed = ring + [ring[0]]
        features.append({
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [ring_closed]},
            "properties": {
                "zone_id": z.zone_id, "zone_name": z.zone_name, "zone_code": z.zone_code,
                "prob_72h": float(z.prob_72h or 0), "tons_q50": float(z.tons_q50 or 0),
                "chla": float(z.chla or 0), "area_km2": float(z.area_km2 or 0),
            },
        })
    return {"type": "FeatureCollection", "features": features}


@app.get("/api/v1/dashboard/eval")
def dashboard_eval(days: int = Query(30, ge=7, le=180), db: Session = Depends(get_db)):
    return evaluate(db, days=days)


@app.get("/api/v1/history/series")
def history_series(days: int = Query(90, ge=7, le=365), db: Session = Depends(get_db)):
    ev = evaluate(db, days=days)
    return {"dates": ev["history"]["dates"], "zones": ev["history"]["zones"]}
