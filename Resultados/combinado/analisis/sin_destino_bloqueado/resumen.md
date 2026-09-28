# Análisis SIN las corridas con colapso por "destino bloqueado" (500 de 618 corridas)

- Situaciones: ventanas (por_replica: cada réplica es una muestra de pedidos distinta, la misma para ambos algoritmos).

## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 90 | 10 | [5, 18] | 5.5556 | [2, 12] | 0 | 9 | 5 | 76 | 0.424 | no | sin diferencia |
| MEDIA | 84 | 22.619 | [14, 33] | 29.7619 | [20, 41] | 6 | 13 | 19 | 46 | 0.3771 | no | sin diferencia |
| ALTA | 61 | 83.6066 | [72, 92] | 81.9672 | [70, 91] | 48 | 3 | 2 | 8 | 1 | no | sin diferencia |

## 0b. % de colapsos por corrida (misma situación, otra semilla de los algoritmos)

| corrida (semilla base) | pares | %colapso_TABU | %colapso_AG | solo_TABU | solo_AG | p_McNemar | significativo |
|---|---|---|---|---|---|---|---|
| 1000 | 78 | 32.0513 | 34.6154 | 7 | 9 | 0.8036 | no |
| 2000 | 80 | 40 | 31.25 | 12 | 5 | 0.1435 | no |
| 3000 | 77 | 28.5714 | 36.3636 | 6 | 12 | 0.2379 | no |

**Con varias corridas, los tramos se repiten**: la McNemar global de abajo los cuenta como pares independientes y es optimista. La prueba combinada válida es la **GEE agrupada por tramo**.

**Global (todos los niveles, McNemar exacta sobre los pares discordantes sumados):** solo TABU colapsa en 25 pares, solo AG en 26; p = 1 → **sin diferencia significativa**.

**Regresión logística GEE (agrupada por tramo), colapso ~ algoritmo + nivel:** odds ratio TABU/AG = 0.83, p = 0.4097.

## 1. Estadística descriptiva por nivel y algoritmo

