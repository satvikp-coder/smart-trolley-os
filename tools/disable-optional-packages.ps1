param(
    [switch]$Apply,
    [string]$AdbPath = 'C:\platform-tools\adb.exe',
    [string]$StatePath = (Join-Path $PSScriptRoot '..\disabled-packages-state.json')
)

$ErrorActionPreference = 'Stop'
$candidates = @(
    'com.aim.racing',
    'com.ea.game.nfs14_row',
    'com.fungames.flightpilot',
    'com.nextwave.wcclite',
    'com.ua.building.Lokicraft',
    'mini.block.craft.free.mc'
)
if (-not (Test-Path -LiteralPath $AdbPath)) { throw "ADB not found: $AdbPath" }
$deviceState = & $AdbPath get-state
if ($LASTEXITCODE -ne 0 -or $deviceState.Trim() -ne 'device') { throw 'Expected one authorized ADB device.' }

$installed = @(& $AdbPath shell pm list packages | ForEach-Object { ($_ -replace '^package:', '').Trim() })
$disabled = @(& $AdbPath shell pm list packages -d | ForEach-Object { ($_ -replace '^package:', '').Trim() })
$enabled = @(& $AdbPath shell pm list packages -e | ForEach-Object { ($_ -replace '^package:', '').Trim() })
$actions = foreach ($packageName in $candidates) {
    if ($installed -notcontains $packageName) { continue }
    [pscustomobject]@{
        PackageName = $packageName
        OriginalState = if ($disabled -contains $packageName) { 'DISABLED' } elseif ($enabled -contains $packageName) { 'ENABLED' } else { 'UNKNOWN' }
    }
}
$actions | Format-Table -AutoSize

if (-not $Apply) {
    Write-Output 'Dry run only. No package state was changed. Obtain explicit owner approval before rerunning with -Apply.'
    return
}
if ($actions.Count -eq 0) { Write-Output 'No installed candidates.'; return }
if ($actions | Where-Object { $_.OriginalState -eq 'UNKNOWN' }) { throw 'Refusing to change a package with unknown initial state.' }
$confirmation = Read-Host 'Type DISABLE OPTIONAL GAMES to disable only the listed, currently enabled games'
if ($confirmation -cne 'DISABLE OPTIONAL GAMES') { throw 'Confirmation did not match; no changes made.' }

$state = [ordered]@{
    CreatedAt = (Get-Date).ToUniversalTime().ToString('o')
    DeviceSerial = (& $AdbPath get-serialno).Trim()
    ChangedPackages = @()
}
foreach ($action in $actions) {
    if ($action.OriginalState -eq 'DISABLED') { continue }
    $result = & $AdbPath shell pm disable-user --user 0 $action.PackageName
    if ($LASTEXITCODE -ne 0 -or $result -notmatch 'disabled') {
        $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $StatePath -Encoding UTF8
        throw "Disable failed for $($action.PackageName). Restore recorded changes with tools/restore-disabled-packages.ps1. Output: $result"
    }
    $state.ChangedPackages += $action.PackageName
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $StatePath -Encoding UTF8
    Write-Output "$($action.PackageName): $result"
}
Write-Output "Restoration record: $StatePath"
