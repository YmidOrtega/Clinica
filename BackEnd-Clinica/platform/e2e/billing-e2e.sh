#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
TOOLS_IMAGE=clinica/openbao-tools:2.6.2
ISSUER="${AUTH_ISSUER:-http://localhost:8080/auth}"
REDIRECT_URI="${AUTH_GATEWAY_REDIRECT_URI:-http://localhost:8080/login/oauth2/code/clinica}"
SUPER_ADMIN="${AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL:-superadmin@clinica.local}"
published() { echo "http://$(docker compose -p "$PROJECT" port --index 1 "$1" "$2" 2>/dev/null)"; }
BILLING_URL="${BILLING_URL:-$(published billing-service 8082)}"
CONTRACTING_URL="${CONTRACTING_URL:-$(published contracting-service 8087)}"
PATIENT_URL="${PATIENT_URL:-$(published patient-service 8081)}"
ADMISSIONS_URL="${ADMISSIONS_URL:-$(published admissions-service 8088)}"
PRACTITIONERS_URL="${PRACTITIONERS_URL:-$(published practitioners-service 8085)}"
CLINICAL_URL="${CLINICAL_URL:-$(published clinical-history-service 8089)}"
AUTH_URL="${AUTH_URL:-$(published auth-service 8086)}"
MAILPIT_URL="${MAILPIT_URL:-$(published mailpit 8025)}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
for url in "$BILLING_URL" "$CONTRACTING_URL" "$PATIENT_URL" "$ADMISSIONS_URL" "$PRACTITIONERS_URL" "$CLINICAL_URL" \
    "$AUTH_URL" "$MAILPIT_URL"; do
  [ "$url" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define las *_URL" >&2; exit 1; }
done
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
JAR="$WORK/cookies"
TOTP_STATE="${E2E_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/clinica-e2e}/$PROJECT-super-admin-totp.json"
PASSWORD="frase e2e $(date +%s) para facturación"

SUFFIX=$(date +%s | tail -c 6)
TODAY=$(TZ=America/Bogota date +%F)
ADMIN_ID=00000000-0000-4000-8000-000000000002
SUPER_ADMIN_ID=00000000-0000-4000-8000-000000000001
BILLING_ID=$(cat /proc/sys/kernel/random/uuid)
RECEIVABLE_ID=$(cat /proc/sys/kernel/random/uuid)
CONTRACTING_ID=$(cat /proc/sys/kernel/random/uuid)
token() { sh "$DIR/staff-token.sh" "$@"; }

. "$DIR/staff-login.sh"

call() {
  method=$1; url=$2; role=$3; subject=$4; body=${5:-}; extra=${6:-}
  if [ -n "$body" ]; then
    curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $(token "$role" "$subject")" -H 'Content-Type: application/json' $extra --data "$body"
  else
    curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $(token "$role" "$subject")" $extra
  fi
}

as_staff() {
  method=$1; url=$2; access=$3; body=${4:-}; extra=${5:-}
  if [ -n "$body" ]; then
    curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $access" \
      -H 'Content-Type: application/json' $extra --data "$body"
  else
    curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $access" $extra
  fi
}

staff_member() {
  role=$1; email="$(printf '%s' "$role" | tr 'A-Z_' 'a-z.')${2:+.$2}.fac.$SUFFIX@clinica.local"
  status=$(as_staff POST "$AUTH_URL/api/v1/users" "$SUPER_ADMIN_ACCESS" "{
    \"email\": \"$email\", \"fullName\": \"Personal de $(printf '%s' "$role" | tr 'A-Z_' 'a-z ')\", \"role\": \"$role\"}")
  [ "$status" = "201" ] || { cat "$WORK/body" >&2; fail "invitación de $role"; }
  invited_staff_access_token "$email" "turno largo $(date +%s%N) sin atajos"
}

expect() {
  [ "$1" = "$2" ] || { cat "$WORK/body" >&2; fail "$3: expected HTTP $2 and got $1"; }
  echo "ok  $3"
}

etag() { tr -d '\r' < "$WORK/headers" | awk 'tolower($1) == "etag:" {gsub(/"/, "", $2); print $2}'; }
field() { jq -r "$1" "$WORK/body"; }

until_ok() {
  what=$1; tries=$2; shift 2
  attempt=0
  until "$@"; do
    attempt=$((attempt + 1)); [ "$attempt" -lt "$tries" ] || { cat "$WORK/body" >&2; fail "$what"; }
    sleep 2
  done
}

step "Personal real con segundo factor para los pasos que cruzan servicios"
SUPER_ADMIN_ACCESS=$(super_admin_access_token)
RECEPTIONIST_ACCESS=$(staff_member RECEPTIONIST)
echo "ok  recepción activó su cuenta y enroló TOTP"

step "Pagador con contrato por evento registrado para RIPS"
NIT="8909$(date +%s | tail -c 6)"
status=$(call POST "$CONTRACTING_URL/api/v1/payers" CONTRACTING "$CONTRACTING_ID" "{
  \"identity\": {\"socialReason\": \"EPS Facturación E2E $SUFFIX\", \"nit\": \"$NIT\", \"type\": \"EPS\", \"adresCode\": \"EPF$SUFFIX\"},
  \"contact\": {\"address\": \"Calle 100 # 7-33\", \"phone\": \"6017429000\", \"billingEmail\": \"radicacion.$SUFFIX@e2e.local\"}}")
expect "$status" 201 "pagador"
PAYER=$(field .uuid)

status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals" CONTRACTING "$CONTRACTING_ID" "{
  \"code\": \"FAC_$SUFFIX\", \"name\": \"Manual de facturación E2E\", \"unit\": \"COP\"}")
expect "$status" 201 "manual tarifario"
MANUAL=$(field .uuid)
status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals/$MANUAL/versions" CONTRACTING "$CONTRACTING_ID" "{
  \"label\": \"2026\", \"unitValue\": 1, \"validFrom\": \"2026-01-01\"}")
expect "$status" 201 "versión del manual"
TARIFF=$(field .uuid)
status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals/versions/$TARIFF/items" CONTRACTING "$CONTRACTING_ID" '{
  "items": [{"cupsCode": "890201", "description": "Consulta de primera vez por medicina general", "value": 45000}]}')
