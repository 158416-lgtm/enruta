# Operación y demo

## Arranque completo

```bash
# 1. Infraestructura + observabilidad
docker compose -f infra/docker-compose.yml --profile obs up -d --wait

# 2. Datos de rutas (una sola vez)
./scripts/fetch-osrm.sh

# 3. Servicios (7) con sus propiedades
./scripts/run-local.sh

# 4. Frontend
cd web && npm install && npm run dev
```

`scripts/run-local.sh` arranca los servicios con las variables correctas y espera a que estén sanos.

## Prueba de carga (k6)

k6 no viene con el proyecto. Instalación:

```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

Ejecución (el límite por conductor se eleva solo para la prueba):

```bash
# arrancar tracking con el límite alto
MAX_BATCHES=100 java -jar services/tracking-service/target/tracking-service-0.1.0-SNAPSHOT.jar

RATE=300 DURATION=60s k6 run load/k6/ingest.js
```

Umbrales exigidos: 99% de `202`, p95 < 200 ms. En operación normal el límite es **3 lotes/s por
conductor** (ADR-0011); la prueba lo sube para medir el pipeline, no la política.

## Observabilidad

| Herramienta | URL | Qué mirar |
|---|---|---|
| Prometheus | http://localhost:9090 | `/targets` debe mostrar 8 UP |
| Grafana | http://localhost:3000 | dashboard **EnRuta · plataforma** (anónimo, sin login) |
| MailHog | http://localhost:8025 | correos de entrega |
| Actuator | `:18080/actuator/health` | estado por servicio |

Métricas propias destacadas:

- `eta_requests_total{result="hit|miss|fallback"}` — eficacia de la caché y del plan B.
- `notification_attempts_total{result="sent|retry|dead_letter"}` — salud del canal de correo.
- `gps_accepted` / latencia de ingesta (k6).

## Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `docker compose up` falla en `osrm` | faltan datos | `./scripts/fetch-osrm.sh` |
| Servicios no conectan a PostgreSQL | puerto ocupado por el PostgreSQL del sistema | el compose publica **5433**; revisa `.env` |
| `401` en `/actuator/prometheus` | seguridad | ya está permitido; si cambias reglas, recuerda este path |
| Mapas en gris | sin internet para las teselas | normal offline; el resto del mapa (ruta, marcadores) sigue |
| Conductores `BUSY` para siempre | versión vieja de dispatch | reinicia dispatch: al entregar se liberan |
| Testcontainers no encuentra Docker | Docker ≥ 29 + cliente API 1.32 | `echo api.version=1.44 > ~/.docker-java.properties` |
