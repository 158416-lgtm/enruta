#!/usr/bin/env bash
# Muestra en vivo lo que hace cada servicio mientras usas la app.
exec python3 -u "$(dirname "${BASH_SOURCE[0]}")/ver-servicios.py" "$@"
