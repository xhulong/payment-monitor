param(
    [Parameter(Mandatory = $true)]
    [string]$PairingCode,
    [string]$BaseUrl = 'http://localhost:8080',
    [ValidateSet('WECHAT', 'ALIPAY')]
    [string]$Platform = 'WECHAT',
    [long]$AmountMinor = 10001,
    [string]$RawHashSeed,
    [string]$DeviceName = 'PowerShell Fixture Device'
)

$ErrorActionPreference = 'Stop'

function ConvertTo-Hex([byte[]]$Bytes) {
    return ([BitConverter]::ToString($Bytes)).Replace('-', '').ToLowerInvariant()
}

function Get-Sha256Hex([byte[]]$Bytes) {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-Hex ($sha256.ComputeHash($Bytes))
    } finally {
        $sha256.Dispose()
    }
}

function Get-HmacHex([string]$Secret, [string]$Value) {
    $hmac = [System.Security.Cryptography.HMACSHA256]::new(
        [Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        return ConvertTo-Hex ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))
    } finally {
        $hmac.Dispose()
    }
}

function Invoke-DeviceRequest(
    [string]$Method,
    [string]$Path,
    [string]$Body,
    [string]$DeviceId,
    [string]$DeviceSecret,
    [string]$CredentialVersion
) {
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
    $nonce = [Guid]::NewGuid().ToString('N')
    $bodyBytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $canonical = "$Method`n$Path`n$timestamp`n$nonce`n$(Get-Sha256Hex $bodyBytes)"
    $headers = @{
        'X-Device-Id' = $DeviceId
        'X-Credential-Version' = $CredentialVersion
        'X-Timestamp' = $timestamp
        'X-Nonce' = $nonce
        'X-Signature' = Get-HmacHex $DeviceSecret $canonical
    }
    return Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $headers `
        -ContentType 'application/json; charset=utf-8' -Body $bodyBytes
}

$pairBody = @{
    protocolVersion = 1
    pairingCode = $PairingCode
    deviceName = $DeviceName
    androidIdHash = Get-Sha256Hex ([Text.Encoding]::UTF8.GetBytes($env:COMPUTERNAME))
    appVersion = 'debug-fixture'
    parserVersion = '1.0.0'
} | ConvertTo-Json -Compress

$pairResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/devices/pair" `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($pairBody))
if (-not $pairResponse.ok -or $null -eq $pairResponse.data) {
    throw "Device pairing failed: $($pairResponse.error.code) $($pairResponse.error.message)"
}
$deviceId = $pairResponse.data.deviceId.ToString()
$deviceSecret = $pairResponse.data.deviceSecret
$credentialVersion = $pairResponse.data.credentialVersion.ToString()

$heartbeatBody = @{ appVersion = 'debug-fixture'; parserVersion = '1.0.0' } | ConvertTo-Json -Compress
$heartbeat = Invoke-DeviceRequest 'POST' '/api/v1/device/heartbeat' $heartbeatBody $deviceId $deviceSecret $credentialVersion

$eventId = [Guid]::NewGuid().ToString()
$platformLabel = if ($Platform -eq 'WECHAT') { 'WeChat Pay' } else { 'Alipay' }
$amountText = '{0:F2}' -f ($AmountMinor / 100.0)
$fingerprint = Get-Sha256Hex ([Text.Encoding]::UTF8.GetBytes("$Platform-income-$eventId"))
$resolvedRawHashSeed = if ([string]::IsNullOrWhiteSpace($RawHashSeed)) {
    "$platformLabel|Income CNY $amountText"
} else {
    $RawHashSeed
}
$rawHash = Get-Sha256Hex ([Text.Encoding]::UTF8.GetBytes($resolvedRawHashSeed))
$eventBody = @{
    sentAt = [DateTimeOffset]::UtcNow.ToString('o')
    events = @(
        @{
            clientEventId = $eventId
            platform = $Platform
            direction = 'INCOME'
            amountMinor = $AmountMinor
            currency = 'CNY'
            eventTime = [DateTimeOffset]::UtcNow.ToString('o')
            parseStatus = 'PARSED'
            parserVersion = '1.0.0'
            matchedRule = "$($Platform.ToLowerInvariant())_income:fixture"
            fingerprint = $fingerprint
            rawHash = $rawHash
            rawPayload = @{
                title = $platformLabel
                text = "Income CNY $amountText"
            }
        }
    )
} | ConvertTo-Json -Depth 8 -Compress

$upload = Invoke-DeviceRequest 'POST' '/api/v1/payment-events/batch' $eventBody $deviceId $deviceSecret $credentialVersion
$duplicateUpload = Invoke-DeviceRequest 'POST' '/api/v1/payment-events/batch' $eventBody $deviceId $deviceSecret $credentialVersion

[pscustomobject]@{
    DeviceId = $deviceId
    MerchantCode = $pairResponse.data.merchantCode
    MerchantName = $pairResponse.data.merchantName
    ClientEventId = $eventId
    Platform = $Platform
    AmountMinor = $AmountMinor
    HeartbeatOk = $heartbeat.ok
    AcceptedCount = @($upload.data.accepted).Count
    DuplicateCount = @($duplicateUpload.data.duplicates).Count
    RejectedCount = @($upload.data.rejected).Count
}
