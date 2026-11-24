#!/usr/bin/env bash
# Atajo para el laboratorio: ./scripts/lab.sh estado
exec python3 "$(dirname "${BASH_SOURCE[0]}")/lab.py" "$@"
