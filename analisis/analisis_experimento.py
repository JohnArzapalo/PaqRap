"""
Análisis estadístico del experimento numérico de PaqRap (IEN, sección 10).

Uso:
    py analisis/analisis_experimento.py resultados.csv [otro.csv ...]
        [--salida analisis/salida] [--alfa 0.05]
    py analisis/analisis_experimento.py --autoprueba

Detecta el modo por las columnas del CSV:
  - ESTÁTICO (Experimento --modo estatico): factores algoritmo x instancia.
  - SIMULACIÓN (Experimento --modo simulacion): factores algoritmo x nivel,
    variable principal colapso_h (tiempo hasta el colapso) con censura.

Requiere pandas, scipy y matplotlib. statsmodels (ANOVA paramétrico) y
lifelines (verificación del log-rank) son opcionales: si faltan, se usan las
implementaciones propias (ANOVA tipo II por mínimos cuadrados y log-rank),
ambas validadas (--autoprueba).

Salidas en --salida: tablas en CSV y Markdown, gráficos PNG y resumen.md.
Si algún archivo de entrada tiene "SINTETICO" en el nombre (o en las columnas
archivo_ventas / archivo_carga), TODAS las salidas se marcan
"DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME".

ESTÁTICO:
 1. Descriptiva por instancia y algoritmo.  2. Shapiro-Wilk y Levene.
 3. Tabú vs. AG por instancia: t de Welch si ambos grupos son normales; si
    no, U de Mann-Whitney; p-valor y diferencia relativa de medianas.
 4. ANOVA de dos factores (algoritmo x instancia) sobre S_costo y
    pct_pedidos_en_plazo, o ART (rangos alineados) si no hay supuestos.
 5. Regla de decisión: plazos -> costo -> tiempo hasta la mejor solución.
SIMULACIÓN:
 1. Descriptiva por nivel y algoritmo (incluye número de censuradas).
 2. Sin censuradas: ANOVA de dos factores (algoritmo x nivel) sobre
    colapso_h, verificando supuestos (alternativa ART), y Welch/Mann-Whitney
    por nivel. Con censuradas: Kaplan-Meier por algoritmo en cada nivel y
    prueba log-rank.
 3. Regla de decisión: tiempo hasta el colapso (mayor es mejor) -> % de
    pedidos en plazo -> costo acumulado.
"""
import argparse
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
from scipy import stats  # noqa: E402

try:
    import statsmodels.api as sm
    import statsmodels.formula.api as smf
    HAY_STATSMODELS = True
except ImportError:
    HAY_STATSMODELS = False

try:
    from lifelines.statistics import logrank_test as lifelines_logrank
    HAY_LIFELINES = True
except ImportError:
    HAY_LIFELINES = False

AVISO = "DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME"

# Variable -> (etiqueta, True si mayor es mejor)
VARIABLES_ESTATICO = {
    "pct_pedidos_en_plazo": ("% pedidos en plazo", True),
    "H": ("H (entregas sin asignar + tarde)", False),
    "S_costo": ("S (costo, S/)", False),
    "tiempo_mejor_ms": ("Tiempo hasta la mejor solución (ms)", False),
}
REGLA_ESTATICO = ["pct_pedidos_en_plazo", "S_costo", "tiempo_mejor_ms"]

VARIABLES_SIMULACION = {
    "colapso_h": ("Tiempo hasta el colapso (h)", True),
    "pct_pedidos_en_plazo": ("% pedidos en plazo", True),
    "costo_acumulado": ("Costo acumulado (S/)", False),
    "planificador_ms_medio": ("Tiempo medio del planificador (ms)", False),
}
REGLA_SIMULACION = ["colapso_h", "pct_pedidos_en_plazo", "costo_acumulado"]


# ============================ utilidades ============================

