#!/bin/sh
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

KAFKA_HEAP_OPTS='-Xmx128m' "$BIN/kafka-console-consumer.sh" --bootstrap-server "$BOOTSTRAP" --topic "$DLT" --from-beginning --timeout-ms 10000 \
  --property print.key=true --property key.separator="$TAB" 2>/dev/null | grep "^${KEY}${TAB}" | sort -u > "$FOUND" || true

COUNT=$(wc -l < "$FOUND" | tr -d ' ')
if [ "$COUNT" -eq 0 ]; then
  echo "No hay mensajes con la clave $KEY en $DLT" >&2
  exit 1
fi

KAFKA_HEAP_OPTS='-Xmx128m' "$BIN/kafka-console-producer.sh" --bootstrap-server "$BOOTSTRAP" --topic "$TOPIC" \
  --property parse.key=true --property key.separator="$TAB" < "$FOUND"

echo "Reenviados $COUNT mensajes de $DLT a $TOPIC con la clave $KEY"
