#!/usr/bin/env python3
"""
Comprueba la cobertura global de lineas leyendo los reportes de JaCoCo.

    ./scripts/coverage.sh        # exige >= 90%
    ./scripts/coverage.sh 95     # otro umbral

Se ejecuta despues de `mvn verify` (que genera los jacoco.csv).
"""
import csv
import glob
import sys

MIN = float(sys.argv[1]) if len(sys.argv) > 1 else 90.0

# El simulador de conductores es una herramienta de demostración, no un microservicio
# del producto. Se mide aparte para no mezclar la cobertura del núcleo con la del
# generador de tráfico de las pruebas manuales.
EXCLUDED_MODULES = {"tools/driver-simulator"}

per_module = {}
total = {"lm": 0, "lc": 0}

for path in glob.glob("**/target/site/jacoco/jacoco.csv", recursive=True):
    module = path.split("/target/")[0]
    if module in EXCLUDED_MODULES:
        continue
    entry = per_module.setdefault(module, {"lm": 0, "lc": 0})
    with open(path) as handle:
        for row in csv.DictReader(handle):
            missed = int(row["LINE_MISSED"])
            covered = int(row["LINE_COVERED"])
            entry["lm"] += missed
            entry["lc"] += covered
            total["lm"] += missed
            total["lc"] += covered

if not per_module:
    print("No hay reportes de JaCoCo. Ejecuta primero: mvn verify")
    sys.exit(2)


def pct(values):
    lines = values["lc"] + values["lm"]
    return 100.0 * values["lc"] / lines if lines else 0.0


print(f"{'Modulo':32} {'Lineas':>8}")
print("-" * 42)
for module in sorted(per_module):
    print(f"{module:32} {pct(per_module[module]):7.1f}%")
print("-" * 42)
print("(excluye arranque, configuracion, DTOs, seeders y tools/driver-simulator)")

value = pct(total)
print(f"{'TOTAL':32} {value:7.1f}%   ({total['lc']}/{total['lc'] + total['lm']})")

if value < MIN:
    print(f"\nFALLA: la cobertura {value:.1f}% esta por debajo del minimo {MIN:.0f}%")
    sys.exit(1)

print(f"\nOK: cobertura {value:.1f}% (minimo {MIN:.0f}%)")
