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
| BAJA | 40 | 27.5 | [15, 44] | 25 | [13, 41] | 7 | 4 | 3 | 26 | 1 | no | sin diferencia |
| MEDIA | 35 | 34.2857 | [19, 52] | 42.8571 | [26, 61] | 9 | 3 | 6 | 17 | 0.5078 | no | sin diferencia |
| ALTA | 28 | 89.2857 | [72, 98] | 92.8571 | [76, 99] | 24 | 1 | 2 | 1 | 1 | no | sin diferencia |

**Global (todos los niveles, McNemar exacta sobre los pares discordantes sumados):** solo TABU colapsa en 8 pares, solo AG en 11; p = 0.6476 → **sin diferencia significativa**.

**Regresión logística GEE (agrupada por tramo), colapso ~ algoritmo + nivel:** odds ratio TABU/AG = 0.849, p = 0.4906.

## 1. Estadística descriptiva por nivel y algoritmo

| nivel | algoritmo | variable | n | media | desv | mediana | min | max | censuradas |
|---|---|---|---|---|---|---|---|---|---|
| ALTA | AG | colapso_h | 28 | 64.0238 | 26.9214 | 60.7416 | 14.2833 | 120 | 2 |
| ALTA | AG | pct_pedidos_en_plazo | 28 | 99.7536 | 0.261 | 99.8 | 98.55 | 100 | 2 |
| ALTA | AG | costo_por_pedido | 28 | 162.1762 | 7.3611 | 160.9201 | 141.3167 | 173.3044 | 2 |
| ALTA | AG | km_por_pedido | 28 | 26.751 | 1.5629 | 26.8654 | 22.0588 | 29.0826 | 2 |
| ALTA | AG | costo_acumulado | 28 | 93304.7143 | 46388.543 | 83414.5 | 10332 | 183996 | 2 |
| ALTA | AG | planificador_ms_medio | 28 | 2095.8786 | 49.5157 | 2108.1 | 2009.6 | 2185.9 | 2 |
| ALTA | AG | cambios_de_unidad | 28 | 99.5 | 47.8543 | 90.5 | 14 | 216 | 2 |
| ALTA | AG | pedidos_inentregables_bloqueo | 28 | 0.1429 | 0.3563 | 0 | 0 | 1 | 2 |
| ALTA | TABU | colapso_h | 28 | 65.7012 | 26.9299 | 61.975 | 14.2833 | 120 | 3 |
| ALTA | TABU | pct_pedidos_en_plazo | 28 | 99.7718 | 0.241 | 99.81 | 98.67 | 100 | 3 |
| ALTA | TABU | costo_por_pedido | 28 | 162.2795 | 6.548 | 161.8408 | 145.2849 | 172.3635 | 3 |
| ALTA | TABU | km_por_pedido | 28 | 26.9923 | 1.2509 | 27.0735 | 22.8784 | 28.9462 | 3 |
| ALTA | TABU | costo_acumulado | 28 | 95821.1429 | 45004.3493 | 88836 | 10777 | 193839 | 3 |
| ALTA | TABU | planificador_ms_medio | 28 | 2004.2929 | 2.025 | 2004.3 | 1999.2 | 2010.2 | 3 |
| ALTA | TABU | cambios_de_unidad | 28 | 21.5714 | 13.1865 | 19.5 | 5 | 60 | 3 |
| ALTA | TABU | pedidos_inentregables_bloqueo | 28 | 0.1429 | 0.3563 | 0 | 0 | 1 | 3 |
| BAJA | AG | colapso_h | 40 | 104.2483 | 32.4772 | 120 | 5.9167 | 120 | 30 |
| BAJA | AG | pct_pedidos_en_plazo | 40 | 99.6573 | 1.2694 | 100 | 93.75 | 100 | 30 |
| BAJA | AG | costo_por_pedido | 40 | 153.3144 | 5.1833 | 153.4485 | 136.3333 | 161.4444 | 30 |
| BAJA | AG | km_por_pedido | 40 | 24.9377 | 1.1461 | 25.042 | 20.8 | 27.057 | 30 |
| BAJA | AG | costo_acumulado | 40 | 108908.375 | 37034.4292 | 120535.5 | 2045 | 145328 | 30 |
| BAJA | AG | planificador_ms_medio | 40 | 2014.765 | 8.0434 | 2014 | 2000 | 2036.5 | 30 |
| BAJA | AG | cambios_de_unidad | 40 | 277.675 | 115.3404 | 290 | 5 | 441 | 30 |
| BAJA | AG | pedidos_inentregables_bloqueo | 40 | 0 | 0 | 0 | 0 | 0 | 30 |
| BAJA | TABU | colapso_h | 40 | 100.7946 | 35.9645 | 120 | 5.9167 | 120 | 29 |
| BAJA | TABU | pct_pedidos_en_plazo | 40 | 99.634 | 1.2329 | 100 | 94.12 | 100 | 29 |
| BAJA | TABU | costo_por_pedido | 40 | 160.3458 | 7.3653 | 160.8691 | 145.6519 | 195 | 29 |
| BAJA | TABU | km_por_pedido | 40 | 26.4981 | 1.2743 | 26.6439 | 23.4385 | 31.75 | 29 |
| BAJA | TABU | costo_acumulado | 40 | 108325 | 41744.5912 | 125117.5 | 2946 | 149549 | 29 |
| BAJA | TABU | planificador_ms_medio | 40 | 2003.56 | 1.9781 | 2003.35 | 1999.7 | 2011.3 | 29 |
| BAJA | TABU | cambios_de_unidad | 40 | 29.65 | 13.7683 | 29 | 3 | 80 | 29 |
| BAJA | TABU | pedidos_inentregables_bloqueo | 40 | 0 | 0 | 0 | 0 | 0 | 29 |
| MEDIA | AG | colapso_h | 35 | 101.2795 | 29.6334 | 120 | 35.9333 | 120 | 20 |
| MEDIA | AG | pct_pedidos_en_plazo | 35 | 99.9131 | 0.1213 | 100 | 99.61 | 100 | 20 |
| MEDIA | AG | costo_por_pedido | 35 | 160.3441 | 3.2882 | 160.2312 | 154.8774 | 169.2657 | 20 |
| MEDIA | AG | km_por_pedido | 35 | 26.3753 | 0.7034 | 26.4135 | 24.815 | 27.8799 | 20 |
| MEDIA | AG | costo_acumulado | 35 | 135995.8571 | 43888.8416 | 154786 | 39653 | 184190 | 20 |
| MEDIA | AG | planificador_ms_medio | 35 | 2038.8486 | 27.2839 | 2027.6 | 2009.1 | 2123.1 | 20 |
| MEDIA | AG | cambios_de_unidad | 35 | 199.3143 | 89.8269 | 185 | 58 | 403 | 20 |
| MEDIA | AG | pedidos_inentregables_bloqueo | 35 | 0.1143 | 0.3228 | 0 | 0 | 1 | 20 |
| MEDIA | TABU | colapso_h | 35 | 107.1686 | 25.8957 | 120 | 27.8833 | 120 | 23 |
| MEDIA | TABU | pct_pedidos_en_plazo | 35 | 99.9291 | 0.132 | 100 | 99.39 | 100 | 23 |
| MEDIA | TABU | costo_por_pedido | 35 | 162.0327 | 3.2452 | 161.8437 | 150.8659 | 169.5953 | 23 |
| MEDIA | TABU | km_por_pedido | 35 | 26.8841 | 0.5258 | 26.9165 | 25.5122 | 27.8575 | 23 |
| MEDIA | TABU | costo_acumulado | 35 | 148111.1143 | 42130.7117 | 161006 | 24742 | 190745 | 23 |
| MEDIA | TABU | planificador_ms_medio | 35 | 2003.2714 | 1.4403 | 2003.1 | 2000.6 | 2007.5 | 23 |
| MEDIA | TABU | cambios_de_unidad | 35 | 29.4 | 11.67 | 28 | 10 | 69 | 23 |
| MEDIA | TABU | pedidos_inentregables_bloqueo | 35 | 0.1429 | 0.355 | 0 | 0 | 1 | 23 |

