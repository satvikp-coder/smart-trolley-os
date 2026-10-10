# Smart Trolley OS Phase 3 final report

## Status

- Checkpoint branch: `phase3-embedded-navigation`; pre-Phase-3 working baseline
  commit: `857c96a`.
- Legacy debug build: `:app:assembleLegacyDebug` **passed**; unit-test task
  **NO-SOURCE**. APK package `com.dashline.launcher`, minSdk 19, HOME activity
  `TrolleyHomeActivity`, landscape configuration, ARMv7-compatible. Device
  accepted the APK update and explicit activity start; default HOME was not
  changed.
- Internal Dashboard, Navigation, Railway, Airport, Trolley, and Administrator
  screens were opened on the tablet. No fatal app crash was found in the
  inspected logs. Stock `com.android.launcher3` remains installed and selected
  as HOME.
- The embedded map view renders and has attribution, coordinate/search controls,
  saved places, GPS permission path, and route UI. **Live basemap tiles fail on
  this Android 4.4.2 device due to TLS negotiation.** No online geocoding or
  route provider is configured; those states are explicitly unavailable, not
  simulated. See `NAVIGATION_IMPLEMENTATION.md`.
- Cleanup audit completed; no package was disabled/uninstalled and no user data
  was cleared. The recommended optional list is six games only. Approval is
  still required before running the cleanup script with `-Apply`.
- No changes were made to firmware, root state, boot configuration, or default
  launcher.

## Recovery and installation

Known-good Phase 2 APK backup before Phase 3:
`C:\Users\satvi\Documents\SmartTrolleyOS\backups\phase2-known-good.apk`
(SHA-256 `80172992DC39AEB57B1C4B2EBBA6BED08D911632F8FCAC99F2040D19F31BBE42`).
Phase 3 device-tested APK copy:
`C:\Users\satvi\Documents\SmartTrolleyOS\backups\phase3-device-tested.apk`.
The build output is `app/build/outputs/apk/legacy/debug/app-legacy-debug.apk`.

Update/launch without changing HOME:

```powershell
C:\platform-tools\adb.exe install -r app\build\outputs\apk\legacy\debug\app-legacy-debug.apk
C:\platform-tools\adb.exe shell am start -n com.dashline.launcher/.TrolleyHomeActivity
```

To restore the old APK, use `adb install -r` with the backup path above. If the
APK signature differs, stop: do not uninstall or clear app data; the existing
stock launcher can be opened from Android's Home chooser/settings. To restore
stock Home manually, choose Launcher3 in Android Settings → Home (label may
vary by firmware). Smart Trolley OS has not been selected as default.

## Remaining work

1. Resolve and device-test a permitted TLS-compatible tile source or add a
   properly licensed regional offline vector map.
2. Configure and validate an authorized HTTPS geocoding/routing proxy if
   real search and directions are desired.
3. Ask for explicit cleanup approval before disabling any listed game package.
4. Physically verify touchscreen feel, GPS fix outdoors, Wi-Fi transitions,
   tile rendering after transport fix, and charging/battery readings.
