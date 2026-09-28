# Análisis SIN las corridas con colapso por "destino bloqueado" (170 de 206 corridas)

- Situaciones: ventanas (por_replica: cada réplica es una muestra de pedidos distinta, la misma para ambos algoritmos).

## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 31 | 16.129 | [5, 34] | 3.2258 | [0, 17] | 0 | 5 | 1 | 25 | 0.2188 | no | sin diferencia |
| MEDIA | 28 | 32.1429 | [16, 52] | 25 | [11, 45] | 3 | 6 | 4 | 15 | 0.7539 | no | sin diferencia |
| ALTA | 21 | 85.7143 | [64, 97] | 80.9524 | [58, 95] | 17 | 1 | 0 | 3 | 1 | no | sin diferencia |

**Global (todos los niveles, McNemar exacta sobre los pares discordantes sumados):** solo TABU colapsa en 12 pares, solo AG en 5; p = 0.1435 → **sin diferencia significativa**.

**Regresión logística GEE (agrupada por tramo), colapso ~ algoritmo + nivel:** odds ratio TABU/AG = 1.61, p = 0.1545.

## 1. Estadística descriptiva por nivel y algoritmo

| nivel | algoritmo | variable | n | media | desv | mediana | min | max | censuradas |
|---|---|---|---|---|---|---|---|---|---|
| ALTA | AG | colapso_h | 23 | 70.7167 | 31.0087 | 67.3833 | 27.8 | 120 | 5 |
| ALTA | AG | pct_pedidos_en_plazo | 23 | 99.8187 | 0.1304 | 99.83 | 99.52 | 100 | 5 |
| ALTA | AG | costo_por_pedido | 23 | 164.7589 | 6.4085 | 164.9052 | 152.4845 | 179.4199 | 5 |
| ALTA | AG | km_por_pedido | 23 | 27.2712 | 1.073 | 27.378 | 24.8687 | 29.6243 | 5 |
| ALTA | AG | costo_acumulado | 23 | 105843.4348 | 52401.1355 | 97367 | 34027 | 191524 | 5 |
| ALTA | AG | planificador_ms_medio | 23 | 2111.0217 | 65.1673 | 2112 | 2011 | 2219.6 | 5 |
| ALTA | AG | cambios_de_unidad | 23 | 81.3043 | 41.2701 | 70 | 11 | 168 | 5 |
| ALTA | AG | pedidos_inentregables_bloqueo | 23 | 0.1304 | 0.3444 | 0 | 0 | 1 | 5 |
| ALTA | TABU | colapso_h | 22 | 74.7326 | 28.6707 | 63.925 | 36.7667 | 120 | 4 |
| ALTA | TABU | pct_pedidos_en_plazo | 22 | 99.8418 | 0.0982 | 99.82 | 99.68 | 100 | 4 |
| ALTA | TABU | costo_por_pedido | 22 | 164.6949 | 4.5916 | 164.779 | 155.7848 | 173.1031 | 4 |
| ALTA | TABU | km_por_pedido | 22 | 27.4765 | 0.6607 | 27.5141 | 26.3291 | 28.5781 | 4 |
| ALTA | TABU | costo_acumulado | 22 | 112494.9545 | 47341.2412 | 92060.5 | 49228 | 188438 | 4 |
| ALTA | TABU | planificador_ms_medio | 22 | 2004.4727 | 1.7425 | 2004.85 | 1999.9 | 2008.1 | 4 |
| ALTA | TABU | cambios_de_unidad | 22 | 22.8636 | 12.5178 | 20 | 5 | 52 | 4 |
| ALTA | TABU | pedidos_inentregables_bloqueo | 22 | 0.1364 | 0.3513 | 0 | 0 | 1 | 4 |
| BAJA | AG | colapso_h | 32 | 116.6224 | 19.1066 | 120 | 11.9167 | 120 | 31 |
| BAJA | AG | pct_pedidos_en_plazo | 32 | 99.9237 | 0.4313 | 100 | 97.56 | 100 | 31 |
| BAJA | AG | costo_por_pedido | 32 | 153.8119 | 5.4431 | 154.2221 | 133.35 | 164.2134 | 31 |
| BAJA | AG | km_por_pedido | 32 | 25.215 | 0.8769 | 25.1965 | 23.3668 | 26.9153 | 31 |
| BAJA | AG | costo_acumulado | 32 | 122523.75 | 24115.851 | 124688.5 | 5334 | 149270 | 31 |
| BAJA | AG | planificador_ms_medio | 32 | 2015.0687 | 8.9306 | 2012.15 | 2000.9 | 2042.9 | 31 |
| BAJA | AG | cambios_de_unidad | 32 | 297.875 | 93.0466 | 301 | 44 | 449 | 31 |
| BAJA | AG | pedidos_inentregables_bloqueo | 32 | 0 | 0 | 0 | 0 | 0 | 31 |
| BAJA | TABU | colapso_h | 33 | 110.2318 | 26.2915 | 120 | 19.65 | 120 | 28 |
| BAJA | TABU | pct_pedidos_en_plazo | 33 | 99.9118 | 0.2841 | 100 | 98.77 | 100 | 28 |
| BAJA | TABU | costo_por_pedido | 33 | 158.8536 | 5.18 | 160.0789 | 143.95 | 167.4215 | 28 |
| BAJA | TABU | km_por_pedido | 33 | 26.3586 | 0.9636 | 26.3395 | 23.4556 | 28.0096 | 28 |
| BAJA | TABU | costo_acumulado | 33 | 119064.1212 | 32697.9797 | 127598 | 11516 | 149234 | 28 |
| BAJA | TABU | planificador_ms_medio | 33 | 2003.8212 | 1.6361 | 2003.8 | 2000.7 | 2007.8 | 28 |
| BAJA | TABU | cambios_de_unidad | 33 | 33.4545 | 21.1351 | 31 | 11 | 135 | 28 |
| BAJA | TABU | pedidos_inentregables_bloqueo | 33 | 0 | 0 | 0 | 0 | 0 | 28 |
| MEDIA | AG | colapso_h | 30 | 109.5217 | 19.7165 | 120 | 45.0667 | 120 | 21 |
| MEDIA | AG | pct_pedidos_en_plazo | 30 | 99.9517 | 0.0815 | 100 | 99.69 | 100 | 21 |
| MEDIA | AG | costo_por_pedido | 30 | 161.7543 | 2.8752 | 161.6073 | 157.2932 | 169.3527 | 21 |
| MEDIA | AG | km_por_pedido | 30 | 26.7203 | 0.4784 | 26.6254 | 25.8883 | 27.6873 | 21 |
| MEDIA | AG | costo_acumulado | 30 | 150932.5 | 32251.0591 | 161477 | 52412 | 188316 | 21 |
| MEDIA | AG | planificador_ms_medio | 30 | 2045.19 | 28.1063 | 2037.3 | 2012.2 | 2104.6 | 21 |
| MEDIA | AG | cambios_de_unidad | 30 | 183.5333 | 76.1454 | 159.5 | 81 | 360 | 21 |
| MEDIA | AG | pedidos_inentregables_bloqueo | 30 | 0.1 | 0.3051 | 0 | 0 | 1 | 21 |
| MEDIA | TABU | colapso_h | 30 | 108.1139 | 21.6205 | 120 | 45.5833 | 120 | 20 |
| MEDIA | TABU | pct_pedidos_en_plazo | 30 | 99.9467 | 0.0837 | 100 | 99.7 | 100 | 20 |
| MEDIA | TABU | costo_por_pedido | 30 | 163.1809 | 2.4762 | 162.6504 | 159.0445 | 168.9855 | 20 |
| MEDIA | TABU | km_por_pedido | 30 | 27.2004 | 0.3818 | 27.2074 | 26.4623 | 27.9634 | 20 |
| MEDIA | TABU | costo_acumulado | 30 | 151085.3333 | 33857.8763 | 161215 | 55968 | 189265 | 20 |
| MEDIA | TABU | planificador_ms_medio | 30 | 2003.88 | 1.3242 | 2003.8 | 2001.3 | 2006.6 | 20 |
| MEDIA | TABU | cambios_de_unidad | 30 | 28.0333 | 12.3553 | 25 | 9 | 71 | 20 |
| MEDIA | TABU | pedidos_inentregables_bloqueo | 30 | 0.1333 | 0.3457 | 0 | 0 | 1 | 20 |

