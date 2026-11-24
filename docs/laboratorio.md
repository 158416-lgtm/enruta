# Laboratorio: opera EnRuta como cliente y como repartidor

Este documento no es una demo pasiva. Cada comando es una petición real a la API con un JWT
real; no se toca la base de datos ni se simula nada por dentro. Si algo aparece aquí, se puede
demostrar delante de cualquiera.

## Modelo mental en 30 segundos

```
Tú (cliente)  ──HTTP──▶  gateway  ──▶  shipment   ──outbox──▶ Kafka ──▶ dispatch
                                                                          │
Tú (repartidor) ─HTTP──▶ gateway ──▶  tracking  ──Kafka──▶ shipment ──▶ notification ──▶ SMTP
                                 │
                                 └──WebSocket──▶ mapa
```

| Rol | Quién manda | Qué provoca |
|---|---|---|
| **Cliente** | `cliente@enruta.test` | crea el envío, lo sigue, lo cancela |
| **Repartidor** | `driver1..6@enruta.test` | reporta posición, recibe y acepta ofertas |
| **Admin** | `admin@enruta.test` | ve todo, puede cancelar en cualquier estado |

Reglas que hacen el sistema "real":

- Cada servicio tiene **su propia base** y nadie escribe la tabla de otro.
- Los cambios de estado viajan por **eventos** (Kafka), no por llamadas HTTP entre servicios.
- Un envío **no puede** quedar con dos repartidores (tres defensas, ver ADR-0007).
- Todo lo que ves en el mapa viene de un ping GPS autenticado.

## Preparación

```bash
./scripts/start.sh            # todo arriba (o ./scripts/run-local.sh si ya esta)
./scripts/lab.sh simoff       # apaga el simulador: ahora TÚ eres el repartidor
./scripts/lab.sh reset        # limpia el estado del laboratorio
```

> Con el simulador apagado, las ofertas duran **15 segundos**. Ten el comando de aceptar a mano.

---

## Ejercicio 1 — Eres el cliente

```bash
./scripts/lab.sh login cliente
./scripts/lab.sh crear
```

Salida real:

```
ENVIO CREADO (preset: parque)
  id: 5e18b2af-c617-49ab-aa18-9bfbef3751f6
  estado: CREATED  ·  PIN de recogida: 2100
```

Abre **http://localhost:5173**, entra con `cliente@enruta.test` / `Cliente1234!` y verás el envío
en la lista. Mientras el simulador está apagado, nadie lo acepta: a los 15 s la oferta expira y el
despacho prueba el siguiente candidato (3 por ronda, radios 1,5 → 3 → 5 km).

**Qué demuestra:** el cliente solo habla con el gateway; el envío nace en `shipment-service` y se
publica el evento `shipment.created` **en la misma transacción** (outbox).

### Prueba la idempotencia

```bash
KEY=$(uuidgen)
BODY='{"pickup":{"lat":4.3449,"lng":-74.3674,"address":"Parque"},
       "dropoff":{"lat":4.3374,"lng":-74.36,"address":"Terminal"}}'

curl -s -X POST localhost:18080/api/v1/shipments \
  -H "Authorization: Bearer $(cat logs/lab/token-cliente.txt)" \
  -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$BODY" | jq -r .id
# repite el MISMO comando:
```

Ambas respuestas devuelven **el mismo id**. Cambia el cuerpo manteniendo la clave:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:18080/api/v1/shipments \
  -H "Authorization: Bearer $(cat logs/lab/token-cliente.txt)" \
  -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"pickup":{"lat":4.35,"lng":-74.36,"address":"X"},"dropoff":{"lat":4.33,"lng":-74.35,"address":"Y"}}'
