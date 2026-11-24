#!/usr/bin/env python3
"""
APP CLIENTE de EnRuta (menu interactivo).

Es el mismo cliente que la web de http://localhost:5173, pero en terminal: cada opcion es una
peticion HTTP real al gateway con tu JWT. Sirve para operar el sistema sin depender del navegador.

    ./scripts/cliente.py
"""
import sys

import lab

MENU = """
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
"""


def elegir_ruta():
    print("\n  Rutas de ejemplo:")
    for index, (name, preset) in enumerate(lab.PRESETS.items(), start=1):
        print(f"    {index}) {preset['pickup']['address']}  →  {preset['dropoff']['address']}")
    option = input("\n  Elige una [1]: ").strip() or "1"
    names = list(lab.PRESETS)
    try:
        return lab.PRESETS[names[int(option) - 1]]
    except (ValueError, IndexError):
        return lab.PRESETS["parque"]


def crear():
    preset = elegir_ruta()
    token = lab.login("cliente")
    import uuid

    status, payload = lab.http(
        "POST",
        f"{lab.GATEWAY}/api/v1/shipments",
        preset,
        token,
        {"Idempotency-Key": str(uuid.uuid4())},
    )
    if status != 201:
        print(f"\n  No se pudo crear: {payload}\n")
        return
    state = lab.state_load()
    state["shipmentId"] = payload["id"]
    lab.state_save(state)
    print(f"\n  ✔ Envio creado: {payload['id']}")
    print(f"    estado: {payload['status']}   PIN de recogida: {payload['pickupPin']}")
    print("    Abre el mapa (opcion 6) y en unos segundos veras la asignacion.\n")


def estado():
    state = lab.state_load()
    shipment_id = state.get("shipmentId")
    if not shipment_id:
        print("\n  No tienes envios. Crea uno con la opcion 1.\n")
        return
    token = lab.login("cliente")
    status, shipment = lab.http("GET", f"{lab.GATEWAY}/api/v1/shipments/{shipment_id}", token=token)
    if status != 200:
        print(f"\n  Error: {shipment}\n")
        return
    print(f"\n  Envio {shipment_id}")
    print(f"  Estado: {shipment['status']}   Conductor: {shipment.get('driverId') or 'sin asignar'}")
    for step in shipment.get("history", []):
        print(f"    {step['occurredAt'][11:19]}  {(step['from'] or 'inicio')} → {step['to']}")
    status, tracking = lab.http(
        "GET", f"{lab.TRACKING}/api/v1/tracking/shipments/{shipment_id}", token=token
    )
    if status == 200 and tracking.get("lastPosition"):
        position = tracking["lastPosition"]
        print(
            f"    posicion {float(position['lat']):.5f},{float(position['lng']):.5f}"
            f"  ·  {tracking.get('distanceToDropoffMeters', '?')} m  ·  ETA {tracking.get('etaSeconds', '?')} s"
        )
    print()


def en_vivo():
    state = lab.state_load()
    if not state.get("shipmentId"):
        print("\n  Crea un envio primero.\n")
        return
    lab.cmd_ws([])


def cancelar():
    state = lab.state_load()
    shipment_id = state.get("shipmentId")
    if not shipment_id:
        print("\n  No hay envio que cancelar.\n")
        return
    print("\n  Ojo: solo se puede cancelar hasta que el repartidor recoja el paquete.")
    if (input("  ¿Seguro? (s/n) [n]: ").strip().lower()) != "s":
        return
    token = lab.login("cliente")
    status, payload = lab.http(
        "POST", f"{lab.GATEWAY}/api/v1/shipments/{shipment_id}/cancel", token=token
    )
    if status == 200:
        print(f"\n  ✔ Cancelado. Estado: {payload['status']}\n")
    else:
        print(f"\n  No se pudo cancelar: {payload.get('detail', payload)}\n")


def mis_envios():
    token = lab.login("cliente")
    status, page = lab.http("GET", f"{lab.GATEWAY}/api/v1/shipments?page=0&size=10", token=token)
    if status != 200:
        print(f"\n  Error: {page}\n")
        return
    print(f"\n  Tienes {page['totalElements']} envios:")
    for shipment in page["content"]:
        print(f"    {shipment['id'][:8]}  {shipment['status']:12}  {shipment['dropoff']['address'][:40]}")
    print()


def abrir_mapa():
    print("\n  Abre en el navegador:  http://localhost:5173")
    print("  Entra con cliente@enruta.test / Cliente1234!\n")


ACTIONS = {
    "1": crear,
    "2": estado,
    "3": en_vivo,
    "4": cancelar,
    "5": mis_envios,
    "6": abrir_mapa,
}


def main():
    lab.login("cliente")
    while True:
        print(MENU)
        option = input("  Opcion: ").strip()
        if option == "0":
            break
        action = ACTIONS.get(option)
        if action:
            try:
                action()
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
