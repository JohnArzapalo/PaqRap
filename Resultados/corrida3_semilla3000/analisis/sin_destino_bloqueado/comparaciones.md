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
