# Traceability — dominio

Fuentes: RF-053…RF-059, RNF-04, RNF-07, RNF-12; US29–US33, US36; Figura 32 (canvas) y Figura 40 (modelo Neo4j); ADR-002, ADR-006.
Traceability **no** crea lotes, IDs ni QR y no cambia fases: solo refleja lo que publican Production, Quality y Organization & Access.

## Modelo del grafo (Figura 40)

```
(:Company)-[:OWNS_BATCH]->(:Batch)
(:Batch)-[:INPUT_OF]->(:Transformation)-[:PRODUCED]->(:Batch)
(:Batch)-[:SPLIT_INTO]->(:Batch)
(:Batch)-[:TRANSFERRED_VIA {transferId, at}]->(:Company)   // historial, no es genealogía
```

- `:Batch` — siempre compartidos: `batchId`, `traceabilityId`, `qrCode`, `companyId`, `materialType`, `phase`, `qualityStatus`, `createdAt`, `finalProduct`. Configurables: `supplierName`, `origin`, `quantityKg`, `composition`, `receptionCharacteristics`. Más `qualityHold` (de `BatchPhaseChanged`), `provisional` (true si se creó por un evento que llegó antes que `BatchRegistered`) y `batchVersion` (la de `BatchPhaseChanged.batchVersion` del último cambio aplicado; `null` = ninguno todavía). Composición como JSON (una propiedad de Neo4j no puede ser una lista de mapas).
- `:Transformation` — `transformationId`, `companyId` (el filtro B2B decide por la empresa de cada nodo), `type`, `startedAt`, `completedAt`; configurables: `machineName` y `machineType` (foto del momento, de `TransformationCompleted`; el `machineId` no se guarda), `shrinkageKg`, `wasteKg`, `wasteReason`. `wasteReason` viaja en `TransformationCompleted` desde el 2026-10-21 (nulo si no hubo desperdicio).
- `:Company` — `companyId`, `businessName` (de `CompanyRegistered`; nulo hasta que llega), `privacy` (mapa campo → `SHARED`/`PRIVATE`, de `CompanyPrivacyUpdated`, guardado como JSON). **Sin `privacy` o con un campo ausente, el campo es `PRIVATE`** (los configurables son `PRIVATE` por defecto, `organization-access.md`).
- Nunca se guardan datos siempre privados (operario, observaciones internas, datos comerciales): no vienen en los eventos.
- Unicidad: `Batch.batchId`, `Batch.traceabilityId`, `Transformation.transformationId`, `Company.companyId`, `ProcessedEvent.eventId`, `OutboxEvent.eventId`. Índices: `Batch.qrCode` (búsqueda; no único, un lote provisional no lo tiene) y `OutboxEvent(status, createdAt)`.

## Cómo se construye (US29, RF-053)

| Evento | Efecto en el grafo |
|---|---|
| `BatchRegistered` | `MERGE` del `:Batch` y `OWNS_BATCH` desde su empresa |
| `BatchSplit` | Hijos (con su fase inicial) + `SPLIT_INTO` desde el padre. **No cambia la fase del padre** (llega con su `BatchPhaseChanged`) |
| `TransformationCompleted` | `:Transformation`, `INPUT_OF` desde cada entrada y `PRODUCED` hacia cada salida nueva (con su fase inicial). **No cambia la fase de las entradas** (llega con su `BatchPhaseChanged`) |
| `TransferAccepted` | `TRANSFERRED_VIA` hacia la empresa destino (historial); no crea lotes ni relaciones padre-hijo. El cambio de dueño llega por `BatchPhaseChanged` |
| `BatchPhaseChanged` | Si `batchVersion` es mayor que la guardada: actualiza `phase`, `qualityHold` y el dueño (`companyId`, mueve `OWNS_BATCH`) |
| `QualityStatusChanged` | Actualiza `qualityStatus` (en el orden de `quality.control`, clave `batchId`) |
| `CompanyRegistered` | `MERGE` de `:Company` y completa `businessName` si falta (solo usa `companyId` y `businessName`) |
| `CompanyPrivacyUpdated` | Actualiza `Company.privacy` |
| `FailureConfirmed` | Análisis de impacto (abajo) |

Los lotes históricos (`PROCESSED`, `SPLIT`, `DISCARDED`) nunca se borran (RNF-12).

### Orden de llegada (decisión del usuario, 2026-10-21)

