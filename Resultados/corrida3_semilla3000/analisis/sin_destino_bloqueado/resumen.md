# Análisis SIN las corridas con colapso por "destino bloqueado" (165 de 206 corridas)

- Situaciones: ventanas (por_replica: cada réplica es una muestra de pedidos distinta, la misma para ambos algoritmos).

## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 29 | 3.4483 | [0, 18] | 10.3448 | [2, 27] | 0 | 1 | 3 | 25 | 0.625 | no | sin diferencia |
| MEDIA | 28 | 17.8571 | [6, 37] | 35.7143 | [19, 56] | 1 | 4 | 9 | 14 | 0.2668 | no | sin diferencia |
| ALTA | 20 | 80 | [56, 94] | 75 | [51, 91] | 15 | 1 | 0 | 4 | 1 | no | sin diferencia |

**Global (todos los niveles, McNemar exacta sobre los pares discordantes sumados):** solo TABU colapsa en 6 pares, solo AG en 12; p = 0.2379 → **sin diferencia significativa**.

**Regresión logística GEE (agrupada por tramo), colapso ~ algoritmo + nivel:** odds ratio TABU/AG = 0.532, p = 0.09158.

## 1. Estadística descriptiva por nivel y algoritmo

| nivel | algoritmo | variable | n | media | desv | mediana | min | max | censuradas |
|---|---|---|---|---|---|---|---|---|---|
| ALTA | AG | colapso_h | 21 | 80.3492 | 33.0588 | 73.6833 | 36.5 | 120 | 6 |
| ALTA | AG | pct_pedidos_en_plazo | 21 | 99.8557 | 0.1154 | 99.86 | 99.67 | 100 | 6 |
| ALTA | AG | costo_por_pedido | 21 | 164.7514 | 5.4023 | 164.3284 | 157.6491 | 180.3414 | 6 |
| ALTA | AG | km_por_pedido | 21 | 27.2727 | 0.8171 | 27.2208 | 25.9906 | 29.5076 | 6 |
| ALTA | AG | costo_acumulado | 21 | 121851.2857 | 54325.9182 | 110512 | 50065 | 189635 | 6 |
| ALTA | AG | planificador_ms_medio | 21 | 2116.0952 | 54.9079 | 2129.1 | 2012.2 | 2222.3 | 6 |
| ALTA | AG | cambios_de_unidad | 21 | 103.9524 | 50.5059 | 100 | 16 | 244 | 6 |
| ALTA | AG | pedidos_inentregables_bloqueo | 21 | 0.1905 | 0.4024 | 0 | 0 | 1 | 6 |
| ALTA | TABU | colapso_h | 22 | 74.2962 | 29.9263 | 67.0666 | 29.35 | 120 | 4 |
| ALTA | TABU | pct_pedidos_en_plazo | 22 | 99.8345 | 0.1115 | 99.83 | 99.57 | 100 | 4 |
| ALTA | TABU | costo_por_pedido | 22 | 165.2743 | 4.5101 | 166.1643 | 155.434 | 171.9831 | 4 |
| ALTA | TABU | km_por_pedido | 22 | 27.5248 | 0.7002 | 27.536 | 26.1524 | 28.7895 | 4 |
| ALTA | TABU | costo_acumulado | 22 | 112513.0455 | 50539.0247 | 96889.5 | 39299 | 194435 | 4 |
| ALTA | TABU | planificador_ms_medio | 22 | 2002.6818 | 1.4292 | 2002.5 | 1999.9 | 2005.5 | 4 |
| ALTA | TABU | cambios_de_unidad | 22 | 19.6364 | 8.8562 | 21.5 | 4 | 39 | 4 |
| ALTA | TABU | pedidos_inentregables_bloqueo | 22 | 0.0909 | 0.2942 | 0 | 0 | 1 | 4 |
| BAJA | AG | colapso_h | 31 | 116.1285 | 12.3089 | 120 | 68.5 | 120 | 27 |
| BAJA | AG | pct_pedidos_en_plazo | 31 | 99.9765 | 0.0635 | 100 | 99.77 | 100 | 27 |
| BAJA | AG | costo_por_pedido | 31 | 153.6373 | 3.7292 | 152.6522 | 148.6397 | 160.0427 | 27 |
| BAJA | AG | km_por_pedido | 31 | 25.155 | 0.8116 | 25.0772 | 23.6983 | 26.6278 | 27 |
| BAJA | AG | costo_acumulado | 31 | 120472.3548 | 17767.5869 | 120910 | 67824 | 145677 | 27 |
| BAJA | AG | planificador_ms_medio | 31 | 2014.1419 | 6.2481 | 2012.3 | 2007 | 2031.9 | 27 |
| BAJA | AG | cambios_de_unidad | 31 | 316.7742 | 71.0543 | 325 | 160 | 459 | 27 |
| BAJA | AG | pedidos_inentregables_bloqueo | 31 | 0 | 0 | 0 | 0 | 0 | 27 |
| BAJA | TABU | colapso_h | 31 | 118.3618 | 9.1209 | 120 | 69.2167 | 120 | 30 |
| BAJA | TABU | pct_pedidos_en_plazo | 31 | 99.9932 | 0.0377 | 100 | 99.79 | 100 | 30 |
| BAJA | TABU | costo_por_pedido | 31 | 159.7278 | 3.6842 | 160.3329 | 150.6713 | 166.3814 | 30 |
| BAJA | TABU | km_por_pedido | 31 | 26.5687 | 0.7042 | 26.4693 | 25.0813 | 28.0561 | 30 |
| BAJA | TABU | costo_acumulado | 31 | 127567.0968 | 13751.5789 | 127801 | 75480 | 150076 | 30 |
| BAJA | TABU | planificador_ms_medio | 31 | 2004.1161 | 2.5285 | 2003.7 | 2001.2 | 2015.6 | 30 |
| BAJA | TABU | cambios_de_unidad | 31 | 30.3226 | 11.4233 | 28 | 7 | 55 | 30 |
| BAJA | TABU | pedidos_inentregables_bloqueo | 31 | 0 | 0 | 0 | 0 | 0 | 30 |
| MEDIA | AG | colapso_h | 30 | 109.7294 | 17.3377 | 120 | 60.4 | 120 | 18 |
| MEDIA | AG | pct_pedidos_en_plazo | 30 | 99.9463 | 0.0699 | 100 | 99.81 | 100 | 18 |
| MEDIA | AG | costo_por_pedido | 30 | 160.5951 | 2.7652 | 160.1125 | 155.3276 | 168.561 | 18 |
| MEDIA | AG | km_por_pedido | 30 | 26.5359 | 0.496 | 26.4451 | 25.274 | 27.5524 | 18 |
| MEDIA | AG | costo_acumulado | 30 | 150405.8 | 29090.7961 | 157053 | 80615 | 185474 | 18 |
| MEDIA | AG | planificador_ms_medio | 30 | 2041.4467 | 28.0392 | 2029.3 | 2008.3 | 2111.9 | 18 |
| MEDIA | AG | cambios_de_unidad | 30 | 181.1667 | 68.549 | 181.5 | 48 | 299 | 18 |
| MEDIA | AG | pedidos_inentregables_bloqueo | 30 | 0.1 | 0.3051 | 0 | 0 | 1 | 18 |
| MEDIA | TABU | colapso_h | 30 | 114.2806 | 16.3977 | 120 | 59.8833 | 120 | 25 |
| MEDIA | TABU | pct_pedidos_en_plazo | 30 | 99.9747 | 0.0606 | 100 | 99.79 | 100 | 25 |
| MEDIA | TABU | costo_por_pedido | 30 | 163.1074 | 3.2934 | 163.2284 | 155.922 | 173.3853 | 25 |
| MEDIA | TABU | km_por_pedido | 30 | 27.1345 | 0.484 | 27.1109 | 26.3204 | 28.3484 | 25 |
| MEDIA | TABU | costo_acumulado | 30 | 160261.9333 | 27615.6582 | 164715.5 | 76290 | 189996 | 25 |
| MEDIA | TABU | planificador_ms_medio | 30 | 2003.6467 | 1.3758 | 2003.35 | 2001 | 2006.5 | 25 |
| MEDIA | TABU | cambios_de_unidad | 30 | 25.9 | 11.186 | 27 | 9 | 54 | 25 |
| MEDIA | TABU | pedidos_inentregables_bloqueo | 30 | 0.1333 | 0.3457 | 0 | 0 | 1 | 25 |

