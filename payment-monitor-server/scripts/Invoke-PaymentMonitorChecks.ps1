param(
    [string]$AdminRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '..\PaymentMonitorAdmin'),
    [string]$AndroidRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '..\PaymentNotificationMonitor')
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = Split-Path $serverRoot -Parent
$nodeRoot = Join-Path $workspaceRoot '.tools\node-v22'
$env:JAVA_HOME = 'D:\java\jdk-21'
$env:COREPACK_HOME = Join-Path $workspaceRoot '.tools\corepack'
$env:PNPM_HOME = Join-Path $workspaceRoot '.tools\pnpm-home'
$env:Path = "$env:JAVA_HOME\bin;$nodeRoot;$env:PNPM_HOME;$env:Path"
$mavenRepo = (Join-Path $workspaceRoot '.tools\m2-repository').Replace('\', '/')

Push-Location $serverRoot
try {
    & mvn.cmd "-Dmaven.repo.local=$mavenRepo" "-Dmaven.test.skip=false" "-DskipTests=false" `
        "-Dtest.groups=dev | integration" -pl ruoyi-modules/ruoyi-payment -am test
    if ($LASTEXITCODE -ne 0) { throw 'Server tests failed.' }
} finally { Pop-Location }

Push-Location ([System.IO.Path]::GetFullPath($AdminRoot))
try {
    & pnpm.cmd test:payment
    if ($LASTEXITCODE -ne 0) { throw 'Admin payment tests failed.' }
    & pnpm.cmd exec vue-tsc --noEmit
    if ($LASTEXITCODE -ne 0) { throw 'Admin type check failed.' }
    & pnpm.cmd lint
    if ($LASTEXITCODE -ne 0) { throw 'Admin lint failed.' }
    & pnpm.cmd build:prod
    if ($LASTEXITCODE -ne 0) { throw 'Admin build failed.' }
} finally { Pop-Location }

$androidRootPath = [System.IO.Path]::GetFullPath($AndroidRoot)
$androidBuildRoot = $androidRootPath
if ($androidRootPath -match '[^\x00-\x7F]') {
    $androidBuildRoot = 'D:\payment-monitor-android'
    if (-not (Test-Path -LiteralPath $androidBuildRoot)) {
        New-Item -ItemType Junction -Path $androidBuildRoot -Target $androidRootPath | Out-Null
    }
}
Push-Location $androidBuildRoot
try {
    $env:JAVA_HOME = 'D:\toolbox\Android Studio\jbr'
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    & .\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Android checks failed.' }
} finally { Pop-Location }

Write-Host 'All payment-monitor checks passed.'
