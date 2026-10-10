# Device cleanup audit — approval required

## Safety decision

This report is based on read-only ADB inspection. **No apps were disabled,
uninstalled, cleared, or otherwise changed.** The original Launcher3 remains
installed and selected as HOME. System firmware, boot configuration, accounts,
and personal files were not modified. The full package/path/state/classification
snapshot is `DEVICE_PACKAGE_INVENTORY.csv` (142 package records).

## Device snapshot

- Model: iBall 3G Q45 (2+16GB); Android 4.4.2 / API 19; `armeabi-v7a`.
- Display viewport: 1024×600 landscape; density: 160 dpi.
- `/data`: 639.2 MiB free in the latest reading (about 658 MiB at initial
  Phase 3 audit; earlier user-provided inspection was about 678 MiB).
- `/sdcard`: about 8.2 GiB free.
- Battery service reports USB powered and charging; the service exposes
  duplicate `present/status/level` records (present battery level 45%, also a
  `present=false` record at 50%). Do not treat a single service read as
  calibrated battery health. The app's own battery readings varied during
  prior testing.
- Wi-Fi was connected and Android reported Internet available during testing.
  Device clock read `Sat Oct 10 23:53:49 IST 2026` at latest snapshot.
- GPS hardware feature is reported by firmware; no GPS provider/fix was active
  in the inspected location state. No location setting was changed.
- Chrome and the stock Android browser are installed. KitKat's WebView is
  supplied by the system framework rather than a separately listed
  `com.android.webview` package on this tablet. Preserve both browsers and
  their framework dependencies for official information links.
- HOME: `com.android.launcher3/.Launcher` remains the selected HOME. Both the
  stock launcher and Smart Trolley launcher remain installed.
- Disabled package observed before cleanup: `com.google.android.ears` (not
  changed by this work).

## Categories and recommendations

Classification is intentionally conservative. `DEVICE_PACKAGE_INVENTORY.csv`
includes every listed package, APK path, enabled state, and one of these
categories: 29 CRITICAL, 4 REQUIRED, 6 OPTIONAL, and 103 UNKNOWN. Unknown
packages are not proposed for action. Package labels alone are not a safe basis
for cleanup.

### CRITICAL / REQUIRED — preserve

Protect Android framework/System UI, Settings, Package Installer/Container,
settings/media/download/content providers, input method, KeyChain/certificate
installer, location and connectivity services, power/storage/media support,
Google Play Services where present, ADB shell, WebView/browser dependencies,
the original launcher, and Smart Trolley OS. The inventory marks the identified
platform dependencies CRITICAL or REQUIRED. Do not disable anything in these
categories.

### OPTIONAL — proposed only; no changes made

The six candidates below are installed under `/data/app`, enabled, and clearly
identified as games by package name. APK code sizes total about 495,776,068
bytes (473 MiB) from the prior ADB file-size inspection. **Disabling saves no
APK storage** and retains the package, app data, saves, and accounts. It only
prevents normal launch and may avoid work if an app's components otherwise run.
No candidate appeared in the sampled top background-process list, so RAM/CPU
savings are not demonstrated or promised.

| Package | Purpose | APK code size | Dependencies / risk | Exact rollback |
|---|---|---:|---|---|
| `com.aim.racing` | Racing game | 148,294,908 B | Game/data and any game services become unavailable until re-enabled; data is retained. | `adb shell pm enable --user 0 com.aim.racing` |
| `com.ea.game.nfs14_row` | Need for Speed game | 94,085,577 B | EA game, saved state, sign-in, or notifications may be inaccessible while disabled; data retained. | `adb shell pm enable --user 0 com.ea.game.nfs14_row` |
| `com.fungames.flightpilot` | Flight simulator game | 109,794,478 B | Game components unavailable; data retained. | `adb shell pm enable --user 0 com.fungames.flightpilot` |
| `com.nextwave.wcclite` | Cricket game | 58,674,932 B | Game components unavailable; data retained. | `adb shell pm enable --user 0 com.nextwave.wcclite` |
| `com.ua.building.Lokicraft` | Craft/building game | 60,873,689 B | Game components unavailable; data retained. | `adb shell pm enable --user 0 com.ua.building.Lokicraft` |
| `mini.block.craft.free.mc` | Block/craft game | 24,052,484 B | Game components unavailable; data retained. | `adb shell pm enable --user 0 mini.block.craft.free.mc` |

Do not disable `com.google.android.play.games` on this recommendation: keep the
shared game-services dependency intact. Other entertainment/social/browser,
vendor, and system packages remain UNKNOWN unless individually verified; no
bulk recommendation is made.

## Reversible scripts

- `tools/disable-optional-packages.ps1` is **dry-run by default**. It only
  considers the six packages above; `-Apply` also requires typing the exact
  confirmation phrase and writes a device-specific restoration record after
  each successful change.
- `tools/restore-disabled-packages.ps1` re-enables only packages recorded as
  enabled and changed by that script, and refuses a different ADB serial.
- `tools/collect-device-package-inventory.ps1` refreshes the package snapshot.

No package-level operation may be performed until the owner approves the named
packages. Do not run `pm uninstall`, `pm clear`, or system-app removal as part
of this plan.
