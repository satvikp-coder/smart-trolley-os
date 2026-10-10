# Phase 4 physical-device test report

## Recovery-build pass

At initial check, ADB detected serial `0123456789ABCDEF`, model
`3G_Q45_2_16GB_`, API 19, ARMv7. Read-only storage showed 2.4 GiB `/data` and
8.2 GiB `/sdcard` free. After owner approval, map push reported 183,975,384
bytes sent but returned `failed to read copy response: EOF`; the tablet then
disappeared from ADB before remote hash/size verification. The app map copy is
unverified. The APK was **not installed**. No package or HOME changes occurred.

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

After reconnect, verify the map file size/checksum, repeat the transfer only if
needed, then install the APK using the already-approved operation. The detailed
commands and measured data are in `PHASE4_RECOVERY_REPORT.md`.
