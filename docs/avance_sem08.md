# Avance semana 08: etapas 16 a 21

Cada etapa se cierra con: compilación, todas las pruebas JUnit, un resumen aquí y un commit que cita la etapa.

## Etapa 16: control de versiones

- Repositorio git en `PaqRap` con `.gitignore`: compilación, `__pycache__`, `verificacion_*`, `analisis/salida*`, `datos/generados`, PNG y CSV de la raíz, IDE y `obsoleto_sem06`.
- **Historial reconstruido** en la rama `historial`, a partir de las copias en disco:
  - `v-sem06-expuesto` (desde `PaqRap_sem06_expuesto`);
  - `v-sem07-referencia` (desde `PaqRap_version_referencia_sem07`).
- `main` parte de ahí. Primer commit: "Estado etapas 8-15 (sem07)".
- `docs/control_configuracion.md`: elementos de configuración, convención de etiquetas y dónde está cada copia. Es insumo para el plan IEEE 828.
- Nota: en `v-sem07-referencia`, `BusquedaTabu` quedó en `Busquedatabu.java` (Windows no distingue mayúsculas); en `main` se corrigió.

## Etapa 17: destino bloqueado (AV-10, pregunta 11)

- **17.1** Parámetro `red.destino_bloqueado = esperar | nodo_vecino | no_evaluable` (por defecto `esperar`, el comportamiento anterior); argumento `--destino-bloqueado`.
  - `nodo_vecino` (**SI-14**): si el destino está bloqueado al salir o a la llegada estimada, la entrega se hace desde el nodo adyacente no bloqueado más cercano por la red, sin esperar (`Contexto.puntoDeEntrega`).
  - `no_evaluable` (**SI-15**): al llegar el pedido, si su destino está bloqueado durante toda la ventana [registro, hora límite] (`MapaVial.bloqueadoDurante`), queda "inentregable por bloqueo". No se planifica, se excluye del colapso y se cuenta en la columna `pedidos_inentregables_bloqueo`.
- **17.2** `--excluir-destinos-bloqueados si`: `GeneradorCarga` descarta los pedidos sorteados cuyo destino queda bloqueado durante toda su ventana. Solo se aplica si el archivo de ventas es SINTÉTICO; con datos oficiales se avisa y no se filtra. El archivo generado lleva `_sinDestBloq` en el nombre.
- **17.3** El análisis mantiene la tabla de causas de colapso y agrega el análisis completo repetido sin las corridas con causa "destino bloqueado" (subcarpeta `sin_destino_bloqueado/`); se reportan ambos. Con la corrida de la Etapa 14.2 se excluyen 3 de 6 corridas.
- **17.4** `DestinoBloqueadoTest` (4 pruebas: esperar, nodo_vecino, no_evaluable frente a esperar en el simulador, y exclusión en el generador). **Total: 51 pruebas, todas pasan.**

## Etapa 18: penalidad de estabilidad

- **18.1** `estabilidad.penalidad_por_cambio = 16` soles por cada entrega que cambia de unidad frente al plan vigente (**SI-16**; argumento `--penalidad-estabilidad`; columna `penalidad_estabilidad`).
  - Se suma a S dentro de `Compartido.evaluarRuta`, **nunca a H**, así que ninguna entrega a tiempo se sacrifica por estabilidad. Rige en ambos algoritmos porque los dos evalúan con `evaluarRuta`.
  - Por qué 16 soles: equivale a 2 km en auto (8 soles/km) o a unos 2.7 km en moto. Solo se cambia de unidad si eso ahorra más de unos 2 km de recorrido.
  - Con 0 se desactiva. En el modo estático y en `Main` no hay plan vigente, así que no influye.
- **18.2** `AlgoritmoGenetico.asignarTramo`: si dos opciones empatan en tardanzas y costo, se prefiere la unidad que ya tenía más entregas del tramo. Prueba: `ReplanificacionTest.penalidadDeEstabilidadSumaASyNoAH`.
- **18.3** Medición: 3 réplicas × 3 niveles × 2 algoritmos × penalidad {0, 16}, con Ta = 1000 ms, datos SINTÉTICOS y `red.destino_bloqueado = no_evaluable`.
  - Salidas en `verificacion_etapas16a21/estabilidad/`.
  - Las 6 combinaciones de nivel y penalidad corrieron **en paralelo** en la misma PC. Por eso el tiempo de CPU por ciclo fue menor que con la PC libre.

