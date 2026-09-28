## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 0.5078 | 30 | 22.5 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 1 | 42.8571 | 40 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 1 | 85.7143 | 82.1429 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.4753 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.875 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.9295 | 61.6583 | 57.8583 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.8888 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | Wilcoxon (rangos con signo) | 9.584e-07 | 159.8729 | 154.0965 | 3.7485 | sí | AG |
| BAJA | km_por_pedido | t pareada | 1.99e-08 | 26.2617 | 25.1086 | 4.5922 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 0.012 | 125100.5 | 123607.5 | 1.2079 | sí | AG |
| BAJA | planificador_ms_medio | Wilcoxon (rangos con signo) | 5.718e-07 | 2003.65 | 2011.4 | -0.3853 | sí | TABU |
| BAJA | cambios_de_unidad | Wilcoxon (rangos con signo) | 4.833e-08 | 25.5 | 268 | -90.4851 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 1 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | t pareada | 0.0002529 | 162.7966 | 161.1771 | 1.0048 | sí | AG |
| MEDIA | km_por_pedido | Wilcoxon (rangos con signo) | 1.253e-06 | 27.182 | 26.5728 | 2.2923 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.1843 | 159816 | 158751 | 0.6709 | no | sin diferencia |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 5.821e-11 | 2003.8 | 2030.7 | -1.3247 | sí | TABU |
| MEDIA | cambios_de_unidad | Wilcoxon (rangos con signo) | 2.476e-07 | 25 | 135 | -81.4815 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.3173 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.3888 | 99.81 | 99.81 | 0 | no | sin diferencia |
| ALTA | costo_por_pedido | t pareada | 0.5071 | 164.4948 | 164.3987 | 0.0584 | no | sin diferencia |
| ALTA | km_por_pedido | t pareada | 0.1098 | 27.4496 | 27.3126 | 0.5015 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.5824 | 87467 | 85994 | 1.7129 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 2.689e-08 | 2004.2 | 2103.6 | -4.7252 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 1.153e-08 | 20 | 74 | -72.973 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