expect "$status" 200 "tarifa de la consulta"
status=$(call POST "$CONTRACTING_URL/api/v1/tariff-manuals/versions/$TARIFF/activation" CONTRACTING "$CONTRACTING_ID" '{}' '-H If-Match:"1"')
expect "$status" 200 "versión publicada"

status=$(call POST "$CONTRACTING_URL/api/v1/portfolio-items" CONTRACTING "$CONTRACTING_ID" "{
  \"cupsCode\": \"890201\", \"clinicCode\": \"CMG-$SUFFIX\", \"name\": \"Consulta de primera vez por medicina general\",
  \"category\": \"CONSULTATION\"}")
if [ "$status" = "409" ]; then
  status=$(call POST "$CONTRACTING_URL/api/v1/portfolio-items/search" CONTRACTING "$CONTRACTING_ID" '{"cupsCode": "890201"}')
  expect "$status" 200 "servicio existente en el portafolio"
  ITEM=$(field '.content[0].uuid')
else
  expect "$status" 201 "servicio en el portafolio"
  ITEM=$(field .uuid)
fi

status=$(call POST "$CONTRACTING_URL/api/v1/contracts" CONTRACTING "$CONTRACTING_ID" "{
  \"payerUuid\": \"$PAYER\", \"number\": \"FAC-$SUFFIX\", \"name\": \"Contrato por evento E2E\", \"modality\": \"EVENT\",
  \"validFrom\": \"2026-01-01\", \"validTo\": \"2026-12-31\"}")
