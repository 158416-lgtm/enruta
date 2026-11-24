#!/usr/bin/env bash
# Arranca los 7 servicios y el simulador en local, espera a que esten sanos y muestra las URLs.
# Uso: ./scripts/run-local.sh          (usa el infra del compose ya levantado)
#      ./scripts/run-local.sh --stop   (detiene todo)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p logs

PORTS=(18080 18081 18082 18083 8084 8085 8086 8090)
JWK="${AUTH_JWK_PATH:-$ROOT/keys/auth-jwk.json}"

stop_all() {
  for port in "${PORTS[@]}"; do
    fuser -k "${port}/tcp" >/dev/null 2>&1 || true
  done
  echo "servicios detenidos"
}

if [ "${1:-}" = "--stop" ]; then
  stop_all
  exit 0
fi

stop_all
sleep 2

echo "==> arrancando servicios"
DB_URL="jdbc:postgresql://localhost:5433/authdb" AUTH_JWK_PATH="$JWK" SERVER_PORT=18081 \
  nohup java -jar services/auth-service/target/auth-service-0.1.0-SNAPSHOT.jar > logs/auth.log 2>&1 &

DB_URL="jdbc:postgresql://localhost:5433/shipmentdb" KAFKA_BOOTSTRAP=localhost:29092 \
  AUTH_JWKS_URI=http://localhost:18081/api/v1/auth/jwks SERVER_PORT=18082 \
  nohup java -jar services/shipment-service/target/shipment-service-0.1.0-SNAPSHOT.jar > logs/shipment.log 2>&1 &

DB_URL="jdbc:postgresql://localhost:5433/dispatchdb" KAFKA_BOOTSTRAP=localhost:29092 \
  REDIS_HOST=localhost AUTH_JWKS_URI=http://localhost:18081/api/v1/auth/jwks SERVER_PORT=18083 \
  nohup java -jar services/dispatch-service/target/dispatch-service-0.1.0-SNAPSHOT.jar > logs/dispatch.log 2>&1 &

KAFKA_BOOTSTRAP=localhost:29092 REDIS_HOST=localhost \
  AUTH_JWKS_URI=http://localhost:18081/api/v1/auth/jwks ROUTING_URL=http://localhost:8085 \
  SERVER_PORT=8084 \
  nohup java -jar services/tracking-service/target/tracking-service-0.1.0-SNAPSHOT.jar > logs/tracking.log 2>&1 &

OSRM_BASE_URL=http://localhost:5000 AUTH_JWKS_URI=http://localhost:18081/api/v1/auth/jwks \
  SERVER_PORT=8085 \
  nohup java -jar services/routing-eta-service/target/routing-eta-service-0.1.0-SNAPSHOT.jar > logs/routing.log 2>&1 &

DB_URL="jdbc:postgresql://localhost:5433/notifdb" KAFKA_BOOTSTRAP=localhost:29092 \
  AUTH_JWKS_URI=http://localhost:18081/api/v1/auth/jwks MAIL_HOST=localhost MAIL_PORT=1025 \
  SERVER_PORT=8086 \
  nohup java -jar services/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar > logs/notification.log 2>&1 &

AUTH_URI=http://localhost:18081 SHIPMENT_URI=http://localhost:18082 \
  DISPATCH_URI=http://localhost:18083 TRACKING_URI=http://localhost:8084 \
  ROUTING_URI=http://localhost:8085 NOTIFICATION_URI=http://localhost:8086 \
  AUTH_JWKS_URI=http://localhost:18081/api/v1/auth/jwks SERVER_PORT=18080 \
  nohup java -jar services/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar > logs/gateway.log 2>&1 &

echo "==> esperando salud"
healthy=0
for _ in $(seq 1 120); do
  healthy=0
  for port in 18080 18081 18082 18083 8084 8085 8086; do
    curl -sf -m 2 "localhost:${port}/actuator/health" >/dev/null 2>&1 && healthy=$((healthy + 1))
  done
  [ "$healthy" -eq 7 ] && break
  sleep 1
done

if [ "$healthy" -ne 7 ]; then
  echo "ERROR: solo $healthy/7 servicios sanos. Revisa logs/"
  exit 1
fi

AUTH_URL=http://localhost:18081 DISPATCH_URL=http://localhost:18083 \
  SHIPMENT_URL=http://localhost:18082 TRACKING_URL=http://localhost:8084 \
  OSRM_BASE_URL=http://localhost:5000 SERVER_PORT=8090 \
  nohup java -jar tools/driver-simulator/target/driver-simulator-0.1.0-SNAPSHOT.jar > logs/simulator.log 2>&1 &

sleep 10

cat <<EOF

Listo. 7 servicios sanos y simulador en marcha.

  mapa        http://localhost:5173        (cd web && npm run dev)
  gateway     http://localhost:18080
  grafana     http://localhost:3000
  prometheus  http://localhost:9090
  mailhog     http://localhost:8025

  crear envío  ./scripts/demo.sh new
  detener      ./scripts/run-local.sh --stop
EOF
