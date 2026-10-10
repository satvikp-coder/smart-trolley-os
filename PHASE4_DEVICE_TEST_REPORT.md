# Phase 4 device test report

## Read-only checks

- ADB serial: `0123456789ABCDEF`; model `3G_Q45_2_16GB_`; Android 4.4.2/API 19;
  ARMv7; 1024×600 landscape.
- `/data`: 2.4 GiB free; `/sdcard`: 8.2 GiB free.
- Current package list includes `com.dashline.launcher`; Launcher3 is installed
  and remains selected HOME. No Phase 4 APK was installed and no files were
  transferred to the tablet.
- `dumpsys location` exposes GPS internal state but no active GPS provider/fix;
  hardware reporting alone does not verify a usable fix.
- `dumpsys meminfo com.dashline.launcher`: PSS 27,421 KiB (one snapshot; activity
  state not controlled). No Phase 4 app memory comparison exists.
- Battery dump reported USB power, but duplicate battery records disagreed
  (22% present=true vs 50% present=false). Treat battery estimate as ambiguous.

## Not performed

No new APK install, touchscreen session, Mapsforge render, offline dataset test,
backend connectivity test, live provider query, or network-loss transition test
was performed. Stock launcher remains recoverable. No destructive package action
or HOME selection change occurred.

