# Preguntas para el profesor (pendientes de confirmar)

Cada pregunta indica qué supusimos mientras tanto y dónde se cambia. Todas las suposiciones son **parámetros configurables** en `config/parametros.properties`, salvo donde se indica.

| # | Pregunta | Supuesto actual (provisional) | Dónde se cambia |
|---|---|---|---|
| 1 | En la **simulación de 5 días**, ¿se detiene al primer colapso (definición binaria P3) o sigue hasta completar los 5 días y reporta los incumplimientos? | Se detiene al colapsar. | `sim5d.detener_en_colapso=si` o `no` |
| 2 | ¿Cuáles son las **reglas de generación de averías** (frecuencia por día y por tipo, qué unidades, en qué horarios)? ¿El formato del archivo de averías será `##d##h##m:TTNN:tipo`? | Generador sintético: 2 averías/día en promedio; tipos 1/2/3 con probabilidad 0.6/0.3/0.1; hora y unidad al azar. Formato provisional `##d##h##m:TTNN:tipo`. | `averias.*`; clase `Averia` (formato) |
| 3 | ¿El **tiempo de trasvase** entre una unidad averiada y la que recoge su carga es de 30 minutos? ¿Depende de la cantidad de paquetes? | 30 min fijos por parada de trasvase. | `trasvase.minutos` |
| 4 | ¿Cuánto dura el **mantenimiento preventivo** por tipo de unidad (la hoja de preguntas lo tiene pendiente)? ¿La unidad debe estar en el central ese día? | 24 h (de 00:00 a 23:59 del día indicado), en el central, para los tres tipos. | `mantenimiento.horas.AUTO` / `MOTO` / `BICICLETA` |
| 5 | ¿Qué **velocidades** rigen: las del enunciado (auto 40, moto 25, bicicleta 12 km/h) o las de la hoja auxiliar "Aux-Flota-SIN-VALOR" (20 / 40 / 14 km/h, marcada REVISAR)? | Las del enunciado. Con las de la hoja cambian C_max y todos los resultados. | `velocidad.AUTO` / `MOTO` / `BICICLETA` |
| 6 | ¿El **experimento numérico** debe incluir averías? Si sí, ¿con qué frecuencia? | Desactivadas por defecto (el IEN v01 las excluía). Se pueden activar con `--averias si`, con el mismo archivo para ambos algoritmos. | Argumento `--averias` |
| 7 | ¿Están permitidos los **encuentros entre dos unidades en movimiento** (trasvase en ruta, sin que ninguna esté averiada)? | No implementados. Solo se trasvasa desde una unidad averiada detenida. | Mejora futura (docs/avance_sem07.md) |
| 8 | ¿Cuánto tarda la **carga en un almacén**? | 0 min (no figura en el enunciado). | `recarga.minutos` |
| 9 | ¿La **1 h de alimentación** es en un horario fijo por turno? ¿La unidad debe estar en un almacén? | 1 h a las 03:00, 11:00 y 19:00 (4 h después de cada cambio de turno), dondequiera que esté la unidad. | `turnos.alimentacion`; `Contexto.avanzar` |
| 10 | ¿La **capacidad teórica diaria** para los niveles de carga es la de la hoja auxiliar (1 536 paquetes/día) o se calcula? | Cálculo propio (con datos sintéticos da 1 536, con otra composición por tipo). | `carga.cmax_fuente=propio` o `hoja` |
| 11 | Si el **destino** de una entrega está bloqueado cuando llega la unidad, ¿se espera a que se desbloquee, se entrega desde el nodo vecino, o se considera no entregable? **Es importante:** con los datos sintéticos, en el nivel BAJA ambos algoritmos colapsaron en el mismo minuto porque el cliente estaba en un nodo bloqueado desde antes de pedir y hasta después de su plazo. Ese colapso lo deciden los datos, no el algoritmo. | Se espera al desbloqueo; el colapso se reporta con la causa "destino bloqueado". | `MapaVial.finBloqueo`, `Contexto.esperaDestino` |
| 12 | ¿Nos pueden entregar los **archivos oficiales** (`ventas2026mm`, `aaaamm.bloqueadas`, `mant.preventivo`)? | Se usan datos sintéticos marcados como tales. | `--archivo`, `--bloqueos`, `--mantenimiento` |
