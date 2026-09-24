# Propuesta de cambios al IEN 22.dis.experim.v01

> ## ⚠ Pendiente verificar contra el IEN v01
> El IEN v01 (PDF o Word) **no está** en la carpeta del proyecto. El "texto actual resumido" de cada fila sale del resumen que el equipo entregó en el chat, no del documento. Antes de aprobar, verifique cada fila contra el IEN v01, sobre todo el **§12**, cuyo contenido no se conoce, y ajuste la numeración de secciones si no coincide.

| Campo | Valor |
|---|---|
| Documento afectado | 22.dis.experim.v01 (Informe de Experimentación Numérica) |
| Versión propuesta | v02 (borrador para revisión del equipo) |
| Proyecto | PaqRap, Eq5E, horario H982 |
| Motivo general | Nuevas indicaciones del profesor (semana 07), que **tienen prioridad sobre el IEN v01**: bloqueos por horario, almacenes intermedios con stock, replanificación con estado, colapso binario por unidad de producto y tres escenarios. Se implementaron en el código (etapas 8 a 14, ver `docs/avance_sem07.md`). |
| Estado | PROPUESTA: no modifica ningún documento del equipo (control de cambios NTP-ISO/IEC 29110). |

---

## 1. Tabla de control de cambios

| # | Sección del IEN v01 | Texto actual (resumido) | Cambio propuesto | Motivo |
|---|---|---|---|---|
| C-01 | §3 (descripción de los algoritmos) | Afirma que ambos algoritmos manejan ventanas de tiempo, bloqueos y reasignación dinámica, y que el AG tiene búsqueda local. | Dividir en **3.a Diseño objetivo** y **3.b Implementación evaluada**. **Implementado:** plazos; bloqueos por horario (BFS sobre la red); almacenes intermedios con stock y recargas (varios viajes por unidad); replanificación con estado de TODAS las unidades (P16); Tabú con 6 operadores (Reubicación, Intercambio, Inserción, Recarga, 2-opt y Cross-exchange; los dos últimos **provisionales**, a verificar contra el ISA §4.2); AG con Split con estado, búsqueda local memética y siembra desde el plan vigente; trasvase desde unidades averiadas; entregas parciales flexibles. **No implementado:** encuentros entre unidades en movimiento. | Alinear el informe con el código y con el profesor. |
| C-02 | §5 (variables; definición de colapso) | Colapso = primer pedido no entregado en plazo "y la planificación deja de ser estable"; difiere de SU-11 (riesgo RA-06). | **Colapso (P3 del profesor):** el primer instante en que al menos una **unidad de producto** de cualquier pedido supera su hora límite sin haberse entregado, esté a bordo, en una unidad averiada o sin asignar. **La simulación termina en ese instante.** Se registran el pedido, la unidad y la causa (sin asignar, a bordo de unidad averiada, bloqueo, capacidad, llegada tardía). Se descarta la definición multivariable del ISA. La estabilidad (`cambios_de_unidad`) pasa a variable secundaria. | **Cierra el riesgo RA-06.** El **ISA y el Documento de Definición del Producto deben alinearse** con esta definición. |
| C-03 | §6 / §7 (escenarios) | Horizonte de 5 días en 30-60 min reales. | **Tres escenarios (P4):** (1) **simulación de 5 días** (`SIM_5D`), mostrada en ~30 min reales (parámetro); puede colapsar y, por la definición binaria, se detiene al colapsar (`sim5d.detener_en_colapso`, pendiente de confirmar); (2) **operación día a día** (`DIA_A_DIA`), reloj 1:1 con la hora real; (3) **simulación hasta el colapso** (`COLAPSO`). El experimento usa el escenario **`EXPERIMENTO`**: sin pantalla, lo más rápido posible, hasta el colapso o un tope de seguridad de 30 días (sin colapso, queda censurada). | Separar el experimento (rápido) de la visualización (ritmo real o escalado). |
| C-04 | §7 (diseño del experimento) | Factorial 2 × 3: algoritmo × carga (30 / 60 / 90-100 %), 5 réplicas, variable principal = tiempo hasta el colapso. | Se mantiene: algoritmo (TABU, AG) × carga (BAJA, MEDIA, ALTA) × 5 réplicas; semilla de réplica = 1000 + r; orden aleatorio. **Mismas situaciones (P1):** para cada nivel, ambos algoritmos reciben exactamente los mismos pedidos, bloqueos a las mismas horas, flota, almacenes y parámetros (conjuntos en `datos/generados/`). La única diferencia entre corridas comparadas es el algoritmo y la semilla de la réplica. **Reparto entre PCs por nivel**, no por algoritmo. | P1 del profesor; validez interna. |
| C-05 | §7 (bloqueos) | Una configuración estática: la ventana de 24 h con más tramos bloqueados. | **Bloqueos por archivo y por horario** (`aaaamm.bloqueadas`): cada nodo de la polilínea queda bloqueado en [inicio, fin). Las distancias se calculan por BFS evitando los nodos bloqueados. El planificador usa los bloqueos activos al planificar y los que empiezan antes de cada llegada estimada. Si un nodo se bloquea mientras la unidad va hacia él, la unidad se detiene y se replanifica en ese instante. Si el destino está bloqueado, se espera al desbloqueo (por confirmar). | P1 del profesor. |
| C-06 | §7 / §8 (capacidad, almacenes y carga) | Niveles como % de la capacidad teórica, con un solo almacén. | C_max = Σ n_k · q_k · ⌊21 / t_k⌋ (ver `docs/capacidad_y_carga.md`). **Almacenes (P2, P10):** central infinito; Nor-Oeste y Este con 1 000 unidades, repuestas a las 23:59:59; cualquier unidad recarga en cualquier almacén con stock; nunca se planifica una recarga sin stock. **Carga:** remuestreo del archivo mensual (sintético mientras no esté el oficial) conservando la hora del día y el espacio, con semilla fija por nivel. | P2 del profesor. |
| C-07 | §8 (variables y métricas) | Costo acumulado, tiempo del algoritmo por iteración, % de pedidos a tiempo. | **Principal:** `colapso_h`. **Secundarias:** `censurada`, `causa_colapso`, `costo_acumulado` y `km_acumulados` (lo efectivamente recorrido), `pct_pedidos_en_plazo`, `entregas_tarde`, `replanificaciones` y `replan_por_evento`, `planificador_ms_medio` / `_max`, `iteraciones_totales` / `evaluaciones_totales`, `aplazamientos`, `cambios_de_unidad` (estabilidad), viajes por vehículo, `bloqueos_encontrados`, `averias_aplicadas`, `trasvases` y `parciales_creadas`. Registro de eventos por corrida (`--eventos`) para auditar y para el visualizador. | Medir lo que pide el profesor. |
| C-08 | §8 / §9 (replanificación) | Planificación periódica. | **Replanificar, no volver a planificar (P5, P6, P16):** en cada ciclo (Sa = 60 min, y además ante avería o bloqueo encontrado) se replanifican todas las unidades, estén en un almacén o en ruta, desde su estado real (nodo, carga a bordo) y desde el plan vigente reparado. Tabú parte de ese plan; el AG siembra el 30 % de su población con él y nunca devuelve algo peor. Cada unidad es un **almacén móvil** con posición, stock a bordo y capacidad libre. | P5 y P6 del profesor. |
| C-09 | §8 / §9 (criterio de parada) | Mismo presupuesto Ta. | Ta = 2000 ms por llamada al planificador (parada por tiempo, principal). Modo reproducible `--max-evaluaciones` (tope por llamada). | Reproducibilidad (verificada con pruebas). |
| C-10 | §9 (averías y entregas parciales) | Averías, mantenimiento y cancelaciones excluidos. | **Averías opcionales:** desactivadas por defecto en el experimento (como en v01). Con `--averias si`, todas las corridas usan el MISMO archivo. Reglas de los tipos 1, 2 y 3 según la hoja; la unidad averiada es un almacén temporal (trasvase de 30 min, por confirmar); lo no trasvasado vuelve al central. **Mantenimiento preventivo:** se lee `mant.preventivo` si existe. **Entregas parciales flexibles (P13, P14):** las urgentes de más de 8 paquetes que los autos libres no alcanzan se dividen en partes de hasta 8, que pueden ir en unidades de cualquier tipo. | Hoja de preguntas, P13 y P14. |
| C-11 | §10 (análisis) | ANOVA de dos factores, α = 0.05, alternativa no paramétrica. | `analisis/analisis_experimento.py`: sin censuradas, ANOVA algoritmo × carga sobre `colapso_h` (o ART); con censuradas, Kaplan-Meier y log-rank por nivel (propio, validado con un caso a mano y con lifelines); causas de colapso por combinación. Regla de decisión: tiempo hasta el colapso → % en plazo → costo acumulado. | Censura y definición binaria. |
| C-12 | §12 (amenazas a la validez) | (Se desconoce el contenido actual: verificar.) | Agregar la sección 3 de esta propuesta. | Transparencia. |

