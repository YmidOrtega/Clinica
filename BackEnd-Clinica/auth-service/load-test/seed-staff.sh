#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
COUNT="${STAFF_COUNT:-1500}"
SUPER_ADMIN="${AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL:-superadmin@clinica.local}"
PASSWORD="${LOAD_PASSWORD:-frase de la prueba de carga del turno}"
DIR=$(cd "$(dirname "$0")" && pwd)
OUT="${STAFF_FILE:-$DIR/staff.csv}"
AUTH_URL="${AUTH_URL:-http://$(docker compose -p "$PROJECT" port --index 1 auth-service 8086 2>/dev/null)}"
MAILPIT_URL="${MAILPIT_URL:-http://$(docker compose -p "$PROJECT" port --index 1 mailpit 8025 2>/dev/null)}"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
JAR="$WORK/cookies"
. "$DIR/../../platform/e2e/staff-login.sh"

mysql_root() {
  docker exec -i auth-db sh -c 'mysql -N -uroot -p"$(cat "$MYSQL_ROOT_PASSWORD_FILE")" "$MYSQL_DATABASE" 2>/dev/null'
}

step "Contraseña conocida"
prepare_super_admin
HASH=$(echo "SELECT password_hash FROM users WHERE email = '$SUPER_ADMIN'" | mysql_root)
case "$HASH" in '$argon2id$'*) ok "hash Argon2id de la contraseña de carga tomado del SUPER_ADMIN" ;; *) fail "no se obtuvo el hash" ;; esac

step "Personal sintético"
node -e '
const { randomBytes, randomUUID } = require("node:crypto");
const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
const base32 = (bytes) => { let bits = 0, value = 0, out = ""; for (const byte of bytes) { value = (value << 8) | byte; bits += 8;
  while (bits >= 5) { out += alphabet[(value >>> (bits - 5)) & 31]; bits -= 5; } } return bits > 0 ? out + alphabet[(value << (5 - bits)) & 31] : out; };
const roles = ["DOCTOR", "NURSE", "RECEPTIONIST"];
console.log("uuid,email,role,secret");
for (let index = 1; index <= Number(process.argv[1]); index++) {
  console.log([randomUUID(), `carga${String(index).padStart(5, "0")}@clinica.load`, roles[index % roles.length], base32(randomBytes(20))].join(","));
}' "$COUNT" > "$OUT"

{
  echo "DELETE FROM users WHERE email LIKE 'carga%@clinica.load';"
  tail -n +2 "$OUT" | awk -F, -v hash="$HASH" '{
    printf "INSERT INTO users (uuid, version, email, full_name, role, status, status_changed_at, password_hash, credential_state, password_changed_at, tokens_not_before, created_at, updated_at, second_factor_state, second_factor_enrolled_at) VALUES (\"%s\", 0, \"%s\", \"Personal de Carga\", \"%s\", \"ACTIVE\", NOW(6) - INTERVAL 1 DAY, \"%s\", \"CURRENT\", NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 1 DAY, \"TOTP_ENROLLED\", NOW(6) - INTERVAL 1 DAY);\n", $1, $2, $3, hash
  }'
} | mysql_root
[ "$(echo "SELECT COUNT(*) FROM users WHERE email LIKE 'carga%@clinica.load'" | mysql_root)" = "$COUNT" ] || fail "no se sembraron $COUNT usuarios"
ok "$COUNT usuarios activos con TOTP en auth-db"

tail -n +2 "$OUT" | docker run --rm -i --user root --network "${PROJECT}_secrets-net" \
  -v "${PROJECT}_openbao_tls:/openbao/tls:ro" -v "${PROJECT}_openbao_bootstrap:/openbao/bootstrap:ro" \
  -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/openbao/tls/ca.crt \
  --entrypoint sh clinica/openbao-tools:2.6.2 -c '
    export BAO_TOKEN=$(jq -r .root_token /openbao/bootstrap/init.json)
    while IFS=, read -r uuid email role secret; do
      bao write "totp/keys/staff-$uuid" key="$secret" issuer=Clinica account_name="$email" period=30 digits=6 algorithm=SHA1 > /dev/null
    done'
ok "claves TOTP importadas en OpenBao; secretos en $OUT"