Corridas censuradas (sin colapso en el horizonte): 110 de 165.

## 1b. Causas de colapso por nivel y algoritmo

| nivel | algoritmo | causa_colapso | corridas |
|---|---|---|---|
| ALTA | AG | llegada tardía (a bordo) | 7 |
| ALTA | AG | llegada tardía (planificada) | 8 |
| ALTA | TABU | llegada tardía (a bordo) | 6 |
| ALTA | TABU | llegada tardía (planificada) | 12 |
| BAJA | AG | llegada tardía (a bordo) | 2 |
| BAJA | AG | llegada tardía (planificada) | 2 |
| BAJA | TABU | llegada tardía (planificada) | 1 |
| MEDIA | AG | llegada tardía (a bordo) | 4 |
| MEDIA | AG | llegada tardía (planificada) | 8 |
| MEDIA | TABU | llegada tardía (a bordo) | 2 |
| MEDIA | TABU | llegada tardía (planificada) | 3 |

## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel

Log-rank propio verificado con lifelines (columna p_lifelines).

## Log-rank TABU vs. AG por nivel

| nivel | chi2 | p | colapsos_TABU | esperados_TABU | censuradas_TABU | censuradas_AG | p_lifelines |
|---|---|---|---|---|---|---|---|
| BAJA | 1.883 | 0.17 | 1 | 2.5339 | 30 | 27 | 0.17 |
| MEDIA | 3.771 | 0.0521 | 5 | 8.9891 | 25 | 18 | 0.0521 |
| ALTA | 0.6463 | 0.4214 | 18 | 15.7037 | 4 | 6 | 0.4214 |

## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 0.625 | 3.4483 | 10.3448 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 0.2668 | 17.8571 | 35.7143 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 1 | 80 | 75 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.17 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.0521 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.4214 | 67.0666 | 73.6833 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.4652 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | t pareada | 1.893e-09 | 160.3329 | 152.5737 | 5.0856 | sí | AG |
| BAJA | km_por_pedido | t pareada | 4.245e-10 | 26.4817 | 25.0013 | 5.9214 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 6.478e-06 | 127646 | 120910 | 5.5711 | sí | AG |
| BAJA | planificador_ms_medio | t pareada | 3.501e-08 | 2003.9 | 2012.3 | -0.4174 | sí | TABU |
| BAJA | cambios_de_unidad | t pareada | 6.046e-19 | 29 | 330 | -91.2121 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.3457 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | t pareada | 0.0002157 | 163.2284 | 160.368 | 1.7836 | sí | AG |
| MEDIA | km_por_pedido | t pareada | 5.862e-05 | 27.1109 | 26.4976 | 2.3145 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.0136 | 163993.5 | 159227 | 2.9935 | sí | AG |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 7.451e-09 | 2003.35 | 2029.3 | -1.2788 | sí | TABU |
| MEDIA | cambios_de_unidad | t pareada | 2.557e-13 | 28 | 181.5 | -84.573 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.5754 | 99.83 | 99.855 | -0.025 | no | sin diferencia |
| ALTA | costo_por_pedido | t pareada | 0.702 | 166.4331 | 164.7238 | 1.0377 | no | sin diferencia |
| ALTA | km_por_pedido | t pareada | 0.1877 | 27.5841 | 27.2398 | 1.2641 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.8695 | 96889.5 | 109065 | -11.1635 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 2.538e-08 | 2002.45 | 2125.7 | -5.7981 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 5.953e-07 | 21.5 | 101.5 | -78.8177 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.3173 | 0 | 0 |  | no | sin diferencia |

## 4. Regla de decisión (% de colapsos -> tiempo hasta el colapso -> costo por pedido -> estabilidad)

| nivel | decision |
|---|---|
| ALTA | TABU (decide: cambios_de_unidad) |
| BAJA | AG (decide: costo_por_pedido) |
| MEDIA | AG (decide: costo_por_pedido) |
