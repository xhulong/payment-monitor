$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = Split-Path $serverRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$env:DOCKER_CONFIG = Join-Path $workspaceRoot '.tools\docker-config'
New-Item -ItemType Directory -Path $env:DOCKER_CONFIG -Force | Out-Null

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Missing .env.local. Run scripts\Initialize-LocalSecrets.ps1 first.'
}

function Test-DockerReady {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        docker info *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

if (-not (Test-DockerReady)) {
    $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        throw 'Docker Desktop is not running and was not found at its default path.'
    }
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        if (Test-DockerReady) {
            $ready = $true
            break
        }
    }
    if (-not $ready) {
        throw 'Docker Desktop did not become ready within 120 seconds.'
    }
}

$composeFile = Join-Path $serverRoot 'deploy\docker-compose.local.yml'

& docker compose --env-file $envFile -f $composeFile up -d --wait
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose startup failed with exit code $LASTEXITCODE"
}

& docker compose --env-file $envFile -f $composeFile ps
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose status check failed with exit code $LASTEXITCODE"
}
