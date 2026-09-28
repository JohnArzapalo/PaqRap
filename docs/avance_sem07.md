# Avance semana 07: etapas 8 a 15 y 23 a 29 (indicaciones nuevas del profesor)

Cada etapa se cierra con: compilación, todas las pruebas JUnit y un resumen aquí.
Donde una indicación del profesor choca con el IEN v01 o con una decisión anterior, **prevalece la del profesor** y se anota en la sección "Choques resueltos".

## Etapa 8: preparación y datos

- **8.1** Copia congelada en `DP1/PaqRap_version_referencia_sem07` con `LEEME.md` (tabla de Main vigente: I1 708 / 672 / 672; I2 816 / 816 / 816, todas con H = 0).
- **8.2** No hay archivos oficiales en `datos/`. Se generaron `datos/ventas202609_SINTETICO_MES.txt` (3 600 pedidos, 30 días, 24 h con más densidad de día) y `datos/202609_SINTETICO.bloqueadas` (241 polilíneas abiertas; se descartaron 48 candidatos por alcanzabilidad o por tocar un almacén). Parámetros en `docs/datos_sinteticos.md`. Generador: `GeneradorDatosSinteticos` (semilla fija).
- **8.3** `GeneradorCarga` ya remuestrea conservando la hora del día y la posición. Con el archivo mensual, la carga se reparte en las 24 h. El modo simulación usa el archivo mensual por defecto y genera 30 días por nivel (tope de seguridad).
- `MapaVial` (Etapa 9.1) se escribió antes para que el generador valide los bloqueos con el mismo BFS del simulador.
- Pruebas: 28 de 28 pasan.

## Etapas 9 a 12 (núcleo con estado)

Se implementaron juntas porque comparten el mismo cambio de fondo: **una sola función de evaluación de rutas** (`Compartido.evaluarRuta`) que usa un **contexto de planificación** (`Contexto`). Con el contexto por defecto, reproduce exactamente el modelo anterior; se verificó con `Main`, que da los mismos 708 / 672 / 672 y 816 / 816 / 816.

**Etapa 9: red vial con bloqueos**
- `MapaVial`:
  - retícula de 71 × 51 nodos y lector de `aaaamm.bloqueadas` (todos los nodos de cada tramo, extremos incluidos);
  - consulta `bloqueado(x, y, t)`;
  - BFS que no atraviesa nodos bloqueados;
  - caché LRU por (intervalo de estado constante, origen), guardada como `short[]`.
- **Destino bloqueado:** la entrega espera a que el nodo se libere (`finBloqueo`).
- **Aproximación del planificador (9.3):** cada tramo evita los bloqueos activos al salir **y los que empiezan antes de su llegada estimada**: la unión de los estados de la ventana del tramo, que se amplía si el rodeo la alarga. Es conservador.
- **Movimiento (9.4):** el simulador mueve las unidades nodo a nodo por ese mismo camino. Si un nodo se bloquea antes de llegar (por ejemplo, porque la alimentación alargó el tramo), la unidad se detiene en el nodo anterior y se replanifica en ese instante.
- **Resguardos:**
  - Si el bloqueo aparece justo en el instante en que se compromete el plan, la unidad espera en su nodo; replanificar en ese momento repetía el mismo plan y **se detectó un ciclo infinito en una prueba de humo, ya corregido**.
  - Una replanificación por evento que se repite más de 3 veces en el mismo instante se omite y queda registrada.
- **Parámetro:** `red.bloqueos=si|no`.

**Etapa 10: almacenes, recargas y entregas parciales**
- **Stock:** central infinito; Nor-Oeste y Este con 1 000, repuestos a las 23:59:59. El stock se descuenta al cargar. El plan no puede cargar más que el stock de un almacén: es una restricción dura en `evaluarSolucion`, y `mejorInsercion` descarta esas opciones.
- **Varios viajes:** la ruta es una secuencia de viajes; cada viaje empieza con una parada `RECARGA`, o con la salida desde un almacén (carga implícita).
  - **AG:** `splitConEstado` corta la permutación en tramos y elige, para cada tramo, la unidad y el almacén o trasvase que minimizan (tardanzas, costo) reales desde el estado de la unidad.
  - **Tabú:** operador `recarga` (insertar, cambiar de almacén o quitar una RECARGA).
- **Reparto del stock dentro de un plan:**
  - **AG:** reserva en el orden en que se asignan los tramos.
  - **Tabú y C&W:** se verifica la suma de lo cargado en todo el plan.
  - **Simulador:** descuenta cuando la unidad efectivamente carga.
