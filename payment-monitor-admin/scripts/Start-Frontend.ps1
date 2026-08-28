$ErrorActionPreference = 'Stop'
$adminRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = Split-Path $adminRoot -Parent
$nodeRoot = Join-Path $workspaceRoot '.tools\node-v22'

$env:COREPACK_HOME = Join-Path $workspaceRoot '.tools\corepack'
$env:PNPM_HOME = Join-Path $workspaceRoot '.tools\pnpm-home'
$env:Path = "$nodeRoot;$env:PNPM_HOME;$env:Path"

Push-Location $adminRoot
try {
    & (Join-Path $nodeRoot 'pnpm.cmd') install --registry=https://registry.npmmirror.com
    if ($LASTEXITCODE -ne 0) {
        throw "pnpm install failed with exit code $LASTEXITCODE"
    }
    & (Join-Path $nodeRoot 'pnpm.cmd') dev
} finally {
    Pop-Location
}