| nivel | algoritmo | variable | n | media | desv | mediana | min | max | censuradas |
|---|---|---|---|---|---|---|---|---|---|
| ALTA | AG | colapso_h | 66 | 73.3864 | 30.0665 | 68.95 | 27.8 | 120 | 13 |
| ALTA | AG | pct_pedidos_en_plazo | 66 | 99.8309 | 0.1136 | 99.835 | 99.52 | 100 | 13 |
| ALTA | AG | costo_por_pedido | 66 | 164.4044 | 5.9366 | 164.3964 | 152.4845 | 180.3414 | 13 |
| ALTA | AG | km_por_pedido | 66 | 27.2061 | 1.0079 | 27.253 | 24.8687 | 29.6243 | 13 |
| ALTA | AG | costo_acumulado | 66 | 110143.0152 | 50354.9663 | 103557.5 | 34027 | 191524 | 13 |
| ALTA | AG | planificador_ms_medio | 66 | 2112.4682 | 54.2382 | 2116.45 | 2011 | 2222.3 | 13 |
| ALTA | AG | cambios_de_unidad | 66 | 95.9394 | 47.5747 | 89.5 | 11 | 244 | 13 |
| ALTA | AG | pedidos_inentregables_bloqueo | 66 | 0.1515 | 0.3613 | 0 | 0 | 1 | 13 |
| ALTA | TABU | colapso_h | 65 | 73.4541 | 27.8435 | 64.3167 | 29.35 | 120 | 11 |
| ALTA | TABU | pct_pedidos_en_plazo | 65 | 99.8372 | 0.0983 | 99.83 | 99.57 | 100 | 11 |
| ALTA | TABU | costo_por_pedido | 65 | 164.7207 | 4.5251 | 165.128 | 155.434 | 173.1031 | 11 |
| ALTA | TABU | km_por_pedido | 65 | 27.4552 | 0.7119 | 27.5006 | 26 | 28.9462 | 11 |
| ALTA | TABU | costo_acumulado | 65 | 110484.8615 | 46344.0633 | 95941 | 39299 | 194435 | 11 |
| ALTA | TABU | planificador_ms_medio | 65 | 2003.8 | 1.7379 | 2003.9 | 1999.9 | 2008.1 | 11 |
| ALTA | TABU | cambios_de_unidad | 65 | 20.9077 | 11.1967 | 20 | 4 | 52 | 11 |
| ALTA | TABU | pedidos_inentregables_bloqueo | 65 | 0.1077 | 0.3124 | 0 | 0 | 1 | 11 |
| BAJA | AG | colapso_h | 95 | 116.2716 | 15.8704 | 120 | 11.9167 | 120 | 88 |
| BAJA | AG | pct_pedidos_en_plazo | 95 | 99.9589 | 0.2577 | 100 | 97.56 | 100 | 88 |
| BAJA | AG | costo_por_pedido | 95 | 153.6459 | 4.4176 | 153.336 | 133.35 | 164.2134 | 88 |
| BAJA | AG | km_por_pedido | 95 | 25.1368 | 0.87 | 25.0853 | 22.6769 | 27.057 | 88 |
| BAJA | AG | costo_acumulado | 95 | 121251.1474 | 21223.4629 | 123857 | 5334 | 149270 | 88 |
| BAJA | AG | planificador_ms_medio | 95 | 2014.6505 | 7.5979 | 2012.7 | 2000.9 | 2042.9 | 88 |
| BAJA | AG | cambios_de_unidad | 95 | 310.8316 | 82.456 | 324 | 44 | 459 | 88 |
| BAJA | AG | pedidos_inentregables_bloqueo | 95 | 0 | 0 | 0 | 0 | 0 | 88 |
| BAJA | TABU | colapso_h | 96 | 114.4889 | 18.7545 | 120 | 19.65 | 120 | 87 |
| BAJA | TABU | pct_pedidos_en_plazo | 96 | 99.9602 | 0.1747 | 100 | 98.77 | 100 | 87 |
| BAJA | TABU | costo_por_pedido | 96 | 159.5483 | 4.1169 | 160.2232 | 143.95 | 167.4215 | 87 |
| BAJA | TABU | km_por_pedido | 96 | 26.5089 | 0.7805 | 26.4887 | 23.4556 | 28.1074 | 87 |
| BAJA | TABU | costo_acumulado | 96 | 123455.5729 | 23870.2475 | 127438 | 11516 | 150076 | 87 |
| BAJA | TABU | planificador_ms_medio | 96 | 2003.7802 | 1.9231 | 2003.5 | 2000.7 | 2015.6 | 87 |
| BAJA | TABU | cambios_de_unidad | 96 | 32.3125 | 15.6786 | 30 | 7 | 135 | 87 |
| BAJA | TABU | pedidos_inentregables_bloqueo | 96 | 0 | 0 | 0 | 0 | 0 | 87 |
| MEDIA | AG | colapso_h | 90 | 107.6507 | 22.6777 | 120 | 35.9333 | 120 | 59 |
| MEDIA | AG | pct_pedidos_en_plazo | 90 | 99.9417 | 0.0934 | 100 | 99.61 | 100 | 59 |
| MEDIA | AG | costo_por_pedido | 90 | 160.9481 | 3.0358 | 160.4978 | 154.8774 | 169.3527 | 59 |
| MEDIA | AG | km_por_pedido | 90 | 26.5612 | 0.5772 | 26.538 | 24.815 | 27.8799 | 59 |
| MEDIA | AG | costo_acumulado | 90 | 147244.5889 | 35283.8277 | 157053 | 39653 | 188316 | 59 |
| MEDIA | AG | planificador_ms_medio | 90 | 2042.2944 | 28.0546 | 2029.65 | 2008.3 | 2123.1 | 59 |
| MEDIA | AG | cambios_de_unidad | 90 | 189.1667 | 79.9653 | 182.5 | 48 | 403 | 59 |
| MEDIA | AG | pedidos_inentregables_bloqueo | 90 | 0.1 | 0.3017 | 0 | 0 | 1 | 59 |
| MEDIA | TABU | colapso_h | 88 | 111.685 | 20.4229 | 120 | 27.8833 | 120 | 68 |
| MEDIA | TABU | pct_pedidos_en_plazo | 88 | 99.9584 | 0.0959 | 100 | 99.39 | 100 | 68 |
| MEDIA | TABU | costo_por_pedido | 88 | 162.8129 | 3.0963 | 162.7479 | 150.8659 | 173.3853 | 68 |
| MEDIA | TABU | km_por_pedido | 88 | 27.0921 | 0.481 | 27.1076 | 25.5122 | 28.3484 | 68 |
| MEDIA | TABU | costo_acumulado | 88 | 156384.7045 | 32940.4061 | 163993.5 | 24742 | 190745 | 68 |
| MEDIA | TABU | planificador_ms_medio | 88 | 2003.6261 | 1.4215 | 2003.35 | 2000.6 | 2007.5 | 68 |
| MEDIA | TABU | cambios_de_unidad | 88 | 27.6477 | 11.9991 | 27.5 | 9 | 71 | 68 |
| MEDIA | TABU | pedidos_inentregables_bloqueo | 88 | 0.1364 | 0.3451 | 0 | 0 | 1 | 68 |

