$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    docker compose ps --all
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar el estado.' }
} finally { Pop-Location }