## 3. Tabú vs. AG por nivel (en pares: t pareada si las diferencias son normales; si no, Wilcoxon)

| nivel | variable | prueba | p | mediana_TABU | mediana_AG | dif_rel_medianas_% | significativo | mejor |
|---|---|---|---|---|---|---|---|---|
| BAJA | colapso | McNemar exacta | 0.4531 | 25 | 32.5 |  | no | sin diferencia |
| MEDIA | colapso | McNemar exacta | 0.1185 | 28.5714 | 48.5714 |  | no | sin diferencia |
| ALTA | colapso | McNemar exacta | 0.5 | 85.7143 | 78.5714 |  | no | sin diferencia |
| BAJA | colapso_h | log-rank | 0.451 | 120 | 120 |  | no | sin diferencia |
| MEDIA | colapso_h | log-rank | 0.1061 | 120 | 120 |  | no | sin diferencia |
| ALTA | colapso_h | log-rank | 0.6116 | 61.7916 | 66.05 |  | no | sin diferencia |
| BAJA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.1361 | 100 | 100 | 0 | no | sin diferencia |
| BAJA | costo_por_pedido | Wilcoxon (rangos con signo) | 3.063e-08 | 159.9142 | 152.5134 | 4.8526 | sí | AG |
| BAJA | km_por_pedido | Wilcoxon (rangos con signo) | 1.811e-08 | 26.4325 | 24.791 | 6.6211 | sí | AG |
| BAJA | costo_acumulado | Wilcoxon (rangos con signo) | 4.299e-07 | 125867.5 | 116289 | 8.2368 | sí | AG |
| BAJA | planificador_ms_medio | Wilcoxon (rangos con signo) | 1.274e-07 | 2003.5 | 2010.15 | -0.3308 | sí | TABU |
| BAJA | cambios_de_unidad | Wilcoxon (rangos con signo) | 3.568e-08 | 28 | 298.5 | -90.6198 | sí | TABU |
| BAJA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| MEDIA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.1116 | 100 | 100 | 0 | no | sin diferencia |
| MEDIA | costo_por_pedido | Wilcoxon (rangos con signo) | 1.419e-06 | 162.9253 | 159.9675 | 1.849 | sí | AG |
| MEDIA | km_por_pedido | Wilcoxon (rangos con signo) | 2.298e-06 | 27.0692 | 26.4147 | 2.4777 | sí | AG |
| MEDIA | costo_acumulado | Wilcoxon (rangos con signo) | 0.006 | 162737 | 154823 | 5.1116 | sí | AG |
| MEDIA | planificador_ms_medio | Wilcoxon (rangos con signo) | 2.476e-07 | 2003.3 | 2028.3 | -1.2326 | sí | TABU |
| MEDIA | cambios_de_unidad | t pareada | 3.272e-15 | 26 | 175 | -85.1429 | sí | TABU |
| MEDIA | pedidos_inentregables_bloqueo | iguales (sin diferencias) | 1 | 0 | 0 |  | no | sin diferencia |
| ALTA | pct_pedidos_en_plazo | Wilcoxon (rangos con signo) | 0.702 | 99.825 | 99.825 | -1.424e-14 | no | sin diferencia |
| ALTA | costo_por_pedido | Wilcoxon (rangos con signo) | 0.3741 | 165.5206 | 163.8475 | 1.0211 | no | sin diferencia |
| ALTA | km_por_pedido | Wilcoxon (rangos con signo) | 0.1438 | 27.4046 | 27.1659 | 0.8789 | no | sin diferencia |
| ALTA | costo_acumulado | Wilcoxon (rangos con signo) | 0.9375 | 95228.5 | 96764 | -1.5869 | no | sin diferencia |
| ALTA | planificador_ms_medio | t pareada | 3.316e-09 | 2002.5 | 2111.95 | -5.1824 | sí | TABU |
| ALTA | cambios_de_unidad | t pareada | 5.179e-09 | 21.5 | 94 | -77.1277 | sí | TABU |
| ALTA | pedidos_inentregables_bloqueo | Wilcoxon (rangos con signo) | 0.1573 | 0 | 0 |  | no | sin diferencia |
