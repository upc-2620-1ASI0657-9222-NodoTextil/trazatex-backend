# Comunicación entre servicios

## Regla (ADR-008)

1. Leer datos de otro contexto → **proyección local** actualizada por eventos. Nunca REST.
2. Comunicar un hecho de negocio → **evento en Kafka**.
3. Llamada síncrona entre microservicios → solo si (a) el usuario espera la respuesta, (b) la regla protegida pertenece al agregado del otro servicio y (c) decidir con datos desactualizados produce un estado inválido. Hoy solo cumple: **Quality → Production** (ADR-009).

## Sobre de eventos (Shared Kernel)

```json
{
  "eventId": "uuid",
  "type": "BatchRegistered",
  "aggregateId": "<id del agregado>",
  "version": 1,
  "occurredAt": "2026-10-19T14:00:00Z",
  "payload": { }
}
```

- Serialización JSON. Clave del mensaje en Kafka = `aggregateId` (orden por agregado).
- Un tópico por tipo de agregado. Topics con sufijo `.DLT` para errores.

## Catálogo de eventos

| Evento | Productor | Consumidores | Tópico (clave) | Payload mínimo |
|---|---|---|---|---|
| `InvitationCreated` | organization-access | notification | `orgaccess.invitation` (invitationId) | invitationId, email, fullName, role, companyId, acceptUrl, expiresAt |
| `AdminRequestSubmitted` *(agregado en V2)* | organization-access | notification | `orgaccess.invitation` (requestId) | requestId, companyId |
| `AdminRequestResolved` | organization-access | notification | `orgaccess.invitation` (requestId) | requestId, companyId, requesterUserId, decision |
| `UserAccountUpdated` | organization-access | notification, **api-gateway** | `orgaccess.user` (userId) — **compactado** | userId, email, fullName, role, companyId, status |
| `CompanyRegistered` | organization-access | production, **traceability** | `orgaccess.company` (companyId) | companyId, ruc, businessName, activities, status |
| `CompanyStatusChanged` | organization-access | production | `orgaccess.company` (companyId) | companyId, status |
| `CompanyPrivacyUpdated` | organization-access | traceability | `orgaccess.company` (companyId) | companyId, fields: {fieldName → SHARED \| PRIVATE} |
| `BatchRegistered` | production | traceability, quality, analytics | `production.batch` (batchId) | batchId, traceabilityId, qrCode, companyId, materialType, composition[], quantityKg, origin, supplierName, receptionCharacteristics, createdAt, **name** (opcional, solo para la dueña) |
| `BatchSplit` | production | traceability, **quality**, analytics | `production.batch` (parentBatchId) | parentBatchId, companyId, children[{batchId, traceabilityId, qrCode, **materialType**, quantityKg, **composition[]**, **origin**, **supplierName**, **receptionCharacteristics**, qualityStatus, **createdAt**, **name**}] |
| `BatchPhaseChanged` | production | traceability, quality, analytics | `production.batch` (batchId) | batchId, companyId, previousPhase, newPhase, qualityHold, reason, requestId, **batchVersion** |
| `TransformationCompleted` | production | traceability, **quality**, analytics | `production.transformation` (transformationId) | transformationId, type, companyId, machineId, **machineName**, **machineType**, inputs[{batchId, quantityKg}], outputs[{batchId, traceabilityId, qrCode, materialType, quantityKg, composition, finalProduct, **name**}], wasteKg, **wasteReason** (nulo si no hubo desperdicio), shrinkageKg, startedAt, completedAt |
| `TransferInitiated` / `TransferRejected` | production | notification, analytics | `production.transfer` (transferId) | transferId, batchIds, fromCompanyId, toCompanyId |
| `TransferAccepted` | production | traceability, quality, notification, analytics | `production.transfer` (transferId) | transferId, batchIds, fromCompanyId, toCompanyId, acceptedAt |
| `QualityControlCompleted` | quality | analytics, **traceability** | `quality.control` (controlId) | controlId, batchId, companyId, controlType, result, startedAt, finishedAt, **tests[{criterionName, expectedValue, obtainedValue, unit, testResult}]** *(agregado en V2)* |
| `QualityStatusChanged` | quality | production, traceability, analytics | `quality.control` (batchId) | batchId, companyId, previousStatus, newStatus, controlId |
| `FailureConfirmed` | quality | traceability, analytics | `quality.failure` (failureId) | failureId, batchId, controlId, companyId, cause, affectedQuantityKg, confirmedAt |
| `QualityFailureUpdated` *(agregado en V2)* | quality | traceability, analytics | `quality.failure` (failureId) | failureId, batchId, companyId, cause, affectedQuantityKg, status |
| `PossibleDerivedFailureDetected` | traceability | quality, **notification** *(analytics no lo consume: ningún indicador lo necesita)* | `traceability.impact` (failureId) | failureId, sourceBatchId, affected[{batchId, companyId, commonAncestorBatchId}] |

