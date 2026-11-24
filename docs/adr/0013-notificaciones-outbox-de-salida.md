# ADR-0013 — Notificaciones: la fila como outbox de salida

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S5

## Contexto

Enviar un correo es una operación con efectos externos, lenta y falible. Si se hace dentro del
consumidor de Kafka y SMTP falla, el mensaje se reentrega y el cliente puede recibir duplicados; si
se descarta, el usuario nunca se entera.

## Decisión

1. **Persistir antes de enviar.** Al recibir `notification.requested`, se crea una fila
   `notifications` en estado `PENDING` con `attempts=0` y `next_attempt_at=now()`.
2. **Intentar de inmediato** (camino feliz sin esperas) y, si falla, programar el reintento con
   backoff configurable (`2s, 10s, 30s`).
3. **Scheduler** cada segundo reserva las vencidas con
   `SELECT ... FOR UPDATE SKIP LOCKED` y reintenta.
4. **Agotados los intentos** (`max-attempts`, por defecto 3) la fila pasa a `FAILED` y se publica
   en `notification.commands.dlq` con destinatario, intentos y error.
5. **Deduplicación** por `eventId` en `processed_events`: una reentrega de Kafka no genera dos
   correos.

La tabla cumple el mismo papel que el outbox de escritura (ADR-0004), pero en sentido contrario:
garantiza que un hecho ya decidido (el envío se entregó) no se pierda si el canal externo falla.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Enviar dentro del listener y dejar que Kafka reintente | Los reintentos de Kafka bloquean la partición y pueden duplicar correos |
| Spring Retry en memoria | Un reinicio pierde los reintentos pendientes |
| Cola externa dedicada (SQS/Rabbit) | Otro sistema operativo más para el mismo resultado |
| Enviar sin persistir | Imposible auditar ni reintentar |

## Consecuencias

- El estado es auditable: `GET /api/v1/notifications?shipmentId=` muestra intentos y último error.
- Un SMTP lento no bloquea la entrega del envío: shipment solo publica un evento.
- Si SMTP se recupera antes de agotar los intentos, el reintento entrega sin intervención (se
  verificó parando y arrancando MailHog).
- El envío ocurre dentro de la transacción que reserva la fila: con varias réplicas, `SKIP LOCKED`
  evita duplicados, pero un fallo justo después del envío y antes del commit podría repetir el
  correo. Se acepta (at-least-once) y se documenta; la alternativa sería un estado `SENDING` con
  recuperación, que se pospone.
