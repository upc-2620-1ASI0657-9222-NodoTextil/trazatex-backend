# Production — dominio

Fuentes: RF-003, RF-014, RF-019…RF-042, RF-063; US10, US12–US22, US42; Figura 30 (canvas) y Figura 39 (base de datos); ADR-003, ADR-009, ADR-010.
Nombres de enums y la regla de **lote habilitado**: `glossary.md`.

## Agregados y datos

**Batch**
- Figura 39: `id`, `traceabilityId` (único), `companyId` (empresa responsable), `name`, `materialType`, `quantityKg`, `phase` (columna `current_stage`), `qualityStatus` (copia para mostrar), `finalProduct`, `createdAt`, y la tabla `batch_compositions (componentName, percentage)`.
- Además (exigido por los RF): `qrCode` (único, no reutilizable), `supplierName` (opcional), `origin` (origen geográfico), `receptionCharacteristics` (texto técnico), `qualityHold`, `origin type` (`REGISTERED` | `SPLIT` | `TRANSFORMATION`), `createdBy` (operario), `version` (bloqueo optimista), datos comerciales opcionales (`buyerOrDistributor`, `salePrice`, `currency`, `commercialExitDate`, `commercialReference`).
- `quantityKg` y porcentajes: `BigDecimal` con escala fija (objeto de valor `Quantity` en kg, ADR-003). Siempre > 0.

**Transformation**
- Figura 39: `id`, `transformationType`, `machineryId`, `operatorId`, `totalInputKg`, `totalOutputKg`, `wasteKg`, `wasteReason`, `shrinkageKg`, `startTime`, `endTime`.
- Además: `companyId`, `status`, entradas `(batchId, quantityKg)` y salidas `(batchId)`.

**Transfer**
- Figura 39: `id`, `originCompanyId`, `targetCompanyId`, `status`, `departureTime`, `receptionTime`, `rejectionReason`.
- Además: lotes transferidos `(transferId, batchId)`, `initiatedBy`, `resolvedBy`.

**Machine**
- Figura 39: `id`, `companyId`, `internalCode` (único por empresa), `name`, `status`. Además: `type`.

**Evidence** (fotos en Cloudinary): `id`, `ownerType` (`BATCH` | `TRANSFORMATION`), `ownerId`, `companyId` (la empresa que la subió), `status` (`PENDING` | `UPLOADED` | `FAILED`), `url`, `originalFilename`, `contentType`, `sizeBytes`, `uploadedBy`, `createdAt`, `uploadedAt` y, solo de operación, `attempts`, `nextAttemptAt` y `lastError`.

**CompanyProjection** (de eventos de Organization & Access): `companyId`, `businessName`, `status`.

Un `BatchFactory` (Factory Method) crea todo lote nuevo (registro, división, transformación) y siempre le asigna Traceability ID y QR nuevos.

## Transiciones de fase (patrón State)

| Desde | Operación | Hacia |
|---|---|---|
| — | Registrar / nacer de división / nacer de transformación | `AVAILABLE` |
| `AVAILABLE` (habilitado) | Dividir | `SPLIT` |
| `AVAILABLE` (habilitado) | Iniciar transformación | `IN_TRANSFORMATION` |
| `IN_TRANSFORMATION` | Finalizar transformación | `PROCESSED` |
| `AVAILABLE` (habilitado) | Iniciar transferencia | `IN_TRANSFER` |
| `IN_TRANSFER` | Aceptar (cambia `companyId`) / Rechazar | `AVAILABLE` |
| `AVAILABLE` (sin otro control) | Comando `START_EVALUATION` | `IN_QUALITY_CONTROL` |
| `IN_QUALITY_CONTROL` | Comando `RELEASE` | `AVAILABLE` |
| `AVAILABLE` | Comando `DISCARD` | `DISCARDED` |

Cualquier otra transición se rechaza con 409. `PROCESSED`, `SPLIT` y `DISCARDED` son finales.
`START_EVALUATION` exige `AVAILABLE` y no exige "habilitado": un lote retenido (`FAILED`, `PENDING_REEVALUATION`, `POTENTIAL_DERIVED_FAILURE`) debe poder controlarse. Tampoco aplica a productos finales (no pueden "participar en nuevas operaciones", RF-036).

