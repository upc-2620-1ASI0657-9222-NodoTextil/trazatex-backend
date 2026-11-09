#!/usr/bin/env bash
# Actualiza y levanta TrazaTex en la EC2. Uso: ./scripts/aws/deploy.sh   (desde cualquier carpeta)
set -euo pipefail
cd "$(dirname "$0")/../.."
compose() { docker compose -f compose.yaml -f compose.aws.yaml "$@"; }

[ -f .env ] || { echo "Falta .env (cópialo de .env.example y completa los valores de producción)"; exit 1; }

git pull --ff-only

# Una imagen a la vez: compilar los 7 servicios Java en paralelo no cabe en 8 GB.
for service in organization production traceability quality notification analytics gateway web landing; do
  echo "==> Construyendo $service"
  compose build "$service"
done

compose up -d --remove-orphans
docker image prune -f > /dev/null
compose ps
