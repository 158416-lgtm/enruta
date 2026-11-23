# ADR-0006 — Idempotencia con reclamación atómica

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S1

## Contexto

`POST /api/v1/shipments` no es idempotente por naturaleza: un reintento del cliente (timeout,
red móvil, doble clic) no puede crear dos envíos. Tampoco basta con guardar la respuesta al
final: dos peticiones simultáneas con la misma clave pueden ejecutar el caso de uso a la vez.

## Decisión

1. El cliente envía `Idempotency-Key` (obligatorio para crear).
2. Antes de ejecutar nada se **reclama** la clave con
   `INSERT ... ON CONFLICT (key) DO NOTHING`. Solo una petición inserta.
3. Si la clave existe:
   - hash del cuerpo distinto ⇒ **409 `IDEMPOTENCY_KEY_REUSED`**,
   - respuesta aún no escrita ⇒ **409 `IDEMPOTENCY_IN_PROGRESS`**,
   - respuesta completa ⇒ se **reproduce** el status y el cuerpo guardados.
4. Si el caso de uso falla, se libera la clave (`DELETE`) para permitir el reintento.

El hash es SHA-256 del cuerpo serializado; la respuesta se guarda como JSONB con TTL de 24 h.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `save()` de JPA para insertar la clave | Su semántica de *merge* convierte el duplicado en un `UPDATE` silencioso: dos envíos creados (bug detectado por el test de integración) |
| Comprobar y luego insertar | Ventana de carrera entre `SELECT` e `INSERT` |
| Delegar en el cliente | Un reintento automático del gateway o del balanceador lo rompe igual |

## Consecuencias

- Requiere una tabla por servicio que acepte escrituras idempotentes (`idempotency_keys`).
- El almacenamiento crece; se limpia por `expires_at`.
- Un cliente que reutilice la clave con otro cuerpo recibe un error explícito, no un resultado
  incoherente.
