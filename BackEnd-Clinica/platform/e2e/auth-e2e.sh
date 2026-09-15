#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
TOOLS_IMAGE=clinica/openbao-tools:2.6.2
ISSUER="${AUTH_ISSUER:-http://localhost:8080/auth}"
REDIRECT_URI="${AUTH_GATEWAY_REDIRECT_URI:-http://localhost:8080/login/oauth2/code/clinica}"
SUPER_ADMIN="${AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL:-superadmin@clinica.local}"
published() { echo "http://$(docker compose -p "$PROJECT" port --index 1 "$1" "$2" 2>/dev/null)"; }
AUTH_URL="${AUTH_URL:-$(published auth-service 8086)}"
MAILPIT_URL="${MAILPIT_URL:-$(published mailpit 8025)}"
[ "$AUTH_URL" != "http://" ] && [ "$MAILPIT_URL" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define AUTH_URL y MAILPIT_URL" >&2; exit 1; }
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
JAR="$WORK/cookies"

step() { printf '\n== %s\n' "$1"; }
ok() { echo "ok  $1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

bao_root() {
  docker run --rm --user root --network "${PROJECT}_secrets-net" \
    -v "${PROJECT}_openbao_tls:/openbao/tls:ro" -v "${PROJECT}_openbao_bootstrap:/openbao/bootstrap:ro" \
    -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/openbao/tls/ca.crt \
    --entrypoint sh "$TOOLS_IMAGE" -c "BAO_TOKEN=\$(jq -r .root_token /openbao/bootstrap/init.json); export BAO_TOKEN; $*"
}

csrf_header() {
  curl -s -b "$JAR" -c "$JAR" "$AUTH_URL/api/v1/session" > "$WORK/session"
  echo "$(jq -r .csrf.headerName "$WORK/session"): $(jq -r .csrf.token "$WORK/session")"
}

api() {
  path=$1; body=$2
  curl -s -o "$WORK/body" -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST "$AUTH_URL$path" \
    -H 'Content-Type: application/json' -H "$(csrf_header)" --data "$body"
}

latest_token_mailed_to() {
  subject=$1
  attempt=0
  until curl -s "$MAILPIT_URL/api/v1/search?query=$(printf 'to:%s subject:"%s"' "$2" "$subject" | jq -sRr @uri)" > "$WORK/search" \
        && [ "$(jq '.messages | length' "$WORK/search")" -gt 0 ]; do
    attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || return 1; sleep 1
  done
  id=$(jq -r '.messages[0].ID' "$WORK/search")
  curl -s "$MAILPIT_URL/api/v1/message/$id" | jq -r .Text | grep -o 'token=[A-Za-z0-9_-]*' | head -1 | cut -d= -f2
}

client_assertion() {
  now=$(date +%s)
  header=$(printf '{"alg":"ES256","typ":"JWT"}' | b64url)
  payload=$(printf '{"iss":"api-gateway","sub":"api-gateway","aud":"%s","iat":%s,"exp":%s,"jti":"%s"}' \
    "$ISSUER" "$now" "$((now + 60))" "$(cat /proc/sys/kernel/random/uuid)" | b64url)
  input=$(printf '%s.%s' "$header" "$payload" | openssl base64 -A)
  signature=$(bao_root "bao write -field=signature transit/sign/api-gateway-client input=$input hash_algorithm=sha2-256 marshaling_algorithm=jws" | cut -d: -f3)
  printf '%s.%s.%s' "$header" "$payload" "$signature"
}

token_request() {
  curl -s -o "$WORK/tokens" -w '%{http_code}' -X POST "$AUTH_URL/oauth2/token" \
    --data-urlencode "client_id=api-gateway" \
    --data-urlencode "client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer" \
    --data-urlencode "client_assertion=$(client_assertion)" "$@"
}

PASSWORD="frase e2e $(date +%s) para el turno"

step "Primer SUPER_ADMIN"
activation=$(latest_token_mailed_to "Active su cuenta de la Clínica" "$SUPER_ADMIN" || true)
status=000
[ -n "$activation" ] && status=$(api /api/v1/activation "{\"token\": \"$activation\", \"password\": \"$PASSWORD\"}")
if [ "$status" = "204" ]; then
  ok "activación con el enlace del correo"
else
  curl -s -X DELETE "$MAILPIT_URL/api/v1/search?query=$(printf 'to:%s' "$SUPER_ADMIN" | jq -sRr @uri)" > /dev/null
  [ "$(api /api/v1/password-reset/requests "{\"email\": \"$SUPER_ADMIN\"}")" = "202" ] || fail "no se aceptó la solicitud de reseteo"
  reset=$(latest_token_mailed_to "Restablezca su contraseña de la Clínica" "$SUPER_ADMIN") || fail "no llegó el correo de reseteo"
  [ "$(api /api/v1/password-reset "{\"token\": \"$reset\", \"password\": \"$PASSWORD\"}")" = "204" ] || fail "no se restableció la contraseña"
  ok "ya estaba activo: contraseña restablecida con el enlace del correo"
fi

step "Authorization code + PKCE"
VERIFIER=$(openssl rand 32 | b64url)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | b64url)
STATE=$(openssl rand 12 | b64url)
AUTHORIZE="$AUTH_URL/oauth2/authorize?response_type=code&client_id=api-gateway&scope=openid%20profile&state=$STATE&code_challenge=$CHALLENGE&code_challenge_method=S256&redirect_uri=$(printf '%s' "$REDIRECT_URI" | jq -sRr @uri)"
location=$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR" -c "$JAR" "$AUTHORIZE")
case "$location" in */login*) ok "sin sesión redirige a la página de login del frontend";; *) fail "authorize respondió $location";; esac
[ "$(api /api/v1/login "{\"email\": \"$SUPER_ADMIN\", \"password\": \"$PASSWORD\"}")" = "200" ] || fail "login rechazado: $(cat "$WORK/body")"
continue_url=$(jq -r .continueUrl "$WORK/body")
callback=$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR" -c "$JAR" "$continue_url")
case "$callback" in "$REDIRECT_URI"*"state=$STATE"*) ok "login por API y redirección con código";; *) fail "callback inesperado: $callback";; esac
code=$(printf '%s' "$callback" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
[ "$(token_request --data-urlencode grant_type=authorization_code --data-urlencode "code=$code" \
      --data-urlencode "redirect_uri=$REDIRECT_URI" --data-urlencode "code_verifier=$VERIFIER")" = "200" ] || fail "token: $(cat "$WORK/tokens")"
ok "canje del código con private_key_jwt firmado en transit"

step "Tokens ES256"
ACCESS=$(jq -r .access_token "$WORK/tokens")
REFRESH=$(jq -r .refresh_token "$WORK/tokens")
claims=$(node "$DIR/verify-jwt.mjs" "$ACCESS" "$(curl -s "$AUTH_URL/oauth2/jwks")") || fail "la firma del access token no verificó con el JWKS"
[ "$(echo "$claims" | jq -r .role)" = "SUPER_ADMIN" ] && [ "$(echo "$claims" | jq -r '.aud | if type == "array" then .[0] else . end')" = "clinica-api" ] \
  && [ "$(echo "$claims" | jq -r .iss)" = "$ISSUER" ] || fail "claims inesperados: $claims"
ok "firma ES256 verificada con $(echo "$claims" | jq -r .kid) del JWKS; rol, audiencia y emisor correctos"
jwks=$(curl -s "$AUTH_URL/oauth2/jwks")
echo "$jwks" | jq -e 'all(.keys[]; has("d") | not)' > /dev/null || fail "el JWKS expone material privado"
ok "el JWKS solo publica claves públicas"
hash=$(printf '%s' "$REFRESH" | openssl dgst -sha256 -r | cut -d' ' -f1)
stored=$(docker exec auth-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" -e \"SELECT COUNT(*) FROM auth_sessions.authorization_tokens WHERE value_hash = '$hash'\" 2>/dev/null")
[ "$stored" = "1" ] || fail "el refresh token no está guardado como hash"
ok "el refresh token solo existe como SHA-256 en la base"

step "Rotación y reutilización"
[ "$(token_request --data-urlencode grant_type=refresh_token --data-urlencode "refresh_token=$REFRESH")" = "200" ] || fail "refresh: $(cat "$WORK/tokens")"
ROTATED=$(jq -r .refresh_token "$WORK/tokens")
ok "refresh rota el token"
[ "$(token_request --data-urlencode grant_type=refresh_token --data-urlencode "refresh_token=$REFRESH")" = "400" ] || fail "se aceptó un refresh reutilizado"
[ "$(token_request --data-urlencode grant_type=refresh_token --data-urlencode "refresh_token=$ROTATED")" = "400" ] || fail "la familia siguió viva"
ok "reutilizar un refresh rotado revoca toda la familia"

step "Frenado de intentos"
OTHER_JAR="$WORK/other"
JAR="$OTHER_JAR"
for attempt in 1 2 3 4 5; do
  [ "$(api /api/v1/login "{\"email\": \"$SUPER_ADMIN\", \"password\": \"intento fallido $attempt\"}")" = "401" ] || fail "fallo $attempt no respondió 401"
done
status=$(api /api/v1/login "{\"email\": \"$SUPER_ADMIN\", \"password\": \"$PASSWORD\"}")
[ "$status" = "429" ] || fail "tras 5 fallos se esperaba 429 y llegó $status"
ok "tras 5 fallos desde la misma dirección responde 429"
sleep 2
[ "$(api /api/v1/login "{\"email\": \"$SUPER_ADMIN\", \"password\": \"$PASSWORD\"}")" = "200" ] || fail "no se recuperó tras la espera"
ok "tras la espera vuelve a entrar y el contador se limpia"

printf '\nE2E de auth-service completo\n'
