# Notification — dominio

Fuentes: US04 y US06 (correo de la invitación), US07 y US08 (solicitudes de administrador), US21 y US22 (alertas de transferencia), US35 (alerta por posible falla derivada); `communication.md` (catálogo), `overview.md`, `data.md`, ADR-004, ADR-007, ADR-010. **Estado: implementado** (fase 07 y ronda final). No hay una figura del informe para este contexto: lo que sigue describe el servicio tal como está. Lo que el código resolvió sin que el equipo lo hubiera confirmado está en «Decisiones (cerradas)».

Notification **no decide nada de negocio**: solo reacciona a hechos que otros servicios publican y los convierte en (a) una notificación dentro de la aplicación y/o (b) un correo.

## Agregados y datos

- **Notification**: `id`, `recipientUserId`, `companyId`, `type` (`NotificationType`), `data` (JSONB con los ids que el frontend necesita: `transferId`, `batchIds`, `failureId`, `requestId`…), `message` (texto en español), `read`, `readAt`, `createdAt`, `sourceEventId`. Única por `(sourceEventId, recipientUserId)`: un evento reentregado, o un fallo a mitad de camino, nunca duplica una notificación.
- **RecipientDirectory** (proyección de `UserAccountUpdated`): `userId`, `email`, `fullName`, `role`, `companyId`, `status`. Es la única fuente de destinatarios: **no se consulta a organization-access por REST** (ADR-008).
- **DeferredEmail** (ADR-007): `id`, `sourceEventId`, `toEmail`, `subject`, `body`, `status` (`DeliveryStatus`: `PENDING`, `SENT`, `FAILED`, `EXPIRED` si la invitación venció, `UNDELIVERABLE` para dominios reservados o bloqueados), `attempts`, `nextAttemptAt`, `lastError`, `createdAt`, `sentAt`. Única por `(sourceEventId, toEmail)`.
- Estructuras técnicas: `processed_events` e `idempotency_keys` (ADR-010: `PATCH` es una escritura) sí; `outbox` **no**, porque Notification no publica eventos.

`NotificationType` (implementado, código estable que viaja en `type`; la interfaz decide el texto que muestra): `ADMIN_REQUEST_SUBMITTED`, `ADMIN_REQUEST_RESOLVED`, `TRANSFER_RECEIVED`, `TRANSFER_ACCEPTED`, `TRANSFER_REJECTED`, `POTENTIAL_DERIVED_FAILURE`. La tabla de `data` por tipo está en `api.md`.

## Reglas por evento

| Evento | Tópico | Destinatarios | Canal | Historia |
|---|---|---|---|---|
| `UserAccountUpdated` | `orgaccess.user` (compactado) | — | Solo actualiza el directorio (último estado por `userId`) | — |
| `InvitationCreated` | `orgaccess.invitation` | El correo de la invitación (`payload.email`); **no es un usuario**, no hay notificación en la aplicación | Solo correo, con `acceptUrl` y `expiresAt` | US04, US06 |
| `AdminRequestSubmitted` | `orgaccess.invitation` | `LICENSE_OWNER` `ACTIVE` | Aplicación + correo | US07 |
| `AdminRequestResolved` | `orgaccess.invitation` | `requesterUserId` | Aplicación + correo | US08 |
| `TransferInitiated` | `production.transfer` | `COMPANY_ADMIN` `ACTIVE` de `toCompanyId` | Aplicación + correo | US21 |
| `TransferRejected` | `production.transfer` | `COMPANY_ADMIN` `ACTIVE` de `fromCompanyId` (los lotes vuelven a su empresa) | Aplicación + correo | US22 |
| `TransferAccepted` | `production.transfer` | `COMPANY_ADMIN` `ACTIVE` de `fromCompanyId` | Aplicación + correo (aviso informativo; ver decisión 1) | US22 |
| `PossibleDerivedFailureDetected` | `traceability.impact` | `COMPANY_ADMIN` `ACTIVE` de cada `companyId` de `affected[]` | Aplicación + correo | US35 |

