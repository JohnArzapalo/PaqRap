# Módulos por integrante y preguntas del JP

**Propuesta** de reparto para que cada integrante pueda defender una parte del código ante el JP. El reparto es interno; el equipo puede cambiarlo. Cada módulo tiene un responsable principal y otro que lo conoce como respaldo. Las respuestas remiten a la clase y al método; el recorrido completo está en `docs/guia_del_codigo.md`.

| Módulo | Principal | Respaldo | Clases |
|---|---|---|---|
| M1. Búsqueda Tabú | Arzapalo | Alcca | `BusquedaTabu`, `ListaTabu`, `ParTabu`, `Movimiento`, `OperadoresVecindario`, `Heuristicaconstructiva` |
| M2. Algoritmo Genético | Alvarado | Torres | `AlgoritmoGenetico`, `Cromosoma`, `Poblacion` (y la búsqueda local con `OperadoresVecindario`) |
| M3. Núcleo del simulador y replanificación | Arzapalo | Torres | `Simulador` (`correr`, `replanificar`, `comprometer`, `verificarColapso`), `Planificador`, `Reloj` |
| M4. Mapa vial, bloqueos y evaluación de rutas | Alcca | Arzapalo | `MapaVial`, `Contexto`, `Compartido`, `Hito` |
| M5. Averías, mantenimiento, trasvase e integración | Torres | Alvarado | `Averia`, `Mantenimiento`, `Simulador.aplicarAveria`, `ServicioSimulacion`, `EscritorJson` |
| M6. Experimento, datos y análisis | Alvarado | Alcca | `ExperimentoSimulacion`, `CapacidadFlota`, `GeneradorCarga`, `GeneradorDatosSinteticos`, `ValidadorEntradas`, `LectorPedidos`, `analisis/*.py` |

Así, Tabú queda con Arzapalo y Alcca, el AG con Alvarado y Torres, y el simulador, el mapa y el experimento se reparten entre los cuatro.

---

## M1. Búsqueda Tabú (Arzapalo, Alcca)

1. **¿De qué solución parte la búsqueda en cada ciclo?**
   `BusquedaTabu.ejecutarDesdeCero` usa `Contexto.planBase`, el plan vigente reparado por `Simulador.planVigenteReparado`: la ruta que le quedaba a cada unidad, más los pedidos nuevos insertados con `OperadoresVecindario.mejorInsercion`. Solo en el primer ciclo (sin plan vigente) construye con Clarke & Wright (`Heuristicaconstructiva.construirSolucionInicial`).
2. **¿Cómo se genera un vecino y qué operadores hay?**
   En cada iteración `buscar` crea `tabu.candidatos = 20` vecinos con `generarVecino`. Si hay entregas sin asignar, el primer candidato es una Inserción; el resto elige al azar, por pesos, entre Reubicación y Intercambio (0.35 cada uno), 2-opt y Cross-exchange (0.15 cada uno; los dos últimos son provisionales) y Recarga (0.10). Todos están en `OperadoresVecindario`.
3. **¿Qué se guarda en la lista tabú y cuánto dura?**
   Los atributos `Movimiento.prohibir` (`ParTabu`: pedido-unidad o pedido-posición), que impiden deshacer el movimiento aceptado. Duran `tabu.duracion = 8` iteraciones (`ListaTabu.registrar` y `limpiarVencidos`).
4. **¿Cuál es el criterio de aspiración?**
   Un movimiento tabú se acepta si produce una solución mejor que la mejor global según `Compartido.mejorQue` (primero menor H, luego menor S). Si todos los candidatos son tabú y ninguno aspira, se acepta el menos malo, para no estancarse.
5. **¿Cuándo se detiene?**
   Al agotar Ta (`presupuestoMs`) o el tope de evaluaciones (`maxEvaluaciones`, modo calibrado) en `debeDetenerse`. En el simulador no hay corte por estancamiento (`Integer.MAX_VALUE`), para que Tabú y AG usen el mismo presupuesto. Se devuelve la mejor global.

## M2. Algoritmo Genético (Alvarado, Torres)

1. **¿Qué representa un cromosoma?**
   `Cromosoma.permutacion` es el orden de las entregas, y `tipoAsignado` el tipo de vehículo por entrega. No contiene las rutas: se decodifica con `split`, que usa `splitConEstado` en el simulador y `splitClasico` en el modo estático.
2. **¿Cómo se decodifica con estado?**
   `splitConEstado`:
   - fija las entregas a bordo en su unidad;
   - `cortar` parte la permutación en tramos homogéneos (mismo origen) que caben en el tipo del gen;
   - `asignarTramo` agrega cada tramo al final de la unidad y punto de carga (almacén con stock, carga implícita o TRASVASE) que minimizan el aumento real de tardanzas y de costo, evaluados con `Compartido.evaluarRuta`;
   - en caso de empate, prefiere la unidad que ya tenía esas entregas (Etapa 18).
