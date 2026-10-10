# Phase 4 final report

## A. Overall status — PARTIALLY COMPLETE

The Phase 3 application and APK checkpoints are preserved. The API-19 baseline
build succeeds. Provider-neutral FastAPI endpoints and backend contract tests
were added, but no map data, live provider credentials, backend deployment, or
Phase 4 tablet installation is available. Offline map rendering, live railway
boards, live flight results, and actual API 19 connectivity are BLOCKED / NOT
VERIFIED. No destructive or default-launcher changes occurred.

## B. Completed

- Branch `phase4-offline-travel`; Phase 3 source checkpoint commit `e8c5f84`.
- Backend routes, input validation, timeout handling, configurable rate limits,
  bounded cache, explicit cache freshness, and server-side credential config.
- GraphHopper geocode/route and Aviationstack airport/flight adapter code; no
  credentials configured, so live external calls were not validated.
- Railway endpoints return an honest unavailable/not-configured envelope.
- Backend tests cover health, missing credentials, validation, and secret
  non-echoing.
- Cleanup remains proposal-only; stock Launcher3 remains installed/selected.

## C. Actual live integrations

None. GraphHopper and Aviationstack require backend credentials; neither has
been supplied or tested. Indian railway developer access has not been verified.
No independently reachable service is deployed.

## D. Offline

Existing app coordinate pin/saved places work from Phase 3; no offline basemap
or station/airport reference DB exists. Mapsforge 0.21 resolved during
compatibility evaluation but was not integrated. BBBike supports Mapsforge
custom extracts, but a trusted download failed certificate validation; no
dataset was obtained or transferred. [BBBike](https://extract.bbbike.org/extract.html)

## E. Device optimization

PSS 27,421 KiB (~26.8 MiB) observed once; comparison to prior ~26.5 MiB is not
controlled. No app optimization or startup-time improvement is claimed. Storage
free now reads 2.4 GiB `/data`, 8.2 GiB `/sdcard`.

## F. Cleanup

The six historical game candidates were not found in the current user package
list; no cleanup candidate is proposed now. The Phase 3 audit estimated their
combined APK code at ~473 MiB, but current installed states differ. No packages
were touched. See `DEVICE_CLEANUP_APPROVAL.md`.

## G. Build

`:app:assembleLegacyDebug` and `:app:testLegacyDebugUnitTest` succeeded. The
Android unit-test task reports `NO-SOURCE`. APK:
`app/build/outputs/apk/legacy/debug/app-legacy-debug.apk`, 6,744,229 bytes,
package `com.dashline.launcher`, minSdk 19 / targetSdk 36. Since no Android app
source changed and Mapsforge was not adopted, this is the preserved Phase 3
application build; it does not contain Phase 4 offline map or live provider
client features.

The standalone backend test suite passed 5/5 tests, including mocked airport
normalization/cache behavior. Mock tests do not establish live provider access.

## H. Device testing

Read-only ADB only: authorized iBall device, API19/ARMv7, display 1024×600,
Launcher3 selected HOME, storage checked, GPS state inspected, and memory
snapshot taken. No new APK installed, no map transferred, no touchscreen tests.

## I. Blockers

- Ahmedabad Mapsforge extract not obtained; certificate validation failed.
- No API credentials/data agreements (railway especially).
- No HTTPS host deployment and Android 4.4 TLS handshake test.
- No local station/airport reference dataset/cache.
- Physical touch/map and Phase 4 runtime checks remain undone.

## J. Approval needed

- Explicit package-by-package approval before uninstalling optional games.
- Approval before transferring a map dataset or installing a Phase 4 APK.
- Approval before changing HOME selection. None of these actions was performed.

