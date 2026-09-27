# Guía del código de PaqRap

Guía para que cada integrante entienda y defienda el código. Todo está en el paquete `pe.edu.pucp.gamesoft.paqrap` (`src/main/java/...`); las pruebas están en `src/test/java/...`.

---

## 1. Mapa de clases por módulo

### 1.1 Modelo de datos
| Clase | Responsabilidad |
|---|---|
| `TipoUnidad` | Tipos AUTO / MOTO / BICICLETA: capacidad (24/8/4), costo por km (8/6/3) y velocidad configurada (`parametros.properties`, fija). Un cambio en caliente rige solo en su simulación (`Contexto.velocidad`, `Simulador.velocidad`; etapa 24). |
| `UnidadTransporte` | Una unidad de la flota (código TA01…TB12 y tipo). |
| `Almacen` | Almacén con código y coordenadas (central, Nor-Oeste, Este). |
| `Pedido` | Una **entrega** (un pedido, o una parte de un pedido grande): posición, cantidad, plazo, registro y origen de los paquetes (en almacén, `aBordoDe` o `enAveriada`). |
| `ParadaAlg` | Parada de una ruta: ENTREGA, RECARGA (en un almacén) o TRASVASE (en una unidad averiada). |
| `TipoParada` | Tipos de parada. |
| `RutaAlg` | Ruta de una unidad: lista de paradas, km y costo. |
| `Solucion` | Plan completo: rutas, entregas sin asignar, H (sin asignar + tarde) y S (costo). |
| `Parametros` | Lee `config/parametros.properties`, con los valores del enunciado por defecto. |

### 1.2 Evaluación y contexto (núcleo común a ambos algoritmos)
| Clase | Responsabilidad |
|---|---|
| `Contexto` | "El mundo" en el instante de planificar: instante base, mapa vial, almacenes con stock, posición y hora de inicio de cada unidad, unidades averiadas, plan vigente, regla de destino bloqueado, hora límite efectiva (`limiteEfectivo`) y penalidad de estabilidad. Es por hilo; el contexto por defecto reproduce el modelo simple de `Main`. |
| `Compartido` | Función objetivo: `evaluarRuta` (una pasada: tiempos, km, costo, tardanzas y factibilidad), `evaluarSolucion` (H, S, violaciones), `mejorQue` (primero H, luego S) y métricas. |
| `Hito` | Paso de la línea de tiempo de una ruta (TRAMO, ENTREGA, RECARGA, TRASVASE, FIN), producido por `evaluarRuta` para el simulador. |

### 1.3 Búsqueda Tabú (Arzapalo, Alcca)
| Clase | Responsabilidad |
|---|---|
| `BusquedaTabu` | Ciclo tabú: lista de candidatos, aspiración y parada por tiempo o por evaluaciones. `ejecutarDesdeCero` parte del plan vigente o de C&W. Es un objeto por ejecución (`new BusquedaTabu(semilla)`), con su generador aleatorio y sus contadores (etapa 24). |
| `ListaTabu`, `ParTabu` | Atributos prohibidos (pedido, unidad, posición) con su iteración de vencimiento. |
| `Movimiento` | Un vecino evaluado con sus atributos tabú. |
| `OperadoresVecindario` | Operadores compartidos: Reubicación, Intercambio, 2-opt, Cross-exchange (los dos últimos provisionales), Recarga e Inserción; `mejorInsercion` y `conUnidadesLibres`. |
| `Heuristicaconstructiva` | Clarke & Wright (ahorros, fusión por extremos) y paso de inserción: solución inicial del primer ciclo. |

### 1.4 Algoritmo Genético (Alvarado, Torres)
| Clase | Responsabilidad |
|---|---|
| `AlgoritmoGenetico` | Población (sembrada con el plan vigente), torneo, cruce OX, mutación, búsqueda local memética y decodificadores `splitClasico` y `splitConEstado`. Es un objeto por ejecución (`new AlgoritmoGenetico(semilla)`), con su generador aleatorio, presupuesto y contadores (etapa 24). |
| `Cromosoma`, `Poblacion` | Permutación de entregas + tipo de vehículo por entrega, con su aptitud (H, S). |

