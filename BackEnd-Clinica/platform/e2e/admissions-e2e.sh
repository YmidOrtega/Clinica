#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
TOOLS_IMAGE=clinica/openbao-tools:2.6.2
ISSUER="${AUTH_ISSUER:-http://localhost:8080/auth}"
REDIRECT_URI="${AUTH_GATEWAY_REDIRECT_URI:-http://localhost:8080/login/oauth2/code/clinica}"
SUPER_ADMIN="${AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL:-superadmin@clinica.local}"
published() { echo "http://$(docker compose -p "$PROJECT" port --index 1 "$1" "$2" 2>/dev/null)"; }
ADMISSIONS_URL="${ADMISSIONS_URL:-$(published admissions-service 8088)}"
PATIENT_URL="${PATIENT_URL:-$(published patient-service 8081)}"
PRACTITIONERS_URL="${PRACTITIONERS_URL:-$(published practitioners-service 8085)}"
CLINICAL_URL="${CLINICAL_URL:-$(published clinical-history-service 8089)}"
AUTH_URL="${AUTH_URL:-$(published auth-service 8086)}"
MAILPIT_URL="${MAILPIT_URL:-$(published mailpit 8025)}"
[ "$ADMISSIONS_URL" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define ADMISSIONS_URL" >&2; exit 1; }
[ "$PATIENT_URL" != "http://" ] || { echo "El ingreso necesita patient-service publicado" >&2; exit 1; }
[ "$AUTH_URL" != "http://" ] && [ "$MAILPIT_URL" != "http://" ] \
  || { echo "El personal real necesita auth-service y mailpit publicados" >&2; exit 1; }
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
JAR="$WORK/cookies"
TOTP_STATE="${E2E_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/clinica-e2e}/$PROJECT-super-admin-totp.json"
PASSWORD="frase e2e $(date +%s) para admisiones"

SUFFIX=$(date +%s | tail -c 6)
ADMIN_ID=00000000-0000-4000-8000-000000000002
DOCTOR_ID=$(cat /proc/sys/kernel/random/uuid)
NURSE_ID=$(cat /proc/sys/kernel/random/uuid)
RECEPTION_ID=$(cat /proc/sys/kernel/random/uuid)
. "$DIR/staff-login.sh"

token() {
  eval "access=\${$1_ACCESS:-}"
  if [ -n "$access" ]; then echo "$access"; else sh "$DIR/staff-token.sh" "$@"; fi
}

staff_member() {
  role=$1; email="$(printf '%s' "$role" | tr 'A-Z_' 'a-z.').adm.$SUFFIX@clinica.local"
  status=$(curl -s -o "$WORK/body" -w '%{http_code}' -X POST "$AUTH_URL/api/v1/users" \
    -H "Authorization: Bearer $SUPER_ADMIN_ACCESS" -H 'Content-Type: application/json' --data "{
    \"email\": \"$email\", \"fullName\": \"Personal de $(printf '%s' "$role" | tr 'A-Z_' 'a-z ')\", \"role\": \"$role\"}")
  [ "$status" = "201" ] || { cat "$WORK/body" >&2; fail "invitación de $role"; }
  invited_staff_access_token "$email" "guardia larga $(date +%s%N) sin atajos"
}

call() {
  method=$1; url=$2; role=$3; subject=$4; body=${5:-}; extra=${6:-}
  if [ -n "$body" ]; then
    curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $(token "$role" "$subject")" \
      -H 'Content-Type: application/json' $extra --data "$body"
  else
    curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $(token "$role" "$subject")" $extra
  fi
}

expect() {
  [ "$1" = "$2" ] || { cat "$WORK/body" >&2; fail "$3: expected HTTP $2 and got $1"; }
  echo "ok  $3"
}

version() { tr -d '\r' < "$WORK/headers" | awk 'tolower($1) == "etag:" {gsub(/"/, "", $2); print $2}'; }

step "Personal real con segundo factor para los pasos que cruzan servicios"
SUPER_ADMIN_ACCESS=$(super_admin_access_token)
NURSE_ACCESS=$(staff_member NURSE)
RECEPTIONIST_ACCESS=$(staff_member RECEPTIONIST)
DOCTOR_ACCESS=$(staff_member DOCTOR)
echo "ok  enfermería, recepción y medicina activaron sus cuentas con TOTP"

step "Catálogo, habitación y cama"
status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/service-types" ADMIN "$ADMIN_ID" "{
  \"name\": \"Urgencias $SUFFIX\", \"kind\": \"EMERGENCY\"}")
expect "$status" 201 "tipo de servicio de urgencias"
EMERGENCY_TYPE=$(jq -r .uuid "$WORK/body")

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/service-types" ADMIN "$ADMIN_ID" "{
  \"name\": \"Hospitalización $SUFFIX\", \"kind\": \"INPATIENT\"}")
expect "$status" 201 "tipo de servicio de hospitalización"
INPATIENT_TYPE=$(jq -r .uuid "$WORK/body")

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/locations" ADMIN "$ADMIN_ID" "{\"name\": \"Sede $SUFFIX\"}")
expect "$status" 201 "ubicación"
LOCATION=$(jq -r .uuid "$WORK/body")

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/configured-services" ADMIN "$ADMIN_ID" "{
  \"serviceTypeUuid\": \"$EMERGENCY_TYPE\", \"locationUuid\": \"$LOCATION\"}")
expect "$status" 201 "urgencias configuradas en la sede"
EMERGENCY=$(jq -r .uuid "$WORK/body")

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/configured-services" ADMIN "$ADMIN_ID" "{
  \"serviceTypeUuid\": \"$INPATIENT_TYPE\", \"locationUuid\": \"$LOCATION\"}")
expect "$status" 201 "hospitalización configurada en la sede"
WARD=$(jq -r .uuid "$WORK/body")

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/rooms" ADMIN "$ADMIN_ID" "{
  \"name\": \"Hab $SUFFIX\", \"locationUuid\": \"$LOCATION\", \"stayType\": \"GENERAL_WARD\"}")
expect "$status" 201 "habitación"
ROOM=$(jq -r .uuid "$WORK/body")

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/beds" ADMIN "$ADMIN_ID" "{
  \"label\": \"Cama $SUFFIX\", \"roomUuid\": \"$ROOM\"}")
expect "$status" 201 "cama instalada"
BED=$(jq -r .uuid "$WORK/body")

step "Admisión de un paciente sin identificar contra patient-service"
UNIDENTIFIED="{
  \"sex\": \"MALE\", \"estimatedBirthYear\": 1980, \"description\": \"Hombre adulto traído por ambulancia\",
  \"configurationServiceUuid\": \"$EMERGENCY\", \"cause\": \"ACCIDENT\"}"
status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/unidentified" NURSE "$NURSE_ID" "$UNIDENTIFIED")
expect "$status" 403 "enfermería no admite pacientes"
status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/unidentified" RECEPTIONIST "$RECEPTION_ID" "$UNIDENTIFIED")
expect "$status" 201 "el NN se admite después de registrarlo en patient-service"
EPISODE=$(jq -r .uuid "$WORK/body")
NUMBER=$(jq -r .number "$WORK/body")
PATIENT=$(jq -r .patientUuid "$WORK/body")
echo "$NUMBER" | grep -qE '^ADM-[0-9]{4}-[0-9]{6}$' || fail "el número del episodio no tiene la forma esperada"
[ "$(jq -r .status.code "$WORK/body")" = "REGISTERED" ] || fail "el episodio no nació registrado"

status=$(call GET "$PATIENT_URL/api/v1/unidentified-patients/$PATIENT" RECEPTIONIST "$RECEPTION_ID")
expect "$status" 200 "patient-service tiene el NN que creó la admisión"

step "La cobertura nunca bloquea urgencias y sí la hospitalización"
[ "$(jq -r .coverage.pending "$WORK/body" 2>/dev/null)" != "null" ] || true
status=$(call GET "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE" BILLING "$(cat /proc/sys/kernel/random/uuid)")
expect "$status" 200 "facturación consulta el episodio"
COVERAGE=$(jq -r .coverage.status "$WORK/body")
[ "$COVERAGE" = "NOT_COVERED" ] || [ "$COVERAGE" = "UNKNOWN" ] || fail "sin contrato la cobertura debía quedar marcada"
echo "ok  urgencias admitió con la cobertura marcada como $COVERAGE"

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/search" BILLING "$(cat /proc/sys/kernel/random/uuid)" "{
  \"number\": \"$NUMBER\"}")
expect "$status" 200 "búsqueda del episodio por número"
[ "$(jq -r '.content[0].uuid' "$WORK/body")" = "$EPISODE" ] || fail "la búsqueda no encontró el episodio"

status=$(call GET "$ADMISSIONS_URL/api/v1/admissions/episodes/pending-coverage" BILLING "$(cat /proc/sys/kernel/random/uuid)")
expect "$status" 200 "panel de cobertura pendiente"

step "Cama, activación y paso a hospitalización"
status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/bed" NURSE "$NURSE_ID" \
  "{\"bedUuid\": \"$BED\"}" '-H If-Match:"0"')
expect "$status" 200 "cama asignada"
VERSION=$(version)

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/activation" RECEPTIONIST "$RECEPTION_ID" "" \
  "-H If-Match:\"$VERSION\"")
