#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
TOOLS_IMAGE=clinica/openbao-tools:2.6.2
NODES="openbao-1 openbao-2 openbao-3"
DIR=$(cd "$(dirname "$0")" && pwd)
PATIENT_URL="${PATIENT_URL:-http://$(docker compose -p "$PROJECT" port --index 1 patient-service 8081 2>/dev/null)}"
CLINICAL_URL="${CLINICAL_URL:-http://$(docker compose -p "$PROJECT" port --index 1 clinical-history-service 8089 2>/dev/null)}"
[ "$PATIENT_URL" != "http://" ] && [ "$CLINICAL_URL" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define PATIENT_URL y CLINICAL_URL" >&2; exit 1; }
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

step() { printf '\n== %s\n' "$1"; }
ok() { echo "ok  $1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }

bao_as() {
  credentials=$1
  shift
  docker run --rm --network "${PROJECT}_secrets-net" \
    -v "${PROJECT}_openbao_tls:/openbao/tls:ro" \
    -v "${PROJECT}_${credentials}:/credentials:ro" \
    -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/openbao/tls/ca.crt \
    --entrypoint sh "$TOOLS_IMAGE" -c "
      BAO_TOKEN=\$(bao write -field=token auth/approle/login role_id=\$(cat /credentials/role-id) secret_id=\$(cat /credentials/secret-id)) || exit 90
      export BAO_TOKEN
      $*"
}

bao_root() {
  docker run --rm --user root --network "${PROJECT}_secrets-net" \
    -v "${PROJECT}_openbao_tls:/openbao/tls:ro" \
    -v "${PROJECT}_openbao_bootstrap:/openbao/bootstrap:ro" \
    -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/openbao/tls/ca.crt \
    --entrypoint sh "$TOOLS_IMAGE" -c "BAO_TOKEN=\$(jq -r .root_token /openbao/bootstrap/init.json); export BAO_TOKEN; $*"
}

unsealed() { docker exec "$1" bao status > /dev/null 2>&1; }
follows_leader() { docker exec "$1" bao status -format=json 2>/dev/null | grep -q '"leader_address": "https://'; }
cluster_ready() {
  for node in $NODES; do
    unsealed "$node" && follows_leader "$node" || return 1
  done
  [ "$(voters)" = "3" ]
}
active_node() {
  for node in $NODES; do
    if docker exec "$node" bao status -format=json 2>/dev/null | grep -q '"is_self": true'; then
      echo "$node"
      return
    fi
  done
}
voters() { bao_root "bao operator raft list-peers -format=json | jq '[.data.config.servers[] | select(.voter)] | length'"; }

wait_until() {
  description=$1
  shift
  attempt=0
  until "$@"; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 60 ] || fail "$description"
    sleep 2
  done
}

token() { sh "$DIR/staff-token.sh" "$@"; }

clinical() {
  method=$1; path=$2; role=$3; body=${4:-}
  if [ -n "$body" ]; then
    curl -s -o "$WORK/body" -w '%{http_code}' -X "$method" "$CLINICAL_URL/api/v1/clinical$path" \
      -H "Authorization: Bearer $(token "$role")" -H 'Content-Type: application/json' --data "$body"
  else
    curl -s -o "$WORK/body" -w '%{http_code}' -X "$method" "$CLINICAL_URL/api/v1/clinical$path" -H "Authorization: Bearer $(token "$role")"
  fi
}

known_by_clinical() {
  docker exec clinical-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" \"\$MYSQL_DATABASE\" \
    -e \"SELECT COUNT(*) FROM patient_references WHERE uuid = '$1'\" 2>/dev/null" | grep -q '^1$'
}

register_patient() {
  document=$(date +%s%N | cut -c6-15)
  curl -s -o "$WORK/patient" -w '%{http_code}' -X POST "$PATIENT_URL/api/v1/patients" \
    -H "Authorization: Bearer $(token RECEPTIONIST)" -H 'Content-Type: application/json' --data "{
      \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$document\"},
      \"demographics\": {\"firstNames\": \"Ana\", \"lastNames\": \"Rojas Díaz\", \"birthDate\": \"1990-02-01\", \"sex\": \"FEMALE\",
                         \"countryOfOrigin\": \"CO\", \"disability\": \"NONE\"},
      \"contact\": {\"mobile\": \"3001234567\"},
      \"affiliation\": {\"regime\": \"UNINSURED\"},
      \"residence\": {\"department\": \"Santander\", \"municipality\": \"Girón\", \"zone\": \"URBAN\", \"address\": \"Calle 1 # 2-3\"}}"
}

step "Clúster"
wait_until "el clúster no quedó desellado con tres votantes y un nodo activo" cluster_ready
ok "tres nodos desellados, raft con tres votantes y activo en $(active_node)"

step "Políticas por servicio"
bao_as openbao_approle_patient "bao kv get -mount=secret patient/db/app > /dev/null" || fail "patient-service no lee su secreto"
ok "patient-service lee secret/patient/db/app"
status=0
bao_as openbao_approle_patient "bao kv get -mount=secret clinical/db/app > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "patient-service leyó un secreto de clinical-history-service"
ok "patient-service no lee secret/clinical/db/app"
status=0
bao_as openbao_approle_clinical "bao kv get -mount=secret patient/db/root > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "clinical-history-service leyó la contraseña root de patient-db"
ok "clinical-history-service no lee secret/patient/db/root"
status=0
bao_as openbao_approle_auth "bao kv get -mount=secret clinical/db/app > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "auth-service leyó un secreto de clinical-history-service"
bao_as openbao_approle_auth "bao kv get -mount=secret auth/db/app > /dev/null" || fail "auth-service no lee su secreto"
ok "auth-service lee solo secret/auth/db"
bao_as openbao_approle_auth "bao write -field=barcode totp/keys/staff-e2e generate=true issuer=Clinica account_name=e2e > /dev/null \
  && bao delete totp/keys/staff-e2e > /dev/null" || fail "auth-service no administra las claves TOTP del personal"
status=0
bao_as openbao_approle_auth "bao list totp/keys > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "auth-service pudo listar las claves TOTP"
status=0
bao_as openbao_approle_auth "bao write totp/keys/other generate=true issuer=Clinica account_name=e2e > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "auth-service creó una clave TOTP fuera de staff-*"
ok "auth-service administra solo las claves TOTP staff-* y no puede listarlas"
bao_as openbao_approle_gateway "bao kv get -mount=secret gateway/redis > /dev/null && bao write -field=signature transit/sign/api-gateway-client input=aGVsbG8= hash_algorithm=sha2-256 > /dev/null" \
  || fail "api-gateway no lee su secreto de Redis o no firma sus aserciones"
status=0
bao_as openbao_approle_gateway "bao kv get -mount=secret auth/db/app > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "api-gateway leyó un secreto de auth-service"
ok "api-gateway lee solo su Redis y firma solo con api-gateway-client"
status=0
bao_as openbao_approle_agent "bao kv put -mount=secret patient/db/app password=tampered > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "el agente pudo escribir un secreto"
ok "el agente de infraestructura solo lee"
status=0
bao_as openbao_approle_patient "bao write transit/sign/clinical-seal input=aGVsbG8= > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "patient-service firmó con el sello clínico"
ok "patient-service no firma con transit/clinical-seal"
status=0
bao_as openbao_approle_clinical "bao write -f transit/keys/clinical-kek/rotate > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "clinical-history-service rotó su clave maestra"
bao_as openbao_approle_clinical "bao write transit/sign/clinical-seal input=aGVsbG8= hash_algorithm=sha2-256 > /dev/null" \
  || fail "clinical-history-service no puede firmar con su sello"
ok "clinical-history-service firma con transit pero no rota sus claves"

step "Ningún secreto en la configuración de los contenedores"
secrets=$(bao_root "for path in patient/db/root patient/db/app patient/db/migrator patient/db/debezium clinical/db/root clinical/db/app clinical/db/migrator clinical/db/debezium clinical/storage/root auth/db/root auth/db/app auth/db/migrator auth/db/debezium gateway/redis; do bao kv get -mount=secret -field=password \$path; echo; done; bao kv get -mount=secret -field=secret-key clinical/storage/attachments")
containers=$(docker compose -p "$PROJECT" ps -a -q)
inspected=$(docker inspect $containers)
for secret in $secrets; do
  if echo "$inspected" | grep -qF "$secret"; then
    fail "un secreto aparece en docker inspect"
  fi
done
ok "docker inspect no expone contraseñas ni claves"

step "Conmutación: cae el nodo activo"
leader=$(active_node)
docker stop "$leader" > /dev/null
new_leader() { current=$(active_node); [ -n "$current" ] && [ "$current" != "$leader" ]; }
wait_until "no se eligió un nuevo nodo activo" new_leader
ok "$(active_node) asumió tras detener $leader"
bao_as openbao_approle_clinical "bao kv get -mount=secret clinical/db/app > /dev/null" || fail "sin lectura con un nodo caído"
ok "login AppRole y lectura con dos nodos"
docker restart "${PROJECT}-patient-service-2" > /dev/null
healthy() { [ "$(docker inspect -f '{{.State.Health.Status}}' "${PROJECT}-patient-service-2")" = "healthy" ]; }
wait_until "patient-service no arrancó con un nodo caído" healthy
ok "una réplica de patient-service arranca con un nodo caído"
docker start "$leader" > /dev/null
wait_until "$leader no se desselló ni volvió al clúster" cluster_ready
ok "$leader se desselló solo y volvió como votante"

step "Aislamiento: OpenBao completo caído"
[ "$(register_patient)" = "201" ] || fail "no se registró el paciente de la prueba"
PATIENT=$(jq -r .uuid "$WORK/patient")
wait_until "clinical-history-service no recibió el paciente" known_by_clinical "$PATIENT"
[ "$(clinical POST /encounters NURSE "{\"patientUuid\": \"$PATIENT\", \"type\": \"EMERGENCY\"}")" = "201" ] \
  || fail "no se abrió la atención sellada con transit"
for role in RECEPTIONIST NURSE ADMIN; do E2E_TOKEN_REFRESH=1 token "$role" > /dev/null; done
docker stop $NODES > /dev/null
[ "$(register_patient)" = "201" ] || fail "patient-service dejó de registrar pacientes sin OpenBao"
ok "patient-service sigue registrando pacientes con OpenBao caído"
[ "$(clinical GET "/patients/$PATIENT/integrity" ADMIN)" = "200" ] && [ "$(jq .verified "$WORK/body")" = "true" ] \
  || fail "sin OpenBao no se verificó la integridad de una cadena ya sellada"
ok "clinical-history-service verifica la cadena con las claves públicas en caché"
[ "$(clinical POST /encounters NURSE "{\"patientUuid\": \"$PATIENT\", \"type\": \"OUTPATIENT\"}")" = "503" ] \
  && grep -q CLINICAL_KEYS_UNAVAILABLE "$WORK/body" || fail "sellar sin OpenBao no respondió 503 CLINICAL_KEYS_UNAVAILABLE"
ok "sellar sin OpenBao responde 503 CLINICAL_KEYS_UNAVAILABLE sin tumbar el servicio"
docker start $NODES > /dev/null
wait_until "el clúster no se recuperó" cluster_ready
ok "el clúster volvió desellado sin intervención"

step "Auditoría"
audited=0
for node in $NODES; do
  if docker exec "$node" grep -q '"path":"auth/approle/login"' /openbao/logs/audit.log; then
    audited=1
  fi
done
[ "$audited" -eq 1 ] || fail "sin logins en el registro de auditoría"
ok "los logins AppRole quedan en el registro de auditoría"

printf '\nE2E de OpenBao completo\n'