---

## 2. Supuestos de la implementación actual

| ID | Supuesto | Dónde está en el código |
|---|---|---|
| SI-01 | Retícula 71 × 51, 1 km por arista, sin diagonales; nodo bloqueado en [inicio, fin); se puede salir del nodo de origen aunque esté bloqueado. | `MapaVial` |
| SI-02 | Planificador: cada tramo evita los bloqueos activos en toda su ventana estimada (aproximación conservadora). | `MapaVial.distanciaTramo`, `Contexto.distanciaTramo` |
| SI-03 | Destino bloqueado al llegar: la entrega espera el desbloqueo (**por confirmar**). | `Contexto.esperaDestino` |
| SI-04 | Al terminar su ruta, la unidad vuelve al almacén más cercano. Una unidad sin tareas se queda donde está. | `Compartido.evaluarRuta`, `Simulador` |
| SI-05 | Carga en un almacén: 0 min (no está en el enunciado). Trasvase: 30 min (**por confirmar**). | `recarga.minutos`, `trasvase.minutos` |
| SI-06 | Alimentación: 1 h a las 03, 11 y 19 h (4 h después de cada cambio de turno), dondequiera que esté la unidad. | `Contexto.avanzar` |
| SI-07 | Stock: el plan no carga más que el stock actual de cada almacén (no anticipa la reposición de las 23:59:59); el simulador descuenta al cargar. | `Compartido.evaluarSolucion`, `Simulador` |
| SI-08 | Una unidad en ruta, al replanificar, se compromete a llegar al siguiente nodo de su camino; si está entregando o trasvasando, termina esa actividad. | `Simulador.replanificar` |
| SI-09 | Avería: la unidad se detiene en el último nodo alcanzado. Formato del archivo **provisional**. Generador sintético (2 por día; tipos 0.6 / 0.3 / 0.1). | `Averia` |
| SI-10 | Mantenimiento: 24 h por defecto (**duración por tipo pendiente**), en el central; las rutas deben terminar antes. | `Mantenimiento` |
| SI-11 | Entregas parciales: estrategia `urgentes` (plazo ≤ 8 h, partes de hasta 8 paquetes). | `Simulador.dividirUrgentes` |
| SI-12 | Velocidades del enunciado (40 / 25 / 12 km/h), **pendiente de confirmar** frente a la hoja (20 / 40 / 14). | `config/parametros.properties` |
| SI-13 | Datos sintéticos (pedidos del mes y bloqueos) mientras no estén los oficiales; todas las salidas llevan la marca. | `GeneradorDatosSinteticos`, `docs/datos_sinteticos.md` |
| SI-14 | `red.destino_bloqueado = nodo_vecino`: si el destino está bloqueado al salir o al llegar, se entrega en el nodo vecino libre más cercano por la red (**supuesto nuestro, por confirmar**). | `Contexto.puntoDeEntrega` |
| SI-15 | `red.destino_bloqueado = no_evaluable`: un pedido es "inentregable por bloqueo" solo si su destino está bloqueado **durante toda** la ventana [registro, hora límite]; se excluye del colapso y se cuenta en `pedidos_inentregables_bloqueo`. | `MapaVial.bloqueadoDurante`, `Simulador.replanificar` |
| SI-16 | Estabilidad: cada entrega que cambia de unidad frente al plan vigente suma `estabilidad.penalidad_por_cambio = 16` soles a S (nunca a H), equivalente a 2 km en auto o a 2.7 km en moto. | `Compartido.evaluarRuta`, `AlgoritmoGenetico.asignarTramo` |
| SI-17 | Calibración de evaluaciones: la instancia de referencia son los pedidos registrados el día 2 de 08:00 a 12:00 del nivel, planificados en la hora 36, con 3 repeticiones por algoritmo con el mismo Ta; el tope es la media entera (truncada) de las evaluaciones. | `ExperimentoSimulacion.calibrar` |
| SI-18 | Una avería registrada desde el visualizador se aplica en el minuto simulado que corresponde al tiempo real de registro, con la unidad detenida en el último nodo alcanzado; un cambio de velocidad rige desde la siguiente replanificación (P16). | `Simulador.inyectarAveria`, `Reloj.ahora`, `Simulador.cambiarVelocidad` |
| SI-19 | Una simulación por JVM: el `Contexto` es por hilo, pero los algoritmos guardan estado estático (semilla, contadores) y la velocidad es global por tipo de unidad. | `ServicioSimulacion`, `TipoUnidad` |