3. **¿Cómo se forma la población inicial?**
   `inicializarPoblacion`: `ag.proporcion_sembrada = 0.3` de los `ag.poblacion = 30` individuos parte del plan vigente codificado (`aCromosoma`) y sus mutaciones; el resto es aleatorio.
4. **¿Qué operadores genéticos se usan?**
   - selección por torneo binario (`seleccionTorneo`);
   - cruce OX con probabilidad 0.85 (`cruceOX`);
   - mutación con probabilidad 0.15 (`mutar`: intercambia dos genes y, la mitad de las veces, cambia el tipo de vehículo de un gen por `tipoQueQuepa`);
   - elitismo simple (pasa el mejor);
   - búsqueda local memética (`busquedaLocal`) sobre el 20 % de los hijos y siempre sobre el mejor.
5. **¿Puede el AG devolver un plan peor que el vigente?**
   No. Al final `ejecutar` compara el mejor decodificado con el `planBase` usando `Compartido.mejorQue` y devuelve el mejor de los dos. Así el AG no pierde por diseño frente a Tabú, que también parte del plan vigente.

## M3. Núcleo del simulador y replanificación (Arzapalo, Torres)

1. **¿Cómo avanza el tiempo?**
   Por eventos discretos, en `Simulador.correr`. El siguiente instante es el mínimo entre:
   - la próxima replanificación periódica (Sa = 60 min);
   - los eventos de las unidades (nodo a nodo, entregas, cargas);
   - los encuentros con bloqueos, las averías y el mantenimiento;
   - el plazo más próximo (`siguientePlazo`);
   - la reposición de stock.
   El `Reloj` decide si se espera (SIM_5D, DIA_A_DIA) o no (EXPERIMENTO, COLAPSO).
2. **¿Qué recibe el planificador en cada ciclo?**
   Un `Planificador.EstadoPlanificacion` con:
   - un `Contexto` armado en `replanificar`: instante, mapa, stock, `Inicio` de cada unidad operativa y averiadas con carga;
   - copias relativas de las entregas pendientes, a bordo (`aBordoDe`) o en averiada (`enAveriada`);
   - Ta, el tope de evaluaciones y la semilla del ciclo.
3. **¿Cuándo hay colapso y cómo se determina la causa?**
   `verificarColapso(t)` detecta la primera entrega no entregada cuya hora límite ya pasó; la corrida termina exactamente en ese minuto (P3). `causa` distingue, en orden:
   - "a bordo de unidad averiada";
   - "destino bloqueado";
   - "bloqueo" o "llegada tardía (a bordo / planificada)";
   - "capacidad" o "sin asignar".
4. **¿Qué pasa con una unidad que está a mitad de un tramo cuando se replanifica?**
   Queda comprometida hasta el siguiente nodo. Su `Contexto.Inicio` es ese nodo con la hora de llegada, y el evento NODO pendiente se vuelve a encolar (`compromiso`) antes de `comprometer` el plan nuevo.
5. **¿Cómo pasa un plan del algoritmo a movimientos concretos?**
   `comprometer` llama a `Compartido.evaluarRuta(r, hitos)`, que produce la línea de tiempo (TRAMO, ENTREGA, RECARGA, TRASVASE, FIN). Cada TRAMO se expande km a km con `MapaVial.caminoTramo` en eventos NODO, que `aplicarEventosHasta` consume. También calcula `encuentroMin` (el primer bloqueo en el camino) y los cambios de unidad.

## M4. Mapa vial, bloqueos y evaluación de rutas (Alcca, Arzapalo)

1. **¿Cómo se calcula la distancia con bloqueos?**
   `MapaVial.distanciaTramo` hace un BFS sobre la retícula de 71 × 51 con los nodos bloqueados en **cualquier** intervalo de la ventana del tramo (unión de estados). Es conservador: no pasa por un nodo que se bloquea mientras se viaja. Los resultados se guardan en una caché LRU (`red.cache_bfs = 10000`).
2. **¿Qué pasa si el destino está bloqueado?**
   Depende de `red.destino_bloqueado` (`Contexto.ReglaDestino`):
   - `esperar` (por defecto): `Contexto.esperaDestino` espera el `finBloqueo`;
   - `nodo_vecino`: `Contexto.puntoDeEntrega` entrega en el vecino libre más cercano (SI-14);
   - `no_evaluable`: el simulador excluye el pedido si el destino está bloqueado desde el registro hasta la hora límite (`MapaVial.bloqueadoDurante`, SI-15).
3. **¿Qué verifica `Compartido.evaluarRuta` para declarar una ruta infactible?**
   Los motivos están en `EvalRuta.infactible`:
   - "capacidad" (al cargar en almacén o en trasvase);
   - "entrega a bordo de otra unidad";
   - "entrega en almacén sin punto de carga";
   - "entrega sin trasvase previo";
   - "trasvase fuera de tiempo" (la averiada ya dejó el lugar);
   - "almacén fuera del contexto";
   - "mantenimiento" (la ruta termina después de `noDisponibleDesdeH`).
   El stock por almacén se verifica en `evaluarSolucion` (`excesoStock`).
