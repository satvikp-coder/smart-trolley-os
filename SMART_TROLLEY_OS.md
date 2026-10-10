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
- Unit tests: `:app:testLegacyDebugUnitTest` reports `NO-SOURCE`; this repository
  has no unit-test source set.
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
- Navigation is embedded with osmdroid, coordinate markers, saved/recent
  destinations, and an optional HTTPS routing/geocoding proxy. On the tested
  API 19 tablet OSM basemap tile requests currently fail TLS negotiation; no
  proxy or offline region map is bundled. It does not provide indoor maps or
  indoor positioning.
- Railway and airport forms retain user-entered details through screen changes
  and activity recreation. Saved results are explicitly marked as manual.
- Demonstration buttons show sample records labeled `SIMULATION · NOT LIVE`.
- The trolley dashboard reports Android device and software details. External
  trolley battery status is clearly marked as not connected.
- An administrator maintenance screen links to Android Settings. Back returns
  to the dashboard and does not finish the launcher activity.

## Data and limitations

There is no live train or flight feed. NTES is linked as the official Indian
Railways information service at <https://enquiry.indianrail.gov.in/ntes/>. The
app does not scrape NTES or claim a private API. Manual entries and simulations
are kept distinct from live data.

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

Do not install this build until you are ready to prepare the tablet. Confirm
that USB debugging and the stock launcher work first, and leave adequate free
space on `/data` for installation and updates.

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
