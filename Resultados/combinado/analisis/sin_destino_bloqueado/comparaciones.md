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