Corridas censuradas (sin colapso en el horizonte): 109 de 170.

## 1b. Causas de colapso por nivel y algoritmo

| nivel | algoritmo | causa_colapso | corridas |
|---|---|---|---|
| ALTA | AG | llegada tardía (a bordo) | 5 |
| ALTA | AG | llegada tardía (planificada) | 13 |
| ALTA | TABU | llegada tardía (a bordo) | 6 |
| ALTA | TABU | llegada tardía (planificada) | 12 |
| BAJA | AG | llegada tardía (a bordo) | 1 |
| BAJA | TABU | llegada tardía (a bordo) | 3 |
| BAJA | TABU | llegada tardía (planificada) | 2 |
| MEDIA | AG | llegada tardía (planificada) | 9 |
| MEDIA | TABU | llegada tardía (a bordo) | 5 |
| MEDIA | TABU | llegada tardía (planificada) | 5 |

## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel

Log-rank propio verificado con lifelines (columna p_lifelines).

## Log-rank TABU vs. AG por nivel

| nivel | chi2 | p | colapsos_TABU | esperados_TABU | censuradas_TABU | censuradas_AG | p_lifelines |
|---|---|---|---|---|---|---|---|
| BAJA | 2.6509 | 0.1035 | 5 | 3.0064 | 28 | 31 | 0.1035 |
| MEDIA | 0.0751 | 0.7841 | 10 | 9.4036 | 20 | 21 | 0.7841 |
| ALTA | 0.0821 | 0.7744 | 18 | 18.8562 | 4 | 5 | 0.7744 |

## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 0.2188 | 16.129 | 3.2258 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 0.7539 | 32.1429 | 25 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 1 | 85.7143 | 80.9524 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.1035 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.7841 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.7744 | 63.925 | 67.3833 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.3454 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | Wilcoxon (rangos con signo) | 3.57e-05 | 160.0502 | 154.3411 | 3.699 | sí | AG |
| BAJA | km_por_pedido | t pareada | 3.88e-08 | 26.3395 | 25.2017 | 4.515 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 0.0552 | 127069 | 124410 | 2.1373 | no | sin diferencia |
| BAJA | planificador_ms_medio | Wilcoxon (rangos con signo) | 1.915e-06 | 2003.8 | 2012.7 | -0.4422 | sí | TABU |
| BAJA | cambios_de_unidad | t pareada | 2.843e-16 | 27 | 298 | -90.9396 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.7217 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | t pareada | 0.0008514 | 162.658 | 161.2825 | 0.8529 | sí | AG |
| MEDIA | km_por_pedido | t pareada | 1.41e-05 | 27.2074 | 26.6254 | 2.1857 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.4652 | 161215 | 162016.5 | -0.4947 | no | sin diferencia |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 7.451e-09 | 2004 | 2037.3 | -1.6345 | sí | TABU |
| MEDIA | cambios_de_unidad | t pareada | 3.85e-11 | 25 | 159.5 | -84.326 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.3173 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.6013 | 99.82 | 99.83 | -0.01 | no | sin diferencia |
| ALTA | costo_por_pedido | t pareada | 0.6172 | 164.5747 | 165.5204 | -0.5714 | no | sin diferencia |
| ALTA | km_por_pedido | t pareada | 0.557 | 27.5006 | 27.378 | 0.4476 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.7854 | 91305 | 97367 | -6.2259 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 1.547e-07 | 2004.9 | 2114.7 | -5.1922 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 1.998e-06 | 20 | 70 | -71.4286 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |

## 4. Regla de decisión (% de colapsos -> tiempo hasta el colapso -> costo por pedido -> estabilidad)

| nivel | decision |
|---|---|
| ALTA | TABU (decide: cambios_de_unidad) |
| BAJA | AG (decide: costo_por_pedido) |
| MEDIA | AG (decide: costo_por_pedido) |
