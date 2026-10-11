# Phase 4 physical-device test report

## Installation and identity

- ADB serial `0123456789ABCDEF`; iBall `3G_Q45_2_16GB_`, Android API 19,
  `armeabi-v7a`; `/data` free 2.4 GiB and `/sdcard` free 8.2 GiB before map
  transfer.
- Map file transferred in 22 chunks after the first one-shot ADB transfer
  returned EOF. Final device size 183,975,384 bytes and device MD5 matched
  laptop source (`c8b14bfdeb65d09e2bb61633594e53e0`). Incomplete files created by
  the failed first transfer were removed; final map retained.
- New APK `adb install -r` succeeded; `TrolleyHomeActivity` explicitly launched.
  Current APK is backed up as `phase4-tapfix.apk`. Original Launcher3 still
  exists. Default HOME was not changed.

## Tests performed

- **Map render:** verified screenshot visibly showed Ahmedabad label, roads,
  rail, water and surrounding map features from the installed offline map.
- **Tap and marker:** first build crashed. Logcat root cause:
  `NullPointerException` from Mapsforge passing null `layerXY` into Kotlin
  non-null `onTap` parameter. Changed callback parameters to nullable, rebuilt
  and reinstalled. Retest successfully set a map coordinate and displayed a
  marker; no subsequent fatal exception was observed.
- **Pan:** ADB swipe changed the view; tapping a fixed screen point returned a
  different coordinate (from about 23.0250, 72.5687 to 23.0276, 72.4891).
- **Zoom:** Mapsforge zoom control visibly increased road detail.
- **Screen navigation:** injected ADB taps opened Dashboard, Navigation,
  Railway, Airport, Trolley and Administrator; each displayed expected
  screen-specific content and activity remained alive.
- **Orientation:** UIAutomator rotation `1`, logical bounds 1024×552; physical
  panel reports 600×1024 at 160 dpi. Manifest remains landscape.
- **Memory:** map-screen PSS 41,549 KiB (~40.6 MiB), one uncontrolled sample.
- **Storage after map:** `/data` 2.4 GiB free; `/sdcard` 8.0 GiB free.
- **Logcat:** initial tap NPE captured; no fatal exception appeared in the
  post-fix map/screen interactions.

## Not verified

- A human physically touching the display; gestures above were injected via
  ADB. Please confirm touch feel and accuracy on the glass.
- Wi-Fi-off behavior: `adb shell svc wifi disable` was denied by Android
  `CHANGE_WIFI_STATE`; Wi-Fi remained on. The map is sourced from local storage,
  but a network-disconnected screenshot was not obtained.
- GPS fix/location marker, battery duration, controlled startup/PSS comparison,
  live providers, or tablet connection to an HTTPS backend.

The installed app is left on Navigation with the map visible. No package was
removed/disabled and no HOME default changed.
