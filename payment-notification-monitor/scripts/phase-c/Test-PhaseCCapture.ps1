param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath
)

$ErrorActionPreference = 'Stop'
$path = [System.IO.Path]::GetFullPath($InputPath)
if (-not (Test-Path -LiteralPath $path)) {
    throw "Capture file not found: $path"
}

$lineNumber = 0
$records = @()
Get-Content -LiteralPath $path -Encoding UTF8 | ForEach-Object {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($_)) {
        return
    }
    try {
        $record = $_ | ConvertFrom-Json
    } catch {
        throw "Invalid JSON at line $lineNumber`: $($_.Exception.Message)"
    }
    if ($record.schema -ne 1) {
        throw "Unsupported capture schema at line $lineNumber"
    }
    if ($record.raw.packageName -notin @(
        'com.tencent.mm',
        'com.eg.android.AlipayGphone'
    )) {
        throw "Unexpected source package at line $lineNumber"
    }
    $records += $record
}

if ($records.Count -eq 0) {
    throw 'Capture file contains no records'
}

$records |
    Group-Object { "$($_.raw.packageName)|$($_.parser.direction)|$($_.parser.matched)" } |
    Sort-Object Name |
    Select-Object Name, Count
Write-Host "Validated $($records.Count) capture records from $path"
