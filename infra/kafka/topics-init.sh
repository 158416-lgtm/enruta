#!/usr/bin/env bash
# Crea los topicos con particiones y retencion definidas en el plan.
# Idempotente: se puede correr varias veces.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"

create_topic() {
  local name="$1" partitions="$2" retention_ms="$3"
  "${KAFKA_BIN}/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists \
    --topic "$name" \
    --partitions "$partitions" \
    --replication-factor 1 \
    --config "retention.ms=${retention_ms}" \
    --config "cleanup.policy=delete"
  echo "topic listo: ${name} (partitions=${partitions}, retention.ms=${retention_ms})"
}

create_topic shipment.events 3 604800000
create_topic dispatch.events 3 604800000
create_topic tracking.gps.raw 6 3600000
create_topic tracking.events 3 604800000
create_topic notification.commands 3 604800000

create_topic shipment.events.dlq 1 1209600000
create_topic dispatch.events.dlq 1 1209600000
create_topic tracking.events.dlq 1 1209600000
create_topic notification.commands.dlq 1 1209600000

"${KAFKA_BIN}/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --list