### 1.5 Simulador, mapa vial e integración
| Clase | Responsabilidad |
|---|---|
| `Simulador` | Eventos discretos: reloj, llegada de pedidos, replanificación con estado cada Sa y por evento, movimiento nodo a nodo, averías, trasvase, stock, mantenimiento, colapso, registro de eventos e instantánea JSON. |
| `Planificador` | Interfaz entre el simulador y los algoritmos (`EstadoPlanificacion` → `Plan`); adaptadores `tabu()` y `genetico()`, que crean un objeto del algoritmo por llamada con la semilla del ciclo. |
| `MapaVial` | Retícula 71 × 51, lector de bloqueos, BFS con caché por intervalo, `distanciaTramo`, `caminoTramo`, `finBloqueo`/`inicioBloqueo` y `limiteEfectivo` (Etapa 23). |
| `Averia`, `Mantenimiento` | Reglas y lectores de averías (tipos 1-3; formato provisional) y de mantenimiento preventivo. |
| `Reloj` | Ritmo: sin espera, escalado (SIM_5D) o real (DIA_A_DIA); espera interrumpible. |
| `ServicioSimulacion` | Fachada para un visualizador: corre el simulador en un hilo, da la instantánea y recibe averías y cambios de velocidad. |
| `EscritorJson` | Escritor JSON mínimo para la instantánea. |

### 1.6 Experimento, datos y herramientas
| Clase | Responsabilidad |
|---|---|
| `Experimento` | Punto de entrada del experimento. Modo estático (ventanas) y lectura de argumentos. |
| `ExperimentoSimulacion` | Modo simulación: C_max, niveles, matriz algoritmo × nivel × réplica, hashes, calibración y CSV. `--hilos N` corre N simulaciones a la vez (etapa 24). |
| `CapacidadFlota` | C_max = Σ n·q·⌊21/t⌋. |
| `GeneradorCarga` | Pedidos por nivel (bootstrap del archivo base, semilla fija). |
| `GeneradorDatosSinteticos` | Pedidos y bloqueos sintéticos del mes. |
| `LectorPedidos` | Lee `ventas2026mm` (ventana o absoluto) y divide los pedidos de más de 24 paquetes. |
| `ValidadorEntradas` | Valida ventas, bloqueos y mantenimiento antes de correr. |
| `Main`, `Visualizadorrutas` | Demostración con instancias de juguete e imágenes PNG (Swing). |
| `PaqRap` | Clase principal de plantilla de NetBeans. |

---

## 2. Flujo completo de una corrida del simulador

Entrada: `Experimento.main` → `ExperimentoSimulacion.ejecutar` → `Simulador.simular(pedidos, flota, planificador, cfg)`.

1. **`Simulador.crear` → constructor:** crea el estado de cada unidad (`Unidad`: nodo, eventos, carga a bordo), ordena los pedidos por registro y fija el stock (central ∞, intermedios 1 000).
2. **`ejecutar()` → `correr()`**, ciclo principal. En cada vuelta:
   1. **Próximo instante T:** el mínimo entre la próxima replanificación periódica (cada Sa), el próximo evento de cualquier unidad (llegada a un nodo, entrega, carga), el próximo encuentro con un bloqueo, la próxima avería, el cambio de estado de una averiada, el mantenimiento, el **plazo más próximo** (`siguientePlazo`) y la reposición de stock de las 23:59:59.
   2. **`cfg.reloj.esperarHasta(T)`:** no espera en EXPERIMENTO; espera escalada en SIM_5D. Si llega un evento externo, se interrumpe (`Reloj.ahora`).
   3. **`aplicarEventosHasta(T)` → `aplicar`:**
      - NODO: avanza 1 km y suma el costo;
      - RECARGA: los pedidos pasan a bordo y se descuenta el stock;
      - TRASVASE: los paquetes pasan de la averiada a la unidad;
      - ENTREGA: la entrega queda hecha;
      - FIN: la unidad llega a un almacén.
   4. **`verificarColapso(T)`:** si alguna entrega superó su hora límite sin entregarse, se registran el colapso, el pedido, la unidad y la `causa`, y el ciclo termina (P3).
   5. **Transiciones:** bloqueo encontrado en el camino, averías del archivo y averías externas (`aplicarAveria`), fin de la avería en el lugar (`reactivar`, o paso al central), mantenimiento, reposición de stock.
   6. **`replanificar(T)`** si toca por Sa o por evento (ver §3).
3. **`cerrarMetricas()`:** pedidos entregados, % en plazo, viajes y tiempos del planificador.

**`replanificar(T)` paso a paso:**
1. Aplica los cambios de velocidad pendientes (P16).
2. Admite los pedidos llegados; marca los inentregables por bloqueo si la regla es `no_evaluable`.
3. `dividirUrgentes`: entregas parciales flexibles.
4. Arma el **`Contexto`**:
   - instante, mapa y almacenes con su stock;
   - para cada unidad operativa, su `Inicio`: el siguiente nodo si está viajando, o el fin de la actividad en curso;
   - las unidades averiadas con carga;
   - la asignación vigente (estabilidad).
