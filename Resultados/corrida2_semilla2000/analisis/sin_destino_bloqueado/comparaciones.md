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
