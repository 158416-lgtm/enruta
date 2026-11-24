#!/usr/bin/env python3
"""
APP REPARTIDOR de EnRuta (menu interactivo).

Hace exactamente lo que haria la app movil del conductor: consulta ofertas, acepta, reporta
posicion GPS y avanza el envio. Todo son peticiones reales con el JWT del repartidor.

    ./scripts/repartidor.py
"""
import sys
import time

import lab

MENU = """
┌──────────────────────────────────────────────────┐
│          EnRuta · APP REPARTIDOR ({driver})       │
├──────────────────────────────────────────────────┤
│  1) Ver mis ofertas                              │
│  2) Ver ofertas de toda la flota (a quien fue)   │
│  3) Aceptar la oferta                            │
│  4) Rechazar la oferta                           │
│  5) Ping GPS: estoy en el ORIGEN                 │
│  6) Ping GPS: estoy en el DESTINO                │
│  7) Ping GPS: me alejo 500 m                     │
│  8) Conducir automaticamente hasta el destino    │
│  9) Ver el envio que llevo                       │
│  c) Cambiar de repartidor                        │
│  0) Salir                                        │
└──────────────────────────────────────────────────┘
"""


class Session:
    def __init__(self):
        self.number = 1
        self.token = None

    def login(self, number=None):
        if number:
            self.number = int(number)
        self.token = lab.login(f"driver{self.number}")
        print(f"\n  Eres driver{self.number} ({lab.DRIVER_NAMES[self.number]}).\n")

    def driver_uuid(self):
        return lab.driver_id(self.number)


def ver_mis_ofertas(session):
    status, offers = lab.http(
        "GET", f"{lab.GATEWAY}/api/v1/drivers/{session.driver_uuid()}/offers", token=session.token
    )
    if status != 200:
        print(f"\n  Error: {offers}\n")
        return
    if not offers:
        print("\n  No tienes ofertas ahora mismo.")
        print("  Si el cliente acaba de crear un envio, prueba la opcion 2 para ver a quien se la enviaron.\n")
        return
    print()
    for offer in offers:
        seconds = lab._seconds_left(offer["expiresAt"])
        print(
            f"  Oferta {offer['id']}\n"
            f"    envio {offer['shipmentId']}\n"
            f"    distancia al origen: {offer['distanceMeters']} m\n"
            f"    vence en {seconds:.0f} s\n"
        )


def ver_flota(_session):
    lab.cmd_ofertas([])


def aceptar(session):
    number, token, offer = lab._find_offer()
    if not offer:
        print("\n  No hay ofertas pendientes (duran 15 s).\n")
        return
    if number != session.number:
        print(
            f"\n  La oferta esta para driver{number} ({lab.DRIVER_NAMES[number]}), no para ti.\n"
            f"  Cambia de repartidor con la opcion 'c' o acepta como driver{number}.\n"
        )
        if input(f"  ¿Aceptar como driver{number}? (s/n) [s]: ").strip().lower() == "n":
            return
        token = lab.login(f"driver{number}")
        session.number = number
        session.token = token
    status, payload = lab.http(
        "POST", f"{lab.GATEWAY}/api/v1/offers/{offer['id']}/accept", token=token
    )
    if status == 200:
        state = lab.state_load()
        state["driver"] = number
        lab.state_save(state)
        print(f"\n  ✔ Oferta aceptada. assignmentId: {payload['assignmentId']}")
        print("    El envio pasa a ASSIGNED en 1-3 s (lo veras en el mapa).\n")
    else:
        print(f"\n  No se pudo aceptar: {payload.get('detail', payload)}\n")


def rechazar(session):
    number, token, offer = lab._find_offer()
    if not offer:
        print("\n  No hay ofertas pendientes.\n")
        return
    status, _ = lab.http("POST", f"{lab.GATEWAY}/api/v1/offers/{offer['id']}/reject", token=token)
    if status == 204:
        print(f"\n  Rechazada. El despacho ofrecera al siguiente candidato.\n")
    else:
        print("\n  No se pudo rechazar.\n")


