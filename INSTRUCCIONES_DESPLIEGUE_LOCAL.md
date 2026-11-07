# Ejecutar TrazaTex en local

## Lo que necesitas

- Docker Desktop abierto, con contenedores Linux y una versión reciente de Docker Compose compatible con `!override` y `!reset`.
- PowerShell 7 para los scripts `start.ps1`, `status.ps1` y `stop.ps1`.
- WSL 2 habilitado en Windows cuando Docker Desktop lo solicite.
- Internet para descargar las imágenes y dependencias del primer arranque.
- No necesitas instalar Java ni Maven para ejecutar los contenedores. El script de inicio prepara las claves JWT cuando todavía no existen.

## Para abrir TrazaTex

1. Abre la carpeta completa de TrazaTex.
2. Abre Docker Desktop y espera a que el motor esté listo.
3. Abre una terminal en la carpeta principal, donde está start.ps1.
4. Conserva el `.env` privado actual. En otra computadora, prepara uno a partir de `.env.example` y completa las credenciales, la configuración del propietario y los límites de licencia.
5. Ejecuta `pwsh -File .\start.ps1`. Para utilizar Compose directamente por primera vez, prepara antes las claves con `pwsh -File .\backend\infra\scripts\generate-jwt-keys.ps1` y luego ejecuta `docker compose up -d --build`. Con imágenes existentes puedes usar `docker compose up -d --no-build`.
6. Espera a que `docker compose ps` muestre los servicios saludables y entra a las aplicaciones de abajo.

Configura `CLOUDINARY_URL` para las evidencias y `SENDGRID_API_KEY` junto con `SENDGRID_FROM_EMAIL` para los correos. El remitente debe estar verificado en SendGrid; su nombre visible es TrazaTex. `start.ps1` genera el par RSA si las tres variables JWT están vacías; conserva el par existente y rechaza configuraciones parciales. Compose por sí solo necesita esas variables ya preparadas. Nunca publiques las configuraciones privadas.

## Aplicaciones y paneles

- Angular: http://127.0.0.1:14200/login
- Landing: http://127.0.0.1:15173
- Portainer: https://127.0.0.1:19443
- Spring Boot Admin: http://127.0.0.1:18095
- pgAdmin: http://127.0.0.1:15050
- Neo4j Browser: http://127.0.0.1:17474/browser/
- Kafka UI: http://127.0.0.1:18090

### Si cambias el puerto de la aplicación web

El puerto por defecto de la web es `14200` y el valor por defecto de `TRAZATEX_FRONTEND_URL` es `http://127.0.0.1:14200`. Esa URL se usa en tres sitios: el CORS del Gateway, los enlaces de invitación de Organization & Access y el enlace «Ir al login» de la landing (se fija al construir su imagen). Si publicas la web en otro puerto (por ejemplo `24200`), define `TRAZATEX_FRONTEND_URL=http://127.0.0.1:24200` en `.env` y reconstruye la landing (`docker compose up -d --build landing`). Sin esa variable, el navegador recibe `403` en el login por CORS. Sin definirla no cambia nada.

## Primer acceso (Dueño de Licencia)

El sistema inicia vacío: no incluye empresas, cuentas ni datos de prueba. Al arrancar, Organization & Access ejecuta el bootstrap del Dueño de Licencia (`BootstrapConfig` y `OwnerBootstrapService`):

- Exige `TRAZATEX_OWNER_EMAIL`, `TRAZATEX_OWNER_NAME`, `TRAZATEX_LICENSE_MAX_COMPANIES` y `TRAZATEX_LICENSE_MAX_USERS_PER_COMPANY`, sin valores por defecto. Si falta alguna o es inválida, el servicio no arranca.
- Si no hay licencia, la crea con esos límites y emite una invitación de Dueño de Licencia. Nunca crea contraseñas: el Dueño crea su cuenta al aceptar la invitación.
- Si ya hay licencia pero no hay Dueño activo ni invitación vigente, emite una nueva invitación. En cualquier otro caso no hace nada, y los límites de una licencia existente no cambian.
- El enlace de la invitación es `TRAZATEX_FRONTEND_URL` + `/invitations/<token>` (por defecto, `http://127.0.0.1:14200/invitations/<token>`).

**Cómo recibe el Dueño el enlace.** La invitación se publica como evento `InvitationCreated` y Notification envía el correo mediante SendGrid. Sin `SENDGRID_API_KEY` el correo no sale: queda en la tabla `deferred_email` y se reintenta cuando haya credenciales. Además, `.env.example` define `TRAZATEX_MAIL_BLOCKED_DOMAINS=upc.pe`, que impide enviar correo externo a ese dominio.

Para activar al Dueño sin correo, lee el enlace en el `outbox` de Organization & Access (el payload del evento incluye `acceptUrl`). Sustituye `<POSTGRES_USER>` por el valor de tu `.env`:

```bash
docker compose exec -T postgres psql -U <POSTGRES_USER> -d orgaccess_db -tA \
  -c "SELECT payload::text FROM outbox WHERE topic='orgaccess.invitation' ORDER BY created_at DESC LIMIT 1"
```

Abre en el navegador el enlace `http://127.0.0.1:14200/invitations/<token>` y define tu contraseña. Si usaste otro proyecto de Compose, añade `-p <proyecto>` después de `docker compose`. Cada invitación tiene fecha de vencimiento (`expires_at`); si venció, reinicia Organization & Access para que el bootstrap emita una nueva.

## Credenciales de paneles y bases

Los paneles y las cinco cuentas independientes de PostgreSQL utilizan el `.env` privado. Cambiar una variable no cambia automáticamente una contraseña ya almacenada.
En Portainer acepta la advertencia del certificado únicamente para esa dirección local.
Neo4j utiliza `bolt://127.0.0.1:17687`, base `neo4j`.

## Comandos para usarlo

Ver estado: `pwsh -File .\status.ps1`.
Actualizar las imágenes: `pwsh -File .\start.ps1`.
Volver a iniciar sin recompilar: `pwsh -File .\start.ps1 -SkipBuild`.
Ver logs: `docker compose logs --tail 100 quality notification analytics gateway`.
Detener sin perder datos: `docker compose down` o `pwsh -File .\stop.ps1`. No uses `down -v`.

Compose crea las bases y aplica las migraciones en una instalación nueva. Los datos se conservan al detener los servicios. Al compartir el proyecto, excluye `node_modules`, compilaciones, cachés y resultados de pruebas; entrega las configuraciones privadas por un medio separado y seguro.
