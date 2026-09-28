# Análisis del experimento numérico PaqRap (SIMULACIÓN)

- Archivos: experimento_oficial_SIM_5D.csv
- Datos de ventas: ventas.202609.txt
- Corridas: 206; niveles: MEDIA, BAJA, ALTA; algoritmos: AG, TABU
- Modo de parada: tiempo; α = 0.05
- statsmodels: sí; lifelines: sí

- Situaciones: ventanas (por_replica: cada réplica es una muestra de pedidos distinta, la misma para ambos algoritmos).

## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 40 | 25 | [13, 41] | 32.5 | [19, 49] | 8 | 2 | 5 | 25 | 0.4531 | no | sin diferencia |
| MEDIA | 35 | 28.5714 | [15, 46] | 48.5714 | [31, 66] | 6 | 4 | 11 | 14 | 0.1185 | no | sin diferencia |
| ALTA | 28 | 85.7143 | [67, 96] | 78.5714 | [59, 92] | 22 | 2 | 0 | 4 | 0.5 | no | sin diferencia |

**Global (todos los niveles, McNemar exacta sobre los pares discordantes sumados):** solo TABU colapsa en 8 pares, solo AG en 16; p = 0.1516 → **sin diferencia significativa**.

**Regresión logística GEE (agrupada por tramo), colapso ~ algoritmo + nivel:** odds ratio TABU/AG = 0.676, p = 0.1038.

## 1. Estadística descriptiva por nivel y algoritmo

