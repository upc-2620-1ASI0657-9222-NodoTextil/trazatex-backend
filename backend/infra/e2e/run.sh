#!/usr/bin/env bash
# Cierre e2e de 06c en un stack AISLADO (proyecto `trazatex-e2e-final-api`): Kafka, PostgreSQL y Neo4j propios + Production, Traceability y Quality reales.
# Nunca toca el docker-compose.yml de la raíz ni sus datos. Sin secretos en el repo: las contraseñas se generan en cada corrida y viven solo
# en el entorno de esta ejecución. Uso: infra/e2e/run.sh [all | guard-test | down]   (ver README.md)
set -euo pipefail

PROJECT="trazatex-e2e-final-api"          # fijo: ningún argumento ni variable lo cambia
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
COMPOSE_FILE="$HERE/docker-compose.yml"

compose() { docker compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"; }
refuse() { echo "RECHAZADO: $*" >&2; exit 2; }

# ---------------------------------------------------------------------------------------------------------------------------------------
# El ÚNICO camino que borra volúmenes. Se niega para cualquier proyecto que no sea trazatex-e2e y comprueba que todo lo que va a borrar
# pertenece a ese proyecto (etiqueta com.docker.compose.project de cada contenedor).
safe_down() {
  local project="${1:-}"
  [[ "$project" == "trazatex-e2e-final-api" ]] || refuse "'down -v' solo se permite en el proyecto trazatex-e2e-final-api, no en '${project}'"
  if [[ -n "${COMPOSE_PROJECT_NAME:-}" && "$COMPOSE_PROJECT_NAME" != "trazatex-e2e-final-api" ]]; then
    refuse "COMPOSE_PROJECT_NAME='$COMPOSE_PROJECT_NAME' apunta a otro proyecto"
  fi
  local id label
  for id in $(compose ps -aq 2>/dev/null); do
    label="$(docker ps -a --no-trunc --filter "id=$id" --format '{{.Label "com.docker.compose.project"}}')"
    [[ "$label" == "trazatex-e2e-final-api" ]] || refuse "el contenedor $id pertenece al proyecto '$label'"
  done
  compose down -v --remove-orphans
}

if [[ "${1:-all}" == "guard-test" ]]; then
  # Comprueba la guarda sin tocar Docker: debe rechazar el proyecto real y cualquier otro
  for other in trazatex trazatex-e2e trazatex-e2e-final "" otro-proyecto "trazatex-e2e-final-api-2"; do
    if (safe_down "$other") 2>/dev/null; then echo "FALLA: aceptó borrar volúmenes en '$other'"; exit 1; fi
  done
  if (COMPOSE_PROJECT_NAME=trazatex safe_down trazatex-e2e-final-api) 2>/dev/null; then echo "FALLA: aceptó COMPOSE_PROJECT_NAME=trazatex"; exit 1; fi
  echo "OK: safe_down rechaza trazatex, trazatex-e2e, vacío, otro-proyecto, trazatex-e2e-final-api-2 y COMPOSE_PROJECT_NAME ajeno"
  exit 0
fi
if [[ "${1:-all}" == "down" ]]; then
  safe_down "$PROJECT"
  exit 0
fi
[[ "${1:-all}" == "all" ]] || refuse "uso: run.sh [all | guard-test | down]"

# ---------------------------------------------------------------------------------------------------------------------------------------
WORK="$(mktemp -d "${TMPDIR:-/tmp}/trazatex-e2e-final-api.XXXXXX")"
PIDS=()
STARTED=0
OK=0; BAD=0

for port in 39092 35432 37687 38082 38083 38084; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then refuse "el puerto $port ya está en uso: este stack usa 39092, 35432, 37687, 38082, 38083 y 38084"; fi
done
for jar in production traceability quality; do
  [[ -f "$ROOT/$jar/target/$jar-0.1.0-SNAPSHOT-exec.jar" ]] || refuse "falta $jar/target/$jar-0.1.0-SNAPSHOT-exec.jar: ./mvnw -pl production,traceability,quality -am package -DskipTests"
done

cleanup() {
  local code=$?
  for pid in "${PIDS[@]:-}"; do [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true; done
  if [[ "$STARTED" == "1" ]]; then safe_down "$PROJECT" >/dev/null 2>&1 || echo "AVISO: no se pudo destruir el stack; ejecuta infra/e2e/run.sh down" >&2; fi
  echo "Logs de los servicios: $WORK/logs (se pueden borrar)"
  exit $code
}
trap cleanup EXIT
mkdir -p "$WORK/logs"

# Contraseñas de esta corrida: aleatorias, solo en memoria
E2E_PG_PASSWORD="$(openssl rand -hex 16)"; E2E_NEO4J_PASSWORD="$(openssl rand -hex 16)"
export E2E_PG_PASSWORD E2E_NEO4J_PASSWORD

check() { if [[ "$2" == "$3" ]]; then echo "  OK    $1: $2"; OK=$((OK+1)); else echo "  FALLA $1: esperado '$3', fue '$2'"; BAD=$((BAD+1)); fi; }
wait_for() {  # descripción, comando que imprime lo observado, valor esperado, segundos
  local desc="$1" cmd="$2" want="$3" limit="${4:-60}" got="" end
  end=$((SECONDS+limit))
  while (( SECONDS < end )); do got="$(eval "$cmd" 2>/dev/null || true)"; [[ "$got" == "$want" ]] && break; sleep 1; done
  check "$desc" "$got" "$want"
}
py() { python3 -c "import sys,json; d=json.load(sys.stdin); print(d$1)"; }
db() { compose exec -T -e PGPASSWORD="$E2E_PG_PASSWORD" postgres psql -h 127.0.0.1 -U e2e -d "$1" -Atc "$2"; }

echo "== Levantando el stack aislado ($PROJECT)"
STARTED=1
compose up -d --wait kafka postgres neo4j >/dev/null
kcmd() { compose exec -T kafka "$@"; }
kcmd /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --create --topic orgaccess.company --partitions 1 --replication-factor 1 >/dev/null

JAVA_COMMON=(env -i PATH="$PATH" HOME="$HOME" KAFKA_BOOTSTRAP_SERVERS=localhost:39092 CLOUDINARY_URL="")
start_service() {  # nombre, puerto, variables...
  local name="$1" port="$2" counter n; shift 2
  # un log por arranque: reiniciar un servicio no pisa el log del anterior (bash 3.2 de macOS: sin arreglos asociativos)
  counter="STARTS_$name"; n=$(( ${!counter:-0} + 1 )); printf -v "$counter" '%s' "$n"
  "${JAVA_COMMON[@]}" SERVER_PORT="$port" "$@" java -jar "$ROOT/$name/target/$name-0.1.0-SNAPSHOT-exec.jar" >"$WORK/logs/$name-$n.log" 2>&1 &
  PIDS+=("$!")
}
wait_up() {  # puerto
  for _ in $(seq 1 90); do [[ "$(curl -s -o /dev/null -w '%{http_code}' "localhost:$1/api/none" 2>/dev/null)" != "000" ]] && return 0; sleep 1; done
}
# Retraso del grupo de consumo (suma de LAG); "sin-grupo" si aún no hay offsets confirmados
group_lag() { kcmd /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:29092 --describe --group "$1" 2>/dev/null \
  | awk 'NR>1 && $6 ~ /^[0-9]+$/ {s+=$6; n++} END {if (n>0) print s; else print "sin-grupo"}'; }
echo "== Arrancando Production (38082), Traceability (38084) y Quality (38083)"
start_service production 38082 DB_URL=jdbc:postgresql://localhost:35432/production_db DB_USER=e2e DB_PASSWORD="$E2E_PG_PASSWORD" \
  TRAZATEX_MESSAGING_RELAY_DELAY=PT0.5S TRAZATEX_MESSAGING_CONSUMER_QUALITY_TOPIC_CHECK_INTERVAL=PT1S
start_traceability() {
  start_service traceability 38084 NEO4J_URI=bolt://localhost:37687 NEO4J_USER=neo4j NEO4J_PASSWORD="$E2E_NEO4J_PASSWORD" \
    TRAZATEX_MESSAGING_CONSUMER_TOPIC_CHECK_INTERVAL=PT1S TRAZATEX_MESSAGING_RELAY_DELAY=PT0.5S
  TRACEABILITY_PID="${PIDS[${#PIDS[@]}-1]}"
}
start_traceability
start_quality() {
  start_service quality 38083 DB_URL=jdbc:postgresql://localhost:35432/quality_db DB_USER=e2e DB_PASSWORD="$E2E_PG_PASSWORD" PRODUCTION_URL=http://localhost:38082 \
    TRAZATEX_MESSAGING_CONSUMER_TOPIC_CHECK_INTERVAL=PT1S TRAZATEX_MESSAGING_RELAY_DELAY=PT0.5S TRAZATEX_QUALITY_START_JOB_DELAY=PT3S
  QUALITY_PID="${PIDS[${#PIDS[@]}-1]}"
}
start_quality
for p in 38082 38083 38084; do wait_up "$p"; done

# ---- identidades (el Gateway las inyectaría; aquí se mandan directo a cada servicio) ----
A=00000000-0000-0000-0000-0000000000a1; B=00000000-0000-0000-0000-0000000000b1
OP_A=00000000-0000-0000-0000-00000000a0a1; ADMIN_A=00000000-0000-0000-0000-00000000a0ad; ADMIN_B=00000000-0000-0000-0000-00000000b0ad
call() {  # servicio-puerto método ruta usuario rol empresa [cuerpo]
  local port="$1" method="$2" path="$3" user="$4" role="$5" company="$6" body="${7:-}"
  curl -s -w '\n%{http_code}' -X "$method" "localhost:$port$path" -H "X-User-Id: $user" -H "X-User-Role: $role" -H "X-Company-Id: $company" \
    -H 'Content-Type: application/json' ${body:+-d "$body"}
}
body_of() { sed '$d'; }
code_of() { tail -1; }
production_as_operator() { call 38082 "$1" "$2" $OP_A OPERATOR $A "${3:-}"; }
quality_as_admin() { call 38083 "$1" "$2" $ADMIN_A COMPANY_ADMIN $A "${3:-}"; }

echo "== Empresas A y B por Kafka (sustituyen a organization-access)"
for pair in "$A|Hilandería A" "$B|Tintorería B"; do
  id="${pair%%|*}"; name="${pair##*|}"
  python3 - "$id" "$name" <<'PY' | kcmd sh -c "/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic orgaccess.company --reader-property parse.key=true --reader-property key.separator='|'" 2>/dev/null
import sys, json, uuid, datetime
cid, name = sys.argv[1], sys.argv[2]
event = {"eventId": str(uuid.uuid4()), "type": "CompanyRegistered", "aggregateId": cid, "version": 1,
         "occurredAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "payload": {"companyId": cid, "ruc": "20100070970", "businessName": name,
         "activities": ["SPINNING"], "status": "ACTIVE"}}
print(cid + "|" + json.dumps(event))
PY
done
wait_for "Production conoce las dos empresas" "db production_db 'SELECT count(*) FROM company_projection'" 2 60

echo "== Production: un lote se divide en cuatro hijos (D1 falla, D2 se transfiere, D3 y D4 se quedan)"
X="$(production_as_operator POST /api/batches '{"name":"Algodón E2E","materialType":"Fibra","composition":[{"name":"Algodón","percentage":100}],"quantityKg":100,"origin":"Piura","supplierName":"Norte","receptionCharacteristics":"limpia"}' | body_of | py "['id']")"
SPLIT="$(production_as_operator POST "/api/batches/$X/split" '{"children":[{"name":"D1","quantityKg":25},{"name":"D2","quantityKg":25},{"name":"D3","quantityKg":25},{"name":"D4","quantityKg":25}]}' | body_of)"
D1="$(echo "$SPLIT" | py "['children'][0]['id']")"; D2="$(echo "$SPLIT" | py "['children'][1]['id']")"
D3="$(echo "$SPLIT" | py "['children'][2]['id']")"; D4="$(echo "$SPLIT" | py "['children'][3]['id']")"
wait_for "Quality conoce a los cuatro hijos (proyección por Kafka)" "db quality_db \"SELECT count(*) FROM batch_projection WHERE quality_status IS NOT NULL\"" 5 60

echo "== Traceability se detiene con la genealogía al día: así el análisis ocurrirá a mi hora y no en una carrera con el HOLD de Quality"
wait_for "Traceability consumió todos los eventos de lotes" "group_lag traceability.production-batch" 0 60
kill "$TRACEABILITY_PID"; wait "$TRACEABILITY_PID" 2>/dev/null || true

echo "== Quality: D1 se controla, falla y se descarta (US23, US24, US25, US26, US27)"
R="$(quality_as_admin POST "/api/quality/batches/$D1/controls")"; check "iniciar el control de D1 (Production real)" "$(echo "$R" | code_of)" 201
K1="$(echo "$R" | body_of | py "['id']")"
check "Production puso D1 en IN_QUALITY_CONTROL" "$(db production_db "SELECT current_stage FROM batches WHERE id='$D1'")" IN_QUALITY_CONTROL
check "prueba registrada" "$(quality_as_admin POST "/api/quality/controls/$K1/tests" '{"criterionName":"Humedad","expectedValue":"8","obtainedValue":"15","unit":"%","testResult":"FAILED"}' | code_of)" 201
R="$(quality_as_admin POST "/api/quality/controls/$K1/finish")"; check "finalizar con falla (RELEASE real)" "$(echo "$R" | code_of)" 200
F1="$(echo "$R" | body_of | py "['failure']['id']")"
check "Production devolvió D1 a AVAILABLE con retención" "$(db production_db "SELECT current_stage || ' ' || quality_hold FROM batches WHERE id='$D1'")" "AVAILABLE true"
wait_for "Production copió la calidad FAILED (QualityStatusChanged por Kafka)" "db production_db \"SELECT quality_status FROM batches WHERE id='$D1'\"" FAILED 60
check "descartar sin causa ni cantidad: 409" "$(quality_as_admin POST "/api/quality/failures/$F1/discard" | code_of)" 409
check "completar la falla" "$(quality_as_admin PUT "/api/quality/failures/$F1" '{"rootCause":"Humedad excesiva","affectedQtyKg":12.5,"observations":"lote húmedo"}' | code_of)" 200
check "descartar (DISCARD real)" "$(quality_as_admin POST "/api/quality/failures/$F1/discard" | code_of)" 200
check "Production descartó D1" "$(db production_db "SELECT current_stage FROM batches WHERE id='$D1'")" DISCARDED
check "falla CONFIRMED" "$(db quality_db "SELECT status FROM quality_failure WHERE id='$F1'")" CONFIRMED

echo "== Con Traceability caído nadie puede avisar a Quality: se detiene Quality con FailureConfirmed ya publicado"
wait_for "FailureConfirmed publicado por el outbox de Quality" "db quality_db \"SELECT count(*) FROM outbox WHERE event_type='FailureConfirmed' AND status='PUBLISHED'\"" 1 30
kill "$QUALITY_PID"; wait "$QUALITY_PID" 2>/dev/null || true
echo "== Traceability vuelve y analiza con D2 todavía disponible; se espera el aviso en el tópico antes de tocar D2"
start_traceability
wait_up 38084
wait_for "Traceability publicó PossibleDerivedFailureDetected" "kcmd /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic traceability.impact --from-beginning --timeout-ms 3000 | grep -c PossibleDerivedFailureDetected" 1 120
check "ninguna retención de Quality sobre D2 mientras Quality está caído" "$(db production_db "SELECT quality_hold::text FROM batches WHERE id='$D2'")" false
echo "== D2 entra en una transferencia mientras Quality sigue caído"
TRANSFER_REPLY="$(production_as_operator POST /api/transfers "{\"targetCompanyId\":\"$B\",\"batchIds\":[\"$D2\"]}")"
TRANSFER="$(echo "$TRANSFER_REPLY" | body_of | py "['id']" 2>/dev/null || true)"
[[ -n "$TRANSFER" ]] || { echo "  FALLA no se pudo iniciar la transferencia de D2: $TRANSFER_REPLY"; BAD=$((BAD+1)); }
check "D2 en transferencia en Production" "$(db production_db "SELECT current_stage FROM batches WHERE id='$D2'")" IN_TRANSFER
echo "== Quality vuelve: consume el aviso y los cambios del lote (sin orden entre tópicos)"
start_quality
wait_up 38083

echo "== FailureConfirmed → Traceability → PossibleDerivedFailureDetected → Quality retiene D3 y D4 (US34)"
for d in "$D3" "$D4"; do
  wait_for "aviso de $d aplicado (HOLD real)" "db quality_db \"SELECT lock_status FROM impacted_batch WHERE failure_id='$F1' AND batch_id='$d'\"" APPLIED 90
  check "  Quality: calidad de $d" "$(db quality_db "SELECT quality_status || ' ' || quality_hold FROM batch_projection WHERE batch_id='$d'")" "POTENTIAL_DERIVED_FAILURE true"
  wait_for "  Production: retención de $d" "db production_db \"SELECT quality_hold::text FROM batches WHERE id='$d'\"" true 30
done
check "tipo de impacto (otra rama del antecesor común)" "$(db quality_db "SELECT impact_type FROM impacted_batch WHERE failure_id='$F1' AND batch_id='$D3'")" RELATED_BRANCH

echo "== D2 en transferencia: el aviso queda PENDING sin cambiar su calidad (decisión del usuario)"
wait_for "aviso de D2 registrado y sin aplicar" "db quality_db \"SELECT lock_status FROM impacted_batch WHERE failure_id='$F1' AND batch_id='$D2'\"" PENDING 90
check "D2 conserva su calidad en Quality" "$(db quality_db "SELECT quality_status || ' ' || quality_hold FROM batch_projection WHERE batch_id='$D2'")" "NOT_REVIEWED false"
check "D2 sin retención en Production" "$(db production_db "SELECT quality_hold::text FROM batches WHERE id='$D2'")" false
check "la empresa B acepta la transferencia" "$(call 38082 POST "/api/transfers/$TRANSFER/accept" $ADMIN_B COMPANY_ADMIN $B | code_of)" 200
wait_for "al llegar BatchPhaseChanged→AVAILABLE, el HOLD se aplica" "db quality_db \"SELECT lock_status FROM impacted_batch WHERE failure_id='$F1' AND batch_id='$D2'\"" APPLIED 60
check "D2 retenido en la empresa receptora (Quality)" "$(db quality_db "SELECT company_id || ' ' || quality_status || ' ' || quality_hold FROM batch_projection WHERE batch_id='$D2'")" "$B POTENTIAL_DERIVED_FAILURE true"
check "D2 retenido en la empresa receptora (Production)" "$(db production_db "SELECT company_id || ' ' || current_stage || ' ' || quality_hold FROM batches WHERE id='$D2'")" "$B AVAILABLE true"

echo "== US34, revisión con Production real: D3 conforme se desbloquea; D4 con falla genera su propia falla"
R="$(quality_as_admin POST "/api/quality/batches/$D3/controls")"; check "iniciar la revisión de D3 (lote retenido)" "$(echo "$R" | code_of)" 201
check "  es una Evaluación (nunca fue controlado)" "$(echo "$R" | body_of | py "['controlType']")" EVALUATION
K3="$(echo "$R" | body_of | py "['id']")"
quality_as_admin POST "/api/quality/controls/$K3/tests" '{"criterionName":"Humedad","expectedValue":"8","obtainedValue":"7","unit":"%","testResult":"CONFORMING"}' >/dev/null
check "finalizar conforme" "$(quality_as_admin POST "/api/quality/controls/$K3/finish" | code_of)" 200
# Ronda 5b: en el primer intento esta comprobación síncrona vio «CONFORMING true» aunque Production ya había liberado el lote; se espera hasta 30 s
# a la proyección de Quality (consistencia eventual). Si tarda, sigue siendo un hallazgo a revisar (ver PENDIENTES_E2E.md).
SECONDS_BEFORE=$SECONDS
wait_for "D3: Quality lo ve Conforme y sin retención" "db quality_db \"SELECT quality_status || ' ' || quality_hold FROM batch_projection WHERE batch_id='$D3'\"" "CONFORMING false" 30
echo "  (la proyección de D3 tardó $((SECONDS-SECONDS_BEFORE)) s en reflejar el desbloqueo)"
check "D3: Production lo desbloqueó" "$(db production_db "SELECT current_stage || ' ' || quality_hold FROM batches WHERE id='$D3'")" "AVAILABLE false"
R="$(quality_as_admin POST "/api/quality/batches/$D4/controls")"; check "iniciar la revisión de D4" "$(echo "$R" | code_of)" 201
K4="$(echo "$R" | body_of | py "['id']")"
quality_as_admin POST "/api/quality/controls/$K4/tests" '{"criterionName":"Humedad","expectedValue":"8","obtainedValue":"15","unit":"%","testResult":"FAILED"}' >/dev/null
R="$(quality_as_admin POST "/api/quality/controls/$K4/finish")"; check "finalizar con falla" "$(echo "$R" | code_of)" 200
F4="$(echo "$R" | body_of | py "['failure']['id']")"
check "D4 genera su propia falla (otra que la de D1)" "$([[ "$F4" != "$F1" ]] && echo distinta)" distinta
check "D4: Quality lo ve Con falla y retenido" "$(db quality_db "SELECT quality_status || ' ' || quality_hold FROM batch_projection WHERE batch_id='$D4'")" "FAILED true"
check "D4: Production lo mantiene retenido" "$(db production_db "SELECT quality_hold::text FROM batches WHERE id='$D4'")" true

echo "== Los eventos de Quality salieron una sola vez (outbox publicado)"
wait_for "outbox de Quality sin pendientes" "db quality_db \"SELECT count(*) FROM outbox WHERE status <> 'PUBLISHED'\"" 0 30
check "un FailureConfirmed" "$(db quality_db "SELECT count(*) FROM outbox WHERE event_type='FailureConfirmed'")" 1

echo; echo "RESULTADO: $OK bien, $BAD con falla"
[[ "$BAD" == "0" ]]
