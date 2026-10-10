# Third-party notices

- The Dashline-derived project remains licensed under GPL-3.0-only; see
  `LICENSE`.
- Offline vector rendering uses Mapsforge 0.21.0
  (`org.mapsforge:mapsforge-map-android`), LGPL-3.0:
  <https://github.com/mapsforge/mapsforge>.
- Optional external map data is a BBBike OSM extract, not bundled in the APK.
  The map screen attributes “© OpenStreetMap contributors”; data is subject to
  ODbL and BBBike terms. Details: `MAP_DATA_SOURCE_AND_LICENSE.md`.
- The small airport reference dataset derives from OurAirports public-domain
  data. Source: <https://ourairports.com/data/>.
- The optional GraphHopper-compatible client is project code and connects only
  to the configured HTTPS Smart Trolley backend. Provider secrets remain
  server-side.
- The separate backend uses FastAPI (MIT), Pydantic (MIT), and HTTPX
  (BSD-3-Clause). Retain upstream notices when distributing/deploying it.