- **Entregas parciales flexibles (10.3):** estrategia `urgentes`.
  - Las entregas pendientes de más de 8 paquetes con plazo de 8 h o menos se ordenan por urgencia.
  - Las primeras quedan enteras, tantas como autos libres haya en la próxima hora; el resto se divide en partes de hasta 8 (lo que cabe en una moto).
  - Cada parte puede ir en cualquier unidad y suma 1 h de acondicionamiento.
  - **Efecto:** medido en la Etapa 14.

**Etapa 11: replanificación con estado (P5, P6, P16)**
- **Planificador:** recibe `EstadoPlanificacion` (contexto, entregas, flota) y devuelve el plan de **todas** las unidades operativas. El contexto lleva el instante, la posición, la hora de inicio y la carga de cada unidad, los almacenes con stock, las unidades averiadas y el plan vigente.
- **Restricciones:**
  - una entrega a bordo solo la entrega su unidad (salvo trasvase);
  - una unidad en ruta empieza en el siguiente nodo de su camino, o termina la actividad en curso.
- **Tabú** parte del plan vigente reparado (se quita lo hecho y se insertan los pedidos nuevos con la inserción de C&W). C&W solo se usa en el primer ciclo.
- **AG:** siembra el 30 % de la población con el plan vigente codificado y variaciones suyas. Nunca devuelve algo peor que ese plan.
- **Estabilidad:** `cambios_de_unidad`, las entregas que cambian de unidad entre ciclos consecutivos.

**Etapa 12: averías y trasvase**
- **Formato provisional (por confirmar):** `##d##h##m:TTNN:tipo`, con lector y generador con semilla.
- **Reglas por tipo** (sección 2 del pedido):
  - la unidad averiada queda como almacén temporal en su nodo;
  - otra unidad puede hacer `TRASVASE` (30 min, parámetro `trasvase.minutos`) si le alcanza la capacidad;
  - al dejar el lugar, lo no trasvasado vuelve al stock del central (tipos 2 y 3); en el tipo 1, la unidad sigue con su carga.
- Una avería dispara una replanificación inmediata.
- **Mantenimiento preventivo:** lector de `mant.preventivo`. La unidad no se planifica en su ventana y sus rutas deben terminar antes. Duración por tipo: parámetro pendiente de confirmar.
- **Encuentros entre unidades en movimiento:** no implementados (mejora futura).

**Pruebas nuevas (19):** `MapaVialTest` (7), `ReplanificacionTest` (5), `AveriaTest` (4, incluido el ejemplo del profesor) y 3 nuevas en `SimuladorTest` (parciales, registro de eventos, DIA_A_DIA).

En la prueba del ejemplo del profesor, la moto se avería en el minuto 36; el auto sale del central, trasvasa y entrega en el minuto 111, antes del límite de 180, sin colapso. Si nadie puede llegar, el colapso ocurre exactamente en el minuto 180, con la causa "a bordo de unidad averiada".

Total: **47 pruebas, todas pasan.**

## Etapa 13: colapso y escenarios

- **Colapso (P3):** el primer instante en que una entrega (unidad de producto) supera su hora límite sin entregarse, esté a bordo, en una averiada o sin asignar. Se detecta en el minuto exacto con una cola de plazos; la simulación termina ahí.
  - Se registran el pedido, la unidad y la causa: `sin asignar`, `a bordo de unidad averiada`, `bloqueo`, `capacidad` o `llegada tardía (a bordo | planificada)`.
- **Escenarios:** `--escenario EXPERIMENTO | SIM_5D | COLAPSO | DIA_A_DIA`.
  - `EXPERIMENTO` y `COLAPSO`: sin esperas, hasta el colapso o el tope `sim.horizonte_max_dias` (30); si no colapsa, queda censurada.
  - `SIM_5D`: 5 días con ritmo escalado (`sim5d.minutos_reales=30`). Se detiene al colapsar según `sim5d.detener_en_colapso=si`; con `no`, registra el primer colapso y sigue. `--acelerado si` quita las esperas.
  - `DIA_A_DIA`: reloj 1:1. Por ahora hay estructura (interfaz `Reloj`) y una prueba corta con un reloj de prueba.
- **Registro de eventos (13.3):** con `--eventos prefijo.csv` se escribe un CSV por corrida con salidas, entregas, recargas, trasvases, averías, bloqueos encontrados, replanificaciones y el colapso. Los pasos nodo a nodo no se escriben, para no inflar el archivo.

## Etapa 14: experimento alineado con el profesor

- **Modo simulación:** algoritmo × carga × réplicas en escenario EXPERIMENTO, con bloqueos del mes, almacenes intermedios, replanificación con estado, averías opcionales (`--averias si`, el mismo archivo para todas las corridas) y los mismos pedidos y bloqueos por nivel (`datos/generados/`).
- **Corrida corta (1 réplica, Ta = 1000 ms):**
  - colapsos entre el día 5 y el día 10; no hizo falta recalibrar;
  - **3 de 6 colapsos por "destino bloqueado"** (inevitables con la regla actual). Se agregó esa causa y se anotó como pregunta al profesor;
  - resultados en `docs/resultados_para_exposicion.md`.