def _shipment(session, wait_seconds=10):
    """Espera a que la asignacion asincrona se refleje en shipment (evento por Kafka)."""
    state = lab.state_load()
    shipment_id = state.get("shipmentId")
    if not shipment_id:
        print("\n  No hay envio en curso. Crea uno desde la app cliente.\n")
        return None, None

    for attempt in range(wait_seconds):
        status, shipment = lab.http(
            "GET", f"{lab.GATEWAY}/api/v1/shipments/{shipment_id}", token=session.token
        )
        if status == 200:
            return shipment_id, shipment
        if attempt == 0:
            print("  esperando a que el envio quede asignado…", end="", flush=True)
        time.sleep(1)
    print()
    print("\n  Ese envio no esta asignado a ti (¿aceptaste la oferta?).\n")
    return None, None


def ping(session, lat, lng, etiqueta):
    shipment_id, _ = _shipment(session)
    status, payload = lab.http(
        "POST",
        f"{lab.TRACKING}/api/v1/tracking/drivers/{session.driver_uuid()}/location",
        {"points": [{"lat": lat, "lng": lng}]},
        session.token,
    )
    if status != 202:
        print(f"\n  No se pudo reportar: {payload}\n")
        return

    time.sleep(1.2)
    if shipment_id:
        _, shipment = lab.http(
            "GET", f"{lab.GATEWAY}/api/v1/shipments/{shipment_id}", token=lab.login("cliente")
        )
        print(f"  ✔ Ping enviado ({etiqueta})  →  estado del envio: {shipment['status']}")
        if shipment["status"] == "PICKED_UP":
            print("    (geocerca de origen confirmada con 3 pings)")
        elif shipment["status"] == "DELIVERED":
            print("    (geocerca de destino confirmada: envio entregado)")
    else:
        print(f"  ✔ Ping enviado ({etiqueta})")


def ping_origen(session):
    _, shipment = _shipment(session)
    if not shipment:
        return
    pickup = shipment["pickup"]
    ping(session, pickup["lat"], pickup["lng"], "en el origen")


def ping_destino(session):
    _, shipment = _shipment(session)
    if not shipment:
        return
    dropoff = shipment["dropoff"]
    ping(session, dropoff["lat"], dropoff["lng"], "en el destino")


def ping_alejar(session):
    _, shipment = _shipment(session)
    if not shipment:
        return
    pickup = shipment["pickup"]
    ping(session, pickup["lat"] + 0.0035, pickup["lng"] + 0.0035, "alejandome 500 m")


def conducir(session):
    lab.cmd_conducir([str(session.number)])


def ver_envio(session):
    shipment_id, shipment = _shipment(session)
    if not shipment:
        return
    print(f"\n  Llevas el envio {shipment_id}")
    print(f"  Estado: {shipment['status']}")
    print(f"  Origen:  {shipment['pickup']['address']}")
    print(f"  Destino: {shipment['dropoff']['address']}")
    print(f"  PIN que debe dar el cliente: {shipment['pickupPin']}\n")


def cambiar(session):
    number = input("  ¿Que repartidor eres? (1-6): ").strip()
    if number.isdigit() and 1 <= int(number) <= 6:
        session.login(int(number))
    else:
        print("  Numero invalido.")


def main():
    session = Session()
    number = input("  ¿Que repartidor eres? (1-6) [1]: ").strip() or "1"
    session.login(int(number) if number.isdigit() and 1 <= int(number) <= 6 else 1)

    actions = {
        "1": ver_mis_ofertas,
        "2": ver_flota,
        "3": aceptar,
        "4": rechazar,
        "5": ping_origen,
        "6": ping_destino,
        "7": ping_alejar,
        "8": conducir,
        "9": ver_envio,
        "c": cambiar,
    }
    while True:
        print(MENU.format(driver=f"driver{session.number} · {lab.DRIVER_NAMES[session.number]}"))
        option = input("  Opcion: ").strip().lower()
        if option == "0":
            break
        action = actions.get(option)
        if action:
            try:
                action(session)
            except KeyboardInterrupt:
                print()
        else:
            print("  Opcion invalida.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n  Hasta luego.")
        sys.exit(0)
