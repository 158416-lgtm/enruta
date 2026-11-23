# ADR-0001 — Kafka como broker de eventos

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S0

## Contexto

El sistema necesita comunicar siete servicios con requisitos distintos: eventos de dominio
de bajo volumen (`shipment.events`), ingesta de GPS de alto volumen (`tracking.gps.raw`) y
comandos con reintentos (`notification.commands`). Además hay que demostrar orden por
agregado, replay para depuración y DLQ por consumidor.

## Decisión

Usar **Apache Kafka** en modo KRaft (sin ZooKeeper), una sola instancia en local, imagen
`apache/kafka` 4.2.2. Particiones y retención por tópico según el plan:

| Tópico | Particiones | Retención |
|---|---|---|
| `shipment.events` | 3 | 7 días |
| `dispatch.events` | 3 | 7 días |
| `tracking.gps.raw` | 6 | 1 hora |
| `tracking.events` | 3 | 7 días |
| `notification.commands` | 3 | 7 días |
| `*.dlq` | 1 | 14 días |

La clave de partición es siempre `aggregateId`, lo que garantiza orden por envío o por
conductor.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **RabbitMQ** | Colas excelentes, pero sin particiones ni replay; el orden por agregado y el reprocesamiento son parte del valor didáctico del proyecto |
| **Redpanda** | API compatible y menos RAM, pero menos familiar para quien evalúe el portafolio; queda como opción si la máquina va justa |
| **REST síncrono entre servicios** | Elimina el problema interesante (consistencia eventual, outbox, idempotencia) y acopla la disponibilidad |

## Consecuencias

- Consumo de RAM (~700 MB) que se compensa limitando el heap a 512 MB.
- Hay que gestionar idempotencia en cada consumidor (`processed_events`).
- Se gana: orden por agregado, replay, DLQ y desacople temporal entre servicios.
