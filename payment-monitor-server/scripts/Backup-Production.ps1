[CmdletBinding()]
param(
    [string]$ComposeFile,
    [string]$EnvFile,
    [string]$OutputDirectory,
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
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $scriptRoot "..\backups\production"
}
if ([string]::IsNullOrWhiteSpace($EncryptionPassword)) {
    $EncryptionPassword = $env:BACKUP_ENCRYPTION_PASSWORD
}

$compose = [System.IO.Path]::GetFullPath($ComposeFile)
$envFilePath = [System.IO.Path]::GetFullPath($EnvFile)
$output = [System.IO.Path]::GetFullPath($OutputDirectory)
$allowed = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot "..\backups"))
if (-not $output.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Backup output must stay under $allowed"
}
if (-not (Test-Path -LiteralPath $compose) -or -not (Test-Path -LiteralPath $envFilePath)) {
    throw "Compose or environment file is missing"
}
if ([string]::IsNullOrWhiteSpace($EncryptionPassword) -or $EncryptionPassword.Length -lt 16) {
    throw "Encrypted backups require BACKUP_ENCRYPTION_PASSWORD (at least 16 characters)"
}
function Read-EnvValue([string]$name) {
    $line = Get-Content -LiteralPath $envFilePath |
        Where-Object { $_ -match "^\s*$([Regex]::Escape($name))=" } |
        Select-Object -First 1
    if ($line) {
        return ($line -split "=", 2)[1].Trim()
    }
    return $null
}

function Invoke-Compose([string[]]$arguments) {
    & docker compose --env-file $envFilePath -f $compose @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed: $($arguments -join ' ')"
    }
}

New-Item -ItemType Directory -Force -Path $output | Out-Null
$stamp = [DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss")
$stage = Join-Path $output ".staging-$stamp"
$bundle = Join-Path $output "bundle-$stamp.zip"
$encrypted = Join-Path $output "daily-$stamp.pmbak"
$manifest = Join-Path $output "daily-$stamp.pmbak.sha256"
New-Item -ItemType Directory -Force -Path $stage | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stage "database") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stage "storage\minio-bucket") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stage "config") | Out-Null

$postgresContainer = docker compose --env-file $envFilePath -f $compose ps -q postgres
$redisContainer = docker compose --env-file $envFilePath -f $compose ps -q redis
$minioContainer = docker compose --env-file $envFilePath -f $compose ps -q minio
if (-not $postgresContainer -or -not $redisContainer -or -not $minioContainer) {
    throw "PostgreSQL, Redis and MinIO must be running before backup"
}