## Reglas por caso de uso

**Registrar lote (US12, RF-019…021)** — `OPERATOR` de una empresa `ACTIVE`.
- Obligatorios: origen, tipo de material, composición, cantidad, características de recepción. Proveedor opcional.
- Composición: uno o más componentes; la suma de porcentajes es **exactamente** 100 (si no, 400 "composición no válida").
- Nace `AVAILABLE`, `NOT_REVIEWED`, `qualityHold = false`. Evento `BatchRegistered`.

**Buscar y consultar (US13, US14, RF-022, RF-023)**
- Búsqueda y filtros sobre los lotes de la propia empresa (Traceability ID, tipo de material, fase, estado de calidad, rango de fechas).
- **Rendimiento (RNF-03, decisión del usuario)**: la búsqueda por prefijo del Traceability ID usa un índice `text_pattern_ops` (migración V2) y se comprueba con `EXPLAIN`; la paginación tiene **máximo 100 filas por página** (si piden más, se usa 100; por defecto 20) y un orden estable (`created_at` descendente, luego `id`).
- Por QR: si el lote es de la propia empresa, todos sus datos; si es de otra empresa, **solo los campos siempre compartidos** (Production no conoce la privacidad configurable; la vista completa entre empresas es la genealogía de Traceability). QR desconocido: 404 "código no encontrado".

**Dividir (US15, RF-025…027)**
- Lote habilitado; dos o más hijos; suma de cantidades de los hijos = cantidad del padre exactamente (si no, 400).
- Padre → `SPLIT`. Hijos `AVAILABLE` con Traceability ID y QR nuevos, misma composición, tipo de material, origen y proveedor del padre, y el mismo `qualityStatus` del padre. Evento `BatchSplit`.

**Iniciar transformación (US16, RF-028…031)**
- Uno o más lotes habilitados de la empresa del operario; tipo de transformación; máquina `ACTIVE` de la misma empresa.
- Registra `startTime`; entradas → `IN_TRANSFORMATION`. Toda entrada se consume completa (para usar una parte, primero se divide). Más de una entrada = mezcla.

**Finalizar transformación (US17–US19, RF-029, RF-032…035, ADR-003)**
- Una o más salidas, cada una con cantidad > 0 y tipo de material; desperdicio ≥ 0; si desperdicio > 0, el motivo es obligatorio.
- `shrinkageKg = totalInput − totalOutput − waste`; si es negativo, 400 "cantidades no válidas". La merma nunca se ingresa.
- Composición de salidas: una entrada → la misma composición; varias → promedio ponderado por kg.
- Entradas → `PROCESSED`; salidas `AVAILABLE`, `NOT_REVIEWED`, con Traceability ID y QR nuevos. Cada salida puede marcarse como producto final aquí.
- `TransformationStrategy` por tipo agrega validaciones propias, siempre más restrictivas; nunca cambia la fórmula de merma. Todo en una transacción con el outbox. Evento `TransformationCompleted`.
- **Rendimiento mínimo por tipo** (decisión del usuario): cada estrategia exige `salida total / entrada total ≥ mínimo`: `SPINNING` 60 %, `WEAVING` 80 %, `DYEING` 80 %. Configurable en `trazatex.transformation.min-yield-percent.{spinning|weaving|dyeing}`. Si es menor, 400 `YIELD_BELOW_MINIMUM` con un mensaje que indica el rendimiento obtenido y el mínimo. Se evalúa **después** de la invariante de balance (que nunca se relaja) y sin redondear la comparación. Las pérdidas por desperdicio y merma no se "descuentan": el rendimiento es solo salida / entrada.
- **Escalas** (decisión del usuario): `Quantity` en kg con **3 decimales** (gramos); porcentajes con **2 decimales**. Una cantidad o porcentaje con más decimales se rechaza (400), no se redondea en silencio, así toda la aritmética del balance es exacta. El promedio ponderado de la composición reparte el redondeo por **mayor resto** para que sume exactamente 100,00; un componente que quedaría en 0,00 se omite.

