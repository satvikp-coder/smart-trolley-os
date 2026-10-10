# Phase 4 physical-device test report

## Recovery-build pass

At this pass, `C:\platform-tools\adb.exe devices -l` returned an empty device
list and `adb shell` responded `no devices/emulators found`. The iBall could not
be rechecked or tested. No install, map transfer, package action, HOME selection
change, or device write was performed.

## Last known device baseline

Phase 3 reports record iBall Slide 3G Q45 / `3G_Q45_2_16GB_`, Android 4.4.2
API 19 / ARMv7 / 1024×600, stock Launcher3 present and selected as HOME, and
successful launch of the earlier Smart Trolley build. Previous storage figures
were approximately 2.4 GiB free in `/data` and 8.2 GiB on `/sdcard`; these are
historical until ADB reconnects. The six optional-game packages were absent in
the prior connected package inventory; no removal by this project is inferred.

## Phase 4 not yet tested on tablet

- Mapsforge renderer has not rendered the western-zone dataset on-device.
- No pan/zoom, offline rendering, GPS marker, startup time, PSS, battery or
  touchscreen test of this build occurred.
- No configured backend exists for a tablet TLS/health test.
- No live railway/flight source credentials were supplied.
- Original Launcher3 recovery was preserved by making no package/default
  changes; its current runtime state was not re-read while disconnected.

After owner approval and reconnect, copy the map and install with rollback
ready. The detailed commands and measured data are in
`PHASE4_RECOVERY_REPORT.md`.