*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***

| Nivel | Alg. | Penal. | Censuradas | colapso_h (media ± de) | cambios_de_unidad | cambios / 100 entregas | costo_acumulado | costo / pedido |
|---|---|---|---|---|---|---|---|---|
| BAJA | TABU | 0 | 3/3 | 720.0 ± 0 | 292.3 | 18.1 | 288 183 | 178.6 |
| BAJA | TABU | 16 | 3/3 | 720.0 ± 0 | **141.3** | 8.8 | 289 838 | 179.6 |
| BAJA | AG | 0 | 2/3 | 510.6 ± 362.7 | 1 853.3 | 159.2 | 182 636 | 156.7 |
| BAJA | AG | 16 | 3/3 | 720.0 ± 0 | **1 043.3** | 64.8 | 263 335 | 163.4 |
| MEDIA | TABU | 0 | 0/3 | 196.1 ± 41.3 | 45.3 | 6.0 | 145 015 | 186.7 |
| MEDIA | TABU | 16 | 0/3 | 173.2 ± 64.4 | **36.0** | 5.5 | 126 204 | 186.1 |
| MEDIA | AG | 0 | 0/3 | 150.3 ± 0 | 1 418.7 | 250.6 | 98 811 | 174.6 |
| MEDIA | AG | 16 | 0/3 | 150.3 ± 0 | **351.0** | 61.8 | 102 475 | 180.5 |
| ALTA | TABU | 0 | 0/3 | 147.8 ± 45.9 | 37.0 | 4.7 | 167 064 | 186.3 |
| ALTA | TABU | 16 | 0/3 | 155.2 ± 8.5 | **28.7** | 3.0 | 177 816 | 185.9 |
| ALTA | AG | 0 | 0/3 | 129.1 ± 54.9 | 1 192.7 | 159.9 | 142 689 | 184.9 |
| ALTA | AG | 16 | 0/3 | 175.4 ± 29.2 | **346.0** | 31.3 | 207 056 | 186.7 |

Cómo leer la tabla: `costo_acumulado` depende de cuánto dura la corrida (una corrida que colapsa más tarde recorre más), así que para comparar costos conviene `costo / pedido` entregado.

**Resultados**
1. **La penalidad reduce mucho la inestabilidad.**
   - AG: de −44 % (BAJA) a −75 % (MEDIA) en cambios de unidad.
   - Tabú: de −20 % a −52 %.
   - El AG sin penalidad reasigna mucho (160 a 250 cambios por cada 100 entregas) porque decodifica cada cromosoma desde cero. Tabú parte del plan vigente con movimientos locales y ya era estable (3 a 18 por cada 100).
2. **Costo:** el AG gasta algo más por pedido con la penalidad (+3 % en MEDIA, +4 % en BAJA): conserva asignaciones un poco más largas. En Tabú no hay diferencia apreciable.
3. **Colapso:** no hay un adelanto sistemático.
   - Con la penalidad, el colapso llega más tarde en ALTA-AG (129 → 175 h) y en BAJA-AG (una corrida colapsaba y ahora ninguna). En ALTA-Tabú (148 → 155 h) y MEDIA-AG (150.3 en ambos) queda igual.
   - MEDIA-Tabú baja en promedio (196 → 173 h), pero con 3 réplicas y desviaciones de 41 a 64 h no es concluyente. Réplica por réplica: 220 → 150, 220 → 123 y 148 → 246.
   - **Mecanismo por el que la penalidad podría adelantar el colapso:** H va primero, así que la penalidad nunca acepta una tardanza **en el plan del ciclo**. Pero, entre planes con el mismo H, conserva asignaciones que dejan a las unidades peor ubicadas para los pedidos **futuros**, que el planificador aún no ve. Es un efecto miope que puede aparecer con cargas altas.
   - Por eso el parámetro queda **configurable** (0 lo desactiva) y se recomienda medirlo en el experimento real con más réplicas.