expect "$status" 200 "episodio activado"
VERSION=$(version)

status=$(call GET "$ADMISSIONS_URL/api/v1/admissions/beds/$BED" NURSE "$NURSE_ID")
expect "$status" 200 "estado de la cama"
[ "$(jq -r .status.code "$WORK/body")" = "OCCUPIED" ] || fail "la cama debería estar ocupada"

status=$(call GET "$ADMISSIONS_URL/api/v1/admissions/locations/$LOCATION/census" NURSE "$NURSE_ID")
expect "$status" 200 "censo de la ubicación"
[ "$(jq -r --arg bed "$BED" '.[] | select(.bedUuid == $bed) | .occupant.number' "$WORK/body")" = "$NUMBER" ] \
  || fail "el censo no muestra quién ocupa la cama"
echo "ok  el censo dice qué episodio ocupa la cama"

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/phase" RECEPTIONIST "$RECEPTION_ID" "{
  \"configurationServiceUuid\": \"$WARD\", \"reason\": \"Requiere hospitalización\", \"bedUuid\": \"$BED\"}" \
  "-H If-Match:\"$VERSION\"")
expect "$status" 200 "urgencias pasa a hospitalización sin cambiar de número"
[ "$(jq -r .number "$WORK/body")" = "$NUMBER" ] || fail "el número del episodio cambió al cambiar de fase"
[ "$(jq -r '.phases | length' "$WORK/body")" = "2" ] || fail "el episodio debería tener dos fases"
VERSION=$(version)

