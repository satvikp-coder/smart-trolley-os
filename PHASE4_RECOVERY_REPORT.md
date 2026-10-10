# Smart Trolley OS — Phase 4 recovery report

**Overall status: PARTIALLY COMPLETE.** The existing project now contains a
Mapsforge offline map renderer, a real validated regional dataset on the laptop,
local airport reference search, backend-backed travel lookups, and tests. The
Android legacy APK builds. The most important acceptance test—rendering
Ahmedabad geography on the real tablet—remains **NOT DEVICE-TESTED** because
ADB currently reports no connected devices. No APK/map was sent to the tablet,
and no package or HOME setting was changed.

## A. Implemented source changes

- `app/build.gradle.kts`: replace active osmdroid dependency with
  `org.mapsforge:mapsforge-map-android:0.21.0`; configure travel backend URL;
  add JUnit tests. minSdk legacy remains 19.
- `app/src/main/java/com/dashline/launcher/OfflineMapSurface.kt`: Mapsforge map
  discovery/loading, bounds checks, tile rendering, pan/zoom, tap markers,
  location/destination markers, route overlay, scale bar, error state and
  resource disposal.
- `app/src/main/assets/mapsforge/default.xml`: lightweight vector render theme.
- `NavigationProviders.kt` and `TrolleyHomeActivity.kt`: normalized HTTPS
  backend navigation contract, saved coordinates/viewport, admin backend URL
  health probe and client token, airport reference search/map handoff,
  arrivals/departures/flight query UI, manual railway search/reference state.
- `TravelReferenceDatabase.kt`: SQLite airport reference seed and bounded
  manually saved railway search strings. It is not a train schedule database.
- `TravelApiClient.kt`: HTTPS-only, platform certificate validation, bounded
  response, timeouts and optional client bearer token.
- `backend/app.py`: bounded cache and per-IP limiter, normalized provider
  responses, documented Aviationstack airport/flight-number/board adapters,
  and explicit unconfigured railway responses.
- `backend/test_app.py`, Android `app/src/test/.../TravelCompatibilityTest.kt`:
  provider mocks, cache/missing-field behavior, validation and TLS guard tests.
- Updated map/license/provider/backend/performance/device/README reports. GPLv3
  project license preserved; third-party notices now describe Mapsforge and
  external data accurately.

## B. Offline map source and status

The official BBBike western-zone Mapsforge OSM extract was downloaded using
normal Windows TLS validation from:
<https://download3.bbbike.org/osm/region/asia/india/western-zone/>. The earlier
`download.bbbike.org` route failed with Schannel `SEC_E_UNTRUSTED_ROOT`; no
certificate checks were bypassed. The alternate BBBike source validated and
download succeeded.

- File: `C:\Users\satvi\Documents\SmartTrolleyOS\maps\bbbike-extract\western-zone-mapsforge-osm\western-zone.map`
- Size: 183,975,384 bytes (~175.5 MiB); not bundled into APK.
- SHA-256: `4C68952FFBE2E61632F481EC0272C99A5D94E084B7D270052B6E499A5AF51291`.
- Mapsforge 0.21 `MapFile` opened and parsed the file header; bounds
  14.38929–24.71756 N / 67.67457–80.91362 E include Ahmedabad
  (23.0225, 72.5714). This verifies compatible file metadata/coverage, **not**
  visible rendered streets.
- OSM attribution is shown in-app. Dataset is OSM-derived, ODbL applies, and
  BBBike terms govern extract use/redistribution. Source detail:
  `OFFLINE_MAP_IMPLEMENTATION.md` and `MAP_DATA_SOURCE_AND_LICENSE.md`.
- Mapsforge app data location: `/sdcard/Android/data/com.dashline.launcher/files/maps/`.
  App-owned internal `files/maps` fallback is used when external app storage is
  unavailable.
- Map source is present only on the development laptop. It has **not** been
  transferred; pan/zoom, offline drawing, GPS marker and no-network map display
  require approved physical testing.

Status: **IMPLEMENTED BUT NOT DEVICE-TESTED**. A valid map file and integrated
renderer exist; the acceptance criterion requires actual rendering on the iBall.

## C. Railway status

No public documented NTES/CRIS developer API or authorized RailYatri endpoint,
agreement, credentials or redistribution permission was found/configured.
No scraping or undocumented endpoints are used. UI supports manual station
query and locally saved search strings; backend station/train routes respond
with a clear unconfigured `UNKNOWN`/`NOT_AVAILABLE` status. No station catalog,
code database, timetable, live arrivals/departures, delay or platform values
are supplied. Railway live service: **BLOCKED BY PROVIDER ACCESS**. Station
reference persistence is **IMPLEMENTED BUT NOT DEVICE-TESTED**, and is not a
railway timetable.

## D. Airport status

The four local static airport references are AMD, BOM, DEL and BLR, with
OurAirports public-domain names/codes/coordinates. Search and “show on map” are
offline reference actions, not flight schedules.