| nivel | algoritmo | variable | n | media | desv | mediana | min | max | censuradas |
|---|---|---|---|---|---|---|---|---|---|
| ALTA | AG | colapso_h | 28 | 71.3411 | 34.5978 | 66.05 | 14.2833 | 120 | 6 |
| ALTA | AG | pct_pedidos_en_plazo | 28 | 99.7743 | 0.2765 | 99.825 | 98.55 | 100 | 6 |
| ALTA | AG | costo_por_pedido | 28 | 162.9908 | 6.8867 | 163.8475 | 145.3824 | 180.3414 | 6 |
| ALTA | AG | km_por_pedido | 28 | 26.9372 | 1.2932 | 27.1659 | 22.1618 | 29.5076 | 6 |
| ALTA | AG | costo_acumulado | 28 | 105938.25 | 57642.6445 | 96764 | 9886 | 189635 | 6 |
| ALTA | AG | planificador_ms_medio | 28 | 2099.825 | 59.7819 | 2111.95 | 2006 | 2222.3 | 6 |
| ALTA | AG | cambios_de_unidad | 28 | 97.0357 | 49.4567 | 94 | 16 | 244 | 6 |
| ALTA | AG | pedidos_inentregables_bloqueo | 28 | 0.1786 | 0.39 | 0 | 0 | 1 | 6 |
| ALTA | TABU | colapso_h | 28 | 69.2565 | 31.7515 | 61.7916 | 14.2833 | 120 | 4 |
| ALTA | TABU | pct_pedidos_en_plazo | 28 | 99.7729 | 0.2523 | 99.825 | 98.65 | 100 | 4 |
| ALTA | TABU | costo_por_pedido | 28 | 164.3375 | 5.3725 | 165.5206 | 146.4485 | 171.9831 | 4 |
| ALTA | TABU | km_por_pedido | 28 | 27.297 | 0.9901 | 27.4046 | 23.6237 | 28.7895 | 4 |
| ALTA | TABU | costo_acumulado | 28 | 103247.8929 | 53788.6919 | 95228.5 | 12140 | 194435 | 4 |
| ALTA | TABU | planificador_ms_medio | 28 | 2002.9679 | 1.7387 | 2002.5 | 1999.9 | 2007.3 | 4 |
| ALTA | TABU | cambios_de_unidad | 28 | 19.6786 | 9.2137 | 21.5 | 4 | 39 | 4 |
| ALTA | TABU | pedidos_inentregables_bloqueo | 28 | 0.1071 | 0.315 | 0 | 0 | 1 | 4 |
| BAJA | AG | colapso_h | 40 | 99.7267 | 35.5053 | 120 | 5.9167 | 120 | 27 |
| BAJA | AG | pct_pedidos_en_plazo | 40 | 99.6003 | 1.3529 | 100 | 93.33 | 100 | 27 |
| BAJA | AG | costo_por_pedido | 40 | 153.5162 | 11.9066 | 152.5134 | 137.5833 | 219.1429 | 27 |
| BAJA | AG | km_por_pedido | 40 | 24.9219 | 1.7682 | 24.791 | 21.3529 | 32.7143 | 27 |
| BAJA | AG | costo_acumulado | 40 | 103001.675 | 39915.8981 | 116289 | 2461 | 145677 | 27 |
| BAJA | AG | planificador_ms_medio | 40 | 2013.545 | 7.8642 | 2010.15 | 2003 | 2043.3 | 27 |
| BAJA | AG | cambios_de_unidad | 40 | 272.675 | 111.8097 | 298.5 | 7 | 459 | 27 |
| BAJA | AG | pedidos_inentregables_bloqueo | 40 | 0 | 0 | 0 | 0 | 0 | 27 |
| BAJA | TABU | colapso_h | 40 | 105.0904 | 31.5418 | 120 | 5.9167 | 120 | 30 |
| BAJA | TABU | pct_pedidos_en_plazo | 40 | 99.675 | 1.2256 | 100 | 94.12 | 100 | 30 |
| BAJA | TABU | costo_por_pedido | 40 | 159.5394 | 6.8041 | 159.9142 | 143.4278 | 186 | 30 |
| BAJA | TABU | km_por_pedido | 40 | 26.3906 | 0.9508 | 26.4325 | 23.6722 | 28.7778 | 30 |
| BAJA | TABU | costo_acumulado | 40 | 113315.275 | 36225.2804 | 125867.5 | 2715 | 150076 | 30 |
| BAJA | TABU | planificador_ms_medio | 40 | 2003.8625 | 2.4355 | 2003.5 | 1999.7 | 2015.6 | 30 |
| BAJA | TABU | cambios_de_unidad | 40 | 29.25 | 11.7773 | 28 | 5 | 55 | 30 |
| BAJA | TABU | pedidos_inentregables_bloqueo | 40 | 0 | 0 | 0 | 0 | 0 | 30 |
| MEDIA | AG | colapso_h | 35 | 105.0752 | 23.359 | 120 | 22.0167 | 120 | 18 |
| MEDIA | AG | pct_pedidos_en_plazo | 35 | 99.9151 | 0.1396 | 100 | 99.23 | 100 | 18 |
| MEDIA | AG | costo_por_pedido | 35 | 160.0866 | 3.1948 | 159.9675 | 149.8527 | 168.561 | 18 |
| MEDIA | AG | km_por_pedido | 35 | 26.441 | 0.6313 | 26.4147 | 24.062 | 27.5524 | 18 |
| MEDIA | AG | costo_acumulado | 35 | 142810.6 | 37467.7444 | 154823 | 19331 | 185474 | 18 |
| MEDIA | AG | planificador_ms_medio | 35 | 2039.2571 | 26.7372 | 2028.3 | 2008.3 | 2111.9 | 18 |
| MEDIA | AG | cambios_de_unidad | 35 | 177.8857 | 68.1956 | 175 | 48 | 299 | 18 |
| MEDIA | AG | pedidos_inentregables_bloqueo | 35 | 0.1429 | 0.355 | 0 | 0 | 1 | 18 |
| MEDIA | TABU | colapso_h | 35 | 110.1714 | 19.4213 | 120 | 59.8833 | 120 | 25 |
| MEDIA | TABU | pct_pedidos_en_plazo | 35 | 99.9563 | 0.0733 | 100 | 99.79 | 100 | 25 |
| MEDIA | TABU | costo_por_pedido | 35 | 162.7307 | 3.3486 | 162.9253 | 155.922 | 173.3853 | 25 |
| MEDIA | TABU | km_por_pedido | 35 | 27.0664 | 0.5084 | 27.0692 | 25.9959 | 28.3484 | 25 |
| MEDIA | TABU | costo_acumulado | 35 | 152860.6571 | 32745.1882 | 162737 | 76290 | 189996 | 25 |
| MEDIA | TABU | planificador_ms_medio | 35 | 2003.5457 | 1.3781 | 2003.3 | 2001 | 2006.5 | 25 |
| MEDIA | TABU | cambios_de_unidad | 35 | 25.6857 | 10.7259 | 26 | 9 | 54 | 25 |
| MEDIA | TABU | pedidos_inentregables_bloqueo | 35 | 0.1429 | 0.355 | 0 | 0 | 1 | 25 |

Corridas censuradas (sin colapso en el horizonte): 110 de 206.

## 1b. Causas de colapso por nivel y algoritmo

