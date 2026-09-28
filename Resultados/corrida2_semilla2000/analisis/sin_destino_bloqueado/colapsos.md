## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 31 | 16.129 | [5, 34] | 3.2258 | [0, 17] | 0 | 5 | 1 | 25 | 0.2188 | no | sin diferencia |
| MEDIA | 28 | 32.1429 | [16, 52] | 25 | [11, 45] | 3 | 6 | 4 | 15 | 0.7539 | no | sin diferencia |
| ALTA | 21 | 85.7143 | [64, 97] | 80.9524 | [58, 95] | 17 | 1 | 0 | 3 | 1 | no | sin diferencia |
