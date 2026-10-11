# Smart Trolley OS — Phase 4 recovery report

**Overall status: PARTIALLY COMPLETE.** The new Android API 19 build is
installed and running on the iBall. A genuine Mapsforge map covering Ahmedabad
renders from device storage; map taps, panning, zoom, and internal screens were
tested with ADB-injected input. A crash in the first build was diagnosed and
fixed, then verified not to recur in the tested interactions. Network-off
operation, live providers/backend, GPS fix, and human physical-touch testing
remain unverified. No packages or HOME settings were changed.

## A. Source changes

- Integrated Mapsforge 0.21.0 as the only active map engine; retained API 19.
- `app/src/main/java/com/dashline/launcher/OfflineMapSurface.kt`: local map
  discovery, geographic-bound validation, vector rendering, markers, pan/zoom,
  tap-to-pin, route overlay, scale bar, missing/corrupt map state and cleanup.
- `app/src/main/assets/mapsforge/default.xml`: lightweight render theme.
- `NavigationProviders.kt`, `TravelApiClient.kt` and `TrolleyHomeActivity.kt`:
  normalized HTTPS backend client, navigation geocode/route, backend settings
  and health check, manual railway search, airport lookup/results, and map
  handoff. Provider keys remain server-side.
- `TravelReferenceDatabase.kt`: four local airport references and up to 128
  manually saved station-search strings; no train timetable or station-code
  catalog is fabricated.
- `backend/app.py` and `backend/test_app.py`: bounded provider response/cache,
  rate limiting, normalized Aviationstack adapters and unconfigured railway
  responses.
- Updated licensing and Phase 4 documentation; GPLv3 and third-party notices
  retained.

## B. Offline map

The verified BBBike western-zone Mapsforge extract came from
<https://download3.bbbike.org/osm/region/asia/india/western-zone/>. The original
`download.bbbike.org` route failed Windows Schannel trust validation
(`SEC_E_UNTRUSTED_ROOT`); certificate checks were never disabled and the
alternate source downloaded successfully.

- Source file: `C:\Users\satvi\Documents\SmartTrolleyOS\maps\bbbike-extract\western-zone-mapsforge-osm\western-zone.map`
- Size: 183,975,384 bytes; SHA-256
  `4C68952FFBE2E61632F481EC0272C99A5D94E084B7D270052B6E499A5AF51291`.
- Mapsforge reader validated its header and bounds (14.38929–24.71756 N,
  67.67457–80.91362 E), including Ahmedabad. OSM attribution is visible;
  ODbL and BBBike terms apply. See `OFFLINE_MAP_IMPLEMENTATION.md`.
- Installed at
  `/sdcard/Android/data/com.dashline.launcher/files/maps/a-western-zone.map`.
  Size on tablet: 183,975,384 bytes; tablet `md5` matched source
  `c8b14bfdeb65d09e2bb61633594e53e0`.
- ADB's initial large push returned EOF and left a truncated partial file. The
  verified transfer used 22 chunks. Only our incomplete transfer artifacts
  were removed; the completed map remains.
- On tablet, Ahmedabad street/road/water features and place labels were visibly
  rendered. ADB tap placed a coordinate marker; swipe changed the view/selected
  coordinate; zoom control visibly magnified detail. No post-fix fatal crash.
  This is **IMPLEMENTED AND DEVICE-TESTED** for rendering/tap/pan/zoom using
  injected ADB input, not a person's physical touchscreen.
- Wi-Fi remained enabled. `svc wifi disable` was denied with
  `CHANGE_WIFI_STATE` security exception, so disconnected-network behavior is
  **NOT VERIFIED**. The map renderer reads a local vector file and does not
  depend on tile downloads, but that is not a substitute for the offline
  network test.

## C. Railway

No authorized, documented NTES/CRIS developer API or RailYatri agreement,
credentials, or redistribution rights were configured. The railway screen
accepts and stores manual search terms; backend endpoints report
`UNKNOWN`/`NOT_AVAILABLE`. No local train timetable/station catalog, live
arrival/departure board, delay, or platform is supplied. Live railway data is
**BLOCKED BY PROVIDER ACCESS**; no scraping or undocumented endpoints were used.

## D. Airport

