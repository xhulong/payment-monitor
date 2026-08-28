param(
    [string]$AdminRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '..\payment-monitor-admin')
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = Split-Path $serverRoot -Parent
$adminRoot = [System.IO.Path]::GetFullPath($AdminRoot)
$nodeRoot = Join-Path $workspaceRoot '.tools\node-v22'
$serverEnv = Join-Path $serverRoot '.env.local'
$adminDevelopmentEnv = Join-Path $adminRoot '.env.development.local'
$adminProductionEnv = Join-Path $adminRoot '.env.production.local'

if (-not (Test-Path -LiteralPath $serverEnv)) {
    throw 'Missing .env.local. Run scripts\Initialize-LocalSecrets.ps1 first.'
}
if (-not (Test-Path -LiteralPath $adminDevelopmentEnv)) {
    throw 'Missing frontend .env.development.local. Run scripts\Initialize-LocalSecrets.ps1 first.'
}

$env:JAVA_HOME = 'D:\java\jdk-21'
$env:COREPACK_HOME = Join-Path $workspaceRoot '.tools\corepack'
$env:PNPM_HOME = Join-Path $workspaceRoot '.tools\pnpm-home'
$env:Path = "$env:JAVA_HOME\bin;$nodeRoot;$env:PNPM_HOME;$env:Path"
$mavenRepo = (Join-Path $workspaceRoot '.tools\m2-repository').Replace('\', '/')

Push-Location $serverRoot
try {
    & mvn.cmd "-Dmaven.repo.local=$mavenRepo" '-DskipTests=true' -pl ruoyi-admin,ruoyi-extend/ruoyi-snailai-server -am package
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$productionEnv = @"
VITE_APP_BASE_API=/prod-api
VITE_APP_PORT=80
VITE_APP_API_CRYPTO_V2=true
"@
Set-Content -LiteralPath $adminProductionEnv -Value $productionEnv -Encoding UTF8

$vite = Join-Path $adminRoot 'node_modules\.bin\vite.cmd'
if (-not (Test-Path -LiteralPath $vite)) {
    throw "Frontend dependencies are missing: $vite"
}
Push-Location $adminRoot
try {
    & $vite build --mode production
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host 'Backend JAR and frontend dist are ready for the local Docker stack.'