Kafka solo ordena una misma clave dentro de un tópico; `production.batch`, `production.transformation` y `production.transfer` llegan sin orden entre sí. El grafo final debe ser el mismo en cualquier orden de llegada y con duplicados, **sin depender del reloj** (`occurredAt` no es estrictamente creciente):
- **Fase, `qualityHold` y dueño** solo cambian con un `BatchPhaseChanged` cuyo `batchVersion` sea **mayor** que el guardado en el nodo (`communication.md`).
- **Los eventos de creación** (`BatchRegistered`, `BatchSplit`, `TransformationCompleted`) solo **completan** un nodo nuevo o provisional (`MERGE` por `batchId`); nunca pisan datos de una versión mayor.
- **Fase de los lotes que ya existen** (el padre de una división, las entradas de una transformación): solo cambia con `BatchPhaseChanged`. Los eventos de negocio crean las relaciones, no mueven la fase.
- **Fase inicial de un lote que nace** (hijo o salida; `AVAILABLE`, sin retención, empresa del evento): sale de su evento de creación **solo si el nodo aún no tiene `batchVersion`**. Si el primer `BatchPhaseChanged` del hijo llegó antes que `BatchSplit`, el nodo ya tiene versión y la creación solo completa lo que falta (códigos, cantidad, relaciones).
- **`qualityStatus` inicial** (el del padre en un hijo, `NOT_REVIEWED` en una salida): lo pone la creación **solo si aún no hay `qualityStatus`**; un `QualityStatusChanged` que llegó antes manda.
- Un nodo deja de ser `provisional` cuando llega su evento de creación.
- **`qualityStatus`** sigue el orden de `quality.control` (una sola clave por lote en un solo tópico).
- Sin desempates por `occurredAt` ni por "fase terminal".

### Nodos incompletos nunca salen en una consulta (decisión del usuario, 2026-10-21; se aplica en 05c)

Un nodo `provisional` —o con algún dato obligatorio todavía ausente (`traceabilityId`, `qrCode`, `materialType`, `companyId`, `phase`, `qualityStatus`, `createdAt`, `finalProduct`)— **nunca aparece en la respuesta de una consulta**: ni como nodo, ni como extremo de una arista, ni en los conteos. Mientras el evento que lo completa no llegue, la consulta se comporta como si ese lote no existiera (y no se muestra una arista hacia un nodo que no se muestra). Aplica a las cuatro consultas (búsqueda, ancestros, descendientes y completa) y también a la privacidad B2B: el filtro de 05d recibe solo nodos completos.

**El recorrido se detiene en un nodo incompleto** (decisión del usuario, 2026-10-21): no lo atraviesa, así que los lotes que solo se alcanzan a través de él tampoco aparecen, y no cuenta para el límite de 10 niveles. La respuesta lleva un indicador de genealogía incompleta (`complete: false`; `true` si el recorrido no se detuvo en ningún nodo incompleto) para que el frontend muestre "en actualización". Con `complete: false` la respuesta es una vista parcial, no un error: se completa sola cuando llegan los eventos que faltan.

### Visibilidad de `qualityHold` (decisión del usuario, 2026-10-21; se aplica en 05d)

`qualityHold` es un **dato interno**: solo lo ve la empresa dueña del lote (es la retención por calidad; otra empresa ve el `qualityStatus`, no si el lote está retenido). El filtro B2B lo oculta siempre a las demás empresas, no es configurable y no está en `ConfigurableField`.

## Consultas (US30–US32, US36)

Decisiones del usuario del 2026-10-21 (plan de 05c):

