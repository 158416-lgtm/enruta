#!/usr/bin/env bash
# Guion y atajos para la exposicion.
set -uo pipefail

BASE="${ENRUTA_BASE:-http://localhost:18080}"
CLIENT_EMAIL="${ENRUTA_CLIENT:-cliente@enruta.test}"
CLIENT_PASSWORD="${ENRUTA_CLIENT_PASSWORD:-Cliente1234!}"

login() {
  curl -s -X POST "$BASE/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$CLIENT_EMAIL\",\"password\":\"$CLIENT_PASSWORD\"}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])'
}

case "${1:-help}" in
  urls)
    for u in "$BASE/actuator/health" "http://localhost:8084/actuator/health" \
             "http://localhost:8025" "http://localhost:5000/route/v1/driving/-74.3674,4.3449;-74.3600,4.3374?overview=false"; do
      echo "  $u"
    done
    ;;
  new)
    TOKEN=$(login)
    if [ -z "$TOKEN" ]; then
      echo "No se pudo hacer login. ¿Estan los servicios arriba? (./scripts/preflight.sh)"
      exit 1
    fi
    ID=$(curl -s -X POST "$BASE/api/v1/shipments" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())')" \
      -H 'Content-Type: application/json' \
      -d '{"pickup":{"lat":4.3449,"lng":-74.3674,"address":"Parque Principal Fusagasuga"},
           "dropoff":{"lat":4.3374,"lng":-74.3600,"address":"Terminal de Transportes"}}' \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
    echo "Envio creado: $ID"
    echo "Canal en vivo:"
    echo "  ws://localhost:8084/ws/tracking?shipmentId=$ID&token=<TU_TOKEN>"
    echo "Sigue el estado con:"
    echo "  watch -n2 \"curl -s $BASE/api/v1/shipments/$ID -H 'Authorization: Bearer \$TOKEN' | jq -r .status\""
    ;;
  status)
    TOKEN=$(login)
    curl -s "$BASE/api/v1/shipments/$2" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
    ;;
  *)
    cat <<'GUION'
==========================================================
 GUION DE DEMO (2 min) - EnRuta
==========================================================
 1. Terminal A: docker compose -f infra/docker-compose.yml ps   -> todo "healthy"
 2. Terminal B: ./scripts/demo.sh new      -> crea un envio
 3. En 2-5 s: oferta al conductor mas cercano y aceptacion automatica
    -> el envio pasa a ASSIGNED
 4. El simulador conduce al origen; a los 3 pings dentro de 100 m
    -> PICKED_UP, y al alejarse -> IN_TRANSIT
 5. Llega al destino (150 m) -> DELIVERED
 6. WebSocket: posiciones en vivo durante todo el trayecto
 7. Kafka: shipment.events, dispatch.events, tracking.events
 8. Grafana / Tempo (S6)
==========================================================
GUION
    ;;
esac
