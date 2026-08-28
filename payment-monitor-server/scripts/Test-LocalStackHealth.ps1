$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'
$composeFile = Join-Path $serverRoot 'deploy\docker-compose.local.yml'

$services = @('postgres', 'redis', 'minio', 'qdrant', 'snailai', 'backend', 'admin')
foreach ($service in $services) {
    $containerId = (& docker compose --env-file $envFile -f $composeFile ps -q $service).Trim()
    if (-not $containerId) {
        throw "Service is not running: $service"
    }
    $health = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId).Trim()
    if ($health -ne 'healthy' -and $health -ne 'running') {
        throw "Service $service is not healthy: $health"
    }
}

$admin = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173/' -TimeoutSec 10
if ($admin.StatusCode -ne 200) {
    throw "Admin returned HTTP $($admin.StatusCode)"
}
$modulePath = [regex]::Match($admin.Content, '<script[^>]+type="module"[^>]+src="([^"]+)"').Groups[1].Value
if (-not $modulePath) {
    throw 'Admin index did not contain a module script.'
}
$module = Invoke-WebRequest -UseBasicParsing -Uri ("http://127.0.0.1:5173" + $modulePath) -TimeoutSec 10
if ($module.Headers['Content-Type'] -notmatch 'javascript') {
    throw "Admin module MIME is invalid: $($module.Headers['Content-Type'])"
}

$snailAi = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173/snail-ai/' -TimeoutSec 20
if ($snailAi.StatusCode -ne 200 -or $snailAi.Content -notmatch 'Snail AI') {
    throw 'Snail AI console is unavailable.'
}
$snailAiAssets = [regex]::Matches($snailAi.Content, '(?:src|href)="([^"]+)"') |
    ForEach-Object { $_.Groups[1].Value } |
    Where-Object { $_ -match '^/snail-ai/.+\.(?:js|css)$' }
if (-not $snailAiAssets) {
    throw 'Snail AI console did not contain JavaScript or CSS assets.'
}
foreach ($assetPath in $snailAiAssets) {
    $asset = Invoke-WebRequest -UseBasicParsing -Uri ("http://127.0.0.1:5173" + $assetPath) -TimeoutSec 20
    if ($asset.StatusCode -ne 200) {
        throw "Snail AI asset returned HTTP $($asset.StatusCode): $assetPath"
    }
}

$snailChat = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173/prod-api/snail-chat/' -TimeoutSec 20
if ($snailChat.StatusCode -ne 200 -or $snailChat.Content -notmatch 'Snail AI Chat') {
    throw 'Snail AI chat UI is unavailable.'
}
$snailChatAssets = [regex]::Matches($snailChat.Content, '(?:src|href)="([^"]+)"') |
    ForEach-Object { $_.Groups[1].Value } |
    Where-Object { $_ -match '\.(?:js|css)$' }
if (-not $snailChatAssets) {
    throw 'Snail AI chat UI did not contain JavaScript or CSS assets.'
}
$snailChatBaseUri = [Uri]'http://127.0.0.1:5173/prod-api/snail-chat/'
foreach ($assetPath in $snailChatAssets) {
    $assetUri = [Uri]::new($snailChatBaseUri, $assetPath)
    $asset = Invoke-WebRequest -UseBasicParsing -Uri $assetUri.AbsoluteUri -TimeoutSec 20
    if ($asset.StatusCode -ne 200) {
        throw "Snail AI chat asset returned HTTP $($asset.StatusCode): $assetPath"
    }
}

$qdrant = Invoke-RestMethod -Uri 'http://127.0.0.1:6333/' -TimeoutSec 10
if ($qdrant.title -ne 'qdrant - vector search engine') {
    throw 'Qdrant vector database is unavailable.'
}

Write-Host 'Local stack health check passed.'