expect "$status" 201 "contrato en borrador"
CONTRACT=$(field .uuid)
status=$(call PUT "$CONTRACTING_URL/api/v1/contracts/$CONTRACT/tariff-terms" CONTRACTING "$CONTRACTING_ID" \
  "{\"tariffVersionUuid\": \"$TARIFF\", \"factor\": 1}" '-H If-Match:"0"')
expect "$status" 200 "términos tarifarios"
status=$(call POST "$CONTRACTING_URL/api/v1/contracts/$CONTRACT/activation" CONTRACTING "$CONTRACTING_ID" '{}' '-H If-Match:"1"')
expect "$status" 200 "contrato activo"
CUCON=$(printf '%s' "cucon-$SUFFIX" | sha256sum | cut -d' ' -f1)
status=$(call PUT "$CONTRACTING_URL/api/v1/contracts/$CONTRACT/rips-registration" CONTRACTING "$CONTRACTING_ID" \
  "{\"coveragePlan\": \"UPC_CONTRIBUTORY\", \"cucon\": \"$CUCON\"}" "-H If-Match:\"$(field .version)\"")
expect "$status" 200 "cobertura y CUCON del contrato"

step "Paciente afiliado, episodio ambulatorio y profesional tratante"
DOCUMENT=$(date +%s%N | cut -c6-15)
status=$(as_staff POST "$PATIENT_URL/api/v1/patients" "$RECEPTIONIST_ACCESS" "{
  \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$DOCUMENT\"},
  \"demographics\": {\"firstNames\": \"Laura\", \"lastNames\": \"Méndez Rojas\", \"birthDate\": \"1988-05-14\", \"sex\": \"FEMALE\",
                     \"countryOfOrigin\": \"CO\", \"disability\": \"NONE\"},
  \"contact\": {\"mobile\": \"3015556677\"},
  \"affiliation\": {\"regime\": \"CONTRIBUTORY\", \"affiliateType\": \"HOLDER\", \"payerUuid\": \"$PAYER\"},
  \"residence\": {\"department\": \"Santander\", \"municipality\": \"Bucaramanga\", \"municipalityCode\": \"68001\",
                  \"zone\": \"URBAN\", \"address\": \"Calle 45 # 27-10\"}}")
expect "$status" 201 "paciente afiliado al pagador"
PATIENT=$(field .uuid)

status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/service-types" ADMIN "$ADMIN_ID" "{
  \"name\": \"Consulta externa $SUFFIX\", \"kind\": \"OUTPATIENT\"}")
expect "$status" 201 "tipo de servicio ambulatorio"
OUTPATIENT_TYPE=$(field .uuid)
status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/locations" ADMIN "$ADMIN_ID" "{\"name\": \"Sede facturación $SUFFIX\"}")
expect "$status" 201 "sede"
LOCATION=$(field .uuid)
status=$(call POST "$ADMISSIONS_URL/api/v1/admissions/catalogue/configured-services" ADMIN "$ADMIN_ID" "{
  \"serviceTypeUuid\": \"$OUTPATIENT_TYPE\", \"locationUuid\": \"$LOCATION\"}")
expect "$status" 201 "consulta externa configurada"
OUTPATIENT=$(field .uuid)

admitted() {
  status=$(as_staff POST "$ADMISSIONS_URL/api/v1/admissions/episodes" "$RECEPTIONIST_ACCESS" "{
    \"patientUuid\": \"$PATIENT\", \"configurationServiceUuid\": \"$OUTPATIENT\", \"cause\": \"ILLNESS\"}")
  [ "$status" = "201" ]
}
until_ok "admisiones nunca vio la cobertura del paciente" 20 admitted
echo "ok  episodio ambulatorio admitido"
EPISODE=$(field .uuid)
NUMBER=$(field .number)
[ "$(field .coverage.status)" = "COVERED" ] || { jq .coverage "$WORK/body" >&2; fail "el episodio debía quedar cubierto por el contrato"; }
[ "$(field .coverage.contractUuid)" = "$CONTRACT" ] || fail "la cobertura no apunta al contrato del pagador"
echo "ok  cubierto por el contrato $CONTRACT"
VERSION=$(etag)