# 409
```

**Qué demuestra:** un reintento del cliente (red móvil, doble clic, timeout) no crea envíos
duplicados. Verificado: `mismo id` y `409 IDEMPOTENCY_KEY_REUSED`.

---

## Ejercicio 2 — Eres el repartidor

```bash
./scripts/lab.sh ofertas
```

```
driver1 (Ana  )  oferta 6d785303-6c67-43ca-9bdd-8bb36ded0d60  envio 75cb052b  186 m  vence en 12s
```

Acéptala:

```bash
./scripts/lab.sh aceptar
```

```
OFERTA ACEPTADA por driver1 (Ana)
  assignmentId: 6d8f87b7-9ea0-41f3-88ee-a4f87b361564
  envio 5e18b2af → ASSIGNED en 1-3 s (evento por Kafka)
```

Comprueba el efecto desde el otro lado:

```bash
./scripts/lab.sh estado
# ENVIO 5e18b2af · ASSIGNED · conductor: 1111...101
# CREATED → ASSIGNED
```

**Qué demuestra:** el conductor actúa con **su** token (rol `DRIVER`); dispatch valida que la
oferta sea suya, toma el lock en Redis, actualiza condicionalmente en PostgreSQL e inserta la
asignación con índice único.

### Conduce tú mismo

Manual, un ping a la vez:

```bash
./scripts/lab.sh mover 1 4.3435 -74.3650
./scripts/lab.sh mover 1 4.3442 -74.3660
./scripts/lab.sh mover 1 4.3449 -74.3674     # dentro de 100 m del origen
```

Hacen falta **3 pings consecutivos** dentro de la geocerca (histéresis): el tercero dispara
`PICKED_UP`. Luego, al alejarte más de 150 m, `IN_TRANSIT`.

O deja que conduzca la ruta completa (lo que haría la app del repartidor):

```bash
./scripts/lab.sh conducir 1
```

```
ASSIGNED   → PICKED_UP    05:05:22
PICKED_UP  → IN_TRANSIT   05:05:25
IN_TRANSIT → DELIVERED    05:05:45
posicion 4.33725,-74.36007 · 19 m al destino · ETA 0 s
```

**Qué demuestra:** las transiciones no las decide una persona: las produce la geocerca sobre pings
reales, y `shipment-service` las aplica al consumir `tracking.events`.

---

## Ejercicio 3 — Demuéstralo con números

### Doble aceptación simultánea

```bash
OFFER=<pega aqui el uuid completo de la oferta>
DT=$(./scripts/lab.sh login driver1 >/dev/null; cat logs/lab/token-driver1.txt)

( curl -s -o /tmp/a1 -w '%{http_code}\n' -X POST localhost:18080/api/v1/offers/$OFFER/accept -H "Authorization: Bearer $DT" ) &
( curl -s -o /tmp/a2 -w '%{http_code}\n' -X POST localhost:18080/api/v1/offers/$OFFER/accept -H "Authorization: Bearer $DT" ) &
wait
```

Resultado real:

```
respuesta 1: 409  OFFER_ALREADY_CLAIMED
respuesta 2: 200
```

**Qué demuestra:** 50 hilos en el test automático, y aquí dos peticiones reales: **solo una
gana**. Si repites con `driver2` verás `403` (la oferta no es suya).

### El evento existe en Kafka

```bash
./scripts/lab.sh eventos shipment.events
./scripts/lab.sh eventos dispatch.events
./scripts/lab.sh eventos tracking.events
```

**Qué demuestra:** el cambio de estado no viaja por HTTP: queda publicado y es reproducible
(replay). Es la diferencia entre "funciona" y "es auditable".

### El correo llegó de verdad

Abre **http://localhost:8025** o:

```bash
curl -s "http://localhost:8025/api/v2/messages?limit=3" | jq -r '.items[] | .Content.Headers.Subject[0]'
# Tu envio fue entregado
```

Y su registro en la base de notificaciones:

```bash
SID=$(jq -r .shipmentId logs/lab/state.json)
curl -s "localhost:18080/api/v1/notifications?shipmentId=$SID" \
  -H "Authorization: Bearer $(cat logs/lab/token-cliente.txt)" \
  | jq -r '.[] | "\(.template) → \(.recipient) | \(.status) | intentos=\(.attempts)"'
