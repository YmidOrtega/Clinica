#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
published() { echo "http://$(docker compose -p "$PROJECT" port --index 1 "$1" "$2" 2>/dev/null)"; }
PATIENT_URL="${PATIENT_URL:-$(published patient-service 8081)}"
CLINICAL_URL="${CLINICAL_URL:-$(published clinical-history-service 8089)}"
[ "$PATIENT_URL" != "http://" ] && [ "$CLINICAL_URL" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define PATIENT_URL y CLINICAL_URL" >&2; exit 1; }
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

DOCTOR_ID=$(cat /proc/sys/kernel/random/uuid)
NURSE_ID=$(cat /proc/sys/kernel/random/uuid)
token() { sh "$DIR/staff-token.sh" "$@"; }
step() { printf '\n== %s\n' "$1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }

call() {
  method=$1; url=$2; role=$3; subject=$4; body=${5:-}; extra=${6:-}
  if [ -n "$body" ]; then
    curl -s -o "$WORK/body" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $(token "$role" "$subject")" \
      -H 'Content-Type: application/json' $extra --data "$body"
  else
    curl -s -o "$WORK/body" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $(token "$role" "$subject")" $extra
  fi
}

expect() {
  [ "$1" = "$2" ] || { cat "$WORK/body" >&2; fail "$3: expected HTTP $2 and got $1"; }
  echo "ok  $3"
}

step "patient-service registra al paciente y lo publica por Debezium"
DOCUMENT=$(date +%s%N | cut -c6-15)
status=$(call POST "$PATIENT_URL/api/v1/patients" RECEPTIONIST "$(cat /proc/sys/kernel/random/uuid)" "{
  \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$DOCUMENT\"},
  \"demographics\": {\"firstNames\": \"Lucía\", \"lastNames\": \"Pérez Gómez\", \"birthDate\": \"1987-05-14\", \"sex\": \"FEMALE\",
                     \"countryOfOrigin\": \"CO\", \"disability\": \"NONE\"},
  \"contact\": {\"mobile\": \"3017654321\"},
  \"affiliation\": {\"regime\": \"UNINSURED\"},
  \"residence\": {\"department\": \"Santander\", \"municipality\": \"Girón\", \"zone\": \"URBAN\", \"address\": \"Calle 1 # 2-3\"}}")
expect "$status" 201 "registro del paciente"
PATIENT=$(jq -r .uuid "$WORK/body")

attempt=0
until docker exec clinical-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" \"\$MYSQL_DATABASE\" -e \"SELECT COUNT(*) FROM patient_references WHERE uuid = '$PATIENT'\" 2>/dev/null" | grep -q '^1$'; do
  attempt=$((attempt + 1)); [ "$attempt" -lt 60 ] || fail "el evento del paciente no llegó a clinical-history-service"; sleep 1
done
echo "ok  clinical-history-service recibió patient.events.v1 en ${attempt}s"

step "Atención con triage, anexo, epicrisis y cierre"
status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters" NURSE "$NURSE_ID" "{\"patientUuid\": \"$PATIENT\", \"type\": \"EMERGENCY\"}")
expect "$status" 201 "apertura de la atención"
ENCOUNTER=$(jq -r .id "$WORK/body")
status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters/$ENCOUNTER/care-team" NURSE "$NURSE_ID" "{\"clinicianUuid\": \"$DOCTOR_ID\", \"role\": \"DOCTOR\"}")
expect "$status" 201 "médico agregado al equipo"

status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters/$ENCOUNTER/drafts" NURSE "$NURSE_ID" "{
  \"content\": {\"type\": \"TRIAGE\", \"level\": \"II\", \"reason\": \"Dolor torácico\"},
  \"updates\": [{\"kind\": \"RECORD_VITAL_SIGNS\", \"measuredAt\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
                 \"readings\": [{\"kind\": \"HEART_RATE\", \"value\": 112}, {\"kind\": \"OXYGEN_SATURATION\", \"value\": 94}]}]}")
expect "$status" 201 "borrador de triage"
TRIAGE=$(jq -r .id "$WORK/body")
printf '%%PDF-1.7\n%% electrocardiograma\n%%%%EOF\n' > "$WORK/ekg.pdf"
status=$(curl -s -o "$WORK/body" -w '%{http_code}' -X POST "$CLINICAL_URL/api/v1/clinical/drafts/$TRIAGE/attachments" \
  -H "Authorization: Bearer $(token NURSE "$NURSE_ID")" -F "file=@$WORK/ekg.pdf;type=application/pdf")
expect "$status" 201 "anexo cifrado en espera"
status=$(call POST "$CLINICAL_URL/api/v1/clinical/drafts/$TRIAGE/signature" NURSE "$NURSE_ID" "" "-H If-Match:\"0\"")
expect "$status" 201 "firma del triage y archivo WORM del anexo"
ATTACHMENT=$(jq -r '.attachments[0].id' "$WORK/body")

status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters/$ENCOUNTER/drafts" DOCTOR "$DOCTOR_ID" "{
  \"content\": {\"type\": \"DISCHARGE\", \"admissionSummary\": \"Dolor torácico\", \"evolutionSummary\": \"Troponinas negativas\",
               \"dischargeCondition\": \"Estable\", \"recommendations\": \"Control\", \"followUp\": \"Cardiología\",
               \"diagnoses\": [{\"code\": \"R072\", \"role\": \"PRINCIPAL\", \"type\": \"IMPRESSION\"}]}}")
if [ "$status" = "503" ] && grep -q TERMINOLOGY_NOT_ACTIVE "$WORK/body"; then
  status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters/$ENCOUNTER/drafts" DOCTOR "$DOCTOR_ID" "{
    \"content\": {\"type\": \"DISCHARGE\", \"admissionSummary\": \"Dolor torácico\", \"evolutionSummary\": \"Troponinas negativas\",
                 \"dischargeCondition\": \"Estable\", \"recommendations\": \"Control\", \"followUp\": \"Cardiología\"}}")
  echo "     (sin catálogo CIE-10 activo: la epicrisis se firma sin diagnóstico y debe fallar por diagnóstico principal)"
  expect "$status" 201 "borrador de epicrisis"
  DISCHARGE=$(jq -r .id "$WORK/body")
  status=$(call POST "$CLINICAL_URL/api/v1/clinical/drafts/$DISCHARGE/signature" DOCTOR "$DOCTOR_ID" "" "-H If-Match:\"0\"")
  expect "$status" 422 "la epicrisis exige diagnóstico principal"
else
  expect "$status" 201 "borrador de epicrisis con CIE-10"
  DISCHARGE=$(jq -r .id "$WORK/body")
  status=$(call POST "$CLINICAL_URL/api/v1/clinical/drafts/$DISCHARGE/signature" DOCTOR "$DOCTOR_ID" "" "-H If-Match:\"0\"")
  expect "$status" 201 "firma de la epicrisis"
  status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters/$ENCOUNTER/closure" DOCTOR "$DOCTOR_ID")
  expect "$status" 200 "cierre de la atención"
fi

step "Lectura, anexo, integridad y copia"
status=$(call GET "$CLINICAL_URL/api/v1/clinical/notes/$TRIAGE/attachments/$ATTACHMENT" DOCTOR "$DOCTOR_ID")
expect "$status" 200 "descarga verificada del anexo"
cmp -s "$WORK/body" "$WORK/ekg.pdf" || fail "el anexo descargado no es idéntico al subido"
status=$(call GET "$CLINICAL_URL/api/v1/clinical/patients/$PATIENT/vital-signs" DOCTOR "$DOCTOR_ID")
expect "$status" 200 "serie de signos vitales"
[ "$(jq '.observations | length' "$WORK/body")" = "2" ] || fail "se esperaban 2 signos vitales"
status=$(call GET "$CLINICAL_URL/api/v1/clinical/patients/$PATIENT/integrity" ADMIN "$(cat /proc/sys/kernel/random/uuid)")
expect "$status" 200 "verificación de integridad"
[ "$(jq .verified "$WORK/body")" = "true" ] || fail "la cadena no verificó"
status=$(call GET "$CLINICAL_URL/api/v1/clinical/patients/$PATIENT/encounters" DOCTOR "$(cat /proc/sys/kernel/random/uuid)")
expect "$status" 403 "sin relación de cuidado no hay acceso"
status=$(curl -s -o "$WORK/copy.pdf" -w '%{http_code}' -X POST "$CLINICAL_URL/api/v1/clinical/patients/$PATIENT/record-copies" \
  -H "Authorization: Bearer $(token MEDICAL_RECORDS)" -H 'Content-Type: application/json' --data '{"reason": "Solicitud de la paciente en ventanilla"}')
[ "$status" = "200" ] && head -c 5 "$WORK/copy.pdf" | grep -q '%PDF-' || fail "no se generó la copia en PDF (HTTP $status)"
echo "ok  copia sellada en PDF ($(wc -c < "$WORK/copy.pdf") bytes)"

step "Eventos publicados en Kafka"
EVENTS=$(docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic clinical.encounters.v1 \
  --from-beginning --timeout-ms 15000 --property print.key=true 2>/dev/null | grep -c "^$PATIENT" || true)
[ "$EVENTS" -ge 2 ] || fail "se esperaban eventos del paciente en clinical.encounters.v1 y hubo $EVENTS"
echo "ok  $EVENTS eventos del paciente en clinical.encounters.v1"
AUDIT=$(docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic clinical.access-audit.v1 \
  --from-beginning --timeout-ms 15000 --property print.key=true 2>/dev/null | grep "^$PATIENT" | grep -c ClinicalRecordAccessDenied || true)
[ "$AUDIT" -ge 1 ] || fail "el acceso denegado no llegó a clinical.access-audit.v1"
echo "ok  acceso denegado auditado en clinical.access-audit.v1"

step "Aislamiento: patient-service caído"
docker compose -p "$PROJECT" stop patient-service > /dev/null 2>&1
status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters" NURSE "$NURSE_ID" "{\"patientUuid\": \"$PATIENT\", \"type\": \"OUTPATIENT\"}")
expect "$status" 201 "abre atención de un paciente ya conocido con patient-service caído"
status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters" NURSE "$NURSE_ID" "{\"patientUuid\": \"$(cat /proc/sys/kernel/random/uuid)\", \"type\": \"OUTPATIENT\"}")
expect "$status" 503 "paciente desconocido responde 503 sin tumbar el servicio"
docker compose -p "$PROJECT" start patient-service > /dev/null 2>&1
echo "ok  patient-service reiniciado"

printf '\nE2E clínico completo: paciente %s\n' "$PATIENT"
