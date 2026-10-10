# Phase 4 device cleanup approval proposal

**No package changes have been made.** The last connected-device audit was
read-only and reported `/data` 2.4 GiB free and `/sdcard` 8.2 GiB free, but ADB
currently sees no device, so those figures are not freshly verified. Phase 3
reported Launcher3 installed and selected as HOME; no HOME change was made.

## Current recheck — none of the six candidates found

Read-only `pm list packages -3` currently shows Adobe Flash Player/Reader,
WhatsApp, Dashline, `com.zc.cc2016`, Play Games, YouTube, and Chrome. None of
the six historical game packages below appears in the current package list or
`pm path` results. No removal is proposed now. This may reflect a state change
since the Phase 3 snapshot; no package operation was performed by this phase.

## Historical Phase 3 candidates (not currently present)

Phase 3 audit identified the following six user-installed game packages. Their
APK sizes are historical inspected sizes, not remeasured in this pass; sizes
total approximately 473 MiB. APK code size is not guaranteed reclaimed space:
installation data, optimized code, and app data affect the result. Uninstalling
removes the package's private app data and saved games. Reinstalling its APK
does **not** restore that data. First export/backup desired saves through each
app's own supported mechanism; no backup is currently verified. None was seen
in the last sampled top process list, so no RAM/CPU saving is claimed.

| Package | Identity/purpose | Historical APK size | Risk and loss | Rollback |
|---|---|---:|---|---|
| `com.aim.racing` | Racing game | 148,294,908 B | Private game data/saves removed; reinstall may not restore | Reinstall original APK from a verified backup; restore data only from app-supported backup |
| `com.ea.game.nfs14_row` | Need for Speed game | 94,085,577 B | EA game state/sign-in data removed | Same; no original APK backup verified |
| `com.fungames.flightpilot` | Flight simulator | 109,794,478 B | Game state removed | Same; no original APK backup verified |
| `com.nextwave.wcclite` | Cricket game | 58,674,932 B | Game state removed | Same; no original APK backup verified |
| `com.ua.building.Lokicraft` | Craft/building game | 60,873,689 B | World/save data removed | Same; no original APK backup verified |
| `mini.block.craft.free.mc` | Block/craft game | 24,052,484 B | World/save data removed | Same; no original APK backup verified |

If a package is reinstalled later, first recheck package identity and data. The
template uninstall command is `adb shell pm uninstall --user 0 PACKAGE`; it is
not reversible from the device alone without the original APK and does not
restore deleted app data. Do not use `pm clear` or delete files directly. The
103 UNKNOWN audit packages are not proposed for action.

## Approval boundary

No action is currently requested. If an exact candidate is found installed in a
future audit, provide its exact package name to request uninstall approval.

