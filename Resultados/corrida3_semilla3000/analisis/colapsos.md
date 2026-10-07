## 0. Variable principal: % de corridas con colapso (pares TABU/AG en la misma situación)

IC: Clopper-Pearson al 95 %. Solo los pares discordantes (solo_TABU, solo_AG) informan la diferencia; la prueba es McNemar exacta (binomial de solo_TABU sobre solo_TABU + solo_AG con p = 1/2).

| nivel | pares | %colapso_TABU | IC_TABU | %colapso_AG | IC_AG | ambos | solo_TABU | solo_AG | ninguno | p_McNemar | significativo | mejor |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BAJA | 40 | 25 | [13, 41] | 32.5 | [19, 49] | 8 | 2 | 5 | 25 | 0.4531 | no | sin diferencia |
| MEDIA | 35 | 28.5714 | [15, 46] | 48.5714 | [31, 66] | 6 | 4 | 11 | 14 | 0.1185 | no | sin diferencia |
| ALTA | 28 | 85.7143 | [67, 96] | 78.5714 | [59, 92] | 22 | 2 | 0 | 4 | 0.5 | no | sin diferencia |
