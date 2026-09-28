# Análisis del experimento numérico PaqRap (SIMULACIÓN)

- Archivos: experimento_oficial_combinado.csv
- Datos de ventas: ventas.202609.txt
- Corridas: 618; niveles: MEDIA, BAJA, ALTA; algoritmos: AG, TABU
- Modo de parada: tiempo; α = 0.05
- statsmodels: sí; lifelines: sí

- Situaciones: ventanas (por_replica: cada réplica es una muestra de pedidos distinta, la misma para ambos algoritmos).

## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 120 | 27.5 | [20, 36] | 26.6667 | [19, 36] | 21 | 12 | 11 | 76 | 1 | no | sin diferencia |
| MEDIA | 105 | 35.2381 | [26, 45] | 43.8095 | [34, 54] | 24 | 13 | 22 | 46 | 0.1755 | no | sin diferencia |
| ALTA | 84 | 86.9048 | [78, 93] | 84.5238 | [75, 91] | 68 | 5 | 3 | 8 | 0.7266 | no | sin diferencia |

## 0b. % de colapsos por corrida (misma situación, otra semilla de los algoritmos)

| corrida (semilla base) | pares | %colapso_TABU | %colapso_AG | solo_TABU | solo_AG | p_McNemar | significativo |
|---|---|---|---|---|---|---|---|
| 1000 | 103 | 46.6019 | 49.5146 | 8 | 11 | 0.6476 | no |
| 2000 | 103 | 49.5146 | 44.6602 | 14 | 9 | 0.4049 | no |
| 3000 | 103 | 42.7184 | 50.4854 | 8 | 16 | 0.1516 | no |

**Con varias corridas, los tramos se repiten**: la McNemar global de abajo los cuenta como pares independientes y es optimista. La prueba combinada válida es la **GEE agrupada por tramo**.

**Global (todos los niveles, McNemar exacta sobre los pares discordantes sumados):** solo TABU colapsa en 30 pares, solo AG en 36; p = 0.5386 → **sin diferencia significativa**.

**Regresión logística GEE (agrupada por tramo), colapso ~ algoritmo + nivel:** odds ratio TABU/AG = 0.903, p = 0.4602.

## 1. Estadística descriptiva por nivel y algoritmo

