# Performance test report

Device: iBall Slide 3G Q45 (2+16GB), Android 4.4.2/API 19, ARMv7, 1024×600
logical landscape, 160 dpi. Values below are single ADB observations, not a
controlled benchmark or a claim of improvement.

| Measurement | Phase 2 baseline | Phase 3 test build | Notes |
|---|---:|---:|---|
| Explicit activity start | 959 ms | 1,801 ms cold start; later 736 ms after update | Single `am start -W` samples; timing is noisy and not directly comparable. |
| App TOTAL PSS | 25,646 kB | 28,709 kB while navigation screen/map view was open | Activity/page states differ; map adds memory. Dashboard reading after start was 26,462 kB. |
| `/data` free | about 678 MiB | about 650 MiB after install/dexopt/cache | System-wide free-space readings vary. |
| APK file | 6,460,423 bytes | 6,744,229 bytes at tested build | Built APK on host; device package install is stored/dex-optimized separately. |

The map uses one download worker, bounded queues, up to 24 in-memory tiles,
and a 32 MiB on-disk tile cap trimmed toward 24 MiB. The measured tile cache
database was only a few KiB because TLS tile requests failed. No background
polling service was introduced. Battery/network observers and periodic UI
refresh behavior are inherited from the functioning dashboard.

No automated unit tests exist (`testLegacyDebugUnitTest` reports `NO-SOURCE`).
The 1024×600 hierarchy was inspected on device. Dashboard and all five module
screens stayed in the app; no fatal exception was observed in the inspected
Logcat window. The principal performance/functionality blocker is the real
OSM tile TLS failure, described in `NAVIGATION_IMPLEMENTATION.md`.
