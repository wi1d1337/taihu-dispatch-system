"""algo-service v2：太湖蓝藻"研判→工单"算法微服务（FastAPI，无鉴权，仅 compose 内网）。

路由（契约 §3）：healthz / seed / pipeline/run-daily / assess/today / assess/accuracy /
params(GET/PUT) / autotune(run/history) / dashboard/eval / daily/report / zones/geojson。
"""

from __future__ import annotations

import datetime as dt
import json

from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sqlalchemy.orm import Session

from .assess import LEVEL_NAMES, MODEL_VERSION
from .autotune import autotune_history, autotune_run
from .db import get_db
from .evalx import assess_accuracy, daily_report, evaluate
from .models import TzAssessment, TzZone
from .params import apply_param, params_payload
from .pipeline import run_daily
from .seed import seed

app = FastAPI(title="taihu algo-service", version="2.0.0",
              description="太湖蓝藻研判引擎与工单算法服务（演示版；仅供个人研究学习，禁止商业使用）")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


@app.get("/api/v1/healthz")
def healthz():
    return {"status": "ok", "version": "2.0.0"}


@app.post("/api/v1/seed")
def api_seed(days: int = Query(730, ge=365, le=1095), db: Session = Depends(get_db)):
    try:
        return {"ok": True, "stats": seed(db, days=days)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"seed failed: {e}") from e


@app.post("/api/v1/pipeline/run-daily")
def api_run_daily(date: str | None = None, db: Session = Depends(get_db)):
    d = dt.date.fromisoformat(date) if date else dt.date.today()
    return run_daily(db, d)


@app.get("/api/v1/assess/today")
def assess_today(db: Session = Depends(get_db)):
    today = dt.date.today()
    zones = {z.zone_id: z for z in db.query(TzZone).all()}
    rows = (db.query(TzAssessment)
            .filter(TzAssessment.assess_date == today).all())
    if not rows:
        return {"date": today.isoformat(), "zones": [], "summary": {},
                "note": "今日无研判记录（先 POST /pipeline/run-daily 或 /seed）"}
    out, level_count = [], {}
    for a in rows:
        basis = {}
        try:
            basis = json.loads(a.basis or "{}")
        except Exception:
            pass
        z = zones.get(a.zone_id)
        lv = int(a.level or 0)
        level_count[str(lv)] = level_count.get(str(lv), 0) + 1
        out.append({
            "zone_id": a.zone_id, "zone_name": z.zone_name if z else None,
            "zone_code": z.zone_code if z else None,
            "level": lv, "level_name": LEVEL_NAMES.get(lv),
            "predicted": float(a.predicted) if a.predicted is not None else None,
            "yoy": float(a.yoy) if a.yoy is not None else None,
            "recent7": float(a.recent7) if a.recent7 is not None else None,
            "trend": float(a.trend) if a.trend is not None else None,
            "confidence": float(a.confidence) if a.confidence is not None else None,
            "basis": basis,                       # 参数快照/阈值/超阈/同比偏差 + 概率预报第二意见
            "forecast": basis.get("forecast"),    # 并列展示位：{prob,q25,q50,q75}
            "model_version": a.model_version, "param_version": a.param_version,
        })
    return {"date": today.isoformat(), "model_version": MODEL_VERSION,
            "param_version": max(r.param_version or 1 for r in rows),
            "zones": out,
            "summary": {"level_counts": level_count,
                        "dispatchable": level_count.get("2", 0) + level_count.get("3", 0)}}


@app.get("/api/v1/assess/accuracy")
def api_accuracy(days: int = Query(30, ge=7, le=180), db: Session = Depends(get_db)):
    return assess_accuracy(db, days=days)


@app.get("/api/v1/params")
def api_params(db: Session = Depends(get_db)):
    return {"params": params_payload(db)}


class ParamBody(BaseModel):
    value: float
    operator: str = "admin"


@app.put("/api/v1/params/{key}")
def api_param_put(key: str, body: ParamBody, db: Session = Depends(get_db)):
    try:
        return {"ok": True, "param": apply_param(db, key, body.value, body.operator)}
    except KeyError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e


@app.post("/api/v1/autotune/run")
def api_autotune_run(db: Session = Depends(get_db)):
    return autotune_run(db)


@app.get("/api/v1/autotune/history")
def api_autotune_history(db: Session = Depends(get_db)):
    return autotune_history(db)


@app.get("/api/v1/dashboard/eval")
def dashboard_eval(days: int = Query(30, ge=7, le=180), db: Session = Depends(get_db)):
    return evaluate(db, days=days)


@app.get("/api/v1/daily/report")
def api_daily_report(date: str | None = None, db: Session = Depends(get_db)):
    d = dt.date.fromisoformat(date) if date else dt.date.today()
    return daily_report(db, d)


@app.get("/api/v1/zones/geojson")
def zones_geojson(db: Session = Depends(get_db)):
    today = dt.date.today()
    assess_by_zone = {a.zone_id: a for a in
                      db.query(TzAssessment).filter(TzAssessment.assess_date == today).all()}
    features = []
    for z in db.query(TzZone).all():
        ring = json.loads(z.boundary or "[]")
        if not ring:
            continue
        ring_closed = ring + [ring[0]]
        a = assess_by_zone.get(z.zone_id)
        basis = {}
        try:
            basis = json.loads(a.basis or "{}") if a else {}
        except Exception:
            pass
        features.append({
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [ring_closed]},
            "properties": {
                "zone_id": z.zone_id, "zone_name": z.zone_name, "zone_code": z.zone_code,
                "level": int(a.level) if a else None,          # 地图色阶改用等级（0-3）
                "level_name": LEVEL_NAMES.get(int(a.level)) if a else None,
                "prob": float(z.prob_72h or 0),                 # 概率预报保留返回
                "predicted": float(a.predicted) if a and a.predicted is not None else None,
                "tons_q50": float(z.tons_q50 or 0),
                "chla": float(z.chla or 0), "area_km2": float(z.area_km2 or 0),
            },
        })
    return {"type": "FeatureCollection", "features": features}
