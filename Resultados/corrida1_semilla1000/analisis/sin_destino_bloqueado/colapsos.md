## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 30 | 10 | [2, 27] | 3.3333 | [0, 17] | 0 | 3 | 1 | 26 | 0.625 | no | sin diferencia |
| MEDIA | 28 | 17.8571 | [6, 37] | 28.5714 | [13, 49] | 2 | 3 | 6 | 17 | 0.5078 | no | sin diferencia |
| ALTA | 20 | 85 | [62, 97] | 90 | [68, 99] | 16 | 1 | 2 | 1 | 1 | no | sin diferencia |
