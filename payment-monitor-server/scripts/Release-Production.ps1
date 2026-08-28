[CmdletBinding()]
param(
    [string]$ComposeFile,
    [string]$EnvFile,
    [int]$HealthTimeoutSeconds = 240,
    [switch]$SimulateFailureAfterHealth
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
    $ComposeFile = Join-Path $scriptRoot "..\deploy\docker-compose.production.yml"
}
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $scriptRoot "..\deploy\.env.production"
}
$compose = [System.IO.Path]::GetFullPath($ComposeFile)
$env = [System.IO.Path]::GetFullPath($EnvFile)
$cert = [System.IO.Path]::GetFullPath((Join-Path (Split-Path $compose) "production\certs\payment-monitor.crt"))
$key = [System.IO.Path]::GetFullPath((Join-Path (Split-Path $compose) "production\certs\payment-monitor.key"))
foreach ($path in @($compose, $env, $cert, $key)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Production preflight failed, missing: $path"
    }
}

$environment = @{}
Get-Content -LiteralPath $env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line.Split("=", 2)
        $environment[$parts[0]] = $parts[1]
    }
}
if ([string]::IsNullOrWhiteSpace($environment["MONITOR_PASSWORD"])) {
    throw "Production preflight failed, MONITOR_PASSWORD is required"
}
if ([string]::IsNullOrWhiteSpace($environment["BACKUP_ENCRYPTION_PASSWORD"]) -or
    $environment["BACKUP_ENCRYPTION_PASSWORD"].Length -lt 16) {
    throw "Production preflight failed, BACKUP_ENCRYPTION_PASSWORD must be at least 16 characters"
}
$secretDirectory = Join-Path (Split-Path $compose) "production\secrets"
$monitorPasswordFile = Join-Path $secretDirectory "monitor_password"
New-Item -ItemType Directory -Force -Path $secretDirectory | Out-Null
Set-Content -LiteralPath $monitorPasswordFile -Value $environment["MONITOR_PASSWORD"] -Encoding Ascii -NoNewline

docker compose --env-file $env -f $compose config --quiet
if ($LASTEXITCODE -ne 0) { throw "Compose validation failed" }

$domain = if ($environment["PAYMENT_DOMAIN"]) { $environment["PAYMENT_DOMAIN"] } else { "payment-monitor.local" }
$backendImage = if ($environment["BACKEND_IMAGE"]) { $environment["BACKEND_IMAGE"] } else { "payment-monitor-backend:phase-g" }
$adminImage = if ($environment["ADMIN_IMAGE"]) { $environment["ADMIN_IMAGE"] } else { "payment-monitor-admin:phase-g" }

$postgresContainer = docker compose --env-file $env -f $compose ps -q postgres
if ($postgresContainer) {
    $postgresRunning = docker inspect --format "{{.State.Running}}" $postgresContainer
    if ($postgresRunning -eq "true") {
        & (Join-Path $scriptRoot "Backup-Production.ps1") `
            -ComposeFile $compose `
            -EnvFile $env `
            -EncryptionPassword $environment["BACKUP_ENCRYPTION_PASSWORD"]
    }
}

$existingBackendImage = docker image ls --quiet $backendImage
$hasBackendRollback = -not [string]::IsNullOrWhiteSpace(($existingBackendImage | Select-Object -First 1))
if ($existingBackendImage) {
    docker tag $backendImage "payment-monitor-backend:rollback"
}
$existingAdminImage = docker image ls --quiet $adminImage
$hasAdminRollback = -not [string]::IsNullOrWhiteSpace(($existingAdminImage | Select-Object -First 1))
if ($existingAdminImage) {
    docker tag $adminImage "payment-monitor-admin:rollback"
}

try {
    docker compose --env-file $env -f $compose build --pull backend gateway
    if ($LASTEXITCODE -ne 0) { throw "Image build failed" }
    docker compose --env-file $env -f $compose up -d --wait
    if ($LASTEXITCODE -ne 0) { throw "Compose startup failed" }

    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    do {
        $services = @(docker compose --env-file $env -f $compose ps --format json | ConvertFrom-Json)
        $unhealthy = @($services | Where-Object {
            $_.State -ne "running" -or ($_.Health -and $_.Health -ne "healthy")
        })
        if ($services.Count -ge 8 -and $unhealthy.Count -eq 0) { break }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    if ($services.Count -lt 8 -or $unhealthy.Count -gt 0) {
        docker compose --env-file $env -f $compose ps
        throw "Health checks did not become healthy"
    }
    if ($SimulateFailureAfterHealth) {
        throw "Simulated post-health deployment failure"
    }

    & curl.exe --fail --silent --show-error --insecure `
        --resolve "${domain}:443:127.0.0.1" "https://$domain/" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "HTTPS gateway smoke test failed" }
    $prometheus = docker compose --env-file $env -f $compose exec -T prometheus `
        promtool check healthy --url=http://127.0.0.1:9090
    if ($LASTEXITCODE -ne 0) { throw "Prometheus smoke test failed: $prometheus" }
    Write-Host "Production release rehearsal passed"
}
catch {
    $releaseError = $_
    Write-Warning "Release failed, restoring previous image tags"
    if ($hasBackendRollback -and (docker image ls --quiet "payment-monitor-backend:rollback")) {
        docker tag "payment-monitor-backend:rollback" $backendImage
    }
    if ($hasAdminRollback -and (docker image ls --quiet "payment-monitor-admin:rollback")) {
        docker tag "payment-monitor-admin:rollback" $adminImage
    }
    if ($hasBackendRollback -and $hasAdminRollback) {
        docker compose --env-file $env -f $compose up -d --no-build --force-recreate backend gateway
        if ($LASTEXITCODE -ne 0) {
            throw "Release failed and rollback startup also failed: $($releaseError.Exception.Message)"
        }
        docker compose --env-file $env -f $compose up -d --no-build --wait
        if ($LASTEXITCODE -ne 0) {
            throw "Release failed and rollback health recovery also failed: $($releaseError.Exception.Message)"
        }
        Write-Host "Previous backend and gateway images restored"
    }
    else {
        docker compose --env-file $env -f $compose stop gateway prometheus backend | Out-Null
        Write-Warning "No complete previous image set existed; newly deployed application services were stopped"
    }
    throw $releaseError
}
