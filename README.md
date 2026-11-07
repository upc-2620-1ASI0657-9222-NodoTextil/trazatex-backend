# TrazaTex

Plataforma de trazabilidad para la cadena textil. Permite a las empresas registrar la producción, seguir el origen y las transformaciones de cada lote, controlar su calidad, recibir notificaciones y consultar indicadores.

## Arquitectura

Siete servicios Java 21 / Spring Boot (Maven multimódulo en `backend/`) comunicados por **Kafka**:

| Servicio | Función | Almacenamiento |
|---|---|---|
| `api-gateway` | Punto de entrada del backend (no se publica en el host; solo lo alcanza el Nginx de `web`) | — |
| Organization & Access | Licencia, empresas, usuarios, invitaciones y JWT | PostgreSQL |
| Production | Producción y transformaciones | PostgreSQL |
| Traceability | Genealogía de lotes | Neo4j |
| Quality | Controles de calidad y reportes PDF | PostgreSQL |
| Notification | Bandeja y correos (SendGrid) | PostgreSQL |
| Analytics | Indicadores por empresa | PostgreSQL |

Además: **frontend** Angular 21 (`frontend/`) y **landing** con Vite, GSAP y Three.js (`landing/`).

## Requisitos

- Docker con Compose reciente (el `compose.yaml` usa `!override` y `!reset`).
- `bash` y `openssl` para generar las claves JWT (en Windows, `backend/infra/scripts/generate-jwt-keys.ps1`).
- Para las pruebas del backend: Java 21 y Docker encendido (Testcontainers).

## Arranque

```bash
cp .env.example .env          # completa contraseñas, TRAZATEX_OWNER_EMAIL/NAME y límites de licencia
bash backend/infra/scripts/generate-jwt-keys.sh   # escribe JWT_PRIVATE_KEY, JWT_PUBLIC_KEY y JWT_KEY_ID en .env
docker compose up -d --build
```

En Windows también puedes usar `start.ps1`, `status.ps1` y `stop.ps1`. Detalles en [INSTRUCCIONES_DESPLIEGUE_LOCAL.md](INSTRUCCIONES_DESPLIEGUE_LOCAL.md).

## URLs (solo en 127.0.0.1)

| Aplicación | URL |
|---|---|
| Angular | http://127.0.0.1:14200 |
| Landing | http://127.0.0.1:15173 |
| Portainer | https://127.0.0.1:19443 |
| Spring Boot Admin | http://127.0.0.1:18095 |
| pgAdmin | http://127.0.0.1:15050 |
| Neo4j Browser / Bolt | http://127.0.0.1:17474 / `bolt://127.0.0.1:17687` |
| Kafka UI | http://127.0.0.1:18090 |

PostgreSQL y Kafka no se publican en el host.

## Aviso sobre volúmenes

Por defecto el compose usa volúmenes de nombre fijo (`trazatex-integration_*`; los de secretos y configuración de Kafka son `trazatex-kafka-secrets` y `trazatex-kafka-config`). Dos copias del proyecto en la misma máquina comparten los datos. Para aislarlas, define `TRAZATEX_CONTAINER_PREFIX` y las variables `TRAZATEX_*_VOLUME` (ver `.env.example`). No uses `docker compose down -v`: borra los datos.

## Pruebas

```bash
cd backend && ./mvnw -B -ntp verify
```

Las imágenes se compilan con `-DskipTests`, así que esta verificación no sustituye a la construcción. Las pruebas de navegador (Playwright) están en `frontend/e2e`.

## Estructura

```
backend/    servicios, shared-kernel/messaging/testing, docs, infra (scripts, plataforma Docker)
frontend/   aplicación Angular y pruebas e2e
landing/    sitio web de presentación
compose.yaml, .env.example, start.ps1, status.ps1, stop.ps1
```

> El sistema inicia vacío; no incluye datos de prueba.
