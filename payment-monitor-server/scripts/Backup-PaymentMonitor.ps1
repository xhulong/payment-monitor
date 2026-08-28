param(
    [string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'backups')
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$composeFile = Join-Path $serverRoot 'deploy\docker-compose.local.yml'
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$fileName = "payment-monitor-$timestamp.dump"
$containerFile = "/tmp/$fileName"
$hostFile = Join-Path $resolvedOutput $fileName

& docker compose --env-file $envFile -f $composeFile exec -T postgres `
    pg_dump -U payment_monitor -d payment_monitor -Fc -f $containerFile
if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed with exit code $LASTEXITCODE"
}

try {
    & docker cp "payment-monitor-postgres:$containerFile" $hostFile
    if ($LASTEXITCODE -ne 0) {
        throw "docker cp failed with exit code $LASTEXITCODE"
    }
} finally {
    & docker compose --env-file $envFile -f $composeFile exec -T postgres rm -f $containerFile | Out-Null
}

$hash = (Get-FileHash -LiteralPath $hostFile -Algorithm SHA256).Hash
Set-Content -LiteralPath "$hostFile.sha256" -Value "$hash  $fileName" -Encoding ascii
Write-Host "Backup created: $hostFile"
Write-Host "SHA256: $hash"