$pgRemote = "/tmp/payment-monitor-$stamp.dump"
$redisRemote = "/tmp/payment-monitor-$stamp.rdb"
$redisPassword = Read-EnvValue "REDIS_PASSWORD"
try {
    Invoke-Compose @("exec", "-T", "postgres", "pg_dump", "-U", "payment_monitor", "-d", "payment_monitor", "-Fc", "-f", $pgRemote)
    Invoke-Compose @("exec", "-T", "postgres", "pg_restore", "--list", $pgRemote)
    docker cp "${postgresContainer}:$pgRemote" (Join-Path $stage "database\postgres.dump") | Out-Null

    $redisCommand = if ([string]::IsNullOrWhiteSpace($redisPassword)) {
        "redis-cli --rdb '$redisRemote'"
    } else {
        "redis-cli -a '$redisPassword' --rdb '$redisRemote'"
    }
    Invoke-Compose @("exec", "-T", "redis", "sh", "-c", $redisCommand)
    docker cp "${redisContainer}:$redisRemote" (Join-Path $stage "database\redis.rdb") | Out-Null

    $minioBucket = Read-EnvValue "MINIO_BUCKET"
    if ([string]::IsNullOrWhiteSpace($minioBucket)) {
        $minioBucket = "payment-monitor-private"
    }
    $minioMirror = [System.IO.Path]::GetFullPath((Join-Path $stage "storage\minio-bucket"))
    & docker run --rm `
        --network "container:$minioContainer" `
        --env-file $envFilePath `
        --env "MINIO_BUCKET=$minioBucket" `
        --mount "type=bind,source=$minioMirror,target=/backup" `
        --entrypoint /bin/sh `
        minio/mc:RELEASE.2025-04-16T18-13-26Z `
        -c 'mc alias set source http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mirror --overwrite --preserve "source/$MINIO_BUCKET" /backup'
    if ($LASTEXITCODE -ne 0) {
        throw "MinIO bucket mirror failed"
    }
    @{
        bucket = $minioBucket
        mirroredAtUtc = [DateTime]::UtcNow.ToString("o")
        mode = "mc-mirror"
    } | ConvertTo-Json | Set-Content `
        -LiteralPath (Join-Path $stage "storage\minio-bucket-manifest.json") `
        -Encoding UTF8

    Copy-Item -LiteralPath $envFilePath -Destination (Join-Path $stage "config\env-production.snapshot") -Force
    $certDirectory = Join-Path (Split-Path $compose) "production\certs"
    $certMetadata = @(
        Get-ChildItem -LiteralPath $certDirectory -File -ErrorAction SilentlyContinue |
            ForEach-Object {
                $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
                [PSCustomObject]@{
                    file = $_.Name
                    bytes = $_.Length
                    sha256 = $hash.Hash
                    lastWriteTimeUtc = $_.LastWriteTimeUtc.ToString("o")
                }
            }
    )
    $certMetadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $stage "config\certificate-metadata.json") -Encoding UTF8
    Get-ChildItem -LiteralPath $stage -Recurse -File |
        ForEach-Object {
            $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
            "$($hash.Hash)  $($_.FullName.Substring($stage.Length + 1))"
        } | Set-Content -LiteralPath (Join-Path $stage "manifest.sha256") -Encoding ASCII

    Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $bundle -CompressionLevel Optimal -Force
    Protect-PaymentBackupFile $bundle $encrypted $EncryptionPassword
    if (-not (Test-Path -LiteralPath $encrypted)) {
        throw "Encrypted backup creation failed"
    }
    $encryptedHash = Get-FileHash -LiteralPath $encrypted -Algorithm SHA256
    "$($encryptedHash.Hash)  $(Split-Path $encrypted -Leaf)" |
        Set-Content -LiteralPath $manifest -Encoding ASCII
}
finally {
    Invoke-Compose @("exec", "-T", "postgres", "rm", "-f", $pgRemote) 2>$null
    Invoke-Compose @("exec", "-T", "redis", "rm", "-f", $redisRemote) 2>$null
    Remove-Item -LiteralPath $bundle -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
}

$today = [DateTime]::UtcNow
if ($today.DayOfWeek -eq [DayOfWeek]::Sunday) {
    Copy-Item -LiteralPath $encrypted -Destination (Join-Path $output "weekly-$stamp.pmbak") -Force
}
if ($today.DayOfWeek -eq [DayOfWeek]::Sunday -and $today.Month -in @(1, 4, 7, 10) -and $today.Day -le 7) {
    Copy-Item -LiteralPath $encrypted -Destination (Join-Path $output "quarterly-$stamp.pmbak") -Force
}

Get-ChildItem -LiteralPath $output -Filter "daily-*.pmbak" |
    Sort-Object LastWriteTime -Descending | Select-Object -Skip 7 | Remove-Item -Force
Get-ChildItem -LiteralPath $output -Filter "daily-*.pmbak.sha256" |
    Sort-Object LastWriteTime -Descending | Select-Object -Skip 7 | Remove-Item -Force
Get-ChildItem -LiteralPath $output -Filter "weekly-*.pmbak" |
    Sort-Object LastWriteTime -Descending | Select-Object -Skip 4 | Remove-Item -Force
Get-ChildItem -LiteralPath $output -Filter "quarterly-*.pmbak" |
    Sort-Object LastWriteTime -Descending | Select-Object -Skip 3 | Remove-Item -Force

Write-Host $encrypted
