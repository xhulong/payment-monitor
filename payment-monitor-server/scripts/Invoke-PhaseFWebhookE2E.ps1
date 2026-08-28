param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [int]$ReceiverPort = 19090
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$receiverScript = Join-Path $PSScriptRoot 'webhook_receiver.py'
$cipherScript = Join-Path $PSScriptRoot 'payment_secret_cipher.py'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing local environment file: $envFile"
}

$values = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        $values[$parts[0]] = $parts[1]
    }
}

function New-DatabaseId {
    return [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000 +
        (Get-Random -Minimum 100000 -Maximum 999999)
}

function Invoke-Psql([string]$Sql) {
    $result = & docker exec payment-monitor-postgres psql `
        -v ON_ERROR_STOP=1 -U payment_monitor -d payment_monitor -Atc $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
    return ($result | Out-String).Trim()
}

function Wait-Until(
    [scriptblock]$Condition,
    [string]$Description,
    [int]$TimeoutSeconds = 30
) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $result = & $Condition
        if ($result) {
            return $result
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Description"
}

function Start-Receiver([string]$Secret, [string]$OutputPath) {
    $previousSecret = $env:WEBHOOK_SECRET
    $env:WEBHOOK_SECRET = $Secret
    try {
        $process = Start-Process -FilePath 'python.exe' `
            -ArgumentList @(
                $receiverScript,
                '--host', '0.0.0.0',
                '--port', $ReceiverPort,
                '--output', $OutputPath
            ) `
            -WorkingDirectory $serverRoot `
            -WindowStyle Hidden `
            -PassThru
    } finally {
        $env:WEBHOOK_SECRET = $previousSecret
    }
    Wait-Until {
        try {
            $response = Invoke-WebRequest -UseBasicParsing `
                -Uri "http://127.0.0.1:$ReceiverPort/health" -TimeoutSec 2
            return $response.StatusCode -eq 200
        } catch {
            return $false
        }
    } 'local Webhook receiver' | Out-Null
    return $process
}

function Stop-Receiver($Process) {
    if ($Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit(5000) | Out-Null
        Start-Sleep -Milliseconds 500
    }
}

