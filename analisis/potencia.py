"""
Tamaño de muestra para el experimento de simulación (Etapas 19.3 y 25).

Uso:
    py analisis/potencia.py resultados.csv [otro.csv ...] [--alfa 0.05] [--potencia 0.8]
        [--delta-pp 15] [--ta-ms 2000] [--hilos 1]
    py analisis/potencia.py --tabla [--alfa 0.05] [--potencia 0.8]
    py analisis/potencia.py resultados.csv --variable colapso_h [--delta 24]

VARIABLE PRINCIPAL (Etapa 25, indicación del profesor): % de corridas con colapso.
El diseño es PAREADO: en cada nivel, la réplica r es una situación (los mismos
pedidos y bloqueos) que corren TABU y AG. De cada par solo informan los
discordantes:
    b = pares en que solo TABU colapsa,   c = pares en que solo AG colapsa.
La prueba es la de McNemar (exacta: binomial de b sobre b + c con p = 1/2).
Con p_b = P(solo TABU) y p_c = P(solo AG), la diferencia de % de colapsos es
delta = p_b - p_c y la probabilidad de discordancia es psi = p_b + p_c.

Pares necesarios (Connor, 1987):
    n = [ z(1-alfa/2)·sqrt(psi) + z(potencia)·sqrt(psi - delta²) ]² / delta²
Como con pocos colapsos la aproximación normal falla, n se verifica con la
POTENCIA EXACTA de la prueba de McNemar exacta (enumeración binomial) y se
aumenta hasta alcanzar la potencia pedida.

Con un CSV, estima p_b y p_c por nivel con los pares observados. Si la
diferencia observada es 0 (o no hay discordantes), usa --delta-pp (puntos
porcentuales) con la discordancia observada (mínimo 2·delta).
--tabla imprime la tabla de planificación (sin CSV) para varias psi y delta.

Limitaciones: con pocas réplicas p_b y p_c son muy inciertas; el resultado
orienta el número de réplicas, no lo garantiza.
"""
import argparse
import math
import sys
from functools import lru_cache
from pathlib import Path

import pandas as pd
from scipy import stats

AVISO = "DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME"


# ===================== proporciones pareadas (McNemar) =====================

def pares_connor(pb, pc, alfa, potencia):
    """Pares necesarios con la fórmula normal de Connor (1987)."""
    delta, psi = abs(pb - pc), pb + pc
    if delta <= 0 or psi <= 0:
        return math.inf
    za, zb = stats.norm.ppf(1 - alfa / 2), stats.norm.ppf(potencia)
    return math.ceil((za * math.sqrt(psi) + zb * math.sqrt(psi - delta ** 2)) ** 2 / delta ** 2)


@lru_cache(maxsize=None)
def region_rechazo(k, alfa):
    """Valores de b (de 0 a k) con p-valor binomial exacto (p = 1/2, bilateral) menor que alfa."""
    return tuple(b for b in range(k + 1) if stats.binomtest(b, k, 0.5).pvalue < alfa)


def potencia_exacta(n, pb, pc, alfa):
    """Potencia de la prueba de McNemar EXACTA (bilateral) con n pares.
    K = discordantes ~ Bin(n, psi); dado K = k, b ~ Bin(k, pb/psi). Se rechaza si
    el p-valor binomial exacto de b sobre k (p = 1/2) es menor que alfa."""
    psi = pb + pc
    if psi <= 0:
        return 0.0
    q = pb / psi
    total = 0.0
    for k in range(1, n + 1):
        pk = stats.binom.pmf(k, n, psi)
        if pk < 1e-12:
            continue
        total += pk * sum(stats.binom.pmf(b, k, q) for b in region_rechazo(k, alfa))
    return total