def tabla_markdown(df, decimales=4):
    """Tabla Markdown sin depender de 'tabulate'."""
    def fmt(v):
        if isinstance(v, (float, np.floating)):
            if np.isnan(v):
                return ""
            if v != 0 and abs(v) < 1e-3:
                return f"{v:.{decimales}g}"
            return f"{v:.{decimales}f}".rstrip("0").rstrip(".")
        return str(v)
    cols = list(df.columns)
    lineas = ["| " + " | ".join(cols) + " |", "|" + "|".join("---" for _ in cols) + "|"]
    for _, fila in df.iterrows():
        lineas.append("| " + " | ".join(fmt(fila[c]) for c in cols) + " |")
    return "\n".join(lineas)


class Salida:
    """Escribe todas las salidas, marcando las sintéticas."""

    def __init__(self, carpeta, sintetico):
        self.carpeta = Path(carpeta)
        self.carpeta.mkdir(parents=True, exist_ok=True)
        self.sintetico = sintetico
        self.resumen = []
        if sintetico:
            self.resumen.append(f"# ⚠ {AVISO}\n")

    def tabla(self, df, nombre, titulo):
        df = df.copy()
        if self.sintetico:
            df.insert(0, "advertencia", AVISO)
        df.to_csv(self.carpeta / f"{nombre}.csv", index=False)
        cabecera = f"> **{AVISO}**\n\n" if self.sintetico else ""
        md = f"{cabecera}## {titulo}\n\n{tabla_markdown(df.drop(columns=['advertencia'], errors='ignore'))}\n"
        (self.carpeta / f"{nombre}.md").write_text(md, encoding="utf-8")
        self.resumen.append(md)

    def texto(self, md):
        self.resumen.append(md)

    def figura(self, fig, nombre, titulo):
        ax = fig.axes[0]
        ax.set_title(f"{AVISO}\n{titulo}" if self.sintetico else titulo, color="red" if self.sintetico else "black")
        if self.sintetico:
            fig.text(0.5, 0.5, "SINTÉTICO", fontsize=48, color="red", alpha=0.15,
                     ha="center", va="center", rotation=25)
        fig.tight_layout()
        fig.savefig(self.carpeta / nombre, dpi=120)
        plt.close(fig)

    def cerrar(self):
        if self.sintetico:
            self.resumen.append(f"\n> **{AVISO}**\n")
        (self.carpeta / "resumen.md").write_text("\n".join(self.resumen), encoding="utf-8")


def es_constante(x):
    x = np.asarray(x, dtype=float)
    return len(x) == 0 or np.allclose(x, x[0])


def shapiro(x):
    """(estadístico, p, nota). Shapiro-Wilk necesita n >= 3 y datos no constantes."""
    x = np.asarray(x, dtype=float)
    if len(x) < 3:
        return np.nan, np.nan, "n<3"
    if es_constante(x):
        return np.nan, np.nan, "constante"
    w, p = stats.shapiro(x)
    return w, p, ""


# ============================ ANOVA ============================

def _matriz(df, a, b, efectos):
    """Matriz de diseño (codificación por tratamiento) con los efectos pedidos."""
    n = len(df)
    columnas = [np.ones(n)]
    da = pd.get_dummies(df[a], drop_first=True).astype(float).values
    db = pd.get_dummies(df[b], drop_first=True).astype(float).values
    if "A" in efectos:
        columnas.append(da)
    if "B" in efectos:
        columnas.append(db)
    if "AB" in efectos:
        inter = [da[:, [i]] * db[:, [j]] for i in range(da.shape[1]) for j in range(db.shape[1])]
        if inter:
            columnas.append(np.hstack(inter))
    return np.column_stack(columnas)


def _rss(X, y):
    beta, *_ = np.linalg.lstsq(X, y, rcond=None)
    r = y - X @ beta
    return float(r @ r), np.linalg.matrix_rank(X)