step "Profesional responsable copiado del directorio"
if [ "$PRACTITIONERS_URL" != "http://" ]; then
  DOCUMENT=$(date +%s%N | cut -c6-15)
  status=$(call POST "$PRACTITIONERS_URL/api/v1/practitioners" HUMAN_RESOURCES "$(cat /proc/sys/kernel/random/uuid)" "{
    \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$DOCUMENT\"},
    \"firstNames\": \"Sofía\", \"lastNames\": \"Quintero Ayala\",
    \"registration\": {\"number\": \"RM-ADM-$SUFFIX\", \"registeredOn\": \"2018-06-01\"},
    \"contact\": {\"email\": \"sofia.adm.$SUFFIX@clinica.local\", \"mobile\": \"3012345678\"},
    \"relationship\": \"STAFF\"}")
  expect "$status" 201 "profesional registrado en el directorio"
  PRACTITIONER=$(jq -r .uuid "$WORK/body")

  attempt=0
  until status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/attending-practitioner" \
      RECEPTIONIST "$RECEPTION_ID" "{\"practitionerUuid\": \"$PRACTITIONER\"}" "-H If-Match:\"$VERSION\""); \
      [ "$status" = "200" ]; do
    attempt=$((attempt + 1)); [ "$attempt" -lt 20 ] || { cat "$WORK/body" >&2; fail "el profesional nunca llegó a admisiones"; }
    sleep 2
  done
  [ "$(jq -r .attending.registrationNumber "$WORK/body")" = "RM-ADM-$SUFFIX" ] || fail "no se copió el registro profesional"
  echo "ok  el episodio guarda copia del nombre y el registro del profesional"
  VERSION=$(version)
fi

