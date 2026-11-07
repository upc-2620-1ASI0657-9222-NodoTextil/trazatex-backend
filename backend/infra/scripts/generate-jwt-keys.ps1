param([string]$EnvPath = (Join-Path $PSScriptRoot '../../../.env'))
$ErrorActionPreference = 'Stop'
$EnvPath = [IO.Path]::GetFullPath($EnvPath)
if (-not (Test-Path -LiteralPath $EnvPath -PathType Leaf)) { throw 'Prepara .env a partir de .env.example antes de generar las claves.' }
$content = [IO.File]::ReadAllText($EnvPath)
$names = @('JWT_PRIVATE_KEY','JWT_PUBLIC_KEY','JWT_KEY_ID')
$present = @($names | Where-Object {
    $match = [regex]::Match($content, "(?m)^$($_)=(.*)$")
    $match.Success -and $match.Groups[1].Value.Trim().Trim('"', "'")
})
if ($present.Count -eq 3) { Write-Host 'Se conserva el par JWT configurado.'; return }
if ($present.Count -ne 0) { throw 'La configuración JWT está incompleta. Conserva el par existente y completa sus tres variables; no se reemplazó ninguna clave.' }
$rsa = [Security.Cryptography.RSA]::Create(2048)
try {
    $values = @{
        JWT_PRIVATE_KEY = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($rsa.ExportPkcs8PrivateKeyPem()))
        JWT_PUBLIC_KEY = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($rsa.ExportSubjectPublicKeyInfoPem()))
        JWT_KEY_ID = 'trazatex-' + [DateTime]::UtcNow.ToString('yyyyMMdd') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
    }
    foreach ($key in $names) {
        $value = $values[$key]
        if ([regex]::IsMatch($content, "(?m)^$key=")) {
            $content = [regex]::Replace($content, "(?m)^$key=.*$", { param($match) "$key=$value" })
        } else { $content = $content.TrimEnd() + "`n$key=$value`n" }
    }
    [IO.File]::WriteAllText($EnvPath, $content.ReplaceLineEndings("`n"), [Text.UTF8Encoding]::new($false))
    Write-Host 'Par RSA 2048 preparado en la configuración privada. No se muestran sus claves.'
} finally { $rsa.Dispose() }
