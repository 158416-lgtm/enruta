# ADR-0004 — Outbox con relay por polling y ShedLock

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S1

## Contexto

Al crear o cambiar el estado de un envío hay que publicar un evento en Kafka **sin perder
consistencia**: no puede existir un envío creado sin evento, ni un evento de un envío que no se
guardó. Publicar dentro del `@Transactional` no sirve: Kafka no participa de la transacción.

## Decisión

Patrón **transactional outbox**:

1. El servicio escribe el envío, su historial y una fila en `outbox` en la **misma transacción**.
2. Un `OutboxRelay` cada 500 ms reserva hasta 100 filas con
   `SELECT ... FOR UPDATE SKIP LOCKED`.
3. Publica con `KafkaTemplate` (`acks=all`, `enable.idempotence=true`) y **espera el ack**.
4. Solo entonces marca `published_at`. Si falla, incrementa `attempts` y reintenta en el
   siguiente tick.

`ShedLock` (`@SchedulerLock`) impide que dos réplicas publiquen a la vez, y `SKIP LOCKED`
permite paralelismo seguro aunque el lock fallen.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Publicar dentro de la transacción | Kafka no es transaccional con PostgreSQL: o se pierde el evento o se publica uno fantasma |
| Kafka Transactions (EOS) | Requiere leer-escribir-procesar en el mismo cluster y complica el modelo; útil en S3+ para consumidores, no para el primer salto |
| CDC con Debezium | Correcto y elegante, pero suma un conector y un contenedor más; se documenta como evolución |
| Publicar y marcar sin esperar ack | Marca como enviado un evento que puede no haber llegado |

## Consecuencias

- Latencia extra de hasta 500 ms entre commit y evento visible: irrelevante para este dominio.
- La tabla `outbox` crece; en producción se purga por `published_at < now() - interval '7 days'`.
- Tras 10 intentos el relay deja de reintentar y lo reporta como error para intervención manual.
