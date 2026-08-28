param(
    [string]$AdminRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '..\payment-monitor-admin')
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$adminRoot = [System.IO.Path]::GetFullPath($AdminRoot)

function New-RandomSecret([int]$Length = 48) {
    $bytes = New-Object byte[] $Length
    $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-RandomHex([int]$Length = 16) {
    $bytes = New-Object byte[] $Length
    $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}

$databasePassword = New-RandomSecret 24
$redisPassword = New-RandomSecret 24

$serverEnv = @"
DB_URL=jdbc:postgresql://localhost:5433/payment_monitor
DB_PORT=5433
DB_USERNAME=payment_monitor
DB_PASSWORD=$databasePassword
PAYMENT_PUBLIC_BASE_URL=http://localhost:8080
PAYMENT_RAW_PAYLOAD_UPLOAD_ENABLED=false
PAYMENT_AGREEMENT_VERSION=2026-07
PAYMENT_PRIVACY_VERSION=2026-07
REDIS_HOST=localhost
REDIS_PORT=6380
REDIS_PASSWORD=$redisPassword
SA_TOKEN_JWT_SECRET=$(New-RandomSecret)
PAYMENT_MASTER_KEY=$(New-RandomSecret)
ACCOUNT_MFA_MASTER_KEY=$(New-RandomSecret)
ACCOUNT_RECOVERY_CODE_PEPPER=$(New-RandomSecret)
MAIL_OUTBOX_MASTER_KEY=$(New-RandomSecret)
APK_DOWNLOAD_SIGNING_SECRET=$(New-RandomSecret)
MINIO_ROOT_USER=paymentmonitor
MINIO_ROOT_PASSWORD=$(New-RandomSecret 24)
MINIO_BUCKET=payment-monitor-private
SNAIL_AI_CRYPTO_KEY=$(New-RandomHex 16)
SNAIL_AI_CRYPTO_IV=$(New-RandomHex 16)
SNAIL_AI_MODEL_PROVIDER_NAME=OpenAI Compatible
SNAIL_AI_MODEL_PROVIDER_KEY=openai
SNAIL_AI_MODEL_NAME=gpt-4.1-mini
SNAIL_AI_MODEL_API_KEY=
SNAIL_AI_MODEL_ENDPOINT=https://api.openai.com/v1
SNAIL_AI_WEB_PORT=8900
SNAIL_AI_GRPC_PORT=18888
SNAIL_AI_UPLOAD_MAX_FILE_SIZE=100MB
SNAIL_AI_UPLOAD_MAX_REQUEST_SIZE=120MB
QDRANT_HTTP_PORT=6333
QDRANT_GRPC_PORT=6334
API_CRYPTO_V2_ENABLED=true
API_CRYPTO_V2_ALLOW_EPHEMERAL_DEV_KEY=true
"@

$adminEnv = @"
VITE_APP_PORT=5173
VITE_APP_API_CRYPTO_V2=true
"@

Set-Content -LiteralPath (Join-Path $serverRoot '.env.local') -Value $serverEnv -Encoding UTF8
Set-Content -LiteralPath (Join-Path $adminRoot '.env.development.local') -Value $adminEnv -Encoding UTF8

Write-Host "Created backend environment file: $(Join-Path $serverRoot '.env.local')"
Write-Host "Created frontend environment file: $(Join-Path $adminRoot '.env.development.local')"
