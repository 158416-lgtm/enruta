#!/usr/bin/env bash
# Cobertura global de lineas (requiere haber corrido mvn verify). Minimo: 90%.
exec python3 "$(dirname "${BASH_SOURCE[0]}")/coverage.py" "$@"
