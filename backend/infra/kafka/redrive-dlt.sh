#!/bin/sh
# Reenvía a su tópico original los mensajes de una DLT cuya clave es la indicada (por ejemplo, un FailureConfirmed que agotó sus reintentos
# porque aún faltaban eventos de Production). Corre DENTRO del contenedor de Kafka, con sus propias herramientas.
#
# Uso: redrive-dlt.sh <bootstrap> <topic.DLT> <topic> <key>
#   bootstrap  dónde escucha el broker visto desde dentro del contenedor (en el docker-compose: localhost:9092)
#   topic.DLT  la DLT de donde leer (p. ej. quality.failure.DLT)
#   topic      el tópico original al que reenviar (p. ej. quality.failure)
#   key        la clave de los mensajes a reenviar (en quality.failure es el failureId)
#
# El mensaje se reenvía idéntico (misma clave y mismo sobre, con su mismo eventId): el consumidor es idempotente, así que reenviarlo dos veces, o reenviar uno que
# ya se procesó, no repite el efecto. Los mensajes siguen en la DLT (no se borran). Si hay varios con esa clave, se reenvían una sola vez cada uno.
set -eu

if [ "$#" -ne 4 ]; then
  echo "Uso: $0 <bootstrap> <topic.DLT> <topic> <key>" >&2
  exit 2
fi
BOOTSTRAP=$1
DLT=$2
TOPIC=$3
KEY=$4
BIN=/opt/kafka/bin
TAB=$(printf '\t')
FOUND=$(mktemp)
trap 'rm -f "$FOUND"' EXIT

# 1. Lee la DLT completa (clave, tabulador, sobre JSON en una línea) y se queda con lo de esa clave. Termina a los 10 s sin mensajes nuevos.
KAFKA_HEAP_OPTS='-Xmx128m' "$BIN/kafka-console-consumer.sh" --bootstrap-server "$BOOTSTRAP" --topic "$DLT" --from-beginning --timeout-ms 10000 \
  --property print.key=true --property key.separator="$TAB" 2>/dev/null | grep "^${KEY}${TAB}" | sort -u > "$FOUND" || true

COUNT=$(wc -l < "$FOUND" | tr -d ' ')
if [ "$COUNT" -eq 0 ]; then
  echo "No hay mensajes con la clave $KEY en $DLT" >&2
  exit 1
fi

# 2. Los publica en el tópico original con la misma clave
KAFKA_HEAP_OPTS='-Xmx128m' "$BIN/kafka-console-producer.sh" --bootstrap-server "$BOOTSTRAP" --topic "$TOPIC" \
  --property parse.key=true --property key.separator="$TAB" < "$FOUND"

echo "Reenviados $COUNT mensajes de $DLT a $TOPIC con la clave $KEY"
