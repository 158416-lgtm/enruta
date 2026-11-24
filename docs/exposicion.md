# Exposición — EnRuta

Guion para **10–12 minutos**: 4 de arquitectura, 5 de demo en vivo, 2 de conclusiones.

## Antes de entrar (checklist de 2 minutos)

```bash
# 1. Todo arriba
docker compose -f infra/docker-compose.yml --profile obs up -d --wait
./scripts/preflight.sh

# 2. Pestañas abiertas en este orden:
#    http://localhost:5173        mapa
#    http://localhost:3000        Grafana (dashboard EnRuta)
#    http://localhost:8025        MailHog
#    terminal con los logs

# 3. Un envío ya creado y en curso (para no empezar con el mapa vacío)
./scripts/demo.sh new
```

Deja el portátil **enchufado**, notificaciones apagadas y el zoom del navegador al 100 %.

---

## Diapositivas

### 1. Problema (45 s)

- Última milla: asignar, seguir y entregar.
- Lo difícil no es el CRUD: es **asignar sin duplicar**, **seguir en vivo** y **no perder eventos**.
- Demo sobre Fusagasugá: calles reales, 6 conductores, 2–5 km por trayecto.

### 2. Arquitectura (2 min)

- 7 servicios + gateway + simulador; una base por servicio; Kafka como columna vertebral.
- Mostrar `docs/c4.md` (diagrama de contenedores).
- Frase clave: *"cada servicio es dueño de su dato; nadie escribe la tabla de otro"*.

### 3. El camino crítico (1 min)

- Diagrama de secuencia de la asignación.
- Las tres barreras: Redis `SET NX`, `UPDATE ... WHERE status='CREATED'`, índice único parcial.
- El test: **50 hilos, 1 ganador**.

### 4. Demo en vivo (5 min)

1. Crear el envío desde el mapa → `CREATED`.
2. En 2–5 s: oferta al más cercano → aceptación automática → `ASSIGNED`.
3. El conductor se mueve; el WebSocket pinta la posición y el ETA baja.
4. Llega al origen: **3 pings dentro de 100 m** → `PICKED_UP`.
5. Se aleja 150 m → `IN_TRANSIT`.
6. Llega al destino → `DELIVERED` → correo en MailHog.
7. Grafana: peticiones/s, latencia p95, heap, caché de ETA.

### 5. Lo que no se ve (1,5 min)

- **Outbox**: el evento se escribe en la misma transacción; el relay publica y solo entonces marca.
- **Idempotencia**: misma clave + mismo cuerpo ⇒ mismo envío; cuerpo distinto ⇒ 409.
- **Saga**: si el conductor abandona, el envío vuelve a `CREATED` y se reoferta.
- **Geocercas con histéresis**: 3 pings, no uno, porque el GPS oscila ±30 m.
- **Notificaciones**: fila persistida, reintentos con backoff, DLQ.

### 6. Decisiones y trade-offs (1 min)

- Kafka en vez de REST: orden por clave, replay, DLQ. Costo: consistencia eventual.
- Un PostgreSQL con 4 bases en local (RAM); en producción una instancia por servicio.
- WebSocket de una réplica, con la evolución a Redis Pub/Sub documentada.
- Tracking sin base de datos: el estado es reconstruible.

### 7. Calidad (45 s)

- 79 tests, incluidos IT con PostgreSQL, Redis y MailHog reales.
- 13 ADRs; cada decisión con alternativas descartadas.
- `mvn verify` en CI con smoke de infraestructura.

### 8. Qué haría con 3 meses más (45 s)

- Multi-tenant y facturación real.
- Tiles offline y app móvil real.
- Trazas distribuidas completas (Tempo) y SLOs con alertas.
- Reasignación predictiva (tiempo de espera estimado por zona).

---

## Guion de la demo (2 min, si el tiempo aprieta)

```
1. Navegador en :5173, sesión iniciada
2. Clic en "Parque Principal → Terminal"
3. Señalar: "CREATED... buscando conductor... oferta al más cercano"
4. El marcador se mueve; el ETA baja en el panel
5. "Llegó al origen" → PICKED_UP → IN_TRANSIT
6. "Llegó al destino" → DELIVERED
7. MailHog: correo de entrega
8. Grafana: 4 paneles
```

## Plan B

- Si la red falla: el mapa pierde las teselas (OSM es remoto) pero el resto funciona; ten una
  captura del mapa en las diapositivas.
- Si un servicio no arranca: `./scripts/reset.sh app` (2 min) o usa el video de respaldo.
- Ten el video grabado y accesible sin internet.

## Preguntas probables

| Pregunta | Respuesta corta |
|---|---|
| ¿Por qué no un monolito? | Lo justifican tres cargas distintas: ingesta GPS alta, lectura geoespacial y mensajería con reintentos. Y el equipo podría separarse por dominio. |
| ¿Cómo evitas eventos duplicados? | `processed_events` con `INSERT ... ON CONFLICT DO NOTHING` por `eventId`, y consumidores idempotentes. |
| ¿Qué pasa si Kafka está caído? | El outbox retiene el evento y el relay reintenta; el negocio no pierde el cambio pero se retrasa la publicación. |
| ¿Y si Redis se reinicia? | Se pierde la posición actual y el índice GEO; el seeder reconstruye el índice desde PostgreSQL y el siguiente ping corrige la posición. |
| ¿Cómo escalas el WebSocket? | Hoy una réplica; el plan es Redis Pub/Sub por envío, documentado en ADR-0010. |
| ¿Por qué RS256 y no un secreto compartido? | Ningún servicio puede falsificar tokens; el gateway valida contra JWKS sin llamar a auth. |
| ¿Dónde está la dificultad real? | En los caminos tristes: oferta que expira, aceptación simultánea, conductor que abandona, SMTP caído. |
