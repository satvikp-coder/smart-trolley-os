# Offline map implementation status

## Status: NOT IMPLEMENTED / BLOCKED ON MAP DATA

Phase 3 osmdroid MapView remains intact so the known-working dashboard and
coordinate pin workflow are not regressed. Its online OSM tiles failed TLS on
this API 19 tablet. Phase 4 verified that Mapsforge is an appropriate candidate:
the official project describes offline vector `.map` rendering and API 10+
support; artifact `org.mapsforge:mapsforge-map-android:0.21.0` resolves from
Maven Central. The artifact was evaluated, not integrated into the app. No claim
of offline map rendering is made. [Mapsforge](https://github.com/mapsforge/mapsforge),
[0.21 changelog](https://github.com/mapsforge/mapsforge/blob/0.21.0/docs/Changelog.md)

BBBike supports Mapsforge output, but requires a custom extract request for
Ahmedabad. Its current directory contains other regional maps but no verified
Ahmedabad prebuilt file. Download attempt from this Windows environment failed
certificate validation (`SEC_E_UNTRUSTED_ROOT`). TLS validation was not bypassed;
no map file was downloaded or sent to the tablet. The tablet has 8.2 GiB free on
`/sdcard`, but no space was consumed. [BBBike formats and service help](https://extract.bbbike.org/extract.html)

Next safe step: obtain a small Ahmedabad extract through BBBike's supported
Mapsforge request process, verify its `.map` format and checksum, then integrate
and test Mapsforge 0.21 on API 19 before replacing osmdroid. User approval is
required before transferring the dataset to the tablet. Mapsforge is LGPL-3.0;
OpenStreetMap data attribution and ODbL obligations apply.

