#!/bin/bash
# Lo ejecuta el entrypoint de postgres solo al inicializar el volumen. Pasa las credenciales del entorno
# (definidas en .env) a init.sql como variables de psql, de modo que ninguna contraseña vive en un archivo del proyecto.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v orgaccess_user="$ORGACCESS_DB_USER"       -v orgaccess_password="$ORGACCESS_DB_PASSWORD" \
  -v production_user="$PRODUCTION_DB_USER"     -v production_password="$PRODUCTION_DB_PASSWORD" \
  -v quality_user="$QUALITY_DB_USER"           -v quality_password="$QUALITY_DB_PASSWORD" \
  -v notification_user="$NOTIFICATION_DB_USER" -v notification_password="$NOTIFICATION_DB_PASSWORD" \
  -v analytics_user="$ANALYTICS_DB_USER"       -v analytics_password="$ANALYTICS_DB_PASSWORD" \
  -f /opt/trazatex/init.sql