- **Quién consulta**: `OPERATOR` y `COMPANY_ADMIN`; `LICENSE_OWNER` (sin empresa, y la política de privacidad necesita la del solicitante) → 403. **Cualquier usuario autenticado abre la genealogía de cualquier lote** y de los nodos de otras empresas ve solo lo siempre compartido, como con el QR de Production.
- **Buscar** (`GET /api/traceability/lineages`): `qrCode` por **coincidencia exacta**, para cualquier lote. `traceabilityId` **por prefijo solo sobre los lotes de la propia empresa**; para un lote de otra empresa, **solo coincidencia exacta** (así un prefijo como `TT-` no lista lotes ajenos). Se normaliza lo escrito a mano (mayúsculas, `O`→`0`, `I`/`L`→`1`). Se exige al menos uno; con los dos, deben cumplirse ambos. Paginada (`page` desde 0, `size` por defecto 20 y máximo 100). Solo salen lotes completos.
- **Hacia atrás** (`ancestors`): antecesores por `SPLIT_INTO` e `INPUT_OF`/`PRODUCED` en sentido inverso. **Hacia adelante** (`descendants`): en sentido directo, con sus ramificaciones. **Completa** (`lineage`): ambas direcciones desde el lote (no incluye a los hermanos que cuelgan de un antecesor).
- **Un nivel es un salto de lote a lote**: `SPLIT_INTO` cuenta 1 y `INPUT_OF → PRODUCED` también 1 (la transformación no cuenta). El nivel de un nodo es su **distancia más corta** al lote consultado. Máximo **10 niveles**. Objetivo RNF-04: p95 ≤ 5 s con 100 consultas simultáneas sobre 500 nodos (medido en 05c-4: p95 ≈ 260 ms, 0 errores, por HTTP directo al servicio y con la memoria del compose; ver `progress.md`).
- **Recorrido nivel por nivel**, con un conjunto de nodos ya visitados y sin patrones de longitud variable que enumeren caminos: un grafo con mezclas y divisiones (en forma de diamante) tiene exponencialmente más caminos que nodos y cada nodo se visita una sola vez. Cuesta como máximo 2 consultas a la base por nivel.
- **`truncated`**: `true` si pasado el nivel 10 queda cualquier vecino en esa dirección (completo o no); es aparte de `complete`. En la vista completa, `truncated` es verdadero si lo es en cualquiera de las dos direcciones y `complete` solo si lo es en las dos.
- **Lote de inicio desconocido, provisional o incompleto**: 404 `BATCH_NOT_FOUND`, sin distinguir el motivo (no se filtra que existe).
- **Forma de la respuesta**: `{batchId, direction, maxLevels, complete, truncated, nodes, edges}`. Los `nodes` son lotes (`kind: BATCH`) y transformaciones (`kind: TRANSFORMATION`), cada uno con su `level` (negativo hacia atrás, positivo hacia adelante, 0 el consultado) y `access` (`OWN` | `SHARED`); lleva también `businessName` de la empresa responsable. Las `edges` son `SPLIT_INTO`, `INPUT_OF` y `PRODUCED` (`{type, from, to}`). La vista **completa** agrega a cada lote su `transferHistory` (`[{transferId, toCompanyId, toBusinessName, at}]`), **visible para todos**; ancestros y descendientes llevan solo aristas genealógicas.
- La respuesta devuelve nodos y aristas (para dibujar el grafo en el frontend).
- **Decisiones de 05c-3 (2026-10-21, a pedido del usuario)**: (1) **toda respuesta de `/api/traceability/**` lleva `Cache-Control: no-store`** (lo que se ve cambia según la empresa que consulta): la pone el servicio en un filtro (también en los errores) y el Gateway la impone él mismo, incluso en sus propios errores; (2) un usuario de una **empresa inactiva consulta** (es lectura; el servicio no guarda estados de empresa), sin cabeceras de identidad → 401, `LICENSE_OWNER` → 403; (3) **el mismo 404 en todos los casos**: lote inexistente, provisional o incompleto/ilegible devuelven el mismo cuerpo `BATCH_NOT_FOUND` ("El lote no existe.", sin el id ni pistas; solo difiere `timestamp`); un `batchId` que no es UUID es 400 `INVALID_PARAMETER`, como cualquier parámetro mal formado de la API; (4) lo que la política oculta **no sale ni como `null`** en el JSON (`spring.jackson.default-property-inclusion: non_null`; las listas vacías y los `false` sí salen); (5) ArchUnit: todo handler declara `@RequiresRole` y la capa web no tiene escrituras.

## Privacidad B2B (ADR-006, RNF-07)

**Ninguna respuesta de consulta sale sin pasar por la política** (`LineageAuthorizationPolicy`): lo exige ArchUnit. El filtro se aplica en el servidor, nunca en el frontend.

Para cada nodo de otra empresa (`node.companyId ≠ X-Company-Id`): se devuelven los campos siempre compartidos y solo los configurables que esa empresa marcó `SHARED` en su configuración **vigente**. Los nodos de la propia empresa van completos (incluido `qualityHold`, que **nunca** sale a otra empresa, ni siquiera con todo compartido). La relación (arista) siempre se muestra. Un campo que no se ve **no sale en el JSON** (ni como `null`).

