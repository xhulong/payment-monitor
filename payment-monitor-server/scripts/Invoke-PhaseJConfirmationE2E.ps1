param(
    [Parameter(Mandatory = $true)]
    [string]$AdminToken,
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$ClientId = 'e5cd7e4891bf95d1d19206ce24a7b32e',
    [int]$ReceiverPort = 19091
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$receiverScript = Join-Path $PSScriptRoot 'webhook_receiver.py'
$headers = @{
    Authorization = "Bearer $AdminToken"
    clientid = $ClientId
}

function Invoke-AdminApi {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body
    )
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        TimeoutSec = 20
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $json = $Body | ConvertTo-Json -Depth 12 -Compress
        $parameters.ContentType = 'application/json'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    $response = Invoke-RestMethod @parameters
    if ($response.code -ne 200) {
        throw "Admin API failed: $Method $Path, code=$($response.code), msg=$($response.msg)"
    }
    return $response.data
}

function Wait-Until {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Condition,
        [Parameter(Mandatory = $true)][string]$Description,
        [int]$TimeoutSeconds = 45
    )
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

function Start-Receiver {
    param(
        [Parameter(Mandatory = $true)][string]$Secret,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )
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
    Wait-Until -Description 'Phase J Webhook receiver' -Condition {
        try {
            $response = Invoke-WebRequest -UseBasicParsing `
                -Uri "http://127.0.0.1:$ReceiverPort/health" `
                -TimeoutSec 2
            return $response.StatusCode -eq 200
        } catch {
            return $false
        }
    } | Out-Null
    return $process
}

function Stop-Receiver($Process) {
    if ($Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit(5000) | Out-Null
    }
}

function Find-WebhookRecord {
    param(
        [Parameter(Mandatory = $true)][string]$OutputPath,
        [Parameter(Mandatory = $true)][string]$EventType,
        [long]$OrderId,
        [long]$TransactionId,
        [string]$DeliveryId
    )
    if (-not (Test-Path -LiteralPath $OutputPath)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $OutputPath -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $record = $line | ConvertFrom-Json
        if (-not $record.signatureValid -or $record.payload.type -ne $EventType) {
            continue
        }
        if ($DeliveryId -and $record.deliveryId -ne $DeliveryId) {
            continue
        }
        if ($OrderId -and [long]$record.payload.data.orderId -ne $OrderId) {
            continue
        }
        if ($TransactionId -and [long]$record.payload.data.transactionId -ne $TransactionId) {
            continue
        }
        return $record
    }
    return $null
}

& (Join-Path $PSScriptRoot 'Test-LocalStackHealth.ps1')

$timestamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMdd-HHmmss')
$artifactDir = Join-Path $serverRoot "artifacts\phase-j\$timestamp"
New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
$receiverOutput = Join-Path $artifactDir 'webhook-receiver.jsonl'
$resultOutput = Join-Path $artifactDir 'phase-j-e2e-result.json'