| nivel | algoritmo | variable | n | media | desv | mediana | min | max | censuradas |
|---|---|---|---|---|---|---|---|---|---|
| ALTA | AG | colapso_h | 84 | 67.2143 | 30.9938 | 61.65 | 14.2833 | 120 | 13 |
| ALTA | AG | pct_pedidos_en_plazo | 84 | 99.7615 | 0.2667 | 99.805 | 98.55 | 100 | 13 |
| ALTA | AG | costo_por_pedido | 84 | 162.8466 | 7.2222 | 164.0404 | 141.3167 | 180.3414 | 13 |
| ALTA | AG | km_por_pedido | 84 | 26.8957 | 1.3976 | 27.146 | 22.0588 | 29.6243 | 13 |
| ALTA | AG | costo_acumulado | 84 | 98917.881 | 52449.0911 | 86292.5 | 9886 | 191524 | 13 |
| ALTA | AG | planificador_ms_medio | 84 | 2099.0976 | 58.5794 | 2108.1 | 2003.2 | 2222.3 | 13 |
| ALTA | AG | cambios_de_unidad | 84 | 92.7619 | 46.3992 | 87 | 11 | 244 | 13 |
| ALTA | AG | pedidos_inentregables_bloqueo | 84 | 0.1548 | 0.3638 | 0 | 0 | 1 | 13 |
| ALTA | TABU | colapso_h | 84 | 67.6143 | 29.2462 | 61.975 | 14.2833 | 120 | 11 |
| ALTA | TABU | pct_pedidos_en_plazo | 84 | 99.7744 | 0.2382 | 99.82 | 98.65 | 100 | 11 |
| ALTA | TABU | costo_por_pedido | 84 | 163.5665 | 5.618 | 164.1843 | 145.2849 | 173.1031 | 11 |
| ALTA | TABU | km_por_pedido | 84 | 27.2004 | 1.0424 | 27.3238 | 22.8784 | 28.9462 | 11 |
| ALTA | TABU | costo_acumulado | 84 | 99789.119 | 49182.4217 | 89691.5 | 10777 | 194435 | 11 |
| ALTA | TABU | planificador_ms_medio | 84 | 2003.8357 | 1.9075 | 2003.75 | 1999.2 | 2010.2 | 11 |
| ALTA | TABU | cambios_de_unidad | 84 | 21.1905 | 11.5325 | 20 | 3 | 60 | 11 |
| ALTA | TABU | pedidos_inentregables_bloqueo | 84 | 0.131 | 0.3394 | 0 | 0 | 1 | 11 |
| BAJA | AG | colapso_h | 120 | 102.1886 | 34.7615 | 120 | 5.9167 | 120 | 88 |
| BAJA | AG | pct_pedidos_en_plazo | 120 | 99.6125 | 1.3132 | 100 | 93.33 | 100 | 88 |
| BAJA | AG | costo_por_pedido | 120 | 153.2372 | 8.7042 | 153.1453 | 131.4875 | 219.1429 | 88 |
| BAJA | AG | km_por_pedido | 120 | 24.9403 | 1.4804 | 24.9939 | 20.3125 | 32.7143 | 88 |
| BAJA | AG | costo_acumulado | 120 | 106517.275 | 39440.5875 | 119818.5 | 2045 | 149270 | 88 |
| BAJA | AG | planificador_ms_medio | 120 | 2014.1317 | 8.4629 | 2011.85 | 1996.8 | 2043.3 | 88 |
| BAJA | AG | cambios_de_unidad | 120 | 269.625 | 115.5351 | 289.5 | 5 | 459 | 88 |
| BAJA | AG | pedidos_inentregables_bloqueo | 120 | 0 | 0 | 0 | 0 | 0 | 88 |
| BAJA | TABU | colapso_h | 120 | 101.3996 | 35.2014 | 120 | 5.9167 | 120 | 87 |
| BAJA | TABU | pct_pedidos_en_plazo | 120 | 99.6382 | 1.2016 | 100 | 94.12 | 100 | 87 |
| BAJA | TABU | costo_por_pedido | 120 | 159.3846 | 6.8412 | 160.0646 | 143.4278 | 195 | 87 |
| BAJA | TABU | km_por_pedido | 120 | 26.3295 | 1.1452 | 26.4323 | 22.4756 | 31.75 | 87 |
| BAJA | TABU | costo_acumulado | 120 | 109118.9417 | 40945.981 | 125451.5 | 2715 | 150076 | 87 |
| BAJA | TABU | planificador_ms_medio | 120 | 2003.8033 | 2.2196 | 2003.45 | 1999.7 | 2015.6 | 87 |
| BAJA | TABU | cambios_de_unidad | 120 | 29.825 | 15.6332 | 27 | 3 | 135 | 87 |
| BAJA | TABU | pedidos_inentregables_bloqueo | 120 | 0 | 0 | 0 | 0 | 0 | 87 |
| MEDIA | AG | colapso_h | 105 | 103.2935 | 26.4932 | 120 | 22.0167 | 120 | 59 |
| MEDIA | AG | pct_pedidos_en_plazo | 105 | 99.9148 | 0.1348 | 100 | 99.23 | 100 | 59 |
| MEDIA | AG | costo_por_pedido | 105 | 160.4591 | 3.3583 | 160.2521 | 149.8527 | 169.3527 | 59 |
| MEDIA | AG | km_por_pedido | 105 | 26.4542 | 0.6863 | 26.4492 | 24.062 | 27.8799 | 59 |
| MEDIA | AG | costo_acumulado | 105 | 140027.7524 | 41081.6545 | 156103 | 19331 | 188316 | 59 |
| MEDIA | AG | planificador_ms_medio | 105 | 2040.2819 | 26.8019 | 2029.6 | 2008.3 | 2123.1 | 59 |
| MEDIA | AG | cambios_de_unidad | 105 | 183.1714 | 78.6426 | 175 | 48 | 403 | 59 |
| MEDIA | AG | pedidos_inentregables_bloqueo | 105 | 0.1238 | 0.3309 | 0 | 0 | 1 | 59 |
| MEDIA | TABU | colapso_h | 105 | 107.6371 | 22.6319 | 120 | 27.8833 | 120 | 68 |
| MEDIA | TABU | pct_pedidos_en_plazo | 105 | 99.9396 | 0.0999 | 100 | 99.39 | 100 | 68 |
| MEDIA | TABU | costo_por_pedido | 105 | 162.6533 | 3.0463 | 162.5043 | 150.8659 | 173.3853 | 68 |
| MEDIA | TABU | km_por_pedido | 105 | 27.043 | 0.4968 | 27.0821 | 25.5122 | 28.3484 | 68 |
| MEDIA | TABU | costo_acumulado | 105 | 149111.819 | 36776.2018 | 161505 | 24742 | 190745 | 68 |
| MEDIA | TABU | planificador_ms_medio | 105 | 2003.5733 | 1.4114 | 2003.3 | 2000.6 | 2007.5 | 68 |
| MEDIA | TABU | cambios_de_unidad | 105 | 27.6667 | 11.3558 | 27 | 9 | 71 | 68 |
| MEDIA | TABU | pedidos_inentregables_bloqueo | 105 | 0.1429 | 0.3516 | 0 | 0 | 1 | 68 |

Corridas censuradas (sin colapso en el horizonte): 326 de 618.

## 1b. Causas de colapso por nivel y algoritmo

