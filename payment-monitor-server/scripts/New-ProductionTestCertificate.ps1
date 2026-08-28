[CmdletBinding()]
param(
    [string]$Domain = "payment-monitor.local",
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $scriptRoot "..\deploy\production\certs"
}
$resolved = [System.IO.Path]::GetFullPath($OutputDirectory)
$expectedRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot "..\deploy\production"))
if (-not $resolved.StartsWith($expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Certificate output must stay under $expectedRoot"
}
New-Item -ItemType Directory -Force -Path $resolved | Out-Null

$configPath = Join-Path $resolved "openssl-test.cnf"
@"
[req]
distinguished_name=req_distinguished_name
x509_extensions=v3_req
prompt=no
[req_distinguished_name]
CN=$Domain
[v3_req]
subjectAltName=@alt_names
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
[alt_names]
DNS.1=$Domain
DNS.2=localhost
IP.1=127.0.0.1
"@ | Set-Content -Encoding Ascii $configPath

docker run --rm `
    -v "${resolved}:/certs" `
    alpine/openssl req -x509 -nodes -newkey rsa:2048 -days 30 `
    -keyout /certs/payment-monitor.key `
    -out /certs/payment-monitor.crt `
    -config /certs/openssl-test.cnf

if ($LASTEXITCODE -ne 0) {
    throw "Self-signed certificate generation failed"
}
Remove-Item -LiteralPath $configPath -Force
Write-Host "Created local test certificate for $Domain in $resolved"
