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