**Eventos agregados en V2 (documentados el 2026-11-03, hallazgo I14).**

- **`AdminRequestSubmitted`**: organization-access lo publica, en la misma transacción, cuando una empresa solicita un administrador (`POST /api/admin-requests`, US07). Tópico `orgaccess.invitation`, clave `requestId`; payload `requestId` y `companyId`. Lo consume **notification**, que avisa al dueño de la licencia (`LICENSE_OWNER`) para que revise la solicitud.
- **`QualityControlCompleted.tests[]`**: las pruebas del control, una por criterio, con `criterionName`, `expectedValue`, `obtainedValue`, `unit` y `testResult` (`CONFORMING` o `FAILED`). Es una lista vacía en un control sin pruebas. Traceability la guarda en el lote (dato de `QUALITY_TEST_VALUES`) y analytics la registra.
- **`QualityFailureUpdated`**: Quality lo publica, en la misma transacción que el cambio, cada vez que una falla cambia: al finalizar un control que la **genera** (`OPEN`), la **reabre** o la **resuelve** (`RESOLVED`), sale **antes** que `QualityControlCompleted` y `QualityStatusChanged`; al **completar su información** (`PUT /api/quality/failures/{id}`); y cuando Production **confirma su descarte** (`CONFIRMED`), **después** de `FailureConfirmed`. Tópico `quality.failure`, clave `failureId`; payload `failureId`, `batchId`, `companyId`, `cause`, `affectedQuantityKg` y `status` (`OPEN`, `CONFIRMED` o `RESOLVED`). `cause` y `affectedQuantityKg` van en `null` hasta que se completa la información. Lo consumen **traceability** (la causa, dato de `FAILURE_CAUSE`) y **analytics**. Sin confirmación de Production no se escribe (igual que los demás eventos de 06c). Lo prueba `FinishAndDiscardApiIT`, que exige el orden, la clave y los seis campos con su valor.

**Proyección de lotes en Quality.** Quality alimenta su `BatchProjection` con `BatchRegistered`, `BatchSplit`, `TransformationCompleted`, `BatchPhaseChanged` y `TransferAccepted`. Los dos del medio son necesarios porque los hijos de una división y las salidas de una transformación **no** llevan `BatchRegistered`: nacen `AVAILABLE` (los hijos heredan el `qualityStatus` del padre, que viaja en `BatchSplit`; las salidas nacen `NOT_REVIEWED`) y deben poder evaluarse. Sin ellos Quality no conocería esos lotes.

**Tópico compactado `orgaccess.user` (RF-012, revocación de usuarios inactivos).** Se declara con `cleanup.policy=compact`: la clave es el `userId` y `UserAccountUpdated` trae el estado completo, así que Kafka conserva el último estado de cada usuario sin límite de tiempo. El api-gateway lo consume **sin grupo de consumidores** (cada instancia lee todo): al arrancar lo relee desde el inicio hasta el final que tenía al empezar y no se declara listo hasta terminarlo; luego sigue en vivo. Mantiene en memoria el conjunto de `userId` con `status = INACTIVE` y responde 401 a sus tokens. Solo `UserAccountUpdated` va a este tópico y su clave es siempre el `userId` (otra clave compactaría eventos distintos). En Azure Event Hubs (Standard) el event hub se crea con compactación desde el inicio: no se puede cambiar después.

