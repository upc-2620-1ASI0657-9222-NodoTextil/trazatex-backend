#!/bin/bash
set -euo pipefail
mkdir -p /config
chmod 755 /config
printf '%s' "$PORTAINER_PASSWORD" > /config/portainer-password
chmod 600 /config/portainer-password
printf '{"Servers":{' > /config/servers.json
: > /config/pgpass
index=0
for service in ORGACCESS PRODUCTION QUALITY NOTIFICATION ANALYTICS; do
  index=$((index + 1))
  user_key="${service}_DB_USER"
  password_key="${service}_DB_PASSWORD"
  username="${!user_key}"
  password="${!password_key}"
  [[ "$username" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]] || { echo "Nombre de usuario PostgreSQL no válido."; exit 1; }
  database="$(printf '%s' "$service" | tr '[:upper:]' '[:lower:]')_db"
  if [ "$index" -gt 1 ]; then printf ',' >> /config/servers.json; fi
  printf '"%s":{"Name":"%s","Group":"TrazaTex","Host":"postgres","Port":5432,"MaintenanceDB":"%s","Username":"%s","SSLMode":"prefer","PassFile":".pgpass"}' "$index" "$service" "$database" "$username" >> /config/servers.json
  escaped="${password//\\/\\\\}"
  escaped="${escaped//:/\\:}"
  printf 'postgres:5432:%s:%s:%s\n' "$database" "$username" "$escaped" >> /config/pgpass
done
printf '}}\n' >> /config/servers.json
chmod 644 /config/servers.json
chown 5050:5050 /config/pgpass
chmod 600 /config/pgpass


# pgAdmin resuelve PassFile dentro del almacenamiento privado del usuario.
storage_user="${PGADMIN_EMAIL//@/_}"
[[ "$storage_user" =~ ^[a-zA-Z0-9_.-]+$ && "$storage_user" != "." && "$storage_user" != ".." ]] || { echo "Directorio de pgAdmin no válido."; exit 1; }
storage_dir="/pgadmin-data/storage/$storage_user"
mkdir -p "$storage_dir"
chown 5050:5050 /pgadmin-data/storage "$storage_dir"
chmod 700 /pgadmin-data/storage
chmod 700 "$storage_dir"
cp /config/pgpass "$storage_dir/.pgpass"
chown 5050:5050 "$storage_dir/.pgpass"
chmod 600 "$storage_dir/.pgpass"
