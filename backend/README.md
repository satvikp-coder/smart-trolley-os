# Smart Trolley travel backend

Python 3.10+ service. Run `python -m pip install -r requirements.txt`, copy
`.env.example` to `.env` (never commit `.env`), then run
`uvicorn app:app --host 127.0.0.1 --port 8000`. Tests: `python -m pytest`.
Deploy behind a managed HTTPS reverse proxy with valid public certificates;
never expose provider keys to clients. No production deployment or provider
account is configured by this repository. The service must be hosted on an
independent service before the tablet can call it.

Railway endpoints intentionally return NOT CONFIGURED until a data agreement
and provider adapter are approved. Only the GraphHopper and Aviationstack
endpoints implemented in `app.py` can make provider requests when credentials
are configured. Review their current commercial, retention, and redistribution
terms before enabling production traffic.