**Regla de `BatchPhaseChanged` (decisión del usuario, 2026-10-20).** Production publica `BatchPhaseChanged` **por cada lote cuya fase o retención por calidad (`qualityHold`) cambie, sea cual sea la operación que lo provoque**, y lo hace **además** del evento de negocio de esa operación (`BatchSplit`, `TransformationCompleted`, `TransferInitiated/Accepted/Rejected`…). No depende del origen: aplica igual a las operaciones del usuario (dividir, iniciar y finalizar una transformación, iniciar, aceptar y rechazar una transferencia) y a los comandos de Quality (`START_EVALUATION`, `RELEASE`, `DISCARD`, `HOLD`, `RELEASE_HOLD`).
- **Uno por lote y por cambio.** Una transformación con 3 entradas produce 3 `BatchPhaseChanged` más su `TransformationCompleted`; una transferencia de 5 lotes, 5 más el evento de la transferencia. Cada uno se escribe en el outbox en la **misma transacción** que el cambio (ADR-004): si la operación se revierte, no queda ninguno.
- **Solo si algo cambió.** Un `HOLD` sobre un lote ya retenido, un `RELEASE_HOLD` sobre uno sin retención, la repetición de un comando o de una petición con la misma `Idempotency-Key` no publican nada. Cambiar solo el `qualityStatus` que se muestra (`QualityStatusChanged`) o los datos comerciales no cambia fase ni retención: no genera `BatchPhaseChanged`.
- **Un lote que nace no "cambia".** Los lotes que nacen (registro, hijos de una división, salidas de una transformación) llegan con su estado inicial en `BatchRegistered`, `BatchSplit` y `TransformationCompleted`; no llevan además un `BatchPhaseChanged`.
- **Payload.** `companyId` es la empresa dueña del lote **después** del cambio (en `TransferAccepted`, la destino); `qualityHold` es el valor posterior. Si solo cambió la retención, `previousPhase` y `newPhase` son iguales.
- **`reason` y `requestId`: qué lo provocó.** En las operaciones del usuario, `reason` es el código de la operación (`SPLIT`, `TRANSFORMATION_STARTED`, `TRANSFORMATION_COMPLETED`, `TRANSFER_INITIATED`, `TRANSFER_ACCEPTED`, `TRANSFER_REJECTED`) y `requestId` el id de la transformación, la transferencia o (en la división) el lote padre. En los comandos de Quality vienen del comando (`reason`, `requestId` = controlId o failureId).
- **Orden dentro de la transacción.** Primero el evento de negocio y después los `BatchPhaseChanged`, en orden de `batchId`. El `BatchSplit` lleva como clave el lote padre, la misma de su `BatchRegistered` y su `BatchPhaseChanged`, así que los tres llegan en orden.
- **`batchVersion` (decisión del usuario, 2026-10-21).** Es el `@Version` del lote (bloqueo optimista) **después** del cambio. Crece en cada `BatchPhaseChanged` del mismo lote, y también con cambios que no publican este evento (p. ej. `QualityStatusChanged` copia el `qualityStatus` y sube la versión sin publicar nada), así que entre dos eventos puede haber **saltos, nunca retrocesos ni repeticiones**. Existe para que un consumidor ordene los cambios de un lote que llegan por tópicos distintos **sin depender del reloj** (`occurredAt` sale de `Instant.now` en cada evento: no es estrictamente creciente, puede empatar y el reloj de pared puede retroceder). Reglas para los consumidores: fase, `qualityHold` y empresa dueña (`companyId`, que en una aceptación de transferencia es la destino) se aplican **solo si `batchVersion` es mayor que la guardada**; los eventos de creación (`BatchRegistered`, `BatchSplit`, `TransformationCompleted`) solo completan un lote nuevo o provisional y nunca pisan una versión mayor. Production garantiza que un lote tiene **a lo sumo un** `BatchPhaseChanged` por transacción (`BatchChanges` lanza `IllegalStateException` si hubiera dos: compartirían versión). Pruebas: `BatchVersionOrderingIT` y `BatchChangesTest`.
- **Analytics (I9, 2026-11-04).** Aplica esta regla: la empresa dueña de un lote es la del `BatchPhaseChanged` con mayor `batchVersion`, y `TransferAccepted` ya no la mueve (la mueven los `BatchPhaseChanged` de la transferencia). `ProjectionOrderIndependenceIT` lo prueba con eventos en 30 órdenes y con la hora al revés de la versión. Analytics consume cada tópico con su propio grupo y puede reconstruirse desde ellos: [`operations/analytics-rebuild.md`](../operations/analytics-rebuild.md).
- **Orden.** La clave en Kafka es el `batchId`, así que los cambios de un mismo lote llegan en orden. Entre tópicos distintos (`production.batch` frente a `production.transformation` o `production.transfer`) no hay orden garantizado: los consumidores resuelven con `MERGE` y nodos provisionales (ver "Consumo idempotente").
- **Prueba obligatoria** (`BatchPhaseChangedEventsIT`): recorre **todas** las operaciones de escritura de Production (cada ruta `POST/PUT/PATCH` y el comando interno) y falla si una no está clasificada como "cambia fase o retención" (y publica) o "no las cambia" (y no publica); así una operación nueva no puede olvidar el evento.

