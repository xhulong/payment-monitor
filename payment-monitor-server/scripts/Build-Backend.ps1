$ErrorActionPreference = 'Stop'
$serverRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = Split-Path $serverRoot -Parent
$env:JAVA_HOME = 'D:\java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$mavenRepo = (Join-Path $workspaceRoot '.tools\m2-repository').Replace('\', '/')

Push-Location $serverRoot
try {
    & mvn.cmd "-Dmaven.repo.local=$mavenRepo" '-DskipTests=false' '-Dmaven.test.skip=false' clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