- **Un cambio de privacidad se aplica a todos los nodos de la empresa, incluidos los antiguos, desde que llega `CompanyPrivacyUpdated`** (decisión del usuario, 2026-10-21; el documento lo daba por supuesto —la privacidad es un atributo de la empresa, `Company.privacy`, no del nodo— pero no lo decía). No se guarda una copia de la privacidad en cada lote: se lee de la empresa dueña **en el momento de la consulta**. Por eso un lote registrado antes de que su empresa compartiera un campo lo muestra desde ese momento, y vuelve a ocultarlo en cuanto la empresa lo marca `PRIVATE`; también los lotes que se registren después. Antes de que llegue el primer `CompanyPrivacyUpdated` de una empresa, todo lo configurable es `PRIVATE`.
- **El último mapa reemplaza al anterior** (`CompanyPrivacyUpdated` trae el mapa completo): un campo que falta en el mapa más reciente es `PRIVATE`. Un campo desconocido del mapa se ignora (con un aviso en el log), un valor que no es `SHARED` ni `PRIVATE` se toma como `PRIVATE`, y una privacidad guardada que no se puede leer se trata como todo `PRIVATE`: lo dudoso nunca se comparte.
- **Una consulta, una lectura**: la privacidad de las empresas ajenas del recorrido se lee con **una sola consulta a Neo4j por petición** (como los nombres), no una por empresa ni por nodo; `LineageQueryCountIT` vigila que las consultas por petición dependan de los niveles y no de la cantidad de nodos o empresas.
- **Qué controla cada campo** (`ConfigurableField` → dato): `SUPPLIER` → `supplierName`; `ORIGIN` → `origin`; `QUANTITY` → `quantityKg`; `COMPOSITION` → `composition`; `RECEPTION_CHARACTERISTICS` → `receptionCharacteristics` (lotes); `MACHINERY` → `machineName` y `machineType`; `SHRINKAGE` → `shrinkageKg`; `WASTE` → `wasteKg`; `WASTE_REASON` → `wasteReason` (transformaciones). `FAILURE_CAUSE` → `failureCause` (de `QualityFailureUpdated.cause`) y `QUALITY_TEST_VALUES` → `qualityTests` (de `QualityControlCompleted.tests[]`). **Resuelto** (V2, documentado el 2026-11-03; antes no tenían dato en el grafo).
- **Siempre privado** (nunca sale a otra empresa): la retención por calidad (`qualityHold`), la identidad del operario y las observaciones internas (estas dos no están en el grafo).

## Análisis de impacto ante una falla confirmada (US33, RF-058, RF-059)

Al consumir `FailureConfirmed(batchId)` (tópico `quality.failure`, clave `failureId`) Traceability calcula la **familia** del lote fallado y publica qué lotes requieren revisión preventiva. Es un análisis **interno**: ve los lotes de **todas** las empresas, no pasa por el filtro B2B y su resultado **solo sale por el evento** (ninguna ruta HTTP lo devuelve; ArchUnit lo exige).

