# Persistencia por servicio

Las tablas de negocio salen de `docs/domain/<contexto>.md` (que parte de las Figuras 39–44 del informe y las completa con lo que exigen los RF). Aquí van las **estructuras técnicas** que exige la arquitectura de microservicios. Migraciones con Flyway en cada servicio.

## Estructuras técnicas comunes (servicios con PostgreSQL)

```sql
CREATE TABLE outbox (
  id            UUID PRIMARY KEY,          -- = eventId
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id  VARCHAR(80) NOT NULL,
  event_type    VARCHAR(80) NOT NULL,
  topic         VARCHAR(120) NOT NULL,
  payload       JSONB NOT NULL,             -- sobre completo
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED (no hay estado FAILED)
  attempts      INT NOT NULL DEFAULT 0,                  -- intentos de publicación fallidos
  last_error    TEXT,                                    -- último error (clase: mensaje, hasta 1000 caracteres)
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),  -- la aplicación escribe el valor (OutboxEventRecorder): now() es la hora de inicio de la transacción y empataría eventos de una misma transacción
  published_at  TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox(status, created_at);
```

Outbox (`OutboxRelay`, ver `communication.md`): cada fallo de publicación suma 1 a `attempts`, guarda `last_error` y registra un WARN con `eventId`, tópico y error; la fila sigue `PENDING` y se reintenta en la pasada siguiente. Publicar con éxito no borra `attempts` ni `last_error` (quedan como historial). Lectura: `ORDER BY created_at ... FOR UPDATE SKIP LOCKED`. Un mensaje que nunca se puede publicar solo bloquea a su agregado; se detecta con `attempts` alto en `ix_outbox_pending`.

```sql

CREATE TABLE processed_events (
  event_id     UUID PRIMARY KEY,
  event_type   VARCHAR(80) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (            -- RNF-11
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL,
  company_id   UUID,
  action       VARCHAR(80) NOT NULL,
  entity_type  VARCHAR(80) NOT NULL,
  entity_id    VARCHAR(80) NOT NULL,
  changes      JSONB,
  occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
  idempotency_key VARCHAR(64) PRIMARY KEY,
  user_id        VARCHAR(64) NOT NULL,
  request_hash   VARCHAR(64) NOT NULL,
  response_status INT NOT NULL,
  response_body  JSONB,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()   -- caduca a las 24 h
);
```

## Por servicio

| Servicio | Estructuras nuevas | Propósito |
|---|---|---|
| organization-access | `outbox`, `idempotency_keys`, `audit_log`, `validated_ruc_cache(ruc PK, business_name, taxpayer_status, domicile_condition, validated_at)` (solo RUC ACTIVO y HABIDO), `login_attempts(email PK, failed_attempts, locked_until, updated_at)` (RNF-06, por correo normalizado, exista o no la cuenta). Las claves JWT van por variables de entorno: no hay tabla `signing_keys` | ADR-004, ADR-010, ADR-012, ADR-005 |
| production | `outbox`, `processed_events`, `idempotency_keys`, `audit_log`, `company_projection(company_id PK, business_name (nulo si la empresa se conoció por un cambio de estado), status, updated_at)`, columnas `quality_hold BOOLEAN` y `version BIGINT` en el lote, `phase_command_log(batch_id, request_id, operation, result_phase, result_quality_hold, created_at; PK batch_id+request_id+operation)` (guarda también la retención resultante para que repetir el comando devuelva exactamente lo mismo); solo guarda los comandos aplicados (un rechazo no deja fila). `audit_log.user_id` admite nulo (V4): nulo = acción del sistema, p. ej. un `HOLD` sin `requestedBy` | Proyección de empresas, retención por calidad, bloqueo optimista, idempotencia del comando interno. Índices de búsqueda de lotes (RNF-03): `ix_batches_company_created (company_id, created_at DESC, id)` para el listado paginado y `ix_batches_company_traceability_prefix (company_id, traceability_id text_pattern_ops)` (V2) para `LIKE 'prefijo%'`: el UNIQUE por defecto no sirve para prefijos con la colación de la base. Además `evidence(id PK = public_id en Cloudinary, owner_type BATCH|TRANSFORMATION, owner_id, company_id, status PENDING|UPLOADED|FAILED, url, original_filename, content_type, size_bytes, uploaded_by, created_at, uploaded_at, attempts, next_attempt_at, last_error)` y `evidence_upload_queue(evidence_id PK/FK, content BYTEA)` (V5; US42/ADR-007: los bytes de lo que aún no se subió a Cloudinary viven en PostgreSQL, nunca en el disco del contenedor; la fila de la cola se borra al pasar a `UPLOADED` y se conserva en `FAILED`). `evidence` no tiene FK al lote o a la transformación (el dueño es polimórfico y una FK haría que insertarla tomara `FOR KEY SHARE` sobre el lote y bloqueara a una división simultánea); índice `ix_evidence_due (next_attempt_at) WHERE status = 'PENDING'` para el job. Además `machine_status_history(machine_id, from_status, to_status, changed_by, changed_at)` (US10: historial de estados de la maquinaria; la fila de registro tiene `from_status` nulo) |
| quality *(06c)* | `outbox`, `processed_events`, `idempotency_keys`, `audit_log` (`user_id` nulo = acción del sistema; `CONTROL_STARTED`, `CONTROL_CANCELLED`), `quality_control(id, batch_id, company_id, control_type, status, overall_result, inspector_id, start_date, end_date, internal_observations, cancel_reason (código de error de Production que canceló el inicio), attempts, next_attempt_at (espera hasta el próximo intento y, mientras alguien lo intenta, su arrendamiento; solo con valor en STARTING), last_error, finish_requested_at/by + finish_result (la finalización pedida: el resultado queda congelado hasta que Production confirme RELEASE; el control sigue `IN_PROGRESS`))` con un **índice único parcial por lote para los controles `STARTING`/`IN_PROGRESS`** (un solo control activo) y un índice parcial de los `STARTING` por `next_attempt_at` (el reclamo `FOR UPDATE SKIP LOCKED` del job), `quality_failure(id, control_id (el ÚLTIMO control que la originó o reabrió), batch_id, company_id, root_cause, affected_qty_kg (> 0), observations, status, created_at, discard_requested_at/by, attempts, next_attempt_at, last_error)` con un índice único parcial (**una sola falla `OPEN` por lote**), `impacted_batch(failure_id + batch_id PK, impact_type (deducido de commonAncestorBatchId, sin columna extra), lock_status, created_at, attempts, next_attempt_at, last_error)`, `quality_test(id, control_id, seq, criterion_name, expected_value, obtained_value, unit, test_result, remarks)`, `batch_projection(batch_id PK, company_id, phase, quality_status (nulo mientras el lote solo se conoce por su BatchPhaseChanged), quality_hold, ever_controlled, batch_version (-1: ningún BatchPhaseChanged aplicado), updated_at, traceability_id VARCHAR(13) (V4; nulo en las filas anteriores o que solo se conocen por un BatchPhaseChanged: el reporte PDF muestra entonces el UUID))`. **06d (V4)**: `evidence(id PK = public_id en Cloudinary, owner_type CONTROL\|FAILURE, owner_id, company_id, status PENDING\|UPLOADED\|FAILED, url, original_filename, content_type, size_bytes, uploaded_by, created_at, uploaded_at, attempts, next_attempt_at, last_error)` y `evidence_upload_queue(evidence_id PK/FK, content BYTEA)` (los mismos que Production, ADR-007; sin FK al dueño, que es polimórfico; la fila de la cola se borra al pasar a `UPLOADED` y se conserva en `FAILED`); auditoría `EVIDENCE_ATTACHED` y `REPORT_GENERATED`. **El reporte PDF no tiene tabla**: se genera en memoria. El estado `INICIANDO` del control es `STARTING` | Lectura local del lote y recuperación de comandos pendientes |
| notification | `processed_events`, `user_projection` (directorio), `notification` (con `type` código estable y `data` JSONB), `notification_read`, `deferred_email` | Destinatarios sin consultar a organization-access; correos diferidos con reintento acotado. `deferred_email.body` solo existe mientras el correo está `PENDING` (CHECK `status = 'PENDING' OR body = ''`) |
| analytics | `analytics_event` (todos los eventos; `company_id` y `business_at` extraídos, índice por empresa, tipo y fecha), `batch_birth`, `batch_owner` (con `batch_version`), `batch_quality`. La idempotencia es la clave `event_id` | CQRS; reconstruible desde Kafka ([procedimiento](../operations/analytics-rebuild.md)) |


