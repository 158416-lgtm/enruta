#!/usr/bin/env bash
# Deja el entorno limpio y arriba. Uso: ./scripts/reset.sh [app|obs]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml")

PROFILES=()
case "${1:-core}" in
  app) PROFILES=(--profile app --profile routing) ;;
  obs) PROFILES=(--profile app --profile routing --profile obs) ;;
  core) ;;
  *) echo "uso: $0 [core|app|obs]"; exit 2 ;;
esac

"${COMPOSE[@]}" "${PROFILES[@]}" down -v --remove-orphans
"${COMPOSE[@]}" "${PROFILES[@]}" up -d --wait
"${COMPOSE[@]}" ps
