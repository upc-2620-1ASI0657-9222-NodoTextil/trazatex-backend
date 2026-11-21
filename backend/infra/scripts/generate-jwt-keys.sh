#!/usr/bin/env bash
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

set_var() {
  local name="$1" value="$2" tmp
  tmp="$work/env.new"
  VALUE="$value" awk -v name="$name" '
    BEGIN { value = ENVIRON["VALUE"] }
    index($0, name "=") == 1 { print name "=" value; found = 1; next }
    { print }
    END { if (!found) print name "=" value }
  ' "$env_file" > "$tmp"
  cat "$tmp" > "$env_file"
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
