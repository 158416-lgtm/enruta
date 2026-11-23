# ADR-0009 — Tracking sin base de datos ni outbox

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S3

## Contexto

`tracking-service` recibe posiciones GPS de alta frecuencia (cientos por segundo en producción).
Guardar cada ping en una base relacional sería caro e inútil: la posición de hace 100 ms no
interesa a nadie.

## Decisión

Tracking **no tiene base de datos**. Su estado vive en Redis y es reconstruible:

| Clave | Contenido | TTL |
|---|---|---|
| `driver:{id}:last` | última posición, timestamp | 10 min |
| `driver:{id}:shipment` | envío activo del conductor | 12 h |
| `shipment:{id}` | origen, destino, conductor | 12 h |
| `geo:{shipment}:{fence}:hits` | contador de histéresis | 2 min |
| `geo:{shipment}:{fence}:done` | geocerca ya confirmada | 6 h |

Tampoco usa el patrón **outbox**: no hay transacción de base que proteger. Los eventos se publican
directo a Kafka; si uno se pierde, el siguiente ping del mismo conductor vuelve a evaluar la
geocerca. La pérdida es auto-reparable por diseño del dominio.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `gps_points` en PostgreSQL | Millones de filas/día para un dato que caduca en segundos |
| TimescaleDB / InfluxDB | Correcto para series temporales, pero suma un motor más a la demo para un caso que Redis resuelve |
| Outbox en tracking | Requiere base transaccional; aportaría complejidad sin proteger nada |

## Consecuencias

- Reiniciar tracking **no pierde** lo importante: el historial del envío está en shipment y la
  última posición se reconstruye con el siguiente ping.
- La trazabilidad histórica de rutas no existe (no es un requisito de este producto).
- Si en el futuro se necesita historial, se añade un consumidor que escriba en un almacén de series
  sin tocar el camino caliente.