**Producto final (US20, RF-036, RF-037)**
- Se marca al finalizar la transformación (por salida). Ya no se divide, transforma ni transfiere, y sigue en la genealogía.
- Datos comerciales opcionales, editables después por el operario; si hay precio, la moneda es obligatoria. Son siempre privados: no viajan en ningún evento.

**Transferencias (US21, US22, RF-038…042)**
- Iniciar (`OPERATOR`): uno o más lotes completos y habilitados de su empresa hacia otra empresa de la cadena. **Solo lotes completos (US21 esc. 2):** quien pida una parte de un lote (`quantitiesKg` distinto de la cantidad del lote) recibe 400 `PARTIAL_TRANSFER_NOT_ALLOWED` y debe dividir antes; se comprueba antes de la regla de lote habilitado y sin cambiar nada. Lotes → `IN_TRANSFER`, transferencia `PENDING`. Evento `TransferInitiated` (Notification avisa a los administradores receptores).
- Aceptar (`COMPANY_ADMIN` de la empresa destino): `receptionTime`, `companyId` de los lotes = destino, lotes `AVAILABLE`, mismos Traceability ID y QR. Evento `TransferAccepted`.
- Rechazar (`COMPANY_ADMIN` destino, motivo obligatorio): lotes `AVAILABLE` en la empresa de origen. Evento `TransferRejected`.

**Maquinaria (US10, RF-014)** — `COMPANY_ADMIN`: registrar (código, nombre, tipo → `ACTIVE`) y cambiar estado entre `ACTIVE`, `OUT_OF_SERVICE`, `INACTIVE`. Solo `ACTIVE` se usa en transformaciones. Repetir el estado actual es 409 `INVALID_STATUS_TRANSITION`. Cada cambio queda en `machine_status_history` (US10 esc. 2: "conserva su historial").

**Evidencias (US42, RF-063, ADR-007)** — `OPERATOR` de la empresa dueña adjunta fotos a un lote o a una transformación; `OPERATOR` y `COMPANY_ADMIN` las listan.
- **Validación** (todo o nada): JPEG, PNG o WebP según los **primeros bytes** del archivo (no la extensión ni el `Content-Type`), hasta **10 MB** cada uno y hasta **5 archivos por petición**. Formato no válido → 400 `UNSUPPORTED_FILE_FORMAT` indicando los formatos permitidos; sin archivos → 400 `NO_FILES`; más de 5 → 400 `TOO_MANY_FILES`; un archivo de más de 10 MB → **413** `FILE_TOO_LARGE`.
- **Cloudinary y la cola diferida**: la petición sube cada archivo (`public_id` = id de la evidencia, `overwrite=false`: reintentar no duplica). Si Cloudinary falla (timeout, 5xx, 429, credenciales, circuito abierto, sin `CLOUDINARY_URL`) la petición **termina igual con 201**: la evidencia queda `PENDING` y sus bytes en `evidence_upload_queue` (PostgreSQL), en la misma transacción que su fila, y tras el primer fallo el resto de la petición va directo a la cola. Un job (cada 30 s, `FOR UPDATE SKIP LOCKED` con lease de 5 min) la sube con espera creciente (30 s ×2, tope 30 min). Si Cloudinary dice que la imagen no es válida (400/422): en la petición → 400 y nada se guarda; en el job → `FAILED`. Tras 30 intentos fallidos → `FAILED`. Un circuito abierto o la falta de configuración **no consumen intentos**. En `FAILED` los bytes se conservan (reencolado manual: `docs/progress.md`).
- **Listado**: una evidencia `PENDING` trae `url: null` y `uploadedAt: null`; no expone intentos ni errores.
- **Permisos y alcance**: adjuntar se permite aunque la empresa esté inactiva o desconocida y en cualquier fase del lote o estado de la transformación. **Cada empresa ve solo las evidencias que subió ella** (decisión del usuario, la opción conservadora): cuando un lote se transfiere, la nueva dueña no ve las fotos de la anterior ni la anterior ve ya el lote. Que una empresa vea las evidencias de otras a lo largo de la cadena se decide en **Traceability con ADR-006** (privacidad B2B), no aquí.
- **No bloquea el lote**: el dueño se lee sin bloqueo y la subida ocurre fuera de cualquier transacción que lo tenga tomado; una división, transferencia o finalización simultáneas no esperan a Cloudinary.
- No publica eventos (el catálogo no define ninguno); sí auditoría `EVIDENCE_ATTACHED`.