- **Tiempo estimado:** ≈ 3 h para las 30 corridas en 1 PC; reparto por nivel en 3 PCs (`docs/capacidad_y_carga.md`).
- **Efecto de las parciales:** +27 h en MEDIA y +3.8 h en ALTA (1 réplica).

## Etapa 15: documentos

- `docs/propuesta_cambios_IEN.md`: reescrito con las indicaciones del profesor (C-01 a C-12, supuestos SI-01 a SI-13, amenazas AV-01 a AV-10). Mantiene la nota "Pendiente verificar contra el IEN v01".
- `docs/preguntas_para_el_profesor.md`: 12 preguntas con el supuesto vigente de cada una.
- `docs/resultados_para_exposicion.md`, `docs/capacidad_y_carga.md`, `docs/datos_sinteticos.md` y `docs/diseno_simulador.md` actualizados.
- Pruebas al cierre: **47 de 47 pasan.**

## Choques resueltos (prevalece el profesor)

| Tema | Antes (IEN v01 / decisión previa) | Ahora (indicación del profesor) |
|---|---|---|
| Colapso | Pedido original no entregado en plazo (criterio operativo propuesto) | Al menos una **unidad de producto** fuera de plazo (P3). Equivale a la primera entrega parcial tardía; la simulación termina en ese instante. |
| Bloqueos | IEN v01: una configuración estática (la ventana de 24 h con más tramos) | Bloqueos por archivo y **por horario** (P1) |
| Almacenes | Solo el central en el simulador mínimo | Almacenes intermedios con stock y recargas (P2, P10) |
| Replanificación | Rutas despachadas fijas (P16 pendiente) | Replanificar todas las unidades desde el plan vigente y el estado real (P5, P6, P16) |
| Entregas parciales | Solo bloques de 24 paquetes | División flexible entre unidades de cualquier tipo (P13-P14) |

## Criterio de comparación (indicación del profesor)

- Es normal que algunas corridas terminen en colapso logístico (un pedido no entregado dentro de su plazo).
- El mejor algoritmo es el de **menor porcentaje de corridas con colapso**. El equipo decidió calcularlo sobre la **simulación de 5 días (SIM_5D)**: cada corrida colapsa o no dentro de las 120 h. Falta la confirmación del profesor (pregunta 13).
- El tiempo hasta el colapso, el costo y la estabilidad quedan como criterios de desempate.
- Detalle del diseño: `CLAUDE.md` §5.

## Etapa 23: hora límite efectiva (SI-23)

- **Problema:** si el destino se bloquea antes de la hora límite y el bloqueo dura más allá de ella, llegar después del **inicio** del bloqueo obliga a esperar el desbloqueo y la entrega sale tarde.
  - H ya contaba esa entrega como tarde, porque `evaluarRuta` espera en el destino bloqueado.
  - Pero la holgura de seguridad (SI-20) medía el margen hasta la hora límite. Un plan que llegaba 5 minutos antes del bloqueo parecía tener horas de margen, y cualquier retraso lo convertía en colapso.
- **Cambio (en el evaluador común, igual para ambos algoritmos):**
  - `MapaVial.inicioBloqueo` y `MapaVial.limiteEfectivo`: si el destino está bloqueado en la hora límite, la hora límite efectiva es el inicio de ese bloqueo (los bloqueos encadenados cuentan como uno).
  - `Contexto.limiteEfectivo(pedido)`: la misma regla en horas relativas. Con `nodo_vecino` rige la hora límite, porque no se espera en el destino.
  - `Compartido.evaluarRuta`: la holgura se mide hasta la hora límite efectiva. Solo cambia S; la definición de «tarde» (H) no cambia.
  - La urgencia de los pedidos usa la hora límite efectiva: `Heuristicaconstructiva` (orden de C&W y de inserción), `Simulador.dividirUrgentes` y la inserción de faltantes en `Simulador.armarReparado`.
  - El colapso se sigue declarando en la hora límite real. Si el bloqueo dura más que el plazo, la replanificación decide con el estado real.
- **Pruebas:** `LimiteEfectivoTest` tiene 5 pruebas: inicio del bloqueo que cubre el plazo, bloqueos encadenados, horas relativas y regla de destino, holgura medida hasta la hora límite efectiva, y llegada dentro del bloqueo que cuenta como tarde. **Total: 69 pruebas, todas pasan.**
- `Main` no cambia (708/672/672 y 816/816/816), porque sin mapa la hora límite efectiva es la hora límite.
- **Pendiente:** repetir la verificación de SIM_5D (`docs/configuracion_5_dias.md` §5) y recalcular la potencia y el número de réplicas para proporciones pareadas.

