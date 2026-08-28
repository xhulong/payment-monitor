$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$composeFile = Join-Path $serverRoot 'deploy\docker-compose.local.yml'

& docker compose --env-file $envFile -f $composeFile down
if ($LASTEXITCODE -ne 0) {
    throw "Docker stack stop failed with exit code $LASTEXITCODE"
}
