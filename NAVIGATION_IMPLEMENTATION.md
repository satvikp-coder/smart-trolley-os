# Navigation implementation and compatibility

## What is implemented

- Navigation remains an internal Smart Trolley OS screen. It uses osmdroid
  6.1.20 `MapView`, pan/zoom, touch-to-pin, destination marker, saved/recent
  coordinates, and a bounded app-private tile cache (32 MiB maximum, trimmed
  toward 24 MiB). In-memory map tiles are capped at 24 and download/file queues
  are limited. No prefetch or bulk download is requested.
- Coordinate entry works offline. GPS uses Android `LocationManager` only
  after the user requests it and grants permission; Android GPS hardware is
  reported by the tablet, but no fix was observed and the GPS provider was not
  enabled. No indoor positioning is claimed.
- Name search and walking routes use a modular GraphHopper-compatible HTTPS
  proxy adapter. Configure its base URL with Gradle property
  `trolleyNavigationProxyUrl` or environment variable
  `TROLLEY_NAVIGATION_PROXY_URL`. The proxy must provide GraphHopper-compatible
  `/api/1/geocode` and `/api/1/route` responses. The APK contains no API key;
  keep provider credentials on the proxy. No proxy is configured in this
  build, so name search and route calculation correctly report unavailable.
- Route lines, distances, durations, and instructions are shown only when
  returned by that configured service. No route is simulated.

## Physical-device finding — important defect

The Android 4.4.2 tablet accepted and launched the APK, rendered the embedded
map view and attribution, but its OSM tiles did **not** render. Logcat records
`SSLHandshakeException` / `SSL23_GET_SERVER_HELLO:unsupported protocol` for
`tile.openstreetmap.org` even with an accurate device clock, connected Wi-Fi,
and network access. This is a transport compatibility failure, not a blank
map-data issue. TLS certificate validation was not weakened. The global TLS
adapter restricts sockets to supported TLS 1.2 while retaining Android's
default trust manager, but this did not resolve this tablet's tile handshake.
Until an authorized compatible tile endpoint/proxy or a verified offline map
dataset is available, live basemap rendering on this tablet remains incomplete.
The map fallback view, markers, manual coordinate pinning, and internal screens
remain present.

## Provider evaluation

- **osmdroid 6.1.20 selected:** native `MapView` and overlays; Android API 19
  build and runtime verified. Apache-2.0. This is the right lightweight
  embedding API for a raster tile map, but its live service path has the
  KitKat TLS defect above.
- **Mapsforge 0.21 considered, not integrated:** its upstream README supports
  Android API 10+ and offline vector `.map` files under LGPL-3.0. It requires a
  separately sourced regional map file, which was not downloaded or bundled;
  it would not by itself solve place search or routing.
- **OpenStreetMap tile service:** tile URL is the standard OSM tile endpoint;
  attribution is visible, a product User-Agent is sent, caches honor provider
  expiry, and code does not prefetch. Policy:
  <https://operations.osmfoundation.org/policies/tiles/>. No offline bulk tile
  dataset is downloaded.
- **Public Nominatim intentionally unused:** the public service has strict
  request policy and explicitly restricts embedding its public API in
  code-generation output. Search is therefore user-coordinate input offline
  or through the operator's authorized proxy, not public autocomplete.
  Policy: <https://operations.osmfoundation.org/policies/nominatim/>.
- **GraphHopper Directions API:** available through a proxy adapter, not a
  direct unauthenticated public call. Provider credentials, quota, and terms
  must be handled by the operator/backend:
  <https://docs.graphhopper.com/openapi> and
  <https://www.graphhopper.com/terms/>.

## Offline maps and limitations

There is no bundled offline regional map file. Previously cached OSM tiles may
be reused according to their expiry, within the bounded cache; the cache is not
a substitute for an offline map. Do not bulk-download OSM public tiles. A
future offline deployment must acquire a regional Mapsforge file under
appropriate licensing and add a verified renderer/data setup step. No such
file is included in the APK.

## Legacy TLS note

The app's TLS 1.2 socket adapter uses Android's default trust configuration and
does not accept invalid certificates. It is not a guarantee that API 19 can
connect to every modern HTTPS server: server cipher/protocol negotiation may
still fail. The observed failure is documented rather than bypassed.
# Phase 4 status update (2026-10-11)

The Phase 3 osmdroid screen is preserved as the working fallback; online tiles
remain unusable on the tested KitKat tablet because of TLS negotiation. No
offline map renderer or Ahmedabad map file is installed. Mapsforge 0.21 was
checked as a likely candidate and its artifact resolved, but not integrated.
BBBike provides Mapsforge extracts, but this machine rejected the provider
certificate (`SEC_E_UNTRUSTED_ROOT`); TLS was not bypassed and no data was
obtained.

Backend source now includes proposed HTTPS GraphHopper-compatible geocode/route
endpoints. They return `NOT_AVAILABLE` until a provider key is configured on a
host. No API credentials, backend URL, deployment, or live result exists.
Android app is not yet wired to the new backend. See
`OFFLINE_MAP_IMPLEMENTATION.md` and `TRAVEL_BACKEND_SETUP.md`.