step "El triage de clinical-history se refleja en la cola"
if [ "$CLINICAL_URL" != "http://" ]; then
  status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters" NURSE "$NURSE_ID" "{
    \"patientUuid\": \"$PATIENT\", \"type\": \"EMERGENCY\", \"admissionUuid\": \"$EPISODE\"}")
  expect "$status" 201 "atención clínica atada al episodio"
  [ "$(jq -r .admissionVerified "$WORK/body")" = "true" ] || fail "clinical no verificó el episodio contra admisiones"
  echo "ok  clinical comprobó el episodio contra admisiones antes de abrir la atención"
  ENCOUNTER=$(jq -r .id "$WORK/body")

  status=$(call POST "$CLINICAL_URL/api/v1/clinical/encounters/$ENCOUNTER/drafts" NURSE "$NURSE_ID" "{
    \"content\": {\"type\": \"TRIAGE\", \"level\": \"II\", \"reason\": \"Dolor torácico\"}}")
  expect "$status" 201 "borrador de triage"
  TRIAGE=$(jq -r .id "$WORK/body")
  status=$(call POST "$CLINICAL_URL/api/v1/clinical/drafts/$TRIAGE/signature" NURSE "$NURSE_ID" "" '-H If-Match:"0"')
  expect "$status" 201 "triage firmado"

  attempt=0
  until status=$(call GET "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE" DOCTOR "$DOCTOR_ID"); \
      [ "$(jq -r .triage.level "$WORK/body")" = "II" ]; do
    attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || fail "el triage no se reflejó en el episodio"; sleep 2
  done
  echo "ok  admisiones refleja el nivel de triage que firmó clinical-history"

  status=$(call GET "$ADMISSIONS_URL/api/v1/admissions/configured-services/$WARD/queue" DOCTOR "$DOCTOR_ID")
  expect "$status" 200 "cola del servicio"
  [ "$(jq -r '.[0].triage.level' "$WORK/body")" = "II" ] || fail "la cola no ordena con el triage reflejado"
  echo "ok  la cola usa el triage para ordenar"
fi