**US35 (implementado).** Se genera **una alerta por falla y por empresa afectada** (no una por lote), con todos los lotes de esa empresa en `data.batchIds`, a partir de `PossibleDerivedFailureDetected`. Es independiente de lo que Quality haga con cada lote: la alerta sale también cuando Quality registra el aviso como `NO_ACTION` (lote en fase histórica o ya retenido). Notification **ya no consume `quality.control`**: derivar US35 de `QualityStatusChanged` solo avisaba de los lotes que Quality retenía. Los avisos sin `companyId` se omiten.

Reglas comunes: se omiten los usuarios `INACTIVE`; el destinatario se resuelve **al crear la notificación** —el correo se crea en la **misma transacción** que la notificación y que la marca de evento procesado: o nace todo o no nace nada—, y no se reevalúa después (un usuario reactivado o incorporado más tarde no recibe avisos antiguos); una empresa `INACTIVE` también recibe (la inactividad bloquea escrituras, no lecturas).

## Correo (ADR-007)

- `EmailSenderPort` (aplicación) → `SendGridEmailSender` (`infrastructure/external`), con timeout (conexión 2 s, lectura 8 s) y Circuit Breaker de Resilience4j. `EmailDeliveryService` (aplicación) decide qué hacer con cada resultado; `MailDeliveryJob` solo lo programa (`@Scheduled`, 30 s). Ningún controlador, listener ni job toca JDBC.
- **Nunca se pierde un correo**: se guarda primero en `deferred_email`, en la misma transacción que la notificación, y el job lo envía (`PENDING` → `SENT`). El job **no recorre las notificaciones**: toma solo los correos pendientes y vencidos (hasta 10, arrendados 5 minutos con `FOR UPDATE SKIP LOCKED`, así dos réplicas no envían el mismo).
- **Retry acotado.** Un fallo transitorio (429, 408, 5xx, tiempo agotado, red) suma un intento y espera más (30 s, 60 s, 120 s… hasta 1 h). Al llegar a `trazatex.mail.max-attempts` (8 por omisión) pasa a `FAILED` con `last_error = REINTENTOS_AGOTADOS: <motivo>`.
- **4xx permanente** (clave inválida, remitente sin verificar, petición rechazada) → `FAILED` al primer intento, sin reintentar, y **no cuenta para el circuito** (una clave inválida no es una caída del proveedor, y no debe frenar los demás correos).
- **Circuito abierto**: no se llama a SendGrid y los correos vuelven a la cola **sin gastar intentos**.
- **Sin `SENDGRID_API_KEY` o `SENDGRID_FROM_EMAIL`** (el caso local por omisión): el servicio arranca con un único WARN, los correos quedan `PENDING` con su cuerpo y no se envía nada. El enlace de una invitación sigue obteniéndose de Kafka UI, como hoy en la colección Bruno.
- Dominios reservados (RFC 2606) y los de `TRAZATEX_MAIL_BLOCKED_DOMAINS` → `UNDELIVERABLE` sin enviar.
- **Seguridad.** El cuerpo de `InvitationCreated` lleva el token de invitación (un secreto de un solo uso). El cuerpo se conserva **solo mientras el correo está `PENDING`**; al pasar a cualquier estado final (`SENT`, `FAILED`, `EXPIRED`, `UNDELIVERABLE`) se vacía (`body = ''`), y un `CHECK` de la tabla (`status = 'PENDING' OR body = ''`) impide guardarlo de otro modo. El cuerpo no se registra en logs ni en `last_error` (que solo lleva un código corto como `SENDGRID_HTTP_503`), ni aparece en `notification`, `processed_events` ni `progress.md`.
- Remitente verificado en SendGrid: `SENDGRID_FROM_EMAIL`.

## Mensajería

