#!/usr/bin/env bash
# Pruebas de carga y de estrés con ApacheBench (ab).
#
#   ./load/ab.sh                 # 2000 peticiones, 20 concurrentes
#   ./load/ab.sh 5000 50         # personalizado
#
# Requisitos: servicios levantados (./scripts/start.sh) y apache2-utils instalado.
# Genera docs/carga.md con los resultados y falla si el p95 de algún endpoint
# supera el umbral de 500 ms.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
N="${1:-2000}"
C="${2:-20}"
P95_MAX_MS="${P95_MAX_MS:-500}"
OUT="docs/carga.md"

command -v ab >/dev/null || {
  echo "Falta ab. Instala con: sudo apt-get install -y apache2-utils"
  exit 1
}
command -v jq >/dev/null || {
  echo "Falta jq."
  exit 1
}

echo "==> Autenticando"
TOKEN=$(curl -sf -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@enruta.test","password":"Admin1234!"}' | jq -r .accessToken)
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "No se pudo autenticar"; exit 1; }

SHIPMENT_ID=$(curl -sf "$BASE/api/v1/shipments?page=0&size=1" -H "Authorization: Bearer $TOKEN" \
  | jq -r '.content[0].id // empty')
[ -n "$SHIPMENT_ID" ] || SHIPMENT_ID="00000000-0000-0000-0000-000000000000"

# nombre|url
TARGETS=(
  "ETA|$BASE/api/v1/eta?fromLat=4.3449&fromLng=-74.3674&toLat=4.3374&toLng=-74.3600"
  "Ruta con geometria|$BASE/api/v1/eta/route?fromLat=4.3449&fromLng=-74.3674&toLat=4.3374&toLng=-74.3600"
  "Distancia OSRM|$BASE/api/v1/eta/distance?fromLat=4.3449&fromLng=-74.3674&toLat=4.3374&toLng=-74.3600"
  "Listado de envios|$BASE/api/v1/shipments?page=0&size=20"
  "Historial de envio|$BASE/api/v1/shipments/$SHIPMENT_ID/history"
  "Estado de rastreo|$BASE/api/v1/tracking/shipments/$SHIPMENT_ID"
  "Estadisticas de notificaciones|$BASE/api/v1/notifications/stats"
)

mkdir -p docs
RESULTS=$(mktemp)
echo "| Endpoint | Peticiones | Concurrencia | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Errores de red | No-2xx |" > "$RESULTS"
echo "|---|---:|---:|---:|---:|---:|---:|---:|---:|" >> "$RESULTS"

FAILED=0
for target in "${TARGETS[@]}"; do
  NAME="${target%%|*}"
  URL="${target#*|}"
  echo "==> $NAME ($N peticiones, $C concurrentes)"
  LOG=$(mktemp)
  ab -q -n "$N" -c "$C" -H "Authorization: Bearer $TOKEN" "$URL" > "$LOG" 2>&1 || true
  RPS=$(awk '/Requests per second:/ {print $4}' "$LOG")
  P50=$(awk '/^  50%/ {print $2}' "$LOG")
  P95=$(awk '/^  95%/ {print $2}' "$LOG")
  P99=$(awk '/^  99%/ {print $2}' "$LOG")
  NET_ERRORS=$(grep -o '(Connect: [0-9]*, Receive: [0-9]*, Length: [0-9]*, Exceptions: [0-9]*)' "$LOG" \
    | sed 's/[^0-9 ]//g' | awk '{print $1 + $2 + $4}')
  NON2XX=$(awk '/Non-2xx responses:/ {print $3}' "$LOG")
  if [ -z "$RPS" ]; then
    echo "ERROR: ab no produjo resultados para $NAME"; sed -n '1,20p' "$LOG"; rm -f "$LOG"; exit 1
  fi
  RPS=${RPS:-0}; P50=${P50:-0}; P95=${P95:-0}; P99=${P99:-0}
  NET_ERRORS=${NET_ERRORS:-0}; NON2XX=${NON2XX:-0}
  printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n" \
    "$NAME" "$N" "$C" "$RPS" "$P50" "$P95" "$P99" "$NET_ERRORS" "$NON2XX" >> "$RESULTS"
  awk -v p="$P95" -v max="$P95_MAX_MS" 'BEGIN { exit !(p > max) }' && FAILED=1
  [ "$NET_ERRORS" -eq 0 ] || FAILED=1
  [ "$NON2XX" -eq 0 ] || FAILED=1
  rm -f "$LOG"
done

{
  echo "# Pruebas de carga y estrés"
  echo
  echo "Herramienta: ApacheBench (\`ab\`). Fecha: $(date -Iseconds)."
  echo "Servidor: $(uname -srm), $(nproc) núcleos. Servicios en localhost (7 JVM + Kafka + PostgreSQL + Redis)."
  echo "Umbral exigido: **p95 ≤ ${P95_MAX_MS} ms**."
  echo
  cat "$RESULTS"
  echo
  echo "> \`ab\` abre una conexión TCP por petición (sin \`-k\`), usa el mismo JWT y no incluye el tiempo de login."
  echo "> La columna Errores de red suma Connect+Receive+Exceptions (0 = sin fallos). Las diferencias"
  echo "> de longitud que reporta \`ab\` no son errores: vienen de \`cached:true/false\` o del plan B."
  echo "> Los endpoints de escritura se excluyen a propósito: crean envíos y ensucian la base."
  echo
  if [ "$FAILED" -eq 1 ]; then
    echo "**Resultado: REVISAR.** Hubo un p95 sobre ${P95_MAX_MS} ms, errores de red o respuestas no-2xx."
  else
    echo "**Resultado: OK.** Todos los endpoints quedaron por debajo de ${P95_MAX_MS} ms en el percentil 95, sin errores de red ni respuestas no-2xx."
  fi
  echo
  echo "## Estrés"
  echo
  echo "Para buscar el punto de quiebre se sube la concurrencia hasta que aparezcan fallos:"
  echo
  echo '```bash'
  echo "for c in 50 100 200 400; do ./load/ab.sh 3000 \$c; done"
  echo '```'
} > "$OUT"

rm -f "$RESULTS"
echo
echo "Resultados en $OUT"
[ "$FAILED" -eq 0 ] || exit 1