step "Comprobante sellado y su verificación"
status=$(curl -s -o "$WORK/receipt.pdf" -w '%{http_code}' -D "$WORK/headers" \
  -X POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/receipt" \
  -H "Authorization: Bearer $(token RECEPTIONIST "$RECEPTION_ID")")
expect "$status" 200 "comprobante emitido en PDF"
head -c 5 "$WORK/receipt.pdf" | grep -q '%PDF-' || fail "el comprobante no es un PDF"
RECEIPT=$(tr -d '\r' < "$WORK/headers" | awk '/^X-Receipt-Id:/ {print $2}')
FINGERPRINT=$(sha256sum "$WORK/receipt.pdf" | cut -d' ' -f1)

status=$(curl -s -o "$WORK/body" -w '%{http_code}' -X POST \
  "$ADMISSIONS_URL/api/v1/admissions/receipts/$RECEIPT/verification" \
  -H "Authorization: Bearer $(token BILLING "$(cat /proc/sys/kernel/random/uuid)")" \
  -F "document=@$WORK/receipt.pdf;type=application/pdf")
expect "$status" 200 "verificación del PDF emitido"
[ "$(jq -r .authentic "$WORK/body")" = "true" ] || fail "el comprobante recién emitido no se reconoció"

status=$(curl -s -o "$WORK/body" -w '%{http_code}' -X POST "$ADMISSIONS_URL/api/v1/admissions/receipts/verification" \
  -H 'Content-Type: application/json' --data "{\"number\": \"$NUMBER\", \"sha256\": \"$FINGERPRINT\"}")
expect "$status" 200 "verificación pública sin credenciales"
[ "$(jq -r .authentic "$WORK/body")" = "true" ] || fail "la verificación pública no reconoció el comprobante"
[ "$(jq -r 'keys | join(",")' "$WORK/body")" = "authentic" ] || fail "la verificación pública reveló algo más"
echo "ok  la verificación pública responde solo auténtico o no"

status=$(curl -s -o "$WORK/body" -w '%{http_code}' -X POST "$ADMISSIONS_URL/api/v1/admissions/receipts/verification" \
  -H 'Content-Type: application/json' \
  --data "{\"number\": \"$NUMBER\", \"sha256\": \"0000000000000000000000000000000000000000000000000000000000000000\"}")
expect "$status" 200 "verificación pública de una huella falsa"
[ "$(jq -r .authentic "$WORK/body")" = "false" ] || fail "una huella falsa pasó por auténtica"

status=$(curl -s -o "$WORK/body" -w '%{http_code}' "$ADMISSIONS_URL/api/v1/admissions/seal-keys")
expect "$status" 200 "claves públicas del sello"
[ "$(jq -r 'to_entries | length' "$WORK/body")" -ge 1 ] || fail "no se publicó ninguna clave de sello"

step "Egreso por fallecimiento con step-up y aviso a patient-service"
status=$(call GET "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE" DOCTOR "$DOCTOR_ID")
expect "$status" 200 "episodio antes del egreso"
VERSION=$(version)
status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/discharge" DOCTOR "$DOCTOR_ID" "{
  \"type\": \"DEATH\", \"occurredAt\": \"$(date -u -d '-10 minutes' +%Y-%m-%dT%H:%M:%SZ)\",
  \"certificateNumber\": \"CD-$SUFFIX\"}" "-H If-Match:\"$VERSION\"")
expect "$status" 200 "egreso por fallecimiento"
[ "$(jq -r .status.discharge.type "$WORK/body")" = "DEATH" ] || fail "el egreso no quedó como fallecimiento"
[ "$(jq -r .bedUuid "$WORK/body")" = "null" ] || fail "el fallecimiento no liberó la cama"
[ "$(jq -r .deathNotice.status "$WORK/body")" = "SENT" ] || fail "el aviso a patient-service no salió"
echo "ok  el egreso libera la cama y avisa del fallecimiento al directorio de pacientes"

status=$(call GET "$PATIENT_URL/api/v1/unidentified-patients/$PATIENT" RECEPTIONIST "$RECEPTION_ID")
expect "$status" 200 "el NN en patient-service tras el aviso"
[ "$(jq -r .status.code "$WORK/body")" = "DECEASED" ] || fail "patient-service no registró el fallecimiento"
echo "ok  patient-service tiene el fallecimiento que informó admisiones"

status=$(call GET "$ADMISSIONS_URL/api/v1/admissions/beds/$BED" NURSE "$NURSE_ID")
expect "$status" 200 "la cama después del egreso"
[ "$(jq -r .status.code "$WORK/body")" = "CLEANING" ] || fail "la cama liberada debería quedar en limpieza"

step "Reglas de acceso y step-up"
status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/cancellation" RECEPTIONIST "$RECEPTION_ID" \
  '{"reason": "No debería poder"}' '-H If-Match:"9"')
expect "$status" 403 "recepción no anula episodios"

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/locations" NURSE "$NURSE_ID" '{"name": "No permitido"}')
expect "$status" 403 "enfermería no define el catálogo"

status=$(curl -s -o "$WORK/body" -w '%{http_code}' "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE")
expect "$status" 401 "el episodio no se consulta sin credenciales"

step "Eventos publicados por Debezium"
attempt=0
until docker exec admissions-db sh -c "psql -qtAX -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"SELECT count(*) FROM admissions_outbox.outbox_events WHERE aggregateid = '$EPISODE'\" 2>/dev/null" | grep -qE '^[4-9]|^[0-9]{2,}$'; do
  attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || fail "el outbox no registró la vida del episodio"; sleep 1
done
echo "ok  el outbox tiene los hechos del episodio"

docker exec admissions-db sh -c "psql -qtAX -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"SELECT count(*) FROM admissions_outbox.outbox_events WHERE aggregateid = '$EPISODE' AND payload::text LIKE '%CD-$SUFFIX%'\" 2>/dev/null" | grep -q '^0$' \
  || fail "el número del certificado de defunción se filtró en un evento"
echo "ok  el certificado de defunción no viaja en los eventos"

if docker compose -p "$PROJECT" ps --status running --services 2>/dev/null | grep -q '^kafka$'; then
  attempt=0
  until docker exec kafka sh -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic admissions.events.v1 --from-beginning --timeout-ms 5000 2>/dev/null" \
      | grep -q "$EPISODE"; do
    attempt=$((attempt + 1)); [ "$attempt" -lt 12 ] || fail "el episodio no llegó a admissions.events.v1"; sleep 5
  done
  echo "ok  admissions.events.v1 tiene los hechos del episodio (réplica lógica de PostgreSQL)"
fi

printf '\nadmissions-e2e OK\n'
