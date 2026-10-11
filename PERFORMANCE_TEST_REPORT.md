# Phase 4 performance and resource report

## Observations

| Metric | Phase 3 reference | Phase 4 device observation | Interpretation |
|---|---:|---:|---|
| Dashboard PSS | ~26.5 MiB | Not measured | No comparison |
| Navigation PSS | ~28.7 MiB | 41,549 KiB (~40.6 MiB) with map open | Single uncontrolled sample; added renderer/data cost visible |
| APK size | 6,744,229 B | 6,811,253 B | +67,024 B; map file not in APK |
| Map file | None | 183,975,384 B external app data | Map itself occupies `/sdcard`, not `/data` |
| `/data` free | Prior reports ~639 MiB then ~2.4 GiB | 2.4 GiB | Current device sample after install |
| `/sdcard` free | Prior ~8.2 GiB | 8.0 GiB | After map transfer and temporary-file cleanup |
| Startup time | Not controlled | Not measured | No claim |

PSS was sampled once on Navigation with the map displayed. It is not a controlled
comparison with Phase 3; background load, uptime and measurement conditions
differ. The `41,549 KiB` number comes from `dumpsys meminfo` TOTAL PSS.

## Resource controls and outstanding work

Mapsforge is the sole active engine. Map cache uses the Mapsforge Android tile
cache helper; map resources are disposed when leaving the screen. The local
SQLite reference data has four airport entries and caps manual station queries
at 128. Backend response LRU defaults to 256 entries/120s and is server memory,
not device offline cache. HTTP operations have bounded timeouts and response
size limits. GPS is requested by the user and removed when leaving Navigation.

No RAM, startup, battery or responsiveness improvement is claimed. The map
renderer works and visibly draws Ahmedabad on this device, but a human touch
test, network-loss test, GPS fix, longer memory soak and repeated controlled
startup/PSS comparison are still outstanding.