5. Arma las entregas a planificar como copias relativas: pendientes en almacén, a bordo (`aBordoDe`) o en una averiada (`enAveriada`).
6. `planVigenteReparado`: la ruta que le quedaba a cada unidad, sin lo ya hecho, más los pedidos nuevos insertados con `OperadoresVecindario.mejorInsercion`. Si queda infactible, se reconstruye desde las entregas a bordo.
7. `planificador.planificar(EstadoPlanificacion)`, con el cerrojo liberado.
8. `comprometer`:
   - `Compartido.evaluarRuta(r, hitos)` produce la línea de tiempo;
   - cada TRAMO se expande nodo a nodo con `MapaVial.caminoTramo`;
   - se detectan los encuentros con bloqueos (`encuentroMin`);
   - se mide la estabilidad (`cambiosDeUnidad`).

---

## 3. Flujo de una llamada al planificador con estado

`Planificador.tabu(d)` o `Planificador.genetico(p)` reciben un `EstadoPlanificacion` (contexto, entregas, flota, Ta, tope de evaluaciones, semilla) y activan el contexto (`Contexto.usar`). Cada llamada crea su propio objeto del algoritmo (`new BusquedaTabu(semilla)` o `new AlgoritmoGenetico(semilla)`), así que no hay estado compartido entre simulaciones (etapa 24).

### Búsqueda Tabú
1. `BusquedaTabu.ejecutarDesdeCero`: la solución inicial es el **plan vigente reparado** (`Contexto.planBase`), o `Heuristicaconstructiva.construirSolucionInicial` (C&W) en el primer ciclo.
2. `buscar`:
   - `OperadoresVecindario.conUnidadesLibres` agrega las unidades sin ruta;
   - en cada iteración se generan 20 candidatos (`generarVecino`): si hay entregas sin asignar, el primero es una Inserción; el resto, un operador elegido al azar por pesos;
   - cada operador copia la solución, la modifica y la descarta si `Compartido.esFactible` falla (capacidad, stock, a bordo, trasvase, mantenimiento);
   - se acepta el mejor candidato no tabú, o uno tabú que cumpla aspiración, o el menos malo;
   - la lista tabú registra los atributos de `Movimiento.prohibir`.
3. Se devuelve la mejor solución global; `Plan.de` se queda con las rutas no vacías.

### Algoritmo Genético
1. `AlgoritmoGenetico.ejecutar` → `inicializarPoblacion`: el 30 % se siembra con el plan vigente codificado (`aCromosoma`) y variaciones suyas (mutaciones); el resto es aleatorio.
2. `evaluar(c)` → `split(c)` → **`splitConEstado`**:
   - las entregas a bordo van a su unidad;
   - `cortar` hace una programación dinámica de tramos homogéneos (un solo origen) que caben en el tipo del gen;
   - `asignarTramo` pone cada tramo al final de la ruta de la unidad y con el punto de carga (almacén con stock, carga implícita o TRASVASE) que minimizan (Δtardanzas, Δcosto) **reales**, con desempate por la unidad previa.
3. Generaciones: torneo, `cruceOX` (0.85), `mutar` (0.15), `busquedaLocal` (al 20 % de los hijos y siempre al mejor) y elitismo.
4. Al final: se decodifica el mejor; si el plan vigente es mejor, se devuelve el plan vigente (**nunca peor**).

---

## 4. Cómo se evalúa una ruta (`Compartido.evaluarRuta` + `Contexto`)

Una sola pasada por las paradas:

1. **Inicio:** `Contexto.inicioDe(unidad)` da el nodo y la hora relativa (0 = instante de planificación) desde la que la unidad puede empezar; por defecto, el central en la hora 0.
2. **Carga inicial:** las entregas `aBordoDe` de esta unidad. Una entrega a bordo de **otra** unidad hace la ruta infactible.
3. **Carga implícita:** si la unidad arranca en un almacén, carga ahí las entregas en almacén hasta la primera RECARGA.
4. **Por cada parada:**
   - con `nodo_vecino`, `Contexto.puntoDeEntrega` puede mover la entrega al nodo vecino;
   - `Contexto.distanciaTramo` usa la distancia por la red, con los bloqueos de la ventana del tramo;
   - `Contexto.avanzar` suma el viaje y la hora de alimentación si la cruza.
   - **ENTREGA:** `esperaDestino` espera si el destino está bloqueado; se compara la llegada con `horaLimite` (tarde +1); si llega a tiempo, el margen para la holgura se mide hasta `Contexto.limiteEfectivo` (Etapa 23: el inicio del bloqueo del destino que cubre la hora límite, si lo hay); suma 1 h de acondicionamiento y baja la carga.
   - **RECARGA:** carga las entregas en almacén hasta la próxima RECARGA (uso del almacén) y verifica la capacidad.
   - **TRASVASE:** verifica que la averiada siga en el lugar al terminar (30 min), carga sus entregas y verifica la capacidad.