---

## 3. Amenazas a la validez (para §12)

| ID | Amenaza | Mitigación | Riesgo residual |
|---|---|---|---|
| AV-01 | **Datos sintéticos** (pedidos del mes, bloqueos y averías). | Marca "DATOS SINTÉTICOS" en todas las salidas; generadores con semilla y parámetros documentados. | Las conclusiones no se pueden generalizar hasta tener los archivos oficiales. |
| AV-02 | **Reglas de avería no publicadas** (frecuencia, tipos, horario). | Averías desactivadas por defecto; generador parametrizable. | Si el profesor exige averías, los resultados pueden cambiar. |
| AV-03 | **Trasvase de 30 min por confirmar**; duración del mantenimiento pendiente. | Parámetros. | Sensibilidad no medida. |
| AV-04 | **Velocidades por confirmar** (40/25/12 frente a 20/40/14). | Parámetros; C_max se recalcula solo. | Todos los resultados dependen de este dato. |
| AV-05 | Reproducibilidad con parada por tiempo real. | Modo `--max-evaluaciones`; semillas registradas; reparto por nivel entre PCs. | Con parada por tiempo, las corridas no se repiten exactamente. |
| AV-06 | Aproximación conservadora de los bloqueos en el planificador (ventana del tramo). | Evita la mayoría de los encuentros en ruta; los que quedan se replanifican por evento. | Rutas algo más largas que las óptimas. |
| AV-07 | Censura en el tope de 30 días. | Kaplan-Meier y log-rank. | Poca potencia con 5 réplicas. |
| AV-08 | Encuentros entre unidades en movimiento no implementados. | Documentado como mejora futura. | Subestima la capacidad de rescate del sistema. |
| AV-09 | Operadores 2-opt y Cross-exchange provisionales (sin ISA). | Pesos configurables. | Podrían no coincidir con el ISA §4.2. |
| AV-10 | **Colapsos inevitables por destino bloqueado:** un cliente en un nodo bloqueado desde antes de su pedido y hasta después de su plazo colapsa con cualquier algoritmo (observado en el nivel BAJA: ambos algoritmos en el mismo minuto). | Causa "destino bloqueado" en el CSV; pregunta 11 al profesor. | Mientras no se aclare la regla, esos colapsos no discriminan entre algoritmos. Opción: que el generador de carga no ubique pedidos en nodos bloqueados durante su ventana, o analizar aparte las corridas con esa causa. |