**Empresa inactiva (RF-003: "no podrán iniciar nuevas operaciones productivas")** — si la empresa del usuario está `INACTIVE` en la proyección:
- **Se bloquean con 409 `COMPANY_INACTIVE`** las operaciones que *inician* trabajo o *incorporan* material: registrar un lote, dividir, iniciar una transformación, iniciar una transferencia y **aceptar** una transferencia entrante.
- **Se permiten** las que *cierran* trabajo ya iniciado, para que ningún lote quede atascado: **finalizar** una transformación ya iniciada y **rechazar** una transferencia entrante (devuelve los lotes al origen). También las lecturas, la maquinaria, los datos comerciales y las evidencias.
- Una empresa sin fila en la proyección (evento aún no consumido) se trata como desconocida: 409 `COMPANY_UNKNOWN`; no se opera sobre un estado sin verificar. Aplica a las mismas operaciones bloqueadas.
- La empresa **destino** de una transferencia debe estar `ACTIVE` al iniciarla (409 `TARGET_COMPANY_INACTIVE`).

## Comando interno de Quality (ADR-009)

`POST /internal/batches/{batchId}/phase-transitions` — contrato en `docs/architecture/communication.md`. Resumen:

| Operación | Efecto |
|---|---|
| `START_EVALUATION` | `AVAILABLE` → `IN_QUALITY_CONTROL` |
| `RELEASE` (con `qualityHold` true/false) | `IN_QUALITY_CONTROL` → `AVAILABLE` y fija la retención en el mismo cambio |
| `DISCARD` | `AVAILABLE` → `DISCARDED` |
| `HOLD` | `qualityHold = true` (lote `AVAILABLE`; posible falla derivada). Si ya estaba retenido: 200 sin cambios ni evento |
| `RELEASE_HOLD` | `qualityHold = false`. Si no estaba retenido: 200 sin cambios ni evento |

Idempotente por `(batchId, requestId, operation)`: repetirlo devuelve el resultado guardado aunque el lote haya cambiado. Publica `BatchPhaseChanged` solo si cambió la fase o la retención. Contrato completo (códigos, `requestedBy`, 503 con `Retry-After`, cómo reacciona Quality) en `docs/architecture/communication.md`.

**Empresa inactiva (decisión del usuario, RF-003)**: de estos comandos solo `START_EVALUATION` se bloquea (409 `COMPANY_INACTIVE` / `COMPANY_UNKNOWN`); `RELEASE`, `DISCARD`, `HOLD` y `RELEASE_HOLD` no, para que ningún lote quede atrapado en `IN_QUALITY_CONTROL` ni se ignore una falla derivada.

`HOLD` y `RELEASE_HOLD` expresan un **estado deseado** (decisión del usuario): pedir el estado en que el lote ya está responde 200 con el estado actual, sin cambiar nada y **sin publicar `BatchPhaseChanged`** (dos fallas distintas pueden retener el mismo lote). El 409 queda solo para transiciones de fase imposibles.

## Auditoría (RNF-11)
Toda operación sobre lotes, transformaciones y transferencias escribe en `audit_log` (usuario, empresa, fecha y hora, acción, entidad, cambios) en la misma transacción.

## Eventos que consume
- `CompanyRegistered`, `CompanyStatusChanged` (`orgaccess.company`, grupo `production.orgaccess-company`, desde el inicio del tópico) → `CompanyProjection`.
  - El estado lo manda el `CompanyStatusChanged` más reciente: un `CompanyRegistered` sobre una empresa que ya se conocía solo completa el nombre.
  - **`CompanyStatusChanged` de una empresa que la proyección no conoce** (decisión del usuario): se crea la fila con ese estado y sin nombre (WARN). Bloquear a una empresa inactiva es más seguro que ignorarla; el nombre se completa cuando llegue su `CompanyRegistered`.
  - Por ese tópico llega también `CompanyPrivacyUpdated` (de traceability) y puede llegar cualquier tipo nuevo: Production los **ignora** sin error, sin DLT y sin registrarlos en `processed_events`.
