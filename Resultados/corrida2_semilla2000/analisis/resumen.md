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
| BAJA | 40 | 30 | [17, 47] | 22.5 | [11, 38] | 6 | 6 | 3 | 25 | 0.5078 | no | sin diferencia |
| MEDIA | 35 | 42.8571 | [26, 61] | 40 | [24, 58] | 9 | 6 | 5 | 15 | 1 | no | sin diferencia |
| ALTA | 28 | 85.7143 | [67, 96] | 82.1429 | [63, 94] | 22 | 2 | 1 | 3 | 1 | no | sin diferencia |

**Global (todos los niveles, McNemar exacta sobre los pares discordantes sumados):** solo TABU colapsa en 14 pares, solo AG en 9; p = 0.4049 → **sin diferencia significativa**.

**Regresión logística GEE (agrupada por tramo), colapso ~ algoritmo + nivel:** odds ratio TABU/AG = 1.28, p = 0.2954.

## 1. Estadística descriptiva por nivel y algoritmo

| nivel | algoritmo | variable | n | media | desv | mediana | min | max | censuradas |
|---|---|---|---|---|---|---|---|---|---|
| ALTA | AG | colapso_h | 28 | 66.278 | 31.6562 | 57.8583 | 14.2833 | 120 | 5 |
| ALTA | AG | pct_pedidos_en_plazo | 28 | 99.7568 | 0.2717 | 99.81 | 98.57 | 100 | 5 |
| ALTA | AG | costo_por_pedido | 28 | 163.3729 | 7.6128 | 164.3987 | 144.1215 | 179.4199 | 5 |
| ALTA | AG | km_por_pedido | 28 | 26.999 | 1.362 | 27.3126 | 23.6519 | 29.6243 | 5 |
| ALTA | AG | costo_acumulado | 28 | 97510.6786 | 53815.2566 | 85994 | 10421 | 191524 | 5 |
| ALTA | AG | planificador_ms_medio | 28 | 2101.5893 | 67.1223 | 2103.6 | 2003.2 | 2219.6 | 5 |
| ALTA | AG | cambios_de_unidad | 28 | 81.75 | 41.1138 | 74 | 11 | 168 | 5 |
| ALTA | AG | pedidos_inentregables_bloqueo | 28 | 0.1429 | 0.3563 | 0 | 0 | 1 | 5 |
| ALTA | TABU | colapso_h | 28 | 67.8851 | 29.8218 | 61.6583 | 14.2833 | 120 | 4 |
| ALTA | TABU | pct_pedidos_en_plazo | 28 | 99.7786 | 0.2295 | 99.81 | 98.77 | 100 | 4 |
| ALTA | TABU | costo_por_pedido | 28 | 164.0825 | 4.7614 | 164.4948 | 153.7778 | 173.1031 | 4 |
| ALTA | TABU | km_por_pedido | 28 | 27.3119 | 0.8532 | 27.4496 | 25.0635 | 28.5781 | 4 |
| ALTA | TABU | costo_acumulado | 28 | 100298.3214 | 49882.7874 | 87467 | 12631 | 188438 | 4 |
| ALTA | TABU | planificador_ms_medio | 28 | 2004.2464 | 1.6996 | 2004.2 | 1999.9 | 2008.1 | 4 |
| ALTA | TABU | cambios_de_unidad | 28 | 22.3214 | 12.0924 | 20 | 3 | 52 | 4 |
| ALTA | TABU | pedidos_inentregables_bloqueo | 28 | 0.1429 | 0.3563 | 0 | 0 | 1 | 4 |
| BAJA | AG | colapso_h | 40 | 102.5908 | 36.8907 | 120 | 5.9167 | 120 | 31 |
| BAJA | AG | pct_pedidos_en_plazo | 40 | 99.58 | 1.3478 | 100 | 93.75 | 100 | 31 |
| BAJA | AG | costo_por_pedido | 40 | 152.8809 | 7.8946 | 154.0965 | 131.4875 | 175.6 | 31 |
| BAJA | AG | km_por_pedido | 40 | 24.9614 | 1.4988 | 25.1086 | 20.3125 | 28.4667 | 31 |
| BAJA | AG | costo_acumulado | 40 | 107641.775 | 41973.647 | 123607.5 | 2415 | 149270 | 31 |
| BAJA | AG | planificador_ms_medio | 40 | 2014.085 | 9.5512 | 2011.4 | 1996.8 | 2042.9 | 31 |
| BAJA | AG | cambios_de_unidad | 40 | 258.525 | 121.3361 | 268 | 8 | 449 | 31 |
| BAJA | AG | pedidos_inentregables_bloqueo | 40 | 0 | 0 | 0 | 0 | 0 | 31 |
| BAJA | TABU | colapso_h | 40 | 98.3138 | 38.3213 | 120 | 5.9167 | 120 | 28 |
| BAJA | TABU | pct_pedidos_en_plazo | 40 | 99.6055 | 1.175 | 100 | 94.74 | 100 | 28 |
| BAJA | TABU | costo_por_pedido | 40 | 158.2685 | 6.3257 | 159.8729 | 143.95 | 173.8889 | 28 |
| BAJA | TABU | km_por_pedido | 40 | 26.0999 | 1.1775 | 26.2617 | 22.4756 | 28.0096 | 28 |
| BAJA | TABU | costo_acumulado | 40 | 105716.55 | 45058.1405 | 125100.5 | 2946 | 149234 | 28 |
| BAJA | TABU | planificador_ms_medio | 40 | 2003.9875 | 2.256 | 2003.65 | 2000.7 | 2012.8 | 28 |
| BAJA | TABU | cambios_de_unidad | 40 | 30.575 | 20.4085 | 25.5 | 3 | 135 | 28 |
| BAJA | TABU | pedidos_inentregables_bloqueo | 40 | 0 | 0 | 0 | 0 | 0 | 28 |
| MEDIA | AG | colapso_h | 35 | 103.5257 | 26.752 | 120 | 22.0167 | 120 | 21 |
| MEDIA | AG | pct_pedidos_en_plazo | 35 | 99.916 | 0.1461 | 100 | 99.26 | 100 | 21 |
| MEDIA | AG | costo_por_pedido | 35 | 160.9466 | 3.6164 | 161.1771 | 151.4924 | 169.3527 | 21 |
| MEDIA | AG | km_por_pedido | 35 | 26.5463 | 0.7295 | 26.5728 | 24.2537 | 27.6873 | 21 |
| MEDIA | AG | costo_acumulado | 35 | 141276.8 | 42497.1647 | 158751 | 20335 | 188316 | 21 |
| MEDIA | AG | planificador_ms_medio | 35 | 2042.74 | 26.9922 | 2030.7 | 2012.2 | 2104.6 | 21 |
| MEDIA | AG | cambios_de_unidad | 35 | 172.3143 | 76.0258 | 135 | 80 | 360 | 21 |
| MEDIA | AG | pedidos_inentregables_bloqueo | 35 | 0.1143 | 0.3228 | 0 | 0 | 1 | 21 |
| MEDIA | TABU | colapso_h | 35 | 105.5714 | 22.533 | 120 | 45.5833 | 120 | 20 |
| MEDIA | TABU | pct_pedidos_en_plazo | 35 | 99.9334 | 0.0853 | 100 | 99.7 | 100 | 20 |
| MEDIA | TABU | costo_por_pedido | 35 | 163.1964 | 2.4361 | 162.7966 | 159.0445 | 168.9855 | 20 |
| MEDIA | TABU | km_por_pedido | 35 | 27.1784 | 0.4179 | 27.182 | 26.0746 | 27.9634 | 20 |
| MEDIA | TABU | costo_acumulado | 35 | 146363.6857 | 35588.0411 | 159816 | 55968 | 189265 | 20 |
| MEDIA | TABU | planificador_ms_medio | 35 | 2003.9029 | 1.3834 | 2003.8 | 2001.3 | 2006.6 | 20 |
| MEDIA | TABU | cambios_de_unidad | 35 | 27.9143 | 11.6628 | 25 | 9 | 71 | 20 |
| MEDIA | TABU | pedidos_inentregables_bloqueo | 35 | 0.1429 | 0.355 | 0 | 0 | 1 | 20 |

