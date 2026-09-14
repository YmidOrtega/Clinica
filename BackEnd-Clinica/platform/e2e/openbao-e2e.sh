#!/bin/sh
set -eu

: "${JWT_PRIVATE_KEY:?JWT_PRIVATE_KEY must point to the RSA private key matching JWT_PUBLIC_KEY}"
PROJECT="${COMPOSE_PROJECT:-clinica}"
TOOLS_IMAGE=clinica/openbao-tools:2.6.2
NODES="openbao-1 openbao-2 openbao-3"
DIR=$(cd "$(dirname "$0")" && pwd)
PATIENT_URL="${PATIENT_URL:-http://$(docker compose -p "$PROJECT" port --index 1 patient-service 8081 2>/dev/null)}"
[ "$PATIENT_URL" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define PATIENT_URL" >&2; exit 1; }

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

register_patient() {
  document=$(date +%s%N | cut -c6-15)
  token=$(node "$DIR/generate-token.mjs" "$JWT_PRIVATE_KEY" RECEPTIONIST "$(cat /proc/sys/kernel/random/uuid)")
  curl -s -o /dev/null -w '%{http_code}' -X POST "$PATIENT_URL/api/v1/patients" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' --data "{
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
bao_as openbao_approle_agent "bao kv put -mount=secret patient/db/app password=tampered > /dev/null 2>&1" || status=$?
[ "$status" -ne 0 ] && [ "$status" -ne 90 ] || fail "el agente pudo escribir un secreto"
ok "el agente de infraestructura solo lee"

step "Ningún secreto en la configuración de los contenedores"
secrets=$(bao_root "for path in patient/db/root patient/db/app patient/db/migrator patient/db/debezium clinical/db/root clinical/db/app clinical/db/migrator clinical/db/debezium clinical/storage/root; do bao kv get -mount=secret -field=password \$path; echo; done; bao kv get -mount=secret -field=secret-key clinical/storage/attachments")
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
docker stop $NODES > /dev/null
[ "$(register_patient)" = "201" ] || fail "patient-service dejó de registrar pacientes sin OpenBao"
ok "patient-service sigue registrando pacientes con OpenBao caído"
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
