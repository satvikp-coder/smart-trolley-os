param(
    [string]$AdbPath = 'C:\platform-tools\adb.exe',
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\DEVICE_PACKAGE_INVENTORY.csv')
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw "ADB not found: $AdbPath" }
$deviceState = & $AdbPath get-state
if ($LASTEXITCODE -ne 0 -or $deviceState.Trim() -ne 'device') { throw 'Expected one authorized ADB device.' }

$paths = & $AdbPath shell pm list packages -f
$disabled = @(& $AdbPath shell pm list packages -d | ForEach-Object { ($_ -replace '^package:', '').Trim() })
$enabled = @(& $AdbPath shell pm list packages -e | ForEach-Object { ($_ -replace '^package:', '').Trim() })
$optional = @(
    'com.aim.racing',
    'com.ea.game.nfs14_row',
    'com.fungames.flightpilot',
    'com.nextwave.wcclite',
    'com.ua.building.Lokicraft',
    'mini.block.craft.free.mc'
)
$required = @('com.dashline.launcher', 'com.android.launcher3', 'com.android.chrome', 'com.android.browser')
$critical = @(
    'android', 'com.android.systemui', 'com.android.settings', 'com.android.packageinstaller',
    'com.android.defcontainer', 'com.android.providers.settings', 'com.android.providers.media',
    'com.android.providers.downloads', 'com.android.providers.downloads.ui', 'com.android.inputmethod.latin',
    'com.android.keychain', 'com.android.certinstaller', 'com.android.location.fused',
    'com.google.android.gms', 'com.android.phone', 'com.android.server.telecom', 'com.android.shell',
    'com.android.externalstorage', 'com.android.providers.contacts', 'com.android.providers.calendar',
    'com.android.providers.telephony', 'com.android.providers.userdictionary', 'com.android.bluetooth',
    'com.android.nfc', 'com.android.sharedstoragebackup', 'com.android.backupconfirm',
    'com.google.android.inputmethod.latin',
    'com.android.vpndialogs', 'com.android.proxyhandler', 'com.android.htmlviewer',
    'com.android.providers.applications', 'com.android.providers.partnerbookmarks',
    'com.android.providers.blockednumber', 'com.android.providers.settings'
)

$rows = foreach ($entry in $paths) {
    if ($entry -notmatch '^package:(.+)=(.+)$') { continue }
    $apkPath = $Matches[1].Trim()
    $packageName = $Matches[2].Trim()
    $state = if ($disabled -contains $packageName) { 'DISABLED' } elseif ($enabled -contains $packageName) { 'ENABLED' } else { 'STATE_UNKNOWN' }
    $classification = if ($optional -contains $packageName) { 'OPTIONAL' }
        elseif ($required -contains $packageName) { 'REQUIRED' }
        elseif ($critical -contains $packageName) { 'CRITICAL' }
        else { 'UNKNOWN' }
    [pscustomobject]@{
        PackageName = $packageName
        ApkPath = $apkPath
        State = $state
        Classification = $classification
    }
}

$rows | Sort-Object PackageName | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
Write-Output "Wrote $($rows.Count) package records to $OutputPath"
Write-Output (($rows | Group-Object Classification | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', ')
