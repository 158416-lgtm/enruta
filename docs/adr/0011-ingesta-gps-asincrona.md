# ADR-0011 — Ingesta GPS asíncrona con 202 y rate limit

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S3

## Contexto

Si la ingesta esperara a Kafka (o a la base) para responder, un pico de pings bloquearía a los
clientes móviles y la latencia se dispararía. Además, un GPS defectuoso puede enviar decenas de
posiciones por segundo.

## Decisión

- `POST /api/v1/tracking/drivers/{id}/location` acepta un **lote de hasta 10 puntos** y responde
  **202 Accepted** inmediatamente después de publicar en `tracking.gps.raw`.
- El procesamiento (Redis, geocercas, WebSocket) ocurre en un **consumidor separado** con su propio
  grupo (`tracking-fanout`), escalable de forma independiente.
- **Rate limit** por conductor: 3 lotes por segundo con contador Redis (`INCR` + TTL 1 s). Al
  superarlo: `429 RATE_LIMITED`.
- Partición por `driverId`: las posiciones de un conductor mantienen orden.
- El consumidor descarta duplicados por `(driverId, recordedAt)` implícitamente al ser idempotente
  por naturaleza (sobrescribe la última posición).

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Responder 200 tras escribir en Redis | Acopla la latencia del cliente al camino caliente y no absorbe picos |
| Síncrono con Kafka (`send().get()`) | Un broker lento se convierte en un servicio caído |
| Sin rate limit | Un solo cliente con un GPS roto degrada a todos |

## Consecuencias

- El cliente debe asumir entrega al menos una vez y reintentar con backoff; el servidor tolera
  duplicados.
- Si Kafka está caído se pierden pings (no hay buffer persistente). Es aceptable porque el siguiente
  ping corrige la posición; un buffer en disco sería la mejora si se necesitara garantía.
- El lag del consumidor es la métrica clave a vigilar (S6: `kafka_consumer_lag`).
