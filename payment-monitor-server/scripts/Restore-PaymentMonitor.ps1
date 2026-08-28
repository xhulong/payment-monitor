param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
if (-not $Force) {
    throw 'Restore replaces the current payment_monitor database. Re-run with -Force after verifying the backup.'
}

$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$composeFile = Join-Path $serverRoot 'deploy\docker-compose.local.yml'
$resolvedBackup = [System.IO.Path]::GetFullPath($BackupFile)
if (-not (Test-Path -LiteralPath $resolvedBackup -PathType Leaf)) {
    throw "Backup file not found: $resolvedBackup"
}

$workspaceRoot = [System.IO.Path]::GetFullPath($serverRoot)
if (-not $resolvedBackup.StartsWith($workspaceRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Warning 'The backup is outside the workspace. Ensure it is trusted before continuing.'
}

$containerFile = "/tmp/payment-monitor-restore-$([guid]::NewGuid().ToString('N')).dump"
& docker cp $resolvedBackup "payment-monitor-postgres:$containerFile"
if ($LASTEXITCODE -ne 0) {
    throw "docker cp failed with exit code $LASTEXITCODE"
}

try {
    & docker compose --env-file $envFile -f $composeFile stop backend
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to stop backend before restore.'
    }
    & docker compose --env-file $envFile -f $composeFile exec -T postgres `
        pg_restore -U payment_monitor -d payment_monitor --clean --if-exists --no-owner --no-privileges $containerFile
    if ($LASTEXITCODE -ne 0) {
        throw "pg_restore failed with exit code $LASTEXITCODE"
    }
} finally {
    & docker compose --env-file $envFile -f $composeFile exec -T postgres rm -f $containerFile | Out-Null
    & docker compose --env-file $envFile -f $composeFile start backend | Out-Null
}

& docker compose --env-file $envFile -f $composeFile up -d --wait
if ($LASTEXITCODE -ne 0) {
    throw 'Stack did not become healthy after restore.'
}
Write-Host "Restore completed from: $resolvedBackup"