function New-PendingOrder([long]$QrAssetId, [long]$AmountMinor, [string]$Suffix) {
    $orderId = New-DatabaseId
    $merchantOrderNo = "PHASE-F-$Suffix-$orderId"
    $publicToken = ([Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N'))
    $sql = @"
insert into pm_payment_order (
    id, merchant_id, merchant_order_no, platform, qr_asset_id,
    requested_amount_minor, payable_amount_minor, amount_offset_minor,
    currency, status, public_token, subject, created_at, expires_at, updated_at
) values (
    $orderId, 1900000000000000001, '$merchantOrderNo', 'WECHAT', $QrAssetId,
    $AmountMinor, $AmountMinor, 0,
    'CNY', 'PENDING', '$publicToken', 'Phase F Webhook E2E',
    now(), now() + interval '10 minutes', now()
);
"@
    Invoke-Psql $sql | Out-Null
    return [pscustomobject]@{
        Id = $orderId
        MerchantOrderNo = $merchantOrderNo
        AmountMinor = $AmountMinor
    }
}

function Upload-Income([long]$AmountMinor) {
    $pairing = & (Join-Path $PSScriptRoot 'New-LocalPairingCode.ps1')
    return & (Join-Path $PSScriptRoot 'Invoke-DeviceFixture.ps1') `
        -PairingCode $pairing.PairingCode `
        -BaseUrl $BaseUrl `
        -Platform WECHAT `
        -AmountMinor $AmountMinor
}

& (Join-Path $PSScriptRoot 'Test-LocalStackHealth.ps1')

$timestamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMdd-HHmmss')
$artifactDir = Join-Path $serverRoot "artifacts\phase-f\$timestamp"
New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
$receiverOutput = Join-Path $artifactDir 'webhook-receiver.jsonl'

$secretBytes = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($secretBytes)
} finally {
    $random.Dispose()
}
$webhookSecret = [Convert]::ToBase64String($secretBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$cipherRequest = @{
    masterKey = $values['PAYMENT_MASTER_KEY']
    plainText = $webhookSecret
} | ConvertTo-Json -Compress
$secretCiphertext = $cipherRequest | python.exe $cipherScript
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($secretCiphertext)) {
    throw 'Failed to encrypt Webhook secret.'
}

$endpointId = New-DatabaseId
$endpointName = "Phase F E2E $timestamp"
$endpointUrl = "http://host.docker.internal:$ReceiverPort/webhook"
$insertEndpoint = @"
insert into pm_webhook_endpoint (
    id, merchant_id, endpoint_name, endpoint_url, secret_ciphertext,
    status, created_at, updated_at
) values (
    $endpointId, 1900000000000000001, '$endpointName', '$endpointUrl',
    '$secretCiphertext', '0', now(), now()
);
"@
Invoke-Psql $insertEndpoint | Out-Null

$receiver = $null
try {
    $receiver = Start-Receiver $webhookSecret $receiverOutput
    $qrAssetId = Invoke-Psql @"
select id
from pm_qr_asset
where merchant_id = 1900000000000000001
  and platform = 'WECHAT'
  and status = '0'
order by created_at desc
limit 1;
"@
    if (-not $qrAssetId) {
        throw 'No enabled WECHAT QR asset is available for the Phase F E2E order.'
    }

    $onlineAmount = Get-Random -Minimum 2000000 -Maximum 3000000
    $onlineOrder = New-PendingOrder ([long]$qrAssetId) $onlineAmount 'ONLINE'
    $onlineFixture = Upload-Income $onlineOrder.AmountMinor
    $onlineDelivery = Wait-Until {
        $row = Invoke-Psql @"
select o.delivery_id || '|' || o.status || '|' || p.status || '|' ||
       coalesce((select count(*) from pm_webhook_delivery_log l where l.outbox_id = o.id), 0)
from pm_webhook_outbox o
join pm_payment_order p on p.id = o.aggregate_id
where o.endpoint_id = $endpointId and o.aggregate_id = $($onlineOrder.Id);
"@
        if ($row -match '^[^|]+\|DELIVERED\|PAID\|[1-9][0-9]*$') { return $row }
        return $false
    } 'online Webhook delivery'
    $onlineParts = $onlineDelivery.Split('|')
    $onlineDeliveryId = $onlineParts[0]
    Wait-Until {
        if (-not (Test-Path -LiteralPath $receiverOutput)) { return $false }
        foreach ($line in Get-Content -LiteralPath $receiverOutput -Encoding utf8) {
            if (-not $line) { continue }
            $record = $line | ConvertFrom-Json
            if ($record.deliveryId -eq $onlineDeliveryId -and $record.signatureValid) {
                return $record
            }
        }
        return $false
    } 'signed online receiver record' | Out-Null

    Stop-Receiver $receiver
    $receiver = $null

    $offlineAmount = Get-Random -Minimum 3000001 -Maximum 4000000
    $offlineOrder = New-PendingOrder ([long]$qrAssetId) $offlineAmount 'OFFLINE'
    $offlineFixture = Upload-Income $offlineOrder.AmountMinor
    $offlineState = Wait-Until {
        $row = Invoke-Psql @"
select o.id || '|' || o.delivery_id || '|' || o.status || '|' || p.status || '|' || o.attempt_count
from pm_webhook_outbox o
join pm_payment_order p on p.id = o.aggregate_id
where o.endpoint_id = $endpointId and o.aggregate_id = $($offlineOrder.Id);
"@
        if ($row -match '^[^|]+\|[^|]+\|RETRYING\|PAID\|[1-9][0-9]*$') { return $row }
        return $false
    } 'offline order PAID with retrying Webhook'
    $offlineParts = $offlineState.Split('|')
    $offlineOutboxId = $offlineParts[0]
    $offlineDeliveryId = $offlineParts[1]

    $receiver = Start-Receiver $webhookSecret $receiverOutput
    Invoke-Psql @"
update pm_webhook_outbox
set status = 'RETRYING', next_attempt_at = now(), locked_at = null, updated_at = now()
where id = $offlineOutboxId;
"@ | Out-Null
    $offlineDelivered = Wait-Until {
        $row = Invoke-Psql @"
select status || '|' || attempt_count || '|' ||
       (select count(*) from pm_webhook_delivery_log l where l.outbox_id = o.id) || '|' ||
       (select count(distinct delivery_id) from pm_webhook_delivery_log l where l.outbox_id = o.id)
from pm_webhook_outbox o
where id = $offlineOutboxId;
"@
        if ($row -match '^DELIVERED\|([2-9]|[1-9][0-9]+)\|([2-9]|[1-9][0-9]+)\|1$') { return $row }
        return $false
    } 'recovered offline Webhook delivery'
    $offlineDeliveredParts = $offlineDelivered.Split('|')

    Stop-Receiver $receiver
    $receiver = $null

    $recoveryAmount = Get-Random -Minimum 4000001 -Maximum 5000000
    $recoveryOrder = New-PendingOrder ([long]$qrAssetId) $recoveryAmount 'RESTART'
    $recoveryFixture = Upload-Income $recoveryOrder.AmountMinor
    $recoveryState = Wait-Until {
        $row = Invoke-Psql @"
select o.id || '|' || o.delivery_id || '|' || o.status || '|' || p.status || '|' || o.attempt_count
from pm_webhook_outbox o
join pm_payment_order p on p.id = o.aggregate_id
where o.endpoint_id = $endpointId and o.aggregate_id = $($recoveryOrder.Id);
"@
        if ($row -match '^[^|]+\|[^|]+\|RETRYING\|PAID\|[1-9][0-9]*$') { return $row }
        return $false
    } 'restart-recovery order PAID with retrying Webhook'
    $recoveryParts = $recoveryState.Split('|')
    $recoveryOutboxId = $recoveryParts[0]
    $recoveryDeliveryId = $recoveryParts[1]

    $receiver = Start-Receiver $webhookSecret $receiverOutput
    Invoke-Psql @"
update pm_webhook_outbox
set status = 'DELIVERING',
    locked_at = now() - interval '5 minutes',
    next_attempt_at = now() + interval '1 day',
    updated_at = now()
where id = $recoveryOutboxId;
"@ | Out-Null
    & docker restart payment-monitor-backend | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to restart the backend container.'
    }
    Wait-Until {
        $health = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' `
            payment-monitor-backend).Trim()
        return $health -eq 'healthy'
    } 'healthy backend after restart' 150 | Out-Null
    $recoveryDelivered = Wait-Until {
        $row = Invoke-Psql @"
select status || '|' || attempt_count || '|' ||
       (select count(*) from pm_webhook_delivery_log l where l.outbox_id = o.id) || '|' ||
       (select count(distinct delivery_id) from pm_webhook_delivery_log l where l.outbox_id = o.id)
from pm_webhook_outbox o
where id = $recoveryOutboxId;
"@
        if ($row -match '^DELIVERED\|([2-9]|[1-9][0-9]+)\|([2-9]|[1-9][0-9]+)\|1$') { return $row }
        return $false
    } 'stale lock recovery after backend restart' 60
    $recoveryDeliveredParts = $recoveryDelivered.Split('|')

    $outboxCount = Invoke-Psql @"