1. **Puntos de partida**: el propio lote fallado (nivel 0) **y** sus antecesores, hasta 10 niveles hacia atrás, incluidos los históricos (`PROCESSED`, `SPLIT`, `DISCARDED`: solo sirven para reconstruir). Si el lote fallado no tiene antecesores, sus derivados salen igual.
2. **Hacia adelante** desde **cada** punto de partida, hasta 10 niveles. Es el recorrido por niveles de las consultas (sin enumerar caminos) con todos los puntos de partida a la vez: cuesta como máximo 2 consultas a Neo4j por nivel en total, no una pasada por antecesor.
3. **Qué se marca** (regla literal, decisión del usuario, 2026-10-21): los lotes completos de la familia con `phase = AVAILABLE`, que no sean el lote fallado ni tengan `qualityStatus = FAILED`. `FAILED` es el estado de calidad ("Con falla", glosario) y `DISCARDED` es una fase: esta última ya queda fuera por no ser `AVAILABLE`; lo que excluye `FAILED` es un lote disponible pero retenido por su propia falla (entre el control y el descarte). **No excluyen** `POTENTIAL_DERIVED_FAILURE` (una segunda falla confirmada es información nueva), `PENDING_REEVALUATION`, `CONFORMING`, `NOT_REVIEWED` ni la retención `qualityHold`: Quality decide qué hacer con ellos (ver `quality.md`). Un antecesor que siguiera `AVAILABLE` (dato atrasado) también es de la familia.
4. **`commonAncestorBatchId`** (regla determinista): entre los puntos de partida desde los que se llega al afectado dentro del límite, **el más cercano al lote fallado** (menor nivel hacia atrás; el propio lote fallado es el nivel 0, así que un derivado suyo siempre lo tiene a él); **en un empate, el de menor `batchId`** (texto del UUID canónico). El nivel es la distancia hacia atrás desde el lote fallado, no la distancia al afectado.
5. **Publica** `PossibleDerivedFailureDetected` con `failureId`, `sourceBatchId` y `affected[{batchId, companyId, commonAncestorBatchId}]`, **ordenada por `batchId`**. **Si la lista está vacía, no publica nada.** Por outbox (en la misma transacción de Neo4j que el procesamiento) hacia `traceability.impact` (clave `failureId`, sin borrado por tiempo, `retention.ms=-1`, como los demás); **un `FailureConfirmed` repetido publica una sola vez** (por el `eventId` ya procesado y porque ya existe un evento de ese `failureId`).
6. **Nodos incompletos** (decisión del usuario): si la familia contiene un nodo provisional o incompleto, o el lote fallado aún no está completo, **no se publica un resultado parcial**: se lanza una excepción **reintentable** (`ImpactAnalysisIncompleteException`, que a propósito no es una `TrazatexException` ni una `IllegalArgumentException`, porque esas van a la DLT sin reintentos) y el evento se reintenta, porque los eventos de Production pueden llegar después que `FailureConfirmed`. Reintentos propios de este error: **10, de 1 s hasta 60 s** (configurable, ≈ 5 min en total). Al agotarlos, el mensaje va a `quality.failure.DLT` con un `ERROR` que dice que hay **lotes posiblemente afectados sin retener**, el `failureId`, el lote fallado, los lotes que faltan y cómo reenviarlo (procedimiento en `docs/progress.md`, «Reenvío desde la DLT de quality.failure»: un script que corre dentro del contenedor de Kafka y reenvía el mensaje idéntico al tópico original; repetirlo es seguro y se publica una sola vez). Los reintentos no expulsan al consumidor del grupo: cada espera es una quinta parte de `max.poll.interval.ms` y se valida al arrancar que no pase de la mitad. Un nodo incompleto **fuera** de la familia (p. ej. la otra entrada de una mezcla) no bloquea.
7. **Más de 10 niveles**: se publica lo que entra en el límite y se deja un `WARN` con el `failureId` (los 10 niveles son la especificación).

Traceability no cambia el estado de calidad ni bloquea lotes: Quality marca `POTENTIAL_DERIVED_FAILURE` y le pide a Production `HOLD`; Notification avisa a los administradores.

**Costo medido** (diamante de 500 nodos, Neo4j con la memoria del compose, 15 fallas en lotes de distintas capas): mediana ≈ 20 ms, p95 ≈ 30 ms por análisis, de 13 a 45 consultas a Neo4j (2 por nivel por dirección, no una por nodo ni por antecesor) y hasta 124 afectados en una publicación; cada resultado se verificó contra una implementación ingenua independiente (ver `progress.md`).

**Limitación conocida: descendientes tardíos.** El análisis solo ve lo que Traceability conoce cuando llega `FailureConfirmed`. Un descendiente cuyo evento de Production llegue **después** de que el resultado se publicó **no se reanaliza**: no queda marcado ni retenido por este análisis. Solo se reintenta cuando ya hay un nodo provisional visible en la familia (un evento que nombra un lote cuyo registro aún no llegó); si el evento que crea al descendiente ni siquiera ha llegado, no hay nada que detecte la falta. En la práctica el control de calidad y la confirmación de la falla ocurren mucho después de la producción, así que el riesgo es bajo; si se quisiera cerrar, haría falta reanalizar las fallas confirmadas cuando llega un lote nuevo a una familia ya analizada (no se hace en este sprint).

**Cucumber de US33 (RNF-10).** El escenario 3 ("cambia su calidad a Posible falla derivada y lo bloquea preventivamente") describe un efecto de **otros servicios**: Quality cambia la calidad y Production lo retiene, y ambos llegan en el siguiente sprint. Traceability **no reescribe** el escenario (su texto es el de `user-stories.md`): el paso verifica el evento que lo dispara, es decir, que el lote figura en `PossibleDerivedFailureDetected`. Ver la regla general en `conventions.md`.
