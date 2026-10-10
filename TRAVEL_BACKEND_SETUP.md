# Travel backend setup

FastAPI service source is in `backend/`. It is locally testable and **not
deployed**. The tablet does not need the development laptop in the final setup;
deploy the service on an independent host with a publicly trusted HTTPS
certificate whose chain/ciphers are accepted by Android 4.4.

## Local developer setup

Use Python 3.10+, install `backend/requirements.txt`, copy
`backend/.env.example` to an untracked `.env`, and set authorized provider
credentials only in the service environment. From `backend/`, run
`uvicorn app:app --host 127.0.0.1 --port 8000`; tests run with
`python -m pytest -q`. Loopback/local plain HTTP is only for isolated local
development and is intentionally rejected by the Android client. Do not expose
the dev server over public Wi-Fi.

## Android connection

Configure an `https://` base URL and optional client bearer token from
Administrator → Travel API. Build-time alternative: Gradle property
`trolleyTravelApiBaseUrl` or environment `TROLLEY_TRAVEL_API_BASE_URL`. Health
probe calls `/api/health`. App request timeouts are 5 seconds connect / 7
seconds read, response cap 1 MB, normal platform TLS validation. No trust-all
certificate behavior exists. KitKat TLS support varies; a real device test of
the exact host is required. Never use HTTP for a hosted backend.

Provider secrets (`GRAPHHOPPER_API_KEY`, `AVIATIONSTACK_ACCESS_KEY`) stay on
the server and are never part of the APK. The optional `ALLOWED_CLIENT_TOKEN`
protects non-health API routes; enter the corresponding client token in the
Administrator panel. Use a secret manager/environment settings at the host,
rate-limit ingress, and avoid logging query credentials. `.env.example` has
names, not secrets.

## API and current status

- `GET /api/health`
- `GET /api/navigation/geocode?q=...`
- `GET /api/navigation/route?origin=lat,lon&destination=lat,lon&profile=foot`
- `GET /api/railways/stations/search?q=...`
- `GET /api/railways/stations/{stationCode}/board?direction=...`
- `GET /api/railways/trains/search?q=...` and `/api/railways/trains/{number}`
- `GET /api/airports/search?q=...`
- `GET /api/airports/{airportCode}/flights?direction=arrivals|departures`
- `GET /api/flights/{flightNumber}`

Responses carry provider, retrieval timestamp, freshness/category, cache
status, records and an explanatory message. Provider source timestamps remain
null unless the provider actually supplies one. Live routes/places/flights
require authorized keys; railway endpoints explicitly report not configured.
Aviationstack adapter uses documented airport/flights query endpoints and is
covered with mocks, not credentials/live results. Current in-memory cache is
bounded by entry count and TTL; review provider terms before enabling retention.
It is not durable offline sync. Deployment, provider access, Indian coverage and
Android-to-host TLS remain unverified.