No existen eventos de "solicitud de cambio de fase" ni de "confirmación/rechazo": eso es el comando síncrono de abajo.

**`CompanyRegistered` en Traceability (decisión del usuario, 2026-10-21).** Traceability lo consume solo para guardar `businessName` en `:Company`: la empresa responsable es un dato siempre compartido y una respuesta con solo un UUID no sirve al frontend. Usa `companyId` y `businessName` y ignora el resto (`ruc`, `activities`, `status`); `CompanyStatusChanged` sigue sin ser suyo. El nombre se completa si falta y no cambia una vez guardado (el alta es única por empresa); una empresa que Traceability conoce antes por otro evento (un lote, `CompanyPrivacyUpdated`) se completa cuando llega su `CompanyRegistered`.

**Retención de `orgaccess.company`.** El tópico no se compacta (comparte la clave `companyId` entre `CompanyRegistered`, `CompanyStatusChanged` y `CompanyPrivacyUpdated`), así que se declara con `retention.ms=-1`: Production y Traceability lo leen desde el inicio al arrancar por primera vez o al reconstruirse, y las altas antiguas no deben desaparecer. En Azure Event Hubs Standard la retención máxima es de 7 días (ver el riesgo en `docs/progress.md`).

**`traceability.impact` y `quality.failure.DLT` (Traceability, 05e-2).** Traceability declara `traceability.impact` (el único tópico que produce: `PossibleDerivedFailureDetected`, clave `failureId`) con `retention.ms=-1` y sin compactar, como los demás tópicos de eventos de negocio (Quality, Notification y Analytics lo leen desde el inicio cuando existan o se reconstruyan; con `modify-topic-configs=true`, en local, corrige uno que ya existía con otra retención), y `quality.failure.DLT`. `quality.failure` es de Quality y Traceability no lo crea: lo consume cuando aparezca. Un `FailureConfirmed` con la familia incompleta se reintenta con **10 reintentos propios (1 s → 60 s, ≈ 5 min en total, configurables)** y, agotados, va a `quality.failure.DLT` con un `ERROR`; el procedimiento de reenvío está en `docs/progress.md` («Reenvío desde la DLT de quality.failure»). La espera más larga debe ser como máximo la mitad de `max.poll.interval.ms` (explícito, 5 min; una quinta parte con los valores por omisión): se valida al arrancar.

**Retención de `production.batch`, `production.transformation` y `production.transfer`.** Tampoco se borran por tiempo (`retention.ms=-1`, sin compactar: varios tipos de evento comparten la clave y cada uno es un hecho de la historia). Traceability reconstruye la genealogía leyéndolos desde el inicio, y con la retención por defecto (7 días) los lotes antiguos desaparecerían de su grafo al reconstruirlo. Production los declara así al arrancar y, con `modify-topic-configs=true` (local), corrige también los que ya existían (`EventTopicsRetentionIT`). En Azure Event Hubs Standard rige el mismo límite de 7 días (ver el riesgo en `docs/progress.md`).

**Campos configurables de privacidad B2B en los eventos (revisión del 2026-10-21).** Traceability filtra por los 11 `ConfigurableField` de `organization-access.md`; esto es lo que cada evento trae de cada uno para que el dato tenga sentido a otra empresa:

| Campo | Dónde viaja | Estado |
|---|---|---|
| `SUPPLIER` | `BatchRegistered.supplierName`, `BatchSplit.children[].supplierName` | Bien. Las salidas de una transformación nacen sin proveedor (decisión de 04a): nulo, y sus hijos también |
| `ORIGIN` | `BatchRegistered.origin`, `BatchSplit.children[].origin` | Bien. Idem salidas |
| `QUANTITY` | `quantityKg` en `BatchRegistered`, `BatchSplit.children[]` y `TransformationCompleted.outputs[]` | Bien |
| `COMPOSITION` | `BatchRegistered.composition[]`, `BatchSplit.children[].composition`, `TransformationCompleted.outputs[].composition` | Bien. En los hijos de una división es la del padre (**agregado el 2026-10-21**) |
| `RECEPTION_CHARACTERISTICS` | `BatchRegistered.receptionCharacteristics`, `BatchSplit.children[].receptionCharacteristics` | Bien. Idem salidas |
| `MACHINERY` | `TransformationCompleted.machineName` y `machineType` (`Machine.name` y `Machine.type` al finalizar, foto del momento) | **Agregado el 2026-10-21**. `machineId` sigue viajando (para Analytics) pero otra empresa no puede consultarlo; el `internalCode` de la máquina **no** viaja |
| `SHRINKAGE` | `TransformationCompleted.shrinkageKg` | Bien |
| `WASTE` | `TransformationCompleted.wasteKg` | Bien |
| `WASTE_REASON` | `TransformationCompleted.wasteReason` | **Agregado el 2026-10-21** (antes no viajaba); nulo si no hubo desperdicio |
| `FAILURE_CAUSE` | `QualityFailureUpdated.cause` | **Resuelto (V2, documentado el 2026-11-03).** Traceability guarda la última causa por lote (gana el `occurredAt` más reciente). Ojo: hoy se muestra a otra empresa aunque la falla siga abierta o se haya resuelto (hallazgo I15, abierto) |
| `QUALITY_TEST_VALUES` | `QualityControlCompleted.tests[]` | **Resuelto (V2, documentado el 2026-11-03).** Traceability guarda las pruebas del último control y otra empresa las ve como `qualityTests` (criterionName, expectedValue, obtainedValue, unit, testResult) solo si se comparte |

**`name`, el nombre libre del lote (decisión del usuario, 2026-11-03).** Viaja opcional en `BatchRegistered`, `BatchSplit.children[]` y `TransformationCompleted.outputs[]` (nulo en eventos anteriores). No es un campo siempre compartido ni un `ConfigurableField`: **solo lo ve la empresa dueña del lote**. Traceability lo guarda en el nodo y `LineageAuthorizationPolicy` lo devuelve solo en los lotes `OWN` de las vistas hacia atrás, hacia adelante y completa; en un nodo `SHARED` no sale nunca, aunque la empresa dueña comparta los 11 configurables. Los resultados de búsqueda por QR o Traceability ID (`VisibleBatchSummary`) no lo llevan para nadie, y `PossibleDerivedFailureDetected` tampoco. Quality lo ignora. Cualquier otro consumidor que lo exponga debe respetar la misma regla. Pruebas: `LineageAuthorizationPolicyTest`, `LineageApiIT` y `FailureImpactIT`.

Los hijos de `BatchSplit` traen, además de sus códigos, los datos que heredan del padre (`materialType`, `composition`, `origin`, `supplierName`, `receptionCharacteristics`) y `createdAt` (el instante de la división), porque no tienen `BatchRegistered` y los consumidores no los conocerían de otro modo. `finalProduct` no viaja: un hijo nunca es producto final. Las salidas de una transformación nacen sin origen, proveedor ni recepción (04a), así que sus hijos los llevan nulos; el `createdAt` de una salida es el `completedAt` de su transformación.

## Publicación: Transactional Outbox (ADR-004)

- El caso de uso guarda el agregado **y** una fila en `outbox` en la misma transacción.
- `OutboxRelay` (`@Scheduled`, ~1 s) lee filas `PENDING`, publica en Kafka y las marca `PUBLISHED`.
- Entrega at-least-once: los duplicados son normales.
- En Traceability (Neo4j) el outbox son nodos `OutboxEvent` creados en la misma transacción del grafo.

## Consumo idempotente

- Cada consumidor registra `eventId` en `processed_events` **en la misma transacción** que aplica el efecto; si ya existe, descarta el evento.
- En Neo4j: nodo `ProcessedEvent {eventId}` con restricción de unicidad; escrituras con `MERGE` por `traceabilityId`.
- Reintentos con espera creciente; tras el máximo, el evento va a `<topic>.DLT`.
- Si llega un evento que referencia un lote aún desconocido, `MERGE` crea un nodo provisional que el evento posterior completa.

