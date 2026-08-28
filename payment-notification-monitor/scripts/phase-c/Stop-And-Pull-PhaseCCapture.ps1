param(
    [string]$DeviceSerial = 'f6zh89or49vorgin',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.example.paymentmonitor'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot 'research\phase-c\raw'
}
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null

& adb -s $DeviceSerial shell am broadcast `
    -a 'com.example.paymentmonitor.DEBUG_CAPTURE_STOP' `
    -n "$packageName/.debug.DebugFixtureReceiver" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to stop phase C capture'
}

$files = @(
    & adb -s $DeviceSerial shell run-as $packageName `
        ls -1t 'files/phase-c-captures'
) | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^[A-Za-z0-9._-]+\.jsonl$' }

if ($files.Count -eq 0) {
    throw 'No capture files were found in the app private directory'
}

$adb = (Get-Command adb).Source
$pulled = @()
foreach ($fileName in $files) {
    $destination = Join-Path $OutputDirectory $fileName
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $adb
    $psi.Arguments = "-s $DeviceSerial exec-out run-as $packageName cat files/phase-c-captures/$fileName"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $process = [System.Diagnostics.Process]::Start($psi)
    $content = $process.StandardOutput.ReadToEnd()
    $errorText = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "Failed to pull $fileName`: $errorText"
    }
    [System.IO.File]::WriteAllText(
        $destination,
        $content,
        [System.Text.UTF8Encoding]::new($false)
    )
    $pulled += Get-Item -LiteralPath $destination
}

$pulled | Select-Object FullName, Length, LastWriteTime
