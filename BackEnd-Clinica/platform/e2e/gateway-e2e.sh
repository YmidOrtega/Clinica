#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
FRONTEND="${GATEWAY_FRONTEND_ORIGIN:-http://localhost:4321}"
SUPER_ADMIN="${AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL:-superadmin@clinica.local}"
AUTH_URL="$GATEWAY_URL/auth"
MAILPIT_URL="${MAILPIT_URL:-http://$(docker compose -p "$PROJECT" port --index 1 mailpit 8025 2>/dev/null)}"
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
JAR="$WORK/browser"
TOTP_STATE="${E2E_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/clinica-e2e}/$PROJECT-super-admin-totp.json"
PASSWORD="frase del gateway $(date +%s) para el turno"
. "$DIR/staff-login.sh"

browser() {
  curl -s -o "$WORK/response" -D "$WORK/headers" -w '%{http_code}' -b "$JAR" -c "$JAR" -H "Origin: $FRONTEND" "$@"
}

location() { tr -d '\r' < "$WORK/headers" | sed -n 's/^[Ll]ocation: //p'; }
header() { tr -d '\r' < "$WORK/headers" | sed -n "s/^$1: //Ip"; }

gateway_csrf() {
  curl -s -b "$JAR" -c "$JAR" "$GATEWAY_URL/bff/session" | jq -r '"\(.csrf.headerName): \(.csrf.token)"'
}

step "Sesión anónima"
[ "$(browser "$GATEWAY_URL/bff/session")" = "200" ] && [ "$(jq -r .authenticated "$WORK/response")" = "false" ] \
  && [ -n "$(jq -r .csrf.token "$WORK/response")" ] || fail "sesión anónima: $(cat "$WORK/response")"
[ "$(header Access-Control-Allow-Origin)" = "$FRONTEND" ] && [ "$(header Access-Control-Allow-Credentials)" = "true" ] \
  || fail "CORS con credenciales no permite al frontend"
[ "$(browser "$GATEWAY_URL/api/v1/me")" = "401" ] && [ "$(jq -r .loginUrl "$WORK/response")" = "/bff/login" ] || fail "la API sin sesión no respondió 401"
ok "sin sesión la API responde 401 con la URL de login y CORS admite al frontend con credenciales"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X OPTIONS -H 'Access-Control-Request-Method: POST' -H 'Origin: http://evil.test' "$GATEWAY_URL/api/v1/patients")" = "403" ] \
  || fail "un origen ajeno pasó el preflight"
ok "un origen ajeno no pasa el preflight"

step "Login del frontend a través del gateway"
prepare_super_admin
[ "$(browser "$GATEWAY_URL/bff/login?returnTo=$(printf '%s' "$FRONTEND/pacientes" | jq -sRr @uri)")" = "302" ] || fail "/bff/login no redirigió"
[ "$(browser "$GATEWAY_URL$(location)")" = "302" ] || fail "no inició la autorización"
authorize=$(location)
case "$authorize" in "$AUTH_URL/oauth2/authorize?"*code_challenge_method=S256*) ;; *) fail "autorización inesperada: $authorize" ;; esac
[ "$(browser "$authorize")" = "302" ] && case "$(location)" in "$FRONTEND"/login*) true ;; *) false ;; esac || fail "auth no pidió el login del frontend: $(location)"
ok "el gateway inicia authorization code con PKCE y auth envía al login del frontend"
[ "$(api /api/v1/login "{\"email\": \"$SUPER_ADMIN\", \"password\": \"$PASSWORD\"}")" = "200" ] || fail "login: $(cat "$WORK/body")"
complete_second_factor "$(jq -r .outcome "$WORK/body")"
continue_url=$(jq -r .continueUrl "$WORK/body")
[ "$(browser "$continue_url")" = "302" ] || fail "la autorización no devolvió código: $continue_url $(head -c 300 "$WORK/response")"
callback=$(location)
case "$callback" in "$GATEWAY_URL/login/oauth2/code/clinica?"*) ;; *) fail "callback inesperado: $callback" ;; esac
[ "$(browser "$callback")" = "302" ] && [ "$(location)" = "$FRONTEND/pacientes" ] || fail "el gateway no terminó el login: $(cat "$WORK/response")"
[ "$(browser "$GATEWAY_URL/bff/session")" = "200" ] && [ "$(jq -r .user.role "$WORK/response")" = "SUPER_ADMIN" ] \
  && jq -e '.user.methods | index("mfa")' "$WORK/response" > /dev/null || fail "sesión tras el login: $(cat "$WORK/response")"