def anova_propia(df, y, a="algoritmo", b="instancia"):
    """ANOVA de dos factores tipo II por comparación de modelos (mínimos cuadrados)."""
    yv = df[y].astype(float).values
    rss_full, rango_full = _rss(_matriz(df, a, b, {"A", "B", "AB"}), yv)
    rss_ab, rango_ab = _rss(_matriz(df, a, b, {"A", "B"}), yv)
    rss_a, rango_a = _rss(_matriz(df, a, b, {"A"}), yv)
    rss_b, rango_b = _rss(_matriz(df, a, b, {"B"}), yv)
    gl_res = len(yv) - rango_full
    filas = []
    for efecto, ss, gl in [
        (a, rss_b - rss_ab, rango_ab - rango_b),
        (b, rss_a - rss_ab, rango_ab - rango_a),
        (f"{a}:{b}", rss_ab - rss_full, rango_full - rango_ab),
    ]:
        f = (ss / gl) / (rss_full / gl_res) if gl > 0 and gl_res > 0 and rss_full > 0 else np.nan
        p = stats.f.sf(f, gl, gl_res) if not np.isnan(f) else np.nan
        filas.append({"efecto": efecto, "SS": ss, "gl": gl, "F": f, "p": p})
    filas.append({"efecto": "residuo", "SS": rss_full, "gl": gl_res, "F": np.nan, "p": np.nan})
    return pd.DataFrame(filas), gl_res


def anova_statsmodels(df, y, a="algoritmo", b="instancia"):
    modelo = smf.ols(f"{y} ~ C({a}) * C({b})", data=df).fit()
    t = sm.stats.anova_lm(modelo, typ=2).reset_index()
    t.columns = ["efecto", "SS", "gl", "F", "p"]
    t["efecto"] = t["efecto"].replace({f"C({a})": a, f"C({b})": b, f"C({a}):C({b})": f"{a}:{b}",
                                       "Residual": "residuo"})
    return t


def comparar_anovas(df, y, a, b):
    """Diferencia máxima entre el ANOVA de statsmodels y el propio (F y p)."""
    t_sm = anova_statsmodels(df, y, a, b).set_index("efecto")
    t_pr, _ = anova_propia(df, y, a, b)
    t_pr = t_pr.set_index("efecto")
    efectos = [e for e in t_pr.index if e != "residuo"]
    d_f = max(abs(t_sm.loc[e, "F"] - t_pr.loc[e, "F"]) for e in efectos)
    d_p = max(abs(t_sm.loc[e, "p"] - t_pr.loc[e, "p"]) for e in efectos)
    return d_f, d_p


def anova_parametrica(df, y, a="algoritmo", b="instancia"):
    if HAY_STATSMODELS:
        t = anova_statsmodels(df, y, a, b)
        d_f, d_p = comparar_anovas(df, y, a, b)
        nota = (f"statsmodels (OLS, ANOVA tipo II). Verificación cruzada con la implementación propia: "
                f"máx |ΔF| = {d_f:.2e}, máx |Δp| = {d_p:.2e}"
                + (" -> COINCIDEN." if d_f < 1e-6 and d_p < 1e-6 else " -> ¡DIFIEREN! Revisar."))
        return t, nota
    t, _ = anova_propia(df, y, a, b)
    return t, "implementación propia (mínimos cuadrados, ANOVA tipo II; statsmodels no instalado)"


def anova_art(df, y, a="algoritmo", b="instancia"):
    """ANOVA sobre rangos alineados (ART): un ANOVA factorial por efecto,
    sobre los rangos de la respuesta alineada para ese efecto."""
    d = df[[a, b, y]].copy()
    d[y] = d[y].astype(float)
    mu = d[y].mean()
    m_a = d.groupby(a)[y].transform("mean")
    m_b = d.groupby(b)[y].transform("mean")
    m_ab = d.groupby([a, b])[y].transform("mean")
    residuo = d[y] - m_ab
    alineadas = {
        a: residuo + (m_a - mu),
        b: residuo + (m_b - mu),
        f"{a}:{b}": residuo + (m_ab - m_a - m_b + mu),
    }
    filas = []
    for efecto, valores in alineadas.items():
        d["_rango"] = stats.rankdata(valores)
        t, _ = anova_propia(d, "_rango", a, b)
        filas.append(t[t["efecto"] == efecto].iloc[0].to_dict())
    return pd.DataFrame(filas), "ART: ANOVA sobre rangos alineados (no paramétrica)"


