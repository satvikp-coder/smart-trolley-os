# Smart Trolley OS

Smart Trolley OS is a landscape Android HOME launcher built on Dashline. The
existing firmware and drivers stay in place. The launcher is compatible with
Android API 19 and does not require Google Play Services.

## Build

From the repository root, run:

```powershell
.\gradlew.bat :app:assembleLegacyDebug
```

The APK is written to
`app/build/outputs/apk/legacy/debug/app-legacy-debug.apk`. The legacy flavor
uses `minSdk 19`. The APK contains no native ABI-specific libraries, so it can
run on 32-bit ARM Android. A local Android SDK with API 36 and Android Build
Tools 35 or newer is required by this project configuration.

## Build report

- Original baseline: `:app:assembleLegacyDebug` succeeded before the Smart
  Trolley changes.
- Smart Trolley build: `:app:assembleLegacyDebug` succeeds; the APK declares
  `minSdk 19`, landscape HOME, and label `Smart Trolley OS`.
- Unit tests: `:app:testLegacyDebugUnitTest` passes 2 Android unit tests; backend
  `python -m pytest -q` passes 7 tests using mocked/provider-free cases.
- Lint: `:app:lintLegacyDebug` completes. It reports inherited issues in the
  unused Dashline screens and widgets; the new `TrolleyHomeActivity` has no lint
  errors. The legacy issues include API 21 calls and old back-handler patterns.
- Phase 3 was installed and opened on the iBall API 19 tablet. The embedded map
  view opened, but HTTPS tile downloads failed KitKat TLS negotiation. Refer to
  `NAVIGATION_IMPLEMENTATION.md` and `PHASE3_FINAL_REPORT.md` for the actual
  device findings.

## What works

- Landscape HOME dashboard with large touch controls, clock, tablet battery and
  charging state, Wi-Fi state, and an HTTPS internet connectivity check.
- Navigation is embedded with Mapsforge 0.21, local `.map` discovery,
  coordinates, saved destinations, markers and route overlay. The verified
  western-zone dataset includes Ahmedabad and rendered on the tablet. ADB
  injected tap, pan and zoom passed after a callback nullability fix; human
  touchscreen and Wi-Fi-off behavior remain unverified. Online geocoding/routing require the configured
  HTTPS backend and provider credentials. Indoor maps/positioning are not
  implemented.
- Railway and airport forms retain user-entered details through screen changes
  and activity recreation. Saved results are explicitly marked as manual.
- Demonstration buttons show sample records labeled `SIMULATION · NOT LIVE`.
- The trolley dashboard reports Android device and software details. External
  trolley battery status is clearly marked as not connected.
- An administrator maintenance screen links to Android Settings. Back returns
  to the dashboard and does not finish the launcher activity.

## Data and limitations

No live train feed or timetable is configured. NTES is linked as the official
Indian Railways information service at <https://enquiry.indianrail.gov.in/ntes/>.
The app does not scrape NTES or claim a private API. Railway UI supports
manual train/station inputs and offline named-feature search from the Ahmedabad–
Gandhinagar Mapsforge region. Its all-day demo board is fictional and all
platform numbers are marked simulated. Offline airport reference lookup includes four static
records (AMD/BOM/DEL/BLR), sourced from OurAirports public-domain data; they are
not flight schedules. The Aviationstack adapter has mock tests, but no live
key/backend is configured. Manual, reference, cache and live categories remain
distinct.

FlightAware [AeroAPI](https://www.flightaware.com/commercial/aeroapi/) is a
possible authorized flight data provider, subject to its current account, API
key, and licensing terms. A production integration
should put credentials and provider access behind a secured service; this APK
contains no API key and displays no live flight status. Airport terminal maps
and indoor navigation also need a separately selected data source and a real
positioning method.

The HTTPS connectivity indicator tests access to Google's connectivity-check
endpoint. A blocked endpoint, captive portal, or unusual network can be shown
as `NO INTERNET` even if some websites are reachable. Wi-Fi state and internet
reachability are reported separately.

Android 4.4 immersive mode hides system bars where supported, but this is not a
security-grade kiosk. Hardware controls, Android system UI, and system error
screens may remain accessible. The installer does not automatically select a
default HOME app.

## Install and select the launcher

The current recovery APK and map are installed/transferred for testing. The
APK backup and prior Phase 3 APK are preserved. Launcher3 remains installed;
the default HOME selection has not been changed. See
`PHASE4_RECOVERY_REPORT.md` for hashes, rollback, and test results.

```powershell
C:\platform-tools\adb.exe install -r app/build/outputs/apk/legacy/debug/app-legacy-debug.apk
```

Press the tablet's Home button and select **Smart Trolley OS** in Android's
launcher chooser. Choose **Always** only when you want it to become the default
home screen. Installing the APK alone does not change the default launcher.

## Administrator and ADB recovery

Use **ADMIN · MAINTENANCE** in the dashboard to open Android Settings. To return
to the stock launcher, open the app details for Smart Trolley OS, clear its
**Launch by default** preference, then press Home and select the stock launcher.
Settings labels may vary on the iBall firmware.

ADB recovery commands (run from a computer with the Android platform tools):

```powershell
C:\platform-tools\adb.exe devices
C:\platform-tools\adb.exe shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.dashline.launcher
```

On the tablet, tap **Clear defaults** in the app details, then press Home and
choose the stock launcher. To remove only this app after restoring the stock
launcher:

```powershell
C:\platform-tools\adb.exe uninstall com.dashline.launcher
```

These steps do not disable, remove, or modify Android system applications.

## License

This work retains Dashline's GPLv3 license and upstream attribution:
<https://github.com/metehankaygsz/dashline>.
# Phase 4 implementation status

Phase 4 did not replace the functioning Phase 3 UI. The app remains an Android
HOME candidate on API 19 but has not been made the device default. Offline map
rendering, live rail/flight data, and locally cached reference datasets are not
implemented. The separate backend is source-only and has no configured
credentials or deployment. See `PHASE4_FINAL_REPORT.md` for actual checks and
remaining blockers.
