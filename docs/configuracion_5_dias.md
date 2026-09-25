# Configuración para cumplir «5 días sin colapso» (Etapa 22)

**Requisito:** en la simulación de 5 días (`SIM_5D`), ningún pedido debe quedar sin entregar al vencer su plazo antes de las 120 h, con ninguno de los dos algoritmos.

**Resultado:** con la configuración de este documento, **42 de 42 corridas** llegan a las 120 h sin colapso: ambos algoritmos, 4 niveles de carga, 4 tramos distintos del mes y 3 réplicas por caso.

*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***: falta repetir la verificación con los datos oficiales, con el mismo comando (§5).

---

## 1. Qué fallaba y por qué

Primero se corrió la configuración anterior (la de la semana 08) en SIM_5D:

| Caso | Tabú | AG | Causa del colapso |
|---|---|---|---|
| BAJA (30 % de C_max) | 3/3 cumplen | 3/3 | — |
| MEDIA (60 %) | 3/3 | 1/1 (las demás no terminaron por falta de memoria de la PC) | — |
| ALTA (90 %) | **0/3** (101 a 104 h) | **0/3** (104 h) | Un pedido **imposible por los datos** (destino bloqueado durante todo su plazo) y un pedido a bordo que llegó tarde |
| ARCHIVO (ventas tal cual, 67 %) | **0/3** (14.4 h, el día 1) | **1/3** | Un **empate numérico en el límite** y un pedido a bordo que llegó tarde |

El diagnóstico con el registro de eventos encontró **cuatro causas**. Ninguna era falta de flota:

1. **Empate en la hora límite (error de redondeo).**
   - Una bicicleta, la unidad más barata, llegaba **exactamente** a la hora límite: 14:25 frente a 14:25.
   - El planificador lo consideraba a tiempo, pero el simulador declaraba el colapso porque la entrega se procesaba una fracción de 10⁻¹² min después.
2. **Planes al filo del plazo.**
   - H solo exige «llegar a tiempo» y luego se minimiza el costo, así que los algoritmos preferían unidades lentas y baratas con margen cero.
   - Cualquier desvío (un pedido nuevo que se intercala, un bloqueo) convertía esa entrega en tardía, y si ya estaba a bordo no se podía pasar a otra unidad.
3. **Ventana del tramo sin la hora de alimentación.**
   - Para elegir el camino se evitan los bloqueos activos entre la salida y la llegada estimada, pero esa llegada no sumaba la parada de 1 h para comer.
   - Resultado: un bloqueo que empezaba durante la comida quedaba fuera, la unidad lo encontraba al reanudar y quedaba detenida hasta que terminara (4.5 h en el caso observado).
4. **Pedidos imposibles por los datos.**
   - Dos pedidos sintéticos tienen el destino bloqueado durante **todo** su plazo: c5648 en ALTA y c8975 el día 22.
   - Con la regla `esperar`, nadie puede entregarlos: el colapso se debe a los datos, no al algoritmo.

## 2. Configuración que cumple el requisito

| Parámetro (`config/parametros.properties`) | Valor | Antes | Por qué |
|---|---|---|---|
| **Tolerancia en el límite** (código: `Simulador.TOLERANCIA_MIN`) | 10⁻⁶ min | 0 | Causa 1: una entrega en el mismo instante que el límite cuenta como a tiempo (SI-21). |
| `plan.holgura_min` | **60** | (no existía) | Causa 2: una entrega a tiempo con menos de 60 min de margen se penaliza (SI-20). |
| `plan.penalidad_holgura` | **200** soles/hora | (no existía) | Faltar 1 h de margen cuesta lo mismo que 25 km en auto. Así se prefiere una unidad más rápida cuando la barata llega al filo. Solo va a S, nunca a H. |
| **Ventana del tramo con alimentación** (código: `MapaVial.distanciaTramo(..., alimentacion)`) | sí | no | Causa 3: planificador y simulador usan la misma ventana, comidas incluidas (SI-22). |
| `red.destino_bloqueado` | **`no_evaluable`** | `esperar` | Causa 4: los pedidos imposibles por los datos se excluyen del colapso y se cuentan en `pedidos_inentregables_bloqueo` (SI-15). Hubo uno en ALTA y uno el día 22. |
| `simulacion.ta_ms` (Ta) | 2000 | igual | Sin cambio. |
| `simulacion.sa_min` (Sa) | 60 | igual | Sin cambio. |
| `estabilidad.penalidad_por_cambio` | 16 | igual | Sin cambio (Etapa 18). |
| `parciales.estrategia` | `urgentes` (plazo ≤ 8 h, partes de 8) | igual | Sin cambio. |
| Flota | 10 autos, 15 motos, 12 bicicletas | igual | Enunciado. |
| Velocidades | 40 / 25 / 12 km/h | igual | Enunciado (SI-12, por confirmar). |

Nota sobre `red.destino_bloqueado`: en la semana 08 se había fijado `esperar` por defecto. Se cambió a `no_evaluable` porque, con `esperar`, esos pedidos hacen imposible cumplir el requisito. Para volver atrás basta una línea: `red.destino_bloqueado=esperar`. Queda como pregunta para el profesor (pregunta 11) qué hacer con un pedido cuyo destino está cerrado durante todo su plazo.