def analizar_anova(df, y, alfa, b="instancia"):
    """Verifica supuestos y elige ANOVA paramétrica o ART."""
    a = "algoritmo"
    if es_constante(df[y]):
        return None, f"{y}: la respuesta es constante en todas las corridas; no hay variación que analizar."
    if df[a].nunique() < 2 or df[b].nunique() < 2:
        return None, f"{y}: se necesitan al menos 2 niveles de cada factor para el ANOVA de dos factores."
    _, gl_res = anova_propia(df, y, a, b)
    if gl_res <= 0:
        return None, f"{y}: réplicas insuficientes para estimar el error (se necesita más de 1 réplica por celda)."
    residuos = df[y] - df.groupby([a, b])[y].transform("mean")
    _, p_norm, nota_norm = shapiro(residuos)
    celdas = [g[y].values for _, g in df.groupby([a, b])]
    p_lev = np.nan if all(es_constante(c) for c in celdas) else stats.levene(*celdas, center="median").pvalue
    normal = nota_norm == "" and p_norm > alfa
    homogeneo = not np.isnan(p_lev) and p_lev > alfa
    if normal and homogeneo:
        tabla, metodo = anova_parametrica(df, y, a, b)
    else:
        tabla, metodo = anova_art(df, y, a, b)
    supuestos = (f"Shapiro-Wilk de residuos: p = {p_norm:.4g} {nota_norm}; "
                 f"Levene (mediana) entre celdas: p = {p_lev:.4g}. "
                 f"{'Se cumplen' if normal and homogeneo else 'NO se cumplen'} los supuestos -> {metodo}.")
    return tabla, supuestos


# ============================ comparaciones ============================

def comparar(g_tabu, g_ag, alfa):
    """Tabú vs. AG: Welch si ambos normales, si no Mann-Whitney."""
    if len(g_tabu) == 0 or len(g_ag) == 0:
        return "sin datos", np.nan
    if es_constante(np.concatenate([g_tabu, g_ag])):
        return "iguales (sin variación)", 1.0
    if len(g_tabu) < 2 or len(g_ag) < 2:
        return "n insuficiente", np.nan
    _, p1, n1 = shapiro(g_tabu)
    _, p2, n2 = shapiro(g_ag)
    if n1 == "" and n2 == "" and p1 > alfa and p2 > alfa:
        return "t de Welch", stats.ttest_ind(g_tabu, g_ag, equal_var=False).pvalue
    return "U de Mann-Whitney", stats.mannwhitneyu(g_tabu, g_ag, alternative="two-sided").pvalue


def fila_comparacion(grupo_col, grupo, var, t, a, prueba, p, alfa, mayor_mejor):
    med_t = np.median(t) if len(t) else np.nan
    med_a = np.median(a) if len(a) else np.nan
    dif_rel = (med_t - med_a) / abs(med_a) * 100 if not np.isnan(med_a) and med_a != 0 else np.nan
    significativo = not np.isnan(p) and p < alfa
    if significativo:
        mejor_tabu = (med_t > med_a) if mayor_mejor else (med_t < med_a)
        ganador = "TABU" if mejor_tabu else "AG"
    else:
        ganador = "sin diferencia"
    return {grupo_col: grupo, "variable": var, "prueba": prueba, "p": p,
            "mediana_TABU": med_t, "mediana_AG": med_a, "dif_rel_medianas_%": dif_rel,
            "significativo": "sí" if significativo else "no", "mejor": ganador}


def decidir(filas, grupo_col, regla):
    decisiones = []
    for g in sorted({f[grupo_col] for f in filas}):
        ganadores = {f["variable"]: f["mejor"] for f in filas if f[grupo_col] == g}
        decision = "sin diferencia significativa en ningún criterio"
        for var in regla:
            if ganadores.get(var) in ("TABU", "AG"):
                decision = f"{ganadores[var]} (decide: {var})"
                break
        decisiones.append({grupo_col: g, "decision": decision})
    return pd.DataFrame(decisiones)