5. **Regreso** al almacén más cercano (`almacenMasCercano`).
6. **Fin:** si pasa del próximo mantenimiento, la ruta es infactible.
7. **Costo** = km × costo/km + (con plan vigente) cambios de unidad × penalidad de estabilidad + (en el simulador) horas de margen faltante × penalidad de holgura (Etapa 22: una entrega a tiempo con menos de 60 min de margen es un plan frágil; Etapa 23: el margen se cuenta hasta la hora límite efectiva).

**Hora límite efectiva (Etapa 23, SI-23).** `MapaVial.limiteEfectivo(x, y, límite)`: si el destino está bloqueado en la hora límite, devuelve el inicio de ese bloqueo (`inicioBloqueo`, que une bloqueos encadenados); si no, la hora límite. `Contexto.limiteEfectivo(pedido)` la da en horas relativas y respeta la regla de destino (con `nodo_vecino` rige la hora límite). Se usa en la holgura de `evaluarRuta` y como urgencia al ordenar pedidos (`Heuristicaconstructiva`, `Simulador.dividirUrgentes` y la inserción de faltantes en `armarReparado`). El colapso se sigue declarando en la hora límite real.

`evaluarSolucion` suma las rutas y verifica el stock total por almacén. H = sin asignar + tarde (+ violaciones como resguardo); S = costo.

---

## 5. Ejemplo trazado a mano: el ejemplo del profesor (`AveriaTest.otraUnidadTrasvasaYEntregaATiempo`)

**Datos:**
- Pedido E: 8 paquetes en (57,14), 30 km al este del central (27,14); registro en el minuto 0; plazo 3 h, así que la hora límite es el minuto 180.
- Flota: moto TM01 (25 km/h) y auto TA01 (40 km/h), este último en mantenimiento del minuto 0 al 30.
- Avería tipo 1 de TM01 en el minuto 36. Sa = 60 min, sin alimentación ni almacenes intermedios.

| Minuto | Qué pasa | Método |
|---|---|---|
| 0 | Llega E. TA01 entra en mantenimiento y queda disponible solo TM01. Primer plan (C&W): TM01 = [E], sale cargada del central. Llegada: 30 km / 25 = 1.2 h = **72 min**. | `replanificar`, `construirSolucionInicial`, `evaluarRuta` |
| 0 | Línea de tiempo: RECARGA implícita en el minuto 0 (E a bordo de TM01), 30 nodos cada 2.4 min, ENTREGA en el minuto 72. | `comprometer`, `caminoTramo` |
| 30 | Termina el mantenimiento de TA01 y se replanifica. E está a bordo de TM01, así que solo TM01 puede entregarla: el plan no cambia. | `replanificar`, restricción `aBordoDe` |
| 36 | **Avería tipo 1 de TM01** en el nodo (42,14), a 15 km × 2.4 min. E pasa a "en averiada" (A:TM01). TM01 queda en el lugar hasta el minuto 156 (2 h). Se replanifica de inmediato. | `aplicarAveria` |
| 36 | Contexto: averiada TM01 en (42,14), disponible para trasvase hasta 120 min después; TA01 libre en el central. E tiene `enAveriada=TM01`. `mejorInsercion` prueba [TRASVASE(TM01), E] en TA01: central → (42,14) son 15 km / 40 = 22.5 min; trasvase de 30 min (termina a los 52.5 min relativos, antes de 120); (42,14) → (57,14) son 15 km = 22.5 min. **Llegada: 36 + 75 = 111 min**, antes de 180. H = 0. | `planVigenteReparado`, `mejorInsercion`, `evaluarRuta` (caso TRASVASE) |
| 58.5 | TA01 llega a (42,14) y trasvasa: E pasa a bordo de TA01 (ocupado hasta 88.5). | `aplicar` (TRASVASE) |
| 60 | Replanificación periódica: TA01 está trasvasando, así que su Inicio es (42,14) en el minuto 88.5. E ya está a bordo de TA01; el plan no cambia. | `replanificar` |
| 111 | **ENTREGA de E**, a tiempo (límite 180). TA01 vuelve al almacén más cercano. | `aplicar` (ENTREGA) |
| 180 | No hay colapso: E ya se entregó. | `verificarColapso` |
| 600 | Fin del horizonte de la prueba: corrida censurada, `trasvases = 1`. | `correr` |

**Contraste** (`siNadieLlegaElColapsoEsEnLaHoraLimiteExacta`): sin TA01 y con una avería tipo 2 (la moto queda hasta 4 h en el lugar), nadie puede llevar E. El colapso ocurre **exactamente en el minuto 180**, con la causa "a bordo de unidad averiada".
