#!/bin/sh
set -eu

export BAO_ADDR="${BAO_ADDR:-https://openbao-1:8200}"
export BAO_CACERT=/openbao/tls/ca.crt
BOOTSTRAP_DIR=/openbao/bootstrap
POLICIES_DIR=/openbao/policies
CREDENTIALS_DIR=/openbao/approle
NODES="openbao-1 openbao-2 openbao-3"
APPROLES="patient-service clinical-history-service auth-service infra-agent"

random_secret() {
  openssl rand -base64 36 | tr -d '/+=\n' | cut -c1-40
}

wait_for() {
  description=$1
  shift
  attempt=0
  until "$@" > /dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 90 ]; then
      echo "Timed out waiting for $description" >&2
      exit 1
    fi
    sleep 2
  done
}

responds() {
  status=0
  BAO_ADDR="https://$1:8200" bao status -format=json > /dev/null 2>&1 || status=$?
  [ "$status" -ne 1 ]
}

unsealed() {
  BAO_ADDR="https://$1:8200" bao status > /dev/null 2>&1
}

voters() {
  [ "$(bao operator raft list-peers -format=json | jq '[.data.config.servers[] | select(.voter)] | length')" -eq 3 ]
}

for node in $NODES; do
  wait_for "$node to respond" responds "$node"
done

if [ "$(bao status -format=json | jq -r .initialized)" != "true" ]; then
  bao operator init -format=json -recovery-shares=5 -recovery-threshold=3 > "$BOOTSTRAP_DIR/init.json.tmp"
  chmod 0400 "$BOOTSTRAP_DIR/init.json.tmp"
  mv "$BOOTSTRAP_DIR/init.json.tmp" "$BOOTSTRAP_DIR/init.json"
  echo "Initialized the OpenBao cluster"
fi

BAO_TOKEN=$(jq -r .root_token "$BOOTSTRAP_DIR/init.json")
export BAO_TOKEN

for node in $NODES; do
  wait_for "$node to unseal" unsealed "$node"
done
wait_for "three raft voters" voters

if ! bao secrets list -format=json | jq -e 'has("secret/")' > /dev/null; then
  bao secrets enable -path=secret -version=2 kv
fi

if ! bao secrets list -format=json | jq -e 'has("transit/")' > /dev/null; then
  bao secrets enable transit
fi

if ! bao secrets list -format=json | jq -e 'has("totp/")' > /dev/null; then
  bao secrets enable totp
fi

transit_key() {
  if ! bao read "transit/keys/$1" > /dev/null 2>&1; then
    bao write -f "transit/keys/$1" type="$2" exportable=false allow_plaintext_backup=false > /dev/null
    echo "Created transit key $1 ($2)"
  fi
}

transit_key clinical-kek aes256-gcm96
transit_key clinical-seal ecdsa-p256
transit_key auth-jwt ecdsa-p256
bao write "transit/keys/auth-jwt/config" auto_rotate_period=720h > /dev/null
transit_key api-gateway-client ecdsa-p256

if ! bao auth list -format=json | jq -e 'has("approle/")' > /dev/null; then
  bao auth enable approle
fi

for role in $APPROLES; do
  bao policy write "$role" "$POLICIES_DIR/$role.hcl" > /dev/null
  bao write "auth/approle/role/$role" token_policies="$role" token_ttl=1h token_max_ttl=24h \
    secret_id_ttl=0 secret_id_num_uses=0 > /dev/null

  directory="$CREDENTIALS_DIR/$role"
  mkdir -p "$directory"
  bao read -field=role_id "auth/approle/role/$role/role-id" > "$directory/role-id"
  if [ ! -s "$directory/secret-id" ] || \
     ! bao write -format=json "auth/approle/role/$role/secret-id/lookup" secret_id="$(cat "$directory/secret-id")" 2>/dev/null | jq -e '.data' > /dev/null; then
    bao write -f -field=secret_id "auth/approle/role/$role/secret-id" > "$directory/secret-id"
    echo "Issued a secret-id for $role"
  fi
  chmod 0444 "$directory/role-id" "$directory/secret-id"
done

seed() {
  path=$1
  shift
  if bao kv get -mount=secret "$path" > /dev/null 2>&1; then
    return
  fi
  bao kv put -mount=secret -cas=0 "$path" "$@" > /dev/null
  echo "Seeded secret/$path"
}

for service in patient clinical; do
  seed "$service/db/root" password="$(random_secret)"
  seed "$service/db/migrator" username="${service}_migrator" password="$(random_secret)"
  seed "$service/db/app" username="${service}_app" password="$(random_secret)"
  seed "$service/db/debezium" username="${service}_debezium" password="$(random_secret)"
done
seed auth/db/root password="$(random_secret)"
seed auth/db/migrator username="auth_migrator" password="$(random_secret)"
seed auth/db/app username="auth_app" password="$(random_secret)"
seed auth/bootstrap super-admin-email="superadmin@clinica.local" super-admin-name="Administración Inicial"
seed clinical/storage/root username="clinical-storage-admin" password="$(random_secret)"
seed clinical/storage/attachments access-key="clinical-history-app" secret-key="$(random_secret)"

echo "OpenBao configured"
