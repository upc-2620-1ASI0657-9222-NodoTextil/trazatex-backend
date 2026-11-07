#!/bin/bash
set -euo pipefail
for service in ORGACCESS PRODUCTION QUALITY NOTIFICATION ANALYTICS; do
  user_key="${service}_DB_USER"
  password_key="${service}_DB_PASSWORD"
  database="$(printf '%s' "$service" | tr '[:upper:]' '[:lower:]')_db"
  psql -v ON_ERROR_STOP=1 --host="${PGHOST:-/var/run/postgresql}" --username "$POSTGRES_USER" --dbname postgres \
    -v username="${!user_key}" -v password="${!password_key}" -v database="$database" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'username', :'password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'username')
\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'database', :'username')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database')
\gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'database')
\gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'database', :'username')
\gexec
SQL
done
if [ -f /init/panels.sh ]; then bash /init/panels.sh; fi
