$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

if (-not (Test-Path ".\src\main\resources\application-local.yaml")) {
    throw "Missing src\main\resources\application-local.yaml. Create it from the README example and add local database credentials."
}

& ".\mvnw.cmd" spring-boot:run "-Dspring-boot.run.profiles=local"
exit $LASTEXITCODE