# Navigation implementation

Navigation remains entirely inside Smart Trolley OS. Mapsforge 0.21.0 renders a
regional offline `.map` file, with touch pan/zoom, tap-to-place destination,
saved coordinates, map viewport persistence, route overlays, a scale bar, and
Android `LocationManager` current-location markers where a GPS fix is available.
The offline map and live directions are independent: a map works offline; route
calculation requires a configured backend and current origin coordinates.

The map file is stored outside the APK under the app-specific
`Android/data/com.dashline.launcher/files/maps/` directory. The supplied
western-zone extract includes Ahmedabad and is installed on the tablet. The
map rendered; ADB-injected tap, pan and zoom passed after a nullable callback
fix. Human touchscreen and network-disconnected tests remain unverified. See
`OFFLINE_MAP_IMPLEMENTATION.md`.

The replaceable `NavigationProvider` uses the normalized backend contract at
`/api/navigation/geocode` and `/api/navigation/route`. Provider credentials are
server-side; the app accepts an optional client bearer token in Administrator
settings. Place search and routing are unavailable until an authorized HTTPS
backend and GraphHopper credentials/configuration are deployed. The backend
normalizes provider results; no route or geocode is fabricated. Android keeps
certificate validation enabled. API 19 connectivity to a specific hosted TLS
chain has not been verified on the tablet.

GPS is requested only when the user asks. If disabled, unavailable, or lacking
a fix, coordinate entry and destination marking remain available. No indoor
positioning or platform-level accuracy is claimed.

There is no Android external-map launch path for normal navigation. GPS fix and
actual Wi-Fi-disconnected operation remain **not verified**. ADB input tests
are not a substitute for a human touchscreen test.

The Railway screen can search named POIs and map-way labels from the local
Mapsforge map within the Ahmedabad–Gandhinagar search bounds; selecting one
opens Navigation centered on its map coordinates. This is offline OSM map-name
search, not a railway-station schedule service. The Railway day-board demo uses
fictional service names, times and platforms, all explicitly labeled simulated.
