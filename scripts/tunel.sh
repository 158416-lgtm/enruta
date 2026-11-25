#!/usr/bin/env bash
# Crea (o renueva) el tunel HTTPS para abrir la app desde el movil y muestra la URL.
#
#   ./scripts/tunel.sh             # crea el tunel y muestra el banner
#   ./scripts/tunel.sh --url-only  # imprime solo la URL (para usar en scripts)
#
# El link cambia cada vez que lo ejecutas. Cloudflare limita los tuneles anonimos
# (error 429): si pasa, espera un par de minutos y reintenta.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="/tmp/cloudflared"
URL_ONLY=0
[ "${1:-}" = "--url-only" ] && URL_ONLY=1
mkdir -p "$ROOT/logs"

say() { [ "$URL_ONLY" -eq 1 ] || echo "$@"; }

if [ ! -x "$BIN" ]; then
  say "==> descargando cloudflared (solo la primera vez)"
  if ! curl -fsSL -o "$BIN" \
      https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64; then
    say "ERROR: no se pudo descargar cloudflared (¿hay internet?)"
    exit 1
  fi
  chmod +x "$BIN"
fi

kill_tunnel() {
  for pid in $(pgrep -f "^/tmp/cloudflared tunnel" 2>/dev/null || true); do
    kill "$pid" 2>/dev/null || true
  done
}

URL=""
for attempt in 1 2; do
  kill_tunnel
  sleep 1
  : > "$ROOT/logs/tunnel.log"
  setsid nohup "$BIN" tunnel --url http://localhost:5173 --no-autoupdate \
    > "$ROOT/logs/tunnel.log" 2>&1 < /dev/null &

  for _ in $(seq 1 20); do
    URL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$ROOT/logs/tunnel.log" 2>/dev/null | head -1)
    [ -n "$URL" ] && break
    grep -q "429" "$ROOT/logs/tunnel.log" 2>/dev/null && break
    sleep 1
  done

  [ -n "$URL" ] && break
  if grep -q "429" "$ROOT/logs/tunnel.log" 2>/dev/null; then
    say "    Cloudflare limito los tuneles (429). Reintento en 20 s ($attempt/2)…"
    sleep 20
  else
    sleep 5
  fi
done

if [ -z "$URL" ]; then
  say ""
  say "  No se pudo crear el tunel."
  say "  - Si es el limite de Cloudflare (429), espera 2 minutos y ejecuta: ./scripts/tunel.sh"
  say "  - Sin internet, usa la red local: http://$(hostname -I 2>/dev/null | awk '{print $1}'):5173"
  say "    (ojo: el GPS del movil necesita HTTPS, la red local sola no basta)"
  exit 1
fi

if [ "$URL_ONLY" -eq 1 ]; then
  echo "$URL"
  exit 0
fi

cat <<EOF

==========================================================
  LINK PARA EL MOVIL (abrelo en el navegador del celular)

      $URL

  Repartidor: driver1@enruta.test / Driver1234!
  (el link cambia cada vez que ejecutas este script)
==========================================================

EOF
