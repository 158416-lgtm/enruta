#!/usr/bin/env bash
# Crea (o renueva) el tunel HTTPS para abrir la app desde el movil y muestra la URL.
#
#   ./scripts/tunel.sh
#
# El link cambia cada vez que lo ejecutas. Necesita internet en el PC y en el movil,
# pero NO necesitan estar en la misma red.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="/tmp/cloudflared"
mkdir -p "$ROOT/logs"

if [ ! -x "$BIN" ]; then
  echo "==> descargando cloudflared (solo la primera vez)"
  curl -sL -o "$BIN" \
    https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
  chmod +x "$BIN"
fi

# Cierra un tunel previo por PID (pkill -f podria matar esta misma shell).
for pid in $(pgrep -f "^/tmp/cloudflared tunnel" 2>/dev/null || true); do
  kill "$pid" 2>/dev/null || true
done
sleep 1

setsid nohup "$BIN" tunnel --url http://localhost:5173 --no-autoupdate \
  > "$ROOT/logs/tunnel.log" 2>&1 < /dev/null &

URL=""
for _ in $(seq 1 30); do
  URL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$ROOT/logs/tunnel.log" 2>/dev/null | head -1)
  [ -n "$URL" ] && break
  sleep 1
done

if [ -z "$URL" ]; then
  echo "No se pudo crear el tunel. Revisa logs/tunnel.log"
  exit 1
fi

echo
echo "=========================================================="
echo "  LINK PARA EL MOVIL (abrelo en el navegador del celular)"
echo
echo "      $URL"
echo
echo "  Usuario repartidor: driver1@enruta.test / Driver1234!"
echo "  (el link cambia cada vez que ejecutas este script)"
echo "=========================================================="
echo