## Comando interno Quality → Production (ADR-009)

`POST /internal/batches/{batchId}/phase-transitions` — **solo red interna, no se enruta por el Gateway**.

```json
{
  "operation": "START_EVALUATION | RELEASE | DISCARD | HOLD | RELEASE_HOLD",
  "requestId": "controlId o failureId",
  "qualityHold": true,
  "reason": "texto",
  "requestedBy": "userId"
}
```

- `START_EVALUATION`: `AVAILABLE` → `IN_QUALITY_CONTROL` (en los diagramas aparece como `EN_EVALUACION`).
- `RELEASE`: `IN_QUALITY_CONTROL` → `AVAILABLE`; `qualityHold` (obligatorio solo aquí) fija la retención en el mismo cambio: `false` si el control salió conforme, `true` si salió con falla.
- `DISCARD`: `AVAILABLE` → `DISCARDED`.
- `HOLD` / `RELEASE_HOLD`: activa o quita la **retención por calidad** sobre un lote `AVAILABLE` (no es una fase; es una marca que impide dividir, transformar o transferir). `HOLD` se usa ante una posible falla derivada.
  - **Estado deseado (decisión del usuario, 2026-10-20):** `HOLD` y `RELEASE_HOLD` expresan el estado al que se quiere llegar, no una transición. Si el lote ya está en ese estado responden **200 con el estado actual, sin cambios y sin publicar `BatchPhaseChanged`** (dos fallas distintas pueden retener el mismo lote; la segunda no debe fallar). El comando igualmente queda registrado en `phase_command_log` para que repetirlo devuelva lo mismo. El **409** queda solo para transiciones de fase imposibles (p. ej. `HOLD` sobre un lote que no está `AVAILABLE`).
- Nombres de fases: `docs/domain/glossary.md`.
- **`requestedBy` (UUID del usuario)**: obligatorio en `START_EVALUATION`, `RELEASE`, `DISCARD` y `RELEASE_HOLD` (si falta, `400 MISSING_FIELDS`); **opcional solo en `HOLD`**, que por una falla derivada lo dispara un evento y no una persona (la auditoría lo registra como acción del sistema: `audit_log.user_id` nulo). Quality envía `requestedBy` en el cuerpo y **no propaga el JWT** del usuario: Production no valida identidad en esta ruta (red interna, ADR-005) y no inventa autenticación entre servicios.
- `reason` (texto, opcional): es el `reason` del `BatchPhaseChanged`; si falta, se usa el nombre de la operación.

### Respuestas y cómo debe reaccionar Quality

| Respuesta | Significado | Quality |
|---|---|---|
| `200` `{batchId, phase, qualityHold}` | Aplicado, o repetición de un comando ya aplicado (devuelve lo que devolvió la primera vez) | Confirma: el control pasa a `EN_CURSO` (o se completa lo que corresponda) |
| `409` | **Solo rechazo de negocio**; el cuerpo `ApiError.code` dice el motivo | **Cancelar el control** |
| `404` `BATCH_NOT_FOUND` | El lote no existe | **Cancelar el control** |
| `503` `TEMPORARILY_UNAVAILABLE` con `Retry-After: 1`, o *timeout* / sin respuesta | Una carrera con otra operación no se resolvió, o el lote estaba bloqueado por una operación del usuario | **Reintentar con el mismo `requestId`** (el reintento es seguro: la idempotencia lo protege) |
| `400` | La petición está mal (`MISSING_FIELDS`, `INVALID_REQUEST_ID`, `MALFORMED_REQUEST`) | Registrar **ERROR**, **no reintentar** y **cancelar el control con el código del 400 como motivo** *(ampliación acordada con el usuario, 2026-10-23: Production no deja rastro de un 400, así que cancelar es seguro, y un control que ningún reintento arregla quedaría `STARTING` bloqueando el lote para siempre por el índice de un solo control activo)*: es un defecto de Quality |

**Cualquier otra respuesta** (implementación de 06b): otro 4xx (401, 403, 405, 415, 422...) se trata como el `400`; otro 5xx (500, 502, 504), un `200` con cuerpo ilegible, cualquier 1xx/2xx/3xx que no sea `200`, un timeout (lectura de 2 s) o una conexión que falla se tratan como el `503` —no se sabe si Production aplicó el comando—, y **un control nunca se cancela por duda**; con el circuito abierto no se llama y el control espera sin contar un intento. El motivo de cancelación (`quality_control.cancel_reason`) es el `ApiError.code` de Production, acotado a 100 caracteres (`HTTP_<status>` si no trae uno legible).

