## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 120 | 27.5 | [20, 36] | 26.6667 | [19, 36] | 21 | 12 | 11 | 76 | 1 | no | sin diferencia |
| MEDIA | 105 | 35.2381 | [26, 45] | 43.8095 | [34, 54] | 24 | 13 | 22 | 46 | 0.1755 | no | sin diferencia |
| ALTA | 84 | 86.9048 | [78, 93] | 84.5238 | [75, 91] | 68 | 5 | 3 | 8 | 0.7266 | no | sin diferencia |
