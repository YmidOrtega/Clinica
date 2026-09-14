#!/bin/sh
set -eu

CONNECT_URL="${CONNECT_URL:-http://kafka-connect:8083}"

for file in /connectors/*/*.json; do
  name=$(basename "$file" .json)
  echo "Registering connector ${name}"
  curl -fsS -X PUT -H 'Content-Type: application/json' --data @"$file" "${CONNECT_URL}/connectors/${name}/config" > /dev/null

  attempt=0
  until curl -fs "${CONNECT_URL}/connectors/${name}/status" 2>/dev/null | grep -q '"state":"RUNNING".*"tasks":\[{"id":0,"state":"RUNNING"'; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 30 ]; then
      echo "Connector ${name} did not reach RUNNING" >&2
      curl -fsS "${CONNECT_URL}/connectors/${name}/status" >&2 || true
      exit 1
    fi
    sleep 2
  done
  echo "Connector ${name} is running"
done
