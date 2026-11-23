#!/usr/bin/env bash
# Verifica en segundos si la maquina esta lista para desarrollar o exponer.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAIL=0
WARN=0

ok()   { printf "  [ OK ] %s\n" "$1"; }
fail() { printf "  [FALLA] %s\n" "$1"; FAIL=$((FAIL+1)); }
warn() { printf "  [AVISO] %s\n" "$1"; WARN=$((WARN+1)); }

echo "== Herramientas"
command -v java >/dev/null && ok "java $(java -version 2>&1 | head -1)" || fail "java no encontrado (necesitas JDK 21)"
command -v mvn  >/dev/null && ok "maven $(mvn -v 2>/dev/null | head -1)" || fail "maven no encontrado"
command -v node >/dev/null && ok "node $(node -v)" || warn "node no encontrado (solo hace falta para web/)"
command -v docker >/dev/null && ok "docker $(docker --version 2>/dev/null)" || fail "docker no instalado: sudo apt install docker.io docker-compose-v2"
docker compose version >/dev/null 2>&1 && ok "docker compose $(docker compose version --short 2>/dev/null)" || fail "docker compose v2 no disponible"
docker info >/dev/null 2>&1 && ok "daemon de docker respondiendo" || fail "el daemon de docker no responde (o falta el grupo docker)"

echo "== Recursos"
AVAIL_MB=$(awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo)
if [ "$AVAIL_MB" -ge 7000 ]; then ok "RAM disponible: ${AVAIL_MB} MB"; else fail "RAM disponible: ${AVAIL_MB} MB (minimo recomendado 7000 MB)"; fi
FREE_GB=$(df -BG --output=avail "$ROOT" | tail -1 | tr -dc '0-9')
if [ "${FREE_GB:-0}" -ge 15 ]; then ok "disco libre: ${FREE_GB} GB"; else warn "disco libre: ${FREE_GB} GB (recomendado 15 GB)"; fi

echo "== Puertos"
DOCKER_OK=0
docker info >/dev/null 2>&1 && DOCKER_OK=1
for p in 18080 8081 8082 8083 8084 8085 8086 8090 9092 29092 5433 6379 5000 8025 3000; do
  if ss -ltn 2>/dev/null | grep -q ":${p} "; then
    if [ "$DOCKER_OK" -eq 1 ] && docker ps --filter "publish=${p}" --format '{{.Names}}' 2>/dev/null | grep -q '^enruta-'; then
      ok "puerto ${p} en uso por un contenedor de enruta"
    else
      warn "puerto ${p} en uso por un proceso ajeno al proyecto"
    fi
  fi
done

echo "== Datos de demo"
[ -f "$ROOT/infra/osrm/data/fusagasuga.osrm" ] \
  && ok "datos OSRM presentes ($(du -sh "$ROOT/infra/osrm/data" | cut -f1))" \
  || fail "faltan datos OSRM: ./scripts/fetch-osrm.sh"
[ -d "$ROOT/web/node_modules" ] && ok "dependencias web instaladas" || warn "web/node_modules ausente (npm ci en web/)"

echo "== Imagenes de infraestructura"
if docker info >/dev/null 2>&1; then
  for img in apache/kafka:4.2.2 postgres:16-alpine redis:7-alpine mailhog/mailhog:v1.0.1 osrm/osrm-backend:v5.25.0; do
    docker image inspect "$img" >/dev/null 2>&1 && ok "imagen $img" || warn "imagen $img no descargada (se baja en el primer up)"
  done
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULTADO: listo. Avisos: $WARN"
  exit 0
else
  echo "RESULTADO: $FAIL bloqueo(s), $WARN aviso(s)"
  exit 1
fi
