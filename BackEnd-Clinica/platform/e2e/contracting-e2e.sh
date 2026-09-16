#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
published() { echo "http://$(docker compose -p "$PROJECT" port --index 1 "$1" "$2" 2>/dev/null)"; }
CONTRACTING_URL="${CONTRACTING_URL:-$(published contracting-service 8087)}"
PATIENT_URL="${PATIENT_URL:-$(published patient-service 8081)}"
[ "$CONTRACTING_URL" != "http://" ] && [ "$PATIENT_URL" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define CONTRACTING_URL y PATIENT_URL" >&2; exit 1; }
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

CONTRACTING_ID=$(cat /proc/sys/kernel/random/uuid)
BILLING_ID=$(cat /proc/sys/kernel/random/uuid)
SUFFIX=$(date +%s | tail -c 6)
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

step "Pagador, manual tarifario y contrato"
status=$(call POST "$CONTRACTING_URL/api/v1/payers" CONTRACTING "$CONTRACTING_ID" "{
  \"identity\": {\"socialReason\": \"EPS de prueba E2E\", \"nit\": \"890903938\", \"type\": \"EPS\", \"adresCode\": \"EPS$SUFFIX\"},
  \"contact\": {\"address\": \"Calle 100 # 7-33\", \"phone\": \"6017429000\", \"billingEmail\": \"radicacion@e2e.local\"}}")
if [ "$status" = "409" ]; then
  status=$(call POST "$CONTRACTING_URL/api/v1/payers/search" CONTRACTING "$CONTRACTING_ID" '{"nit": "890903938"}')
  expect "$status" 200 "pagador existente"
  PAYER=$(jq -r '.content[0].uuid' "$WORK/body")
  echo "ok  pagador reutilizado"
else
  expect "$status" 201 "registro del pagador"
  PAYER=$(jq -r .uuid "$WORK/body")
fi

status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals" CONTRACTING "$CONTRACTING_ID" "{
  \"code\": \"SOAT_$SUFFIX\", \"name\": \"SOAT de prueba\", \"unit\": \"SMLDV\"}")
expect "$status" 201 "registro del manual tarifario"
MANUAL=$(jq -r .uuid "$WORK/body")

status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals/$MANUAL/versions" CONTRACTING "$CONTRACTING_ID" "{
  \"label\": \"2026\", \"unitValue\": 47450.00, \"validFrom\": \"2026-01-01\"}")
expect "$status" 201 "versión del manual en borrador"
VERSION=$(jq -r .uuid "$WORK/body")

status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals/versions/$VERSION/items" CONTRACTING "$CONTRACTING_ID" '{
  "items": [{"cupsCode": "890201", "description": "Consulta de medicina general", "value": 2.5},
            {"cupsCode": "903841", "description": "Hemograma IV", "value": 1.25}]}')
expect "$status" 200 "carga de tarifas"
[ "$(jq -r .loaded "$WORK/body")" = "2" ] || fail "la carga no registró las dos tarifas"

status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals/versions/$VERSION/items" CONTRACTING "$CONTRACTING_ID" '{
  "items": [{"cupsCode": "890201", "description": "Consulta de medicina general", "value": 2.5},
            {"cupsCode": "903841", "description": "Hemograma IV", "value": 1.25}]}')
expect "$status" 200 "carga repetida"
[ "$(jq -r .alreadyLoaded "$WORK/body")" = "true" ] || fail "la carga repetida no fue idempotente"

status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals/versions/$VERSION/activation" CONTRACTING "$CONTRACTING_ID" '{}' '-H If-Match:"1"')
expect "$status" 200 "publicación de la versión"

status=$(call POST "$CONTRACTING_URL/api/v1/contracts" CONTRACTING "$CONTRACTING_ID" "{
  \"payerUuid\": \"$PAYER\", \"number\": \"CNT-$SUFFIX\", \"name\": \"Contrato E2E\", \"modality\": \"EVENT\",
  \"validFrom\": \"2026-01-01\", \"validTo\": \"2026-12-31\"}")