## Etapa 24: sin estado estático en los algoritmos (SI-19 superado)

- **Problema:** `BusquedaTabu` y `AlgoritmoGenetico` guardaban en campos `static` su generador aleatorio, su presupuesto y sus contadores, y el cambio de velocidad en caliente modificaba `TipoUnidad` para todo el proceso. Dos simulaciones en el mismo proceso se pisaban, así que había que correr una por JVM (SI-19). Eso impedía correr en paralelo las muchas réplicas que exige el % de colapsos.
- **Cambio (la lógica de los algoritmos no cambia; solo dónde se guarda el estado):**
  - `BusquedaTabu` y `AlgoritmoGenetico` son **objetos**: `new BusquedaTabu(semilla)` y `new AlgoritmoGenetico(semilla)`, cada uno con su generador aleatorio, su presupuesto y sus contadores (`ultimasIteraciones`, `ultimasEvaluaciones`, etc.). Desaparece `setSemilla`.
  - Los parámetros (candidatos, pesos, población, búsqueda local) son constantes (`static final`) leídas una vez del archivo. `AlgoritmoGenetico.BUSQUEDA_LOCAL` se puede cambiar con `--busqueda-local` al iniciar, igual para todas las corridas.
  - `Planificador.tabu()` y `Planificador.genetico()` crean un objeto por llamada con la semilla del ciclo, que es lo que antes hacía `setSemilla`.
  - La velocidad configurada en `TipoUnidad` es `final`. Un cambio en caliente (P16) rige solo en su simulación: `Simulador.velocidad` y `Contexto.velocidad`.
  - `ExperimentoSimulacion`: opción `--hilos N` (N simulaciones a la vez; 1 por defecto). Las filas del CSV salen en el mismo orden aleatorio, con cualquier N.
  - `Main` usa un objeto por algoritmo para toda la demostración, así que la secuencia aleatoria es la de antes.
- **Verificación:**
  - **Mismos resultados que antes del cambio:** una simulación de 24 h (30 pedidos sintéticos, con bloqueos, semilla 1001, 400 evaluaciones) da exactamente el mismo costo, km, entregas, evaluaciones, iteraciones y cambios de unidad con el código de la etapa 23 y con el nuevo, para Tabú y para AG.
  - **Paralelo = serie:** SIM_5D, nivel BAJA, 2 réplicas × 2 algoritmos, 300 evaluaciones por ciclo. El CSV con `--hilos 2` es idéntico al de `--hilos 1`, salvo las columnas de tiempo real (`tiempo_real_ms`, `planificador_ms_*`). Tardó 25 s frente a 30 s. *(SINTETICO)*
  - `Main` no cambia (708/672/672 y 816/816/816).
- **Pruebas:**
  - `ReproducibilidadTest.simulacionesEnParaleloDanLoMismoQueEnSerie` corre 4 simulaciones a la vez (Tabú, AG, Tabú, AG) y exige el mismo resultado que en serie.
  - `IntegracionTest.cambioDeVelocidadEnLaSiguienteReplanificacion` verifica que el cambio de velocidad no afecta a otra simulación.
  - **Total: 70 pruebas, todas pasan.**
- **Cuidado con la parada por tiempo (Ta):** con `--hilos N` los hilos se reparten la CPU, así que cada planificador hace menos trabajo en el mismo Ta. Hay que usar N ≤ núcleos físicos, o la parada por evaluaciones, cuyo resultado no depende de N.
- **Observación al pasar (SINTETICO, no es resultado):** con solo 300 evaluaciones por ciclo (muy por debajo de Ta = 2 s), Tabú réplica 2 colapsó en BAJA a las 67.9 h por una «llegada tardía (a bordo)». Con poco presupuesto también aparecen colapsos; hay que tenerlo en cuenta al calibrar el experimento del % de colapsos.

## Etapa 25: situaciones por réplica, calibración de la carga y análisis del % de colapsos (SI-24)

