# Datos sintéticos del mes (Etapa 8.2)

> **DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME.** Se generan porque en `datos/` no están los archivos oficiales (`ventas2026mm`, `aaaamm.bloqueadas`, `mant.preventivo`). Al recibirlos, se usan en su lugar con `--archivo` y `--bloqueos`.

Generador: `GeneradorDatosSinteticos` (Java, semilla fija, reproducible):
```
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.GeneradorDatosSinteticos datos
```

## 1. Pedidos: `datos/ventas202609_SINTETICO_MES.txt`

Formato oficial `##d##h##m:posX,posY,cIdCliente,qq,hl`. Semilla 20260901. **3 600 pedidos** (30 días × 120).

| Parámetro | Valor |
|---|---|
| Hora de llegada | Por hora del día, con pesos relativos: 00-05 → 0.25; 06 → 0.5; 07 → 0.8; 08-11 → 1.3; 12-13 → 1.1; 14-17 → 1.3; 18-19 → 1.0; 20-21 → 0.7; 22-23 → 0.4. Minuto uniforme dentro de la hora. Resultado: ~46 pedidos/mes a las 03 h frente a ~250 a las 08 h. |
| Posición | 65 % uniforme en toda la retícula 71 × 51; 35 % alrededor de 4 zonas densas (15,40), (50,35), (35,10) y (60,10) con desviación normal de 5 km. Nunca en un almacén. |
| Cantidad (paquetes) | 1-4: 45 %; 5-10: 30 %; 11-24: 20 %; 25-50: 5 % (las de más de 24 se dividen en entregas parciales) |
| Plazo hl | 36 h: 55 %; 18 h: 12 %; 12 h: 12 %; 8 h: 13 %; 4 h: 8 % (proporciones cercanas al archivo sintético de 65 líneas) |
| Cliente | `c1000` a `c9999`, al azar |

Resultado de los plazos: 36 h = 1 985, 18 h = 437, 12 h = 412, 8 h = 440, 4 h = 326.

## 2. Bloqueos: `datos/202609_SINTETICO.bloqueadas`

Formato oficial `##d##h##m-##d##h##m:x1,y1,...,xn,yn`. Semilla 20260902. **241 bloqueos** en 30 días.

| Parámetro | Valor |
|---|---|
| Bloqueos por día | 5 a 11 (uniforme) |
| Forma | Polilínea **abierta** de 2 a 6 tramos que alternan horizontal y vertical; cada tramo de 1 a 6 km |
| Inicio y duración | Inicio uniforme en el día (minuto); duración de 120 a 720 min (2 a 12 h) |
| Restricciones | Ningún nodo de la polilínea es un almacén. Con el nuevo bloqueo y **todos** los bloqueos que se superponen con él en el tiempo activos a la vez (caso más restrictivo), todo nodo no bloqueado sigue alcanzable desde el central (BFS de `MapaVial`). Los candidatos que no cumplen se descartan: se descartaron 48. |

## 3. Uso en el experimento

`GeneradorCarga` remuestrea (bootstrap) los pedidos del archivo mensual para cada nivel de carga (30/60/90 % de C_max por día), conservando la hora del día y la posición. Los bloqueos se usan tal cual, con el mismo archivo para ambos algoritmos. Los conjuntos generados se guardan en `datos/generados/`.
