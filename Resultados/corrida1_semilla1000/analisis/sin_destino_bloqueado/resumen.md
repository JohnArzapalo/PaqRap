# Análisis SIN las corridas con colapso por "destino bloqueado" (165 de 206 corridas)

- Situaciones: ventanas (por_replica: cada réplica es una muestra de pedidos distinta, la misma para ambos algoritmos).

## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 30 | 10 | [2, 27] | 3.3333 | [0, 17] | 0 | 3 | 1 | 26 | 0.625 | no | sin diferencia |
| MEDIA | 28 | 17.8571 | [6, 37] | 28.5714 | [13, 49] | 2 | 3 | 6 | 17 | 0.5078 | no | sin diferencia |
| ALTA | 20 | 85 | [62, 97] | 90 | [68, 99] | 16 | 1 | 2 | 1 | 1 | no | sin diferencia |

**Global (todos los niveles, McNemar exacta sobre los pares discordantes sumados):** solo TABU colapsa en 7 pares, solo AG en 9; p = 0.8036 → **sin diferencia significativa**.

**Regresión logística GEE (pares como grupos), colapso ~ algoritmo + nivel:** odds ratio TABU/AG = 0.646, p = 0.3038.

## 1. Estadística descriptiva por nivel y algoritmo

| nivel | algoritmo | variable | n | media | desv | mediana | min | max | censuradas |
|---|---|---|---|---|---|---|---|---|---|
| ALTA | AG | colapso_h | 22 | 69.531 | 26.0392 | 61.65 | 34.35 | 120 | 2 |
| ALTA | AG | pct_pedidos_en_plazo | 22 | 99.82 | 0.0923 | 99.825 | 99.64 | 100 | 2 |
| ALTA | AG | costo_por_pedido | 22 | 163.7024 | 6.1231 | 164.2358 | 153.5921 | 173.3044 | 2 |
| ALTA | AG | km_por_pedido | 22 | 27.0744 | 1.1292 | 27.2461 | 25 | 29.0826 | 2 |
| ALTA | AG | costo_acumulado | 22 | 103461.9545 | 44236.4202 | 98157.5 | 42545 | 183996 | 2 |
| ALTA | AG | planificador_ms_medio | 22 | 2110.5182 | 42.0211 | 2112.65 | 2019 | 2185.9 | 2 |
| ALTA | AG | cambios_de_unidad | 22 | 103.5909 | 49.3131 | 92.5 | 14 | 216 | 2 |
| ALTA | AG | pedidos_inentregables_bloqueo | 22 | 0.1364 | 0.3513 | 0 | 0 | 1 | 2 |
| ALTA | TABU | colapso_h | 21 | 71.2325 | 25.8767 | 64.3167 | 37.6667 | 120 | 3 |
| ALTA | TABU | pct_pedidos_en_plazo | 21 | 99.8352 | 0.0878 | 99.82 | 99.7 | 100 | 3 |
| ALTA | TABU | costo_por_pedido | 21 | 164.1678 | 4.6231 | 164.1089 | 157.705 | 172.3635 | 3 |
| ALTA | TABU | km_por_pedido | 21 | 27.36 | 0.7956 | 27.3073 | 26 | 28.9462 | 3 |
| ALTA | TABU | costo_acumulado | 21 | 106254.2857 | 42543.5488 | 92763 | 55270 | 193839 | 3 |
| ALTA | TABU | planificador_ms_medio | 21 | 2004.2667 | 1.4934 | 2004.3 | 2001.9 | 2007.2 | 3 |
| ALTA | TABU | cambios_de_unidad | 21 | 20.1905 | 12.1557 | 17 | 5 | 50 | 3 |
| ALTA | TABU | pedidos_inentregables_bloqueo | 21 | 0.0952 | 0.3008 | 0 | 0 | 1 | 3 |
| BAJA | AG | colapso_h | 32 | 116.0594 | 15.8699 | 120 | 43.65 | 120 | 30 |
| BAJA | AG | pct_pedidos_en_plazo | 32 | 99.9772 | 0.097 | 100 | 99.49 | 100 | 30 |
| BAJA | AG | costo_por_pedido | 32 | 153.4882 | 4.0043 | 153.4485 | 146.0796 | 161.2345 | 30 |
| BAJA | AG | km_por_pedido | 32 | 25.0409 | 0.9342 | 25.042 | 22.6769 | 27.057 | 30 |
| BAJA | AG | costo_acumulado | 32 | 120733 | 21822.0745 | 123673.5 | 28559 | 145328 | 30 |
| BAJA | AG | planificador_ms_medio | 32 | 2014.725 | 7.5542 | 2013.45 | 2005.2 | 2036.5 | 30 |
| BAJA | AG | cambios_de_unidad | 32 | 318.0312 | 82.5268 | 327 | 100 | 441 | 30 |
| BAJA | AG | pedidos_inentregables_bloqueo | 32 | 0 | 0 | 0 | 0 | 0 | 30 |
| BAJA | TABU | colapso_h | 32 | 115.1271 | 15.7836 | 120 | 59.7833 | 120 | 29 |
| BAJA | TABU | pct_pedidos_en_plazo | 32 | 99.9781 | 0.0703 | 100 | 99.72 | 100 | 29 |
| BAJA | TABU | costo_por_pedido | 32 | 160.0909 | 3.2025 | 160.8691 | 153.6817 | 165.6135 | 29 |
| BAJA | TABU | km_por_pedido | 32 | 26.6059 | 0.6255 | 26.6909 | 25.4636 | 28.1074 | 29 |
| BAJA | TABU | costo_acumulado | 32 | 124001.2188 | 20536.1426 | 126447 | 58105 | 149549 | 29 |
| BAJA | TABU | planificador_ms_medio | 32 | 2003.4125 | 1.4591 | 2003.35 | 2000.9 | 2006.5 | 29 |
| BAJA | TABU | cambios_de_unidad | 32 | 33.0625 | 12.649 | 30.5 | 12 | 80 | 29 |
| BAJA | TABU | pedidos_inentregables_bloqueo | 32 | 0 | 0 | 0 | 0 | 0 | 29 |
| MEDIA | AG | colapso_h | 30 | 103.7011 | 29.4064 | 120 | 35.9333 | 120 | 20 |
| MEDIA | AG | pct_pedidos_en_plazo | 30 | 99.927 | 0.1219 | 100 | 99.61 | 100 | 20 |
| MEDIA | AG | costo_por_pedido | 30 | 160.4948 | 3.37 | 159.8998 | 154.8774 | 169.2657 | 20 |
| MEDIA | AG | km_por_pedido | 30 | 26.4274 | 0.7086 | 26.4147 | 24.815 | 27.8799 | 20 |
| MEDIA | AG | costo_acumulado | 30 | 140395.4667 | 43143.5184 | 156258 | 39653 | 184190 | 20 |
| MEDIA | AG | planificador_ms_medio | 30 | 2040.2467 | 28.7324 | 2026.85 | 2009.1 | 2123.1 | 20 |
| MEDIA | AG | cambios_de_unidad | 30 | 202.8 | 93.9998 | 187 | 58 | 403 | 20 |
| MEDIA | AG | pedidos_inentregables_bloqueo | 30 | 0.1 | 0.3051 | 0 | 0 | 1 | 20 |
| MEDIA | TABU | colapso_h | 28 | 112.7304 | 23.0284 | 120 | 27.8833 | 120 | 23 |
| MEDIA | TABU | pct_pedidos_en_plazo | 28 | 99.9536 | 0.133 | 100 | 99.39 | 100 | 23 |
| MEDIA | TABU | costo_por_pedido | 28 | 162.1032 | 3.4476 | 162.052 | 150.8659 | 169.5953 | 23 |
| MEDIA | TABU | km_por_pedido | 28 | 26.9307 | 0.5433 | 27.0274 | 25.5122 | 27.8575 | 23 |
| MEDIA | TABU | costo_acumulado | 28 | 157908.4286 | 37358.2229 | 164820.5 | 24742 | 190745 | 23 |
| MEDIA | TABU | planificador_ms_medio | 28 | 2003.3321 | 1.5597 | 2003.2 | 2000.6 | 2007.5 | 23 |
| MEDIA | TABU | cambios_de_unidad | 28 | 29.1071 | 12.6413 | 28.5 | 10 | 69 | 23 |
| MEDIA | TABU | pedidos_inentregables_bloqueo | 28 | 0.1429 | 0.3563 | 0 | 0 | 1 | 23 |

