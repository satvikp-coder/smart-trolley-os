# Offline map implementation

## Status

**IMPLEMENTED AND DEVICE-TESTED (ADB-injected interaction).** The app uses Mapsforge 0.21.0 for
vector maps and no longer instantiates osmdroid. The renderer checks a local
Mapsforge `.map` file, validates its header and geographic bounds, renders
vector tiles with pan/zoom, supports tap-to-pin, saved destination/current
location markers, route overlays, scale bar, viewport persistence, and cleanup
on page changes. Missing or invalid files produce an in-app explanation.

Map location on Android 4.4:
`/sdcard/Android/data/com.dashline.launcher/files/maps/`
(app-specific external files; internal `files/maps` is used if external storage
is unavailable). Put one `.map` file in this directory. Dataset is deliberately
not bundled in the APK.

## Verified dataset

BBBike's regional Mapsforge OSM extract was downloaded from its western-zone
directory using normal Windows certificate validation:
<https://download3.bbbike.org/osm/region/asia/india/western-zone/>. The resulting
file is at `C:\Users\satvi\Documents\SmartTrolleyOS\maps\bbbike-extract\western-zone-mapsforge-osm\western-zone.map`.
It is 183,975,384 bytes (about 175.5 MiB), SHA-256
`4C68952FFBE2E61632F481EC0272C99A5D94E084B7D270052B6E499A5AF51291`. Its
Mapsforge reader header is valid; bounds are approximately 14.38929–24.71756 N,
67.67457–80.91362 E and include Ahmedabad (23.0225, 72.5714). Generated
2026-10-07 per the extract README. The archive's `CHECKSUM.txt` MD5 was also
verified after extraction. It is a broad western India regional map, not a
small city-only file.

The official Mapsforge western-zone map is a larger fallback (213,018,151
bytes); it was not selected. The verified dataset is installed at
`/sdcard/Android/data/com.dashline.launcher/files/maps/a-western-zone.map`;
device MD5 matched the local file. The laptop map occupies storage outside the
repository and APK.

## Acquisition note and limits

The initial `download.bbbike.org` route failed Windows Schannel trust with
`SEC_E_UNTRUSTED_ROOT`; certificate validation was not disabled. The alternate
BBBike `download3` directory/data host and official Mapsforge site validated
normally, so a legitimate extract was obtained. The map rendered on the tablet;
ADB taps placed a marker, a swipe panned, and zoom magnified map detail. The
first build's tap handler crashed when Mapsforge supplied null layer coordinates;
the nullable-safe fix is installed and the same interaction passed. Wi-Fi-off
behavior is not verified because Android denied the ADB Wi-Fi toggle. See
`PHASE4_DEVICE_TEST_REPORT.md`.

Attribution: “© OpenStreetMap contributors” is shown in the map screen. See
`MAP_DATA_SOURCE_AND_LICENSE.md` for ODbL/source details. The app uses
Mapsforge's bundled default render theme, not online tile downloads.