| nivel | algoritmo | causa_colapso | corridas |
|---|---|---|---|
| ALTA | AG | destino bloqueado | 7 |
| ALTA | AG | llegada tardía (a bordo) | 7 |
| ALTA | AG | llegada tardía (planificada) | 8 |
| ALTA | TABU | destino bloqueado | 6 |
| ALTA | TABU | llegada tardía (a bordo) | 6 |
| ALTA | TABU | llegada tardía (planificada) | 12 |
| BAJA | AG | destino bloqueado | 9 |
| BAJA | AG | llegada tardía (a bordo) | 2 |
| BAJA | AG | llegada tardía (planificada) | 2 |
| BAJA | TABU | destino bloqueado | 9 |
| BAJA | TABU | llegada tardía (planificada) | 1 |
| MEDIA | AG | destino bloqueado | 5 |
| MEDIA | AG | llegada tardía (a bordo) | 4 |
| MEDIA | AG | llegada tardía (planificada) | 8 |
| MEDIA | TABU | destino bloqueado | 5 |
| MEDIA | TABU | llegada tardía (a bordo) | 2 |
| MEDIA | TABU | llegada tardía (planificada) | 3 |

## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel

Log-rank propio verificado con lifelines (columna p_lifelines).

## Log-rank TABU vs. AG por nivel

| nivel | chi2 | p | colapsos_TABU | esperados_TABU | censuradas_TABU | censuradas_AG | p_lifelines |
|---|---|---|---|---|---|---|---|
| BAJA | 0.5681 | 0.451 | 10 | 11.7998 | 30 | 27 | 0.451 |
| MEDIA | 2.6114 | 0.1061 | 10 | 14.1799 | 25 | 18 | 0.1061 |
| ALTA | 0.2579 | 0.6116 | 24 | 22.2882 | 4 | 6 | 0.6116 |

## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 0.4531 | 25 | 32.5 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 0.1185 | 28.5714 | 48.5714 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 0.5 | 85.7143 | 78.5714 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.451 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.1061 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.6116 | 61.7916 | 66.05 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.1361 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | Wilcoxon (rangos con signo) | 3.063e-08 | 159.9142 | 152.5134 | 4.8526 | sí | AG |
| BAJA | km_por_pedido | Wilcoxon (rangos con signo) | 1.811e-08 | 26.4325 | 24.791 | 6.6211 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 4.299e-07 | 125867.5 | 116289 | 8.2368 | sí | AG |
| BAJA | planificador_ms_medio | Wilcoxon (rangos con signo) | 1.274e-07 | 2003.5 | 2010.15 | -0.3308 | sí | TABU |
| BAJA | cambios_de_unidad | Wilcoxon (rangos con signo) | 3.568e-08 | 28 | 298.5 | -90.6198 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.1116 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | Wilcoxon (rangos con signo) | 1.419e-06 | 162.9253 | 159.9675 | 1.849 | sí | AG |
| MEDIA | km_por_pedido | Wilcoxon (rangos con signo) | 2.298e-06 | 27.0692 | 26.4147 | 2.4777 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.006 | 162737 | 154823 | 5.1116 | sí | AG |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 2.476e-07 | 2003.3 | 2028.3 | -1.2326 | sí | TABU |
| MEDIA | cambios_de_unidad | t pareada | 3.272e-15 | 26 | 175 | -85.1429 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.702 | 99.825 | 99.825 | -1.424e-14 | no | sin diferencia |
| ALTA | costo_por_pedido | Wilcoxon (rangos con signo) | 0.3741 | 165.5206 | 163.8475 | 1.0211 | no | sin diferencia |
| ALTA | km_por_pedido | Wilcoxon (rangos con signo) | 0.1438 | 27.4046 | 27.1659 | 0.8789 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.9375 | 95228.5 | 96764 | -1.5869 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 3.316e-09 | 2002.5 | 2111.95 | -5.1824 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 5.179e-09 | 21.5 | 94 | -77.1277 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.1573 | 0 | 0 |  | no | sin diferencia |

## 4. Regla de decisión (% de colapsos -> tiempo hasta el colapso -> costo por pedido -> estabilidad)

| nivel | decision |
|---|---|
| ALTA | TABU (decide: cambios_de_unidad) |
| BAJA | AG (decide: costo_por_pedido) |
| MEDIA | AG (decide: costo_por_pedido) |


## Análisis sin las corridas con colapso por destino bloqueado

Se excluyen 41 de 206 corridas. Resultados en `sin_destino_bloqueado/resumen.md`.
