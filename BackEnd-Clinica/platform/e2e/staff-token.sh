#!/bin/sh
set -eu

ROLE=${1:?Usage: staff-token.sh ROLE [SUBJECT_UUID]}
SUBJECT=${2:-$(printf '%s' "$ROLE" | md5sum | sed 's/^\(.\{8\}\)\(.\{4\}\).\(.\{3\}\).\(.\{3\}\)\(.\{12\}\).*/\1-\2-4\3-8\4-\5/')}
PROJECT="${COMPOSE_PROJECT:-clinica}"
ISSUER="${AUTH_ISSUER:-http://localhost:8080/auth}"
CACHE="${E2E_TOKEN_CACHE:-${TMPDIR:-/tmp}/clinica-e2e-tokens-$PROJECT}"
CACHED="$CACHE/$ROLE-$SUBJECT"

age() { echo $(($(date +%s) - $(stat -c %Y "$CACHED"))); }
if [ -z "${E2E_TOKEN_REFRESH:-}" ] && [ -f "$CACHED" ] && [ "$(age)" -lt 240 ]; then
  cat "$CACHED"
  exit 0
fi

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
now=$(date +%s)
payload=$(jq -cn --arg iss "$ISSUER" --arg sub "$SUBJECT" --arg role "$ROLE" --arg jti "$(cat /proc/sys/kernel/random/uuid)" --argjson now "$now" --argjson ttl "${E2E_TOKEN_TTL:-300}" \
  '{iss: $iss, sub: $sub, aud: ["clinica-api"], iat: $now, nbf: $now, exp: ($now + $ttl), jti: $jti, role: $role,
    email: (($role | ascii_downcase) + "@clinica.local"), name: ("E2E " + $role), auth_time: $now, amr: ["pwd", "otp", "mfa"],
    acr: "urn:clinica:acr:mfa"}' | b64url)

signed=$(docker run --rm --user root --network "${PROJECT}_secrets-net" \
  -v "${PROJECT}_openbao_tls:/openbao/tls:ro" -v "${PROJECT}_openbao_bootstrap:/openbao/bootstrap:ro" \
  -e BAO_ADDR=https://openbao:8200 -e BAO_CACERT=/openbao/tls/ca.crt -e PAYLOAD="$payload" \
  --entrypoint sh clinica/openbao-tools:2.6.2 -c '
    export BAO_TOKEN=$(jq -r .root_token /openbao/bootstrap/init.json)
    version=$(bao read -field=latest_version transit/keys/auth-jwt)
    header=$(printf "{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"auth-jwt-v%s\"}" "$version" | openssl base64 -A | tr "+/" "-_" | tr -d "=")
    input=$(printf "%s.%s" "$header" "$PAYLOAD" | openssl base64 -A)
    signature=$(bao write -field=signature transit/sign/auth-jwt input="$input" hash_algorithm=sha2-256 marshaling_algorithm=jws | cut -d: -f3)
    [ -n "$signature" ] && printf "%s.%s.%s" "$header" "$PAYLOAD" "$signature"' 2>/dev/null) || signed=""

if [ -z "$signed" ]; then
  if [ -f "$CACHED" ] && [ "$(age)" -lt 290 ]; then
    cat "$CACHED"
    exit 0
  fi
  echo "Could not sign a token with transit/auth-jwt" >&2
  exit 1
fi

mkdir -p "$CACHE"
(umask 077 && printf '%s' "$signed" > "$CACHED")
printf '%s' "$signed"
