param(
    [string]$DeviceSerial = 'f6zh89or49vorgin',
    [string]$SessionId = 'phase-c',
    [string]$Scenario = 'WECHAT_CLERK_INCOME',
    [switch]$StartMonitoring
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.example.paymentmonitor'

& adb -s $DeviceSerial get-state | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "ADB device is unavailable: $DeviceSerial"
}

$installed = & adb -s $DeviceSerial shell pm list packages $packageName
if ($installed -notmatch [regex]::Escape($packageName)) {
    throw "Debug app is not installed on $DeviceSerial"
}

if ($StartMonitoring) {
    & adb -s $DeviceSerial shell am broadcast `
        -a 'com.example.paymentmonitor.DEBUG_START_MONITORING' `
        -n "$packageName/.debug.DebugFixtureReceiver" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to start notification monitoring'
    }
}

& adb -s $DeviceSerial logcat -c
& adb -s $DeviceSerial shell am broadcast `
    -a 'com.example.paymentmonitor.DEBUG_CAPTURE_START' `
    -n "$packageName/.debug.DebugFixtureReceiver" `
    --es captureSession $SessionId `
    --es captureScenario $Scenario | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to start phase C capture'
}

Start-Sleep -Seconds 1
& adb -s $DeviceSerial shell am broadcast `
    -a 'com.example.paymentmonitor.DEBUG_STATE' `
    -n "$packageName/.debug.DebugFixtureReceiver" | Out-Null
Start-Sleep -Milliseconds 500

$state = & adb -s $DeviceSerial logcat -d -s 'PaymentMonitorDebug:I' '*:S'
$state | Select-Object -Last 1
Write-Host "Phase C capture started on $DeviceSerial"
Write-Host "Session:  $SessionId"
Write-Host "Scenario: $Scenario"