Corridas censuradas (sin colapso en el horizonte): 107 de 206.

## 1b. Causas de colapso por nivel y algoritmo

| nivel | algoritmo | causa_colapso | corridas |
|---|---|---|---|
| ALTA | AG | destino bloqueado | 6 |
| ALTA | AG | llegada tardía (a bordo) | 6 |
| ALTA | AG | llegada tardía (planificada) | 14 |
| ALTA | TABU | destino bloqueado | 7 |
| ALTA | TABU | llegada tardía (a bordo) | 6 |
| ALTA | TABU | llegada tardía (planificada) | 12 |
| BAJA | AG | destino bloqueado | 8 |
| BAJA | AG | llegada tardía (a bordo) | 2 |
| BAJA | TABU | destino bloqueado | 8 |
| BAJA | TABU | llegada tardía (a bordo) | 3 |
| MEDIA | AG | destino bloqueado | 5 |
| MEDIA | AG | llegada tardía (a bordo) | 3 |
| MEDIA | AG | llegada tardía (planificada) | 7 |
| MEDIA | TABU | destino bloqueado | 7 |
| MEDIA | TABU | llegada tardía (a bordo) | 2 |
| MEDIA | TABU | llegada tardía (planificada) | 3 |

## 2. Hay censuradas: Kaplan-Meier por algoritmo y prueba log-rank por nivel