- `QualityStatusChanged` (`quality.control`, grupo `production.quality-control`) → copia de `qualityStatus` en el lote (solo para mostrar; las reglas usan `qualityHold`).
  - No cambia la fase ni `qualityHold` ni publica `BatchPhaseChanged`; pasa por `BatchChanges`, que lo vigila. Se aplica por `batchId`; si el `companyId` del evento ya no es el del lote (lote transferido) se registra un WARN y se aplica igual.
  - Lote inexistente: ERROR y directo a `quality.control.DLT`, sin reintentos. Un conflicto de `@Version` con una operación del usuario **se reintenta** con la espera creciente del consumidor (el lote se lee sin bloqueo de fila a propósito); agotados los reintentos, DLT.
  - `quality.control` es de Quality (siguiente sprint) y **no existe todavía**: Production no lo crea, arranca sin él y empieza a leerlo cuando aparezca (`TopicWatcher` de `shared-messaging`, revisa cada 15 s con `listTopics`). Declara su `quality.control.DLT`. Por ese tópico también llega `QualityControlCompleted` (para analytics, con otra clave): se ignora.
- Los reintentos son 5 con espera creciente (1 s ×2 hasta 30 s); luego `<tópico>.DLT` con la misma clave.

## Decisiones tomadas (2026-10-20, respondidas por el usuario)
- Los hijos de una división **heredan el `qualityStatus` del padre**.
- Evidencias: **JPEG, PNG y WebP, máximo 10 MB** cada una.
- **Traceability ID** = `TT-` + 10 caracteres aleatorios en Crockford Base32 (alfabeto `0123456789ABCDEFGHJKMNPQRSTVWXYZ`: sin `O`, `I`, `L` ni `U`, para evitar confusiones al dictarlo o escribirlo). El **QR** contiene un código aleatorio distinto del Traceability ID; la imagen la genera el frontend.
- La empresa destino de una transferencia **debe estar `ACTIVE`** (si no, 409).
- **Empresa `INACTIVE` (decisión del usuario, fiel a RF-003)**: se bloquean registrar, dividir, iniciar transformación, iniciar transferencia y aceptar una transferencia entrante; se permiten finalizar una transformación ya iniciada y rechazar una transferencia entrante (ningún lote queda atascado). Ver la regla de arriba.
- `HOLD`/`RELEASE_HOLD` sobre un lote que ya está en el estado pedido: **200 sin cambios ni evento**; 409 solo para transiciones de fase imposibles (ver el comando interno).
- **Rendimiento mínimo por estrategia** (`SPINNING` 60 %, `WEAVING` 80 %, `DYEING` 80 %, configurable), escalas (kg a 3 decimales, porcentajes a 2, reparto por mayor resto) e historial de maquinaria (`machine_status_history`): ver las reglas de arriba.
- **Supuestos aprobados**: `OPERATOR` registra, divide, transforma, transfiere y adjunta evidencias; `COMPANY_ADMIN` gestiona maquinaria y resuelve transferencias; ambos consultan; `LICENSE_OWNER` (sin empresa) recibe 403 en estas rutas. "Misma cadena" (US21) = cualquier otra empresa `ACTIVE` de la proyección. Un QR de otra empresa devuelve solo los campos siempre compartidos (`batchId`, `traceabilityId`, `qrCode`, `companyId`, `materialType`, `phase`, `qualityStatus`, `createdAt`, `finalProduct`). El QR es un código de 26 caracteres Crockford Base32 (130 bits). El `name` del lote es opcional: por defecto el del padre (hijos) o su Traceability ID (registro y salidas). **Máximo 20 caracteres** en el registro, en cada hijo de una división y en cada salida de una transformación (400 `INVALID_BATCH_NAME`); el nombre por defecto se recorta a 20. Regla de V2 que se adopta (2026-11-04): el frontend ya la aplica al escribir y el backend la exige.
