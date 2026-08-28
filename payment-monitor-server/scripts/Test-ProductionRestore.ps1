[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$ComposeFile,
    [string]$EnvFile,
    [string]$EncryptionPassword
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "BackupCrypto.ps1")
if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
    $ComposeFile = Join-Path $scriptRoot "..\deploy\docker-compose.production.yml"
}
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $scriptRoot "..\deploy\.env.production"
}
if ([string]::IsNullOrWhiteSpace($EncryptionPassword)) {
    $EncryptionPassword = $env:BACKUP_ENCRYPTION_PASSWORD
}
$backup = [System.IO.Path]::GetFullPath($BackupFile)
if (-not (Test-Path -LiteralPath $backup)) {
    throw "Backup file not found: $backup"
}

function Invoke-Compose([string[]]$arguments) {
    & docker compose --env-file $EnvFile -f $ComposeFile @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed: $($arguments -join ' ')"
    }
}

$work = Join-Path $env:TEMP ("payment-monitor-restore-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $work | Out-Null
$archive = $null
$dump = $backup
try {
    if ($backup.EndsWith(".pmbak", [StringComparison]::OrdinalIgnoreCase)) {
        if ([string]::IsNullOrWhiteSpace($EncryptionPassword) -or $EncryptionPassword.Length -lt 16) {
            throw "Encrypted restore requires BACKUP_ENCRYPTION_PASSWORD"
        }
        $archive = Join-Path $work "decrypted.zip"
        Unprotect-PaymentBackupFile $backup $archive $EncryptionPassword
        $extract = Join-Path $work "extract"
        New-Item -ItemType Directory -Force -Path $extract | Out-Null
        Expand-Archive -LiteralPath $archive -DestinationPath $extract -Force
        $dump = Join-Path $extract "database\postgres.dump"
        if (-not (Test-Path -LiteralPath $dump)) {
            throw "Backup archive does not contain database/postgres.dump"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $extract "database\redis.rdb"))) {
            throw "Backup archive does not contain database/redis.rdb"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $extract "storage\minio-bucket-manifest.json"))) {
            throw "Backup archive does not contain storage/minio-bucket-manifest.json"
        }
        if (-not (Test-Path -LiteralPath (Join-Path $extract "storage\minio-bucket") -PathType Container)) {
            throw "Backup archive does not contain storage/minio-bucket"
        }
    }

    $container = docker compose --env-file $EnvFile -f $ComposeFile ps -q postgres
    if (-not $container) {
        throw "PostgreSQL container is not running"
    }
    $database = "payment_monitor_restore_" + (Get-Date -Format "yyyyMMddHHmmss")
    docker cp $dump "${container}:/tmp/payment-monitor-restore.dump" | Out-Null
    try {
        Invoke-Compose @("exec", "-T", "postgres", "createdb", "-U", "payment_monitor", $database)
        Invoke-Compose @("exec", "-T", "postgres", "pg_restore", "-U", "payment_monitor", "-d", $database, "--clean", "--if-exists", "/tmp/payment-monitor-restore.dump")
        $version = docker compose --env-file $EnvFile -f $ComposeFile exec -T postgres `
            psql -U payment_monitor -d $database -Atc `
            "select version from pm_flyway_schema_history where success order by installed_rank desc limit 1"
        if ([string]::IsNullOrWhiteSpace($version)) {
            throw "Restore validation did not find Flyway history"
        }
        Write-Host "Restore rehearsal passed; latest Flyway version: $version"
    }
    finally {
        Invoke-Compose @("exec", "-T", "postgres", "dropdb", "-U", "payment_monitor", "--if-exists", $database) 2>$null
        Invoke-Compose @("exec", "-T", "postgres", "rm", "-f", "/tmp/payment-monitor-restore.dump") 2>$null
    }
}
finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
