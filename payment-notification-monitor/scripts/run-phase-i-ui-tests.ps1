param(
    [string]$Serial = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaHome = "D:\toolbox\Android Studio\jbr"
$adbCandidates = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "D:\toolbox\Android Studio\sdk\platform-tools\adb.exe",
    "D:\toolbox\Android Studio\platform-tools\adb.exe"
)
$adb = $adbCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $adb) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        $adb = $adbCommand.Source
    }
}
if (-not $adb) {
    throw "adb.exe was not found"
}

if (-not $Serial) {
    $deviceLines = & $adb devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "\sdevice$" }
    if ($deviceLines.Count -ne 1) {
        throw "Use -Serial to select a device; connected device count: $($deviceLines.Count)"
    }
    $Serial = ($deviceLines[0] -split "\s+")[0]
}

if (-not $SkipBuild) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
    Push-Location $projectRoot
    try {
        & .\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed"
        }
    } finally {
        Pop-Location
    }
}

$appApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
if (-not (Test-Path -LiteralPath $appApk) -or -not (Test-Path -LiteralPath $testApk)) {
    throw "Debug APK or AndroidTest APK was not found"
}

& $adb -s $Serial install --no-streaming -r -t $appApk
if ($LASTEXITCODE -ne 0) {
    throw "Debug APK installation failed"
}
& $adb -s $Serial install --no-streaming -r -t $testApk
if ($LASTEXITCODE -ne 0) {
    throw "AndroidTest APK installation failed"
}

$manufacturer = (& $adb -s $Serial shell getprop ro.product.manufacturer).Trim()
if ($manufacturer -match "Xiaomi|Redmi") {
    # MIUI 将 instrumentation 启动测试 Activity 视为后台弹窗；
    # 10021 是“后台弹出界面”AppOp，仅对本 Debug 包授权。
    # MIUI may reset this AppOp asynchronously just after an APK update.
    Start-Sleep -Seconds 3
    $miuiAppOp = ""
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        & $adb -s $Serial shell cmd appops set --user 0 com.example.paymentmonitor 10021 allow
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to set MIUI background test Activity AppOp"
        }
        Start-Sleep -Seconds 1
        $miuiAppOp = (& $adb -s $Serial shell cmd appops get --user 0 com.example.paymentmonitor 10021 2>&1) -join "`n"
        if ($miuiAppOp -match "allow") {
            break
        }
    }
    if ($miuiAppOp -notmatch "allow") {
        throw "MIUI background test Activity AppOp was not applied: $miuiAppOp"
    }
}

& $adb -s $Serial shell am force-stop com.example.paymentmonitor.test
& $adb -s $Serial shell am force-stop com.example.paymentmonitor

$testClasses = @(
    "com.example.paymentmonitor.ui.MainScreenTest",
    "com.example.paymentmonitor.ui.UiPreferencesTest"
) -join ","

& $adb -s $Serial shell am instrument -w -r `
    -e class $testClasses `
    com.example.paymentmonitor.test/.PaymentMonitorTestRunner 2>&1 |
    Tee-Object -Variable instrumentationOutput |
    ForEach-Object { Write-Host $_ }
$output = $instrumentationOutput -join "`n"

if ($output -match "FAILURES!!!" -or $output -notmatch "OK \(\d+ tests?\)") {
    throw "Phase I UI device tests failed"
}

Write-Host "Phase I UI device tests passed: $Serial"
