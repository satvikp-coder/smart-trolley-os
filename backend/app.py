"""Minimal provider-neutral Smart Trolley travel API.

Only configured, documented providers may return LIVE records. No third-party
credentials are accepted from Android clients.
"""
from __future__ import annotations

import os
import time
from collections import OrderedDict, defaultdict, deque
from datetime import datetime, timezone
from typing import Any
from urllib.parse import quote

import httpx
from fastapi import FastAPI, Header, HTTPException, Query, Request
from pydantic import BaseModel, ConfigDict, Field

app = FastAPI(title="Smart Trolley Travel API", version="0.1.0")
TTL = max(0, int(os.getenv("DATA_CACHE_TTL_SECONDS", "120")))
MAX_CACHE = max(1, int(os.getenv("MAX_CACHE_ENTRIES", "256")))
cache: OrderedDict[str, tuple[float, dict[str, Any], datetime]] = OrderedDict()
requests_by_ip: dict[str, deque[float]] = defaultdict(deque)


class Envelope(BaseModel):
    model_config = ConfigDict(extra="allow")
    provider: str
    source_timestamp: datetime | None = None
    retrieved_at: datetime
    freshness: str
    cache_status: str
    category: str
    records: list[dict[str, Any]] = Field(default_factory=list)
    message: str | None = None


def rate_limit(request: Request) -> None:
    now = time.monotonic()
    bucket = requests_by_ip[request.client.host if request.client else "unknown"]
    while bucket and now - bucket[0] >= 60:
        bucket.popleft()
    if len(bucket) >= 30:
        raise HTTPException(429, "Request limit exceeded; retry later")
    bucket.append(now)


def require_client_token(authorization: str | None) -> None:
    configured = os.getenv("ALLOWED_CLIENT_TOKEN", "").strip()
    if configured and authorization != f"Bearer {configured}":
        raise HTTPException(401, "Client authorization required")


async def get_json(url: str, params: dict[str, str], headers: dict[str, str] | None = None) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=httpx.Timeout(8.0, connect=4.0)) as client:
        response = await client.get(url, params=params, headers=headers)
        response.raise_for_status()
        payload = response.json()
        if not isinstance(payload, dict):
            raise ValueError("Provider returned an invalid response")
        return payload


async def cached(key: str, loader: Any) -> tuple[dict[str, Any], str, datetime]:
    now = time.monotonic()
    entry = cache.get(key)
    if entry and now - entry[0] < TTL:
        cache.move_to_end(key)
        return entry[1], "HIT", entry[2]
    value = await loader()
    fetched_at = datetime.now(timezone.utc)
    cache[key] = (now, value, fetched_at)
    cache.move_to_end(key)
    while len(cache) > MAX_CACHE:
        cache.popitem(last=False)
    return value, "MISS", fetched_at


def unconfigured(provider: str, message: str) -> dict[str, Any]:
    return Envelope(provider=provider, retrieved_at=datetime.now(timezone.utc), freshness="NOT_AVAILABLE",
                    cache_status="MISS", category="UNKNOWN", records=[], message=message).model_dump(mode="json")


@app.middleware("http")
async def protect_and_limit(request: Request, call_next: Any):
    if request.url.path.startswith("/api/") and request.url.path != "/api/health":
        rate_limit(request)
        require_client_token(request.headers.get("authorization"))
    return await call_next(request)