def descriptiva(df, grupo_col, variables):
    filas = []
    for (g, alg), d in df.groupby([grupo_col, "algoritmo"]):
        for var in variables:
            x = d[var].astype(float)
            fila = {grupo_col: g, "algoritmo": alg, "variable": var, "n": len(x),
                    "media": x.mean(), "desv": x.std(ddof=1) if len(x) > 1 else np.nan,
                    "mediana": x.median(), "min": x.min(), "max": x.max()}
            if "censurada" in d.columns:
                fila["censuradas"] = int((d["censurada"] == "si").sum())
            filas.append(fila)
    return pd.DataFrame(filas)


def cajas(df, grupo_col, variables, out):
    for var, (etiqueta, _) in variables.items():
        fig, ax = plt.subplots(figsize=(8, 4.5))
        grupos, nombres = [], []
        for (g, alg), d in df.groupby([grupo_col, "algoritmo"]):
            grupos.append(d[var].astype(float).values)
            nombres.append(f"{g}\n{alg}")
        ax.boxplot(grupos, tick_labels=nombres)
        ax.set_ylabel(etiqueta)
        out.figura(fig, f"caja_{var}.png", f"{etiqueta} por {grupo_col} y algoritmo")


# ============================ supervivencia ============================

def kaplan_meier(tiempos, eventos):
    """Estimador de Kaplan-Meier: (tiempos de evento, S(t) justo después de cada uno)."""
    t = np.asarray(tiempos, dtype=float)
    e = np.asarray(eventos, dtype=bool)
    puntos, s = [0.0], [1.0]
    sup = 1.0
    for ti in np.unique(t[e]):
        n = np.sum(t >= ti)
        d = np.sum((t == ti) & e)
        sup *= (1 - d / n)
        puntos.append(ti)
        s.append(sup)
    return np.array(puntos), np.array(s)


def logrank(t1, e1, t2, e2):
    """Prueba log-rank de dos grupos. Devuelve (chi2, p, O1, E1, V)."""
    t1, t2 = np.asarray(t1, float), np.asarray(t2, float)
    e1, e2 = np.asarray(e1, bool), np.asarray(e2, bool)
    tiempos = np.unique(np.concatenate([t1[e1], t2[e2]]))
    o1 = ex1 = var = 0.0
    for ti in tiempos:
        n1, n2 = np.sum(t1 >= ti), np.sum(t2 >= ti)
        d1, d2 = np.sum((t1 == ti) & e1), np.sum((t2 == ti) & e2)
        n, d = n1 + n2, d1 + d2
        if n == 0:
            continue
        o1 += d1
        ex1 += d * n1 / n
        if n > 1:
            var += d * (n1 / n) * (n2 / n) * (n - d) / (n - 1)
    if var == 0:
        return np.nan, np.nan, o1, ex1, var
    chi2 = (o1 - ex1) ** 2 / var
    return chi2, stats.chi2.sf(chi2, 1), o1, ex1, var


