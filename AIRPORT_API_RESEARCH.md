# Airport API research and integration status

## Aviationstack adapter

Backend adapter is implemented for documented `/v1/airports` search and
`/v1/flights` queries: airport catalog search, arrivals by `arr_iata`,
departures by `dep_iata`, and flight lookup by `flight_number`. It normalizes
flight number, airline, status, scheduled/estimated times, and only supplied
terminal/gate fields. Missing fields are omitted. Responses are validated as
JSON objects, time out, are bounded/cached, and do not expose provider keys.
The adapter and cache behavior are tested with mock fixtures; **no real key or
live request was tested**, so Indian coverage and Ahmedabad gate/terminal
availability remain unverified. Aviationstack says the API has real-time
flight status, airports, and terminal/gate fields, but that does not guarantee
availability for a specific flight/airport. [Official API docs](https://docs.apilayer.com/apilayer/docs/aviationstack),
[official FAQ](https://aviationstack.com/faq).

The current public pricing page lists a free personal/non-commercial tier at
100 requests and HTTPS, while paid tiers are presented for commercial use and
include higher quotas/features. Terms, licensing, quota and whether the
intended trolley deployment qualifies must be confirmed before production use;
pricing can change. [Official pricing](https://aviationstack.com/pricing),
checked 2026-10-11.

## App behavior

Four airport references (Ahmedabad AMD, Mumbai BOM, Delhi DEL, Bengaluru BLR)
are bundled locally from OurAirports public-domain records. They support
reference search and map-centering; they are not flight schedules. Source:
[OurAirports data](https://ourairports.com/data/). If an HTTPS backend is
configured, search may return provider airports or flight boards; no result is
shown as LIVE unless the API actually returns it. Without configuration the UI
labels live results unavailable and retains static airport references.

## Limits

The provider's current exact India coverage, endpoint entitlement under a
specific plan, update rate, and permission to retain/redistribute returned
records have not been verified against an account contract. No gate or terminal
is inferred. Live airline data is **BLOCKED BY CREDENTIALS/TERMS** until an
authorized API key, independent HTTPS backend deployment and actual device test
exist. Mock tests do not prove live integration.