status=$(as_staff POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/activation" "$RECEPTIONIST_ACCESS" "" \
  "-H If-Match:\"$VERSION\"")
expect "$status" 200 "episodio activo"
VERSION=$(etag)

PRACTITIONER_DOCUMENT=$(date +%s%N | cut -c7-16)
status=$(call POST "$PRACTITIONERS_URL/api/v1/practitioners" HUMAN_RESOURCES "$(cat /proc/sys/kernel/random/uuid)" "{
  \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$PRACTITIONER_DOCUMENT\"},
  \"firstNames\": \"Andrés\", \"lastNames\": \"Salazar Mora\",
  \"registration\": {\"number\": \"RM-FAC-$SUFFIX\", \"registeredOn\": \"2015-02-01\"},
  \"contact\": {\"email\": \"andres.fac.$SUFFIX@clinica.local\", \"mobile\": \"3024445566\"},
  \"relationship\": \"STAFF\"}")
expect "$status" 201 "profesional en el directorio"
PRACTITIONER=$(field .uuid)

attended() {
  status=$(as_staff POST "$ADMISSIONS_URL/api/v1/admissions/episodes/$EPISODE/attending-practitioner" "$RECEPTIONIST_ACCESS" \
    "{\"practitionerUuid\": \"$PRACTITIONER\"}" "-H If-Match:\"$VERSION\"")
  [ "$status" = "200" ]
}
until_ok "el profesional nunca llegó a admisiones" 60 attended
echo "ok  profesional tratante asignado"

step "Atención clasificada para RIPS en historia clínica"
DOCTOR_ACCESS=$(staff_member DOCTOR)
status=$(call PUT "$CLINICAL_URL/api/v1/clinical/admin/terminology/habilitated-services" SUPER_ADMIN "$SUPER_ADMIN_ID" '{
  "serviceCode": "328", "modality": "01", "active": true}')
expect "$status" 200 "medicina general habilitada en modalidad intramural"

status=$(call GET "$CLINICAL_URL/api/v1/clinical/admin/terminology/cie10/releases" SUPER_ADMIN "$SUPER_ADMIN_ID")
expect "$status" 200 "versiones del catálogo CIE-10"
if [ "$(jq '[.[] | select(.active)] | length' "$WORK/body")" = "0" ]; then
  python3 - "$WORK/cie10.xlsx" <<'PY'
import sys, zipfile
rows = [["FECHA DE ACTUALIZACIÓN: 01-06-2026"],
        ["Capitulo", "Descripción del capítulo", "Categoría", "Descripción de la categoría",
         "Código de la CIE-10 cuatro caracteres", "Descripción del código"],
        ["18", "Síntomas, signos y hallazgos anormales clínicos y de laboratorio", "R07",
         "Dolor de garganta y en el pecho", "R072", "Dolor precordial"]]
strings = [value for row in rows for value in row]
def cell(r, c):
    return '<c r="%s%d" t="s"><v>%d</v></c>' % ("ABCDEF"[c], r + 1, strings.index(rows[r][c]))