4. **Colapsos por "destino bloqueado" pese a `no_evaluable`** (16 de las 24 corridas de ALTA y MEDIA).
   - Todos se deben a solo tres pedidos (c9862, c4679 y c8269). Un ejemplo: c9862, registrado a las 142.33 h, con plazo de 8 h y destino (51,35) bloqueado de 143.88 a 150.87 h. Había **93 min** para entregarlo antes del bloqueo, así que según SI-15 no es inentregable.
   - Si ninguna unidad llega en ese margen, con la regla `esperar` llega tarde y la causa es "destino bloqueado". Todo el AG en MEDIA colapsa en ese pedido, por eso su desviación es 0.
   - Sin esas corridas quedan muy pocas por grupo (1 o 2); la tabla completa está en `tabla_estabilidad.csv`. Queda como pregunta para el equipo si conviene priorizar estos pedidos con ventana corta antes de un bloqueo (ver pendientes).

## Etapa 19: experimento en tres PC, calibración, potencia y validación

- **19.1** Scripts por PC:
  - `ejecutar_pc1.bat` (BAJA), `ejecutar_pc2.bat` (MEDIA), `ejecutar_pc3.bat` (ALTA), con la lógica común en `ejecutar_nivel.bat`;
  - compilan si hace falta, verifican que existan las entradas y corren `ValidadorEntradas` (si hay errores, se detienen);
  - ejecutan el experimento con `resultados_<PC>_<etiqueta>_<nivel>.csv` y registran el SHA-256 de las entradas en `*_hashes.txt` (Java) y en `*_certutil.txt` (Windows).
  - Probado en esta PC con `ejecutar_pc3.bat`: los hashes de Java y de certutil coinciden (`verificacion_etapas16a21/scripts_pc/`).
- **19.2** `--calibrar-evaluaciones si`: mide cuántas evaluaciones hace cada algoritmo en Ta (**SI-17**) y fija ese tope por algoritmo (`modo_parada = evaluaciones_calibradas`).
  - En esta PC, con Ta = 1000 ms, sobre 15 entregas del nivel BAJA: **TABU = 83 759, AG = 10 386 evaluaciones**.
  - La calibración se hizo con la PC cargada (otras 6 corridas en paralelo). En el experimento real hay que calibrar con la PC libre y **la misma** calibración para las 3 PC; si no, los topes dependen de la carga.
  - Corridas calibradas en BAJA (1 réplica por algoritmo): Tabú y AG quedaron censuradas a las 720 h; tardaron 34 y 37 min, con la PC cargada.
- **19.3** `analisis/potencia.py` con los datos de la Etapa 18: diferencia de 24 h en `colapso_h`, α = 0.05, potencia 0.8, t de dos colas con varianzas distintas; tiempos reescalados a Ta = 2000 ms.

*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***

| Nivel | Penal. | de Tabú (h) | de AG (h) | Réplicas por algoritmo | ¿Alcanzan 5? | min por corrida | h de cómputo (2 × n) |
|---|---|---|---|---|---|---|---|
| BAJA | 16 | 0 | 0 | 2 (todas censuradas) | sí* | 24.1 | 1.6 |
| MEDIA | 16 | 64.4 | 0 | **58** | no | 5.5 | 10.7 |
| ALTA | 16 | 8.5 | 29.2 | **14** | no | 6.0 | 2.8 |
| BAJA | 0 | 0 | 362.7 | 1 794* | no | 20.6 | — |
| MEDIA | 0 | 41.3 | 0 | 25 | no | 5.9 | 4.9 |
| ALTA | 0 | 45.9 | 54.9 | 71 | no | 5.0 | 11.7 |