Códigos de los `409` (no hay más): `INVALID_PHASE_TRANSITION` (la fase del lote no admite la operación: `START_EVALUATION`/`DISCARD`/`HOLD`/`RELEASE_HOLD` exigen `AVAILABLE`, `RELEASE` exige `IN_QUALITY_CONTROL`), `BATCH_IS_FINAL_PRODUCT` (`START_EVALUATION` sobre un producto final, RF-036) y, solo en `START_EVALUATION`, `COMPANY_INACTIVE` o `COMPANY_UNKNOWN` (RF-003). Una carrera **nunca** es 409 `CONCURRENT_MODIFICATION` en esta ruta.

### Reglas de Production

- **Idempotente por `(batchId, requestId, operation)`** (tabla `phase_command_log`): repetir la misma combinación devuelve el resultado **guardado**, aunque el lote haya cambiado después e incluso si hoy la operación ya no sería válida (o la empresa se hubiera inactivado). El mismo `requestId` con otra `operation` es **otro comando** (`START_EVALUATION` y `RELEASE` de un control comparten `controlId`). Solo se guardan los comandos aplicados (200, incluidos los `HOLD`/`RELEASE_HOLD` sin cambio); un rechazo (409, 400, 404) no deja rastro y, si se repite, se vuelve a evaluar. Una repetición de `RELEASE` con otro `qualityHold` devuelve igualmente el resultado original (se registra un WARN).
- **Bloqueo optimista** (`version` del agregado `Batch`, decisión del informe/ADR-009): el lote se lee **sin** `FOR UPDATE`. Si otra transacción lo modificó, o dos comandos idénticos chocan en la clave de `phase_command_log`, Production **reintenta la transacción completa** (hasta 3 veces, con una espera mínima) y el reintento ve el resultado o el cambio del ganador. Si la carrera persiste, responde **503**. Cuando la escritura espera a una operación del usuario que tiene el lote con `FOR UPDATE`, la espera máxima es `lock_timeout = 1500 ms` (menor que el timeout de 2 s de Quality) y se responde 503 de inmediato, sin reintentar dentro.
- **Empresa inactiva (RF-003, decisión del usuario)**: una empresa inactiva es de solo lectura y Quality no conoce el estado de las empresas, así que **solo `START_EVALUATION` se bloquea** (409 `COMPANY_INACTIVE`, o `COMPANY_UNKNOWN` si la empresa aún no está en la proyección). `RELEASE`, `DISCARD`, `HOLD` y `RELEASE_HOLD` no se bloquean: una empresa inactivada a mitad de un control no debe dejar el lote atrapado en `IN_QUALITY_CONTROL`.
- Production publica `BatchPhaseChanged` por outbox cuando el comando cambia la fase o la retención (regla general arriba); estos comandos no tienen otro evento de negocio. Y escribe una fila de auditoría (`BATCH_PHASE_COMMAND`) cuando hubo cambio.
- La ruta está fuera de `/api/**`: no la alcanzan el interceptor de identidad ni el `IdempotencyFilter` (no hay `X-User-Id`); el Gateway responde 404 a `/internal/**`.
- Cliente en Quality: `ProductionPhaseClient` (implementa `ProductionPhasePort`) con `RestClient`, Timeout 2 s, Circuit Breaker, Retry acotado. **Sin fallback que simule éxito.** Quality guarda el control en `INICIANDO` antes de llamar, lo pasa a `EN_CURSO` con la confirmación y lo cancela con un 409 o un 404; con un 503, un *timeout* o sin respuesta, `PendingControlRecoveryJob` reintenta con el mismo `requestId`. **Detalle (06b)**: Retry de 2 intentos en total (300 ms entre ellos) solo ante 5xx, timeout o error de red, por fuera del Circuit Breaker (ventana 10, mínimo 5 llamadas, 50 %, 30 s abierto; los 409/404/400 no cuentan como fallos); el control se guarda `STARTING` en una transacción **propia** (`REQUIRES_NEW`, porque el `IdempotencyFilter` envuelve la petición en una que un rechazo o una caída revierte) y se confirma o cancela en otra, también propia; el job reclama con `FOR UPDATE SKIP LOCKED` (arrendamiento de 2 min, espera 15 s × 2 hasta 5 min, **sin tope de intentos**, ERROR desde el intento 20) y la petición que lo creó tiene 30 s de gracia antes de que el job pueda tocarlo. El usuario recibe `201` si Production confirma y `202` con el control `STARTING` si no responde (ver `api.md`).

