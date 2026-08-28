param(
    [string]$Code,
    [int]$TtlMinutes = 10,
    [long]$MerchantId = 1900000000000000001
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing local environment file: $envFile"
}

if ([string]::IsNullOrWhiteSpace($Code)) {
    $Code = Get-Random -Minimum 10000000 -Maximum 99999999
}
$Code = $Code.ToString()
if ($Code -notmatch '^\d{8}$') {
    throw 'Pairing code must contain exactly 8 digits.'
}
if ($TtlMinutes -lt 1 -or $TtlMinutes -gt 1440) {
    throw 'TtlMinutes must be between 1 and 1440.'
}

$values = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        $values[$parts[0]] = $parts[1]
    }
}

$sha = [System.Security.Cryptography.SHA256]::Create()
try {
    $hashBytes = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Code))
    $codeHash = ([BitConverter]::ToString($hashBytes)).Replace('-', '').ToLowerInvariant()
} finally {
    $sha.Dispose()
}

$id = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000 + (Get-Random -Minimum 100 -Maximum 999)
$sql = @"
insert into pm_pairing_code
    (id, merchant_id, code_hash, expires_at, created_at)
values
    ($id, $MerchantId, '$codeHash', now() + interval '$TtlMinutes minutes', now());
"@

& docker exec -e "PGPASSWORD=$($values['DB_PASSWORD'])" payment-monitor-postgres `
    psql -v ON_ERROR_STOP=1 -U payment_monitor -d payment_monitor -c $sql | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to insert local pairing code. docker/psql exited with $LASTEXITCODE"
}

[pscustomobject]@{
    PairingCode = $Code
    ExpiresAtUtc = [DateTimeOffset]::UtcNow.AddMinutes($TtlMinutes).ToString('o')
}
