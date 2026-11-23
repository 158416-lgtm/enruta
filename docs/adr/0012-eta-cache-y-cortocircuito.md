# ADR-0012 — ETA con caché explícita, cortocircuito y plan B

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S4

## Contexto

El ETA se consulta muchas veces para el mismo tramo (mapa, cliente, operador) y depende de OSRM,
un contenedor que puede tardar o caerse. Sin protección, un OSRM lento convierte al servicio de
rutas en un cuello de botella y arrastra a quien lo consulta.

## Decisión

1. **Caché explícita en Redis con TTL de 20 s**, clave `eta:{lat1,lng1}:{lat2,lng2}` redondeada a
   4 decimales (~11 m). Se implementa a mano (no `@Cacheable`) para poder devolver el flag
   `cached` y medir aciertos/fallos.
2. **Circuit breaker** de Resilience4j (`osrm`): ventana de 10, mínimo 5 llamadas, umbral 50%,
   10 s abierto. Con el circuito abierto se responde en ~40 ms en lugar de esperar 2 s.
3. **Fallback determinista**: haversine × 1.3 (factor de rodeo) a 25 km/h. La respuesta lleva
   `source: FALLBACK` para que nadie confunda una estimación con una ruta real.
4. **Timeout** de 2 s en el cliente HTTP y `timeLimiter` de 3 s.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Solo caché, sin circuit breaker | Ante OSRM caído cada entrada nueva espera 2 s |
| Solo circuit breaker | El 90% de las consultas repetidas seguiría yendo a OSRM |
| Cachear en memoria (Caffeine) | No se comparte entre réplicas; Redis ya está en el stack |
| Devolver error si OSRM falla | El mapa se quedaría sin ETA por un servicio auxiliar |

## Consecuencias

- Un ETA puede tener hasta 20 s de antigüedad para el mismo tramo: aceptable para una estimación
  de minutos, y se ve en el flag `cached`.
- El fallback **nunca** coincide exactamente con OSRM: es una estimación honesta, no una mentira
  disfrazada.
- `tracking-service` consume el ETA propagando el JWT del usuario (2 s de timeout, `null` si
  falla): el endpoint interno no queda abierto sin autenticación. La evolución natural es
  client-credentials entre servicios.
