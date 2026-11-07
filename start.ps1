param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Configura .env a partir de .env.example.' }
    & (Join-Path $PSScriptRoot 'backend/infra/scripts/generate-jwt-keys.ps1')
    $settings = @{}
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot '.env'))) {
        if ($line -match '^([A-Z0-9_]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim('"', "'") }
    }
    $configuration = docker compose config --format json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Configuración Compose inválida.' }
    $existing = @(docker volume ls --format '{{.Name}}')
    foreach ($volume in $configuration.volumes.PSObject.Properties) {
        if ($volume.Value.external -and $existing -notcontains $volume.Value.name) {
            docker volume create $volume.Value.name | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el volumen.' }
        }
    }
    $arguments = @('compose', 'up', '-d', '--wait', '--wait-timeout', '300')
    if (-not $SkipBuild) { $arguments += '--build' }
    & docker @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Revisa los servicios con status.ps1 y Portainer.' }
    $portainer = 'https://127.0.0.1:19443'
    $check = Invoke-WebRequest "$portainer/api/users/admin/check" -SkipCertificateCheck -SkipHttpErrorCheck
    if ($check.StatusCode -eq 404) {
        $body = @{ Username = $settings.ADMIN_USERNAME; Password = $settings.PORTAINER_PASSWORD } | ConvertTo-Json
        Invoke-RestMethod "$portainer/api/users/admin/init" -Method Post -ContentType 'application/json' -Body $body -SkipCertificateCheck | Out-Null
    } elseif ($check.StatusCode -ne 204) { throw 'No se pudo comprobar la autenticación de Portainer.' }
    foreach ($url in @('http://127.0.0.1:14200/login', 'http://127.0.0.1:15173', 'http://127.0.0.1:15050/login', 'http://127.0.0.1:17474/browser/', 'http://127.0.0.1:18090', 'http://127.0.0.1:18095')) {
        $response = Invoke-WebRequest $url -SkipHttpErrorCheck
        if ($response.StatusCode -notin @(200, 401, 302)) { throw "Panel no disponible: $url" }
        Write-Host "Disponible: $url"
    }
    Write-Host "Portainer: $portainer"
    Write-Host 'Credenciales administrativas: archivo privado .env.'
} finally { Pop-Location }