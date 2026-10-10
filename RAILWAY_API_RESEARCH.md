# Railway API research

The CRIS NTES product publishes passenger-facing live train-running status and
ETA/ETD. This research found no public developer API, documented schema,
authorization flow, pricing, or redistribution license suitable for direct app
integration. RailYatri's partner portal requires registration; it is not
evidence of a public API. No web scraping, undocumented endpoints, or access
control bypass was attempted. [CRIS NTES](https://www.cris.org.in/loadpage?page=proNTES),
[RailYatri partner portal](https://partnercare.railyatri.in/)

The app retains manual entry and explicitly marked simulation. The backend
station/trains endpoints return `UNKNOWN` / `NOT_AVAILABLE` until authorized
provider access and data rights are configured. Live station boards, delays,
platform assignments, timetable data, and live train details are NOT
CONFIGURED. No sample records are presented as live. Platform must be verified
at station announcements.

