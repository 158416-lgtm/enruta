# Pruebas de carga y estrés

Herramienta: ApacheBench (`ab`). Fecha: 2026-11-25T09:21:26-05:00.
Servidor: Linux 6.8.0-151-generic x86_64, 12 núcleos. Servicios en localhost (7 JVM + Kafka + PostgreSQL + Redis).
Umbral exigido: **p95 ≤ 500 ms**.

| Endpoint | Peticiones | Concurrencia | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Errores de red | No-2xx |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ETA | 2000 | 20 | 2955.27 | 6 | 10 | 12 | 0 | 0 |
| Ruta con geometria | 2000 | 20 | 2792.60 | 7 | 11 | 14 | 0 | 0 |
| Distancia OSRM | 2000 | 20 | 2953.67 | 6 | 12 | 15 | 0 | 0 |
| Listado de envios | 2000 | 20 | 1081.53 | 18 | 29 | 37 | 0 | 0 |
| Historial de envio | 2000 | 20 | 1297.99 | 14 | 25 | 32 | 0 | 0 |
| Estado de rastreo | 2000 | 20 | 1670.56 | 11 | 18 | 20 | 0 | 0 |
| Estadisticas de notificaciones | 2000 | 20 | 1311.50 | 15 | 22 | 26 | 0 | 0 |

> `ab` abre una conexión TCP por petición (sin `-k`), usa el mismo JWT y no incluye el tiempo de login.
> La columna Errores de red suma Connect+Receive+Exceptions (0 = sin fallos). Las diferencias
> de longitud que reporta `ab` no son errores: vienen de `cached:true/false` o del plan B.
> Los endpoints de escritura se excluyen a propósito: crean envíos y ensucian la base.

**Resultado: OK.** Todos los endpoints quedaron por debajo de 500 ms en el percentil 95, sin errores de red ni respuestas no-2xx.

## Estrés

Se sube la concurrencia hasta encontrar el punto de quiebre. Mismos endpoints, mismas condiciones:

| Endpoint | Concurrencia | Peticiones | RPS | p95 (ms) | Errores de red | No-2xx |
|---|---:|---:|---:|---:|---:|---:|
| ETA | 50 | 2000 | 2114.33 | 37 | 0 | 0 |
| Listado | 50 | 2000 | 1124.18 | 71 | 0 | 0 |
| NotifStats | 50 | 2000 | 733.79 | 121 | 0 | 0 |
| ETA | 100 | 2000 | 2232.12 | 73 | 0 | 0 |
| Listado | 100 | 2000 | 1279.41 | 157 | 0 | 0 |
| NotifStats | 100 | 2000 | 999.28 | 194 | 0 | 0 |
| ETA | 200 | 2000 | 2135.37 | 152 | 0 | 0 |
| Listado | 200 | 2000 | 1194.52 | 397 | 0 | 0 |
| NotifStats | 200 | 2000 | 1112.62 | 389 | 0 | 0 |
| ETA | 400 | 1000 | 3057.98 | 196 | 0 | 0 |
| Listado | 400 | 1000 | 1351.67 | 504 | 0 | 0 |
| NotifStats | 400 | 1000 | 877.04 | 657 | 0 | 0 |

Lectura: con 20 concurrentes el p95 se mantiene por debajo de 50 ms; hasta 200 concurrentes
sigue por debajo de 400 ms. El punto de quiebre aparece hacia **400 concurrentes** (p95 de 504 ms
en el listado y 657 ms en las estadísticas de notificaciones), donde además el *pool* de conexiones
a PostgreSQL y el número de núcleos de la máquina de prueba se vuelven el límite. No hubo errores
de red ni respuestas no-2xx en ningún nivel.

Reproducir:

```bash
for c in 50 100 200 400; do ./load/ab.sh 2000 $c; done
```
