# Organization & Access — dominio

Fuentes: RF-001…RF-018, RF-061, RF-062, RNF-06; US01–US11, US41; Figura 29 (canvas) y Figura 43 (base de datos); ADR-005, ADR-012, ADR-013.
Nombres de enums: `glossary.md`.

## Agregados y datos

**License** (una sola, creada por el bootstrap)
- `maxCompanies`, `maxUsersPerCompany`, `createdAt`.
- Vista de uso (US01): límites, empresas usadas / máximo, y por empresa: usuarios ocupando cupo / máximo.

**Company**
- `id`, `ruc` (11 dígitos, único), `businessName`, `activities` (una o más `TextileActivity`), `status`, `createdAt`.

**User**
- `id`, `companyId` (nulo para el Dueño), `email` (único), `firstName`, `lastName`, `position` (cargo; opcional para el Dueño), `passwordHash` (BCrypt), `role`, `status`, `failedLoginAttempts`, `lockedUntil`, `createdAt`.
- Figura 43 modela los roles como tabla `roles (id, role_name, permissions)`: créala con los tres roles sembrados por migración; la autorización se hace por rol.

**Invitation**
- `id`, `token` (aleatorio e impredecible; va en el enlace del correo), `email`, `firstName`, `lastName`, `position`, `role`, `companyId`, `issuedBy` (userId), `status`, `createdAt`, `expiresAt = createdAt + 7 días`.

**AdminRequest**
- `id`, `companyId`, `requestedBy`, `fullName`, `email`, `position`, `status`, `createdAt`, `resolvedAt`, `resolvedBy`.

**PrivacySetting** (por empresa y campo configurable)
- `companyId`, `field` (`ConfigurableField`), `visibility` (`SHARED`/`PRIVATE`).

## Reglas

### Capacidad (RF-013)
- Cupo usado de una empresa = usuarios `ACTIVE` + invitaciones `PENDING` de esa empresa. Los `INACTIVE` no cuentan.
- Se valida antes de: invitar operario (US06), aprobar solicitud de administrador (US08), reactivar usuario (US09) y crear empresa (la invitación del primer administrador ocupa un cupo).
- Empresas: no se crea si `count(companies) >= maxCompanies` (US02 esc. 2). Las empresas inactivas siguen contando.

### Empresas (US02, US03, RF-061)
- Solo `LICENSE_OWNER`. Datos obligatorios: razón social, RUC, al menos una actividad, correo del primer administrador.
- RUC (ADR-012): **prefijo** (11 dígitos que empiezan con 10, 15, 16, 17 o 20, los únicos que acepta Chequea.pe; se comprueba primero y un RUC con otro prefijo ni siquiera llega a la API) → dígito verificador (módulo 11) → caché `validated_ruc_cache` → Chequea.pe. Solo se acepta `ACTIVO` + `HABIDO`. Si Chequea no responde y no hay caché: rechazar con mensaje de reintento (nunca aceptar sin validar). RUC duplicado: rechazar.
- Al crear: empresa `ACTIVE` + invitación `COMPANY_ADMIN` para el correo indicado (emitida por el Dueño) + configuración de privacidad con todos los campos en `PRIVATE`. Eventos: `CompanyRegistered`, `InvitationCreated`.
- Activar / inactivar: conserva todo; con la empresa `INACTIVE`, sus usuarios no inician nuevas operaciones productivas (lo aplica Production con su proyección). Evento `CompanyStatusChanged`.

### Invitaciones (US04, US06, US41, RF-004…RF-007)
- `PENDING` → `ACCEPTED` | `EXPIRED` | `CANCELLED`. Ninguna vuelve a `PENDING`.
- Expiran solas a los 7 días: job programado + verificación al leer o aceptar (una invitación vencida nunca se acepta aunque el job no haya corrido).
- Solo quien la emitió la cancela, y solo si está `PENDING` (US41).
- No se invita un correo que ya tiene cuenta ni uno con otra invitación `PENDING`.
- Invitar operario: `COMPANY_ADMIN` a su propia empresa; datos: nombres, apellidos, correo, cargo.
- Aceptar (público, por `token`): nombres, apellidos, contraseña y confirmación (deben coincidir); HIBP rechaza contraseñas filtradas (si HIBP no responde: se acepta y se audita, ADR-007). Crea el usuario `ACTIVE` con la empresa y rol de la invitación y pasa la invitación a `ACCEPTED`. Evento `UserAccountUpdated`.