- **Diseño (decisión del equipo):** la variable principal es el **% de corridas de SIM_5D con colapso** (indicación del profesor; pregunta 13). En cada nivel, la réplica r es **una situación propia**: una muestra de pedidos con semilla `semilla_base + 1000·(k+1) + r`. TABU y AG corren **exactamente la misma** situación, así que el diseño es pareado. Antes, todas las réplicas de un nivel usaban el mismo archivo, y el porcentaje solo habría medido el azar del algoritmo en una única situación.
- **Código:**
  - `ExperimentoSimulacion`: `Situacion` (archivo generado, sus pedidos se leen en el hilo de la corrida); `--situaciones por_replica|por_nivel` (parámetro `experimento.situaciones`, por defecto `por_replica`); `--cargas NOMBRE=fracción,...` para niveles a medida; columnas nuevas `situaciones` y `semilla_carga`; hash de cada situación.
  - `ejecutar_nivel.bat`: SIM_5D acelerado por defecto (`ESCENARIO=COLAPSO` para el complementario), `--situaciones por_replica`, `HILOS` y `REPLICAS=40`.
  - `analisis/analisis_experimento.py`:
    - sección 0 con el % de colapsos por nivel y algoritmo, IC de Clopper-Pearson, pares discordantes y McNemar exacta por nivel y global;
    - GEE logística con los pares como grupos;
    - gráfico `pct_colapso.png`;
    - costo y km **por pedido entregado** (el costo acumulado premiaba al que colapsa antes);
    - comparaciones secundarias **pareadas** (t pareada o Wilcoxon);
    - regla: % de colapsos → tiempo hasta el colapso → costo por pedido → estabilidad.
  - `analisis/potencia.py`: pares necesarios para la McNemar exacta. Usa la fórmula de Connor, corregida con la potencia exacta por enumeración (verificada con Monte Carlo: 0.581 frente a 0.576). Incluye `--tabla` de planificación, estimación combinada con los niveles en transición y el modo anterior `--variable colapso_h`.
- **Pruebas:** `ExperimentoSimulacionTest` tiene 2 pruebas: por réplica, TABU y AG comparten situación y las réplicas difieren; por nivel, una sola situación. **Total: 72 pruebas, todas pasan.**

**Calibración de la carga (SIM_5D, Ta = 2 s, 6 hilos en 8 núcleos físicos) — SINTETICO, no válido para el informe:**

| Carga (% de C_max = 1 536 paq./día) | Pares | % colapso TABU | % colapso AG | % colapso total | min por corrida |
|---|---|---|---|---|---|
| 30 / 60 / 90 % (etapa 22) | 21 | 0 | 0 | 0 % | ~4 |
| 100 % | 4 | 25 | 50 | 38 % | 4.3 |
| 105 % | 4 | 50 | 75 | 62 % | 4.4 |
| 110 % | 4 | 100 | 25 | 62 % | 4.0 |
| 115 % | 4 | 75 | 75 | 75 % | 3.3 |
| 120 % a 180 % | 3 c/u | 100 | 100 | 100 % | 1.3 a 2.8 |

CSV: `barrido1_SINTETICO.csv` (120-180 %) y `barrido2_SINTETICO.csv` (100-115 %), en la carpeta temporal de la sesión (no versionados). Se repiten con `--cargas` (`docs/protocolo_experimento.md` §4).

- **Lectura:**
  - La zona útil está entre **100 % y 115 % de C_max**. Por debajo, ninguna corrida colapsa en 5 días; por encima, todas.
  - Con 4 pares por nivel **no hay diferencia significativa** entre algoritmos. Global: solo TABU colapsa en 5 pares, solo AG en 4 (p = 1).
  - Las causas son «llegada tardía (planificada)» y «(a bordo)». No hubo colapsos por destino bloqueado.
- **Réplicas necesarias:**
  - La discordancia combinada es alta (psi = 0.56): si una corrida colapsa depende bastante del azar del algoritmo.
  - Detectar 20 puntos requiere **118 pares**: 40 réplicas por nivel para la decisión global con 3 niveles, o 118 por nivel para decidir en cada uno.
  - Tiempo: con 6 hilos, unos 55 min por nivel y PC (40 réplicas).
- **Pendiente:**
  - Fijar los tres niveles, con los datos oficiales y según la respuesta a la pregunta 14. Con los sintéticos, la propuesta sería alrededor de 95-100 %, 105 % y 115 % de C_max.
  - Correr el experimento completo.

## Etapa 26: niveles provisionales y ensayo completo del experimento (SI-25) — SINTETICO

- **Calibración al 95 % de C_max** (6 pares): colapsan 5 de 12 corridas (TABU 1/6, AG 4/6).
- **Niveles provisionales** (`config/parametros.properties`, SI-25; los del IEN eran 30 / 60 / 90 %):
  - para el ensayo se usaron BAJA 95 %, MEDIA 105 % y ALTA 115 %;
  - después del ensayo, **ALTA se bajó a 110 %**, porque 115 % casi satura (97 %) y aporta pocos pares discordantes.
- **Ensayo:** SIM_5D, situaciones por réplica, 3 niveles × 40 réplicas × 2 algoritmos = **240 corridas**, Ta = 2 s, 7 hilos (8 núcleos físicos), 2.6 h de reloj (4.6 min por corrida). Salidas en `verificacion_etapa26/` (no versionada): `ensayo_SIM_5D_SINTETICO.csv` y `analisis/`.