$endpoint = $null
$receiver = $null
try {
    $endpointResult = Invoke-AdminApi -Method Post -Path '/payment/webhooks/endpoints' -Body @{
        endpointName = "Phase J E2E $timestamp"
        endpointUrl = "http://host.docker.internal:$ReceiverPort/webhook"
        status = '0'
        eventTypes = @(
            'payment.transaction.observed',
            'payment.order.paid',
            'payment.order.confirmed',
            'payment.order.reconciled'
        )
        platformFilter = 'WECHAT'
        payloadVersion = 2
    }
    $endpoint = $endpointResult.endpoint
    $receiver = Start-Receiver `
        -Secret $endpointResult.webhookSecret `
        -OutputPath $receiverOutput

    $assets = Invoke-AdminApi -Method Get `
        -Path '/payment/qr-assets/enabled?platform=WECHAT'
    $asset = @($assets) | Select-Object -First 1
    if (-not $asset) {
        throw 'No enabled WECHAT QR asset is available for Phase J E2E.'
    }

    $requestedAmount = Get-Random -Minimum 5000000 -Maximum 6000000
    $order = Invoke-AdminApi -Method Post -Path '/payment/orders' -Body @{
        merchantOrderNo = "PHASE-J-E2E-$timestamp"
        platform = 'WECHAT'
        qrAssetId = $asset.id
        amountMinor = $requestedAmount
        expiresSeconds = 600
        subject = 'Phase J confirmation E2E'
    }

    $pairing = & (Join-Path $PSScriptRoot 'New-LocalPairingCode.ps1')
    $fixture = & (Join-Path $PSScriptRoot 'Invoke-DeviceFixture.ps1') `
        -PairingCode $pairing.PairingCode `
        -BaseUrl $BaseUrl `
        -Platform WECHAT `
        -AmountMinor $order.payableAmountMinor

    $notificationOrder = Wait-Until -Description 'notification-confirmed order' -Condition {
        $current = Invoke-AdminApi -Method Get -Path "/payment/orders/$($order.id)"
        if (
            $current.status -eq 'PAID' -and
            $current.confirmationStatus -eq 'NOTIFICATION' -and
            $current.transactionId
        ) {
            return $current
        }
        return $false
    }
    $transactionId = [long]$notificationOrder.transactionId

    $observedWebhook = Wait-Until -Description 'v2 observed Webhook' -Condition {
        Find-WebhookRecord `
            -OutputPath $receiverOutput `
            -EventType 'payment.transaction.observed' `
            -TransactionId $transactionId
    }
    $paidWebhook = Wait-Until -Description 'v2 paid Webhook' -Condition {
        Find-WebhookRecord `
            -OutputPath $receiverOutput `
            -EventType 'payment.order.paid' `
            -OrderId ([long]$order.id)
    }

    $confirmedTransaction = Invoke-AdminApi `
        -Method Put `
        -Path "/payment/transactions/$transactionId/confirm" `
        -Body @{ note = 'Phase J E2E single-review confirmation' }
    if (
        $confirmedTransaction.status -ne 'CONFIRMED' -or
        $confirmedTransaction.confirmationStatus -ne 'MANUAL'
    ) {
        throw 'Transaction did not become CONFIRMED/MANUAL.'
    }
    $manualOrder = Wait-Until -Description 'manually confirmed order' -Condition {
        $current = Invoke-AdminApi -Method Get -Path "/payment/orders/$($order.id)"
        if ($current.confirmationStatus -eq 'MANUAL') {
            return $current
        }
        return $false
    }
    $confirmedWebhook = Wait-Until -Description 'v2 confirmed Webhook' -Condition {
        Find-WebhookRecord `
            -OutputPath $receiverOutput `
            -EventType 'payment.order.confirmed' `
            -OrderId ([long]$order.id)
    }

    $reconciliation = Invoke-AdminApi `
        -Method Post `
        -Path '/payment/reconciliation/runs'
    $reconciledOrder = Wait-Until -Description 'internally reconciled order' -Condition {
        $current = Invoke-AdminApi -Method Get -Path "/payment/orders/$($order.id)"
        if ($current.confirmationStatus -eq 'RECONCILED') {
            return $current
        }
        return $false
    }
    $reconciledWebhook = Wait-Until -Description 'v2 reconciled Webhook' -Condition {
        Find-WebhookRecord `
            -OutputPath $receiverOutput `
            -EventType 'payment.order.reconciled' `
            -OrderId ([long]$order.id)
    }

    foreach ($record in @(
        $observedWebhook,
        $paidWebhook,
        $confirmedWebhook,
        $reconciledWebhook
    )) {
        if (
            $record.schemaVersion -ne '2' -or
            -not $record.eventId -or
            $record.eventId -ne $record.payload.eventId -or
            $record.payload.schemaVersion -ne 2
        ) {
            throw 'Webhook v2 header or payload contract validation failed.'
        }
    }

    $outbox = Invoke-AdminApi -Method Get -Path (
        '/payment/webhooks/outbox/list?' +
        "aggregateId=$($order.id)&eventType=payment.order.confirmed&pageNum=1&pageSize=10"
    )
    $confirmedOutbox = @($outbox.rows) | Select-Object -First 1
    if (-not $confirmedOutbox) {
        throw 'Confirmed Webhook outbox record was not found.'
    }
    $replay = Invoke-AdminApi `
        -Method Post `
        -Path "/payment/webhooks/outbox/$($confirmedOutbox.id)/replay" `
        -Body @{ reason = 'Phase J stable event ID E2E' }
    $replayedWebhook = Wait-Until -Description 'replayed confirmed Webhook' -Condition {
        Find-WebhookRecord `
            -OutputPath $receiverOutput `
            -EventType 'payment.order.confirmed' `
            -DeliveryId $replay.deliveryId
    }
    if (
        $replayedWebhook.eventId -ne $confirmedWebhook.eventId -or
        $replayedWebhook.deliveryId -eq $confirmedWebhook.deliveryId
    ) {
        throw 'Webhook replay did not preserve eventId or did not rotate deliveryId.'
    }

    $result = [ordered]@{
        orderId = [long]$order.id
        transactionId = $transactionId
        matchedEventId = [long]$notificationOrder.matchedEventId
        payableAmountMinor = [long]$order.payableAmountMinor
        notificationStatus = $notificationOrder.confirmationStatus
        manualStatus = $manualOrder.confirmationStatus
        reconciledStatus = $reconciledOrder.confirmationStatus
        reconciliationRunId = [long]$reconciliation.id
        reconciliationRunNo = $reconciliation.runNo
        observedWebhookEventId = $observedWebhook.eventId
        paidWebhookEventId = $paidWebhook.eventId
        confirmedWebhookEventId = $confirmedWebhook.eventId
        reconciledWebhookEventId = $reconciledWebhook.eventId
        replayDeliveryId = $replayedWebhook.deliveryId
        replayPreservedEventId = $true
        acceptedCount = $fixture.AcceptedCount
        duplicateCount = $fixture.DuplicateCount
    }
    $result | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath $resultOutput -Encoding UTF8
    [pscustomobject]$result
} finally {
    Stop-Receiver $receiver
    if ($endpoint) {
        try {
            Invoke-AdminApi `
                -Method Put `
                -Path "/payment/webhooks/endpoints/$($endpoint.id)" `
                -Body @{
                    endpointName = $endpoint.endpointName
                    endpointUrl = $endpoint.endpointUrl
                    status = '1'
                    eventTypes = @($endpoint.eventTypes)
                    platformFilter = $endpoint.platformFilter
                    payloadVersion = $endpoint.payloadVersion
                } | Out-Null
        } catch {
            Write-Warning "Failed to disable Phase J E2E endpoint: $($_.Exception.Message)"
        }
    }
}
