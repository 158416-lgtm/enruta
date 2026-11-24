#!/usr/bin/env python3
"""
Visor en vivo: muestra lo que hace cada uno de los 7 servicios mientras usas la app.

    ./scripts/ver-servicios.sh

Cada linea es un mensaje real de los logs, traducido a lenguaje claro y pintado con el color
del servicio que lo emitio. Deja la ventana abierta y usa la app: veras la cadena completa.
"""
import json
import os
import re
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGS = os.path.join(ROOT, "logs")

SERVICES = {
    "gateway": ("PORTERO", "\033[95m"),
    "auth": ("RECEPCIONISTA", "\033[94m"),
    "shipment": ("JEFE DE PEDIDOS", "\033[92m"),
    "dispatch": ("COORDINADOR", "\033[93m"),
    "tracking": ("RASTREADOR", "\033[96m"),
    "routing": ("NAVEGADOR", "\033[38;5;213m"),
    "notification": ("CARTERO", "\033[33m"),
}
RESET = "\033[0m"
DIM = "\033[2m"
BOLD = "\033[1m"


def color_of(service):
    return SERVICES.get(service, ("SISTEMA", "\033[37m"))[1]


def name_of(service):
    return SERVICES.get(service, ("SISTEMA", ""))[0]


# mensajes que valen la pena; el resto (arranques, healthchecks) se ignora
INTERESANTES = [
    "envio creado",
    "oferta creada",
    "asignado",
    "asignacion cerrada",
    "outbox publicado",
    "evento tracking publicado",
    "notificacion enviada",
    "notificacion reintentara",
    "notificacion a DLQ",
    "despacho agotado",
    "conductor demo creado",
    "usuario demo creado",
]


def humanize(service, message):
    """Traduce el mensaje tecnico a algo que se entienda en una demo."""
    m = message

    match = re.search(r"envio creado id=([0-9a-f-]+)", m)
    if match:
        return f"Envio creado · id {match.group(1)[:8]}"

    match = re.search(r"oferta creada shipment=\S+ driver=([0-9a-f-]+) round=(\d+) distancia=(\d+)m", m)
    if match:
        driver = match.group(1)[-1]
        return f"Oferta enviada al repartidor {driver} · {match.group(3)} m · ronda {match.group(2)}"

    if m.startswith("asignado shipment="):
        return "Asignacion confirmada: el pedido ya tiene repartidor"

    if m.startswith("asignacion cerrada"):
        return "Repartidor liberado (puede recibir otro pedido)"

    match = re.search(r"outbox publicado .* type=(\S+)", m)
    if match:
        return f"Evento publicado en Kafka · {match.group(1)}"

    match = re.search(r"evento tracking publicado type=(\S+) shipment=", m)
    if match:
        kind = match.group(1)
        if kind.endswith("geofence-entered"):
            return "Geocerca confirmada (3 pings seguidos)"
        if kind.endswith("trip-started"):
            return "El repartidor se puso en camino"
        return f"Evento de rastreo · {kind}"

    if "entrega ignorada" in m:
        return "Aviso: entrega ignorada (el pedido aun no iba en camino)"

    if m.startswith("oferta creada shipment="):
        return "El jefe de pedidos se entera de la oferta"

    match = re.search(r"notificacion enviada .* to=(\S+) intento=(\d+)", m)
    if match:
        return f"Correo enviado a {match.group(1)} · intento {match.group(2)}"

    match = re.search(r"notificacion reintentara .* intento=(\d+)", m)
    if match:
        return f"El correo fallo; reintento {match.group(1)}"

    if "notificacion a DLQ" in m:
        return "El correo agoto los intentos y fue a la bandeja de fallos"

    if "despacho agotado" in m:
        return "No habia repartidores disponibles"

    match = re.search(r"conductor demo creado id=([0-9a-f-]+)", m)
    if match:
        return f"Repartidor registrado · {match.group(1)[-3:]}"

    return m[:110]


def main():
    desde_inicio = "--desde-el-inicio" in sys.argv
    files = {}
    for service in SERVICES:
        path = os.path.join(LOGS, f"{service}.log")
        if os.path.exists(path):
            handle = open(path, "r", encoding="utf-8", errors="ignore")
            handle.seek(0 if desde_inicio else os.path.getsize(path))
            files[service] = [handle, os.path.getsize(path)]

    if not files:
        print("No encuentro logs/. Arranca los servicios con ./scripts/start.sh")
        return

    print()
    print(f"{BOLD}  Que esta pasando ahora mismo en los 7 servicios{RESET}")
    print("  " + "   ".join(f"{color_of(s)}{name_of(s)}{RESET}" for s in SERVICES))
    print(f"{DIM}  (Ctrl+C para salir · usa la app y veras la cadena){RESET}")
    print()

    seen = {}
    try:
        while True:
            for service, entry in files.items():
                handle, offset = entry
                size = os.path.getsize(handle.name)
                if size < offset:  # el archivo se reinicio
                    handle.seek(0)
                    offset = 0
                if size == offset:
                    continue
                handle.seek(offset)
                chunk = handle.read()
                entry[1] = handle.tell()
                for line in chunk.splitlines():
                    line = line.strip()
                    if not line or not line.startswith("{"):
                        continue
                    try:
                        event = json.loads(line)
                    except Exception:
                        continue
                    message = event.get("message", "")
                    level = event.get("level", "INFO")
                    if "read-model" in message:
                        continue
                    interesting = any(key in message for key in INTERESANTES)
                    if not interesting and level not in ("WARN", "ERROR"):
                        continue
                    if level in ("WARN", "ERROR") and not interesting:
                        continue
                    stamp = event.get("@timestamp", "")[11:19]
                    text = humanize(service, message)
                    key = (text, service)
                    previous = seen.get(key)
                    now = time.time()
                    if previous and now - previous < 2.5:
                        continue
                    seen[key] = now
                    mark = f"{color_of(service)}{name_of(service):15}{RESET}"
                    print(f"  {DIM}{stamp}{RESET}  {mark} {text}", flush=True)
            time.sleep(0.4)
    except KeyboardInterrupt:
        print(f"\n  {DIM}visor cerrado{RESET}\n")


if __name__ == "__main__":
    main()
