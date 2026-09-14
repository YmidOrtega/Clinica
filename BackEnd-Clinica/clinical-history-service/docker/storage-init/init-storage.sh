#!/bin/sh
set -eu

MINIO_ROOT_USER=$(cat "${MINIO_ROOT_USER_FILE:?MINIO_ROOT_USER_FILE is required}")
MINIO_ROOT_PASSWORD=$(cat "${MINIO_ROOT_PASSWORD_FILE:?MINIO_ROOT_PASSWORD_FILE is required}")
CLINICAL_ATTACHMENTS_ACCESS_KEY=$(cat "${CLINICAL_ATTACHMENTS_ACCESS_KEY_FILE:?CLINICAL_ATTACHMENTS_ACCESS_KEY_FILE is required}")
CLINICAL_ATTACHMENTS_SECRET_KEY=$(cat "${CLINICAL_ATTACHMENTS_SECRET_KEY_FILE:?CLINICAL_ATTACHMENTS_SECRET_KEY_FILE is required}")

STAGING=clinical-attachments-staging
ARCHIVE=clinical-attachments

mc alias set clinical http://clinical-storage:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" > /dev/null

mc mb --ignore-existing "clinical/$STAGING"
mc ilm rule import "clinical/$STAGING" <<'LIFECYCLE'
{"Rules": [{"ID": "expire-staged-attachments", "Status": "Enabled",
            "Filter": {"Prefix": "attachments/"}, "Expiration": {"Days": 7}}]}
LIFECYCLE

if ! mc stat "clinical/$ARCHIVE" > /dev/null 2>&1; then
  mc mb --with-lock "clinical/$ARCHIVE"
fi
mc retention info --default "clinical/$ARCHIVE" > /dev/null

mc admin policy create clinical clinical-history-app /policies/clinical-app-policy.json
mc admin user add clinical "$CLINICAL_ATTACHMENTS_ACCESS_KEY" "$CLINICAL_ATTACHMENTS_SECRET_KEY"
mc admin policy attach clinical clinical-history-app --user "$CLINICAL_ATTACHMENTS_ACCESS_KEY" 2>/dev/null || true

echo "Clinical attachment storage ready"
