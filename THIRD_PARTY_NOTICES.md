# Third-party notices

- The project remains licensed under GPL-3.0-only; see `LICENSE`.
- The embedded map uses osmdroid 6.1.20 (`org.osmdroid:osmdroid-android`),
  licensed under Apache License 2.0. Source and license:
  <https://github.com/osmdroid/osmdroid> and
  <https://www.apache.org/licenses/LICENSE-2.0>.
- When network tiles load, map attribution is shown as “© OpenStreetMap
  contributors”. Tile use is subject to the OpenStreetMap Foundation tile
  usage policy: <https://operations.osmfoundation.org/policies/tiles/>.
- No Mapsforge code, map dataset, or GraphHopper SDK is bundled. The optional
  GraphHopper-compatible adapter is project code and requires an authorized
  HTTPS proxy configured by the operator.
# Phase 4 notice

Mapsforge 0.21 was evaluated but is not included in the Android APK. Its
upstream library is LGPL-3.0. The FastAPI service is a separate source component
using FastAPI (MIT), Pydantic (MIT), and HTTPX (BSD-3-Clause); deployer must
retain relevant upstream notices when distributing that service. No map data
was acquired; OSM-derived data must include OSM attribution and comply with
ODbL and source provider terms.
