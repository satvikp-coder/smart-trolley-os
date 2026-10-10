param(
    [string]$AdbPath = 'C:\platform-tools\adb.exe',
    [string]$StatePath = (Join-Path $PSScriptRoot '..\disabled-packages-state.json')
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $StatePath)) { throw "No recorded disable-state file: $StatePath" }
if (-not (Test-Path -LiteralPath $AdbPath)) { throw "ADB not found: $AdbPath" }
$deviceState = & $AdbPath get-state
if ($LASTEXITCODE -ne 0 -or $deviceState.Trim() -ne 'device') { throw 'Expected one authorized ADB device.' }
$state = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
if ($state.DeviceSerial -ne (& $AdbPath get-serialno).Trim()) { throw 'State record belongs to a different device.' }
foreach ($packageName in $state.ChangedPackages) {
    $result = & $AdbPath shell pm enable --user 0 $packageName
    if ($LASTEXITCODE -ne 0 -or $result -notmatch 'enabled') { throw "Restore failed for $packageName. Output: $result" }
    Write-Output "${packageName}: $result"
}
