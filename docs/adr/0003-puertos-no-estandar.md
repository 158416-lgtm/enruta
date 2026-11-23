# ADR-0003 — Puertos no estándar en el host

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S0

## Contexto

La máquina de desarrollo ya tiene servicios ocupando los puertos por defecto del plan:

| Puerto | Ocupado por |
|---|---|
| 8080 | `trading_robot` (proceso Python del usuario) |
| 5432 | PostgreSQL 14 del sistema (`systemd`) |

Publicar el gateway en 8080 o PostgreSQL en 5432 haría fallar `docker compose up` y, peor,
haría fallar la demo en vivo.

## Decisión

Cambiar **solo los puertos del host**, dejando intactos los puertos internos de los contenedores:

| Servicio | Puerto interno | Puerto en el host |
|---|---|---|
| `api-gateway` | 8080 | **18080** |
| `postgres` | 5432 | **5433** |
| `kafka` | 9092 | **29092** (listener EXTERNAL) |

Los contenedores siguen hablando entre sí por 8080/9092/5432, así que las variables `*_URI`
del compose no cambian. El frontend y los `curl` de la demo usan los puertos del host.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Detener PostgreSQL y `trading_robot` | Frágil: depende de que nadie los reinicie, y la demo no debe tocar procesos ajenos al proyecto |
| Mover el gateway al 8081 y reasignar todo | Rompe la numeración legible 808x de los servicios |
| Solo documentar el conflicto | La demo falla si se olvida; mejor que no pueda fallar |

## Consecuencias

- `JWT_ISSUER` pasa a `http://localhost:18080` (el `iss` del token debe coincidir).
- `.env.example`, `preflight.sh`, `demo.sh` y el README usan los puertos del host.
- En un entorno limpio basta cambiar los mapeos de `ports` en el compose.