Aporte de cada cambio (ALTA y ARCHIVO desde el día 1, 3 réplicas por algoritmo):

| Variante | Corridas que llegan a 5 días |
|---|---|
| Configuración anterior | 1/12 |
| + tolerancia | ARCHIVO 6/6; ALTA 5/6 con `no_evaluable` (Tabú réplica 1 colapsa a las 101.5 h por un pedido a bordo) |
| + tolerancia + holgura | **12/12** |
| + ventana con alimentación (configuración final) | 42/42 en todos los casos |

Antes de corregir la ventana del tramo, el AG colapsó en el tramo del día 8 (a las 55 y 70 h); con la corrección ya no.

## 3. Validación final (configuración de §2)

Escenario `SIM_5D` acelerado, 3 réplicas por algoritmo, en una sola PC con 3 procesos en paralelo.

| Nivel / tramo | Paq./día | Tabú: cumple | Tabú: costo en 5 días | Tabú: km | AG: cumple | AG: costo en 5 días | AG: km | Pedidos entregados |
|---|---|---|---|---|---|---|---|---|
| BAJA, día 1 | 461 | 3/3 | S/ 42 276 | 7 124 | 3/3 | S/ 37 595 | 5 982 | ~258 |
| MEDIA, día 1 | 922 | 3/3 | S/ 77 945 | 12 393 | 3/3 | S/ 74 915 | 11 424 | ~452 |
| ALTA, día 1 | 1 382 | 3/3 | S/ 128 507 | 21 367 | 3/3 | S/ 125 235 | 20 516 | ~702 |
| ARCHIVO, día 1 | 1 026 | 3/3 | S/ 89 648 | 14 433 | 3/3 | S/ 83 072 | 13 070 | ~528 |
| ARCHIVO, día 8 | 1 026 | 3/3 | S/ 96 733 | 15 793 | 3/3 | S/ 89 854 | 14 158 | ~524 |
| ARCHIVO, día 15 | 1 026 | 3/3 | S/ 88 738 | 14 751 | 3/3 | S/ 86 663 | 13 949 | ~528 |
| ARCHIVO, día 22 | 1 026 | 3/3 | S/ 82 483 | 13 896 | 3/3 | S/ 78 665 | 12 905 | ~514 |

- En todas las corridas, el 100 % de los pedidos llegó en plazo.
- Pedidos imposibles excluidos: 1 en ALTA y 1 en el tramo del día 22.
- El costo incluye solo los km recorridos (costo por km de cada tipo); las penalidades de estabilidad y de holgura son internas del planificador y no se suman.
- «Pedidos entregados» es el promedio de ambos algoritmos; la diferencia entre ellos es de menos de 1 %.

## 4. Características de cada algoritmo (promedio de las 42 corridas)

| Indicador | Búsqueda Tabú | Algoritmo Genético |
|---|---|---|
| Cumple 5 días | 21/21 | 21/21 |
| **Costo por pedido entregado** | **S/ 171.7** | **S/ 162.4** (5.4 % más barato) |
| km por pedido | 28.3 | 25.9 |
| Pedidos en plazo | 100 % | 100 % |
| Cambios de unidad al replanificar (en 5 días) | **~36** (muy estable) | ~343 (reasigna mucho) |
| Tiempo por replanificación | 2.0 s (usa todo Ta) | 2.0 s (usa todo Ta) |

**Cómo leerlo:**
- **AG:** sale **más barato** en todos los niveles y tramos, entre 2.2 % (ALTA) y 12.3 % (BAJA) menos por pedido; el ahorro es mayor con poca carga. Arma rutas más cortas porque decodifica la secuencia completa en cada ciclo. El costo es la **estabilidad**: cambia la unidad asignada unas 10 veces más que Tabú, lo que en la operación real implica más reasignaciones de choferes.
- **Tabú:** parte del plan vigente y hace movimientos locales, así que es **muy estable** (pocos cambios entre ciclos). Gasta algo más en km.
- **Colapso:** ninguno de los dos colapsa en 5 días con esta configuración, así que el requisito no distingue entre ellos. La comparación de «hasta cuándo aguanta» es el experimento hasta el colapso (escenario EXPERIMENTO / COLAPSO).

## 5. Cómo reproducir la verificación

En la raíz del proyecto, un algoritmo o nivel por comando (tarda unos 4 min por corrida).

```bash
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --escenario SIM_5D --acelerado si --archivo datos/ventas202609_SINTETICO_MES.txt --bloqueos datos/202609_SINTETICO.bloqueadas --niveles ARCHIVO --replicas 3 --salida cinco_dias_SINTETICO.csv
```

- `--niveles BAJA,MEDIA,ALTA,ARCHIVO`: el nivel `ARCHIVO` usa el archivo de ventas **tal cual**, sin remuestreo.
- `--dia-inicio N`: empieza la simulación de 5 días el día N del mes.
- Columnas nuevas del CSV: `holgura_min`, `penalidad_holgura`, `dia_inicio`, `horas_desde_inicio` (el colapso contado desde el inicio de la ventana; 120 = cumple).
- Con los datos oficiales, cambiar `--archivo` y `--bloqueos`.

Resultados de esta validación: `verificacion_etapas16a21/cinco_dias/final/` (y la comparación de variantes en `antes/`).