---

## 4. Respuestas a las preguntas probables del jefe de práctica (versión etapas 8-15)

**1. ¿Qué es el colapso?**
Al menos una unidad de producto que supera su hora límite sin entregarse (P3 del profesor). La simulación termina en ese instante; se registran el pedido, la unidad y la causa. Lo verifican `SimuladorTest` y `AveriaTest`, que comprueban el colapso en el minuto exacto.

**2. ¿Cómo manejan los bloqueos?**
Por archivo y por horario. BFS en la retícula sin atravesar nodos bloqueados, con caché por intervalo de estado constante. El planificador evita los bloqueos activos durante la ventana de cada tramo. La unidad se mueve nodo a nodo; si encuentra un bloqueo nuevo, se replanifica en ese instante (`MapaVialTest`).

**3. ¿Cómo usan los almacenes intermedios?**
Cada unidad hace varios viajes. Cada viaje empieza con una RECARGA en el almacén que minimiza tardanzas y costo y tiene stock. Nor-Oeste y Este tienen 1 000 unidades, que se reponen a las 23:59:59; nunca se planifica una recarga sin stock (`ReplanificacionTest`).

**4. ¿Qué significa "replanificar, no volver a planificar"?**
En cada ciclo se parte del plan vigente reparado y del estado real de todas las unidades, incluidas las que están en ruta. Tabú arranca de ese plan; el AG lo siembra en su población. Ninguno devuelve algo peor que ese plan (`ReplanificacionTest`). La estabilidad se mide con `cambios_de_unidad`.

