# Reconstruir Analytics desde Kafka

Analytics es el lado de lectura (CQRS) y **no tiene datos propios**: todo lo que guarda sale de los eventos de Production y Quality. Si su base se daña, queda desfasada o cambia su esquema, se vacía y se vuelve a construir leyendo los tópicos desde el principio. Sirve también para comprobar que las proyecciones siguen siendo reproducibles.

> Sustituye a `demo/reconcile.mjs` (retirado el 2026-11-04). Aquel script leía los `outbox` de Production y Quality y escribía por SQL en las bases de Quality, Analytics y Notification: rompía «database per service», saltaba el código del servicio (los datos quedaban como los dejaba el script, no como los dejaría Analytics) y no dejaba prueba alguna de que el resultado fuera el correcto. Aquí lo único que se toca es la base de Analytics, y quien reconstruye es el propio servicio.

## Qué hace falta

- Los tópicos conservan todo: `production.batch`, `production.transformation`, `production.transfer`, `quality.control` y `quality.failure` se declaran con `retention.ms=-1` (Production y Quality). Si algún día se borraran mensajes, esos hechos no se podrían recuperar: **verificarlo antes** (`kafka-topics.sh --describe --topic <tópico>`).
- **Con Azure Event Hubs Standard esto no se cumple**: retiene como máximo 7 días y la retención se fija al crear el event hub (el mismo riesgo que ya tiene Traceability, `progress.md`). Allí una reconstrucción solo recuperaría la última semana; el procedimiento vale para Kafka con retención infinita (el `compose.yaml` del proyecto).
- Poder parar el servicio `analytics` (nadie más lee su base).

## Procedimiento

Todos los comandos desde la raíz del proyecto (donde está `compose.yaml`).

1. **Parar el servicio entero** (no solo sus contenedores de Kafka: el vigilante de tópicos reiniciaría los que se detengan a los 15 s).
   ```
   docker compose stop analytics
   ```
2. **Vaciar su base** (solo `analytics_db`):
   ```
   docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d analytics_db -v ON_ERROR_STOP=1 -c "TRUNCATE analytics_event, batch_birth, batch_owner, batch_quality"'
   ```
   No se toca `flyway_schema_history`: el esquema se conserva.
3. **Rebobinar los cinco grupos** al principio de su tópico (con el servicio parado los grupos están vacíos; Kafka lo exige):
   ```
   for par in production.batch:analytics.production-batch production.transformation:analytics.production-transformation \
              production.transfer:analytics.production-transfer quality.control:analytics.quality-control quality.failure:analytics.quality-failure; do
     docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 \
       --group "${par#*:}" --topic "${par%%:*}" --reset-offsets --to-earliest --execute
   done
   ```
4. **Arrancar el servicio** y esperar:
   ```
   docker compose up -d --wait analytics
   ```
   Lee los cinco tópicos de principio a fin. El avance se ve en el retraso (`lag`) de los grupos:
   ```
   docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --describe --group analytics.production-batch
   ```
   (repetir con los otros cuatro). Terminó cuando todos tienen `LAG 0`.
5. **Comprobar**: `SELECT count(*) FROM analytics_event` es el número de eventos distintos de los cinco tópicos, y la pantalla de Analítica da lo mismo que antes. Un mensaje que Analytics no pueda leer queda en la `.DLT` de su tópico (`production.batch.DLT`, ...) y no detiene a los demás.

## Por qué da el mismo resultado

- **Idempotente por `event_id`**: reprocesar un evento no cambia nada.
- **Independiente del orden**: los cinco tópicos se leen en paralelo y en cualquier orden entre sí. La empresa dueña de un lote sale de la mayor `batchVersion`, no de la hora, y los nacimientos nunca pisan una versión mayor (`communication.md`, `domain/analytics.md`).
- **Sin dependencias externas**: el modelo de lectura no consulta a otro servicio ni usa la hora de proyección (`projected_at` es la única columna que cambia, y ningún indicador la lee).

## Prueba

`AnalyticsRebuildKafkaIT` (Kafka y PostgreSQL reales) automatiza los pasos 1 a 4 con las mismas herramientas de Kafka (`AdminClient.alterConsumerGroupOffsets` es lo que hace `--reset-offsets`): publica un mes de operación de dos empresas, deja que Analytics lo consuma, toma una fotografía (las cuatro tablas fila por fila, menos `projected_at`, y los indicadores de ambas empresas), para el servicio, vacía su base, rebobina los cinco grupos, lo arranca y exige que la fotografía sea **idéntica**. El historial incluye una entrega repetida, un cambio de dueño con la versión al revés del reloj, transferencias aceptada y rechazada, un control y fallas.

## Alcance

Esto reconstruye **Analytics**. `demo/reconcile.mjs` también reparaba `batch_projection` de Quality y `user_projection` de Notification; esas dos no tienen aún un procedimiento probado equivalente (se anota en `progress.md`).
