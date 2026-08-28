param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$composeFile = Join-Path $serverRoot 'deploy\docker-compose.local.yml'

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'Build-LocalDockerStack.ps1')
}

& docker compose --env-file $envFile -f $composeFile up -d --wait
if ($LASTEXITCODE -ne 0) {
    & docker compose --env-file $envFile -f $composeFile ps
    throw "Docker stack failed to become healthy with exit code $LASTEXITCODE"
}

if (-not $SkipBuild) {
    # The development containers mount the backend JAR and frontend dist from
    # the host. Compose does not recreate an unchanged service definition, so
    # explicitly restart both processes after a successful build.
    & docker compose --env-file $envFile -f $composeFile restart snailai backend admin
    if ($LASTEXITCODE -ne 0) {
        throw "Docker stack restart failed with exit code $LASTEXITCODE"
    }
    & docker compose --env-file $envFile -f $composeFile up -d --wait
    if ($LASTEXITCODE -ne 0) {
        throw "Docker stack failed health checks after restart with exit code $LASTEXITCODE"
    }
}

Write-Host 'Payment monitor stack is ready:'
Write-Host '  Backend: http://localhost:8080'
Write-Host '  Admin:   http://localhost:5173'
Write-Host '  Qdrant:  http://localhost:6333/dashboard'