expect "$status" 201 "contrato en borrador"
CONTRACT=$(jq -r .uuid "$WORK/body")

status=$(call PUT "$CONTRACTING_URL/api/v1/contracts/$CONTRACT/tariff-terms" CONTRACTING "$CONTRACTING_ID" \
  "{\"tariffVersionUuid\": \"$VERSION\", \"factor\": 1.2}" '-H If-Match:"0"')
expect "$status" 200 "términos tarifarios pactados"

status=$(call POST "$CONTRACTING_URL/api/v1/contracts/$CONTRACT/activation" CONTRACTING "$CONTRACTING_ID" '{}' '-H If-Match:"1"')
expect "$status" 200 "contrato activado"

step "Resolución de precios"
status=$(call POST "$CONTRACTING_URL/api/v1/price-quotes" BILLING "$BILLING_ID" "{
  \"contractUuid\": \"$CONTRACT\", \"on\": \"2026-03-01\",
  \"services\": [{\"cupsCode\": \"890201\", \"quantity\": 2}]}")
expect "$status" 200 "consulta de precio"
PRICE=$(jq -r '.services[0].unitPrice' "$WORK/body")
[ "$PRICE" = "142350.00" ] || fail "2.5 SMLDV x 47450 x 1.2 debía dar 142350.00 y dio $PRICE"
echo "ok  2.5 SMLDV x 47450 x factor 1.2 = $PRICE"

status=$(call POST "$CONTRACTING_URL/api/v1/contracts/$CONTRACT/tariff-exceptions" CONTRACTING "$CONTRACTING_ID" '{
  "cupsCode": "890201", "agreedPrice": 90000, "reason": "Negociación puntual de la consulta", "validFrom": "2026-01-01"}')
expect "$status" 200 "excepción de precio registrada"

status=$(call POST "$CONTRACTING_URL/api/v1/price-quotes" BILLING "$BILLING_ID" "{
  \"contractUuid\": \"$CONTRACT\", \"on\": \"2026-03-01\", \"services\": [{\"cupsCode\": \"890201\"}]}")
expect "$status" 200 "precio con excepción"
[ "$(jq -r '.services[0].origin' "$WORK/body")" = "CONTRACT_EXCEPTION" ] || fail "la excepción no ganó sobre el manual"
echo "ok  la excepción manda sobre el manual"

step "Capitación contrastada contra patient-service"
DOCUMENT=$(date +%s%N | cut -c6-15)
status=$(call POST "$PATIENT_URL/api/v1/patients" RECEPTIONIST "$(cat /proc/sys/kernel/random/uuid)" "{
  \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$DOCUMENT\"},
  \"demographics\": {\"firstNames\": \"Marta\", \"lastNames\": \"Cárdenas Ruiz\", \"birthDate\": \"1991-03-02\", \"sex\": \"FEMALE\",
                     \"countryOfOrigin\": \"CO\", \"disability\": \"NONE\"},
  \"contact\": {\"mobile\": \"3012223344\"},
  \"affiliation\": {\"regime\": \"CONTRIBUTORY\", \"affiliateType\": \"HOLDER\", \"payerUuid\": \"$PAYER\"},
  \"residence\": {\"department\": \"Santander\", \"municipality\": \"Girón\", \"zone\": \"URBAN\", \"address\": \"Calle 1 # 2-3\"}}")
expect "$status" 201 "patient-service verificó el pagador contra contracting-service"
PATIENT=$(jq -r .uuid "$WORK/body")

status=$(call POST "$CONTRACTING_URL/api/v1/contracts" CONTRACTING "$CONTRACTING_ID" "{
  \"payerUuid\": \"$PAYER\", \"number\": \"CAP-$SUFFIX\", \"name\": \"Contrato capitado E2E\", \"modality\": \"CAPITATION\",
  \"validFrom\": \"2026-01-01\", \"validTo\": \"2026-12-31\"}")
