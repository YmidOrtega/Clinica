#!/bin/sh
set -eu

PROJECT="${COMPOSE_PROJECT:-clinica}"
published() { echo "http://$(docker compose -p "$PROJECT" port --index 1 "$1" "$2" 2>/dev/null)"; }
ASSISTANT_URL="${ASSISTANT_URL:-$(published ai-assistant-service 8084)}"
LLM_URL="${LLM_URL:-$(published llm-simulator 8080)}"
for url in "$ASSISTANT_URL" "$LLM_URL"; do
  [ "$url" != "http://" ] || { echo "Publica los puertos con docker-compose.debug.yml o define las *_URL" >&2; exit 1; }
done
DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

step() { printf '\n== %s\n' "$1"; }
fail() { echo "FAIL $1" >&2; exit 1; }
expect() {
  [ "$1" = "$2" ] || { cat "$WORK/body" >&2; fail "$3: expected HTTP $2 and got $1"; }
  echo "ok  $3"
}
field() { jq -r "$1" "$WORK/body"; }
etag() { tr -d '\r' < "$WORK/headers" | awk 'tolower($1) == "etag:" {gsub(/"/, "", $2); print $2}'; }
as_staff() {
  method=$1; url=$2; access=$3; body=${4:-}; extra=${5:-}
  if [ -n "$body" ]; then
    curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $access" \
      -H 'Content-Type: application/json' $extra --data "$body"
  else
    curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $access" $extra
  fi
}
until_ok() {
  what=$1; tries=$2; shift 2
  attempt=0
  until "$@"; do
    attempt=$((attempt + 1)); [ "$attempt" -lt "$tries" ] || { cat "$WORK/body" >&2; fail "$what"; }
    sleep 2
  done
}

step "Ciclo de facturación previo (billing-e2e)"
EXPORT="${BILLING_EXPORT:-}"
if [ -z "$EXPORT" ] || [ ! -s "$EXPORT" ]; then
  EXPORT="$WORK/billing.env"
  E2E_EXPORT_FILE="$EXPORT" sh "$DIR/billing-e2e.sh" > "$WORK/billing.log" 2>&1 || { tail -40 "$WORK/billing.log" >&2; fail "billing-e2e"; }
fi
. "$EXPORT"
ACCESS=$TOKEN
echo "ok  factura $INVOICE_NUMBER emitida, radicada y glosada; facturación con token real"

step "La factura llega al asistente por billing.invoices.v1 y el modelo la consulta con una herramienta"
curl -s -X DELETE "$LLM_URL/__admin/requests" > /dev/null
status=$(as_staff POST "$ASSISTANT_URL/api/v1/assistant/conversations" "$ACCESS" '{"title":"Revisión e2e"}')
expect "$status" 201 "conversación abierta"
CONVERSATION=$(field .uuid)
projected() {
  curl -s -X DELETE "$LLM_URL/__admin/requests" > /dev/null
  [ "$(as_staff POST "$ASSISTANT_URL/api/v1/assistant/conversations/$CONVERSATION/messages" "$ACCESS" \
    "{\"content\":\"¿Qué pasó con la $INVOICE_NUMBER?\"}")" = "201" ] || return 1
  curl -s -X POST "$LLM_URL/__admin/requests/find" -H 'Content-Type: application/json' \
    --data '{"method":"POST","url":"/v1/chat/completions","bodyPatterns":[{"contains":"lastChange"}]}' \
    | jq -e ".requests | map(.body) | map(select(contains(\"$INVOICE_NUMBER\"))) | length > 0" > /dev/null
}
until_ok "la factura no llegó a la proyección del asistente" 20 projected
[ "$(field .answer.content)" = "Revisé la factura con la herramienta de estado (simulador)." ] || fail "respuesta del modelo"
echo "ok  el modelo recibió el estado de $INVOICE_NUMBER desde la copia local"

step "Bandeja de hallazgos"
status=$(as_staff GET "$ASSISTANT_URL/api/v1/assistant/findings/summary" "$ACCESS")
expect "$status" 200 "resumen de hallazgos"
status=$(as_staff GET "$ASSISTANT_URL/api/v1/assistant/invoices/$INVOICE_NUMBER/findings" "$ACCESS")
expect "$status" 200 "historial de hallazgos de la factura"

step "El modelo propone y solo la confirmación del usuario llega a billing"
status=$(as_staff POST "$ASSISTANT_URL/api/v1/assistant/conversations/$CONVERSATION/messages" "$ACCESS" \
  "{\"content\":\"Reenvía la $INVOICE_NUMBER a la DIAN\"}")
expect "$status" 201 "pedido de reenvío"
ACTION=$(field '.proposedActions[0].uuid')
[ "$(field '.proposedActions[0].status')" = "PROPOSED" ] || fail "la acción no quedó propuesta"
echo "ok  acción $ACTION propuesta, sin ejecutar"
status=$(as_staff POST "$ASSISTANT_URL/api/v1/assistant/actions/$ACTION/confirmation" "$ACCESS" "" '-H If-Match:"0"')
expect "$status" 200 "confirmación"
case "$(field .status)" in
  DONE|FAILED) ;;
  *) fail "estado inesperado $(field .status)" ;;
esac
case "$(field .outcome)" in
  *permiso*) fail "billing negó el permiso al token intercambiado: $(field .outcome)" ;;
esac
echo "ok  billing recibió la acción con el token del usuario: $(field .status) - $(field .outcome)"
status=$(as_staff POST "$ASSISTANT_URL/api/v1/assistant/actions/$ACTION/confirmation" "$ACCESS" "" "-H If-Match:\"$(etag)\"")
expect "$status" 422 "una acción no se ejecuta dos veces"

step "Reglas de acceso"
status=$(curl -s -o "$WORK/body" -w '%{http_code}' "$ASSISTANT_URL/api/v1/assistant/findings")
expect "$status" 401 "el asistente no responde sin credenciales"
status=$(as_staff GET "$ASSISTANT_URL/api/v1/assistant/findings" "$(sh "$DIR/staff-token.sh" RECEPTIONIST "$(cat /proc/sys/kernel/random/uuid)")")
expect "$status" 403 "recepción no usa el asistente"

printf '\nassistant-e2e OK\n'
