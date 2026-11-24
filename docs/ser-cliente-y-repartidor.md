# Cómo ser el cliente y cómo ser el repartidor

EnRuta tiene dos "apps" reales:

| Papel | Dónde | Qué es |
|---|---|---|
| **Cliente** | navegador (`http://localhost:5173`) o `./scripts/cliente.py` | crea envíos, los sigue, los cancela |
| **Repartidor** | `./scripts/repartidor.py` | recibe ofertas, las acepta y reporta GPS |

La app del repartidor es de terminal porque **eso es exactamente lo que haría una app móvil**: los
mismos endpoints con su JWT. No hay atajos ni acceso a la base de datos.

## Preparación (una vez)

```bash
cd /home/kevin/enruta
./scripts/start.sh              # todo arriba
./scripts/lab.sh simoff         # apaga el simulador: los ofertas quedan para ti
./scripts/lab.sh reset          # limpia el estado del laboratorio
```

Abre el mapa en el navegador: **http://localhost:5173** (`cliente@enruta.test` / `Cliente1234!`).
Deja esa pestaña a la vista y trabaja en la terminal: verás el marcador moverse mientras tú
reportas posiciones.

---

## Terminal 1 — eres el CLIENTE

```bash
./scripts/cliente.py
```

```
┌──────────────────────────────────────────────┐
│            EnRuta · APP CLIENTE              │
├──────────────────────────────────────────────┤
│  1) Crear un envio                           │
│  2) Ver el estado de mi envio                │
│  3) Seguir en vivo (WebSocket)               │
│  4) Cancelar mi envio                        │
│  5) Ver mis envios recientes                 │
│  6) Abrir el mapa en el navegador            │
│  0) Salir                                    │
└──────────────────────────────────────────────┘
  Opcion:
```

Pulsa `1` y elige ruta (`1`, `2` o `3`):

```
  ✔ Envio creado: 9a410018-adbc-4fb3-bfe6-c74de645c422
    estado: CREATED   PIN de recogida: 0683
```

**Lo que acaba de pasar:** tu petición llegó al gateway con tu JWT, `shipment-service` guardó el
envío y publicó `shipment.created` en la misma transacción, y `dispatch` lo consumió y empezó a
buscar repartidor.

Deja la terminal del cliente abierta: puedes usar la opción `2` en cualquier momento para ver el
estado y el historial.

---

## Terminal 2 — eres el REPARTIDOR

Abre **otra** terminal (o una pestaña de terminal) y ejecuta:

```bash
./scripts/repartidor.py
```

Te pregunta qué repartidor eres:

```
  ¿Que repartidor eres? (1-6) [1]:
```

Elige cualquiera (por ejemplo `1` = Ana). Verás el menú:

```
  1) Ver mis ofertas
  2) Ver ofertas de toda la flota (a quien fue)
  3) Aceptar la oferta
  4) Rechazar la oferta
  5) Ping GPS: estoy en el ORIGEN
  6) Ping GPS: estoy en el DESTINO
  7) Ping GPS: me alejo 500 m
  8) Conducir automaticamente hasta el destino
  9) Ver el envio que llevo
  c) Cambiar de repartidor
  0) Salir
```

### Paso 1 — mira a quién le llegó

Pulsa `2`:

```
  OFERTAS PENDIENTES
      driver6 (Fabio)  oferta 941b671c-…  envio 9a410018  981 m  vence en 10s
```

El despacho eligió al más cercano con mejor calificación. Si no es tu repartidor, no pasa nada:
la app te lo dirá al aceptar.

### Paso 2 — acepta

Pulsa `3`:

```
  La oferta esta para driver6 (Fabio), no para ti.
  ¿Aceptar como driver6? (s/n) [s]:
```

Enter. La app cambia de sesión (JWT de Fabio) y acepta:

```
  ✔ Oferta aceptada. assignmentId: ec94d255-…
    El envio pasa a ASSIGNED en 1-3 s (lo veras en el mapa).
```

**Lo que acaba de pasar:** Redis tomó el lock de la oferta, PostgreSQL confirmó la transición y
se insertó la asignación con índice único. Si otro hubiera aceptado a la vez, recibiría
`409 OFFER_ALREADY_CLAIMED`.

### Paso 3 — recoge el paquete

Pulsa `5` **tres veces**:

```
  ✔ Ping enviado (en el origen)  →  estado del envio: ASSIGNED
  ✔ Ping enviado (en el origen)  →  estado del envio: ASSIGNED
  ✔ Ping enviado (en el origen)  →  estado del envio: PICKED_UP
```

Los dos primeros no cambian nada **a propósito**: la geocerca exige 3 pings consecutivos dentro
de 100 m para no dispararse por el ruido del GPS. El tercero confirma la recogida.

### Paso 4 — ponte en camino

Pulsa `6`... no: primero `7` (alejarse 500 m):

```
  ✔ Ping enviado (alejandome 500 m)  →  estado del envio: IN_TRANSIT
```

Al alejarte más de 150 m del origen, `tracking` publica `trip-started` y el envío pasa a
`IN_TRANSIT`.

### Paso 5 — entrega

Pulsa `6` tres veces:

```
  ✔ Ping enviado (en el destino)  →  estado del envio: IN_TRANSIT
  ✔ Ping enviado (en el destino)  →  estado del envio: IN_TRANSIT
  ✔ Ping enviado (en el destino)  →  estado del envio: DELIVERED
```

**Y ya está:** se disparó la geocerca de destino, el envío quedó `DELIVERED`, se publicó
`notification.requested` y salió el correo. Míralo en **http://localhost:8025**.

### Alternativa: conduce de verdad

Si no quieres ir ping a ping, la opción `8` calcula la ruta con OSRM y reporta posiciones por
calles hasta el destino (es lo que hace el simulador, pero para tu repartidor).

---

## Ver todo lo que ocurrió

```bash
./scripts/lab.sh estado                  # timeline, posición y ETA del último envío
./scripts/lab.sh eventos shipment.events # eventos publicados
```

O en la web: el envío aparece con su historial y, si lo abres, el mapa muestra la ruta recorrida.

## Si algo no sale

| Lo que ves | Qué pasa |
|---|---|
| "No tienes ofertas ahora mismo" | la oferta dura 15 s y ya expiró; usa `2` para ver a quién fue la siguiente |
| "Ese envio no esta asignado a ti" | no aceptaste la oferta de ese envío (o aún no se propagó: espera 2 s) |
| No cambia de estado tras 3 pings | ¿estás dentro de 100 m del origen / 150 m del destino? Usa las opciones 5 y 6, que mandan la coordenada exacta |
| El mapa no se mueve | abre la pestaña del mapa y refresca; el WebSocket reconecta solo |

## Por qué esto es "un proyecto real"

- **Dos clientes distintos**, el mismo servidor: un navegador y una terminal, ambos con JWT.
- **Ningún atajo**: cada botón del menú es una petición HTTP que podrías replicar con `curl`.
- **El estado no lo decide nadie a mano**: lo mueven eventos y geocercas.
- **Todo queda auditable**: historial en PostgreSQL, eventos en Kafka, correo en MailHog.
