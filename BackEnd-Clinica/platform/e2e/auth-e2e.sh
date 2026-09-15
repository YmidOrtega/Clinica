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
PATIENT_URL="${PATIENT_URL:-$(published patient-service 8081)}"
CLINICAL_URL="${CLINICAL_URL:-$(published clinical-history-service 8089)}"
[ "$AUTH_URL" != "http://" ] && [ "$MAILPIT_URL" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define AUTH_URL y MAILPIT_URL" >&2; exit 1; }
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
JAR="$WORK/cookies"
TOTP_STATE="${E2E_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/clinica-e2e}/$PROJECT-super-admin-totp.json"

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

totp() {
  set -- $(node "$DIR/totp-code.mjs" "$(jq -r .otpauthUrl "$TOTP_STATE")" "$(jq -r .lastPeriod "$TOTP_STATE")")
  jq --argjson period "$2" '.lastPeriod = $period' "$TOTP_STATE" > "$TOTP_STATE.tmp" && mv "$TOTP_STATE.tmp" "$TOTP_STATE"
  echo "$1"
}

session_field() {
  curl -s -b "$JAR" -c "$JAR" "$AUTH_URL/api/v1/session" | jq -r "$1"
}

bearer() {
  method=$1; target=$2; shift 2
  curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$AUTH_URL$target" \
    -H "Authorization: Bearer $STAFF_ACCESS" -H 'Content-Type: application/json' "$@"
}

etag() { tr -d '\r' < "$WORK/headers" | sed -n 's/^[Ee][Tt][Aa][Gg]: //p'; }

topic_events() {
  docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic "$1" \
    --from-beginning --timeout-ms 15000 --property print.key=true 2>/dev/null | grep "^$2" || true
}

topic_config() {
  docker exec kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server kafka:9092 --entity-type topics --entity-name "$1" --describe --all 2>/dev/null \
    | tr ' ' '\n' | sed -n "s/^$2=//p" | head -1
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
outcome=$(jq -r .outcome "$WORK/body")
[ "$(session_field .authenticated)" = "false" ] || fail "la contraseña sola autenticó la sesión"
ok "la contraseña sola no autentica: $outcome"

step "Segundo factor"
case "$outcome" in
  SECOND_FACTOR_ENROLLMENT_REQUIRED)
    [ "$(api /api/v1/login/second-factor/enrollment '{}')" = "200" ] || fail "enrolamiento: $(cat "$WORK/body")"
    jq -e '(.qrPngBase64 | length > 0) and (.otpauthUrl | startswith("otpauth://totp/"))' "$WORK/body" > /dev/null \
      || fail "el enrolamiento no trajo el QR y la URL otpauth"
    mkdir -p "$(dirname "$TOTP_STATE")"
    (umask 077 && jq '{otpauthUrl, lastPeriod: -1}' "$WORK/body" > "$TOTP_STATE")
    code=$(totp)
    [ "$(api /api/v1/login/second-factor/enrollment/confirmation "{\"code\": \"$code\"}")" = "200" ] || fail "confirmación: $(cat "$WORK/body")"
    [ "$(jq '.recoveryCodes | length' "$WORK/body")" = "10" ] || fail "se esperaban 10 códigos de recuperación: $(cat "$WORK/body")"
    ok "TOTP enrolado en OpenBao en el primer login; 10 códigos de recuperación entregados una vez"
    ;;
  SECOND_FACTOR_REQUIRED)
    [ -f "$TOTP_STATE" ] || fail "el SUPER_ADMIN ya tiene TOTP y falta $TOTP_STATE; recrear auth-db para enrolarlo de nuevo"
    code=$(totp)
    [ "$(api /api/v1/login/second-factor "{\"code\": \"$code\"}")" = "200" ] || fail "segundo factor: $(cat "$WORK/body")"
    ok "código TOTP verificado en OpenBao"
    ;;
  *) fail "resultado inesperado del login: $outcome" ;;
esac
continue_url=$(jq -r .continueUrl "$WORK/body")
MAIN_JAR="$JAR"
JAR="$WORK/replay"
api /api/v1/login "{\"email\": \"$SUPER_ADMIN\", \"password\": \"$PASSWORD\"}" > /dev/null
status=$(api /api/v1/login/second-factor "{\"code\": \"$code\"}")
[ "$status" = "401" ] || fail "un código TOTP ya usado respondió $status"
ok "un código TOTP ya usado se rechaza en otra sesión"
JAR="$MAIN_JAR"
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
  && [ "$(echo "$claims" | jq -r .iss)" = "$ISSUER" ] && [ "$(echo "$claims" | jq -r '.amr | join(",")')" = "pwd,otp,mfa" ] \
  && [ "$(echo "$claims" | jq -r .acr)" = "urn:clinica:acr:mfa" ] || fail "claims inesperados: $claims"
ok "firma ES256 verificada con $(echo "$claims" | jq -r .kid) del JWKS; rol, audiencia, emisor, amr y acr correctos"
jwks=$(curl -s "$AUTH_URL/oauth2/jwks")
echo "$jwks" | jq -e 'all(.keys[]; has("d") | not)' > /dev/null || fail "el JWKS expone material privado"
ok "el JWKS solo publica claves públicas"
hash=$(printf '%s' "$REFRESH" | openssl dgst -sha256 -r | cut -d' ' -f1)
stored=$(docker exec auth-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" -e \"SELECT COUNT(*) FROM auth_sessions.authorization_tokens WHERE value_hash = '$hash'\" 2>/dev/null")
[ "$stored" = "1" ] || fail "el refresh token no está guardado como hash"
ok "el refresh token solo existe como SHA-256 en la base"

step "Step-up con max_age"
sleep 2
location=$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR" -c "$JAR" "$AUTHORIZE&max_age=1")
case "$location" in *step=step-up*) ok "una sesión más vieja que max_age redirige al step-up";; *) fail "authorize con max_age respondió $location";; esac
[ "$(session_field .pendingStep)" = "STEP_UP" ] || fail "la sesión no quedó pendiente de step-up"
[ "$(api /api/v1/login/step-up "{\"code\": \"$(totp)\"}")" = "200" ] || fail "step-up: $(cat "$WORK/body")"
callback=$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR" -c "$JAR" "$(jq -r .continueUrl "$WORK/body")")
code=$(printf '%s' "$callback" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
[ -n "$code" ] || fail "el step-up no devolvió al cliente con código: $callback"
[ "$(token_request --data-urlencode grant_type=authorization_code --data-urlencode "code=$code" \
      --data-urlencode "redirect_uri=$REDIRECT_URI" --data-urlencode "code_verifier=$VERIFIER")" = "200" ] || fail "token tras step-up: $(cat "$WORK/tokens")"
STAFF_ACCESS=$(jq -r .access_token "$WORK/tokens")
stepped=$(node "$DIR/verify-jwt.mjs" "$STAFF_ACCESS" "$(curl -s "$AUTH_URL/oauth2/jwks")")
[ "$(echo "$stepped" | jq -r .auth_time)" -gt "$(echo "$claims" | jq -r .auth_time)" ] || fail "auth_time no avanzó: $stepped"
ok "tras el step-up el token trae un auth_time nuevo"

step "API de usuarios con Bearer"
[ "$(bearer GET /api/v1/me)" = "200" ] && [ "$(jq -r .user.role "$WORK/body")" = "SUPER_ADMIN" ] || fail "/api/v1/me: $(cat "$WORK/body")"
ok "/api/v1/me responde con el access token"
NURSE_EMAIL="enfermera.e2e.$(date +%s)@clinica.local"
[ "$(bearer POST /api/v1/users --data "{\"email\": \"$NURSE_EMAIL\", \"fullName\": \"Enfermera de Prueba\", \"role\": \"NURSE\"}")" = "201" ] \
  || fail "invitación: $(cat "$WORK/body")"
NURSE_UUID=$(jq -r .uuid "$WORK/body")
latest_token_mailed_to "Active su cuenta de la Clínica" "$NURSE_EMAIL" > /dev/null || fail "no llegó la invitación a Mailpit"
ok "SUPER_ADMIN invita a una enfermera y el correo de activación llega"
[ "$(bearer POST "/api/v1/users/$NURSE_UUID/deactivation" --data '{"reason": "Prueba E2E de desactivación"}')" = "428" ] \
  || fail "desactivar sin If-Match no respondió 428"
[ "$(bearer GET "/api/v1/users/$NURSE_UUID")" = "200" ] || fail "consulta del usuario: $(cat "$WORK/body")"
[ "$(bearer POST "/api/v1/users/$NURSE_UUID/deactivation" -H "If-Match: $(etag)" --data '{"reason": "Prueba E2E de desactivación"}')" = "200" ] \
  && [ "$(jq -r .status.code "$WORK/body")" = "DEACTIVATED" ] || fail "desactivación: $(cat "$WORK/body")"
ok "desactivar exige If-Match con la versión consultada"
[ "$(bearer GET "/api/v1/users/$NURSE_UUID/history")" = "200" ] \
  && [ "$(jq -r 'last.revisedBy' "$WORK/body")" = "$(echo "$stepped" | jq -r .sub)" ] || fail "historial: $(cat "$WORK/body")"
ok "el historial registra quién hizo el cambio"

if [ "$PATIENT_URL" = "http://" ] || [ "$CLINICAL_URL" = "http://" ]; then
  printf '\n== Tokens de auth en patient y clinical\nomitido: publica patient-service y clinical-history-service para probarlo\n'
else
  step "Tokens de auth en patient y clinical"
  DOCTOR_EMAIL="medica.e2e.$(date +%s)@clinica.local"
  DOCTOR_PASSWORD="frase de la medica $(date +%s) en consulta"
  [ "$(bearer POST /api/v1/users --data "{\"email\": \"$DOCTOR_EMAIL\", \"fullName\": \"Médica de Prueba\", \"role\": \"DOCTOR\"}")" = "201" ] \
    || fail "invitación de la médica: $(cat "$WORK/body")"
  DOCTOR_UUID=$(jq -r .uuid "$WORK/body")
  ADMIN_JAR="$JAR"
  JAR="$WORK/doctor"
  activation=$(latest_token_mailed_to "Active su cuenta de la Clínica" "$DOCTOR_EMAIL") || fail "no llegó la invitación de la médica"
  [ "$(api /api/v1/activation "{\"token\": \"$activation\", \"password\": \"$DOCTOR_PASSWORD\"}")" = "204" ] || fail "activación de la médica"
  DOCTOR_VERIFIER=$(openssl rand 32 | b64url)
  DOCTOR_STATE=$(openssl rand 12 | b64url)
  curl -s -o /dev/null -b "$JAR" -c "$JAR" "$AUTH_URL/oauth2/authorize?response_type=code&client_id=api-gateway&scope=openid%20profile&state=$DOCTOR_STATE&code_challenge=$(printf '%s' "$DOCTOR_VERIFIER" | openssl dgst -sha256 -binary | b64url)&code_challenge_method=S256&redirect_uri=$(printf '%s' "$REDIRECT_URI" | jq -sRr @uri)"
  api /api/v1/login "{\"email\": \"$DOCTOR_EMAIL\", \"password\": \"$DOCTOR_PASSWORD\"}" > /dev/null
  [ "$(api /api/v1/login/second-factor/enrollment '{}')" = "200" ] || fail "enrolamiento de la médica: $(cat "$WORK/body")"
  doctor_code=$(node "$DIR/totp-code.mjs" "$(jq -r .otpauthUrl "$WORK/body")" | cut -d' ' -f1)
  [ "$(api /api/v1/login/second-factor/enrollment/confirmation "{\"code\": \"$doctor_code\"}")" = "200" ] || fail "confirmación de la médica"
  callback=$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR" -c "$JAR" "$(jq -r .continueUrl "$WORK/body")")
  [ "$(token_request --data-urlencode grant_type=authorization_code --data-urlencode "code=$(printf '%s' "$callback" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')" \
        --data-urlencode "redirect_uri=$REDIRECT_URI" --data-urlencode "code_verifier=$DOCTOR_VERIFIER")" = "200" ] || fail "token de la médica: $(cat "$WORK/tokens")"
  DOCTOR_ACCESS=$(jq -r .access_token "$WORK/tokens")
  JAR="$ADMIN_JAR"
  ok "una médica invitada activa su cuenta, enrola TOTP y obtiene su token"

  docker pause kafka-connect > /dev/null
  document=$(date +%s%N | cut -c6-15)
  status=$(curl -s -o "$WORK/patient" -w '%{http_code}' -X POST "$PATIENT_URL/api/v1/patients" -H "Authorization: Bearer $STAFF_ACCESS" \
    -H 'Content-Type: application/json' --data "{
      \"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$document\"},
      \"demographics\": {\"firstNames\": \"Ana\", \"lastNames\": \"Rojas Díaz\", \"birthDate\": \"1990-02-01\", \"sex\": \"FEMALE\",
                         \"countryOfOrigin\": \"CO\", \"disability\": \"NONE\"},
      \"contact\": {\"mobile\": \"3001234567\"}, \"affiliation\": {\"regime\": \"UNINSURED\"},
      \"residence\": {\"department\": \"Santander\", \"municipality\": \"Girón\", \"zone\": \"URBAN\", \"address\": \"Calle 1 # 2-3\"}}")
  [ "$status" = "201" ] || { docker unpause kafka-connect > /dev/null; fail "patient-service rechazó el token del SUPER_ADMIN ($status): $(cat "$WORK/patient")"; }
  PATIENT_UUID=$(jq -r .uuid "$WORK/patient")
  ok "patient-service acepta el access token ES256 emitido por auth-service"

  status=$(curl -s -o "$WORK/encounter" -w '%{http_code}' -X POST "$CLINICAL_URL/api/v1/clinical/encounters" -H "Authorization: Bearer $DOCTOR_ACCESS" \
    -H 'Content-Type: application/json' --data "{\"patientUuid\": \"$PATIENT_UUID\", \"type\": \"OUTPATIENT\"}")
  docker unpause kafka-connect > /dev/null
  [ "$status" = "201" ] || fail "clinical no abrió la atención ($status): $(cat "$WORK/encounter")"
  exchanged=$(docker exec auth-db sh -c "mysql -N -uroot -p\"\$(cat \$MYSQL_ROOT_PASSWORD_FILE)\" -e \"SELECT COUNT(*) FROM auth_sessions.authorizations
    WHERE principal_name = '$DOCTOR_UUID' AND grant_type = 'urn:ietf:params:oauth:grant-type:token-exchange' AND registered_client_id = 'clinical-history-service'\" 2>/dev/null")
  [ "$exchanged" -ge 1 ] || fail "clinical no intercambió el token de la médica"
  ok "clinical intercambia el token de la médica por uno para patient-service y abre la atención"

  [ "$(curl -s -o /dev/null -w '%{http_code}' "$PATIENT_URL/api/v1/patients/$PATIENT_UUID" -H "Authorization: Bearer $DOCTOR_ACCESS")" = "200" ] \
    || fail "patient-service rechazó a la médica activa"
  [ "$(bearer GET "/api/v1/users/$DOCTOR_UUID")" = "200" ] || fail "consulta de la médica"
  [ "$(bearer POST "/api/v1/users/$DOCTOR_UUID/suspension" -H "If-Match: $(etag)" --data '{"reason": "Prueba E2E de revocación"}')" = "200" ] \
    || fail "suspensión de la médica: $(cat "$WORK/body")"
  attempt=0
  until [ "$(curl -s -o /dev/null -w '%{http_code}' "$PATIENT_URL/api/v1/patients/$PATIENT_UUID" -H "Authorization: Bearer $DOCTOR_ACCESS")" = "401" ]; do
    attempt=$((attempt + 1)); [ "$attempt" -lt 30 ] || fail "patient-service siguió aceptando el token de la médica suspendida"; sleep 1
  done
  ok "tras suspenderla, patient-service rechaza su token vigente en ${attempt}s vía auth.users.v1"
fi

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
[ "$(api /api/v1/login "{\"email\": \"$SUPER_ADMIN\", \"password\": \"$PASSWORD\"}")" = "200" ] \
  && [ "$(jq -r .outcome "$WORK/body")" = "SECOND_FACTOR_REQUIRED" ] || fail "no se recuperó tras la espera: $(cat "$WORK/body")"
ok "tras la espera vuelve a entrar y el contador se limpia"

step "Eventos en Kafka"
SUPER_ADMIN_UUID=$(echo "$stepped" | jq -r .sub)
topic_events auth.users.v1 "$NURSE_UUID" | grep -q '"type":"UserDeactivated"' || fail "UserDeactivated no llegó a auth.users.v1"
topic_events auth.users.v1 "$NURSE_UUID" | grep -q '"status":"DEACTIVATED"' || fail "auth.users.v1 no trae el estado completo"
ok "auth.users.v1 publica el estado completo de la enfermera desactivada"
audit=$(topic_events auth.security-audit.v1 "$SUPER_ADMIN_UUID")
for type in SignInCompleted SignInFailed RefreshTokenReuseDetected; do
  echo "$audit" | grep -q "\"type\":\"$type\"" || fail "$type no llegó a auth.security-audit.v1"
done
ok "auth.security-audit.v1 registra logins, fallos y reutilización de refresh tokens"
[ "$(topic_config auth.users.v1 cleanup.policy)" = "compact" ] && [ "$(topic_config auth.security-audit.v1 cleanup.policy)" = "delete" ] \
  && [ "$(topic_config auth.security-audit.v1 retention.ms)" = "-1" ] || fail "configuración de topics inesperada"
ok "auth.users.v1 se compacta y auth.security-audit.v1 se conserva sin límite"

printf '\nE2E de auth-service completo\n'