Notas sobre la tabla:
- \* En BAJA casi todas las corridas quedan censuradas a los 30 días. La t sobre `colapso_h` no sirve: una sola corrida que colapsa (92 h frente a 720 h) dispara la varianza. En BAJA hay que comparar con Kaplan-Meier y log-rank (ya en `analisis_experimento.py`) o por la proporción de censuradas.
- Las desviaciones salen de **3 réplicas por grupo**, así que el n es solo orientativo.
- Los minutos por corrida se midieron con 6 procesos en paralelo, así que sobrestiman lo que tardaría una PC libre.

**Conclusión:** **5 réplicas no alcanzan** para detectar 24 h en MEDIA y ALTA con la variabilidad observada. Con la penalidad por defecto harían falta unas 14 en ALTA y unas 58 en MEDIA.
- El valor de MEDIA está inflado porque todo el AG colapsa en el mismo pedido (de = 0) y Tabú varía mucho.
- Cómputo con 5 réplicas por algoritmo y Ta = 2000 ms: unas 4 h en la PC de BAJA (corridas censuradas de 30 días), unas 0.9 h en MEDIA y 1.0 h en ALTA.
- Con 15 réplicas: unas 12 h en BAJA, unas 2.8 h en MEDIA y unas 3 h en ALTA. Con las 3 PC en paralelo, lo que tarde la más lenta.
- **Recomendación:** al menos 15 réplicas por algoritmo en MEDIA y ALTA, y en BAJA reducir el horizonte o analizar con supervivencia. La decisión depende del tiempo disponible y queda para el equipo.
- **19.4** `docs/protocolo_experimento.md`: pasos del 0 al 8, desde los requisitos hasta qué se lleva al IEN.
- **19.5** `ValidadorEntradas`:
  - revisa el formato de ventas, bloqueos y mantenimiento, y que las coordenadas estén dentro de la retícula;
  - detecta nodos inalcanzables con un BFS exacto por intervalo de bloqueo (sale con código 1 si hay errores);
  - `ValidadorEntradasTest` tiene 4 pruebas; los datos sintéticos del mes son válidos.

## Etapa 20: documentación para defender el código

- **20.1** `docs/guia_del_codigo.md`:
  - mapa de clases por módulo;
  - flujo de una corrida del simulador y de `replanificar` paso a paso, con los nombres de los métodos;
  - flujo de una llamada a Tabú y al AG con estado;
  - evaluación de una ruta (`Compartido.evaluarRuta` + `Contexto`);
  - traza a mano del ejemplo del profesor (`AveriaTest`): avería tipo 1 de TM01 en el minuto 36; TA01 sale del central, trasvasa en (42,14) y entrega a los **111 min** (límite 180). Sin el auto y con avería tipo 2, el colapso ocurre exactamente en el minuto 180 con la causa "a bordo de unidad averiada".
- **20.2** Diagramas en Mermaid a partir del código real:
  - `docs/diseno/diagrama_clases.md`: tres vistas (modelo y evaluación; algoritmos; simulador e integración); los atributos y métodos se verificaron contra las fuentes;
  - `docs/diseno/diagrama_secuencia_replanificacion.md`: un ciclo de `replanificar`, incluida la avería externa y la lectura de la instantánea.
- **20.3** `docs/modulos_por_integrante.md` (**propuesta**): 6 módulos con un responsable principal y un respaldo. Tabú: Arzapalo + Alcca. AG: Alvarado + Torres. Simulador, mapa, averías y experimento, repartidos entre los cuatro. Cada módulo tiene 5 preguntas del JP con respuestas que citan la clase y el método.
- **20.4** Cabeceras: todas las clases nuevas desde `v-sem07-referencia` (`Averia`, `Contexto`, `EscritorJson`, `GeneradorDatosSinteticos`, `Hito`, `Mantenimiento`, `MapaVial`, `Reloj`, `ServicioSimulacion`, `ValidadorEntradas`), más `Simulador` y `Compartido`, ya tenían comentario en español con su propósito. Ahora llevan además una línea "Supuestos: SI-xx".
  - Los supuestos nuevos **SI-14 a SI-19** se agregaron a la tabla de `docs/propuesta_cambios_IEN.md`: nodo vecino, no evaluable, penalidad de estabilidad, instancia de calibración, avería externa y una simulación por JVM.
