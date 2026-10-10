# Phase 4 performance and resource report

## Measurements available

| Metric | Phase 3 observation | Phase 4 recovery observation | Notes |
|---|---:|---:|---|
| Dashboard PSS | ~26.5 MiB | Not measured | No comparable on-device run |
| Navigation PSS | ~28.7 MiB | Not measured | Map was not transferred/rendered |
| APK | 6,744,229 B prior APK | 6,810,977 B | +66,748 B; Mapsforge code/assets included, map data excluded |
| Map dataset | None on tablet | 183,975,384 B on laptop | Transfer not approved/performed |
| Startup time | Not controlled | Not measured | No performance claim |
| `/data` free | Prior report ~639 MiB, later report ~2.4 GiB | Not re-read in this pass | ADB currently reports no connected device |

The APK SHA-256 is recorded in `PHASE4_RECOVERY_REPORT.md`. The map uses a
256-pixel tile cache; destination/recent state remains small app preferences.
SQLite reference content currently seeds only four airport records and up to
128 manually saved station search strings. Backend response cache is a bounded
in-memory LRU (default cap 256 entries, TTL 120 seconds); it is server memory,
not an Android offline cache.

## Changes and limitations

Mapsforge is the only active map engine; osmdroid is no longer instantiated.
The activity disposes map view/tile cache/map reader on page exit and handles
missing/corrupt map data without crashing intentionally, but lifecycle and
memory behavior still require device testing. Network calls use bounded
timeouts and JSON response caps; there is no automatic polling. GPS updates are
requested on demand and stopped when leaving Navigation.

No measured RAM or battery improvement is claimed. Compare PSS and cold/warm
startup after installation using the same device conditions; verify memory
under map rendering before asserting the 2 GB device is adequate.
