#!/bin/sh
set -eu

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
REPLICATION="${KAFKA_REPLICATION_FACTOR:-1}"
THIRTY_DAYS=2592000000

create() {
  topic=$1
  shift
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists --topic "$topic" \
    --partitions 3 --replication-factor "$REPLICATION" "$@"
  echo "Topic ${topic} is ready"
}

create billing.filing-deadlines.v1 --config cleanup.policy=delete --config retention.ms="$THIRTY_DAYS"
create billing.claim-objections.v1 --config cleanup.policy=delete --config retention.ms="$THIRTY_DAYS"
