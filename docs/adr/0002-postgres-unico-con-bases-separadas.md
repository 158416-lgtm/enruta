# ADR-0002 — Un PostgreSQL con una base por servicio (solo en local)

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S0

## Contexto

La arquitectura pide una base de datos por servicio (`authdb`, `shipmentdb`, `dispatchdb`,
`notifdb`). Levantar cuatro contenedores PostgreSQL en una laptop para una demo consume
~1,2 GB de RAM y no aporta nada visible en la exposición.

## Decisión

En el entorno local se ejecuta **un solo contenedor PostgreSQL 16** con **cuatro bases
independientes**, creadas por `infra/postgres/init/01-create-databases.sql`. Cada servicio
tiene su propio usuario lógico y su propio `search_path`; **ningún servicio consulta la base
de otro**. Las migraciones Flyway viven dentro de cada servicio.

En producción serían instancias (o al menos clusters) separados, sin cambios en el código:
solo cambia la variable `DB_URL`.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Cuatro contenedores PostgreSQL | +1,2 GB de RAM y 4 healthchecks, sin beneficio demostrable en local |
| Un esquema por servicio en la misma base | Rompe el aislamiento lógico y facilita joins accidentales entre dominios |
| H2 en memoria para tests | Oculta diferencias de SQL real (JSONB, índices parciales) |

## Consecuencias

- Los tests de integración usan Testcontainers con PostgreSQL real, no H2.
- El aislamiento entre servicios se garantiza por convención y por permisos, no por red.
- Documentado como deuda consciente: en producción, una instancia por servicio.
