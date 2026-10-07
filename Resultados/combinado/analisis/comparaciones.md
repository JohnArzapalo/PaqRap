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
