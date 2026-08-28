param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = Split-Path $serverRoot -Parent
$envFile = Join-Path $serverRoot '.env.local'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Missing .env.local. Run scripts\Initialize-LocalSecrets.ps1 first.'
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
    }
}

$env:JAVA_HOME = 'D:\java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$mavenRepo = (Join-Path $workspaceRoot '.tools\m2-repository').Replace('\', '/')

Push-Location $serverRoot
try {
    if (-not $SkipBuild) {
        & mvn.cmd "-Dmaven.repo.local=$mavenRepo" '-DskipTests=true' -pl ruoyi-admin -am package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed with exit code $LASTEXITCODE"
        }
    }
    & java -jar (Join-Path $serverRoot 'ruoyi-admin\target\ruoyi-admin.jar')
} finally {
    Pop-Location
}