def autoprueba():
    """Valida el log-rank con un caso calculado a mano (y con lifelines si está)
    y el ANOVA propio con statsmodels (si está)."""
    # Caso a mano. Grupo A: eventos en t=1 y t=3. Grupo B: evento en t=2, censura en t=4.
    #  t=1: nA=2, nB=2, d=1 (A): E_A = 1/2, V = 1·(1/2)(1/2)(3/3) = 1/4
    #  t=2: nA=1, nB=2, d=1 (B): E_A = 1/3, V = 1·(1/3)(2/3)(2/2) = 2/9
    #  t=3: nA=1, nB=1, d=1 (A): E_A = 1/2, V = 1·(1/2)(1/2)(1/1) = 1/4
    #  O_A = 2; E_A = 4/3; V = 13/18; chi2 = (2/3)^2 / (13/18) = 8/13 = 0.615385
    chi2, p, o, e, v = logrank([1, 3], [1, 1], [2, 4], [1, 0])
    ok = abs(chi2 - 8 / 13) < 1e-12 and abs(o - 2) < 1e-12 and abs(e - 4 / 3) < 1e-12 and abs(v - 13 / 18) < 1e-12
    print(f"log-rank caso a mano: chi2 = {chi2:.6f} (esperado 8/13 = {8/13:.6f}), p = {p:.6f} -> "
          f"{'OK' if ok else 'FALLA'}")
    if HAY_LIFELINES:
        r = lifelines_logrank([1, 3], [2, 4], event_observed_A=[1, 1], event_observed_B=[1, 0])
        print(f"lifelines: chi2 = {r.test_statistic:.6f}, p = {r.p_value:.6f} -> "
              f"{'COINCIDE' if abs(r.test_statistic - chi2) < 1e-9 else 'DIFIERE'}")
        ok = ok and abs(r.test_statistic - chi2) < 1e-9
    rng = np.random.default_rng(1)
    filas = [{"algoritmo": al, "instancia": ins, "y": rng.normal(10 + 2 * (al == "TABU") + int(ins[1]), 1)}
             for al in ["AG", "TABU"] for ins in ["I1", "I2", "I3"] for _ in range(5)]
    df = pd.DataFrame(filas)
    if HAY_STATSMODELS:
        d_f, d_p = comparar_anovas(df, "y", "algoritmo", "instancia")
        print(f"ANOVA propio vs statsmodels: máx |ΔF| = {d_f:.2e}, máx |Δp| = {d_p:.2e} -> "
              f"{'COINCIDEN' if d_f < 1e-6 and d_p < 1e-6 else 'DIFIEREN'}")
        ok = ok and d_f < 1e-6 and d_p < 1e-6
    return 0 if ok else 1


# ============================ análisis por modo ============================

def analizar_estatico(df, out, alfa):
    out.tabla(descriptiva(df, "instancia", VARIABLES_ESTATICO), "descriptiva", "1. Estadística descriptiva")

    filas = []
    for (inst, alg), g in df.groupby(["instancia", "algoritmo"]):
        for var in VARIABLES_ESTATICO:
            w, p, nota = shapiro(g[var])
            filas.append({"instancia": inst, "algoritmo": alg, "variable": var, "W": w, "p": p,
                          "normal": "" if nota else ("sí" if p > alfa else "no"), "nota": nota})
    out.tabla(pd.DataFrame(filas), "normalidad", "2a. Shapiro-Wilk por grupo")

    filas = []
    for inst, g in df.groupby("instancia"):
        for var in VARIABLES_ESTATICO:
            grupos = [gg[var].astype(float).values for _, gg in g.groupby("algoritmo")]
            if len(grupos) < 2 or any(len(x) < 2 for x in grupos) or all(es_constante(x) for x in grupos):
                filas.append({"instancia": inst, "variable": var, "estadistico": np.nan, "p": np.nan,
                              "nota": "no aplicable (n<2 o sin variación)"})
                continue
            r = stats.levene(*grupos, center="median")
            filas.append({"instancia": inst, "variable": var, "estadistico": r.statistic, "p": r.pvalue,
                          "nota": "varianzas homogéneas" if r.pvalue > alfa else "varianzas distintas"})
    out.tabla(pd.DataFrame(filas), "levene", "2b. Levene (centrado en la mediana) por instancia")

    filas = []
    for inst, g in df.groupby("instancia"):
        for var, (_, mayor_mejor) in VARIABLES_ESTATICO.items():
            t = g[g["algoritmo"] == "TABU"][var].astype(float).values
            a = g[g["algoritmo"] == "AG"][var].astype(float).values
            prueba, p = comparar(t, a, alfa)
            filas.append(fila_comparacion("instancia", inst, var, t, a, prueba, p, alfa, mayor_mejor))
    out.tabla(pd.DataFrame(filas), "comparaciones",
              "3. Tabú vs. AG por instancia (Welch si hay normalidad; si no, Mann-Whitney)\n\n"
              "dif_rel_medianas_% = (mediana_TABU - mediana_AG) / |mediana_AG| x 100")

    for var in ["S_costo", "pct_pedidos_en_plazo"]:
        tabla, supuestos = analizar_anova(df, var, alfa, "instancia")
        out.texto(f"## 4. ANOVA de dos factores (algoritmo x instancia) sobre {var}\n\n{supuestos}\n")
        if tabla is not None:
            out.tabla(tabla, f"anova_{var}", f"ANOVA {var}")

    out.tabla(decidir(filas, "instancia", REGLA_ESTATICO), "decision",
              "5. Regla de decisión (plazos -> costo -> tiempo hasta la mejor solución)")
    cajas(df, "instancia", VARIABLES_ESTATICO, out)


