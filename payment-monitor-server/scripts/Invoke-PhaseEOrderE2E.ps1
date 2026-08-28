param(
    [Parameter(Mandatory = $true)]
    [string]$AdminToken,
    [string]$ClientId = 'e5cd7e4891bf95d1d19206ce24a7b32e',
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$headers = @{
    Authorization = "Bearer $AdminToken"
    clientid = $ClientId
}
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

$assetBody = @{
    platform = 'WECHAT'
    assetName = "Phase E E2E $suffix"
    qrContentTemplate = 'https://pay.local/wechat?amount={amount}&order={orderNo}'
    status = '0'
    remark = 'Phase E repeatable E2E fixture'
} | ConvertTo-Json
$assetResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/payment/qr-assets" `
    -Headers $headers -ContentType 'application/json' `
    -Body ([Text.Encoding]::UTF8.GetBytes($assetBody))
if ($assetResponse.code -ne 200) {
    throw "QR asset creation failed: $($assetResponse.msg)"
}

$orders = @()
1..5 | ForEach-Object {
    $orderBody = @{
        merchantOrderNo = "PHASE-E-E2E-$suffix-$_"
        platform = 'WECHAT'
        qrAssetId = $assetResponse.data.id
        amountMinor = 12345
        expiresSeconds = 600
        subject = 'Phase E E2E'
    } | ConvertTo-Json
    $orderResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/payment/orders" `
        -Headers $headers -ContentType 'application/json' `
        -Body ([Text.Encoding]::UTF8.GetBytes($orderBody))
    if ($orderResponse.code -ne 200) {
        throw "Order creation failed: $($orderResponse.msg)"
    }
    $orders += $orderResponse.data
}

$amounts = @($orders | ForEach-Object { $_.payableAmountMinor })
if (@($amounts | Sort-Object -Unique).Count -ne $orders.Count) {
    throw 'Active dynamic amounts are not unique.'
}

$target = $orders[0]
$pairing = & (Join-Path $PSScriptRoot 'New-LocalPairingCode.ps1')
$fixture = & (Join-Path $PSScriptRoot 'Invoke-DeviceFixture.ps1') `
    -PairingCode $pairing.PairingCode -BaseUrl $BaseUrl `
    -Platform WECHAT -AmountMinor $target.payableAmountMinor

$matched = (Invoke-RestMethod -Method Get -Uri "$BaseUrl/payment/orders/$($target.id)" `
    -Headers $headers).data
if ($matched.status -ne 'PAID' -or -not $matched.matchedEventId) {
    throw 'Income event did not match the target order.'
}

$publicStatus = Invoke-RestMethod -Method Get `
    -Uri "$BaseUrl/api/public/payment-orders/$($target.publicToken)"
if ($publicStatus.status -ne 'PAID') {
    throw 'Public payment page status did not become PAID.'
}
$qr = Invoke-WebRequest -UseBasicParsing -Uri `
    "$BaseUrl/api/public/payment-orders/$($target.publicToken)/qr.svg"
if ($qr.StatusCode -ne 200 -or $qr.Headers['Content-Type'] -notmatch 'svg') {
    throw 'Public QR endpoint verification failed.'
}

[pscustomobject]@{
    AssetId = $assetResponse.data.id
    OrderId = $target.id
    PayableAmountMinor = $target.payableAmountMinor
    MatchedEventId = $matched.matchedEventId
    PublicStatus = $publicStatus.status
    AcceptedCount = $fixture.AcceptedCount
    DuplicateCount = $fixture.DuplicateCount
    UniqueReservationCount = @($amounts | Sort-Object -Unique).Count
}
