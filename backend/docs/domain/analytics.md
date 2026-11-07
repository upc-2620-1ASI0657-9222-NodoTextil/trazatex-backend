# Analytics — dominio (implementado)

Fuentes: US37–US40 (EP05); `communication.md` (catálogo), `overview.md`, `data.md`, `api.md`, ADR-004, ADR-008. No hay una figura del informe para este contexto; lo que sigue es lo implementado y las decisiones del usuario (2026-10 y 2026-11-04).

Analytics es el **lado de lectura (CQRS)**: no tiene comandos de negocio ni publica eventos; solo consume los de Production y Quality, los guarda como hechos y responde consultas por periodo. El reporte PDF ya no es suyo (ADR-011: pasó a Quality).

## Modelo de lectura

Una tabla de **hechos** con todos los eventos tal como llegaron y tres tablas pequeñas con el estado de cada lote. La agregación se hace en SQL al consultar; no se precalculan contadores (con los volúmenes del curso no hace falta y complican la idempotencia).

| Tabla | Clave | Se alimenta de | Para |
|---|---|---|---|
| `analytics_event` | `event_id` | los cinco tópicos que consume (ver abajo), **todos** los tipos, tal cual | US38, US39, US40 y la reconstrucción |
| &nbsp;&nbsp;`company_id` | | empresa a la que el indicador atribuye el hecho: `companyId` del payload; en `TransferInitiated`/`Accepted`/`Rejected`, **`fromCompanyId`** (la de origen, I8) | filtrar sin leer el JSON (M9) |
| &nbsp;&nbsp;`business_at` | | cuándo ocurrió el hecho de negocio: `completedAt` (`TransformationCompleted`), `finishedAt` (`QualityControlCompleted`), `acceptedAt` (`TransferAccepted`); `occurred_at` en los demás | el rango del periodo (M9) |
| `batch_birth` | `batch_id` | `BatchRegistered`, `BatchSplit` (cada hijo), `TransformationCompleted` (cada salida) | US37: el universo del periodo (`registered_at`) |
| `batch_owner` | `(event_id, batch_id)` | los nacimientos (`batch_version = -1`) y `BatchPhaseChanged` (con su `batchVersion`) | US37: la empresa dueña al cierre |
| `batch_quality` | `(event_id, batch_id)` | nacimiento (`NOT_REVIEWED`, o el `qualityStatus` del hijo en `BatchSplit`) y `QualityStatusChanged` | US37: el estado al cierre |

Índices (M9): `analytics_event(company_id, event_type, business_at)`, `batch_owner(batch_id, batch_version DESC, occurred_at DESC, event_id DESC)`, `batch_birth(registered_at)`. `AnalyticsMigrationIT` comprueba con `EXPLAIN` que las consultas los usan.

**Idempotencia.** `analytics_event.event_id` es la clave: un evento repetido (Kafka entrega «al menos una vez») no cambia nada. No hay `processed_events` aparte.

**Orden (I9).** Los tópicos llegan en cualquier orden entre sí y el reloj de cada servicio no es monótono, así que **la empresa dueña de un lote no se decide por la hora**: es la del `BatchPhaseChanged` con mayor `batchVersion` (regla de `communication.md`); la hora y el `event_id` solo desempatan filas de la misma versión. Los eventos de creación (versión −1) nunca pisan una versión mayor. `TransferAccepted` **ya no mueve lotes** (antes lo hacía por reloj): el cambio de dueño de una transferencia llega en los `BatchPhaseChanged` de sus lotes, que Production publica en la misma transacción. El estado de calidad es el último `QualityStatusChanged` por `occurred_at` y `event_id`: ese evento no trae versión, pero sale por `quality.control` con clave `batchId`, así que sus cambios de un lote llegan en orden. `ProjectionOrderIndependenceIT` entrega el mismo conjunto en 30 órdenes distintos (con entregas repetidas, versiones al revés del reloj) y exige el mismo resultado.

## Indicadores (todos de **la empresa del solicitante**, nunca de otra)

Periodo: `from` y `to` son fechas **inclusivas** en `America/Lima` (de las 00:00 de `from` al final de `to`), máximo **366 días**. El hecho entra en el periodo por su `business_at`.