**5. ¿Qué pasa si una unidad se avería con carga?**
Queda como almacén temporal. Otra unidad puede ir, trasvasar (30 min) y entregar. Lo cubre el ejemplo del profesor en `AveriaTest`: la moto se avería en el minuto 36 y el auto entrega en el minuto 111, antes del límite de 180. Si nadie llega, el colapso ocurre en la hora límite exacta.

**6. ¿Cómo se reparten los pedidos grandes?**
Cualquier pedido de más de 24 paquetes se divide en bloques de 24. Además, los urgentes que los autos libres no alcanzan se dividen en partes de hasta 8, que cualquier unidad puede llevar. `SimuladorTest` muestra un caso que colapsa por capacidad sin división y se entrega a tiempo con ella.

**7. ¿Cómo garantizan comparaciones justas?**
Mismos pedidos, bloqueos, flota, almacenes y parámetros por nivel; solo cambian el algoritmo y la semilla. Mismo Ta. Reparto de corridas entre PCs por nivel. Modo reproducible por evaluaciones.

**8. ¿Qué operadores tiene Tabú? ¿Y el AG?**
- **Tabú:** Reubicación, Intercambio, Inserción, Recarga, y 2-opt y Cross-exchange (provisionales).
- **AG:** cruce OX, mutación, Split con estado (varios viajes, almacenes y trasvases), búsqueda local memética y siembra desde el plan vigente.

**9. ¿Qué escenarios tienen?**
- `EXPERIMENTO`: rápido, hasta el colapso o 30 días.
- `SIM_5D`: 5 días en ~30 min reales, o acelerado.
- `COLAPSO`: hasta el colapso.
- `DIA_A_DIA`: reloj real, con la estructura y una prueba corta.

Todos escriben el registro de eventos.

**10. ¿Qué falta confirmar?**
Ver `docs/preguntas_para_el_profesor.md`: velocidades, averías, trasvase, mantenimiento, detención de SIM_5D y encuentros en movimiento.
