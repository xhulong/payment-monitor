[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$MerchantId = 1900000000000000101
)

$ErrorActionPreference = "Stop"

function Invoke-Psql([string]$Sql) {
    $output = & docker exec payment-monitor-postgres psql `
        -v ON_ERROR_STOP=1 -U payment_monitor -d payment_monitor -Atc $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
    return ($output | Out-String).Trim()
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

& (Join-Path $PSScriptRoot "Test-LocalStackHealth.ps1")
$merchantCode = Invoke-Psql "select merchant_code from pm_merchant where id=$MerchantId and status='0'"
Assert-True (-not [string]::IsNullOrWhiteSpace($merchantCode)) "Enabled Phase G merchant not found"

$amount = Get-Random -Minimum 7000000 -Maximum 8000000
$sameRawSeed = "phase-g-shared-notification-$([Guid]::NewGuid().ToString('N'))"

$pairing1 = & (Join-Path $PSScriptRoot "New-LocalPairingCode.ps1") -MerchantId $MerchantId
$first = & (Join-Path $PSScriptRoot "Invoke-DeviceFixture.ps1") `
    -PairingCode $pairing1.PairingCode `
    -BaseUrl $BaseUrl `
    -Platform WECHAT `
    -AmountMinor $amount `
    -RawHashSeed $sameRawSeed `
    -DeviceName "Phase G Duplicate Device 1"

$pairing2 = & (Join-Path $PSScriptRoot "New-LocalPairingCode.ps1") -MerchantId $MerchantId
$second = & (Join-Path $PSScriptRoot "Invoke-DeviceFixture.ps1") `
    -PairingCode $pairing2.PairingCode `
    -BaseUrl $BaseUrl `
    -Platform WECHAT `
    -AmountMinor $amount `
    -RawHashSeed $sameRawSeed `
    -DeviceName "Phase G Duplicate Device 2"

Assert-True ($first.MerchantCode -eq $merchantCode -and $second.MerchantCode -eq $merchantCode) `
    "Pairing response did not preserve merchant identity"
$suspected = Invoke-Psql @"
select duplicate_status || '|' || coalesce(duplicate_of_event_id::text, '')
from pm_payment_event
where merchant_id = $MerchantId
  and client_event_id = '$($second.ClientEventId)';
"@
$firstEventId = Invoke-Psql @"
select id
from pm_payment_event
where merchant_id = $MerchantId
  and client_event_id = '$($first.ClientEventId)';
"@
Assert-True ($suspected -eq "SUSPECTED|$firstEventId") `
    "Cross-device duplicate was not linked to the first event: $suspected"

$continuousAmount = $amount + 1
$pairing3 = & (Join-Path $PSScriptRoot "New-LocalPairingCode.ps1") -MerchantId $MerchantId
$continuous1 = & (Join-Path $PSScriptRoot "Invoke-DeviceFixture.ps1") `
    -PairingCode $pairing3.PairingCode `
    -BaseUrl $BaseUrl `
    -Platform WECHAT `
    -AmountMinor $continuousAmount `
    -RawHashSeed "phase-g-real-payment-1-$([Guid]::NewGuid().ToString('N'))" `
    -DeviceName "Phase G Continuous Device 1"

$pairing4 = & (Join-Path $PSScriptRoot "New-LocalPairingCode.ps1") -MerchantId $MerchantId
$continuous2 = & (Join-Path $PSScriptRoot "Invoke-DeviceFixture.ps1") `
    -PairingCode $pairing4.PairingCode `
    -BaseUrl $BaseUrl `
    -Platform WECHAT `
    -AmountMinor $continuousAmount `
    -RawHashSeed "phase-g-real-payment-2-$([Guid]::NewGuid().ToString('N'))" `
    -DeviceName "Phase G Continuous Device 2"

$continuousStatuses = Invoke-Psql @"
select client_event_id || '|' || duplicate_status
from pm_payment_event
where merchant_id = $MerchantId
  and client_event_id in ('$($continuous1.ClientEventId)', '$($continuous2.ClientEventId)')
order by client_event_id;
"@
$continuousRows = @($continuousStatuses -split "`r?`n")
Assert-True ($continuousRows.Count -eq 2) "Continuous same-amount events were lost"
Assert-True (@($continuousRows | Where-Object { $_ -notmatch '\|NONE$' }).Count -eq 0) `
    "Different raw hashes were incorrectly marked duplicate: $continuousStatuses"

$artifactDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) "artifacts\phase-g"
New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
$reportPath = Join-Path $artifactDirectory "duplicate-e2e.json"
[ordered]@{
    testedAt = [DateTimeOffset]::UtcNow.ToString("o")
    merchantId = $MerchantId
    merchantCode = $merchantCode
    suspected = @{
        firstClientEventId = $first.ClientEventId
        secondClientEventId = $second.ClientEventId
        status = "SUSPECTED"
        duplicateOfEventId = $firstEventId
    }
    continuousSameAmount = @{
        amountMinor = $continuousAmount
        retainedCount = 2
        statuses = $continuousRows
    }
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "Phase G duplicate E2E passed: $reportPath"