- **US37 · Calidad — estado AL CIERRE del periodo** *(decisión 2026-11-04)*. Universo: los lotes **registrados dentro del periodo** (incluidos los hijos de una división y las salidas de una transformación) cuya empresa dueña **al cierre** del periodo es la solicitante. Cada uno se cuenta con el **último estado de calidad conocido antes del cierre**. No es «el estado de hoy»: un lote que pasó a `FAILED` después del periodo sigue contando como lo que era al cerrarlo. Cantidad y porcentaje por `QualityStatus`; los porcentajes de una consulta suman 100.
- **US38 · Fallas — fallas REGISTRADAS** *(decisión 2026-11-04; corrige la versión anterior de este documento, que decía «confirmadas»)*. Cuenta **cada falla que Quality informó** (`QualityFailureUpdated`, en cualquier estado: `OPEN`, `CONFIRMED` o `RESOLVED`), **una sola vez**, en el periodo de su **primer evento**, agrupada por la **causa que tenía al cierre**; sin causa todavía, «Causa por registrar». Una falla abierta y luego resuelta sigue siendo una falla registrada. `FailureConfirmed` no se usa: solo cubría descartes.
- **US39 · Tiempos** *(decisión 2026-11-04)*. Duración promedio, en horas, de las operaciones **finalizadas** en el periodo: transformaciones (`completedAt − startedAt`) y controles de calidad (`finishedAt − startedAt`) de la empresa; y **transferencias aceptadas**, **solo para la empresa de origen**, del `TransferInitiated` al `acceptedAt`. Una transferencia rechazada no tiene duración y no cuenta; la empresa destino no la cuenta tampoco.
- **US40 · Pérdidas**: merma (`shrinkageKg`) y desperdicio (`wasteKg`) **acumulados** de las transformaciones finalizadas en el periodo, y cuántas son.

## API (por el Gateway)

`GET /api/analytics/summary?from=AAAA-MM-DD&to=AAAA-MM-DD` *(I6: antes `GET /api/analytics`, ya no existe)* → un solo documento:

```json
{ "from": "2026-10-01", "to": "2026-10-31", "timeZone": "America/Lima",
  "quality":      [{ "status": "CONFORMING", "count": 1, "percentage": 50.00 }],
  "failureCauses":[{ "cause": "Fibra corta", "count": 2 }],
  "durations":    [{ "operation": "Transferencias", "count": 1, "averageHours": 3.000 }],
  "materials":    { "shrinkageKg": 1.5, "wasteKg": 3, "completedTransformations": 2 },
  "generatedAt": "2026-11-04T15:00:00Z" }
```

Solo `COMPANY_ADMIN`. La empresa sale **siempre** de `X-Company-Id`: ningún parámetro puede pedir otra. Periodo sin datos → listas vacías y ceros, nunca un error. Errores, todos con `ApiError` (M1, M2):

| Estado | `code` | Cuándo |
|---|---|---|
| `400` | `INVALID_PERIOD` | falta `from` o `to`, una fecha mal escrita, `from > to`, o más de 366 días |
| `401` | `AUTHENTICATION_REQUIRED` | cabeceras de identidad ausentes o inválidas |
| `403` | `FORBIDDEN` | `OPERATOR` o `LICENSE_OWNER` |
| `404` / `405` | `NOT_FOUND` / `METHOD_NOT_ALLOWED` | otra ruta u otro método (la ruta antigua `/api/analytics` es un `404`) |
| `500` | `INTERNAL_ERROR` | error inesperado (el detalle va al log) |

## Mensajería

Consume `production.batch`, `production.transformation`, `production.transfer`, `quality.control` y `quality.failure`. **Un grupo y un contenedor por tópico** (`analytics.production-batch`, `analytics.production-transformation`, `analytics.production-transfer`, `analytics.quality-control`, `analytics.quality-failure`); arrancan con el `TopicWatcher` de `shared-messaging` cuando su tópico existe (I13). Un mensaje ilegible o sin los datos que necesita (`MalformedEventException`) va a la `.DLT` de su tópico sin reintentos y sin dejar nada a medias: la transacción se revierte, incluida la fila de `analytics_event`. Los grupos anteriores (`analytics.events`) quedan sin uso: los nuevos leen desde el principio y los eventos ya guardados se descartan por `event_id`.

**No consume** `traceability.impact` (`PossibleDerivedFailureDetected`): ningún indicador lo necesita, porque el cambio de calidad ya llega por `QualityStatusChanged`.

## Reconstrucción

La base de Analytics se puede vaciar y volver a construir leyendo otra vez los tópicos (`retention.ms=-1` en los de Production y Quality): ver [`operations/analytics-rebuild.md`](../operations/analytics-rebuild.md). `AnalyticsRebuildKafkaIT` lo hace con Kafka y PostgreSQL reales y exige que las cuatro tablas y los indicadores queden **idénticos**.

## Decisiones

| # | Decisión | Resuelta |
|---|---|---|
| 1 | Inicio de una transferencia (US39): Analytics consume `TransferInitiated` y usa su `occurredAt`; Production no cambia | 2026-10 |
| 2 | A qué empresa cuenta una transferencia: **solo a la de origen**, y solo si fue aceptada | 2026-11-04 |
| 3 | «En el periodo» de US37: lotes registrados en el periodo, con su **estado al cierre** y la dueña al cierre | 2026-11-04 |
| 4 | Zona horaria y límites: fechas inclusivas en `America/Lima`, máximo 366 días, sin valores por omisión (el front los envía) | 2026-10 |
| 5 | Dueño de Licencia: `403` (las historias no le dan indicadores) | 2026-10 |
| 6 | `PossibleDerivedFailureDetected`: no se consume (ver arriba) | 2026-10 |
| 7 | US38 cuenta fallas **registradas**, no solo confirmadas | 2026-11-04 |
