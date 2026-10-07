## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 90 | 10 | [5, 18] | 5.5556 | [2, 12] | 0 | 9 | 5 | 76 | 0.424 | no | sin diferencia |
| MEDIA | 84 | 22.619 | [14, 33] | 29.7619 | [20, 41] | 6 | 13 | 19 | 46 | 0.3771 | no | sin diferencia |
| ALTA | 61 | 83.6066 | [72, 92] | 81.9672 | [70, 91] | 48 | 3 | 2 | 8 | 1 | no | sin diferencia |