Notification consume `orgaccess.user`, `orgaccess.invitation`, `production.transfer` y `traceability.impact`. Son tópicos de otros servicios: declara solo sus `.DLT` y **nunca** los crea. Cada tópico tiene **su propio grupo de consumidores** (`notification.orgaccess-user`, `notification.orgaccess-invitation`, `notification.production-transfer`, `notification.traceability-impact`) y su propio contenedor con `autoStartup = "false"`: el `TopicWatcher` de `shared-messaging` (programado por `TopicWatcherJob`, cada 15 s) arranca cada contenedor cuando su tópico existe. Así un mensaje en reintento en un tópico no frena a los demás y un tópico ausente no llena el log de `UNKNOWN_TOPIC_OR_PARTITION`. Un mensaje ilegible o que agota los reintentos (5, con espera creciente) va a `<tópico>.DLT`. Los grupos son nuevos y leen desde el principio; `processed_events` descarta lo ya aplicado. (El grupo anterior, `notification.events`, queda sin uso.)

## API (por el Gateway, `/api/notifications/**`)

`GET /api/notifications?unread=&type=&page=&size=` (página `{items, page, size, totalElements, totalPages}`, más recientes primero), `PATCH /api/notifications/{id}/read`, `GET /api/notifications/unread-count`. El contrato completo, los códigos de `type` y el contenido de `data` están en `api.md`. Cada usuario ve **solo las suyas**: la de otro usuario responde `404` con el mismo cuerpo que una inexistente. Cualquier usuario autenticado puede consultar las suyas (las del `OPERATOR` serán casi siempre cero). Identidad: cabeceras `X-User-Id`, `X-Company-Id`, `X-User-Role` del Gateway; `/internal/**` no existe aquí.

## Decisiones (cerradas: «implementado así»)

Las seis preguntas del borrador se cierran con lo que el servicio hace hoy (2026-11-04, a pedido del usuario, que las contrastará con el informe). El motivo de cada una queda aquí para poder revisarla.

1. **`TransferAccepted`: se avisa a los administradores de la empresa de origen, con aplicación y correo.** La propuesta decía «sin correo»; se implementó con correo. *Motivo:* todos los avisos a una empresa siguen un solo camino (`notice`: notificación dentro de la aplicación y correo en la misma transacción), y quien inició la transferencia suele no estar dentro de la aplicación cuando el destinatario la resuelve; un canal distinto solo para este caso añadía una excepción sin ganar nada.
2. **Solicitud de administrador (US07): el `LICENSE_OWNER` recibe aviso.** organization-access publica `AdminRequestSubmitted` y Notification crea el aviso (aplicación + correo). *Motivo:* sin él el dueño de la licencia solo se enteraba al listar las solicitudes.
3. **Canal por evento:** la invitación va solo por correo (el destinatario aún no es un usuario); todo lo demás, aplicación + correo. *Motivo:* la propuesta tal cual; el correo sale por una cola con reintento acotado y no satura al proveedor.
4. **Directorio incompleto: no se reintenta.** Si el directorio aún no conoce a ningún administrador activo de la empresa, la notificación dentro de la aplicación **sí se crea** y simplemente no nace ningún correo; un directorio que llega después no escribe hacia atrás (el destinatario se resuelve al crear la notificación). La propuesta era reintentar con espera creciente y mandar a la `.DLT`. *Motivo:* el orden entre tópicos no está garantizado, y reintentar detendría el tópico (un mensaje bloqueado frena a los siguientes de su partición) para proteger solo el correo; la notificación, que es lo que no debe perderse, ya existe. Probado en `NotificationConsumptionIT.withAnIncompleteDirectoryTheNotificationIsKeptNoEmailIsMadeAndNothingIsSentLater`.
5. **Contenido:** `message` genérico en español y `data` con los ids; el frontend los resuelve. Notification no consume `CompanyRegistered` ni `BatchRegistered` solo para eso. US35: una notificación por falla y por empresa afectada, con la lista `batchIds` de esa empresa. *Motivo:* mantiene a Notification sin proyecciones ajenas (ADR-008).
6. **Retención:** no se purgan las notificaciones ni `processed_events`; queda como deuda anotada en `progress.md`. *Motivo:* no hay una regla de negocio que fije el plazo.
