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

totp() {
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