| nivel | algoritmo | causa_colapso | corridas |
|---|---|---|---|
| ALTA | AG | destino bloqueado | 18 |
| ALTA | AG | llegada tardía (a bordo) | 18 |
| ALTA | AG | llegada tardía (planificada) | 35 |
| ALTA | TABU | destino bloqueado | 19 |
| ALTA | TABU | llegada tardía (a bordo) | 18 |
| ALTA | TABU | llegada tardía (planificada) | 36 |
| BAJA | AG | destino bloqueado | 25 |
| BAJA | AG | llegada tardía (a bordo) | 5 |
| BAJA | AG | llegada tardía (planificada) | 2 |
| BAJA | TABU | destino bloqueado | 24 |
| BAJA | TABU | llegada tardía (a bordo) | 6 |
| BAJA | TABU | llegada tardía (planificada) | 3 |
| MEDIA | AG | destino bloqueado | 15 |
| MEDIA | AG | llegada tardía (a bordo) | 7 |
| MEDIA | AG | llegada tardía (planificada) | 24 |
| MEDIA | TABU | destino bloqueado | 17 |
| MEDIA | TABU | llegada tardía (a bordo) | 9 |
| MEDIA | TABU | llegada tardía (planificada) | 11 |

## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel

Log-rank propio verificado con lifelines (columna p_lifelines).

## Log-rank TABU vs. AG por nivel

| nivel | chi2 | p | colapsos_TABU | esperados_TABU | censuradas_TABU | censuradas_AG | p_lifelines |
|---|---|---|---|---|---|---|---|
| BAJA | 0.0237 | 0.8776 | 33 | 32.3831 | 87 | 88 | 0.8776 |
| MEDIA | 1.7253 | 0.189 | 37 | 42.9531 | 68 | 59 | 0.189 |
| ALTA | 0.0123 | 0.9115 | 73 | 72.3379 | 11 | 13 | 0.9115 |

## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 1 | 27.5 | 26.6667 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 0.1755 | 35.2381 | 43.8095 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 0.7266 | 86.9048 | 84.5238 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.8776 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.189 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.9115 | 61.975 | 61.65 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.7833 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | Wilcoxon (rangos con signo) | 1.969e-17 | 160.0646 | 153.1453 | 4.5181 | sí | AG |
| BAJA | km_por_pedido | Wilcoxon (rangos con signo) | 1.906e-18 | 26.4323 | 24.9939 | 5.7552 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 9.733e-09 | 125451.5 | 119818.5 | 4.7013 | sí | AG |
| BAJA | planificador_ms_medio | Wilcoxon (rangos con signo) | 4.345e-19 | 2003.45 | 2011.85 | -0.4175 | sí | TABU |
| BAJA | cambios_de_unidad | Wilcoxon (rangos con signo) | 3.019e-21 | 27 | 289.5 | -90.6736 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.1552 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | Wilcoxon (rangos con signo) | 5.093e-10 | 162.5043 | 160.2521 | 1.4054 | sí | AG |
| MEDIA | km_por_pedido | Wilcoxon (rangos con signo) | 8.122e-12 | 27.0821 | 26.4492 | 2.3929 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 4.678e-05 | 161505 | 156103 | 3.4605 | sí | AG |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 5.833e-19 | 2003.3 | 2029.6 | -1.2958 | sí | TABU |
| MEDIA | cambios_de_unidad | Wilcoxon (rangos con signo) | 5.83e-19 | 27 | 175 | -84.5714 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.1573 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.4077 | 99.82 | 99.805 | 0.015 | no | sin diferencia |
| ALTA | costo_por_pedido | Wilcoxon (rangos con signo) | 0.3536 | 164.1843 | 164.0404 | 0.0877 | no | sin diferencia |
| ALTA | km_por_pedido | Wilcoxon (rangos con signo) | 0.0128 | 27.3238 | 27.146 | 0.6551 | sí | AG |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.8514 | 89691.5 | 86292.5 | 3.9389 | no | sin diferencia |
| ALTA | planificador_ms_medio | Wilcoxon (rangos con signo) | 1.773e-15 | 2003.75 | 2108.1 | -4.95 | sí | TABU |
| ALTA | cambios_de_unidad | Wilcoxon (rangos con signo) | 4.082e-15 | 20 | 87 | -77.0115 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.3173 | 0 | 0 |  | no | sin diferencia |

## 4. Regla de decisión (% de colapsos -> tiempo hasta el colapso -> costo por pedido -> estabilidad)

| nivel | decision |
|---|---|
| ALTA | TABU (decide: cambios_de_unidad) |
| BAJA | AG (decide: costo_por_pedido) |
| MEDIA | AG (decide: costo_por_pedido) |


## Análisis sin las corridas con colapso por destino bloqueado

Se excluyen 118 de 618 corridas. Resultados en `sin_destino_bloqueado/resumen.md`.
