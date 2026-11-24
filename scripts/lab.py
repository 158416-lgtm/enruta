#!/usr/bin/env python3
"""
Laboratorio EnRuta: opera el sistema como lo harian un cliente y un repartidor reales.

Todo va contra la API publica (gateway) o contra servicios concretos, con JWT reales.
No hay atajos ni acceso a la base de datos: si algo se ve aqui, se puede demostrar.

    ./scripts/lab.sh login cliente
    ./scripts/lab.sh crear
    ./scripts/lab.sh ofertas
    ./scripts/lab.sh aceptar
    ./scripts/lab.sh conducir 3
    ./scripts/lab.sh estado
"""
import json
import os
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATE_DIR = os.path.join(ROOT, "logs", "lab")
STATE_FILE = os.path.join(STATE_DIR, "state.json")

GATEWAY = os.environ.get("ENRUTA_GATEWAY", "http://localhost:18080")
TRACKING = os.environ.get("ENRUTA_TRACKING", "http://localhost:8084")
OSRM = os.environ.get("ENRUTA_OSRM", "http://localhost:5000")

DRIVERS = [
    "11111111-1111-1111-1111-111111111101",
    "11111111-1111-1111-1111-111111111102",
    "11111111-1111-1111-1111-111111111103",
    "11111111-1111-1111-1111-111111111104",
    "11111111-1111-1111-1111-111111111105",
    "11111111-1111-1111-1111-111111111106",
]
DRIVER_NAMES = {
    1: "Ana",
    2: "Beto",
    3: "Carla",
    4: "Diego",
    5: "Elena",
    6: "Fabio",
}
PASSWORD = "Driver1234!"

PRESETS = {
    "parque": {
        "pickup": {"lat": 4.3449, "lng": -74.3674, "address": "Parque Principal, Fusagasuga"},
        "dropoff": {"lat": 4.3374, "lng": -74.3600, "address": "Terminal de Transportes"},
    },
    "balmoral": {
        "pickup": {"lat": 4.3480, "lng": -74.3700, "address": "Balmoral"},
        "dropoff": {"lat": 4.3350, "lng": -74.3650, "address": "Unisur"},
    },
    "hospital": {
        "pickup": {"lat": 4.3415, "lng": -74.3655, "address": "Centro"},
        "dropoff": {"lat": 4.3468, "lng": -74.3712, "address": "Hospital San Rafael"},
    },
}