### Solicitudes de administrador (US07, US08)
- `COMPANY_ADMIN` solicita con nombre, correo y cargo (todos obligatorios; si falta alguno, 400 indicando cuáles).
- `LICENSE_OWNER` aprueba (si hay cupo: crea invitación `COMPANY_ADMIN` emitida por el Dueño; sin cupo: 409 y la solicitud sigue `PENDING`) o rechaza. Evento `AdminRequestResolved`.

### Usuarios (US09, RF-012)
- `COMPANY_ADMIN` gestiona usuarios de su empresa. Inactivo: no inicia sesión ni opera; conserva historial.
- Reactivar exige cupo.
- Una empresa debe tener al menos un administrador activo (Figura 29): no se inactiva al último `COMPANY_ADMIN` activo.

### Autenticación (US05, RNF-06, ADR-005)
- Correo + contraseña; solo usuarios `ACTIVE`. Mensaje genérico ante credenciales inválidas.
- 3 intentos fallidos consecutivos → bloqueo de 15 minutos (423 `ACCOUNT_LOCKED`). Se cuentan **por correo normalizado, exista o no la cuenta** (tabla `login_attempts`), de modo que la respuesta es idéntica con correos inexistentes y no revela si una cuenta existe; un correo inexistente también verifica contra un hash BCrypt ficticio para que el tiempo tampoco lo revele. Un login correcto elimina el registro; un job borra los bloqueos vencidos y los contadores parciales sin actividad en 24 h.
- Respuesta: JWT RS256 con `sub` (userId), `role`, `companyId`.

### Privacidad B2B (US11, RF-015…RF-018)
- Campos configurables (`ConfigurableField`), `PRIVATE` por defecto: `SUPPLIER`, `ORIGIN`, `QUANTITY`, `COMPOSITION`, `RECEPTION_CHARACTERISTICS`, `MACHINERY`, `SHRINKAGE`, `WASTE`, `WASTE_REASON`, `FAILURE_CAUSE`, `QUALITY_TEST_VALUES`.
- Siempre compartidos (no configurables; intentar cambiarlos → 400): Traceability ID, empresa responsable, relaciones de genealogía, tipo de material, fase operativa, estado de calidad, fecha de registro, indicador de producto final.
- Siempre privados (no configurables): identidad del operario, observaciones internas de controles y fallas, datos comerciales del producto final.
- Solo para la empresa dueña (no configurable): `name`, el nombre libre del lote. Traceability lo devuelve únicamente en los lotes `OWN`; nunca sale en la vista `SHARED` de la genealogía de otra empresa, aunque esta comparta todos los campos configurables (decisión del usuario, 2026-11-03; ver `glossary.md` y `communication.md`).
- Solo `COMPANY_ADMIN` de la propia empresa. Evento `CompanyPrivacyUpdated` con el mapa completo.

### Bootstrap del Dueño (ADR-013)
- Al iniciar, si no existe licencia: crea la licencia con `TRAZATEX_LICENSE_MAX_COMPANIES` y `TRAZATEX_LICENSE_MAX_USERS_PER_COMPANY`, y una invitación `LICENSE_OWNER` para `TRAZATEX_OWNER_EMAIL` / `TRAZATEX_OWNER_NAME`. Nunca crea contraseñas.
- **Recuperación del Dueño** (ampliación del usuario, 2026-10-19): si la licencia ya existe pero no hay ningún usuario `LICENSE_OWNER` `ACTIVE` ni una invitación `LICENSE_OWNER` `PENDING` vigente (`expires_at > now()`; la invitación original puede haber vencido o cancelado), emite una invitación nueva para `TRAZATEX_OWNER_EMAIL` y publica `InvitationCreated`. Si ya hay un Dueño activo o una invitación vigente, no hace nada.
- Idempotente: reiniciar el servicio no duplica la licencia ni las invitaciones. Los límites de la licencia existente no se modifican aunque cambien las variables.
- Aceptación (US04, RF-006): el formulario pide solo nombres, apellidos y contraseña con su confirmación; **no se pide el cargo**. `User.position` sigue siendo opcional y se hereda de la invitación si esta lo trae.

## Auditoría (RNF-11)
Las operaciones de este contexto no están en la lista crítica de RNF-11, pero registra en `audit_log` los cambios de estado de empresas y usuarios y las decisiones sobre solicitudes.

## Decisiones abiertas (confirmar con el usuario antes de implementar)
- Duración del JWT (propuesta: 8 h, sin refresh token).
- Política de contraseña además de HIBP (el informe no fija longitud mínima; propuesta: mínimo 8 caracteres).
- Si un usuario de una empresa `INACTIVE` puede iniciar sesión para consultar (propuesta: sí, solo lectura).