### Finalizar un control, descartar una falla y retener por falla derivada (06c)

`RELEASE` (requestId = controlId), `DISCARD` (requestId = failureId) y `HOLD` (requestId = failureId, sin `requestedBy`) usan el mismo cliente y la misma tabla de respuestas que `START_EVALUATION`. **Nada cambia de estado, y ningún evento se escribe, hasta que Production confirma con 200**; con la confirmación todo ocurre en **una** transacción de Quality (control `FINISHED`, proyección, falla generada/reabierta/resuelta o confirmada, y el outbox):

| Respuesta | `RELEASE` / `DISCARD` | `HOLD` |
|---|---|---|
| `200` | control `FINISHED` + `QualityControlCompleted` y `QualityStatusChanged` (precedidos de `QualityFailureUpdated` si la falla se genera, reabre o resuelve); falla `CONFIRMED` + `FailureConfirmed` y `QualityFailureUpdated` | aviso `APPLIED`; calidad → `POTENTIAL_DERIVED_FAILURE` + `QualityStatusChanged` |
| sin respuesta / 5xx / circuito abierto | pendiente (el control sigue `IN_PROGRESS`, la falla `OPEN`); el job repite el mismo `requestId` | aviso `PENDING`; el job de 5 s repite |
| `409` / `404` | la solicitud pendiente se descarta, **sin eventos**, ERROR en el log (el lote no está donde Quality cree: procedimiento «RELEASE o DISCARD rechazado» en `progress.md`); el usuario recibe el error | `409`: sigue `PENDING` y se vuelve a decidir con la proyección; `404`: `NO_ACTION` |
| `400` | como el 409; el usuario recibe 500 | sigue `PENDING` con ERROR |

El resultado de la finalización se congela al pedirla (la retención de `RELEASE` es la misma en cada reintento) y `endDate` es el instante en que el usuario finalizó. Cuatro fuentes de comandos pendientes (inicio, finalización, descarte y retención) con su propio manejo de fallos: una que falla no frena a las otras.

## Idempotencia de escrituras del frontend (ADR-010)

- Toda escritura (`POST`, `PUT`, `PATCH`) acepta la cabecera `Idempotency-Key` (UUID generado por el frontend al confirmar y reutilizado en reintentos).
- `IdempotencyFilter` guarda `key + hash del cuerpo + respuesta` en `idempotency_keys` en la misma transacción que la operación.
- Clave repetida → misma respuesta, sin crear nada. Clave repetida con cuerpo distinto → `422`. Caducidad: 24 h.
- **`multipart/form-data`** (evidencias): el hash es de las **partes** —nombre del campo, nombre del archivo, tipo y bytes, en orden— y no del cuerpo crudo, porque el separador (`boundary`) cambia en cada intento aunque los archivos sean los mismos. Las partes las lee el contenedor y el controlador las reutiliza (no se copia el cuerpo, que puede pesar decenas de MB). Si el contenedor no puede leerlas (p. ej. un archivo de más de 10 MB) la petición sigue sin idempotencia y el controlador responde el error; un rechazo nunca deja la clave.
- Frontend: detecta desconexión (eventos `online`/`offline` + plazo de espera **según el tipo de operación**: 2,5 s en lecturas y escrituras JSON normales, 30 s en subidas `multipart` y en el alta de empresa, 15 s en el informe PDF y 8 s en los comandos de Quality; ver la enmienda de ADR-010 en `adr.md`), muestra aviso (en menos de 3 s en las operaciones normales y siempre que el navegador dispare `offline`), guarda borrador en IndexedDB y reintenta con la **misma clave**. Agotar el plazo no descarta la clave pendiente; el cliente la da por vigente 23 h (el servidor la guarda 24 h) y, pasado ese tiempo, avisa de que la operación pudo haberse registrado y el siguiente intento usa una clave nueva.