def pares_necesarios(pb, pc, alfa, potencia, tope=2000):
    """Parte de Connor y aumenta n hasta que la potencia exacta alcance la pedida."""
    n = pares_connor(pb, pc, alfa, potencia)
    if n == math.inf:
        return math.inf, 0.0
    n = max(n, 5)
    while n <= tope:
        pot = potencia_exacta(n, pb, pc, alfa)
        if pot >= potencia:
            return n, pot
        n += max(1, n // 20)
    return math.inf, 0.0


def tabla_planificacion(alfa, potencia):
    print(f"Pares (réplicas por nivel) necesarios, McNemar exacta, alfa = {alfa}, potencia = {potencia}")
    print("psi = % de pares en que colapsa solo uno de los dos; delta = diferencia de % de colapsos\n")
    deltas = [0.10, 0.15, 0.20, 0.25, 0.30]
    print(f"{'psi':>6} " + " ".join(f"{'delta=' + format(d * 100, '.0f') + 'pp':>12}" for d in deltas))
    for psi in [0.10, 0.20, 0.30, 0.40, 0.50]:
        celdas = []
        for d in deltas:
            if d > psi:
                celdas.append(f"{'-':>12}")
                continue
            pb, pc = (psi + d) / 2, (psi - d) / 2
            n, _ = pares_necesarios(pb, pc, alfa, potencia)
            celdas.append(f"{n:>12}" if n != math.inf else f"{'>2000':>12}")
        print(f"{psi:6.2f} " + " ".join(celdas))


def pares_por_nivel(g):
    """Tabla de pares (nivel, réplica) con colapso de TABU y de AG."""
    g = g.assign(colapso=(g["censurada"] != "si").astype(int))
    clave = [c for c in ("replica", "semilla", "semilla_carga") if c in g.columns]   # etapa 29: varias corridas
    t = g[g["algoritmo"] == "TABU"].set_index(clave)["colapso"]
    a = g[g["algoritmo"] == "AG"].set_index(clave)["colapso"]
    comun = t.index.intersection(a.index)
    return t.loc[comun], a.loc[comun]


def modo_proporciones(df, args):
    print(f"Variable principal: % de corridas con colapso (pareado, McNemar exacta); "
          f"alfa = {args.alfa}; potencia = {args.potencia}\n")
    print(f"{'nivel':6} {'pares':>5} {'%col TABU':>9} {'%col AG':>8} {'b':>3} {'c':>3} {'p_b':>6} {'p_c':>6} "
          f"{'pares nec.':>10} {'pot. exacta':>11} {'min/corrida':>11} {'horas':>7}")
    total_horas = 0.0
    comb_n = comb_b = comb_c = 0
    for nivel, g in df.groupby("nivel", sort=False):
        t, a = pares_por_nivel(g)
        n_obs = len(t)
        if n_obs == 0:
            print(f"{nivel:6} sin pares TABU/AG")
            continue
        b = int(((t == 1) & (a == 0)).sum())
        c = int(((t == 0) & (a == 1)).sum())
        if 0 < (t.sum() + a.sum()) < 2 * n_obs:   # nivel "en transición": colapsa una parte
            comb_n, comb_b, comb_c = comb_n + n_obs, comb_b + b, comb_c + c
        pb, pc = b / n_obs, c / n_obs
        nota = ""
        if pb == pc:   # diferencia observada nula: se usa la mínima que interesa detectar
            d = args.delta_pp / 100
            psi = max(pb + pc, 2 * d)
            pb, pc = (psi + d) / 2, (psi - d) / 2
            nota = f"  (supuesto: delta = {args.delta_pp:.0f} pp, psi = {psi:.2f})"
        n, pot = pares_necesarios(pb, pc, args.alfa, args.potencia)
        ta_obs = g["ta_ms"].mean()
        min_corrida = g["tiempo_real_ms"].mean() / 60000 * (args.ta_ms / ta_obs if g["modo_parada"].eq("tiempo").all() else 1)
        horas = (2 * n * min_corrida / 60 / args.hilos) if n != math.inf else math.inf
        total_horas += horas
        print(f"{nivel:6} {n_obs:5d} {t.mean() * 100:9.1f} {a.mean() * 100:8.1f} {b:3d} {c:3d} {pb:6.2f} {pc:6.2f} "
              f"{(str(n) if n != math.inf else '>2000'):>10} {pot:11.3f} {min_corrida:11.1f} {horas:7.1f}{nota}")
    print(f"\nTiempo total estimado (Ta = {args.ta_ms:.0f} ms, {args.hilos} hilo(s) por PC): {total_horas:.1f} h "
          f"en una PC; repartido por nivel entre PCs: lo que tarde el nivel más lento.")
    if comb_n > 0:
        # Con pocas réplicas por nivel, p_b y p_c por nivel son muy inestables: la discordancia
        # combinada de los niveles en transición (ni 0 % ni 100 %) es una base más estable
        psi = (comb_b + comb_c) / comb_n
        print(f"\nCOMBINADO (niveles en transición, {comb_n} pares): solo TABU = {comb_b}, solo AG = {comb_c}, "
              f"psi = {psi:.2f}. Pares por nivel para detectar delta, con esa psi:")
        for d in [0.10, 0.15, 0.20, 0.25]:
            if d > psi:
                continue
            n, pot = pares_necesarios((psi + d) / 2, (psi - d) / 2, args.alfa, args.potencia)
            print(f"  delta = {d * 100:.0f} pp -> {(str(n) if n != math.inf else '>2000')} pares (potencia exacta {pot:.3f})")


# ===================== tiempo hasta el colapso (Etapa 19.3) =====================

def replicas_t(s1, s2, delta, alfa, potencia):
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


def modo_colapso_h(df, args):
    print(f"Variable secundaria colapso_h: diferencia a detectar {args.delta} h; alfa = {args.alfa}; "
          f"potencia = {args.potencia}\n(las censuradas se toman con su tiempo de censura: subestima la varianza)\n")
    print(f"{'nivel':6} {'n obs':>5} {'desv TABU':>10} {'desv AG':>9} {'réplicas/alg':>13}")
    for nivel, g in df.groupby("nivel", sort=False):
        t = g[g["algoritmo"] == "TABU"]["colapso_h"]
        a = g[g["algoritmo"] == "AG"]["colapso_h"]
        if len(t) < 2 or len(a) < 2:
            print(f"{nivel:6} réplicas insuficientes para estimar la varianza")
            continue
        s1, s2 = t.std(ddof=1), a.std(ddof=1)
        print(f"{nivel:6} {len(g):5d} {s1:10.1f} {s2:9.1f} {replicas_t(s1, s2, args.delta, args.alfa, args.potencia):13}")


def main():
    ap = argparse.ArgumentParser(description="Réplicas necesarias para el experimento de simulación.")
    ap.add_argument("csv", nargs="*")
    ap.add_argument("--variable", choices=["colapso", "colapso_h"], default="colapso",
                    help="colapso: %% de corridas con colapso (principal); colapso_h: tiempo hasta el colapso")
    ap.add_argument("--tabla", action="store_true", help="tabla de planificación sin CSV")
    ap.add_argument("--delta-pp", type=float, default=15.0,
                    help="diferencia mínima de %% de colapsos a detectar (puntos porcentuales) si la observada es 0")
    ap.add_argument("--delta", type=float, default=24.0, help="(colapso_h) diferencia a detectar en horas")
    ap.add_argument("--alfa", type=float, default=0.05)
    ap.add_argument("--potencia", type=float, default=0.8)
    ap.add_argument("--ta-ms", type=float, default=2000, help="Ta del experimento real, para estimar tiempos")
    ap.add_argument("--hilos", type=int, default=1, help="corridas simultáneas por PC (--hilos del experimento)")
    ap.add_argument("--filtro", default="", help="expresión de pandas para filtrar filas")
    args = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if args.tabla:
        tabla_planificacion(args.alfa, args.potencia)
        return
    if not args.csv:
        ap.error("indique al menos un CSV (o --tabla)")

    df = pd.concat([pd.read_csv(r) for r in args.csv], ignore_index=True)
    if args.filtro:
        df = df.query(args.filtro)
    sintetico = any("SINTETICO" in Path(r).name.upper() for r in args.csv)
    if sintetico:
        print(f"*** {AVISO} ***")
    if args.variable == "colapso":
        modo_proporciones(df, args)
    else:
        modo_colapso_h(df, args)
    if sintetico:
        print(f"*** {AVISO} ***")


if __name__ == "__main__":
    main()