The backend uses Aviationstack `/v1/airports` search and `/v1/flights` filters
(`arr_iata`, `dep_iata`, and `flight_number`), extracting only provider-supplied
status/time/terminal/gate data. Credentials remain backend environment secrets.
Mock parsing/cache tests passed. No account key or live Indian flight query is
available; Hyderabad/Ahmedabad coverage, actual terminal/gate availability,
retention rights and deployment are unverified. Current provider pricing page
labels its free tier personal/non-commercial with a limited quota; production
license/terms require review. Provider status: **IMPLEMENTED WITH MOCK PROVIDER
ONLY**. Airline/live display is pending credentials, hosting and device test.
Sources: [official Aviationstack docs](https://docs.apilayer.com/apilayer/docs/aviationstack),
[FAQ](https://aviationstack.com/faq), [pricing](https://aviationstack.com/pricing).

## E. Backend connectivity

FastAPI exists locally, has `/api/health`, normalized navigation/railway/airport
paths, token gate, bounded TTL/LRU response cache and per-IP rate limiter. App
Administrator accepts HTTPS base URL and optional client token; navigation,
airport and railway paths use the same API base. Platform TLS validation is
retained, with 5s/7s request timeouts and 1 MiB response bound. Seven backend
tests include mocked Aviationstack queries; no provider credentials are used.

No hosted backend is deployed and the Android client has not reached a live
backend. The user must deploy independently with a publicly trusted chain
accepted by KitKat, configure secrets, and test from tablet hotspot. The laptop
is not a production dependency. Live routing/search remains unavailable until
GraphHopper credentials/provider terms and a backend host are configured.

## F. Build and test results

Commands:

```powershell
$env:ANDROID_HOME='C:\Users\satvi\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat :app:assembleLegacyDebug :app:testLegacyDebugUnitTest
cd backend
python -m pytest -q
```

- Gradle build: **SUCCESS**, `:app:assembleLegacyDebug` and
  `:app:testLegacyDebugUnitTest`.
- Android unit tests: **2 passed**, no failures.
- Backend: **7 passed**, including mock-only provider and cache checks.
- APK: `app/build/outputs/apk/legacy/debug/app-legacy-debug.apk`, 6,811,344
  bytes (~6.49 MiB), SHA-256
  `1C54DE9EC2E05A1E3EB9E4099DEC989C678DE3D687EE2D4DC6A5A240DBC9B63E`.
- `aapt dump badging`: package `com.dashline.launcher`, minSdk 19,
  targetSdk 36, launchable `TrolleyHomeActivity`, launcher component present.
- Phase 3 APK remains unmodified at
  `C:\Users\satvi\Documents\SmartTrolleyOS\backups\phase3-device-tested.apk`,
  SHA-256 `58D7F0AA0A1380DEE04EE47E9ACC4CD42FA3E42D168EC835946F2E891109A24A`.
  Phase 2 backup also exists; both backups were preserved. New and Phase 3 APK
  debug signing certificate SHA-256 matches, supporting `adb install -r`
  rollback/update without uninstalling or clearing app data.
- APK is 67,115 bytes larger than Phase 3 build; map dataset is external.
- Build warnings are existing Android deprecated-network APIs and Java 8
  source/target under JDK 21; build has no compilation errors.

## G. Device state and performance

Current read-only `adb devices -l`: **no devices/emulators found**. Therefore
this pass cannot freshly confirm model, battery, storage, Launcher3 state, or
run Logcat. Phase 3 reports say the stock Launcher3 remained selected and the
earlier build launched on API 19. Last-reported `/data` free ~2.4 GiB and
`/sdcard` ~8.2 GiB are historical, not remeasured this pass. No cleanup or
device writes occurred; optional game packages were absent in the previous
connected inventory, but no removal is inferred.

No Phase 4 PSS, startup, touch, battery, map renderer, TLS or GPS measurements
exist. Do not claim optimization/performance improvements. See
`PERFORMANCE_TEST_REPORT.md` and `PHASE4_DEVICE_TEST_REPORT.md`.

## H. Transfer/install and recovery commands

After reconnecting the tablet, first run read-only checks:

```powershell
C:\platform-tools\adb.exe devices -l
C:\platform-tools\adb.exe shell getprop ro.product.model
C:\platform-tools\adb.exe shell getprop ro.build.version.sdk
C:\platform-tools\adb.exe shell df /data /sdcard
```

**Approval required before either transfer or APK installation.** Once
approved, the expected ~176 MiB map transfer and APK update commands are:

```powershell
C:\platform-tools\adb.exe shell mkdir -p /sdcard/Android/data/com.dashline.launcher/files/maps
C:\platform-tools\adb.exe push "C:\Users\satvi\Documents\SmartTrolleyOS\maps\bbbike-extract\western-zone-mapsforge-osm\western-zone.map" /sdcard/Android/data/com.dashline.launcher/files/maps/western-zone.map
C:\platform-tools\adb.exe shell ls -l /sdcard/Android/data/com.dashline.launcher/files/maps/
C:\platform-tools\adb.exe install -r "C:\Users\satvi\Desktop\dashline-main\app\build\outputs\apk\legacy\debug\app-legacy-debug.apk"
C:\platform-tools\adb.exe shell am start -n com.dashline.launcher/.TrolleyHomeActivity
```

No HOME selection is changed by these commands. Before update, preserve the
current installed state and verify original launcher package exists. Roll back
the code with:

```powershell
C:\platform-tools\adb.exe install -r "C:\Users\satvi\Documents\SmartTrolleyOS\backups\phase3-device-tested.apk"
```

The map file can remain separately; deleting it is unnecessary and is not part
of rollback. If the device must return to stock HOME, use Android Settings to
choose/clear the HOME default; do not remove Launcher3. The new APK does not
automatically select itself as default. Keep ADB enabled for recovery.

## I. Approval checkpoint

1. Reconnect the tablet and approve transfer of the 183,975,384-byte map file.
2. Approve installation of the new APK over the existing app (same signer;
   rollback APK verified available).
3. Leave package cleanup and default HOME changes unapproved/unperformed.

No APK install, map push, app uninstall, user-data clearing, system-package
change, firmware operation or default launcher change was performed.
