#!/usr/bin/env bash
#
# Genera el par RSA 2048 con el que organization-access firma los JWT (RS256, ADR-005) y lo escribe en el .env.
#
# Escribe tres variables (las claves se guardan como el PEM completo codificado en Base64, en una sola línea):
#   JWT_PRIVATE_KEY  PEM PKCS#8 de la clave privada
#   JWT_PUBLIC_KEY   PEM X.509 (SubjectPublicKeyInfo) de la clave pública
#   JWT_KEY_ID       identificador (kid) del par: va en el encabezado de cada JWT y en /.well-known/jwks.json
#
# Uso:  infra/scripts/generate-jwt-keys.sh [--force] [--env-file RUTA]
#   --force          reemplaza un par ya existente. Invalida todos los JWT emitidos hasta ahora: los usuarios
#                    tendrán que iniciar sesión de nuevo. Sin --force el script se niega a pisar claves existentes.
#   --env-file RUTA  archivo a modificar (por defecto, el .env de la raíz del proyecto; debe existir: cp .env.example .env)
#
# Las claves no se imprimen ni quedan en archivos temporales: se generan en un directorio privado que se borra al terminar.
set -euo pipefail

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
env_file="$script_dir/../../.env"
force=0

while [ $# -gt 0 ]; do
  case "$1" in
    --force) force=1; shift ;;
    --env-file) [ $# -ge 2 ] || die "--env-file necesita una ruta"; env_file="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "argumento desconocido: $1" ;;
  esac
done

command -v openssl >/dev/null 2>&1 || die "no se encontró openssl en el PATH"
[ -f "$env_file" ] || die "no existe $env_file. Cópialo desde el ejemplo: cp .env.example .env"

# ¿Alguna de las tres variables ya tiene valor?
has_value() {
  awk -v name="$1" -F= '$1 == name { sub(/^[^=]*=/, ""); if ($0 != "") found = 1 } END { exit found ? 0 : 1 }' "$env_file"
}
if [ "$force" -eq 0 ]; then
  for name in JWT_PRIVATE_KEY JWT_PUBLIC_KEY JWT_KEY_ID; do
    if has_value "$name"; then
      die "$env_file ya tiene $name. Usa --force para reemplazar el par (invalida los JWT emitidos: los usuarios deberán iniciar sesión de nuevo)."
    fi
  done
fi

old_umask="$(umask)"
umask 077
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
umask "$old_umask"

# La clave privada en PKCS#8 (OpenSSL 3 ya lo hace; LibreSSL de macOS emite PKCS#1 y hay que convertirla)
openssl genrsa -out "$work/rsa.pem" 2048 >/dev/null 2>&1 || die "openssl no pudo generar la clave RSA"
openssl pkcs8 -topk8 -nocrypt -in "$work/rsa.pem" -out "$work/private.pem" >/dev/null 2>&1 || die "no se pudo convertir la clave a PKCS#8"
openssl rsa -in "$work/private.pem" -pubout -out "$work/public.pem" >/dev/null 2>&1 || die "no se pudo extraer la clave pública"

one_line_base64() {
  base64 < "$1" | tr -d '\n\r'
}

private_b64="$(one_line_base64 "$work/private.pem")"
public_b64="$(one_line_base64 "$work/public.pem")"
key_id="trazatex-$(date -u +%Y%m%d)-$(openssl rand -hex 4)"
fingerprint="$(openssl rsa -pubin -in "$work/public.pem" -outform DER 2>/dev/null | openssl dgst -sha256 | awk '{print substr($NF, 1, 16)}')"

# Reemplaza la línea NOMBRE=... del .env (o la agrega al final). El valor viaja por el entorno para no tener que escaparlo.
set_var() {
  local name="$1" value="$2" tmp
  tmp="$work/env.new"
  VALUE="$value" awk -v name="$name" '
    BEGIN { value = ENVIRON["VALUE"] }
    index($0, name "=") == 1 { print name "=" value; found = 1; next }
    { print }
    END { if (!found) print name "=" value }
  ' "$env_file" > "$tmp"
  cat "$tmp" > "$env_file"   # conserva propietario y permisos del archivo
}

set_var JWT_PRIVATE_KEY "$private_b64"
set_var JWT_PUBLIC_KEY "$public_b64"
set_var JWT_KEY_ID "$key_id"
chmod 600 "$env_file"

echo "Par RSA 2048 escrito en $env_file"
echo "  JWT_KEY_ID = $key_id"
echo "  huella SHA-256 de la clave pública = $fingerprint"
[ "$force" -eq 1 ] && echo "  Se reemplazó el par anterior: los JWT emitidos antes ya no son válidos."
exit 0