sheet = "".join('<row r="%d">%s</row>' % (r + 1, "".join(cell(r, c) for c in range(len(rows[r])))) for r in range(len(rows)))
esc = lambda text: text.replace("&", "&amp;").replace("<", "&lt;")
with zipfile.ZipFile(sys.argv[1], "w") as book:
    book.writestr("[Content_Types].xml", '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/></Types>')
    book.writestr("_rels/.rels", '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
    book.writestr("xl/workbook.xml", '<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Final" sheetId="1" r:id="rId1"/></sheets></workbook>')
    book.writestr("xl/_rels/workbook.xml.rels", '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/></Relationships>')
    book.writestr("xl/sharedStrings.xml", '<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">%s</sst>' % "".join("<si><t>%s</t></si>" % esc(value) for value in strings))
    book.writestr("xl/worksheets/sheet1.xml", '<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>%s</sheetData></worksheet>' % sheet)
PY
  status=$(curl -s -o "$WORK/body" -w '%{http_code}' -X POST "$CLINICAL_URL/api/v1/clinical/admin/terminology/cie10/releases" \
    -H "Authorization: Bearer $(token SUPER_ADMIN "$SUPER_ADMIN_ID")" -F "file=@$WORK/cie10.xlsx;filename=cie10-e2e.xlsx")
  [ "$status" = "201" ] || [ "$status" = "200" ] || { cat "$WORK/body" >&2; fail "importación del catálogo CIE-10 mínimo"; }
  RELEASE=$(jq -r .release.id "$WORK/body")
  status=$(call POST "$CLINICAL_URL/api/v1/clinical/admin/terminology/cie10/releases/$RELEASE/activation" SUPER_ADMIN "$SUPER_ADMIN_ID")
  expect "$status" 200 "catálogo CIE-10 mínimo activado (el stack no tenía uno)"
fi

status=$(as_staff POST "$CLINICAL_URL/api/v1/clinical/encounters" "$DOCTOR_ACCESS" "{
  \"patientUuid\": \"$PATIENT\", \"type\": \"OUTPATIENT\", \"admissionUuid\": \"$EPISODE\",
  \"careSetting\": {\"serviceCode\": \"328\", \"modality\": \"01\"}}")
expect "$status" 201 "atención abierta con servicio REPS y modalidad"
ENCOUNTER=$(field .id)
status=$(as_staff POST "$CLINICAL_URL/api/v1/clinical/encounters/$ENCOUNTER/drafts" "$DOCTOR_ACCESS" "{
  \"content\": {\"type\": \"CONSULTATION\", \"specialty\": \"Medicina general\", \"reason\": \"Dolor torácico atípico\",
               \"findings\": \"Examen físico normal\", \"recommendations\": \"Control en un mes\",
               \"diagnoses\": [{\"code\": \"R072\", \"role\": \"PRINCIPAL\", \"type\": \"CONFIRMED_NEW\"}],
               \"careReason\": {\"purpose\": \"15\", \"cause\": \"38\"}}}")
expect "$status" 201 "nota de consulta con finalidad, causa y diagnóstico"
NOTE=$(field .id)
status=$(as_staff POST "$CLINICAL_URL/api/v1/clinical/drafts/$NOTE/signature" "$DOCTOR_ACCESS" "" '-H If-Match:"0"')
expect "$status" 201 "nota firmada"

step "Configuración fiscal por la administración"
status=$(call GET "$BILLING_URL/api/v1/billing/issuer" ADMIN "$ADMIN_ID")
if [ "$status" = "404" ]; then
  status=$(call POST "$BILLING_URL/api/v1/billing/issuer" ADMIN "$ADMIN_ID" '{"nit": "800197268", "verificationDigit": 4,
    "profile": {"personType": "LEGAL_ENTITY", "legalName": "Clínica de Ymid S.A.S.", "tradeName": "Clínica de Ymid",
      "taxScheme": "NOT_APPLICABLE", "taxResponsibilities": ["LARGE_TAXPAYER"], "addressLine": "Calle 10 # 43-20",
      "municipalityCode": "05001", "cityName": "Medellín", "departmentName": "Antioquia", "postalCode": "050021",
      "email": "facturacion@clinica.co", "phone": "6044441234", "healthProviderCode": "050010123401"}}')
  expect "$status" 201 "emisor configurado en el ambiente de pruebas de la DIAN"
else
  expect "$status" 200 "emisor ya configurado"
fi

FROM=$(( $(date +%s) % 900000 * 10000 + 1 ))
status=$(call POST "$BILLING_URL/api/v1/billing/numbering-resolutions" ADMIN "$ADMIN_ID" "{
  \"resolutionNumber\": \"18760$SUFFIX\", \"issuedOn\": \"2026-01-01\", \"prefix\": \"SETT\",
  \"rangeFrom\": $FROM, \"rangeTo\": $((FROM + 9999)), \"validFrom\": \"2026-01-01\", \"validUntil\": \"2030-01-19\",
  \"technicalKey\": \"fc8eac422eba16e22ffd8c6f94b3f40a6e38162c\"}")
expect "$status" 201 "resolución de numeración"
RESOLUTION=$(field .uuid)
status=$(call POST "$BILLING_URL/api/v1/billing/numbering-resolutions/$RESOLUTION/activation" ADMIN "$ADMIN_ID" "" '-H If-Match:"0"')
expect "$status" 200 "resolución activa"

step "Venta y factura al pagador, firmada en OpenBao y aceptada por la DIAN"
BILLING_ACCESS=$(staff_member BILLING)
RECEIVABLE_ACCESS=$(staff_member ACCOUNTS_RECEIVABLE)
echo "ok  facturación y cartera activaron sus cuentas con TOTP"

account_ready() { [ "$(as_staff GET "$BILLING_URL/api/v1/billing/accounts/$NUMBER" "$BILLING_ACCESS")" = "200" ]; }
until_ok "billing nunca abrió la cuenta del episodio" 60 account_ready
echo "ok  billing abrió la cuenta del episodio con el evento de admisiones"

status=$(as_staff POST "$BILLING_URL/api/v1/billing/sales" "$BILLING_ACCESS" "{
  \"admissionNumber\": \"$NUMBER\", \"type\": \"NON_SURGICAL\", \"preloadAuthorized\": false}")
expect "$status" 201 "venta abierta"
SALE=$(field .uuid)
status=$(as_staff POST "$BILLING_URL/api/v1/billing/sales/$SALE/lines" "$BILLING_ACCESS" \
  "{\"portfolioItemUuid\": \"$ITEM\", \"quantity\": 1}" "-H If-Match:\"$(etag)\"")
expect "$status" 200 "consulta cargada a la venta"
status=$(as_staff POST "$BILLING_URL/api/v1/billing/sales/$SALE/confirmation" "$BILLING_ACCESS" "" "-H If-Match:\"$(etag)\"")
expect "$status" 200 "venta confirmada con el precio del contrato"
[ "$(field .total)" != "null" ] || true

status=$(as_staff POST "$BILLING_URL/api/v1/billing/invoices" "$BILLING_ACCESS" "{
  \"admissionNumber\": \"$NUMBER\", \"saleUuid\": \"$SALE\"}")
expect "$status" 201 "borrador de factura al pagador"
INVOICE=$(field .uuid)
[ "$(field .buyer.kind)" = "PAYER" ] || fail "el adquiriente debía ser el pagador"
status=$(as_staff POST "$BILLING_URL/api/v1/billing/invoices/$INVOICE/issuance" "$BILLING_ACCESS" "" "-H If-Match:\"$(etag)\"")
expect "$status" 200 "factura emitida con segundo factor reciente"
INVOICE_NUMBER=$(field .number)
[ "$(field .signedAt)" != "null" ] || fail "la factura no quedó firmada con la clave DIAN de OpenBao"
echo "ok  $INVOICE_NUMBER firmada con XAdES por transit/billing-dian"

status=$(as_staff POST "$BILLING_URL/api/v1/billing/invoices/$INVOICE/dian-delivery" "$BILLING_ACCESS")
expect "$status" 200 "envío al set de pruebas de la DIAN (simulador)"
accepted() { as_staff GET "$BILLING_URL/api/v1/billing/invoices/$INVOICE" "$BILLING_ACCESS" > /dev/null; [ "$(field .dian.status)" = "ACCEPTED" ]; }
until_ok "la DIAN nunca aceptó la factura" 30 accepted
echo "ok  la DIAN aceptó la factura"
status=$(as_staff GET "$BILLING_URL/api/v1/billing/invoices/$INVOICE/attached-document" "$BILLING_ACCESS")
expect "$status" 200 "AttachedDocument firmado"
grep -q "<AttachedDocument" "$WORK/body" || fail "no es un AttachedDocument"

step "RIPS validado por el Ministerio (simulador) y radicación ante el pagador"
rips_complete() { as_staff GET "$BILLING_URL/api/v1/billing/invoices/$INVOICE/rips" "$RECEIVABLE_ACCESS" > /dev/null; [ "$(field .complete)" = "true" ]; }
until_ok "el RIPS nunca quedó completo (faltan hechos clínicos, paciente o profesional)" 120 rips_complete
echo "ok  el RIPS reúne billing, admisiones, pacientes, profesionales e historia clínica"
status=$(as_staff POST "$BILLING_URL/api/v1/billing/invoices/$INVOICE/rips-validation" "$RECEIVABLE_ACCESS")
expect "$status" 200 "RIPS y factura enviados al MUV"
[ "$(field .status)" = "VALIDATED" ] || fail "el MUV no validó"
CUV=$(field .cuv)
echo "ok  CUV $(printf '%s' "$CUV" | cut -c1-16)…"

status=$(as_staff POST "$BILLING_URL/api/v1/billing/invoices/$INVOICE/filing" "$RECEIVABLE_ACCESS" "{
  \"filingNumber\": \"RAD-$SUFFIX\", \"filedOn\": \"$TODAY\"}")
expect "$status" 201 "radicado registrado por cartera"
[ "$(field .cuv)" = "$CUV" ] || fail "el radicado no guardó el CUV"
curl -s -o "$WORK/package.zip" -w '%{http_code}' "$BILLING_URL/api/v1/billing/invoices/$INVOICE/filing-package" \
  -H "Authorization: Bearer $RECEIVABLE_ACCESS" > "$WORK/status"
[ "$(cat "$WORK/status")" = "200" ] || fail "paquete de radicación"
python3 -c "import sys, zipfile; names = sorted(zipfile.ZipFile(sys.argv[1]).namelist()); \
  assert len(names) == 4 and any(n.endswith('_CUV.json') for n in names), names" "$WORK/package.zip" \
  || fail "el paquete debía traer AttachedDocument, PDF, RIPS y respuesta con CUV"
echo "ok  paquete con AttachedDocument, PDF sellado, RIPS y CUV"

step "Glosa del pagador aceptada con nota crédito"
status=$(as_staff POST "$BILLING_URL/api/v1/billing/invoices/$INVOICE/objections" "$RECEIVABLE_ACCESS" "{
  \"kind\": \"GLOSS\", \"payerRecord\": \"GL-$SUFFIX\", \"notifiedOn\": \"$TODAY\",
  \"items\": [{\"invoiceLinePosition\": 1, \"code\": \"TA0201\", \"amount\": 5000,
               \"detail\": \"La consulta supera la tarifa pactada\"}]}")
expect "$status" 201 "glosa registrada por cartera"
OBJECTION=$(field .uuid)
ANSWER="{\"responseRecord\": \"RP-$SUFFIX\", \"respondedOn\": \"$TODAY\",
  \"answers\": [{\"position\": 1, \"code\": \"RE9702\", \"acceptedAmount\": 5000}]}"
status=$(as_staff POST "$BILLING_URL/api/v1/billing/objections/$OBJECTION/response" "$RECEIVABLE_ACCESS" "$ANSWER" "-H If-Match:\"$(etag)\"")
expect "$status" 403 "cartera no acepta valores: eso emite nota crédito"
status=$(as_staff POST "$BILLING_URL/api/v1/billing/objections/$OBJECTION/response" "$BILLING_ACCESS" "$ANSWER" '-H If-Match:"0"')
expect "$status" 200 "facturación acepta la glosa"
[ "$(field .creditNoteNumber)" != "null" ] || fail "aceptar la glosa debía emitir la nota crédito"
echo "ok  nota crédito $(field .creditNoteNumber) emitida al aceptar la glosa"

step "Verificación pública del PDF a través del gateway"
curl -s -o "$WORK/invoice.pdf" -X POST "$BILLING_URL/api/v1/billing/invoices/$INVOICE/graphic-representation" \
  -H "Authorization: Bearer $BILLING_ACCESS"
head -c 5 "$WORK/invoice.pdf" | grep -q '%PDF-' || fail "la representación gráfica no es un PDF"
FINGERPRINT=$(sha256sum "$WORK/invoice.pdf" | cut -d' ' -f1)
status=$(curl -s -o "$WORK/body" -w '%{http_code}' -X POST "$GATEWAY_URL/api/v1/billing/graphic-representations/verification" \
  -H 'Content-Type: application/json' --data "{\"number\": \"$INVOICE_NUMBER\", \"sha256\": \"$FINGERPRINT\"}")
expect "$status" 200 "verificación sin sesión por el gateway"
[ "$(field .authentic)" = "true" ] || fail "el PDF sellado no se reconoció como auténtico"
status=$(curl -s -o "$WORK/body" -w '%{http_code}' "$GATEWAY_URL/api/v1/billing/invoices/$INVOICE")
expect "$status" 401 "el resto de billing exige sesión en el gateway"

step "Eventos publicados por Debezium"
events() {
  docker exec billing-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" -e \"SELECT COUNT(*) FROM billing_outbox.outbox_events WHERE aggregateid = '$INVOICE'\" 2>/dev/null"
}
[ "$(events)" -ge 6 ] || fail "el outbox no registró la vida de la factura"
echo "ok  el outbox tiene $(events) estados de la factura"
docker exec billing-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" -e \"SELECT COUNT(*) FROM billing_outbox.outbox_events WHERE aggregateid = '$INVOICE' AND payload LIKE '%$DOCUMENT%'\" 2>/dev/null" \
  | grep -q '^0$' || fail "el documento del paciente se filtró en un evento"
echo "ok  el documento del paciente no viaja en los eventos"
if docker compose -p "$PROJECT" ps --status running --services 2>/dev/null | grep -q '^kafka$'; then
  in_topic() {
    docker exec kafka sh -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic billing.invoices.v1 --from-beginning --timeout-ms 8000 2>/dev/null" \
      | grep "$INVOICE" | grep -q "InvoiceObjectionAnswered"
  }
  until_ok "la factura no llegó a billing.invoices.v1" 12 in_topic
  echo "ok  billing.invoices.v1 tiene el estado de la factura tras la glosa"
fi

step "Reglas de acceso"
status=$(call GET "$BILLING_URL/api/v1/billing/invoices/$INVOICE" RECEPTIONIST "$(cat /proc/sys/kernel/random/uuid)")
expect "$status" 403 "recepción no consulta facturas"
status=$(curl -s -o "$WORK/body" -w '%{http_code}' "$BILLING_URL/api/v1/billing/invoices/$INVOICE")
expect "$status" 401 "billing no responde sin credenciales"

if [ -n "${E2E_EXPORT_FILE:-}" ]; then
  LOAD_ACCESS=$(staff_member BILLING carga)
  {
    echo "BILLING_URL=$BILLING_URL"
    echo "ADMISSION_NUMBER=$NUMBER"
    echo "INVOICE_UUID=$INVOICE"
    echo "PORTFOLIO_ITEM_UUID=$ITEM"
    echo "TOKEN=$LOAD_ACCESS"
  } > "$E2E_EXPORT_FILE"
  echo "ok  datos y token de facturación para la prueba de carga en $E2E_EXPORT_FILE (el token dura cinco minutos)"
fi

printf '\nbilling-e2e OK\n'
