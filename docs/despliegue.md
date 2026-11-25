# Despliegue de EnRuta

## 1. ¿Cloudflare Tunnel cuenta como despliegue?

**No.** Un túnel de Cloudflare (`cloudflared tunnel --url ...`) es un **puente temporal** entre
Internet y un proceso que corre en tu computador. Sirve para demostrar, no para desplegar:

| Criterio de "desplegado" | Cloudflare Tunnel |
|---|---|
| Corre sin tu computador encendido | ❌ No: si apagas el PC, el servicio muere |
| URL estable | ❌ Cambia cada vez que se reinicia el túnel rápido |
| Disponibilidad / SLA | ❌ Ninguno |
| Escala o réplicas | ❌ Una sola instancia local |
| Secretos y datos en un entorno gestionado | ❌ Siguen en tu máquina |
| Dominio propio y TLS | ⚠️ Solo si configuras un túnel con nombre y dominio |

**Conclusión:** el túnel es una herramienta de *exposición temporal* para la demo. El proyecto
está **listo para desplegarse** (imágenes, compose de producción y CI), pero no está desplegado en
un proveedor cloud porque eso exige una cuenta y credenciales que no están disponibles en este
entorno. Cualquiera de las dos rutas siguientes lo deja en producción en menos de una hora.

## 2. Opción A — VPS con Docker Compose (recomendada)

Es la opción natural para este proyecto: Kafka, PostgreSQL, Redis, OSRM y 7 servicios Spring
necesitan memoria y procesos persistentes. Un VPS de **8 GB de RAM / 4 vCPU** es el mínimo
razonable (16 GB si además corres Grafana y Prometheus).

### 2.1 Preparar el servidor

```bash
# Ubuntu 24.04
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo usermod -aG docker "$USER" && newgrp docker

git clone https://github.com/158416-lgtm/enruta.git
cd enruta
cp .env.example .env 2>/dev/null || true
```

### 2.2 Variables de producción

Crea `infra/.env` (nunca lo subas al repositorio):

```dotenv
POSTGRES_PASSWORD=cambia-esta-clave
JWT_ISSUER=https://enruta.tudominio.com
WEBHOOK_SECRET=otra-clave-larga
```

> En producción cambia también `DB_PASSWORD` en `infra/docker-compose.yml` y el
> `POSTGRES_PASSWORD` del contenedor. Los valores `enruta/enruta` son solo para desarrollo.

### 2.3 Rutas de OSRM (una sola vez)

```bash
./scripts/fetch-osrm.sh     # descarga el extract de Colombia y recorta Fusagasugá
```

### 2.4 Levantar todo

```bash
docker compose -f infra/docker-compose.yml \
  --profile app --profile obs up -d --build --wait
```

Esto construye una imagen por microservicio con el `Dockerfile` multi-módulo de la raíz y levanta:

| Contenedor | Puerto host | Notas |
|---|---|---|
| `enruta-api-gateway` | 18080 | único punto de entrada |
| `enruta-auth` | 8081 | |
| `enruta-shipment` | 8082 | |
| `enruta-dispatch` | 8083 | |
| `enruta-tracking` | 8084 | |
| `enruta-routing` | 8085 | |
| `enruta-notification` | 8086 | |
| `enruta-postgres` | 5433 | 4 bases |
| `enruta-kafka` | 29092 | |
| `enruta-redis` | 6379 | |
| `enruta-osrm` | 5000 | |
| `enruta-mailhog` | 8025 | cambia a SMTP real en producción |
| `enruta-prometheus` | 9090 | perfil `obs` |
| `enruta-grafana` | 3000 | perfil `obs` |

### 2.5 Dominio y HTTPS con Caddy

```bash
sudo apt install -y caddy
sudo tee /etc/caddy/Caddyfile >/dev/null <<'EOF'
enruta.tudominio.com {
    reverse_proxy localhost:18080
}
EOF
sudo systemctl reload caddy
```

Caddy obtiene el certificado Let's Encrypt automáticamente. El frontend se sirve aparte
(`web/dist`) o se publica en Cloudflare Pages / Netlify apuntando `VITE_API_URL` al gateway.

### 2.6 Verificación

```bash
./scripts/preflight.sh
curl -fsS https://enruta.tudominio.com/actuator/health
./load/ab.sh 2000 20
```

## 3. Opción B — Railway / Render / Fly.io

Ventaja: no administras servidor. Desventaja: el stack tiene **7 servicios + Kafka + 4 bases +
Redis**; en planes gratuitos no cabe completo.

| Pieza | Servicio gestionado sugerido |
|---|---|
| 7 microservicios | Railway/Render (una imagen por servicio, mismas variables de `x-app-env`) |
| PostgreSQL | Neon / Supabase / Railway Postgres (4 bases o 4 esquemas) |
| Redis | Upstash Redis |
| Kafka | Upstash Kafka o Redpanda Cloud (Kafka compatible) |
| OSRM | Contenedor propio en Railway/Fly con el extract de Fusagasugá |
| Correo | Resend / SendGrid / Amazon SES (SMTP) en lugar de MailHog |

Variables que debe recibir cada servicio (las mismas del compose):

```dotenv
DB_USER=...
DB_PASSWORD=...
DB_URL=jdbc:postgresql://<host>:5432/<base>
KAFKA_BOOTSTRAP=<host>:9092
REDIS_HOST=...
REDIS_PORT=6379
OSRM_BASE_URL=https://<osrm>
MAIL_HOST=...
MAIL_PORT=587
AUTH_JWKS_URI=https://<auth>/api/v1/auth/jwks
SPRING_PROFILES_ACTIVE=prod
```

> El contrato de eventos exige que **todos** los servicios apunten al mismo clúster Kafka y que
> los tópicos existan antes de arrancar. Ejecuta `infra/kafka/topics-init.sh` contra el clúster
> gestionado.

### 3.1 Imágenes listas para publicar

El workflow [`.github/workflows/images.yml`](../.github/workflows/images.yml) construye y publica
las 7 imágenes en GitHub Container Registry (GHCR) al crear un tag `v*`:

```bash
git tag v1.0.0 && git push origin v1.0.0
docker pull ghcr.io/158416-lgtm/enruta/api-gateway:v1.0.0
```

## 4. CI/CD

| Workflow | Qué hace |
|---|---|
| `ci.yml` | `mvn verify` + umbral de cobertura + smoke de infraestructura |
| `images.yml` | publica las 7 imágenes en GHCR al etiquetar |

El despliegue automático al VPS se puede añadir con un step SSH que ejecute
`docker compose pull && docker compose up -d`; queda documentado pero no activo porque requiere
la clave privada del servidor.

## 5. Checklist antes de la entrega

- [ ] `mvn verify` en verde y cobertura sobre el umbral.
- [ ] `docker compose -f infra/docker-compose.yml --profile app up -d --wait` responde `UP`.
- [ ] `POST /api/v1/notifications/test` deja el correo en MailHog (o en el SMTP real).
- [ ] `./load/ab.sh` con p95 ≤ 500 ms.
- [ ] Cambiar `DB_PASSWORD`, `WEBHOOK_SECRET` y el correo de prueba por valores reales.
- [ ] Configurar respaldo de PostgreSQL (`pg_dump` diario) y retención de Kafka.