**Variable principal: % de corridas con colapso (McNemar exacta, pares TABU/AG en la misma situación):**

| Nivel | Pares | TABU | AG | Ambos | Solo TABU | Solo AG | Ninguno | p |
|---|---|---|---|---|---|---|---|---|
| BAJA (95 %) | 40 | 10 % [3, 24] | 20 % [9, 36] | 1 | 3 | 7 | 29 | 0.34 |
| MEDIA (105 %) | 40 | 45 % [29, 62] | 57.5 % [41, 73] | 12 | 6 | 11 | 11 | 0.33 |
| ALTA (115 %) | 40 | 100 % [91, 100] | 95 % [83, 99] | 38 | 2 | 0 | 0 | 0.50 |
| **Global** | 120 | 51.7 % | 57.5 % | | 11 | 18 | | **0.26** |

- **GEE** (colapso ~ algoritmo + nivel, pares como grupos): odds ratio TABU/AG = 0.645, p = 0.19.
- **Robustez:** sin los 10 colapsos por «destino bloqueado» (5 por algoritmo, en las mismas situaciones: los deciden los datos), el resultado es el mismo (p global = 0.26).
- **Lectura:** TABU colapsa algo menos que AG en BAJA y MEDIA (11 pares contra 18), pero **la diferencia no es significativa**. Con 120 pares solo se detectarían diferencias de unos 20 puntos, y la observada es de unos 6.

**Desempates (pruebas pareadas):**

| Variable | BAJA | MEDIA | ALTA |
|---|---|---|---|
| Tiempo hasta el colapso (log-rank) | sin diferencia (p = 0.19) | sin diferencia (p = 0.33) | sin diferencia (p = 0.96) |
| Costo por pedido (S/, TABU vs. AG) | 188.1 vs. 186.3, sin diferencia | 195.7 vs. 193.0, sin diferencia | 196.7 vs. 193.7, sin diferencia |
| km por pedido | AG 1.4 % menos (p = 0.01) | AG 1.6 % menos (p = 0.04) | sin diferencia |
| Cambios de unidad (mediana) | **TABU 14.5 vs. AG 185** (p < 10⁻¹⁹) | **TABU 12.5 vs. 174.5** | **TABU 11.5 vs. 117.5** |

- **Regla de decisión** (% de colapsos → tiempo hasta el colapso → costo por pedido → estabilidad): **TABU en los tres niveles, decidido por la estabilidad.** Es preliminar: son datos sintéticos.
- **Hallazgo que afecta la justicia de la comparación:**
  - **el AG usa en promedio 2 752 ms por replanificación, frente a Ta = 2 000 ms; TABU usa 2 003 ms.**
  - El AG revisa el presupuesto al terminar cada generación, y con estas cargas una generación es larga. Así recibe cerca de un 38 % más de cómputo que TABU.
  - Hay que corregirlo antes del experimento con los datos oficiales: revisar el reloj también dentro de la generación, en el evaluador de cada hijo.
- **Réplicas:**
  - Discordancia combinada psi = 0.24.
  - Detectar 20 puntos requiere 49 pares en total para la prueba global (≈ 17 por nivel con 3 niveles); 15 puntos requieren 90 en total.
  - La diferencia observada (unos 6 puntos) necesitaría varios cientos de pares. Si en los oficiales se mantiene, la decisión la tomarán los desempates.

## Etapa 27: el AG respeta Ta dentro de la generación

- **Problema (etapa 26):**
  - El AG revisaba el presupuesto solo al terminar cada generación. Con cargas altas una generación es larga, y más aún si varias corridas comparten la CPU.
  - En el ensayo (7 hilos, nivel MEDIA) el AG usaba **2 601 ms de media y hasta 3 823 ms** con Ta = 2 000 ms, mientras TABU usaba 2 003 ms: el AG tenía más cómputo.
  - En modo por evaluaciones pasaba lo mismo: con tope 45 hacía 59 evaluaciones.
- **Cambio (`AlgoritmoGenetico.ejecutar`):**
  - El presupuesto se revisa **antes de cada hijo**. Si se agota, la generación queda incompleta y sus hijos ya evaluados cuentan.
  - En la población inicial, si se agota, se conservan los individuos ya evaluados. Siempre queda al menos uno: el primero es el plan vigente sembrado.
  - La lógica del AG (torneo, OX, mutación, búsqueda local, Split) no cambia.
- **Verificación *(SINTETICO)*, SIM_5D nivel MEDIA:**

