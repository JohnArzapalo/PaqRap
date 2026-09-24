"""
Tamaño de muestra para el experimento de simulación (Etapa 19.3).

Uso:
    py analisis/potencia.py resultados.csv [otro.csv ...] [--delta 24] [--alfa 0.05] [--potencia 0.8]
        [--ta-ms 2000]

Para cada nivel de carga, estima cuántas réplicas por algoritmo hacen falta
para detectar una diferencia de --delta horas en colapso_h entre TABU y AG
(prueba t de dos colas, varianzas posiblemente distintas), con la desviación
estándar observada en el CSV. Usa la fórmula normal y la corrige con la t de
Student (iterativa). También estima el tiempo de cómputo de las corridas con
la duración media observada, reescalada a --ta-ms.

Limitaciones:
- Las corridas censuradas se toman con su tiempo de censura (subestima la varianza).
- Con pocas réplicas la desviación estimada es muy incierta: el resultado es orientativo.
"""
import argparse
import math
import sys
from pathlib import Path

import pandas as pd
from scipy import stats

AVISO = "DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME"


def replicas_necesarias(s1, s2, delta, alfa, potencia):
    """n por grupo (dos colas). Primero con la normal y luego iterando con la t."""
    if delta <= 0:
        return math.inf
    var = s1 ** 2 + s2 ** 2
    if var == 0:
        return 2
    z = stats.norm.ppf(1 - alfa / 2) + stats.norm.ppf(potencia)
    n = max(2, math.ceil(z ** 2 * var / delta ** 2))
    for _ in range(50):
        gl = max(1, 2 * n - 2)
        t = stats.t.ppf(1 - alfa / 2, gl) + stats.t.ppf(potencia, gl)
        nuevo = max(2, math.ceil(t ** 2 * var / delta ** 2))
        if nuevo == n:
            break
        n = nuevo
    return n


def main():
    ap = argparse.ArgumentParser(description="Réplicas necesarias para detectar una diferencia en colapso_h.")
    ap.add_argument("csv", nargs="+")
    ap.add_argument("--delta", type=float, default=24.0, help="diferencia a detectar (h)")
    ap.add_argument("--alfa", type=float, default=0.05)
    ap.add_argument("--potencia", type=float, default=0.8)
    ap.add_argument("--ta-ms", type=float, default=2000, help="Ta del experimento real, para estimar tiempos")
    ap.add_argument("--filtro", default="", help="expresión de pandas para filtrar filas (p. ej. penalidad_estabilidad==16)")
    args = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    df = pd.concat([pd.read_csv(r) for r in args.csv], ignore_index=True)
    if args.filtro:
        df = df.query(args.filtro)
    sintetico = any("SINTETICO" in Path(r).name.upper() for r in args.csv)
    if sintetico:
        print(f"*** {AVISO} ***")
    print(f"Diferencia a detectar: {args.delta} h; alfa = {args.alfa}; potencia = {args.potencia}\n")
    print(f"{'nivel':6} {'n obs':>5} {'desv TABU':>10} {'desv AG':>9} {'réplicas/alg':>13} {'¿alcanzan 5?':>13} "
          f"{'min por corrida':>16} {'horas de cómputo':>17}")
    total_horas = 0.0
    for nivel, g in df.groupby("nivel", sort=False):
        t = g[g["algoritmo"] == "TABU"]
        a = g[g["algoritmo"] == "AG"]
        if len(t) < 2 or len(a) < 2:
            print(f"{nivel:6} réplicas insuficientes para estimar la varianza")
            continue
        s1, s2 = t["colapso_h"].std(ddof=1), a["colapso_h"].std(ddof=1)
        n = replicas_necesarias(s1, s2, args.delta, args.alfa, args.potencia)
        ta_obs = g["ta_ms"].mean()
        min_corrida = g["tiempo_real_ms"].mean() / 60000 * (args.ta_ms / ta_obs)
        horas = 2 * n * min_corrida / 60
        total_horas += horas
        print(f"{nivel:6} {len(g):5d} {s1:10.1f} {s2:9.1f} {n:13d} {'sí' if n <= 5 else 'no':>13} "
              f"{min_corrida:16.1f} {horas:17.1f}")
    print(f"\nTiempo total estimado (1 PC, Ta = {args.ta_ms:.0f} ms): {total_horas:.1f} h; en 3 PCs por nivel: "
          f"lo que tarde el nivel más lento.")
    if sintetico:
        print(f"*** {AVISO} ***")


if __name__ == "__main__":
    main()