Corridas censuradas (sin colapso en el horizonte): 109 de 206.

## 1b. Causas de colapso por nivel y algoritmo

| nivel | algoritmo | causa_colapso | corridas |
|---|---|---|---|
| ALTA | AG | destino bloqueado | 5 |
| ALTA | AG | llegada tardía (a bordo) | 5 |
| ALTA | AG | llegada tardía (planificada) | 13 |
| ALTA | TABU | destino bloqueado | 6 |
| ALTA | TABU | llegada tardía (a bordo) | 6 |
| ALTA | TABU | llegada tardía (planificada) | 12 |
| BAJA | AG | destino bloqueado | 8 |
| BAJA | AG | llegada tardía (a bordo) | 1 |
| BAJA | TABU | destino bloqueado | 7 |
| BAJA | TABU | llegada tardía (a bordo) | 3 |
| BAJA | TABU | llegada tardía (planificada) | 2 |
| MEDIA | AG | destino bloqueado | 5 |
| MEDIA | AG | llegada tardía (planificada) | 9 |
| MEDIA | TABU | destino bloqueado | 5 |
| MEDIA | TABU | llegada tardía (a bordo) | 5 |
| MEDIA | TABU | llegada tardía (planificada) | 5 |

## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel

Log-rank propio verificado con lifelines (columna p_lifelines).

