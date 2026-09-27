step() { printf '\n== %s\n' "$1"; }
ok() { echo "ok  $1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

csrf_header() {
  curl -s -b "$JAR" -c "$JAR" "$AUTH_URL/api/v1/session" > "$WORK/session"
  echo "$(jq -r .csrf.headerName "$WORK/session"): $(jq -r .csrf.token "$WORK/session")"
}

api() {
  path=$1; body=$2
  curl -s -o "$WORK/body" -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST "$AUTH_URL$path" \
    -H 'Content-Type: application/json' -H "$(csrf_header)" --data "$body"
}

session_field() {
  curl -s -b "$JAR" -c "$JAR" "$AUTH_URL/api/v1/session" | jq -r "$1"
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

fresh_totp_window() {
  elapsed=$(( $(date +%s) % 30 ))
  [ "$elapsed" -lt 25 ] || sleep $(( 31 - elapsed ))
}

totp() {
  fresh_totp_window
  set -- $(node "$DIR/totp-code.mjs" "$(jq -r .otpauthUrl "$TOTP_STATE")" "$(jq -r .lastPeriod "$TOTP_STATE")")
  jq --argjson period "$2" '.lastPeriod = $period' "$TOTP_STATE" > "$TOTP_STATE.tmp" && mv "$TOTP_STATE.tmp" "$TOTP_STATE"
  echo "$1"
}

prepare_super_admin() {
  activation=$(latest_token_mailed_to "Active su cuenta de la Clínica" "$SUPER_ADMIN" || true)
  status=000
  [ -n "$activation" ] && status=$(api /api/v1/activation "{\"token\": \"$activation\", \"password\": \"$PASSWORD\"}")
  if [ "$status" = "204" ]; then
    ok "activación con el enlace del correo"
    return
  fi
  curl -s -X DELETE "$MAILPIT_URL/api/v1/search?query=$(printf 'to:%s' "$SUPER_ADMIN" | jq -sRr @uri)" > /dev/null
  [ "$(api /api/v1/password-reset/requests "{\"email\": \"$SUPER_ADMIN\"}")" = "202" ] || fail "no se aceptó la solicitud de reseteo"
  reset=$(latest_token_mailed_to "Restablezca su contraseña de la Clínica" "$SUPER_ADMIN") || fail "no llegó el correo de reseteo"
  [ "$(api /api/v1/password-reset "{\"token\": \"$reset\", \"password\": \"$PASSWORD\"}")" = "204" ] || fail "no se restableció la contraseña"
  sleep 1
  ok "ya estaba activo: contraseña restablecida con el enlace del correo"
}

complete_second_factor() {
  case "$1" in
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
    *) fail "resultado inesperado del login: $1" ;;
  esac
}

bao_root() {
  docker run --rm --user root --network "${PROJECT}_secrets-net" \
    -v "${PROJECT}_openbao_tls:/openbao/tls:ro" -v "${PROJECT}_openbao_bootstrap:/openbao/bootstrap:ro" \
    -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/openbao/tls/ca.crt \
    --entrypoint sh "${TOOLS_IMAGE:-clinica/openbao-tools:2.6.2}" -c "BAO_TOKEN=\$(jq -r .root_token /openbao/bootstrap/init.json); export BAO_TOKEN; $*"
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

authorize_url() {
  printf '%s/oauth2/authorize?response_type=code&client_id=api-gateway&scope=openid%%20profile&state=%s&code_challenge=%s&code_challenge_method=S256&redirect_uri=%s' \
    "$AUTH_URL" "$(openssl rand 12 | b64url)" "$(printf '%s' "$1" | openssl dgst -sha256 -binary | b64url)" \
    "$(printf '%s' "$REDIRECT_URI" | jq -sRr @uri)"
}

code_for_token() {
  verifier=$1; callback=$2
  code=$(printf '%s' "$callback" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
  [ -n "$code" ] || fail "el login no devolvió un código: $callback"
  [ "$(token_request --data-urlencode grant_type=authorization_code --data-urlencode "code=$code" \
        --data-urlencode "redirect_uri=$REDIRECT_URI" --data-urlencode "code_verifier=$verifier")" = "200" ] \
    || fail "canje del código: $(cat "$WORK/tokens")"
  jq -r .access_token "$WORK/tokens"
}

super_admin_access_token() {
  JAR="$WORK/super-admin"
  prepare_super_admin >&2
  verifier=$(openssl rand 32 | b64url)
  curl -s -o /dev/null -b "$JAR" -c "$JAR" "$(authorize_url "$verifier")"
  [ "$(api /api/v1/login "{\"email\": \"$SUPER_ADMIN\", \"password\": \"$PASSWORD\"}")" = "200" ] || fail "login del SUPER_ADMIN: $(cat "$WORK/body")"
  complete_second_factor "$(jq -r .outcome "$WORK/body")" >&2
  code_for_token "$verifier" "$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR" -c "$JAR" "$(jq -r .continueUrl "$WORK/body")")"
}

invited_staff_access_token() {
  email=$1; password=$2
  previous_jar="$JAR"
  JAR="$WORK/$(printf '%s' "$email" | md5sum | cut -c1-12)"
  activation=$(latest_token_mailed_to "Active su cuenta de la Clínica" "$email") || fail "no llegó la invitación de $email"
  [ "$(api /api/v1/activation "{\"token\": \"$activation\", \"password\": \"$password\"}")" = "204" ] || fail "activación de $email: $(cat "$WORK/body")"
  verifier=$(openssl rand 32 | b64url)
  curl -s -o /dev/null -b "$JAR" -c "$JAR" "$(authorize_url "$verifier")"
  api /api/v1/login "{\"email\": \"$email\", \"password\": \"$password\"}" > /dev/null
  [ "$(api /api/v1/login/second-factor/enrollment '{}')" = "200" ] || fail "enrolamiento de $email: $(cat "$WORK/body")"
  otpauth=$(jq -r .otpauthUrl "$WORK/body")
  for attempt in 1 2; do
    fresh_totp_window
    enrolled=$(node "$DIR/totp-code.mjs" "$otpauth" | cut -d' ' -f1)
    status=$(api /api/v1/login/second-factor/enrollment/confirmation "{\"code\": \"$enrolled\"}")
    [ "$status" = "200" ] && break
    [ "$attempt" = "1" ] || fail "confirmación de $email: $(cat "$WORK/body")"
    sleep $(( 31 - $(date +%s) % 30 ))
  done
  token=$(code_for_token "$verifier" "$(curl -s -o /dev/null -w '%{redirect_url}' -b "$JAR" -c "$JAR" "$(jq -r .continueUrl "$WORK/body")")")
  JAR="$previous_jar"
  echo "$token"
}
