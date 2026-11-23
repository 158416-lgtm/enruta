# ADR-0010 — WebSocket de una instancia y token en query string

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S3

## Contexto

El mapa necesita posiciones en vivo con latencia baja. Hay que decidir cómo autenticar la conexión
y cómo repartir los mensajes entre réplicas.

## Decisión

1. **Autenticación:** el JWT viaja como query param (`/ws/tracking?shipmentId=...&token=...`),
   porque el API `WebSocket` del navegador no permite cabeceras personalizadas. Se valida en el
   handshake con el mismo `JwtDecoder` del servicio; si es inválido se cierra con
   `POLICY_VIOLATION`.
2. **Suscripción por envío:** el registro vive en memoria del proceso
   (`Map<shipmentId, Set<WebSocketSession>>`).
3. **Una réplica** de `tracking-service` para el fan-out. Al conectar se envía un *snapshot* con la
   última posición conocida, para que el mapa no espere al siguiente ping.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Token en cabecera | El navegador no lo permite en `new WebSocket(...)` |
| Cookie de sesión | El sistema es stateless por JWT; además obliga a CORS con credenciales |
| Redis Pub/Sub entre réplicas | Es la evolución correcta (ver abajo), pero añade un salto y complejidad que la demo no necesita |
| SSE en lugar de WebSocket | Válido (solo servidor→cliente), pero WebSocket permite el `ping/pong` y el canal bidireccional del conductor |

## Consecuencias y límites (declarados)

- El token en la URL puede aparecer en logs del proxy. En producción se usaría un **ticket efímero
  de un solo uso** emitido por REST y canjeado en el handshake.
- Con varias réplicas, un cliente conectado a la réplica A no recibiría posiciones procesadas en la
  B. El plan de evolución es publicar el evento de posición en Redis Pub/Sub y que cada réplica
  entregue solo a sus suscriptores.
- El límite de conexiones por proceso se documenta en el README de operación (S6).
