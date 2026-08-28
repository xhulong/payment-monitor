[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"
$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot ".env.local"
$cipherScript = Join-Path $PSScriptRoot "payment_secret_cipher.py"

$environment = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line.Split("=", 2)
        $environment[$parts[0]] = $parts[1]
    }
}

function ConvertTo-Hex([byte[]]$Bytes) {
    return ([BitConverter]::ToString($Bytes)).Replace("-", "").ToLowerInvariant()
}

function Get-Sha256Hex(
    [AllowNull()]
    [AllowEmptyCollection()]
    [byte[]]$Bytes
) {
    if ($null -eq $Bytes) {
        $Bytes = [byte[]]::new(0)
    }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-Hex ($sha.ComputeHash($Bytes))
    }
    finally {
        $sha.Dispose()
    }
}

function Get-HmacHex([string]$Secret, [string]$Value) {
    $hmac = [System.Security.Cryptography.HMACSHA256]::new(
        [Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        return ConvertTo-Hex ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))
    }
    finally {
        $hmac.Dispose()
    }
}

function Invoke-Psql([string]$Sql) {
    $output = & docker exec payment-monitor-postgres psql `
        -v ON_ERROR_STOP=1 -U payment_monitor -d payment_monitor -Atc $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
    return ($output | Out-String).Trim()
}

function Protect-Secret([string]$Secret) {
    $request = @{
        masterKey = $environment["PAYMENT_MASTER_KEY"]
        plainText = $Secret
    } | ConvertTo-Json -Compress
    $ciphertext = $request | python.exe $cipherScript
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($ciphertext)) {
        throw "Failed to encrypt merchant API secret"
    }
    return $ciphertext.Trim()
}

function Invoke-MerchantRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$KeyId,
        [Parameter(Mandatory = $true)][int]$CredentialVersion,
        [Parameter(Mandatory = $true)][string]$Secret,
        [string]$Body = "",
        [string]$SignatureBody,
        [long]$Timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds(),
        [string]$Nonce = ([Guid]::NewGuid().ToString("N"))
    )
    $bodyBytes = if ($null -eq $Body) {
        [byte[]]::new(0)
    } else {
        [Text.Encoding]::UTF8.GetBytes($Body)
    }
    $signatureBytes = if ($PSBoundParameters.ContainsKey("SignatureBody")) {
        [Text.Encoding]::UTF8.GetBytes($SignatureBody)
    } else {
        $bodyBytes
    }
    $canonical = "$($Method.ToUpperInvariant())`n$Path`n$Timestamp`n$Nonce`n$(Get-Sha256Hex $signatureBytes)"
    $headers = @{
        "X-Merchant-Key-Id" = $KeyId
        "X-Credential-Version" = $CredentialVersion.ToString()
        "X-Timestamp" = $Timestamp.ToString()
        "X-Nonce" = $Nonce
        "X-Signature" = Get-HmacHex $Secret $canonical
    }
    Add-Type -AssemblyName System.Net.Http
    $httpMethod = switch ($Method.ToUpperInvariant()) {
        "GET" { [System.Net.Http.HttpMethod]::Get }
        "POST" { [System.Net.Http.HttpMethod]::Post }
        "PUT" { [System.Net.Http.HttpMethod]::Put }
        default { [System.Net.Http.HttpMethod]::new($Method.ToUpperInvariant()) }
    }
    $client = [System.Net.Http.HttpClient]::new()
    $requestMessage = [System.Net.Http.HttpRequestMessage]::new(
        $httpMethod,
        "$BaseUrl$Path")
    try {
        foreach ($entry in $headers.GetEnumerator()) {
            $requestMessage.Headers.TryAddWithoutValidation(
                $entry.Key,
                [string]$entry.Value) | Out-Null
        }
        if ($bodyBytes.Length -gt 0) {
            $requestMessage.Content = [System.Net.Http.ByteArrayContent]::new($bodyBytes)
            $requestMessage.Content.Headers.ContentType =
                [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse(
                    "application/json; charset=utf-8")
        }
        $response = $client.SendAsync($requestMessage).GetAwaiter().GetResult()
        try {
            $statusCode = [int]$response.StatusCode
            $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        }
        finally {
            $response.Dispose()
        }
    }
    finally {
        $requestMessage.Dispose()
        $client.Dispose()
    }
    $payload = if ([string]::IsNullOrWhiteSpace($content)) {
        $null
    } else {
        $content | ConvertFrom-Json
    }
    return [pscustomobject]@{
        Status = $statusCode
        Payload = $payload
        Timestamp = $Timestamp
        Nonce = $Nonce
        Headers = $headers
    }
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

& (Join-Path $PSScriptRoot "Test-LocalStackHealth.ps1")

$merchantA = 1900000000000000101
$merchantB = 1900000000000000102
$assetA = 1900000000000000201
$assetB = 1900000000000000202
$keyDbA = 1900000000000000301
$keyDbB = 1900000000000000302
$credentialA1 = 1900000000000000401
$credentialB1 = 1900000000000000402
$credentialA2 = 1900000000000000403
$keyIdA = "mk_phase_g_a"
$keyIdB = "mk_phase_g_b"
$secretA1 = "phase-g-a-" + [Guid]::NewGuid().ToString("N")
$secretB1 = "phase-g-b-" + [Guid]::NewGuid().ToString("N")
$secretA2 = "phase-g-a-rotated-" + [Guid]::NewGuid().ToString("N")
$cipherA1 = Protect-Secret $secretA1
$cipherB1 = Protect-Secret $secretB1
$cipherA2 = Protect-Secret $secretA2

$setupSql = @"
delete from pm_order_match_audit where merchant_id in ($merchantA, $merchantB);
delete from pm_webhook_outbox where merchant_id in ($merchantA, $merchantB);
delete from pm_payment_order where merchant_id in ($merchantA, $merchantB);
delete from pm_merchant_api_credential where api_key_id in ($keyDbA, $keyDbB);
delete from pm_merchant_api_key where id in ($keyDbA, $keyDbB);
delete from pm_qr_asset where merchant_id in ($merchantA, $merchantB);
delete from pm_merchant_user where merchant_id in ($merchantA, $merchantB);
insert into pm_merchant (
    id, merchant_code, name, status, timezone, remark, created_at, updated_at
) values
    ($merchantA, 'PHASE_G_A', 'Phase G Merchant A', '0', 'Asia/Shanghai', 'Phase G E2E', now(), now()),
    ($merchantB, 'PHASE_G_B', 'Phase G Merchant B', '0', 'UTC', 'Phase G E2E', now(), now())
on conflict (id) do update set
    merchant_code = excluded.merchant_code,
    name = excluded.name,
    status = excluded.status,
    timezone = excluded.timezone,
    updated_at = now();
insert into pm_qr_asset (
    id, merchant_id, asset_code, platform, asset_name,
    qr_content_template, status, created_at, updated_at
) values
    ($assetA, $merchantA, 'SHARED-ASSET', 'WECHAT', 'Merchant A WeChat QR',
     'https://example.invalid/a?amount={amount}', '0', now(), now()),
    ($assetB, $merchantB, 'SHARED-ASSET', 'WECHAT', 'Merchant B WeChat QR',
     'https://example.invalid/b?amount={amount}', '0', now(), now());
insert into pm_merchant_api_key (
    id, merchant_id, key_id, key_name, status, current_version,
    created_at, updated_at
) values
    ($keyDbA, $merchantA, '$keyIdA', 'Phase G A', '0', 1, now(), now()),
    ($keyDbB, $merchantB, '$keyIdB', 'Phase G B', '0', 1, now(), now());
insert into pm_merchant_api_credential (
    id, api_key_id, credential_version, secret_ciphertext, created_at
) values
    ($credentialA1, $keyDbA, 1, '$cipherA1', now()),
    ($credentialB1, $keyDbB, 1, '$cipherB1', now());
"@
Invoke-Psql $setupSql | Out-Null

$orderNo = "PHASE-G-SHARED-ORDER"
$body = @{
    merchantOrderNo = $orderNo
    platform = "WECHAT"
    qrAssetCode = "SHARED-ASSET"
    amountMinor = 12345
    expiresSeconds = 600
    subject = "Phase G isolation"
} | ConvertTo-Json -Compress

$createA = Invoke-MerchantRequest POST "/api/v1/merchant/orders" $keyIdA 1 $secretA1 $body
$createB = Invoke-MerchantRequest POST "/api/v1/merchant/orders" $keyIdB 1 $secretB1 $body
Assert-True ($createA.Status -eq 200 -and $createA.Payload.ok) `
    "Merchant A order creation failed: HTTP $($createA.Status) $($createA.Payload | ConvertTo-Json -Depth 5 -Compress)"
Assert-True ($createB.Status -eq 200 -and $createB.Payload.ok) `
    "Merchant B order creation failed: HTTP $($createB.Status) $($createB.Payload | ConvertTo-Json -Depth 5 -Compress)"
Assert-True ($createA.Payload.data.payUrl -ne $createB.Payload.data.payUrl) "Merchant orders shared a public token"

$idempotentA = Invoke-MerchantRequest POST "/api/v1/merchant/orders" $keyIdA 1 $secretA1 $body
Assert-True ($idempotentA.Payload.data.payableAmountMinor -eq $createA.Payload.data.payableAmountMinor) `
    "Merchant order idempotency changed payable amount"

$conflictBody = @{
    merchantOrderNo = $orderNo
    platform = "WECHAT"
    qrAssetCode = "SHARED-ASSET"
    amountMinor = 54321
    expiresSeconds = 600
} | ConvertTo-Json -Compress
$conflict = Invoke-MerchantRequest POST "/api/v1/merchant/orders" $keyIdA 1 $secretA1 $conflictBody
Assert-True ($conflict.Status -eq 409 -and $conflict.Payload.error.code -eq "ORDER_CONFLICT") `
    "ORDER_CONFLICT was not returned"

$getPath = "/api/v1/merchant/orders/$orderNo"
$getA = Invoke-MerchantRequest GET $getPath $keyIdA 1 $secretA1
$getB = Invoke-MerchantRequest GET $getPath $keyIdB 1 $secretB1
Assert-True ($getA.Payload.data.payUrl -eq $createA.Payload.data.payUrl) "Merchant A queried wrong order"
Assert-True ($getB.Payload.data.payUrl -eq $createB.Payload.data.payUrl) "Merchant B queried wrong order"

$replayTimestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$replayNonce = [Guid]::NewGuid().ToString("N")
$firstReplay = Invoke-MerchantRequest GET $getPath $keyIdA 1 $secretA1 `
    -Timestamp $replayTimestamp -Nonce $replayNonce
$secondReplay = Invoke-MerchantRequest GET $getPath $keyIdA 1 $secretA1 `
    -Timestamp $replayTimestamp -Nonce $replayNonce
Assert-True ($firstReplay.Status -eq 200) "Initial nonce request failed"
Assert-True ($secondReplay.Status -eq 401 -and $secondReplay.Payload.error.code -eq "AUTH_NONCE_REUSED") `
    "Nonce replay was not rejected"

$expired = Invoke-MerchantRequest GET $getPath $keyIdA 1 $secretA1 `
    -Timestamp ([DateTimeOffset]::UtcNow.AddMinutes(-10).ToUnixTimeSeconds())
Assert-True ($expired.Status -eq 401 -and $expired.Payload.error.code -eq "AUTH_TIMESTAMP_EXPIRED") `
    "Expired timestamp was not rejected"

$tampered = Invoke-MerchantRequest POST "/api/v1/merchant/orders" $keyIdA 1 $secretA1 `
    -Body $conflictBody -SignatureBody $body
Assert-True ($tampered.Status -eq 401 -and $tampered.Payload.error.code -eq "AUTH_SIGNATURE_INVALID") `
    "Tampered body was not rejected"

$rotateSql = @"
update pm_merchant_api_credential
set revoked_at = now()
where api_key_id = $keyDbA and revoked_at is null;
insert into pm_merchant_api_credential (
    id, api_key_id, credential_version, secret_ciphertext, created_at
) values ($credentialA2, $keyDbA, 2, '$cipherA2', now());
update pm_merchant_api_key
set current_version = 2, updated_at = now()
where id = $keyDbA;
"@
Invoke-Psql $rotateSql | Out-Null
$oldCredential = Invoke-MerchantRequest GET $getPath $keyIdA 1 $secretA1
$rotatedCredential = Invoke-MerchantRequest GET $getPath $keyIdA 2 $secretA2
Assert-True ($oldCredential.Status -eq 401 -and $oldCredential.Payload.error.code -eq "MERCHANT_KEY_REVOKED") `
    "Old credential remained active after rotation"
Assert-True ($rotatedCredential.Status -eq 200) "Rotated credential failed"

$cancelB = Invoke-MerchantRequest PUT "$getPath/cancel" $keyIdB 1 $secretB1
Assert-True ($cancelB.Status -eq 200 -and $cancelB.Payload.data.status -eq "CANCELLED") `
    "Merchant B cancellation failed"
Invoke-Psql "update pm_merchant_api_key set status='1', updated_at=now() where id=$keyDbB;" | Out-Null
$revokedB = Invoke-MerchantRequest GET $getPath $keyIdB 1 $secretB1
Assert-True ($revokedB.Status -eq 401 -and $revokedB.Payload.error.code -eq "MERCHANT_KEY_REVOKED") `
    "Revoked API Key remained active"

$databaseRows = Invoke-Psql @"
select merchant_id || '|' || merchant_order_no || '|' || requested_amount_minor
from pm_payment_order
where merchant_id in ($merchantA, $merchantB)
order by merchant_id;
"@
Assert-True (($databaseRows -split "`r?`n").Count -eq 2) "Expected one isolated order per merchant"

$artifactDirectory = Join-Path $serverRoot "artifacts\phase-g"
New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
$reportPath = Join-Path $artifactDirectory "merchant-api-e2e.json"
[ordered]@{
    testedAt = [DateTimeOffset]::UtcNow.ToString("o")
    flywayVersion = Invoke-Psql "select version from pm_flyway_schema_history where success order by installed_rank desc limit 1"
    merchantA = @{
        id = $merchantA
        orderNo = $orderNo
        payableAmountMinor = $createA.Payload.data.payableAmountMinor
        apiKeyRotation = "passed"
    }
    merchantB = @{
        id = $merchantB
        orderNo = $orderNo
        payableAmountMinor = $createB.Payload.data.payableAmountMinor
        apiKeyRevocation = "passed"
    }
    exactBodyHmac = "passed"
    nonceReplay = "passed"
    expiredTimestamp = "passed"
    tamperedBody = "passed"
    idempotency = "passed"
    orderConflict = "passed"
    databaseRows = $databaseRows
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "Phase G merchant API E2E passed: $reportPath"
