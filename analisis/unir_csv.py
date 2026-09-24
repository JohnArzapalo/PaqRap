"""
Une varios CSV de Experimento (por ejemplo, uno por PC) en un solo archivo.

Uso:
    py analisis/unir_csv.py res_pc1.csv res_pc2.csv --salida resultados_unidos.csv

Agrega la columna archivo_origen y avisa si una misma combinación
(instancia, algoritmo, réplica) aparece más de una vez.
Si algún archivo de entrada es SINTÉTICO, el nombre de salida lo indica.
"""
import argparse
import sys
from pathlib import Path

import pandas as pd


def main():
    ap = argparse.ArgumentParser(description="Une CSV de resultados de Experimento.")
    ap.add_argument("csv", nargs="+", help="archivos CSV de entrada")
    ap.add_argument("--salida", default="resultados_unidos.csv", help="archivo CSV de salida")
    args = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")   # acentos en la consola de Windows

    partes = []
    for ruta in args.csv:
        df = pd.read_csv(ruta)
        df["archivo_origen"] = Path(ruta).name
        partes.append(df)
    columnas = [list(p.columns) for p in partes]
    if any(c != columnas[0] for c in columnas):
        print("ERROR: los CSV no tienen las mismas columnas (¿versiones distintas de Experimento?).")
        sys.exit(1)
    todo = pd.concat(partes, ignore_index=True)

    # Modo simulación: (nivel, algoritmo, réplica); modo estático: (instancia, ventana, algoritmo, réplica)
    clave = ["nivel", "algoritmo", "replica"] if "nivel" in todo.columns else ["instancia", "ventana", "algoritmo", "replica"]
    dup = todo[todo.duplicated(clave, keep=False)]
    if not dup.empty:
        print("ADVERTENCIA: combinaciones repetidas (revise el reparto entre PCs):")
        print(dup[clave + ["archivo_origen"]].to_string(index=False))

    salida = Path(args.salida)
    sintetico = any("SINTETICO" in Path(r).name.upper() for r in args.csv) or \
        todo["archivo_ventas"].astype(str).str.upper().str.contains("SINTETICO").any()
    if sintetico and "SINTETICO" not in salida.name.upper():
        salida = salida.with_name(salida.stem + "_SINTETICO" + salida.suffix)
    todo.to_csv(salida, index=False)
    print(f"{len(todo)} corridas de {len(args.csv)} archivos -> {salida}")
    if sintetico:
        print("*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***")


if __name__ == "__main__":
    main()
