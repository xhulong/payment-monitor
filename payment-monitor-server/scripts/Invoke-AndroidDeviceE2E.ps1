param(
    [string]$DeviceSerial,
    [string]$ServerUrl,
    [string]$AndroidRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '..\PaymentNotificationMonitor'),
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    [string]$JavaHome = 'D:\toolbox\Android Studio\jbr',
    [string]$BuildJunctionPath = 'D:\pmandroid',
    [switch]$SkipBuild,
    [switch]$KeepInstalled,
    [switch]$SkipOfflineTest,
    [switch]$SkipRevocationTest
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$androidRootResolved = [System.IO.Path]::GetFullPath($AndroidRoot)
$mainApk = Join-Path $androidRootResolved 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $androidRootResolved 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$runner = 'com.example.paymentmonitor.test/.PaymentMonitorTestRunner'

function Read-EnvironmentFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing environment file: $Path"
    }
    $result = @{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line.Split('=', 2)
            $result[$parts[0]] = $parts[1]
        }
    }
    return $result
}

function Invoke-Adb([string[]]$Arguments, [switch]$AllowFailure) {
    $output = & $AdbPath -s $DeviceSerial @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "ADB failed with exit code $exitCode`: adb $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return @($output)
}

function Invoke-Instrumentation(
    [string]$TestClass,
    [hashtable]$Arguments = @{}
) {
    $adbArguments = @('shell', 'am', 'instrument', '-w', '-r')
    foreach ($key in ($Arguments.Keys | Sort-Object)) {
        $adbArguments += @('-e', $key, $Arguments[$key].ToString())
    }
    $adbArguments += @('-e', 'class', $TestClass, $runner)
    $output = Invoke-Adb $adbArguments
    $text = $output -join "`n"
    Write-Host $text
    if (
        $text -match 'FAILURES!!!' -or
        $text -match 'INSTRUMENTATION_FAILED' -or
        $text -notmatch 'OK \(\d+ test'
    ) {
        throw "Instrumentation test failed: $TestClass"
    }
}

function Invoke-DatabaseCommand([string]$Sql, [switch]$Scalar) {
    $output = & docker exec -e "PGPASSWORD=$($script:environment['DB_PASSWORD'])" `
        payment-monitor-postgres psql -v ON_ERROR_STOP=1 `
        -U payment_monitor -d payment_monitor -t -A -P pager=off -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Database command failed:`n$($output -join "`n")"
    }
    if ($Scalar) {
        return ($output -join "`n").Trim()
    }
    Write-Host ($output -join "`n")
}

function Get-Sha256Hex([string]$Value) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString(
            $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value))
        )).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Ensure-BuildJunction {
    if (Test-Path -LiteralPath $BuildJunctionPath) {
        $item = Get-Item -LiteralPath $BuildJunctionPath -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) {
            throw "$BuildJunctionPath exists and is not a junction."
        }
        $existingTarget = [System.IO.Path]::GetFullPath([string]$item.Target)
        if (-not $existingTarget.Equals(
            $androidRootResolved,
            [StringComparison]::OrdinalIgnoreCase
        )) {
            throw "$BuildJunctionPath points to $existingTarget instead of $androidRootResolved."
        }
        return
    }
    New-Item -ItemType Junction -Path $BuildJunctionPath -Target $androidRootResolved | Out-Null
}

if (-not (Test-Path -LiteralPath $AdbPath)) {
    throw "ADB was not found: $AdbPath"
}
if (-not (Test-Path -LiteralPath $JavaHome)) {
    throw "Java home was not found: $JavaHome"
}

$script:environment = Read-EnvironmentFile $envFile
if ([string]::IsNullOrWhiteSpace($ServerUrl)) {
    $ServerUrl = $script:environment['PAYMENT_PUBLIC_BASE_URL']
}
if ([string]::IsNullOrWhiteSpace($ServerUrl)) {
    throw 'ServerUrl is required and PAYMENT_PUBLIC_BASE_URL is not configured.'
}
$ServerUrl = $ServerUrl.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $devices = & $AdbPath devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '\sdevice$' } |
        ForEach-Object { ($_ -split '\s+')[0] }
    if (@($devices).Count -ne 1) {
        throw "Expected exactly one online ADB device, found $(@($devices).Count)."
    }
    $DeviceSerial = $devices[0]
}

& (Join-Path $PSScriptRoot 'Start-LocalDockerStack.ps1') -SkipBuild