# SHIPMENT_DELIVERED → cliente@enruta.test | SENT | intentos=1
```

### El ETA sale de un motor de rutas real

```bash
curl -s "localhost:18080/api/v1/eta?fromLat=4.3449&fromLng=-74.3674&toLat=4.3374&toLng=-74.36" \
  -H "Authorization: Bearer $(cat logs/lab/token-cliente.txt)" | jq
# {"distanceMeters":2529,"durationSeconds":273,"cached":false,"source":"OSRM"}
# repite y veras "cached": true
```

---

## Ejercicio 4 — Rompe cosas (esto es lo que convence)

### Apaga las notificaciones

```bash
fuser -k 8086/tcp          # notification-service fuera
# crea y entrega otro envio
./scripts/lab.sh estado    # el envio se entrega igual
```

El negocio no depende del correo: `shipment` publica un comando y sigue. Cuando vuelvas a
levantar el servicio, las notificaciones pendientes se reintentan.

### Apaga OSRM

```bash
docker compose -f infra/docker-compose.yml stop osrm
curl -s "localhost:18080/api/v1/eta?fromLat=4.352&fromLng=-74.36&toLat=4.335&toLng=-74.365" \
  -H "Authorization: Bearer $(cat logs/lab/token-cliente.txt)" | jq
# {"source":"FALLBACK", ...} en ~40 ms
docker compose -f infra/docker-compose.yml start osrm
```

**Qué demuestra:** el ETA tiene plan B; el mapa no se queda sin respuesta.

### El repartidor abandona

```bash
ASSIGNMENT=<assignmentId devuelto al aceptar>
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:18080/api/v1/assignments/$ASSIGNMENT/cancel \
  -H "Authorization: Bearer $(cat logs/lab/token-driver1.txt)"
# 204, y el envio vuelve a CREATED y se reoferta
```

**Qué demuestra:** la **saga** compensa. No queda un envío huérfano ni un repartidor ocupado para
siempre.

---

## Ejercicio 5 — Observabilidad

- **Grafana** `http://localhost:3000` → dashboard *EnRuta · plataforma*: peticiones/s, latencia
  p95, heap, caché de ETA, intentos de notificación.
- **Prometheus** `http://localhost:9090` → prueba estas consultas:

```promql
sum(up{job="enruta"})                                  # 7 servicios
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))
sum(rate(eta_requests_total[1m])) by (result)
sum(rate(notification_attempts_total[1m])) by (result)
```

---

## Chuleta

| Comando | Para qué |
|---|---|
| `lab.sh login <rol>` | obtiene un JWT real y lo guarda |
| `lab.sh crear [preset]` | crea un envío (`parque`, `balmoral`, `hospital`) |
| `lab.sh estado` | estado, historial, posición y ETA |
| `lab.sh ofertas` | ofertas pendientes de los 6 conductores |
| `lab.sh aceptar` / `rechazar` | responde a la oferta vigente |
| `lab.sh mover <n> <lat> <lng>` | un ping GPS manual |
| `lab.sh conducir [n]` | ruta completa por calles hasta el destino |
| `lab.sh ws` | canal WebSocket en la terminal |
| `lab.sh eventos [tópico]` | últimos eventos de Kafka |
| `lab.sh simoff` / `simon` | apaga/enciende el simulador de conductores |
| `lab.sh reset` | borra el estado local del laboratorio |

## Cómo leer tu propio JWT

```bash
python3 - <<'PY'
import base64, json, pathlib
token = pathlib.Path("logs/lab/token-cliente.txt").read_text().strip()
payload = token.split(".")[1]
payload += "=" * (-len(payload) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(payload)), indent=2))
PY
```

Verás `sub` (id), `roles`, `email`, `iss` y `exp`: el servidor no guarda sesiones, todo está firmado
con RS256 y se valida con la llave pública de `auth-service`.
