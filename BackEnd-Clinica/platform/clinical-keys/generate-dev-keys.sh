#!/bin/sh
set -eu

TARGET="${1:-.secrets/clinical}"
SEAL_KEY_ID="${CLINICAL_SEAL_ACTIVE_KEY_ID:-seal-dev}"
MASTER_KEY_ID="${CLINICAL_ENCRYPTION_ACTIVE_KEY_ID:-master-dev}"

mkdir -p "$TARGET/seal" "$TARGET/encryption"
umask 077

if [ ! -f "$TARGET/seal/$SEAL_KEY_ID.private.pem" ]; then
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$TARGET/seal/$SEAL_KEY_ID.private.pem"
  openssl pkey -in "$TARGET/seal/$SEAL_KEY_ID.private.pem" -pubout -out "$TARGET/seal/$SEAL_KEY_ID.public.pem"
fi

if [ ! -f "$TARGET/encryption/$MASTER_KEY_ID.key" ]; then
  head -c 32 /dev/urandom | base64 > "$TARGET/encryption/$MASTER_KEY_ID.key"
fi

chmod 0755 "$TARGET" "$TARGET/seal" "$TARGET/encryption"
chmod 0444 "$TARGET"/seal/* "$TARGET"/encryption/*

echo "Development keys in $TARGET (seal: $SEAL_KEY_ID, master: $MASTER_KEY_ID). Never use them in production."