Corridas censuradas (sin colapso en el horizonte): 107 de 165.

## 1b. Causas de colapso por nivel y algoritmo

| nivel | algoritmo | causa_colapso | corridas |
|---|---|---|---|
| ALTA | AG | llegada tardía (a bordo) | 6 |
| ALTA | AG | llegada tardía (planificada) | 14 |
| ALTA | TABU | llegada tardía (a bordo) | 6 |
| ALTA | TABU | llegada tardía (planificada) | 12 |
| BAJA | AG | llegada tardía (a bordo) | 2 |
| BAJA | TABU | llegada tardía (a bordo) | 3 |
| MEDIA | AG | llegada tardía (a bordo) | 3 |
| MEDIA | AG | llegada tardía (planificada) | 7 |
| MEDIA | TABU | llegada tardía (a bordo) | 2 |
| MEDIA | TABU | llegada tardía (planificada) | 3 |

## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel

Log-rank propio verificado con lifelines (columna p_lifelines).

## Log-rank TABU vs. AG por nivel

| nivel | chi2 | p | colapsos_TABU | esperados_TABU | censuradas_TABU | censuradas_AG | p_lifelines |
|---|---|---|---|---|---|---|---|
| BAJA | 0.2002 | 0.6545 | 3 | 2.4997 | 29 | 30 | 0.6545 |
| MEDIA | 1.7879 | 0.1812 | 5 | 7.5837 | 23 | 20 | 0.1812 |
| ALTA | 0.0865 | 0.7686 | 18 | 18.9044 | 3 | 2 | 0.7686 |

## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 0.625 | 10 | 3.3333 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 0.5078 | 17.8571 | 28.5714 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 1 | 85 | 90 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.6545 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.1812 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.7686 | 64.3167 | 61.65 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.715 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | t pareada | 5.613e-11 | 161.0394 | 153.6327 | 4.821 | sí | AG |
| BAJA | km_por_pedido | t pareada | 5.085e-11 | 26.7481 | 25.1218 | 6.4735 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 0.0016 | 125931 | 123673.5 | 1.8254 | sí | AG |
| BAJA | planificador_ms_medio | t pareada | 1.297e-09 | 2003.35 | 2013.45 | -0.5016 | sí | TABU |
| BAJA | cambios_de_unidad | t pareada | 8.247e-18 | 31.5 | 327 | -90.367 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.3503 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | Wilcoxon (rangos con signo) | 0.0218 | 162.052 | 159.8998 | 1.346 | sí | AG |
| MEDIA | km_por_pedido | t pareada | 0.0152 | 27.0274 | 26.443 | 2.21 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.0024 | 164820.5 | 156516 | 5.3058 | sí | AG |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 7.451e-09 | 2003.2 | 2028.35 | -1.2399 | sí | TABU |
| MEDIA | cambios_de_unidad | t pareada | 2.139e-11 | 28.5 | 187 | -84.7594 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.3173 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.9245 | 99.83 | 99.825 | 0.005 | no | sin diferencia |
| ALTA | costo_por_pedido | t pareada | 0.8702 | 163.0806 | 164.8287 | -1.0605 | no | sin diferencia |
| ALTA | km_por_pedido | t pareada | 0.4697 | 27.1978 | 27.5007 | -1.1014 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.8124 | 94539.5 | 98157.5 | -3.6859 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 2.016e-09 | 2004.3 | 2110.05 | -5.0117 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 2.66e-07 | 16.5 | 92.5 | -82.1622 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 1 | 0 | 0 |  | no | sin diferencia |

## 4. Regla de decisión (% de colapsos -> tiempo hasta el colapso -> costo por pedido -> estabilidad)

| nivel | decision |
|---|---|
| ALTA | TABU (decide: cambios_de_unidad) |
| BAJA | AG (decide: costo_por_pedido) |
| MEDIA | AG (decide: costo_por_pedido) |