@app.get("/api/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "smart-trolley-travel", "version": "0.1.0"}


@app.get("/api/navigation/geocode")
async def geocode(q: str = Query(min_length=2, max_length=160)) -> dict[str, Any]:
    key = os.getenv("GRAPHHOPPER_API_KEY", "").strip()
    if not key:
        return unconfigured("GraphHopper", "Navigation provider is not configured")
    try:
        data, cache_status, fetched_at = await cached("geocode:" + q.casefold(), lambda: get_json(
            os.getenv("GRAPHHOPPER_BASE_URL", "https://graphhopper.com/api/1").rstrip("/") + "/geocode",
            {"q": q, "limit": "5", "key": key}))
        hits = data.get("hits", [])
        records = [{"name": h.get("name"), "country": h.get("country"), "latitude": (h.get("point") or {}).get("lat"),
                    "longitude": (h.get("point") or {}).get("lng")} for h in hits if isinstance(h, dict)]
        freshness = "CACHED" if cache_status == "HIT" else "LIVE"
        return Envelope(provider="GraphHopper", source_timestamp=fetched_at, retrieved_at=datetime.now(timezone.utc), freshness=freshness,
                        cache_status=cache_status, category=freshness, records=records).model_dump(mode="json")
    except (httpx.HTTPError, ValueError) as exc:
        raise HTTPException(502, "Navigation provider unavailable") from exc


@app.get("/api/navigation/route")
async def route(origin: str = Query(pattern=r"^-?\d+(\.\d+)?,-?\d+(\.\d+)?$"),
                destination: str = Query(pattern=r"^-?\d+(\.\d+)?,-?\d+(\.\d+)?$"),
                profile: str = Query(default="foot", pattern="^(foot|car|bike)$")) -> dict[str, Any]:
    lat1, lon1 = map(float, origin.split(",")); lat2, lon2 = map(float, destination.split(","))
    if not (-90 <= lat1 <= 90 and -180 <= lon1 <= 180 and -90 <= lat2 <= 90 and -180 <= lon2 <= 180):
        raise HTTPException(422, "Coordinates out of range")
    key = os.getenv("GRAPHHOPPER_API_KEY", "").strip()
    if not key:
        return unconfigured("GraphHopper", "Routing provider is not configured")
    try:
        data = await get_json(os.getenv("GRAPHHOPPER_BASE_URL", "https://graphhopper.com/api/1").rstrip("/") + "/route",
            {"point": [origin, destination], "profile": profile, "points_encoded": "false", "instructions": "true", "key": key})
        path = (data.get("paths") or [{}])[0]
        return Envelope(provider="GraphHopper", retrieved_at=datetime.now(timezone.utc), freshness="LIVE",
            cache_status="BYPASS", category="LIVE", records=[{"geometry": (path.get("points") or {}).get("coordinates"),
            "distance_meters": path.get("distance"), "duration_millis": path.get("time"), "instructions": path.get("instructions", [])}]).model_dump(mode="json")
    except (httpx.HTTPError, ValueError, IndexError) as exc:
        raise HTTPException(502, "Routing provider unavailable") from exc


@app.get("/api/railways/stations/search")
async def rail_station_search(q: str = Query(min_length=2, max_length=100)) -> dict[str, Any]:
    return unconfigured("Indian Railways", "No authorized live railway data provider is configured")


@app.get("/api/railways/stations/{station_code}/board")
async def rail_board(station_code: str, direction: str = Query(default="all", pattern="^(all|arrivals|departures)$")) -> dict[str, Any]:
    if not station_code.replace("_", "").isalnum() or len(station_code) > 12:
        raise HTTPException(422, "Invalid station code")
    return unconfigured("Indian Railways", "No authorized live station-board provider is configured")


@app.get("/api/railways/trains/search")
async def rail_train_search(q: str = Query(min_length=1, max_length=100)) -> dict[str, Any]:
    return unconfigured("Indian Railways", "No authorized live train provider is configured")


@app.get("/api/railways/trains/{train_number}")
async def rail_train(train_number: str) -> dict[str, Any]:
    if not train_number.isdigit() or len(train_number) > 8:
        raise HTTPException(422, "Invalid train number")
    return unconfigured("Indian Railways", "No authorized live train provider is configured")


@app.get("/api/airports/search")
async def airports(q: str = Query(min_length=2, max_length=100)) -> dict[str, Any]:
    key = os.getenv("AVIATIONSTACK_ACCESS_KEY", "").strip()
    if not key:
        return unconfigured("Aviationstack", "Flight provider is not configured")
    try:
        data, cache_status, fetched_at = await cached("airport:" + q.casefold(), lambda: get_json(
            os.getenv("AVIATIONSTACK_BASE_URL", "https://api.aviationstack.com/v1").rstrip("/") + "/airports",
            {"access_key": key, "search": q}))
        rows = data.get("data", [])
        records = [{"airport": r.get("airport_name"), "iata": r.get("iata_code"), "icao": r.get("icao_code"),
                    "city": r.get("city_iata_code"), "country": r.get("country_name"),
                    "latitude": r.get("latitude"), "longitude": r.get("longitude")} for r in rows if isinstance(r, dict)]
        freshness = "CACHED" if cache_status == "HIT" else "LIVE"
        return Envelope(provider="Aviationstack", source_timestamp=fetched_at, retrieved_at=datetime.now(timezone.utc), freshness=freshness,
                        cache_status=cache_status, category=freshness, records=records).model_dump(mode="json")
    except (httpx.HTTPError, ValueError) as exc:
        raise HTTPException(502, "Airport provider unavailable") from exc


@app.get("/api/airports/{airport_code}/flights")
async def airport_flights(airport_code: str, direction: str = Query(default="arrivals", pattern="^(arrivals|departures)$")) -> dict[str, Any]:
    if not airport_code.replace("_", "").isalnum() or len(airport_code) > 5:
        raise HTTPException(422, "Invalid airport code")
    key = os.getenv("AVIATIONSTACK_ACCESS_KEY", "").strip()
    if not key:
        return unconfigured("Aviationstack", "Flight provider is not configured")
    try:
        params = {"access_key": key, "limit": "100", "arr_iata" if direction == "arrivals" else "dep_iata": airport_code.upper()}
        data, cache_status, fetched_at = await cached(f"flight:{airport_code}:{direction}", lambda: get_json(
            os.getenv("AVIATIONSTACK_BASE_URL", "https://api.aviationstack.com/v1").rstrip("/") + "/flights", params))
        records = []
        for flight in data.get("data", []):
            if not isinstance(flight, dict): continue
            airline, details, departure, arrival = flight.get("airline") or {}, flight.get("flight") or {}, flight.get("departure") or {}, flight.get("arrival") or {}
            records.append({"flight_number": details.get("iata") or details.get("number"), "airline": airline.get("name"),
                "status": flight.get("flight_status"), "scheduled_departure": departure.get("scheduled"),
                "estimated_departure": departure.get("estimated"), "scheduled_arrival": arrival.get("scheduled"),
                "estimated_arrival": arrival.get("estimated"), "terminal": departure.get("terminal") if direction == "departures" else arrival.get("terminal"),
                "gate": departure.get("gate") if direction == "departures" else arrival.get("gate")})
        freshness = "CACHED" if cache_status == "HIT" else "LIVE"
        return Envelope(provider="Aviationstack", source_timestamp=fetched_at, retrieved_at=datetime.now(timezone.utc), freshness=freshness,
            cache_status=cache_status, category=freshness, records=records).model_dump(mode="json")
    except (httpx.HTTPError, ValueError) as exc:
        raise HTTPException(502, "Flight provider unavailable") from exc


@app.get("/api/flights/{flight_number}")
async def flight(flight_number: str) -> dict[str, Any]:
    return unconfigured("Aviationstack", "Use the airport flight board; individual-flight lookup is not configured")