ok "el gateway canjea el código, vuelve a la página del frontend y la sesión expone al usuario"
grep -q "CLINICA_SESSION" "$JAR" && ! grep -q "access-token\|refresh" "$JAR" || fail "cookies inesperadas en el navegador"
ok "el navegador solo tiene cookies de sesión; ningún token"

step "API con el token relevado"
[ "$(browser "$GATEWAY_URL/api/v1/me")" = "200" ] && [ "$(jq -r .user.email "$WORK/response")" = "$SUPER_ADMIN" ] || fail "/api/v1/me: $(cat "$WORK/response")"
ok "auth-service recibe el access token del SUPER_ADMIN a través del gateway"
document=$(date +%s%N | cut -c6-15)
body="{\"document\": {\"type\": \"CEDULA_DE_CIUDADANIA\", \"number\": \"$document\"},
  \"demographics\": {\"firstNames\": \"Ana\", \"lastNames\": \"Rojas Díaz\", \"birthDate\": \"1990-02-01\", \"sex\": \"FEMALE\", \"countryOfOrigin\": \"CO\", \"disability\": \"NONE\"},
  \"contact\": {\"mobile\": \"3001234567\"}, \"affiliation\": {\"regime\": \"UNINSURED\"},
  \"residence\": {\"department\": \"Santander\", \"municipality\": \"Girón\", \"zone\": \"URBAN\", \"address\": \"Calle 1 # 2-3\"}}"
[ "$(browser -X POST -H 'Content-Type: application/json' --data "$body" "$GATEWAY_URL/api/v1/patients")" = "403" ] \
  && [ "$(jq -r .code "$WORK/response")" = "CSRF_TOKEN_INVALID" ] || fail "una escritura sin CSRF no fue rechazada"
[ "$(browser -X POST -H 'Content-Type: application/json' -H "$(gateway_csrf)" --data "$body" "$GATEWAY_URL/api/v1/patients")" = "201" ] \
  || fail "registro del paciente: $(cat "$WORK/response")"
[ -n "$(header ETag)" ] || fail "el ETag no llegó al frontend"
ok "patient-service registra con el token relevado; sin CSRF el gateway rechaza la escritura"

step "Step-up"
[ "$(browser "$GATEWAY_URL/bff/step-up?returnTo=$(printf '%s' "$FRONTEND/firmar" | jq -sRr @uri)")" = "302" ] || fail "/bff/step-up no redirigió"
[ "$(browser "$GATEWAY_URL$(location)")" = "302" ] || fail "el step-up no inició la autorización"
case "$(location)" in *max_age=300*) ;; *) fail "el step-up no pidió max_age: $(location)" ;; esac
[ "$(browser "$(location)")" = "302" ] || fail "la autorización del step-up falló"
next=$(location)
case "$next" in
  "$FRONTEND"/login*step=step-up*)
    [ "$(api /api/v1/login/step-up "{\"code\": \"$(totp)\"}")" = "200" ] || fail "step-up: $(cat "$WORK/body")"
    [ "$(browser "$(jq -r .continueUrl "$WORK/body")")" = "302" ] || fail "la autorización tras el step-up falló"
    next=$(location)
    ;;
esac
[ "$(browser "$next")" = "302" ] && [ "$(location)" = "$FRONTEND/firmar" ] || fail "el step-up no volvió al frontend: $next"
ok "el step-up pide max_age=300 a auth-service y vuelve a la página que lo pidió"

step "Cierre de sesión"
[ "$(browser -X POST -H "$(gateway_csrf)" "$GATEWAY_URL/bff/logout")" = "200" ] || fail "logout: $(cat "$WORK/response")"
end_session=$(jq -r .endSessionUrl "$WORK/response")
case "$end_session" in "$AUTH_URL/connect/logout?id_token_hint="*) ;; *) fail "URL de cierre inesperada: $end_session" ;; esac
[ "$(browser "$GATEWAY_URL/api/v1/me")" = "401" ] || fail "la API siguió aceptando la sesión cerrada"
[ "$(browser "$end_session")" = "302" ] && [ "$(location)" = "$FRONTEND/" ] || fail "auth no cerró su sesión: $(location)"
[ "$(browser "$authorize")" = "302" ] && case "$(location)" in "$FRONTEND"/login*) true ;; *) false ;; esac || fail "auth conservó la sesión"
ok "logout revoca en el gateway y en auth-service y vuelve al frontend"

printf '\nE2E del gateway completo\n'
