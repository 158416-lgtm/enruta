# Cómo ser el cliente y cómo ser el repartidor (en el navegador)

Todo se hace en **la web**. No hace falta terminal, ni simulador, ni que nadie acepte por ti.

## Preparación (una vez por sesión)

```bash
cd /home/kevin/enruta
./scripts/start.sh          # todo arriba; el simulador queda APAGADO
./scripts/lab.sh preparar   # 6 repartidores libres y cerca del centro
```

> `./scripts/lab.sh preparar` cancela envíos de pruebas anteriores y deja a los 6 repartidores
> disponibles. Úsalo si algún día ves "no hay ofertas".

## Dos pestañas, dos papeles

Abre **http://localhost:5173** y verás el selector de rol.

### Pestaña 1 — CLIENTE

1. Pulsa **Entrar como cliente** (`cliente@enruta.test` ya está relleno).
2. Elige una ruta y pulsa el botón: se crea el envío y aparece en el mapa.
3. Verás `Buscando conductor` hasta que un repartidor acepte.

### Pestaña 2 — REPARTIDOR

Abre una **pestaña nueva** (`Ctrl + T`) y entra a la misma dirección.

1. En la tarjeta "Soy repartidor", elige quién eres (Ana, Beto, Carla, Diego, Elena o Fabio).
2. Pulsa **Entrar como repartidor**.

Cada pestaña guarda su propia sesión: puedes tener las dos abiertas a la vez, incluso en dos
monitores.

## La historia completa

| Paso | Dónde | Qué ocurre |
|---|---|---|
| 1 | Cliente | crea el envío → `CREATED` |
| 2 | Repartidor | en ≤ 3 s aparece **"Nueva oferta"** con la distancia y una **cuenta atrás de 90 s** |
| 3 | Repartidor | pulsa **Aceptar** (o Rechazar) → el envío pasa a `ASSIGNED` |
| 4 | Repartidor | pulsa **▶ Iniciar recorrido**: el marcador avanza por calles hasta el origen |
| 5 | Repartidor | al llegar, la app reporta 3 posiciones → **`PICKED_UP`** |
| 6 | Repartidor | sigue al destino → 3 posiciones → **`DELIVERED`** |
| 7 | Cliente | el mapa se movió en vivo y el timeline se completó |
| 8 | Cliente | el correo llegó a **http://localhost:8025** |

El repartidor **decide** aceptar; nada es automático. Si rechaza, el despacho ofrece al siguiente
candidato y la cuenta atrás vuelve a empezar.

## Repartidor desde el móvil (GPS real)

El mismo repartidor puede conducir con el GPS del teléfono, no con el botón.

### 1. Abrir el túnel (una vez por sesión)

```bash
curl -L -o /tmp/cloudflared \
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x /tmp/cloudflared
/tmp/cloudflared tunnel --url http://localhost:5173
```

Copia la URL `https://algo-random.trycloudflare.com` que imprime.

### 2. En el móvil del compañero

1. Abre esa URL (da igual que esté en otra red: puede usar datos móviles).
2. **Soy repartidor** → elige **Ana** → entrar.
3. Opcional: menú del navegador → **Añadir a pantalla de inicio**. Queda como una app.

### 3. Conducir con GPS

1. En el PC (cliente), crea el envío.
2. En el móvil aparece la oferta → **Aceptar**.
3. En "Tu envío" elige **GPS real** → **▶ Iniciar recorrido** → permite la ubicación.
4. Camina. En el mapa del PC el marcador se mueve en vivo.

### Por qué el túnel y no la IP local

Los navegadores solo entregan GPS en **contextos seguros** (HTTPS o `localhost`). Por
`http://192.168.x.x` el permiso de ubicación se deniega. El túnel da HTTPS sin desplegar nada.

### Si el GPS falla

- Bajo techo la precisión cae a cientos de metros: sal al aire libre.
- El modo **Simulado** hace el mismo recorrido con el botón, sin GPS.
- La pantalla se mantiene encendida mientras el GPS está activo (*wake lock*).

## Ver los personajes trabajar en vivo

En una terminal aparte:

```bash
./scripts/ver-servicios.sh
```

Deja esa ventana al lado de la app. Cada linea es un mensaje real de un servicio, pintado con su
color y traducido: envío creado, oferta enviada, asignación confirmada, geocerca, correo enviado.

Es la forma más directa de demostrar que hay **siete programas distintos** hablando entre sí.

## Lo que puedes mostrar en la exposición

- Dos pestañas lado a lado: a la izquierda el cliente, a la derecha el repartidor.
- La oferta con cuenta atrás: "mira, no se acepta sola; espera mi decisión".
- El marcador moviéndose en **las dos** pantallas a la vez (WebSocket).
- La transición de estados sin tocar nada: la provoca la geocerca.
- El correo en MailHog.

## Si algo no sale

| Lo que ves | Qué hacer |
|---|---|
| El repartidor no recibe oferta | `./scripts/lab.sh preparar` y vuelve a crear el envío |
| La oferta expiró | el despacho reintenta solo; espera unos segundos |
| "El envío aún no aparece asignado a ti" | espera 2 s y pulsa de nuevo Aceptar |
| El mapa está gris | sin internet no cargan las teselas; la ruta y los marcadores sí funcionan |

## Alternativa por terminal (para pruebas técnicas)

Si quieres ver las peticiones crudas, los mismos pasos existen como menús:

```bash
./scripts/cliente.py
./scripts/repartidor.py
```

Pero para explicar el proyecto, usa el navegador.