# ---------------------------------------------------------------- utilidades
def http(method, url, body=None, token=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            raw = response.read().decode()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        try:
            payload = json.loads(raw)
        except Exception:
            payload = {"raw": raw}
        return error.code, payload
    except urllib.error.URLError as error:
        die(f"No se pudo conectar con {url}. ¿Esta todo arriba? ({error.reason})")


def die(message):
    print(f"\n  ERROR: {message}\n")
    sys.exit(1)


def ok(message):
    print(f"  {message}")


def c(value, code):
    return f"\033[{code}m{value}\033[0m"


def state_load():
    try:
        with open(STATE_FILE) as handle:
            return json.load(handle)
    except Exception:
        return {}


def state_save(state):
    os.makedirs(STATE_DIR, exist_ok=True)
    with open(STATE_FILE, "w") as handle:
        json.dump(state, handle, indent=2)


def login(who):
    if who == "cliente":
        email, password = "cliente@enruta.test", "Cliente1234!"
    elif who == "admin":
        email, password = "admin@enruta.test", "Admin1234!"
    elif who.startswith("driver"):
        number = who.replace("driver", "")
        email, password = f"driver{number}@enruta.test", PASSWORD
    else:
        die("Usa: cliente, admin o driver1..driver6")

    status, payload = http(
        "POST", f"{GATEWAY}/api/v1/auth/login", {"email": email, "password": password}
    )
    if status != 200:
        die(f"login fallo para {email}: {payload}")
    os.makedirs(STATE_DIR, exist_ok=True)
    with open(os.path.join(STATE_DIR, f"token-{who}.txt"), "w") as handle:
        handle.write(payload["accessToken"])
    return payload["accessToken"]


def driver_id(number):
    return DRIVERS[int(number) - 1]


# ---------------------------------------------------------------- comandos
def cmd_login(args):
    who = args[0] if args else "cliente"
    token = login(who)
    print(f"\n  {c('Login real', '1;32')} como {c(who, '1')}")
    ok(f"JWT (primeros 40): {token[:40]}…")
    ok(f"grupos/roles: se decodifican en el servidor a partir del claim 'roles'")
    ok(f"guardado en logs/lab/token-{who}.txt")


def cmd_crear(args):
    preset_name = args[0] if args else "parque"
    if preset_name not in PRESETS:
        die(f"presets disponibles: {', '.join(PRESETS)}")
    token = login("cliente")
    idem = str(uuid.uuid4())
    status, payload = http(
        "POST",
        f"{GATEWAY}/api/v1/shipments",
        PRESETS[preset_name],
        token,
        {"Idempotency-Key": idem},
    )
    if status != 201:
        die(f"no se pudo crear: {payload}")
    state = state_load()
    state["shipmentId"] = payload["id"]
    state["idempotencyKey"] = idem
    state_save(state)
    print(f"\n  {c('ENVIO CREADO', '1;32')} (preset: {preset_name})")
    ok(f"id: {c(payload['id'], '1')}")
    ok(f"estado: {payload['status']}  ·  PIN de recogida: {payload['pickupPin']}")
    ok("mira el mapa: en 2-5 s aparece la asignacion (si el simulador esta activo)")


def cmd_estado(args):
    state = state_load()
    shipment_id = args[0] if args else state.get("shipmentId")
    if not shipment_id:
        die("no hay envio en curso. Ejecuta: ./scripts/lab.sh crear")

    token = login("cliente")
    status, shipment = http("GET", f"{GATEWAY}/api/v1/shipments/{shipment_id}", token=token)
    if status != 200:
        die(f"no se pudo leer el envio: {shipment}")

    print(f"\n  {c('ENVIO ' + shipment_id[:8], '1')}  ·  {c(shipment['status'], '1;36')}")
    ok(f"conductor: {shipment.get('driverId') or '—'}")
    for step in shipment.get("history", []):
        origin = step["from"] or "inicio"
        print(f"      {origin:12} → {step['to']:12} {step['occurredAt'][11:19]}")

    status, tracking = http(
        "GET", f"{TRACKING}/api/v1/tracking/shipments/{shipment_id}", token=token
    )
    if status == 200 and tracking.get("lastPosition"):
        position = tracking["lastPosition"]
        print(
            f"      posicion {float(position['lat']):.5f},{float(position['lng']):.5f}"
            f"  ·  {tracking.get('distanceToDropoffMeters', '?')} m al destino"
            f"  ·  ETA {tracking.get('etaSeconds', '?')} s"
        )


def cmd_ofertas(_args):
    print(f"\n  {c('OFERTAS PENDIENTES', '1')}")
    found = False
    for number in range(1, len(DRIVERS) + 1):
        token = login(f"driver{number}")
        status, offers = http(
            "GET", f"{GATEWAY}/api/v1/drivers/{driver_id(number)}/offers", token=token
        )
        if status != 200:
            continue
        for offer in offers or []:
            found = True
            seconds = max(0, _seconds_left(offer["expiresAt"]))
            print(
                f"      driver{number} ({DRIVER_NAMES[number]:5})  "
                f"oferta {offer['id']}  envio {offer['shipmentId'][:8]}  "
                f"{offer['distanceMeters']} m  vence en {seconds:.0f}s"
            )
    if not found:
        ok("ninguna oferta ahora mismo (espera a crear un envio, o ya expiraron)")


def _seconds_left(expires_at):
    # "2026-11-24T12:00:00Z" -> segundos restantes
    from datetime import datetime, timezone

    clean = expires_at.replace("Z", "+00:00")
    try:
        when = datetime.fromisoformat(clean)
    except ValueError:
        return 0
    return (when - datetime.now(timezone.utc)).total_seconds()


def _find_offer(offer_id=None):
    for number in range(1, len(DRIVERS) + 1):
        token = login(f"driver{number}")
        status, offers = http(
            "GET", f"{GATEWAY}/api/v1/drivers/{driver_id(number)}/offers", token=token
        )
        if status != 200:
            continue
        for offer in offers or []:
            if offer_id is None or offer["id"].startswith(offer_id):
                return number, token, offer
    return None, None, None


def cmd_aceptar(args):
    number, token, offer = _find_offer(args[0] if args else None)
    if not offer:
        die("no encontre esa oferta (¿expiro a los 15 s? vuelve a mirar con 'ofertas')")

    status, payload = http(
        "POST", f"{GATEWAY}/api/v1/offers/{offer['id']}/accept", token=token
    )
    if status != 200:
        die(f"rechazada: {payload}")
    state = state_load()
    state["driver"] = number
    state_save(state)
    print(f"\n  {c('OFERTA ACEPTADA', '1;32')} por driver{number} ({DRIVER_NAMES[number]})")
    ok(f"assignmentId: {payload['assignmentId']}")
    ok(f"envio {payload['shipmentId'][:8]} → ASSIGNED en 1-3 s (evento por Kafka)")
    ok("ahora conduce:  ./scripts/lab.sh conducir " + str(number))


def cmd_rechazar(args):
    number, token, offer = _find_offer(args[0] if args else None)
    if not offer:
        die("no hay oferta que rechazar")
    status, _ = http("POST", f"{GATEWAY}/api/v1/offers/{offer['id']}/reject", token=token)
    if status != 204:
        die("no se pudo rechazar")
    print(f"\n  Oferta rechazada por driver{number}. El despacho pasa al siguiente candidato.")


def cmd_mover(args):
    if len(args) != 3:
        die("uso: mover <driverN> <lat> <lng>")
    number, lat, lng = args[0], float(args[1]), float(args[2])
    token = login(f"driver{number}")
    status, payload = http(
        "POST",
        f"{TRACKING}/api/v1/tracking/drivers/{driver_id(number)}/location",
        {"points": [{"lat": lat, "lng": lng}]},
        token,
    )
    if status != 202:
        die(f"no se pudo reportar: {payload}")
    print(f"  posicion aceptada (202) driver{number} → {lat:.5f},{lng:.5f}")


def cmd_conducir(args):
    state = state_load()
    if args:
        number = int(args[0])
    elif state.get("driver"):
        number = int(state["driver"])
    else:
        die("uso: conducir <driverN>  (o acepta una oferta antes)")
    token = login(f"driver{number}")
    shipment_id = state.get("shipmentId")
    if not shipment_id:
        die("no hay envio en curso. Crea uno con: ./scripts/lab.sh crear")

    # La asignacion es asincrona: esperamos a que shipment refleje al conductor.
    shipment = None
    for _ in range(20):
        status, payload = http("GET", f"{GATEWAY}/api/v1/shipments/{shipment_id}", token=token)
        if status == 200 and payload.get("driverId"):
            shipment = payload
            break
        time.sleep(1)
    if not shipment:
        die("el envio aun no esta asignado a este conductor (acepta la oferta primero)")

    pickup = shipment["pickup"]
    dropoff = shipment["dropoff"]
    start = _last_position(shipment_id) or {"lat": pickup["lat"], "lng": pickup["lng"]}

    def route(a, b):
        url = (
            f"{OSRM}/route/v1/driving/{a['lng']},{a['lat']};{b['lng']},{b['lat']}"
            "?overview=full&geometries=geojson"
        )
        status, payload = http("GET", url)
        if status != 200 or payload.get("code") != "Ok":
            die("OSRM no respondio; ¿esta arriba el contenedor?")
        return [(point[1], point[0]) for point in payload["routes"][0]["geometry"]["coordinates"]]

    print(f"\n  {c('CONDUCIENDO', '1;36')} como driver{number} ({DRIVER_NAMES[number]})")
    ok(f"origen: {pickup['address']}  ·  destino: {dropoff['address']}")

    driver_uuid = driver_id(number)
    _drive(route(start, pickup), "origen", shipment_id, token, driver_uuid)
    print("      · 3 pings dentro de 100 m → geocerca de origen")
    _dwell(shipment_id, token, driver_uuid, 3)

    position = _last_position(shipment_id) or pickup
    _drive(route(position, dropoff), "destino", shipment_id, token, driver_uuid)
    print("      · 3 pings dentro de 150 m → geocerca de destino")
    _dwell(shipment_id, token, driver_uuid, 4)

    cmd_estado([shipment_id])


def _drive(points, pickup_label, shipment_id, token, driver_uuid):
    step = max(1, len(points) // 20)
    for index in range(0, len(points), step):
        lat, lng = points[index]
        status, _ = http(
            "POST",
            f"{TRACKING}/api/v1/tracking/drivers/{driver_uuid}/location",
            {"points": [{"lat": lat, "lng": lng}]},
            token,
        )
        marker = "·" if status == 202 else "x"
        sys.stdout.write(f"\r      {marker} avanzando hacia el {pickup_label}  {index:3}/{len(points)}")
        sys.stdout.flush()
        time.sleep(0.8)
    print()


def _dwell(shipment_id, token, driver_uuid, times):
    for _ in range(times):
        position = _last_position(shipment_id)
        if not position:
            return
        http(
            "POST",
            f"{TRACKING}/api/v1/tracking/drivers/{driver_uuid}/location",
            {"points": [{"lat": position["lat"], "lng": position["lng"]}]},
            token,
        )
        time.sleep(0.8)


def _last_position(shipment_id):
    token = login("cliente")
    status, payload = http(
        "GET", f"{TRACKING}/api/v1/tracking/shipments/{shipment_id}", token=token
    )
    if status != 200:
        return None
    position = payload.get("lastPosition")
    if not position:
        return None
    return {"lat": float(position["lat"]), "lng": float(position["lng"])}


def cmd_ws(args):
    state = state_load()
    shipment_id = args[0] if args else state.get("shipmentId")
    if not shipment_id:
        die("no hay envio en curso")
    token = login("cliente")
    script = os.path.join(ROOT, "scripts", "ws-watch.mjs")
    print("  Ctrl+C para salir\n")
    subprocess.run(["node", script, shipment_id, token])


def cmd_eventos(args):
    topic = args[0] if args else "shipment.events"
    print(f"\n  Ultimos eventos de {c(topic, '1')}\n")
    command = (
        f"docker compose -f {ROOT}/infra/docker-compose.yml exec -T kafka "
        f"/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 "
        f"--topic {topic} --from-beginning --max-messages 5 --timeout-ms 4000"
    )
    result = subprocess.run(
        ["sg", "docker", "-c", command], capture_output=True, text=True, timeout=20
    )
    for line in result.stdout.splitlines():
        try:
            event = json.loads(line)
            print(f"      {event['eventType']:32} {event['occurredAt'][11:19]}")
        except Exception:
            continue


def cmd_simoff(_args):
    subprocess.run(["fuser", "-k", "8090/tcp"], capture_output=True)
    print("\n  Simulador detenido. Ahora los ofertas quedan esperando a que TU las aceptes.\n")


def cmd_simon(_args):
    env = os.environ.copy()
    env.update(
        {
            "AUTH_URL": "http://localhost:18081",
            "DISPATCH_URL": "http://localhost:18083",
            "SHIPMENT_URL": "http://localhost:18082",
            "TRACKING_URL": "http://localhost:8084",
            "OSRM_BASE_URL": "http://localhost:5000",
            "SERVER_PORT": "8090",
        }
    )
    subprocess.Popen(
        [
            "java",
            "-jar",
            os.path.join(ROOT, "tools/driver-simulator/target/driver-simulator-0.1.0-SNAPSHOT.jar"),
        ],
        cwd=ROOT,
        env=env,
        stdout=open(os.path.join(ROOT, "logs/simulator.log"), "a"),
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    print("\n  Simulador arrancado (6 conductores).\n")


def cmd_preparar(_args):
    """Deja el tablero listo: cancela envios abiertos y ubica a los 6 repartidores en el centro."""
    admin = login("admin")
    status, page = http(
        "GET", f"{GATEWAY}/api/v1/shipments?page=0&size=50", token=admin
    )
    if status != 200:
        die(f"no se pudieron listar los envios: {page}")

    abiertos = [
        s
        for s in page["content"]
        if s["status"] not in ("DELIVERED", "CANCELLED", "FAILED", "NO_DRIVER")
    ]
    print(f"\n  Cancelando {len(abiertos)} envio(s) abiertos de pruebas anteriores…")
    for shipment in abiertos:
        code, payload = http(
            "POST", f"{GATEWAY}/api/v1/shipments/{shipment['id']}/cancel", token=admin
        )
        marca = "ok" if code == 200 else f"{code}"
        print(f"    {shipment['id'][:8]}  {shipment['status']:10} → {marca}")

    time.sleep(2)

    # Red de seguridad: asignaciones huerfanas de pruebas viejas (no hay endpoint para listarlas).
    print("\n  Limpiando asignaciones huerfanas…")
    subprocess.run(
        [
            "sg",
            "docker",
            "-c",
            f"docker compose -f {ROOT}/infra/docker-compose.yml exec -T postgres "
            "psql -U enruta -d dispatchdb -c "
            "\"update assignments set status='CANCELLED', ended_at=now() where status='ACTIVE'; "
            'update drivers set status=\'AVAILABLE\' where status=\'BUSY\';"',
        ],
        capture_output=True,
        text=True,
    )

    # Cada repartidor "abre su app" y reporta una posicion cerca del centro.
    centro = (4.3449, -74.3674)
    print("\n  Ubicando a los 6 repartidores alrededor del centro…")
    for number in range(1, len(DRIVERS) + 1):
        token = login(f"driver{number}")
        offset = (number - 3) * 0.0012
        http(
            "POST",
            f"{TRACKING}/api/v1/tracking/drivers/{driver_id(number)}/location",
            {"points": [{"lat": centro[0] + offset, "lng": centro[1] - offset}]},
            token,
        )
        time.sleep(0.4)
        # Marcar disponible es lo que mete al conductor en el indice geografico de dispatch.
        http(
            "POST",
            f"{GATEWAY}/api/v1/drivers/{driver_id(number)}/availability",
            {"status": "AVAILABLE"},
            token,
        )
    time.sleep(1)

    print("\n  Listo: 6 repartidores disponibles y cerca del centro.")
    print("  Ahora crea un envio y mira las ofertas:\n")
    print("    ./scripts/lab.sh crear")
    print("    ./scripts/lab.sh ofertas\n")


def cmd_reset(_args):
    if os.path.isdir(STATE_DIR):
        for name in os.listdir(STATE_DIR):
            os.remove(os.path.join(STATE_DIR, name))
    print("  estado del laboratorio borrado")


COMMANDS = {
    "login": cmd_login,
    "crear": cmd_crear,
    "estado": cmd_estado,
    "ofertas": cmd_ofertas,
    "aceptar": cmd_aceptar,
    "rechazar": cmd_rechazar,
    "mover": cmd_mover,
    "conducir": cmd_conducir,
    "ws": cmd_ws,
    "eventos": cmd_eventos,
    "simoff": cmd_simoff,
    "simon": cmd_simon,
    "preparar": cmd_preparar,
    "reset": cmd_reset,
}


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print(__doc__)
        return
    command = sys.argv[1]
    if command not in COMMANDS:
        die(f"comando desconocido: {command}. Prueba con --help")
    COMMANDS[command](sys.argv[2:])


if __name__ == "__main__":
    main()