expect "$status" 201 "contrato de capitación"
CAPITATED=$(jq -r .uuid "$WORK/body")

status=$(call POST "$CONTRACTING_URL/api/v1/contracts/$CAPITATED/activation" CONTRACTING "$CONTRACTING_ID" '{}' '-H If-Match:"0"')
expect "$status" 200 "contrato de capitación activado"

status=$(call POST "$CONTRACTING_URL/api/v1/contracts/$CAPITATED/capitation-agreement" CONTRACTING "$CONTRACTING_ID" '{
  "perCapitaValue": 38500.50, "periodicity": "MONTHLY", "technicalNote": "Nota técnica anexa al contrato E2E",
  "validFrom": "2026-01-01"}')
expect "$status" 200 "acuerdo de capitación"

status=$(call POST "$CONTRACTING_URL/api/v1/contracts/$CAPITATED/capitated-members/imports?period=2026-03" CONTRACTING "$CONTRACTING_ID" "{
  \"members\": [{\"documentType\": \"CEDULA_DE_CIUDADANIA\", \"documentNumber\": \"$DOCUMENT\", \"fullName\": \"Marta Cárdenas Ruiz\"}]}")
expect "$status" 200 "carga de la población capitada"
[ "$(jq -r .matched "$WORK/body")" = "1" ] || { cat "$WORK/body" >&2; fail "el afiliado no se contrastó contra patient-service"; }
echo "ok  el afiliado quedó vinculado al paciente $PATIENT"

status=$(call GET "$CONTRACTING_URL/api/v1/capitated-members/coverage?documentType=CEDULA_DE_CIUDADANIA&documentNumber=$DOCUMENT&on=2026-03-15" RECEPTIONIST "$(cat /proc/sys/kernel/random/uuid)")
expect "$status" 200 "consulta de cobertura"
[ "$(jq -r '.[0].contractUuid' "$WORK/body")" = "$CAPITATED" ] || fail "la cobertura no devolvió el contrato capitado"
echo "ok  recepción ve la cobertura del paciente"

step "Eventos publicados por Debezium"
attempt=0
until docker exec contracting-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" -e \"SELECT COUNT(*) FROM contracting_outbox.outbox_events WHERE aggregateid = '$CONTRACT'\" 2>/dev/null" | grep -qE '^[4-9]|^[0-9]{2,}$'; do
  attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || fail "el outbox no registró los cambios del contrato"; sleep 1
done
echo "ok  el outbox tiene el historial del contrato"

if docker compose -p "$PROJECT" ps --status running --services 2>/dev/null | grep -q '^kafka$'; then
  attempt=0
  until docker exec kafka sh -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic contracting.contracts.v1 --from-beginning --timeout-ms 5000 2>/dev/null" \
      | grep -q "$CONTRACT"; do
    attempt=$((attempt + 1)); [ "$attempt" -lt 12 ] || fail "el contrato no llegó a contracting.contracts.v1"; sleep 5
  done
  echo "ok  contracting.contracts.v1 tiene el estado del contrato"
else
  echo "--  kafka no está arriba; se omite la verificación del topic"
fi

step "Reglas de acceso"
status=$(call POST "$CONTRACTING_URL/api/v1/payers" BILLING "$BILLING_ID" '{
  "identity": {"socialReason": "No permitido", "nit": "899999068", "type": "EPS"},
  "contact": {"address": "Calle 1", "phone": "6011111111"}}')
expect "$status" 403 "facturación no registra pagadores"

status=$(call POST "$CONTRACTING_URL/api/v1/price-quotes" DOCTOR "$(cat /proc/sys/kernel/random/uuid)" "{
  \"contractUuid\": \"$CONTRACT\", \"on\": \"2026-03-01\", \"services\": [{\"cupsCode\": \"890201\"}]}")
expect "$status" 403 "un médico no consulta precios"

printf '\ncontracting-e2e OK\n'