| Condición | Antes: medio / máx. (ms) | Después: medio / máx. (ms) |
|---|---|---|
| 1 hilo (réplica 1) | 2 074 / 2 224 | 2 007 / 2 029 |
| 7 hilos (ensayo, 40 réplicas; después, 7 réplicas) | 2 601 / 3 823 | 2 050 / 2 200 |

  El exceso que queda, unos 50 ms, es la decodificación del último hijo y la final. TABU usa 2 003 ms.
- **Pruebas:**
  - `PresupuestoAgTest` tiene 3 pruebas: tope de evaluaciones a mitad de generación, tope agotado en la población inicial y tiempo por llamada.
  - Las dos primeras **fallan con el código anterior** (59 y 30 evaluaciones) y pasan con el nuevo.
  - **Total: 75 pruebas, todas pasan.** `Main` no cambia (708/672/672 y 816/816/816).
- **Consecuencia para el ensayo de la etapa 26:** el AG corrió con cerca de un 30 % más de cómputo que TABU. Aun así no hubo diferencia significativa en el % de colapsos. Hay que repetir el ensayo, o correr directamente el experimento con los datos oficiales, con esta corrección.

## Etapa 28: datos oficiales, ventanas reales y experimento completo (SI-26)

- **Datos oficiales** en `juego_de_datos/`, publicados por el profesor y no versionados:
  - ventas y bloqueos mensuales de 2026-01 a 2028-12;
  - mantenimiento de 2026-09/10.
  - Los 36 meses pasan `ValidadorEntradas` sin errores; solo hay avisos de clientes ubicados en un almacén.
- **La demanda del profesor crece mes a mes:** 5 000 pedidos por mes en cada vez menos días. Va del 9 % de C_max (2026-01) al 73 % (2026-09), 106 % (2027-01) y 350 % (2028-12). C_max oficial = 1 296 paquetes/día.
- **Correcciones:**
  1. `MapaVial` compartido entre corridas en paralelo: sus cachés no son seguras entre hilos. Con los bloqueos oficiales la memoria se agotó, y podía dar distancias equivocadas. Error introducido en la etapa 24. Ahora `ExperimentoSimulacion.copiar` da un mapa propio a cada corrida.
  2. `Mantenimiento.leer` ignoraba el mes: el archivo oficial trae septiembre y octubre, y al simular septiembre se aplicaban 37 mantenimientos en lugar de 19. Ahora se filtra por el mes de las ventas.
  3. El análisis usaba la hora absoluta del mes como tiempo hasta el colapso; ahora usa `horas_desde_inicio`.
  4. Los eventos de cada corrida ya no se guardan en memoria hasta el final.
- **Ventanas reales** (decisión del equipo, SI-26):
  - `--situaciones ventanas --carpeta-ventas --carpeta-bloqueos --meses NOMBRE=aaaamm-aaaamm,... --paso-ventana N`.
  - Cada situación es un tramo real de 5 días, con los pedidos, bloqueos y mantenimientos de su mes, que termina antes del último pedido del archivo. Los niveles son grupos de meses.
- **Calibración previa** (septiembre, 4 réplicas): la demanda real de septiembre (73 %, días 1-5) dio 0 de 8 corridas con colapso. Con carga remuestreada al 90-115 %, colapsa entre 38 % y 75 %. Por eso se eligieron meses reales de 78 a 115 %.
- **Pruebas:** hay 4 nuevas (mapa propio por corrida, ventanas comunes a ambos algoritmos, ventanas dentro de los días con pedidos con niveles por grupo, mantenimiento por mes). **Total: 79 pruebas, todas pasan.**

**Experimento oficial** (SIM_5D, Ta = 2 s, 7 hilos, 206 corridas, 1.7 h de reloj). Salidas en `verificacion_etapa28/` (no versionada): `experimento_oficial_SIM_5D.csv` y `analisis/`.

| Nivel (meses reales) | Carga | Pares | % colapso TABU | % colapso AG | Solo TABU | Solo AG | p (McNemar) |
|---|---|---|---|---|---|---|---|
| BAJA (2026-09/10) | 78 % | 40 | 27.5 [15, 44] | 25.0 [13, 41] | 4 | 3 | 1.00 |
| MEDIA (2026-11/12) | 98 % | 35 | 34.3 [19, 52] | 42.9 [26, 61] | 3 | 6 | 0.51 |
| ALTA (2027-01/02) | 115 % | 28 | 89.3 [72, 98] | 92.9 [76, 99] | 1 | 2 | 1.00 |
| **Global** | | 103 | **46.6** | **49.5** | 8 | 11 | **0.65** |

- **GEE:** odds ratio TABU/AG = 0.85, p = 0.49. **Sin los colapsos por destino bloqueado** (el ruido que afecta a ambos): global, solo TABU 7 pares y solo AG 9, p = 0.80.
- **Potencia:** con psi = 0.18, 103 pares alcanzan para detectar diferencias de unos 15 puntos (65 pares necesarios). La diferencia observada es de unos 3 puntos, así que **si existe, es pequeña**.

