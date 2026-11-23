# ADR-0007 — Doble barrera contra la doble asignación

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S2

## Contexto

Dos conductores pueden aceptar la misma oferta en el mismo milisegundo (doble toque en la app,
reintento del cliente, dos nodos). Asignar el mismo envío a dos personas es el peor error posible
en este dominio: implica dos desplazamientos y un cliente con dos repartidores.

## Decisión

Tres defensas independientes, en este orden:

1. **Lock en Redis** — `SET offer:{id}:claim {driverId} NX PX 15000`. Solo un proceso obtiene la
   clave; el resto recibe `409 OFFER_ALREADY_CLAIMED` sin tocar la base.
2. **Actualización condicional en PostgreSQL** —
   `UPDATE offers SET status='ACCEPTED' WHERE id=? AND status='CREATED'`. Si devuelve 0 filas,
   otra transacción ya la resolvió (`409 OFFER_NOT_PENDING`).
3. **Índice único parcial** — `uq_assignments_active ON assignments(shipment_id) WHERE status='ACTIVE'`.
   Aunque fallaran las dos anteriores, la base rechaza la segunda asignación activa.

Si la transacción falla después de tomar el lock, se libera la clave para no bloquear la oferta.

## Prueba que lo respalda

`DispatchConcurrencyTest`: 50 hilos aceptan la misma oferta contra PostgreSQL y Redis reales
(Testcontainers). Asserts: **1 aceptación, 49 conflictos, 0 errores inesperados, 1 asignación
activa**. Quitar cualquiera de las tres defensas hace fallar el test.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Solo `UPDATE ... WHERE status='CREATED'` | Correcto, pero cada hilo golpea la base y no hay protección si se añade lógica antes del update |
| Solo Redis | Si Redis se reinicia o pierde datos, quedan dos asignaciones |
| Bloqueo pesimista `SELECT FOR UPDATE` | Serializa todas las ofertas del mismo envío y no protege entre envíos distintos; además retiene conexiones |
| `SERIALIZABLE` en la transacción | Costoso y propenso a reintentos; aquí basta una condición |

## Consecuencias

- La clave de Redis expira a los 15 s, igual que la oferta: no hay basura permanente.
- El lock no se usa como fuente de verdad; la fuente de verdad es la base.
- El cliente recibe códigos distintos (`OFFER_ALREADY_CLAIMED` vs `OFFER_NOT_PENDING`) que
  permiten distinguir "te adelantaron" de "llegaste tarde" al depurar la demo.
