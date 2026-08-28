$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = Split-Path $serverRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$env:DOCKER_CONFIG = Join-Path $workspaceRoot '.tools\docker-config'
New-Item -ItemType Directory -Path $env:DOCKER_CONFIG -Force | Out-Null

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Missing .env.local. Run scripts\Initialize-LocalSecrets.ps1 first.'
}

& docker compose --env-file $envFile `
    -f (Join-Path $serverRoot 'deploy\docker-compose.local.yml') down
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose shutdown failed with exit code $LASTEXITCODE"
}
