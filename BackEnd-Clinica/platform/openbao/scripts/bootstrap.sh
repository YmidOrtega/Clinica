#!/bin/sh
set -eu

SEAL_DIR=/openbao/seal
TLS_DIR=/openbao/tls
BOOTSTRAP_DIR=/openbao/bootstrap
OPENBAO_UID=100
OPENBAO_GID=1000

umask 077

if [ ! -s "$SEAL_DIR/unseal.key" ]; then
  openssl rand -out "$SEAL_DIR/unseal.key" 32
  echo "Generated static unseal key"
fi
chown "$OPENBAO_UID:$OPENBAO_GID" "$SEAL_DIR/unseal.key"
chmod 0400 "$SEAL_DIR/unseal.key"

if [ ! -s "$BOOTSTRAP_DIR/ca.key" ]; then
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$BOOTSTRAP_DIR/ca.key"
  openssl req -x509 -new -key "$BOOTSTRAP_DIR/ca.key" -sha256 -days 3650 \
    -subj "/CN=Clinica OpenBao development CA" -out "$BOOTSTRAP_DIR/ca.crt"
  rm -f "$TLS_DIR/server.crt"
  echo "Generated development CA"
fi

if [ ! -s "$TLS_DIR/server.crt" ] || ! openssl x509 -checkend 2592000 -noout -in "$TLS_DIR/server.crt" > /dev/null; then
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$TLS_DIR/server.key"
  openssl req -new -key "$TLS_DIR/server.key" -subj "/CN=openbao" -out "$BOOTSTRAP_DIR/server.csr"
  cat > "$BOOTSTRAP_DIR/server.ext" <<EXT
basicConstraints = CA:FALSE
keyUsage = digitalSignature
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = DNS:openbao, DNS:openbao-1, DNS:openbao-2, DNS:openbao-3, DNS:localhost, IP:127.0.0.1
EXT
  openssl x509 -req -in "$BOOTSTRAP_DIR/server.csr" -CA "$BOOTSTRAP_DIR/ca.crt" -CAkey "$BOOTSTRAP_DIR/ca.key" \
    -CAcreateserial -days 365 -sha256 -extfile "$BOOTSTRAP_DIR/server.ext" -out "$TLS_DIR/server.crt"
  rm -f "$BOOTSTRAP_DIR/server.csr" "$BOOTSTRAP_DIR/server.ext"
  echo "Issued OpenBao server certificate"
fi

cp "$BOOTSTRAP_DIR/ca.crt" "$TLS_DIR/ca.crt"
chown "$OPENBAO_UID:$OPENBAO_GID" "$TLS_DIR/server.key"
chmod 0400 "$TLS_DIR/server.key"
chmod 0444 "$TLS_DIR/server.crt" "$TLS_DIR/ca.crt"
echo "OpenBao bootstrap material ready"
