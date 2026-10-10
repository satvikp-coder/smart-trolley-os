# Map data sources and licensing

The verified regional Mapsforge file was obtained from the BBBike western-zone
directory: <https://download3.bbbike.org/osm/region/asia/india/western-zone/>.
It is an OSM-derived Mapsforge vector extract and requires OpenStreetMap
attribution and ODbL compliance. The map is not included in this source tree or
APK. See `OFFLINE_MAP_IMPLEMENTATION.md` for filename, byte size, checksum,
coverage and exact local path. Review BBBike terms for any commercial
redistribution of extracts; this device-test preparation does not redistribute
the dataset.

The application displays “© OpenStreetMap contributors” while rendering the
map. OSM copyright/licence information:
<https://www.openstreetmap.org/copyright>.

Mapsforge Android renderer: `org.mapsforge:mapsforge-map-android:0.21.0`,
LGPL-3.0, source and license at <https://github.com/mapsforge/mapsforge>.
Original Dashline-derived project remains GPLv3; see `LICENSE`. Airport
reference coordinates are sourced from OurAirports' public-domain airport
data: <https://ourairports.com/data/> and
<https://raw.githubusercontent.com/davidmegginson/ourairports-data/main/airports.csv>.