def analizar_simulacion(df, out, alfa):
    orden = {"BAJA": 0, "MEDIA": 1, "ALTA": 2}
    df = df.copy()
    df["_orden"] = df["nivel"].map(orden).fillna(9)
    df = df.sort_values(["_orden", "algoritmo"]).drop(columns="_orden")
    df["evento"] = df["censurada"] != "si"
    n_cens = int((~df["evento"]).sum())

    out.tabla(descriptiva(df, "nivel", VARIABLES_SIMULACION), "descriptiva",
              "1. Estadística descriptiva por nivel y algoritmo")
    out.texto(f"Corridas censuradas (sin colapso en el horizonte): {n_cens} de {len(df)}.\n")

    filas = []
    if n_cens == 0:
        out.texto("## 2. Sin censuradas: ANOVA de dos factores sobre el tiempo hasta el colapso\n")
        tabla, supuestos = analizar_anova(df, "colapso_h", alfa, "nivel")
        out.texto(supuestos + "\n")
        if tabla is not None:
            out.tabla(tabla, "anova_colapso_h", "ANOVA colapso_h (algoritmo x nivel)")
    else:
        out.texto("## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel\n\n"
                  + ("Log-rank propio verificado con lifelines (columna p_lifelines).\n" if HAY_LIFELINES else
                     "lifelines no está instalado: log-rank propio (validado con --autoprueba).\n"))
        filas_lr = []
        for nivel, g in df.groupby("nivel", sort=False):
            t = g[g["algoritmo"] == "TABU"]
            a = g[g["algoritmo"] == "AG"]
            if len(t) == 0 or len(a) == 0:
                continue
            chi2, p, o, e, v = logrank(t["colapso_h"], t["evento"], a["colapso_h"], a["evento"])
            fila = {"nivel": nivel, "chi2": chi2, "p": p, "colapsos_TABU": int(t["evento"].sum()),
                    "esperados_TABU": e, "censuradas_TABU": int((~t["evento"]).sum()),
                    "censuradas_AG": int((~a["evento"]).sum())}
            if HAY_LIFELINES:
                fila["p_lifelines"] = lifelines_logrank(t["colapso_h"], a["colapso_h"], t["evento"], a["evento"]).p_value
            filas_lr.append(fila)
            # En la regla de decisión el log-rank reemplaza a la comparación de colapso_h
            mayor = t["colapso_h"].median() > a["colapso_h"].median()
            sig = not np.isnan(p) and p < alfa
            filas.append({"nivel": nivel, "variable": "colapso_h", "prueba": "log-rank", "p": p,
                          "mediana_TABU": t["colapso_h"].median(), "mediana_AG": a["colapso_h"].median(),
                          "dif_rel_medianas_%": np.nan, "significativo": "sí" if sig else "no",
                          "mejor": ("TABU" if mayor else "AG") if sig else "sin diferencia"})
            fig, ax = plt.subplots(figsize=(8, 4.5))
            for alg, d in [("TABU", t), ("AG", a)]:
                x, s = kaplan_meier(d["colapso_h"], d["evento"])
                horizonte = d["colapso_h"].max()
                ax.step(np.append(x, horizonte), np.append(s, s[-1]), where="post", label=alg)
            ax.set_xlabel("Horas simuladas")
            ax.set_ylabel("Proporción sin colapso")
            ax.set_ylim(0, 1.05)
            ax.legend()
            out.figura(fig, f"kaplan_meier_{nivel}.png", f"Kaplan-Meier, nivel {nivel}")
        if filas_lr:
            out.tabla(pd.DataFrame(filas_lr), "logrank", "Log-rank TABU vs. AG por nivel")

    # Comparaciones por nivel de las demás variables (y de colapso_h si no hay censura)
    for nivel, g in df.groupby("nivel", sort=False):
        for var, (_, mayor_mejor) in VARIABLES_SIMULACION.items():
            if var == "colapso_h" and n_cens > 0:
                continue
            t = g[g["algoritmo"] == "TABU"][var].astype(float).values
            a = g[g["algoritmo"] == "AG"][var].astype(float).values
            prueba, p = comparar(t, a, alfa)
            filas.append(fila_comparacion("nivel", nivel, var, t, a, prueba, p, alfa, mayor_mejor))
    out.tabla(pd.DataFrame(filas), "comparaciones", "3. Tabú vs. AG por nivel")
    out.tabla(decidir(filas, "nivel", REGLA_SIMULACION), "decision",
              "4. Regla de decisión (tiempo hasta el colapso -> % en plazo -> costo acumulado)")
    cajas(df, "nivel", VARIABLES_SIMULACION, out)


