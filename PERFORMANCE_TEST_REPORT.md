# Phase 4 performance report

## Measurements

| Metric | Prior Phase 3 reference | Phase 4 observation | Comparison |
|---|---:|---:|---|
| Dashboard PSS | ~26.5 MiB (single sample) | 27,421 KiB (~26.8 MiB) | Not controlled; broadly similar, no improvement claimed |
| Navigation PSS | ~28.7 MiB | Not measured | No comparison |
| APK size | ~6.7 MB Phase 3 | Rebuild pending final validation | Not comparable yet |
| `/data` free | ~639 MiB earlier | 2.4 GiB current | Different snapshot; not attributable to app changes |
| Startup time | No controlled baseline | Not measured | No claim |

The device memory sample was a read-only ADB `dumpsys meminfo` observation.
Background load, dashboard uptime, and thermal/battery conditions were not
controlled. No app optimization was made in this phase. A bounded backend
in-memory LRU cache limits service memory to 256 keys, but it is not an Android
performance improvement.

## Outstanding measurements

Collect multiple cold/warm startup timings and PSS for Dashboard/Navigation
before and after a tested map integration, under matching network and process
conditions. The map layer is not integrated and no map PSS/battery measurement
exists.