## Log-rank TABU vs. AG por nivel

| nivel | chi2 | p | colapsos_TABU | esperados_TABU | censuradas_TABU | censuradas_AG | p_lifelines |
|---|---|---|---|---|---|---|---|
| BAJA | 0.5097 | 0.4753 | 12 | 10.3711 | 28 | 31 | 0.4753 |
| MEDIA | 0.0247 | 0.875 | 15 | 14.5776 | 20 | 21 | 0.875 |
| ALTA | 0.0078 | 0.9295 | 24 | 24.3016 | 4 | 5 | 0.9295 |

## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 0.5078 | 30 | 22.5 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 1 | 42.8571 | 40 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 1 | 85.7143 | 82.1429 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.4753 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.875 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.9295 | 61.6583 | 57.8583 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.8888 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | Wilcoxon (rangos con signo) | 9.584e-07 | 159.8729 | 154.0965 | 3.7485 | sí | AG |
| BAJA | km_por_pedido | t pareada | 1.99e-08 | 26.2617 | 25.1086 | 4.5922 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 0.012 | 125100.5 | 123607.5 | 1.2079 | sí | AG |
| BAJA | planificador_ms_medio | Wilcoxon (rangos con signo) | 5.718e-07 | 2003.65 | 2011.4 | -0.3853 | sí | TABU |
| BAJA | cambios_de_unidad | Wilcoxon (rangos con signo) | 4.833e-08 | 25.5 | 268 | -90.4851 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 1 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | t pareada | 0.0002529 | 162.7966 | 161.1771 | 1.0048 | sí | AG |
| MEDIA | km_por_pedido | Wilcoxon (rangos con signo) | 1.253e-06 | 27.182 | 26.5728 | 2.2923 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.1843 | 159816 | 158751 | 0.6709 | no | sin diferencia |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 5.821e-11 | 2003.8 | 2030.7 | -1.3247 | sí | TABU |
| MEDIA | cambios_de_unidad | Wilcoxon (rangos con signo) | 2.476e-07 | 25 | 135 | -81.4815 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.3173 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.3888 | 99.81 | 99.81 | 0 | no | sin diferencia |
| ALTA | costo_por_pedido | t pareada | 0.5071 | 164.4948 | 164.3987 | 0.0584 | no | sin diferencia |
| ALTA | km_por_pedido | t pareada | 0.1098 | 27.4496 | 27.3126 | 0.5015 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.5824 | 87467 | 85994 | 1.7129 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 2.689e-08 | 2004.2 | 2103.6 | -4.7252 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 1.153e-08 | 20 | 74 | -72.973 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |

## 4. Regla de decisión (% de colapsos -> tiempo hasta el colapso -> estabilidad -> costo por pedido)

| nivel | decision |
|---|---|
| ALTA | TABU (decide: cambios_de_unidad) |
| BAJA | TABU (decide: cambios_de_unidad) |
| MEDIA | TABU (decide: cambios_de_unidad) |


## Análisis sin las corridas con colapso por destino bloqueado

Se excluyen 36 de 206 corridas. Resultados en `sin_destino_bloqueado/resumen.md`.
