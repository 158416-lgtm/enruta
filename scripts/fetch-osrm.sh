#!/usr/bin/env bash
# Descarga el extract de Colombia, lo recorta al casco urbano de Fusagasuga y
# preprocesa los datos de OSRM. Todo el resultado queda en infra/osrm/data.
#
# Solo se ejecuta una vez; despues la demo funciona sin red.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="$ROOT/infra/osrm/cache"
DATA="$ROOT/infra/osrm/data"

# Orden: oeste, sur, este, norte. Casco urbano de Fusagasuga.
BBOX="${OSRM_BBOX:--74.395,4.325,-74.335,4.365}"
SOURCE_URL="https://download.geofabrik.de/south-america/colombia/colombia-latest.osm.pbf"
OSRM_IMAGE="${OSRM_IMAGE:-osrm/osrm-backend:v5.25.0}"
OSMIUM_IMAGE="${OSMIUM_IMAGE:-alpine:3.20}"

mkdir -p "$CACHE" "$DATA"

echo "==> 1/5 Descargando extract de Colombia (~335 MB) si hace falta"
if [ ! -f "$CACHE/colombia.osm.pbf" ]; then
  curl -L --fail --progress-bar -o "$CACHE/colombia.osm.pbf.part" "$SOURCE_URL"
  mv "$CACHE/colombia.osm.pbf.part" "$CACHE/colombia.osm.pbf"
fi

echo "==> 2/5 Recortando bbox=$BBOX"
docker run --rm \
  -v "$CACHE:/cache:ro" \
  -v "$DATA:/data" \
  "$OSMIUM_IMAGE" sh -c "
    apk add --no-cache osmium-tool >/dev/null &&
    osmium extract --overwrite --bbox ${BBOX} \
      --output /data/fusagasuga.osm.pbf /cache/colombia.osm.pbf
  "

ls -lh "$DATA/fusagasuga.osm.pbf"
sha256sum "$DATA/fusagasuga.osm.pbf" | tee "$DATA/fusagasuga.sha256"

echo "==> 3/5 osrm-extract"
docker run --rm -v "$DATA:/data" "$OSRM_IMAGE" \
  osrm-extract -p /opt/car.lua /data/fusagasuga.osm.pbf

echo "==> 4/5 osrm-partition"
docker run --rm -v "$DATA:/data" "$OSRM_IMAGE" osrm-partition /data/fusagasuga.osrm

echo "==> 5/5 osrm-customize"
docker run --rm -v "$DATA:/data" "$OSRM_IMAGE" osrm-customize /data/fusagasuga.osrm

echo
echo "Listo. Prueba con:"
echo "  docker compose -f infra/docker-compose.yml up -d osrm"
echo "  curl 'http://localhost:5000/route/v1/driving/-74.3674,4.3449;-74.3600,4.3400?overview=false'"
