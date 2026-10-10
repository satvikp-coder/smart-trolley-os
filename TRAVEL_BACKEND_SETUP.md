# Travel backend setup and deployment

The FastAPI source is in `backend/`. Python 3.10+, dependencies in
`backend/requirements.txt`. Locally: install dependencies, copy
`backend/.env.example` to an untracked `.env`, configure provider credentials,
then run `uvicorn app:app --host 127.0.0.1 --port 8000` from `backend/`.
Test with `python -m pytest`.

Deploy to an independently hosted service (not the development laptop), behind
HTTPS with a publicly trusted certificate, using secret environment variables
and restricted ingress. Set `ALLOWED_CLIENT_TOKEN` and provision a client token
through an appropriate protected configuration mechanism; do not embed provider
keys in the APK. Current app source has no backend URL configured and backend
deployment has not occurred. Android 4.4 TLS must be verified against the actual
hosting chain on-device; certificate checks remain enabled. Do not substitute
plain HTTP across the internet or bypass certificate verification.

Routes: `/api/health`, `/api/navigation/geocode`, `/api/navigation/route`,
`/api/railways/stations/search`, `/api/railways/stations/{code}/board`,
`/api/railways/trains/search`, `/api/railways/trains/{number}`,
`/api/airports/search`, `/api/airports/{code}/flights`, `/api/flights/{number}`.
Provider responses include category, provider, freshness, retrieval time, and
cache status. Apply current provider terms before enabling caching/redistribution.

