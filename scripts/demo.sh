#!/usr/bin/env bash
# Imprime el guion de la exposicion y abre las pestanas necesarias.
set -uo pipefail

BASE="${ENRUTA_BASE:-http://localhost}"

cat <<'GUION'
==========================================================
 GUION DE DEMO (2 min) - EnRuta
==========================================================
 1. Terminal A: docker compose -f infra/docker-compose.yml ps   -> todo "healthy"
 2. Navegador: /map           conductores moviendose en Fusagasuga
 3. Terminal B: ./scripts/demo.sh new   (crea un envio de ejemplo)
 4. Logs de dispatch: oferta -> aceptacion -> ASSIGNED
 5. Mapa: ruta OSRM dibujada y ETA bajando
 6. Al llegar: PICKED_UP -> IN_TRANSIT -> DELIVERED
 7. MailHog: email de entrega | UI: timeline de eventos
 8. Grafana: dispatch-health y tracking-pipeline con k6 corriendo
 9. Tempo: un traceId atravesando 5 servicios
10. Cierre: 0 dobles asignaciones, lag p95 < 1 s
==========================================================
GUION

case "${1:-help}" in
  urls)
    for u in "$BASE:5173" "$BASE:3000" "$BASE:8025" "$BASE:18080/actuator/health"; do echo "  $u"; done
    ;;
  new)
    echo "Pendiente: implementado en S2 (crear envio de ejemplo con curl)."
    ;;
  *) echo "uso: $0 [urls|new|help]" ;;
esac