**Borrado de evidencias (production V6/V7 y quality V5/V7).** `evidence.deleted_at` marca la evidencia borrada (la excluyen los listados). `purge_attempts INT` y `purge_retry_at TIMESTAMPTZ` (V7) aplazan una evidencia cuya imagen no se pudo borrar en Cloudinary: espera de 10 s que se duplica en cada fallo (tope 5 min); el job `purgeDeleted` salta las aplazadas y sigue con las demás. Ver ADR-007 · borrado de evidencias.

## Traceability (Neo4j)

Modelo de la Figura 40 del informe; detalle en `docs/domain/traceability.md`.

- Nodos: `:Batch`, `:Transformation`, `:Company`, más los técnicos `:ProcessedEvent {eventId}` y `:OutboxEvent` (con las mismas propiedades que la tabla `outbox`, incluidas `attempts` y `lastError`).
- Relaciones: `(:Company)-[:OWNS_BATCH]->(:Batch)`, `(:Batch)-[:INPUT_OF]->(:Transformation)-[:PRODUCED]->(:Batch)`, `(:Batch)-[:SPLIT_INTO]->(:Batch)`, `(:Batch)-[:TRANSFERRED_VIA {transferId, at}]->(:Company)`.
- Restricciones de unicidad: `Batch.batchId`, `Batch.traceabilityId`, `Transformation.transformationId`, `Company.companyId`, `ProcessedEvent.eventId`, `OutboxEvent.eventId` (las crea el servicio al arrancar con `IF NOT EXISTS`, antes de consumir; si los datos existentes las violan no arranca). Índices: `Batch.qrCode`, `Batch(companyId, traceabilityId)` (búsqueda por prefijo del Traceability ID dentro de una empresa) `OutboxEvent(status, createdAt)` (el relay) y `OutboxEvent(aggregateId, eventType)` (saber si una falla ya tiene su `PossibleDerivedFailureDetected`). Las consultas de lectura van con `USING INDEX SEEK` para que Neo4j nunca recorra todos los lotes (ver `progress.md`, 05c-2). La edición community no tiene restricciones de existencia.
- Propiedades que no son de la Figura 40: `Batch.batchVersion` (orden de `BatchPhaseChanged`), `Batch.qualityHold`, `provisional` en `Batch` y `Transformation`, `Transformation.companyId`, `Transformation.machineName` y `machineType` (en lugar de un id de máquina), `Company.businessName` (de `CompanyRegistered`), `OutboxEvent.claimedAt`. Los datos de identidad de un lote se completan solo si faltan y nunca se sobrescriben. Fechas como `datetime`; cantidades en kg como texto decimal (Neo4j no tiene decimales); composición y `Company.privacy` como JSON.
- Consultas de genealogía acotadas a 10 niveles (RNF-04: p95 ≤ 5 s con 500 nodos y 100 consultas simultáneas).