if (-not $SkipBuild) {
    Ensure-BuildJunction
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
    Push-Location $BuildJunctionPath
    try {
        & '.\gradlew.bat' testDebugUnitTest assembleDebug assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) {
            throw "Android build failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $mainApk)) {
    throw "Missing Debug APK: $mainApk"
}
if (-not (Test-Path -LiteralPath $testApk)) {
    throw "Missing AndroidTest APK: $testApk"
}

if (-not $KeepInstalled) {
    Invoke-Adb @('uninstall', 'com.example.paymentmonitor.test') -AllowFailure | Out-Null
    Invoke-Adb @('uninstall', 'com.xhulong.paymentmonitor') -AllowFailure | Out-Null
}

Write-Host "Installing APKs on $DeviceSerial ..."
Invoke-Adb @('install', '-r', '-t', $mainApk) | Write-Host
Invoke-Adb @('install', '-r', '-t', $testApk) | Write-Host
Invoke-Adb @(
    'shell',
    'cmd',
    'notification',
    'allow_listener',
    'com.example.paymentmonitor/com.example.paymentmonitor.monitor.PaymentNotificationListenerService',
    '0'
) | Out-Null

Invoke-Instrumentation 'com.example.paymentmonitor.data.PaymentDatabaseMigrationTest'
Invoke-Instrumentation 'com.example.paymentmonitor.data.PaymentDatabaseTest'
Invoke-Instrumentation 'com.example.paymentmonitor.capture.NotificationCaptureTest'
Invoke-Instrumentation `
    'com.example.paymentmonitor.sync.DeviceEndToEndTest#foregroundMonitorServiceStartsAndStops'

$pairing = & (Join-Path $PSScriptRoot 'New-LocalPairingCode.ps1') -TtlMinutes 15
Invoke-Instrumentation `
    'com.example.paymentmonitor.sync.DeviceEndToEndTest#pairAndUploadFourFixtures' `
    @{ serverUrl = $ServerUrl; pairingCode = $pairing.PairingCode }
Invoke-Instrumentation `
    'com.example.paymentmonitor.sync.DeviceEndToEndTest#incomeUsesImmediateSyncWhileExpenseStaysDeferred'

$pairingCodeHash = Get-Sha256Hex $pairing.PairingCode
$deviceId = Invoke-DatabaseCommand -Scalar -Sql @"
select used_by_device_id
from pm_pairing_code
where code_hash = '$pairingCodeHash';
"@
if ($deviceId -notmatch '^\d+$') {
    throw "Unable to resolve the paired device ID. Database returned: $deviceId"
}

if (-not $SkipOfflineTest) {
    Invoke-Instrumentation `
        'com.example.paymentmonitor.sync.DeviceEndToEndTest#offlineQueueUploadsAfterWifiReturns'
}

if (-not $SkipRevocationTest) {
    try {
        Invoke-DatabaseCommand "update pm_device set status='1', updated_at=now() where id=$deviceId;"
        Invoke-Instrumentation `
            'com.example.paymentmonitor.sync.DeviceEndToEndTest#heartbeatMarksRepairRequiredAfterServerRevocation'
    } finally {
        Invoke-DatabaseCommand "update pm_device set status='0', updated_at=now() where id=$deviceId;"
    }

    $repairPairing = & (Join-Path $PSScriptRoot 'New-LocalPairingCode.ps1') -TtlMinutes 15
    Invoke-Instrumentation `
        'com.example.paymentmonitor.sync.DeviceEndToEndTest#repairAfterServerReenabledIncrementsCredentialVersion' `
        @{ serverUrl = $ServerUrl; pairingCode = $repairPairing.PairingCode }
}

$credentialVersion = Invoke-DatabaseCommand -Scalar -Sql @"
select max(key_version)
from pm_device_credential
where device_id = $deviceId;
"@
$activeCredentials = Invoke-DatabaseCommand -Scalar -Sql @"
select count(*)
from pm_device_credential
where device_id = $deviceId and revoked_at is null;
"@
if ($activeCredentials -ne '1') {
    throw "Expected one active credential for device $deviceId, found $activeCredentials."
}

[pscustomobject]@{
    DeviceSerial = $DeviceSerial
    DeviceId = $deviceId
    CredentialVersion = [int]$credentialVersion
    MainApk = $mainApk
    AndroidTestApk = $testApk
    ServerUrl = $ServerUrl
    Status = 'PASS'
}
