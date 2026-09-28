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
