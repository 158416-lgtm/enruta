# ADR-0008 — Saga coreografiada entre shipment y dispatch

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S2

## Contexto

El envío vive en `shipment-service` y el proceso de asignación en `dispatch-service`. Ninguno debe
tocar la tabla del otro: si dispatch escribiera `shipments.status`, el estado tendría dos dueños y
aparecerían actualizaciones contradictorias.

## Decisión

**Coreografía** sobre eventos de Kafka, con cada servicio dueño exclusivo de su dato:

```
shipment.created ──▶ dispatch: busca candidatos, emite oferta
dispatch.offer-created ──▶ (simulador/conductor acepta)
dispatch.assigned ──▶ shipment: CREATED → ASSIGNED
dispatch.assignment-cancelled ──▶ shipment: ASSIGNED → CREATED (compensación)
shipment.unassigned ──▶ dispatch: reinicia la búsqueda
dispatch.failed ──▶ shipment: CREATED → NO_DRIVER
```

Reglas que hacen la coreografía segura:

1. Todo evento se publica por **outbox** (ADR-0004), nunca dentro del `@Transactional` de Kafka.
2. `partition key = shipmentId`, así el orden por envío está garantizado y no hay
   "cancelado antes de creado".
3. Cada consumidor deduplica por `eventId` en `processed_events`.
4. Las transiciones son **idempotentes**: si `dispatch.assigned` llega dos veces, la segunda no
   hace nada (`onDispatchAssigned` comprueba estado y conductor).
5. Los eventos tardíos sobre un envío terminal se ignoran y se registran (no se resucita un envío
   entregado).

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Orquestador central (dispatch manda comandos) | Un servicio más con toda la lógica y acoplamiento temporal; peor para el portafolio y para la disponibilidad |
| Llamada REST de dispatch a shipment | Acopla disponibilidad: si shipment está caído, se pierde la asignación |
| Tabla compartida | Rompe el aislamiento por servicio (ADR-0002) y elimina la necesidad de eventos |

## Consecuencias

- Consistencia eventual: entre el commit y la asignación pasan ~1 s (outbox + relay).
- La compensación es explícita y testeable: cancelar la asignación devuelve el envío a `CREATED` y
  dispatch lo vuelve a ofertar.
- Depurar requiere seguir `shipmentId` en los logs; en S6 se añade traza distribuida.