Corridas censuradas (sin colapso en el horizonte): 326 de 500.

## 1b. Causas de colapso por nivel y algoritmo

| nivel | algoritmo | causa_colapso | corridas |
|---|---|---|---|
| ALTA | AG | llegada tardía (a bordo) | 18 |
| ALTA | AG | llegada tardía (planificada) | 35 |
| ALTA | TABU | llegada tardía (a bordo) | 18 |
| ALTA | TABU | llegada tardía (planificada) | 36 |
| BAJA | AG | llegada tardía (a bordo) | 5 |
| BAJA | AG | llegada tardía (planificada) | 2 |
| BAJA | TABU | llegada tardía (a bordo) | 6 |
| BAJA | TABU | llegada tardía (planificada) | 3 |
| MEDIA | AG | llegada tardía (a bordo) | 7 |
| MEDIA | AG | llegada tardía (planificada) | 24 |
| MEDIA | TABU | llegada tardía (a bordo) | 9 |
| MEDIA | TABU | llegada tardía (planificada) | 11 |

## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel

Log-rank propio verificado con lifelines (columna p_lifelines).

## Log-rank TABU vs. AG por nivel

| nivel | chi2 | p | colapsos_TABU | esperados_TABU | censuradas_TABU | censuradas_AG | p_lifelines |
|---|---|---|---|---|---|---|---|
| BAJA | 0.2673 | 0.6051 | 9 | 7.966 | 87 | 88 | 0.6051 |
| MEDIA | 2.9574 | 0.0855 | 20 | 26.1319 | 68 | 59 | 0.0855 |
| ALTA | 0.0193 | 0.8896 | 54 | 53.2845 | 11 | 13 | 0.8896 |

## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 0.424 | 10 | 5.5556 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 0.3771 | 22.619 | 29.7619 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 1 | 83.6066 | 81.9672 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.6051 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.0855 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.8896 | 64.3167 | 68.95 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.3002 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | Wilcoxon (rangos con signo) | 1.815e-14 | 160.2232 | 153.4485 | 4.415 | sí | AG |
| BAJA | km_por_pedido | t pareada | 1.693e-26 | 26.5392 | 25.108 | 5.7003 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 2.412e-07 | 126545.5 | 123607.5 | 2.3769 | sí | AG |
| BAJA | planificador_ms_medio | Wilcoxon (rangos con signo) | 8.314e-16 | 2003.5 | 2012.95 | -0.4695 | sí | TABU |
| BAJA | cambios_de_unidad | Wilcoxon (rangos con signo) | 1.801e-16 | 30 | 324.5 | -90.755 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.4551 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | Wilcoxon (rangos con signo) | 1.683e-07 | 162.748 | 160.4978 | 1.402 | sí | AG |
| MEDIA | km_por_pedido | t pareada | 4.445e-09 | 27.1076 | 26.5757 | 2.0015 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.0003918 | 163627.5 | 159861.5 | 2.3558 | sí | AG |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 1.71e-15 | 2003.35 | 2030 | -1.3128 | sí | TABU |
| MEDIA | cambios_de_unidad | t pareada | 3.173e-33 | 28 | 182.5 | -84.6575 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.1573 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.903 | 99.83 | 99.83 | 0 | no | sin diferencia |
| ALTA | costo_por_pedido | t pareada | 0.8276 | 164.9834 | 165.1192 | -0.0822 | no | sin diferencia |
| ALTA | km_por_pedido | t pareada | 0.1304 | 27.457 | 27.2729 | 0.675 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.8434 | 95941 | 102384 | -6.293 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 8.861e-23 | 2003.8 | 2114.7 | -5.2442 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 3.432e-18 | 20 | 91 | -78.022 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.5637 | 0 | 0 |  | no | sin diferencia |

## 4. Regla de decisión (% de colapsos -> tiempo hasta el colapso -> costo por pedido -> estabilidad)

| nivel | decision |
|---|---|
| ALTA | TABU (decide: cambios_de_unidad) |
| BAJA | AG (decide: costo_por_pedido) |
| MEDIA | AG (decide: costo_por_pedido) |
