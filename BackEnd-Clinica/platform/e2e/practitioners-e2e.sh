#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
TOOLS_IMAGE=clinica/openbao-tools:2.6.2
ISSUER="${AUTH_ISSUER:-http://localhost:8080/auth}"
REDIRECT_URI="${AUTH_GATEWAY_REDIRECT_URI:-http://localhost:8080/login/oauth2/code/clinica}"
SUPER_ADMIN="${AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL:-superadmin@clinica.local}"
published() { echo "http://$(docker compose -p "$PROJECT" port --index 1 "$1" "$2" 2>/dev/null)"; }
PRACTITIONERS_URL="${PRACTITIONERS_URL:-$(published practitioners-service 8085)}"
AUTH_URL="${AUTH_URL:-$(published auth-service 8086)}"
MAILPIT_URL="${MAILPIT_URL:-$(published mailpit 8025)}"
[ "$PRACTITIONERS_URL" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define PRACTITIONERS_URL" >&2; exit 1; }
[ "$AUTH_URL" != "http://" ] && [ "$MAILPIT_URL" != "http://" ] || { echo "El vínculo con la cuenta necesita auth-service y mailpit publicados" >&2; exit 1; }
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
JAR="$WORK/cookies"
TOTP_STATE="${E2E_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/clinica-e2e}/$PROJECT-super-admin-totp.json"
PASSWORD="frase e2e $(date +%s) para el directorio"

HR_ID=00000000-0000-4000-8000-000000000009
SUFFIX=$(date +%s | tail -c 6)
token() { sh "$DIR/staff-token.sh" "$@"; }
step() { printf '\n== %s\n' "$1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }

. "$DIR/staff-login.sh"

call() {
  method=$1; url=$2; role=$3; subject=$4; body=${5:-}; extra=${6:-}
  if [ -n "$body" ]; then
    curl -s -o "$WORK/body" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $(token "$role" "$subject")" \
      -H 'Content-Type: application/json' $extra --data "$body"
  else
    curl -s -o "$WORK/body" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $(token "$role" "$subject")" $extra
  fi
}

bearer_call() {
  method=$1; url=$2; access=$3; body=${4:-}
  if [ -n "$body" ]; then
    curl -s -o "$WORK/body" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $access" \
      -H 'Content-Type: application/json' --data "$body"
  else
    curl -s -o "$WORK/body" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $access"
  fi
}

expect() {
  [ "$1" = "$2" ] || { cat "$WORK/body" >&2; fail "$3: expected HTTP $2 and got $1"; }
  echo "ok  $3"
}

step "Catálogo de especialidades"
status=$(call POST "$PRACTITIONERS_URL/api/v1/specialties" HUMAN_RESOURCES "$HR_ID" "{
  \"code\": \"CAR$SUFFIX\", \"name\": \"Cardiología\"}")
expect "$status" 201 "registro de la especialidad"
SPECIALTY=$(jq -r .uuid "$WORK/body")

status=$(call POST "$PRACTITIONERS_URL/api/v1/specialties/$SPECIALTY/sub-specialties" HUMAN_RESOURCES "$HR_ID" "{
  \"code\": \"HEM$SUFFIX\", \"name\": \"Hemodinamia\"}")
expect "$status" 201 "registro de la subespecialidad"

status=$(call POST "$PRACTITIONERS_URL/api/v1/specialties/imports" HUMAN_RESOURCES "$HR_ID" "{
  \"specialties\": [{\"code\": \"CAR$SUFFIX\", \"name\": \"Cardiología\",
    \"subSpecialties\": [{\"code\": \"HEM$SUFFIX\", \"name\": \"Hemodinamia\"}]}]}")
expect "$status" 200 "carga repetida del catálogo"
[ "$(jq -r .changedSomething "$WORK/body")" = "false" ] || fail "la carga repetida cambió algo"
echo "ok  la carga del catálogo es idempotente"

step "Ficha del profesional"
DOCUMENT=$(date +%s%N | cut -c6-15)
status=$(call POST "$PRACTITIONERS_URL/api/v1/practitioners" HUMAN_RESOURCES "$HR_ID" "{
  \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$DOCUMENT\"},
  \"firstNames\": \"Sofía\", \"lastNames\": \"Quintero Ayala\",
  \"registration\": {\"number\": \"RM-$SUFFIX\", \"registeredOn\": \"2018-06-01\"},
  \"contact\": {\"email\": \"sofia.$SUFFIX@clinica.local\", \"mobile\": \"3012345678\"},
  \"relationship\": \"STAFF\"}")
expect "$status" 201 "registro del profesional"
PRACTITIONER=$(jq -r .uuid "$WORK/body")

status=$(call PUT "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER/specialties" HUMAN_RESOURCES "$HR_ID" "{
  \"specialties\": [{\"specialtyCode\": \"CAR$SUFFIX\", \"subSpecialtyCode\": \"HEM$SUFFIX\", \"principal\": true}]}" '-H If-Match:"0"')
expect "$status" 200 "especialidades asignadas"
[ "$(jq -r '.specialties[0].subSpecialtyName' "$WORK/body")" = "Hemodinamia" ] || fail "la subespecialidad no quedó asignada"

status=$(call PUT "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER/contact" HUMAN_RESOURCES "$HR_ID" "{
  \"email\": \"sofia.$SUFFIX@clinica.local\", \"mobile\": \"3019998877\"}")
expect "$status" 428 "corregir sin If-Match"

status=$(call POST "$PRACTITIONERS_URL/api/v1/practitioners/search" HUMAN_RESOURCES "$HR_ID" "{
  \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$DOCUMENT\"}}")
expect "$status" 200 "búsqueda por documento fuera de la URL"
[ "$(jq -r '.[0].uuid' "$WORK/body")" = "$PRACTITIONER" ] || fail "la búsqueda no encontró al profesional"

step "Cuenta de auth vinculada al profesional"
STAFF_ACCESS=$(super_admin_access_token)
DOCTOR_EMAIL="medica.directorio.$SUFFIX@clinica.local"
status=$(bearer_call POST "$AUTH_URL/api/v1/users" "$STAFF_ACCESS" "{
  \"email\": \"$DOCTOR_EMAIL\", \"fullName\": \"Sofía Quintero Ayala\", \"role\": \"DOCTOR\"}")
expect "$status" 201 "invitación de la médica en auth"
DOCTOR_UUID=$(jq -r .uuid "$WORK/body")

attempt=0
until status=$(call PUT "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER/account" HUMAN_RESOURCES "$HR_ID" \
    "{\"userUuid\": \"$DOCTOR_UUID\"}" '-H If-Match:"1"'); [ "$status" = "200" ]; do
  attempt=$((attempt + 1)); [ "$attempt" -lt 20 ] || { cat "$WORK/body" >&2; fail "la cuenta nunca llegó a auth.users.v1"; }
  sleep 2
done
echo "ok  la cuenta se vincula cuando su evento llega por auth.users.v1"
[ "$(jq -r .account.state "$WORK/body")" = "INACTIVE" ] || fail "una cuenta apenas invitada no debería aparecer activa"
echo "ok  la cuenta invitada aún no cuenta como activa"

JAR="$WORK/medica"
activation=$(latest_token_mailed_to "Active su cuenta de la Clínica" "$DOCTOR_EMAIL") || fail "no llegó la invitación de la médica"
[ "$(api /api/v1/activation "{\"token\": \"$activation\", \"password\": \"clave de la medica $SUFFIX en consulta\"}")" = "204" ] \
  || fail "activación de la médica: $(cat "$WORK/body")"
JAR="$WORK/cookies"

attempt=0
until status=$(call GET "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER" HUMAN_RESOURCES "$HR_ID"); \
    [ "$(jq -r .account.state "$WORK/body")" = "ACTIVE" ]; do
  attempt=$((attempt + 1)); [ "$attempt" -lt 20 ] || fail "el directorio no reflejó que la cuenta quedó activa"; sleep 2
done
echo "ok  al activarse la cuenta, el directorio lo refleja sin tocar al profesional"

step "Honorarios con step-up"
status=$(call POST "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER/fee-agreements" HUMAN_RESOURCES "$HR_ID" '{
  "basis": "HOURLY", "amount": 85000.00, "validFrom": "2026-01-01", "note": "Consulta externa"}')
expect "$status" 201 "honorarios por hora pactados"

status=$(call POST "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER/fee-agreements" HUMAN_RESOURCES "$HR_ID" '{
  "basis": "PER_PROCEDURE", "validFrom": "2026-07-01",
  "procedures": [{"serviceCode": "890201", "amount": 45000.00}]}')
expect "$status" 201 "honorarios por procedimiento desde julio"

status=$(call GET "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER/fee-agreements/in-force?on=2026-03-15" HUMAN_RESOURCES "$HR_ID")
expect "$status" 200 "honorarios vigentes en marzo"
[ "$(jq -r .basis "$WORK/body")" = "HOURLY" ] || fail "en marzo debía regir el acuerdo por hora"
[ "$(jq -r .amount "$WORK/body")" = "85000.00" ] || fail "el valor por hora cambió"

status=$(call GET "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER/fee-agreements" RECEPTIONIST "$(cat /proc/sys/kernel/random/uuid)")
expect "$status" 403 "recepción no ve los honorarios"

step "Eventos publicados por Debezium"
attempt=0
until docker exec practitioners-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" -e \"SELECT COUNT(*) FROM practitioners_outbox.outbox_events WHERE aggregateid = '$PRACTITIONER'\" 2>/dev/null" | grep -qE '^[3-9]|^[0-9]{2,}$'; do
  attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || fail "el outbox no registró los cambios del profesional"; sleep 1
done
echo "ok  el outbox tiene el historial del profesional"

docker exec practitioners-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" -e \"SELECT COUNT(*) FROM practitioners_outbox.outbox_events WHERE aggregateid = '$PRACTITIONER' AND payload LIKE '%85000%'\" 2>/dev/null" | grep -q '^0$' \
  || fail "los honorarios se filtraron en un evento"
echo "ok  los honorarios no viajan en los eventos"

if docker compose -p "$PROJECT" ps --status running --services 2>/dev/null | grep -q '^kafka$'; then
  attempt=0
  until docker exec kafka sh -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic practitioners.v1 --from-beginning --timeout-ms 5000 2>/dev/null" \
      | grep -q "$PRACTITIONER"; do
    attempt=$((attempt + 1)); [ "$attempt" -lt 12 ] || fail "el profesional no llegó a practitioners.v1"; sleep 5
  done
  echo "ok  practitioners.v1 tiene el estado del profesional"
fi

if docker compose -p "$PROJECT" ps --status running --services 2>/dev/null | grep -q '^clinical-history-service$'; then
  attempt=0
  until docker exec clinical-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" ${CLINICAL_DB_NAME:-clinical_db} -e \"SELECT registration_number FROM practitioner_references WHERE user_uuid = '$DOCTOR_UUID'\" 2>/dev/null" \
      | grep -q "RM-$SUFFIX"; do
    attempt=$((attempt + 1)); [ "$attempt" -lt 20 ] || fail "la historia clínica no recibió el registro profesional"; sleep 3
  done
  echo "ok  clinical-history conoce el registro profesional de quien firma"
fi

step "Reglas de acceso"
status=$(call GET "$PRACTITIONERS_URL/api/v1/practitioners/$PRACTITIONER" DOCTOR "$(cat /proc/sys/kernel/random/uuid)")
expect "$status" 403 "un médico no consulta el directorio"

status=$(call POST "$PRACTITIONERS_URL/api/v1/specialties" RECEPTIONIST "$(cat /proc/sys/kernel/random/uuid)" '{
  "code": "NOPE", "name": "No permitido"}')
expect "$status" 403 "recepción no escribe el catálogo"

printf '\npractitioners-e2e OK\n'