4. **¿Por qué el `Contexto` es por hilo?**
   Desde la Etapa 21 guarda el contexto activo en un `ThreadLocal`. Así un servidor puede leer la instantánea o correr pruebas en otro hilo sin mezclar contextos. Si nadie llama a `Contexto.usar`, rige el contexto por defecto: el modelo simple que usan `Main` y el modo estático.
5. **¿Qué diferencia hay entre H y S y cómo se comparan dos soluciones?**
   H = entregas sin asignar + entregas fuera de plazo (+ violaciones, como resguardo). S = costo en soles: km × costo/km, más la penalidad de estabilidad si hay plan vigente. `Compartido.mejorQue` es lexicográfico: primero menor H y, si H empata, menor S. La penalidad de estabilidad solo entra en S, nunca en H.

## M5. Averías, mantenimiento, trasvase e integración (Torres, Alvarado)

1. **¿Qué hace cada tipo de avería?**
   `Averia.enLugarHastaMin` y `disponibleDesdeMin`:
   - tipo 1: 2 h inmovilizada en el lugar; luego sigue;
   - tipo 2: 4 h en el lugar y disponible al fin del turno siguiente;
   - tipo 3: 4 h en el lugar y disponible al día 3 a las 15:00.
   El formato del archivo de averías es provisional hasta que el profesor confirme.
2. **¿Qué pasa con los paquetes de una unidad averiada?**
   `Simulador.aplicarAveria` los pasa al estado `A:<unidad>` y dispara una replanificación inmediata. El planificador puede enviar otra unidad con una parada TRASVASE de 30 min en la posición de la averiada, siempre que llegue antes de que deje el lugar (`Contexto.Averiada.hastaH`). Si nadie llega, la causa del colapso es "a bordo de unidad averiada" (`AveriaTest`).
3. **¿Cómo afecta el mantenimiento preventivo?**
   Durante el mantenimiento, la unidad no está operativa (`enMantenimiento`). Antes de él, el `Inicio` de la unidad lleva `noDisponibleDesdeH`, y `evaluarRuta` declara infactible toda ruta que termine después.
4. **¿Cómo se registra una avería desde un visualizador?**
   `ServicioSimulacion.registrarAveria(unidad, tipo)` → `Simulador.inyectarAveria`: la avería entra en una cola concurrente y el hilo de la simulación se interrumpe. `Reloj.esperarHasta` devuelve false, el simulador toma el minuto simulado real (`Reloj.ahora`) y aplica la avería en ese instante (SI-18).
5. **¿Qué contiene la instantánea y cómo se evita leer un estado a medias?**
   `Simulador.instantaneaJson` devuelve:
   - reloj y semáforo (umbrales `Visualizadorrutas.UMBRAL_*`);
   - colapso y almacenes con stock;
   - unidades con estado, posición, carga, paradas y camino;
   - bloqueos activos, pedidos e indicadores.
   Se arma con el `cerrojo` (`ReentrantLock`) tomado; el simulador solo lo suelta mientras espera al reloj o mientras planifica.

## M6. Experimento, datos y análisis (Alvarado, Alcca)

1. **¿Cómo se definen los niveles BAJA, MEDIA y ALTA?**
   Como fracciones de la capacidad teórica de la flota, C_max = Σ n·q·⌊21/t⌋ (`CapacidadFlota`, con `carga.horas_efectivas = 21`). `GeneradorCarga` genera, con semilla fija, la carga de cada nivel por bootstrap del archivo base.
2. **¿Qué variable responde el experimento y por qué hay censura?**
   `colapso_h`, las horas hasta el primer pedido fuera de plazo. Si no colapsa dentro del horizonte (30 días), la corrida queda **censurada**. Con censuras, `analisis_experimento.py` usa Kaplan-Meier y la prueba log-rank por nivel; sin censuras, un ANOVA de dos factores o ART, según la normalidad (Shapiro).
3. **¿Cómo se garantiza que los datos de las tres PC sean los mismos?**
   `ExperimentoSimulacion` calcula el SHA-256 de cada entrada y lo escribe en `<salida>_hashes.txt` y en las columnas `sha256_*` del CSV. Además, `ejecutar_nivel.bat` genera el hash con `certutil` para contrastarlo. Antes de correr, `ValidadorEntradas` revisa el formato, las coordenadas y que ningún destino quede inalcanzable.
4. **¿Qué es `--calibrar-evaluaciones`?**
   Mide cuántas evaluaciones hace cada algoritmo en Ta sobre una instancia fija: día 2, de 08:00 a 12:00 (SI-17). Luego fija ese tope por algoritmo (`cfg.maxEvaluaciones`), para que el resultado no dependa de la velocidad de cada PC. Los topes se escriben en `<salida>_calibracion.txt`.
5. **¿Cuántas réplicas hacen falta?**
   `analisis/potencia.py` estima n por grupo para detectar una diferencia de 24 h en `colapso_h` (α = 0.05, potencia 0.8), con la desviación observada en cada nivel, y el tiempo de cómputo total. Los resultados están en `docs/avance_sem08.md` (Etapa 19).