Offline reference search includes AMD, BOM, DEL, BLR from OurAirports
public-domain data. Names/codes/coordinates are reference data, not schedules;
selecting a record can show its location on the map.

Backend Aviationstack `/v1/airports` and `/v1/flights` adapters use documented
airport search, arrival/departure filters, and flight-number filter. They
normalize only supplied status/times/terminal/gate values. Provider parsing and
cache were tested with mocks only. No API key/live Indian flight result was
tested; Ahmedabad coverage, gate availability, account retention rights and
deployment remain unverified. Status: **IMPLEMENTED WITH MOCK PROVIDER ONLY**.
See `AIRPORT_API_RESEARCH.md` and `TRAVEL_BACKEND_SETUP.md`.

## E. Backend and routing

FastAPI provides health, navigation, railway, airport and flight endpoints;
provider credentials are environment-only. Android uses HTTPS with platform
certificate checks, 5s connect / 7s read timeouts and a 1 MiB response cap.
Server provider responses are capped, rate-limited, and cached in a bounded
in-memory LRU. There is no hosted endpoint configured; tablet-to-backend TLS
and live GraphHopper routing/geocoding are untested/not configured. The
development laptop is not a deployment dependency.

## F. Builds and tests

```powershell
$env:ANDROID_HOME='C:\Users\satvi\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat :app:assembleLegacyDebug :app:testLegacyDebugUnitTest
cd backend
python -m pytest -q
```

- Final tap-fix build: **SUCCESS**; Android unit tests **2 passed**; backend
  tests **7 passed** (including mock provider cases).
- Final APK `app/build/outputs/apk/legacy/debug/app-legacy-debug.apk`: 6,811,253
  bytes, SHA-256
  `AD43B26C0637B40CE58DBEDE51F9D9E0D41AE2C75FC2A4537B0B2D530385A937`.
- Latest APK backup: `C:\Users\satvi\Documents\SmartTrolleyOS\backups\phase4-tapfix.apk`
  (same SHA-256). Previous `phase4-recovery-build.apk`, Phase 2, and Phase 3
  backups remain preserved.
- `aapt` verified `com.dashline.launcher`, minSdk 19, landscape HOME activity
  `TrolleyHomeActivity`. APK signing certificate matches the Phase 3 backup.
- The fixed APK was installed with `adb install -r` and launched explicitly.
  No HOME preference was changed. The stock `com.android.launcher3` APK remains
  installed.

## G. Device and performance results

Before transfer: iBall `3G_Q45_2_16GB_`, API 19, `armeabi-v7a`; `/data` free
2.4 GiB and `/sdcard` 8.2 GiB. After transfer and removal of partial files,
`/sdcard` free was 8.0 GiB. `/data` remained 2.4 GiB. Display reports
600×1024 physical, 160 dpi; UIAutomator reports rotation 1 and logical
1024×552 content. All six internal pages showed their expected content via
ADB-injected navigation.

Map-screen PSS was 41,549 KiB (~40.6 MiB) in one sample; no controlled startup,
battery, or before/after benchmark. The battery service returned conflicting
duplicate values. No performance improvement is claimed. Wi-Fi state remained
on; no internet-loss transition or GPS fix test. Recent Logcat after the fix
showed no fatal exception for map interactions.

The first APK's map tap caused a Kotlin NPE because Mapsforge supplied null
`layerXY`. The callback was made nullable-safe, then rebuilt, reinstalled, and
the same tap test passed. Details: `PHASE4_DEVICE_TEST_REPORT.md`.

## H. Recovery and remaining work

Rollback without uninstall/data clearing:

```powershell
C:\platform-tools\adb.exe install -r "C:\Users\satvi\Documents\SmartTrolleyOS\backups\phase3-device-tested.apk"
```

Map file can remain in app storage. Launcher3 remains installed and was not
disabled; HOME selection was not changed. No package removal, user-data
clearing, firmware operation, or system modification occurred.

Remaining blockers: real offline-network-loss test, human touchscreen
confirmation, GPS fix, live railway provider agreement, Aviationstack
credentials/terms, independent backend deployment, Android 4.4 TLS to that
host, and live routing provider setup. No additional approval is needed for the
already-approved map transfer/APK installation; no HOME-default change is
performed.
