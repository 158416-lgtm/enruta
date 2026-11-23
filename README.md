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
```

## Puertos

| Servicio | Puerto | Servicio | Puerto |
|---|---|---|---|
| gateway | 18080 | tracking | 8084 |
| auth | 8081 | routing-eta | 8085 |
| shipment | 8082 | notification | 8086 |
| dispatch | 8083 | simulador | 8090 |
| web | 5173 | MailHog UI | 8025 |
| Grafana | 3000 | OSRM | 5000 |
| PostgreSQL | 5433 | Kafka (host) | 29092 |

> **Puertos no estándar:** el 8080 y el 5432 ya están ocupados en esta máquina, por eso el
> gateway usa **18080** y el PostgreSQL del compose se publica en **5433**. Los contenedores
> siguen hablando entre ellos por los puertos internos (8080, 5432).

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
| Build | `mvn verify` verde, 12 módulos, 26 tests (incluye IT con PostgreSQL en Testcontainers) |
| Kafka | healthy; 9 tópicos creados (`tracking.gps.raw` con 6 particiones, retención 1 h) |
| PostgreSQL | healthy; 4 bases (`authdb`, `shipmentdb`, `dispatchdb`, `notifdb`) |
| Redis / MailHog | healthy |
| OSRM | extract de Fusagasugá generado (4,3 MB desde 320 MB de Colombia) y ruta real: **2,27 km / 237 s** |
| S1 de punta a punta | login → crear envío → outbox → relay → evento en `shipment.events`, con replay idempotente |

> **Docker 29 + Testcontainers:** el cliente de Testcontainers usa API 1.32 y Docker 29 exige
> 1.44. En esta máquina se resolvió con `~/.docker-java.properties` → `api.version=1.44`.
> El CI hace lo mismo antes de correr los tests.

## Roadmap

| Sprint | Alcance | Estado |
|---|---|---|
| S0 | Esqueleto, compose, CI, ADRs | ✅ |
| S1 | auth + gateway + shipment (máquina de estados, outbox, idempotencia) | ✅ |
| S2 | dispatch (ofertas, timeout, anti-doble-asignación) + simulador | ⏳ |
| S3 | tracking (ingesta, Redis GEO, WebSocket, geofences) | ⏳ |
| S4 | routing/ETA + OSRM + circuit breaker | ⏳ |
| S5 | notifications (reintentos, DLQ, MailHog) | ⏳ |
| S6 | Observabilidad, k6, documentación y demo | ⏳ |

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