**Desempates (pruebas pareadas):**

| Variable | BAJA | MEDIA | ALTA |
|---|---|---|---|
| Tiempo hasta el colapso (log-rank) | sin diferencia (p = 0.77) | sin diferencia (p = 0.43) | sin diferencia (p = 0.76) |
| Costo por pedido (S/, TABU vs. AG) | 160.9 vs. **153.4** (AG 4.8 % menos, p < 10⁻⁹) | 161.8 vs. **160.2** (AG 1.0 % menos, p = 0.003) | sin diferencia |
| km por pedido | AG 6.4 % menos | AG 1.9 % menos | sin diferencia |
| Cambios de unidad (mediana) | **TABU 29** vs. 290 | **TABU 28** vs. 185 | **TABU 19.5** vs. 90.5 |
| Tiempo por replanificación | TABU 2 004 ms; AG 2 045 ms (Ta = 2 000) | | |

- **Regla acordada** (% de colapsos → tiempo hasta el colapso → costo por pedido → estabilidad):
  - **AG en BAJA y MEDIA**, decidido por el costo por pedido;
  - **TABU en ALTA**, decidido por la estabilidad.
- **Lectura:** los dos algoritmos son **equivalentes en lo principal**: colapsan en la misma proporción y al mismo tiempo. Se diferencian en un intercambio:
  - el AG es algo más barato con carga baja o media (1-5 % por pedido);
  - Tabú es mucho más estable: de 3 a 10 veces menos reasignaciones de unidades al replanificar.
- La elección final depende de cuánto pese cada criterio y queda para decisión del equipo.

## Etapa 29: tres corridas con semillas distintas y veredicto

- **Objetivo:** comprobar que la conclusión de la etapa 28 no depende del azar de los algoritmos, que pesa porque en cerca del 18 % de los tramos colapsa uno solo.
- **Cambios:**
  - `--semilla-base S`: la réplica r usa la semilla S + r en los algoritmos. Los tramos no cambian (los fija `carga.semilla_base`).
  - Análisis y `unir_csv.py`: el par TABU/AG se identifica por réplica, semilla y tramo, así que se pueden unir corridas.
  - Tabla «0b» por corrida.
  - La GEE se agrupa por tramo: con varias corridas es la prueba combinada válida, porque la McNemar global cuenta el mismo tramo varias veces.
- **Resultados** en `Resultados/` (versionados): `corrida1_semilla1000/`, `corrida2_semilla2000/`, `corrida3_semilla3000/` y `combinado/`, cada uno con su CSV, hashes, registro y análisis. Ver `Resultados/LEEME.md`.

| Corrida (semilla) | % colapso TABU | % colapso AG | Solo TABU / solo AG | p (McNemar) |
|---|---|---|---|---|
| 1 (1000) | 46.6 | 49.5 | 8 / 11 | 0.65 |
| 2 (2000) | 49.5 | 44.7 | 14 / 9 | 0.40 |
| 3 (3000) | 42.7 | 50.5 | 8 / 16 | 0.15 |
| **Combinadas (309 pares)** | **46.3** | **48.2** | 30 / 36 | **GEE p = 0.46** (odds ratio TABU/AG = 0.90) |

Por nivel, con las tres corridas combinadas: BAJA 27.5 % frente a 26.7 %, MEDIA 35.2 % frente a 43.8 % (p = 0.18) y ALTA 86.9 % frente a 84.5 %. Ninguno es significativo.

- **Veredicto sobre la variable principal:** los dos algoritmos son **equivalentes en el % de colapsos**. Ninguna corrida muestra diferencia significativa, la dirección cambia de una corrida a otra (en la 2 colapsa más TABU) y la prueba combinada tampoco la encuentra. El tiempo hasta el colapso tampoco difiere (log-rank p = 0.19 a 0.91).
- **Desempates, con las tres corridas combinadas** (los p son orientativos, porque repiten tramos; el sentido es el mismo en las tres):
  - Costo por pedido: AG 4.5 % más barato en BAJA y 1.4 % en MEDIA; igual en ALTA.
  - km por pedido: AG de 0.7 a 5.8 % menos.
  - Cambios de unidad (mediana): TABU 27 frente a 289.5 (BAJA), 27 frente a 175 (MEDIA) y 20 frente a 87 (ALTA).
- **Con la regla escrita** (costo antes que estabilidad): AG en BAJA y MEDIA, TABU en ALTA. Igual en las tres corridas.
- **Pendiente:** la decisión del equipo sobre el orden costo/estabilidad y, con ella, el algoritmo elegido.
- **Pruebas:** 79, todas pasan.
