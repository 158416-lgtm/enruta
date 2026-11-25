# EnRuta

Plataforma de logística de última milla con microservicios: asignación de repartidores,
rastreo GPS en vivo y notificaciones. Demo sobre el casco urbano de **Fusagasugá, Cundinamarca**.

> Estado: **S0 — esqueleto, infraestructura y CI**. Los servicios arrancan y responden
> `/actuator/health`; la lógica de negocio entra desde S1 (ver [Roadmap](#roadmap)).

## Arquitectura

```
                        ┌──────────────┐
   React + Leaflet ────▶│ api-gateway  │ :18080 (JWT, rate limit, routing)
                        └──────┬───────┘
        ┌──────────────┬───────┼────────────┬──────────────┐
        ▼              ▼       ▼            ▼              ▼
   auth-service  shipment  dispatch   tracking     routing-eta   notification
     :8081        :8082     :8083       :8084         :8085          :8086
        │              │       │            │              │              │
   ┌────┴──────────────┴───────┴────────────┴──────────────┴──────────────┴────┐
   │ PostgreSQL (una base por servicio) · Kafka · Redis · OSRM · MailHog       │
   └───────────────────────────────────────────────────────────────────────────┘
```

| Servicio | Responsabilidad | Dato que posee |
|---|---|---|
| `api-gateway` | Ruteo, validación JWT, rate limit | — |
| `auth-service` | Usuarios, roles, emisión RS256 + JWKS | `authdb` |
| `shipment-service` | Ciclo de vida del envío, outbox | `shipmentdb` |
| `dispatch-service` | Matching conductor↔envío, ofertas | `dispatchdb` + Redis GEO |
| `tracking-service` | Ingesta GPS, geofences, WebSocket | Redis (efímero) |
| `routing-eta-service` | Rutas y ETA vía OSRM con caché y fallback | Redis (caché) |
| `notification-service` | Email/webhook con reintentos y DLQ | `notifdb` |

## Requisitos

- JDK 21, Maven 3.9+
- Docker Engine 24+ con Compose v2
- Node 20+ (solo para `web/`)
- 8 GB de RAM libres (16 recomendado)

Verificación rápida: `./scripts/preflight.sh`

## Arranque

```bash
# 1. Infraestructura (Kafka, PostgreSQL, Redis, MailHog)
docker compose -f infra/docker-compose.yml up -d --wait

# 2. Datos de rutas para OSRM (una sola vez, ~335 MB de descarga)
./scripts/fetch-osrm.sh

# 3. Servicios (perfil app) — opcional en S0
docker compose -f infra/docker-compose.yml --profile app up -d
```

Desarrollo local de un servicio (sin contenedor):

```bash
mvn -pl services/shipment-service -am spring-boot:run
```

> El broker se expone al host en `localhost:29092` (los contenedores usan `kafka:9092`).

## Build y calidad

```bash
mvn -T 1C verify        # compila, tests, jacoco y spotless
mvn spotless:apply      # formatea
./scripts/coverage.sh   # exige >= 70% de líneas (lo mismo que hace el CI)
```

## Puertos

| Servicio | Puerto | Servicio | Puerto |
|---|---|---|---|
| gateway | 18080 | tracking | 8084 |
| auth | 8081 | routing-eta | 8085 |
| shipment | 8082 | notification | 8086 |
| dispatch | 8083 | simulador | 8090 |
| web | 5173 | MailHog UI | 8025 |
| Grafana | 3000 | Prometheus | 9090 |
| OSRM | 5000 | PostgreSQL | 5433 |
| PostgreSQL | 5433 | Kafka (host) | 29092 |

> **Puertos no estándar:** el 8080 y el 5432 ya están ocupados en esta máquina, por eso el
> gateway usa **18080** y el PostgreSQL del compose se publica en **5433**. Los contenedores
> siguen hablando entre ellos por los puertos internos (8080, 5432).

## Frontend

React + Leaflet en `web/`: login, creación de envíos con rutas predefinidas, mapa con el
conductor en vivo (WebSocket), ETA y timeline de estados.

```bash
cd web && npm install && npm run dev    # http://localhost:5173
```

El proxy de Vite reenvía `/api` al gateway, así no hay CORS en desarrollo. Las teselas vienen de
OpenStreetMap (requieren internet); la ruta y los marcadores funcionan igual sin conexión.

## Observabilidad

```bash
docker compose -f infra/docker-compose.yml --profile obs up -d prometheus grafana
```

- Prometheus en `:9090` (8 targets: 7 servicios + él mismo).
- Grafana en `:3000`, dashboard **EnRuta · plataforma** provisionado y accesible de forma anónima.

## Juega los dos papeles

```bash
./scripts/start.sh && ./scripts/lab.sh simoff && ./scripts/lab.sh preparar && ./scripts/lab.sh reset

# Terminal 1 (cliente):  crear un envio
./scripts/cliente.py

# Terminal 2 (repartidor): ver ofertas, aceptar, reportar GPS
./scripts/repartidor.py
```

Guía paso a paso con lo que debes ver en cada momento:
[`docs/ser-cliente-y-repartidor.md`](docs/ser-cliente-y-repartidor.md).

**Repartidor con GPS real:** abre un túnel HTTPS y usa el móvil como conductor
(`cloudflared tunnel --url http://localhost:5173`). La vista del repartidor es una PWA con
`watchPosition`, filtrado de pings y modo simulado de respaldo.

## Ver los 7 servicios trabajando en vivo

```bash
./scripts/ver-servicios.sh
```

Una ventana con los 7 servicios en colores que va narrando lo que pasa mientras usas la app:

```
13:14:46  JEFE DE PEDIDOS  Envio creado · id ca96949b
13:14:46  COORDINADOR      Oferta enviada al repartidor 1 · 31 m · ronda 1
13:14:49  COORDINADOR      Asignacion confirmada: el pedido ya tiene repartidor
13:14:56  RASTREADOR       Geocerca confirmada (3 pings seguidos)
13:14:59  RASTREADOR       El repartidor se puso en camino
13:15:19  RASTREADOR       Geocerca confirmada (3 pings seguidos)
13:15:19  JEFE DE PEDIDOS  Evento publicado en Kafka · shipment.delivered
13:15:19  COORDINADOR      Repartidor liberado (puede recibir otro pedido)
13:15:20  CARTERO          Correo enviado a cliente@enruta.test · intento 1
```

## Laboratorio (opera el sistema tú mismo)

```bash
./scripts/lab.sh simoff          # apaga el simulador: ahora tú eres el repartidor
./scripts/lab.sh crear           # crea un envío como cliente
./scripts/lab.sh ofertas         # mira la oferta que te llega
./scripts/lab.sh aceptar         # acéptala
./scripts/lab.sh conducir        # conduce hasta el destino (o usa 'mover')
```

Guía completa con pruebas de idempotencia, doble aceptación simultánea, Kafka y resiliencia en
[`docs/laboratorio.md`](docs/laboratorio.md).

## Prueba de carga

```bash
RATE=300 DURATION=60s k6 run load/k6/ingest.js
```

Instrucciones completas en [`docs/operacion.md`](docs/operacion.md). Umbrales: 99% de `202` y
p95 < 200 ms.

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/c4.md`](docs/c4.md) | diagramas C4 y secuencias (Mermaid) |
| [`docs/exposicion.md`](docs/exposicion.md) | guion de 10–12 min, slides y preguntas probables |
| [`docs/operacion.md`](docs/operacion.md) | arranque, k6, observabilidad y problemas frecuentes |
| [`docs/laboratorio.md`](docs/laboratorio.md) | **guía práctica**: opera el sistema como cliente y repartidor |
| [`docs/enruta-explicado-con-plastilina.pdf`](docs/enruta-explicado-con-plastilina.pdf) | **PDF conceptual (14 pág.)**: los microservicios explicados con analogías y colores |
| [`docs/enruta-codigo-explicado.pdf`](docs/enruta-codigo-explicado.pdf) | **PDF técnico (15 pág.)**: arquitectura, 4 bases, 22 endpoints, anotaciones y código real |
| [`docs/adr/`](docs/adr) | 13 decisiones con alternativas descartadas |

## Decisiones

| ADR | Tema |
|---|---|
| [0001](docs/adr/0001-kafka-como-broker.md) | Kafka como broker de eventos |
| [0002](docs/adr/0002-postgres-unico-con-bases-separadas.md) | Un PostgreSQL con una base por servicio en local |
| [0003](docs/adr/0003-puertos-no-estandar.md) | Puertos 18080/5433/29092 por conflictos en la máquina |

## Entorno verificado

Comprobado en esta máquina, no solo compilado:

| Elemento | Resultado |
|---|---|
| Build | `mvn verify` verde, 12 módulos, **166 tests** y **71,9 % de cobertura de líneas** |
| Kafka | healthy; 9 tópicos creados (`tracking.gps.raw` con 6 particiones, retención 1 h) |
| PostgreSQL | healthy; 4 bases (`authdb`, `shipmentdb`, `dispatchdb`, `notifdb`) |
| Redis / MailHog | healthy |
| OSRM | extract de Fusagasugá generado (4,3 MB desde 320 MB de Colombia) y ruta real: **2,27 km / 237 s** |
| S1 de punta a punta | login → crear envío → outbox → relay → evento en `shipment.events`, con replay idempotente |
| S2 de punta a punta | 6 conductores simulados por calles reales → oferta al más cercano → aceptación → `ASSIGNED` en ~8 s, con `dispatch.offer-created` y `dispatch.assigned` en Kafka |
| Concurrencia | 50 hilos aceptando la misma oferta: **1 ganador, 49 conflictos, 1 asignación activa** |
| S3 en vivo | `CREATED → ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED` en ~2 min con WebSocket recibiendo posiciones; eventos `tracking.geofence-entered` (PICKUP/DROPOFF) y `tracking.trip-started` |
| S4 ETA | OSRM real **2.529 m / 273 s**, segunda llamada `cached:true`; con OSRM apagado responde `FALLBACK` en **43 ms** (haversine) |
| Recuperación | tras cada entrega los 6 conductores vuelven a `AVAILABLE` y al índice GEO; un segundo envío se asigna de inmediato |
| S5 correo | entrega → `notifications` en `SENT` con 1 intento → correo visible en MailHog (`:8025`) |
| S5 reintentos | MailHog apagado → 3 intentos (2s, 3s, 5s) → `FAILED` + mensaje en `notification.commands.dlq` con destinatario y error |
| S6 frontend | `vite build` OK; login por proxy y listado de envíos verificado contra el gateway |
| S6 observabilidad | Prometheus **8/8 targets UP**; dashboard de Grafana provisionado |

> **Docker 29 + Testcontainers:** el cliente de Testcontainers usa API 1.32 y Docker 29 exige
> 1.44. En esta máquina se resolvió con `~/.docker-java.properties` → `api.version=1.44`.
> El CI hace lo mismo antes de correr los tests.

## Roadmap

| Sprint | Alcance | Estado |
|---|---|---|
| S0 | Esqueleto, compose, CI, ADRs | ✅ |
| S1 | auth + gateway + shipment (máquina de estados, outbox, idempotencia) | ✅ |
| S2 | dispatch (ofertas, timeout, anti-doble-asignación) + simulador | ✅ |
| S3 | tracking (ingesta, Redis GEO, WebSocket, geofences) | ✅ |
| S4 | routing/ETA + OSRM + circuit breaker | ✅ |
| S5 | notifications (reintentos, DLQ, MailHog) | ✅ |
| S6 | Frontend, observabilidad, k6, documentación y demo | ✅ |

## API disponible (S1)

| Método | Ruta | Notas |
|---|---|---|
| POST | `/api/v1/auth/register` | rol `CLIENT` por defecto |
| POST | `/api/v1/auth/login` | devuelve access + refresh |
| POST | `/api/v1/auth/refresh` | rotación: el refresh usado se revoca |
| POST | `/api/v1/auth/logout` | revoca el refresh |
| GET | `/api/v1/auth/jwks` | llave pública (público) |
| GET | `/api/v1/auth/me` | requiere token |
| POST | `/api/v1/shipments` | **requiere `Idempotency-Key`** |
| GET | `/api/v1/shipments/{id}` | dueño, conductor asignado o admin |
| POST | `/api/v1/shipments/{id}/cancel` | cliente hasta `ASSIGNED`; admin hasta `IN_TRANSIT` |
| POST | `/api/v1/drivers/{id}/location` | el conductor reporta posición (202) |
| POST | `/api/v1/drivers/{id}/availability` | `AVAILABLE` / `BUSY` / `OFFLINE` |
| GET | `/api/v1/drivers/{id}/offers` | ofertas vigentes del conductor |
| POST | `/api/v1/offers/{id}/accept` | doble barrera; 409 si te adelantaron |
| POST | `/api/v1/offers/{id}/reject` | pasa al siguiente candidato |
| POST | `/api/v1/assignments/{id}/cancel` | compensación de la saga |
| GET | `/api/v1/dispatch/shipments/{id}` | fase, ronda y oferta actual (diagnóstico) |
| POST | `/api/v1/tracking/drivers/{id}/location` | lote de hasta 10 puntos, responde **202** |
| GET | `/api/v1/tracking/shipments/{id}` | última posición y suscriptores |
| WS | `/ws/tracking?shipmentId=&token=` | posiciones en vivo |
| GET | `/api/v1/eta?fromLat=&fromLng=&toLat=&toLng=` | ruta y ETA con caché y plan B |
| GET | `/api/v1/notifications?shipmentId=` | historial de notificaciones del envío |
| GET | `/api/v1/notifications/{id}` | estado, intentos y último error |
| POST | `/api/v1/notifications/test` | solo ADMIN; encola un correo de prueba |

### Canal en vivo (WebSocket)

Al conectar con un JWT válido el servidor envía un *snapshot* y luego cada posición:

```json
{"type":"position","shipmentId":"...","driverId":"...","lat":4.3449,"lng":-74.3674,"at":"..."}
{"type":"status","shipmentId":"...","status":"DELIVERED"}
{"type":"subscribed","shipmentId":"..."}
```

El cliente debe reconectar con backoff (1 s → 30 s) y, al volver, pedir
`GET /api/v1/tracking/shipments/{id}` para resincronizar.

### Ciclo completo

`CREATED → ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED`, todo automático:
el simulador conduce al origen, la geocerca de 100 m (con 3 pings consecutivos) confirma la
recogida, alejarse 150 m marca `IN_TRANSIT` y llegar al destino cierra el envío.

### Flujo de asignación

1. El cliente crea el envío (`CREATED`).
2. `dispatch` consume `shipment.created`, busca con Redis GEO y oferta **a los 3 mejores a la vez**
   (70% cercanía, 30% calificación), 90 s de vigencia. Gana el que acepte primero.
3. El conductor acepta: Redis `SET NX` + `UPDATE ... WHERE status='CREATED'` + índice único parcial;
   al ganar, las ofertas de los demás quedan retiradas.
4. `dispatch.assigned` → el envío pasa a `ASSIGNED`.
5. Si la oferta vence, se prueba el siguiente candidato; 3 por ronda, radios 1,5 → 3 → 5 km.
6. Si el conductor abandona: `dispatch.assignment-cancelled` → vuelve a `CREATED` y se re-oferta.

Usuarios de demo (`enruta.auth.seed=true`): `admin@enruta.test` / `Admin1234!`,
`cliente@enruta.test` / `Cliente1234!`, `driver{1..3}@enruta.test` / `Driver1234!`.

```bash
TOKEN=$(curl -s -X POST localhost:18080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"cliente@enruta.test","password":"Cliente1234!"}' | jq -r .accessToken)

curl -X POST localhost:18080/api/v1/shipments \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"pickup":{"lat":4.3449,"lng":-74.3674,"address":"Parque Principal"},
       "dropoff":{"lat":4.3374,"lng":-74.3600,"address":"Terminal"}}'
```

## Licencia

MIT.