select count(*)
from pm_webhook_outbox
where endpoint_id = $endpointId
  and aggregate_id in ($($onlineOrder.Id), $($offlineOrder.Id), $($recoveryOrder.Id));
"@
    if ([int]$outboxCount -ne 3) {
        throw "Expected exactly three endpoint/order Outbox rows, got $outboxCount."
    }

    [pscustomobject]@{
        EndpointId = $endpointId
        ReceiverOutput = $receiverOutput
        OnlineOrderId = $onlineOrder.Id
        OnlineDeliveryId = $onlineDeliveryId
        OnlineAcceptedCount = $onlineFixture.AcceptedCount
        OnlineDuplicateCount = $onlineFixture.DuplicateCount
        OfflineOrderId = $offlineOrder.Id
        OfflineDeliveryId = $offlineDeliveryId
        OfflineAcceptedCount = $offlineFixture.AcceptedCount
        OfflineDuplicateCount = $offlineFixture.DuplicateCount
        OfflineAttemptCount = [int]$offlineDeliveredParts[1]
        OfflineDeliveryLogCount = [int]$offlineDeliveredParts[2]
        RestartRecoveryOrderId = $recoveryOrder.Id
        RestartRecoveryDeliveryId = $recoveryDeliveryId
        RestartRecoveryAcceptedCount = $recoveryFixture.AcceptedCount
        RestartRecoveryDuplicateCount = $recoveryFixture.DuplicateCount
        RestartRecoveryAttemptCount = [int]$recoveryDeliveredParts[1]
        RestartRecoveryLogCount = [int]$recoveryDeliveredParts[2]
        SignatureVerified = $true
    }
} finally {
    Stop-Receiver $receiver
    Invoke-Psql "update pm_webhook_endpoint set status = '1', updated_at = now() where id = $endpointId;" | Out-Null
}