Log-rank propio verificado con lifelines (columna p_lifelines).

## Log-rank TABU vs. AG por nivel

| nivel | chi2 | p | colapsos_TABU | esperados_TABU | censuradas_TABU | censuradas_AG | p_lifelines |
|---|---|---|---|---|---|---|---|
| BAJA | 0.0846 | 0.7712 | 11 | 10.3365 | 29 | 30 | 0.7712 |
| MEDIA | 0.6207 | 0.4308 | 12 | 14.0363 | 23 | 20 | 0.4308 |
| ALTA | 0.0971 | 0.7553 | 25 | 26.1078 | 3 | 2 | 0.7553 |

## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 1 | 27.5 | 25 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 0.5078 | 34.2857 | 42.8571 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 1 | 89.2857 | 92.8571 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.7712 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.4308 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.7553 | 61.975 | 60.7416 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.4236 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | Wilcoxon (rangos con signo) | 8.131e-10 | 160.8691 | 153.4485 | 4.8359 | sí | AG |
| BAJA | km_por_pedido | Wilcoxon (rangos con signo) | 1.601e-10 | 26.6439 | 25.042 | 6.3967 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 0.0034 | 125117.5 | 120535.5 | 3.8014 | sí | AG |
| BAJA | planificador_ms_medio | t pareada | 1.527e-10 | 2003.35 | 2014 | -0.5288 | sí | TABU |
| BAJA | cambios_de_unidad | t pareada | 7.844e-17 | 29 | 290 | -90 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.2941 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | Wilcoxon (rangos con signo) | 0.0032 | 161.8437 | 160.2312 | 1.0064 | sí | AG |
| MEDIA | km_por_pedido | t pareada | 0.0015 | 26.9165 | 26.4135 | 1.9044 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.0017 | 161006 | 154786 | 4.0185 | sí | AG |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 5.821e-11 | 2003.1 | 2027.6 | -1.2083 | sí | TABU |
| MEDIA | cambios_de_unidad | t pareada | 2.644e-13 | 28 | 185 | -84.8649 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.3173 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.5028 | 99.81 | 99.8 | 0.01 | no | sin diferencia |
| ALTA | costo_por_pedido | t pareada | 0.9121 | 161.8408 | 160.9201 | 0.5721 | no | sin diferencia |
| ALTA | km_por_pedido | t pareada | 0.2218 | 27.0735 | 26.8654 | 0.7749 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 1 | 88836 | 83414.5 | 6.4995 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 2.039e-10 | 2004.3 | 2108.1 | -4.9239 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 3.502e-09 | 19.5 | 90.5 | -78.453 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 1 | 0 | 0 |  | no | sin diferencia |

## 4. Regla de decisión (% de colapsos -> tiempo hasta el colapso -> estabilidad -> costo por pedido)

| nivel | decision |
|---|---|
| ALTA | TABU (decide: cambios_de_unidad) |
| BAJA | TABU (decide: cambios_de_unidad) |
| MEDIA | TABU (decide: cambios_de_unidad) |


## Análisis sin las corridas con colapso por destino bloqueado

Se excluyen 41 de 206 corridas. Resultados en `sin_destino_bloqueado/resumen.md`.