# ============================ programa ============================

def main():
    ap = argparse.ArgumentParser(description="Análisis estadístico del experimento PaqRap.")
    ap.add_argument("csv", nargs="*", help="CSV generados por Experimento")
    ap.add_argument("--salida", default="analisis/salida", help="carpeta de salida")
    ap.add_argument("--alfa", type=float, default=0.05, help="nivel de significancia")
    ap.add_argument("--autoprueba", action="store_true", help="valida log-rank y ANOVA y termina")
    args = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")   # acentos en la consola de Windows
    if args.autoprueba:
        return autoprueba()
    if not args.csv:
        ap.error("indique al menos un CSV (o --autoprueba)")
    alfa = args.alfa

    df = pd.concat([pd.read_csv(r).assign(archivo_csv=Path(r).name) for r in args.csv], ignore_index=True)
    columnas_datos = [c for c in ("archivo_ventas", "archivo_carga") if c in df.columns]
    sintetico = any("SINTETICO" in Path(r).name.upper() for r in args.csv) or any(
        df[c].astype(str).str.upper().str.contains("SINTETICO").any() for c in columnas_datos)
    simulacion = "modo" in df.columns and (df["modo"] == "simulacion").all()
    out = Salida(args.salida, sintetico)
    if sintetico:
        print(f"*** {AVISO} ***")

    clave = ["nivel", "algoritmo", "replica"] if simulacion else ["instancia", "ventana", "algoritmo", "replica"]
    if not df[df.duplicated(clave, keep=False)].empty:
        print("ADVERTENCIA: hay combinaciones repetidas entre los CSV.")

    grupos = "niveles: " + ", ".join(df["nivel"].unique()) if simulacion else \
        "instancias: " + ", ".join(sorted(df["instancia"].unique()))
    out.texto(f"# Análisis del experimento numérico PaqRap ({'SIMULACIÓN' if simulacion else 'ESTÁTICO'})\n\n"
              f"- Archivos: {', '.join(sorted(df['archivo_csv'].unique()))}\n"
              f"- Datos de ventas: {', '.join(sorted(df['archivo_ventas'].astype(str).unique()))}\n"
              f"- Corridas: {len(df)}; {grupos}; algoritmos: {', '.join(sorted(df['algoritmo'].unique()))}\n"
              f"- Modo de parada: {', '.join(sorted(df['modo_parada'].unique()))}; α = {alfa}\n"
              f"- statsmodels: {'sí' if HAY_STATSMODELS else 'no'}; lifelines: {'sí' if HAY_LIFELINES else 'no'}\n")
    if simulacion:
        analizar_simulacion(df, out, alfa)
    else:
        analizar_estatico(df, out, alfa)
    out.cerrar()
    print(f"Salidas en: {Path(args.salida).resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
