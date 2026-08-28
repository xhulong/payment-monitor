param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$input = [System.IO.Path]::GetFullPath($InputPath)
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($input)
    $OutputPath = Join-Path $projectRoot "research\phase-c\generated\$baseName.fixture.json"
}
$output = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($output)) | Out-Null

function Get-HashToken([string]$Prefix, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        $hashText = [BitConverter]::ToString($sha.ComputeHash($bytes))
        $hash = $hashText.Replace('-', '').Substring(0, 16)
        return "<$Prefix`_$hash>"
    } finally {
        $sha.Dispose()
    }
}

function Mask-Text([object]$Value) {
    if ($null -eq $Value) {
        return $null
    }
    $text = [string]$Value
    $text = [regex]::Replace($text, '(?<!\d)1[3-9]\d{9}(?!\d)', '<PHONE>')
    $text = [regex]::Replace(
        $text,
        '(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',
        '<UUID>'
    )
    $text = [regex]::Replace($text, '(?<!\d)\d{10,}(?!\d)', '<LONG_ID>')
    $text = [regex]::Replace($text, '(尾号|末四位)\s*\d{4}', '$1<ACCOUNT_TAIL>')
    return $text
}

$cases = @()
$index = 0
Get-Content -LiteralPath $input -Encoding UTF8 | ForEach-Object {
    if ([string]::IsNullOrWhiteSpace($_)) {
        return
    }
    $record = $_ | ConvertFrom-Json
    $index++
    $raw = $record.raw
    $extras = [ordered]@{}
    foreach ($property in $raw.extras.PSObject.Properties) {
        $extras[$property.Name] = Mask-Text $property.Value
    }
    $sanitizedRaw = [ordered]@{
        packageName = $raw.packageName
        notificationId = $raw.notificationId
        notificationTag = Get-HashToken 'TAG' ([string]$raw.notificationTag)
        notificationKey = Get-HashToken 'KEY' ([string]$raw.notificationKey)
        postTime = 1700000000000 + $index
        title = Mask-Text $raw.title
        text = Mask-Text $raw.text
        bigText = Mask-Text $raw.bigText
        textLines = @($raw.textLines | ForEach-Object { Mask-Text $_ })
        ticker = Mask-Text $raw.ticker
        subText = Mask-Text $raw.subText
        infoText = Mask-Text $raw.infoText
        summaryText = Mask-Text $raw.summaryText
        extras = $extras
    }
    $cases += [ordered]@{
        caseId = ('{0}_{1:d3}' -f $record.scenario.ToString().ToLowerInvariant(), $index)
        scenario = $record.scenario
        sourcePackageVersion = $record.sourcePackageVersion
        reviewed = $false
        expected = [ordered]@{
            matched = [bool]$record.parser.matched
            platform = $record.parser.platform
            direction = $record.parser.direction
            parseStatus = $record.parser.parseStatus
            amount = $record.parser.amount
        }
        raw = $sanitizedRaw
    }
}

$fixture = [ordered]@{
    schema = 1
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    sourceFileHash = (Get-FileHash -LiteralPath $input -Algorithm SHA256).Hash.ToLowerInvariant()
    cases = $cases
}
$json = $fixture | ConvertTo-Json -Depth 30
[System.IO.File]::WriteAllText(
    $output,
    $json,
    [System.Text.UTF8Encoding]::new($false)
)
Get-Item -LiteralPath $output | Select-Object FullName, Length, LastWriteTime
Write-Host 'Review every case and set reviewed=true before copying it into app/src/test/resources/payment-fixtures/v2.'
