$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    docker compose stop --timeout 60
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener toda la plataforma.' }
    Write-Host 'TrazaTex detenido. Los contenedores, volúmenes y datos se conservan.'
} finally { Pop-Location }