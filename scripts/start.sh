#!/usr/bin/env bash
# Arranca TODO (infraestructura, 7 servicios, simulador y frontend) y abre el mapa.
#
#   ./scripts/start.sh                # arranca y abre el navegador
#   ./scripts/start.sh --no-browser   # arranca sin abrir nada (util en SSH)
#   ./scripts/start.sh --build        # recompila los jars antes de arrancar
#   ./scripts/start.sh --stop         # detiene servicios y frontend
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p logs

COMPOSE_FILE="$ROOT/infra/docker-compose.yml"
OPEN_BROWSER=1
FORCE_BUILD=0
WITH_SIMULATOR=0

for arg in "$@"; do
  case "$arg" in
    --no-browser) OPEN_BROWSER=0 ;;
    --build) FORCE_BUILD=1 ;;
    --sim) WITH_SIMULATOR=1 ;;
    --stop)
      "$ROOT/scripts/run-local.sh" --stop
      fuser -k 5173/tcp >/dev/null 2>&1 || true
      echo "frontend detenido"
      exit 0
      ;;
    -h|--help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
  esac
done

# --- Docker: tolera shells sin el grupo docker activo -------------------------
if docker info >/dev/null 2>&1; then
  dc() { docker compose -f "$COMPOSE_FILE" "$@"; }
elif sg docker -c "docker info" >/dev/null 2>&1; then
  dc() { sg docker -c "docker compose -f $COMPOSE_FILE $*"; }
else
  echo "ERROR: Docker no responde."
  echo "  - ¿Esta instalado?  sudo apt install docker.io docker-compose-v2"
  echo "  - ¿Estas en el grupo?  sudo usermod -aG docker \$USER  (y vuelve a entrar)"
  exit 1
fi

# --- 1. Infraestructura ------------------------------------------------------
echo "==> 1/5 Infraestructura (Kafka, PostgreSQL, Redis, MailHog)"
dc up -d --wait kafka postgres redis mailhog || {
  echo "ERROR: la infraestructura no levanto. Revisa: docker compose -f infra/docker-compose.yml logs"
  exit 1
}

if [ -f "$ROOT/infra/osrm/data/fusagasuga.osrm" ]; then
  echo "==> 2/5 OSRM"
  dc --profile routing up -d osrm >/dev/null
else
  echo "==> 2/5 OSRM: faltan los datos."
  echo "    Ejecuta una vez:  ./scripts/fetch-osrm.sh   (descarga ~335 MB)"
  exit 1
fi

echo "==> 3/5 Observabilidad (Prometheus + Grafana)"
dc --profile obs up -d prometheus grafana >/dev/null

# --- 4. Servicios ------------------------------------------------------------
if [ "$FORCE_BUILD" -eq 1 ] || [ ! -f "$ROOT/services/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar" ]; then
  echo "==> 4/5 Compilando servicios (mvn package)"
  if ! command -v mvn >/dev/null; then
    echo "ERROR: falta Maven (necesitas mvn y JDK 21)"
    exit 1
  fi
  mvn -B -q -DskipTests package || { echo "ERROR de compilacion"; exit 1; }
else
  echo "==> 4/5 Servicios (jars ya compilados)"
fi

WITH_SIMULATOR="$WITH_SIMULATOR" "$ROOT/scripts/run-local.sh" || exit 1

# --- 5. Frontend -------------------------------------------------------------
echo "==> 5/5 Frontend"
if ! command -v npm >/dev/null; then
  echo "AVISO: npm no esta instalado; se omite el frontend (usa la API por curl)"
else
  if [ ! -d "$ROOT/web/node_modules" ]; then
    echo "    instalando dependencias del frontend (una sola vez)"
    (cd "$ROOT/web" && npm install --no-audit --no-fund >/dev/null 2>&1)
  fi
  fuser -k 5173/tcp >/dev/null 2>&1 || true
  (cd "$ROOT/web" && nohup npm run dev > "$ROOT/logs/web.log" 2>&1 </dev/null &)
  for _ in $(seq 1 40); do
    curl -sf -m 2 localhost:5173 >/dev/null 2>&1 && break
    sleep 1
  done
  if curl -sf -m 2 localhost:5173 >/dev/null 2>&1; then
    echo "    frontend listo en http://localhost:5173"
  else
    echo "    AVISO: el frontend no respondio; revisa logs/web.log"
  fi
fi

# --- Aviso de salud + abrir el navegador -------------------------------------
healthy=0
for port in 18080 18081 18082 18083 8084 8085 8086; do
  curl -sf -m 2 "localhost:${port}/actuator/health" >/dev/null 2>&1 && healthy=$((healthy + 1))
done

cat <<EOF

==========================================================
 EnRuta listo — ${healthy}/7 servicios sanos
==========================================================
  MAPA         http://localhost:5173
  Grafana      http://localhost:3000
  MailHog      http://localhost:8025
  Prometheus   http://localhost:9090

  crear un envio   ./scripts/demo.sh new
  detener todo     ./scripts/start.sh --stop
==========================================================
EOF

if [ "$OPEN_BROWSER" -eq 1 ]; then
  if command -v xdg-open >/dev/null; then
    xdg-open http://localhost:5173 >/dev/null 2>&1 &
    sleep 1
    xdg-open http://localhost:3000 >/dev/null 2>&1 &
  elif command -v open >/dev/null; then
    open http://localhost:5173 >/dev/null 2>&1 &
  else
    echo "(no encontre como abrir el navegador; abre las URLs a mano)"
  fi
fi
