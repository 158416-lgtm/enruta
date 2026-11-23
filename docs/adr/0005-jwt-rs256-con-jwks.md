# ADR-0005 — JWT RS256 propio con JWKS

- **Estado:** aceptado
- **Fecha:** 2026-11-23
- **Contexto:** S1

## Contexto

Siete servicios necesitan autenticar al usuario. Las opciones habituales son un proveedor externo
(Keycloak), un secreto compartido (HS256) o llaves asimétricas propias con JWKS.

## Decisión

- `auth-service` firma **RS256** y expone `GET /api/v1/auth/jwks`.
- El par RSA se genera la primera vez y se persiste en `${enruta.auth.jwk-path}`; los reinicios no
  invalidan tokens.
- El gateway y los servicios validan con `spring-boot-starter-oauth2-resource-server` apuntando a
  ese JWKS. **Ningún servicio comparte el secreto de firma** y ninguno llama a auth por petición.
- Claims: `sub` (id de usuario), `roles` (`ADMIN|CLIENT|DRIVER`), `email`, `name`, `jti`.
- Access token 15 min; refresh opaco de 32 bytes, guardado **hasheado** (SHA-256) y con rotación:
  cada refresh revoca el anterior.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Keycloak | +700 MB de RAM y deja `auth-service` sin código que mostrar; anotado como sustitución posible |
| HS256 con secreto compartido | Un servicio comprometido puede firmar tokens de cualquier usuario |
| Sesiones con cookie en cada servicio | Rompe el estatismo y complica el escalado horizontal |

## Consecuencias

- Revocar un access token antes de su expiración no es posible sin lista negra; se mitiga con TTL
  de 15 min y revocación inmediata del refresh.
- El endpoint JWKS es público por diseño (solo contiene la llave pública).
- Los tests de auth verifican firma, emisor, `kid` y que el JWKS no expone `d`.
