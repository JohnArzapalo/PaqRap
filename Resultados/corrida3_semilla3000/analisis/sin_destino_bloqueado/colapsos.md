## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 29 | 3.4483 | [0, 18] | 10.3448 | [2, 27] | 0 | 1 | 3 | 25 | 0.625 | no | sin diferencia |
| MEDIA | 28 | 17.8571 | [6, 37] | 35.7143 | [19, 56] | 1 | 4 | 9 | 14 | 0.2668 | no | sin diferencia |
| ALTA | 20 | 80 | [56, 94] | 75 | [51, 91] | 15 | 1 | 0 | 4 | 1 | no | sin diferencia |