- Sin cambios de comportamiento: **60 pruebas, todas pasan.**

## Etapa 21: integración con el visualizador (sin frontend)

- **21.1** `docs/propuesta_arquitectura_integracion.md`: tres opciones, con pros, contras y esfuerzo.
  - A: el mismo proceso difunde la instantánea por un canal bidireccional.
  - B: el mismo proceso sirve HTTP simple (consulta periódica o eventos del servidor).
  - C: un proceso por escenario y un coordinador.
  - Cubre cómo se conectan SIM_5D, COLAPSO y DIA_A_DIA, la vista en varios dispositivos (difusión de la instantánea completa), las averías registradas desde el visualizador y el cambio de velocidad en caliente (P6, P16).
  - No elige framework. Deja como criterios de decisión: escenarios simultáneos, latencia aceptable y experiencia del equipo.
- **21.2** Capa independiente de la tecnología web (commit `8926be9`):
  - `Contexto` pasa a ser por hilo (`ThreadLocal`);
  - `Reloj.esperarHasta` es interrumpible y se agrega `ahora()`;
  - `Simulador` suma `crear`/`ejecutar`, un cerrojo, `inyectarAveria`, `cambiarVelocidad` (desde la siguiente replanificación), `detener` e `instantaneaJson`;
  - nuevas clases `EscritorJson` y `ServicioSimulacion`.
  - `IntegracionTest` tiene 4 pruebas: JSON completo y bien formado, avería externa, cambio de velocidad y servicio en otro hilo con avería en caliente.
- **Pendiente:** registrar pedidos en vivo (`inyectarPedido`) para DIA_A_DIA, porque el formato no está definido. Varios escenarios en una JVM requieren quitar el estado estático de los algoritmos (SI-19).

## Etapa 22: cinco días sin colapso (requisito prioritario)

Detalle completo, con tablas, en `docs/configuracion_5_dias.md`.

- **Diagnóstico:** con la configuración anterior, SIM_5D colapsaba en ALTA (101 a 104 h) y con el archivo de ventas tal cual (día 1, a las 14.4 h). No era falta de flota; eran cuatro causas:
  1. un empate numérico en la hora límite;
  2. planes con margen cero (una bicicleta que llega justo a la hora límite);
  3. una ventana de tramo que no sumaba la hora de alimentación, de modo que la unidad quedaba detenida por un bloqueo que empezaba mientras comía;
  4. pedidos imposibles por los datos (destino bloqueado durante todo su plazo).
- **Cambios:**
  - `Simulador.TOLERANCIA_MIN` y `vencido()` (SI-21);
  - holgura de seguridad en S, `plan.holgura_min = 60` y `plan.penalidad_holgura = 200` (SI-20);
  - `MapaVial.distanciaTramo/caminoTramo(..., alimentacion)` y `Contexto.finConAlimentacion` (SI-22);
  - `red.destino_bloqueado = no_evaluable` por defecto (antes `esperar`);
  - nivel `ARCHIVO` (ventas tal cual) y `--dia-inicio N` para simular 5 días desde cualquier día del mes;
  - columnas nuevas: `holgura_min`, `penalidad_holgura`, `dia_inicio`, `horas_desde_inicio`.
- **Validación (SINTÉTICO):** **42/42 corridas** llegan a 5 días, con Tabú y AG, en BAJA, MEDIA, ALTA y ARCHIVO (días 1, 8, 15 y 22), 3 réplicas, 100 % de pedidos en plazo.
- **Costo por pedido:** el AG sale 5.4 % más barato (S/ 162.4 frente a S/ 171.7 de Tabú), pero cambia de unidad unas 10 veces más.
- **Pruebas:** `CincoDiasTest` tiene 4 pruebas (límite exacto, holgura en S y no en H, preferencia por la unidad con margen, ventana con alimentación). **Total: 64 pruebas, todas pasan.**
- `Main` sin cambios (708/672/672 y 816/816/816): la holgura solo rige con estado, y la ventana con alimentación solo con mapa.
