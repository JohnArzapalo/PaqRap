# Propuesta de cambios al IEN 22.dis.experim.v01

> ## ⚠ Pendiente verificar contra el IEN v01
> El IEN v01 (PDF o Word) **no está** en la carpeta del proyecto. El "texto actual resumido" de cada fila sale del resumen que el equipo entregó en el chat, no del documento. Antes de aprobar, verifique cada fila contra el IEN v01, sobre todo el **§12**, cuyo contenido no se conoce, y ajuste la numeración de secciones si no coincide.

| Campo | Valor |
|---|---|
| Documento afectado | 22.dis.experim.v01 (Informe de Experimentación Numérica) |
| Versión propuesta | v02 (borrador para revisión del equipo) |
| Proyecto | PaqRap, Eq5E, horario H982 |
| Motivo general | Semana 06: experimento estático preliminar (opción B). Semana 07: se implementó el simulador mínimo y el experimento de simulación del IEN (algoritmo × carga), además de la 2da iteración de los algoritmos. |
| Estado | PROPUESTA: no modifica ningún documento del equipo. La aprobación y la incorporación quedan a cargo del equipo (control de cambios NTP-ISO/IEC 29110). |

---

## 1. Tabla de control de cambios

| # | Sección del IEN v01 | Texto actual (resumido) | Cambio propuesto | Motivo |
|---|---|---|---|---|
| C-01 | §3 (descripción de los algoritmos) | Afirma que ambos algoritmos manejan ventanas de tiempo, bloqueos y reasignación dinámica, y que el AG tiene búsqueda local. | Dividir en **3.a Diseño objetivo** y **3.b Implementación evaluada**. **Implementado:** plazos en la función objetivo y en el Split; Tabú con lista de candidatos y 5 operadores (Reubicación, Intercambio, Inserción, 2-opt y Cross-exchange; los dos últimos **provisionales**, a verificar contra el ISA §4.2); AG con Split y búsqueda local memética (Reubicación, Intercambio e Inserción); replanificación periódica cada Sa en el simulador. **No implementado:** bloqueos, almacenes intermedios con stock, turnos, averías y mantenimiento, y **replanificación de unidades en ruta (P16)**: una ruta despachada no cambia. | El informe no debe afirmar capacidades que el código no tiene. |
| C-02 | §5 (variables; definición de colapso) | Colapso = primer pedido que no se entrega en su plazo "y la planificación deja de ser estable"; difiere de SU-11 (riesgo RA-06). | **Criterio operativo único:** el colapso ocurre en el primer instante simulado en que la hora límite de un pedido ORIGINAL pasa sin que esté completamente entregado, esté o no asignado. La estabilidad pasa a variable secundaria (`aplazamientos`: entregas que un plan dejó sin asignar). Implementado en `Simulador.simular`. | Una sola definición medible, igual en DDP e IEN. **Cierra RA-06** cuando el equipo lo apruebe. |
| C-03 | §7 (diseño del experimento) | Factorial 2 × 3: algoritmo × carga (30 / 60 / 90-100 % de la capacidad teórica), 5 réplicas, horizonte de 5 días, variable principal = tiempo hasta el colapso. | **Se mantiene y ya se puede ejecutar** (`Experimento --modo simulacion`): algoritmo (TABU, AG) × carga (BAJA 30 %, MEDIA 60 %, ALTA 90 % de C_max por día, configurables) × 5 réplicas; semilla por réplica = 1000 + r; orden aleatorio (semilla 20260923); horizonte 7 200 min; Sa = 60 min; Ta = 2000 ms por llamada. Sin colapso en el horizonte, la corrida queda **censurada** en 7 200 min. Agregar **§7.b Experimento estático preliminar** (`--modo estatico`) como estudio complementario de calidad de solución. | Diseño ya ejecutable. |
| C-04 | §7 / §8 (capacidad teórica y niveles) | Niveles como % de la "capacidad teórica de la flota" (sin fórmula explícita). | Definir C_max = Σ n_k · q_k · ⌊T / t_k⌋, con T = 21 h y t_k = d̄/v_k + 1 h × e_k (ver `docs/capacidad_y_carga.md`). Con datos sintéticos, C_max propio = 1 536 paquetes/día; coincide en total con la hoja auxiliar del profesor (1 536, marcada REVISAR) pero no en composición por tipo. Por defecto se usa el propio (`carga.cmax_fuente`). **Generación de carga:** bootstrap de pedidos completos del archivo base, conservando hora del día y posición; semilla fija por nivel (ambos algoritmos reciben los mismos pedidos); archivos en `datos/generados/`. | Hacer reproducible y trazable el factor carga. |
| C-05 | §8 (variables y métricas) | Secundarias: costo acumulado, tiempo del algoritmo por iteración, % de pedidos a tiempo. | **Simulación:** `colapso_h` (principal), `censurada`, `costo_acumulado`, `km_acumulados`, `pedidos_entregados`, `pct_pedidos_en_plazo` (pedidos originales cuyo resultado ya se conoce al colapso o al horizonte), `replanificaciones`, `planificador_ms_medio` / `_max` (tiempo por llamada), `iteraciones_totales`, `evaluaciones_totales`, `aplazamientos` y viajes por vehículo. **Estático:** como en v01-b (H, S, % en plazo por pedido original, `vencidos_al_planificar`, `tramos_cambio_tipo`, etc.). | Medir exactamente las variables del IEN. |
| C-06 | §8 / §9 (criterio de parada) | Mismo presupuesto Ta para ambos. | Parada **por tiempo** (principal) porque las evaluaciones cuestan distinto (Split O(m·n²) frente a O(n)); se registran iteraciones y evaluaciones; hay un modo **por evaluaciones** reproducible (`--max-evaluaciones`, tope por llamada al planificador). | Reproducibilidad (verificada con pruebas). |
| C-07 | §9 (realización) | Tabú parte de C&W. | C&W se construye **dentro del Ta** de cada llamada (igual que la población inicial del AG). C&W: fusión clásica por extremos con control de capacidad y plazos; la inserción elige ruta y posición (primero tardanzas, luego costo). **Reparto de corridas:** por nivel entre PCs, no por algoritmo, para no confundir algoritmo con máquina. | Presupuesto equivalente; validez interna. |
| C-08 | §10 (análisis) | ANOVA de dos factores, α = 0.05, alternativa no paramétrica. | `analisis/analisis_experimento.py`: **sin censuradas**, ANOVA de dos factores (algoritmo × carga) sobre `colapso_h` con Shapiro-Wilk y Levene, o ART si no se cumplen; **con censuradas**, Kaplan-Meier por algoritmo en cada nivel y **log-rank** (propio, validado con un caso a mano; coincide con lifelines). Regla de decisión: tiempo hasta el colapso → % en plazo → costo acumulado. Para el estático: Welch o Mann-Whitney por instancia y ANOVA/ART. El ANOVA propio coincide con statsmodels (diferencias < 1e-11). | Tratamiento correcto de la censura. |
| C-09 | §12 (amenazas a la validez) | (Se desconoce el contenido actual: verificar.) | Agregar la sección 3 de esta propuesta. | Transparencia. |

---

## 2. Supuestos de la implementación actual

| ID | Supuesto | Dónde está en el código | Efecto |
|---|---|---|---|
| SI-01 | **Un solo almacén:** todas las unidades salen del central (27,14) y vuelven a él. Nor-Oeste y Este solo se dibujan. | `Compartido.ALMACEN_CENTRAL` | Distancias mayores que con varios almacenes; sin stock ni recargas intermedias. |
| SI-02 | **Estático:** un viaje por vehículo (tope de 408 paquetes). **Simulación:** varios viajes, porque la unidad vuelve al central y queda disponible desde su hora de regreso. | `Compartido.horasLlegada`, `Compartido.duracionRuta`, `Simulador` | El tope de 408 paquetes solo aplica al modo estático. |
| SI-03 | **Sin bloqueos:** distancia Manhattan directa. | `Compartido.distancia` | Distancias optimistas. |
| SI-04 | **Sin turnos ni alimentación** en la simulación (en C_max sí se descuentan 3 h). | `Simulador` | Operación optimista. |
| SI-05 | **Estático:** se planifica al final de la ventana. **Simulación:** los pedidos entran a pendientes cuando el reloj alcanza su registro y se planifican en el siguiente ciclo (cada Sa = 60 min). | `LectorPedidos.leerVentana`, `Simulador` | Hasta Sa minutos de espera antes del despacho. |
| SI-06 | **Entregas parciales:** más de 24 paquetes se dividen en partes de hasta 24; el colapso y el % en plazo se miden sobre el pedido original. | `LectorPedidos`, `Simulador` | — |
| SI-07 | **Velocidades del enunciado** (40 / 25 / 12 km/h), configurables. La hoja auxiliar (20 / 40 / 14) está pendiente de confirmación. | `TipoUnidad`, `Parametros` | Si cambian, cambian C_max y todos los resultados. |
| SI-08 | **Plazos como restricción blanda** dentro del planificador (penalizados en H); capacidad como restricción dura. | `Compartido.evaluarSolucion`, `Compartido.cumpleCapacidad` | — |
| SI-09 | **Despacho inmediato y rutas fijas:** en cada ciclo se despachan todas las rutas planificadas y no se replanifican en ruta (P16 pendiente). | `Simulador` | Las unidades pueden quedar ocupadas cuando llega un pedido urgente. |
| SI-10 | **Carga generada** por bootstrap del archivo base (misma distribución espacial y temporal). | `GeneradorCarga` | Con el archivo sintético, toda la carga llega entre las 08:00 y las 12:00 de cada día. |
| SI-11 | **Datos sintéticos:** hasta recibir el archivo oficial, todo se ejecuta con `ventas202609_SINTETICO_08a12h.txt`; todas las salidas llevan "DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME". | `Experimento`, `analisis/*.py` | Los resultados actuales no son válidos para el informe. |

---

## 3. Amenazas a la validez (para §12)

| ID | Amenaza | Tipo | Mitigación implementada | Riesgo residual |
|---|---|---|---|---|
| AV-01 | Reproducibilidad con parada por tiempo real. | Confiabilidad | Registro de iteraciones y evaluaciones; modo `--max-evaluaciones` reproducible (pruebas `ReproducibilidadTest` y `SimuladorTest`). | Con parada por tiempo, las corridas no se repiten exactamente. |
| AV-02 | Confusión algoritmo-máquina si las corridas se reparten por algoritmo entre PCs distintas. | Interna | Instrucción de repartir **por nivel** (`docs/capacidad_y_carga.md`). | Si se reparte mal, el efecto del algoritmo queda sesgado. |
| AV-03 | **Concentración temporal de la carga sintética:** toda la carga del día llega entre 08:00 y 12:00, así que hasta BAJA satura la mañana. En la corrida corta de verificación todas las corridas colapsaron entre los días 1 y 3. | Constructo / externa | Marca SINTÉTICO; el generador respeta el perfil del archivo que se use. | Desaparece con el archivo oficial si este reparte los pedidos en el día. |
| AV-04 | **Autos como cuello de botella:** las entregas de más de 8 paquetes solo caben en autos (10 unidades). En la verificación, todos los colapsos vinieron de pedidos con plazo de 4 h lejos del central que solo un auto puede llevar. | Constructo | Se reporta `pedido_colapso`. | Es una característica del caso, no un error; conviene analizarla. |
| AV-05 | **Despacho inmediato sin replanificación en ruta (P16).** | Constructo | Documentado; `aplazamientos` como indicador. | Puede adelantar el colapso frente a un sistema que reasigna en ruta. |
| AV-06 | **Censura:** si hay corridas sin colapso, el ANOVA sobre `colapso_h` está sesgado. | Conclusión estadística | Kaplan-Meier y log-rank automáticos cuando hay censura. | Con pocas réplicas, el log-rank tiene poca potencia. |
| AV-07 | El Split no controla cuántas unidades hay de cada tipo. | Interna | La decodificación usa unidades reales; se registra `tramos_cambio_tipo`. | — |
| AV-08 | Operadores 2-opt y Cross-exchange **provisionales** (el ISA no está disponible). | Constructo | Marcados en el código; pesos configurables. | Podrían no coincidir con el ISA §4.2. |
| AV-09 | C_max con aproximaciones optimistas (un trayecto por viaje, viajes llenos). | Constructo | Documentado; comparado con la hoja del profesor. | Los niveles podrían corresponder a una carga real algo mayor. |

---

## 4. Respuestas a las preguntas probables del jefe de práctica (actualizadas a la semana 07, etapas 5-7)

Solo se afirma lo que está en el código (`src/main/java/pe/edu/pucp/gamesoft/paqrap/`).

**1. ¿Por qué dos niveles (H, S)?**
Por la política de entregar todo en plazo. `Compartido.mejorQue` compara primero H y solo si hay empate compara S. En el Split se logra lo mismo con una penalidad de 10⁶ por entrega tarde.

**2. ¿Cuál es el vecindario de Tabú?**
Cinco operadores en `OperadoresVecindario`, compartidos con el AG:
- **Reubicación** e **Intercambio**.
- **2-opt:** invierte un tramo de una ruta.
- **Cross-exchange:** intercambia segmentos de 1 a 3 entregas entre dos rutas.
- **Inserción:** saca una entrega de "sin asignar" y la pone en la mejor posición.

Por iteración se generan 20 candidatos con pesos configurables (0.35 / 0.35 / 0.15 / 0.15). 2-opt y Cross-exchange son provisionales hasta verificarlos contra el ISA.

**3. ¿Qué guarda la lista tabú?**
- **Entre rutas:** (pedido, unidad de la que salió); un movimiento es tabú si lleva el pedido a esa unidad, es decir, se compara contra el **destino**.
- **Dentro de una ruta:** (pedido, unidad, posición anterior).
- **2-opt:** prohíbe devolver los extremos del tramo a su posición.
- **Cross-exchange:** se trata como reubicaciones entre rutas.
- Una entrada es vigente solo si su vencimiento es mayor que la iteración actual.

Lo cubre `ListaTabuTest`.

**4. ¿Aspiración, y qué pasa si todo es tabú?**
Se acepta un candidato tabú si mejora la mejor solución global. Si todo es tabú y nada aspira, se acepta el menos malo.

**5. ¿Tabú resuelve I2 de Main?**
Sí, H = 0 con S/ 816. Además, C&W ya entrega H = 0 en I2 gracias a la nueva inserción por ruta y posición.

**6. ¿Qué hace el Split?**
Programación dinámica O(m·n²) que corta la permutación en como máximo m rutas, con costo km × costo/km + 10⁶ × tardes. Si al decodificar falta una unidad del tipo pedido, se usa la mejor unidad libre con valores reales y se cuenta en `tramos_cambio_tipo`.

**7. ¿Por qué el AG es "híbrido"? ¿Tiene búsqueda local?**
Sí (2da iteración). Se aplica al 20 % de los hijos y siempre al mejor de cada generación, con hasta 30 movimientos de Reubicación o Intercambio (más Inserción si hay entregas sin asignar), aceptando solo mejoras. La mejora vuelve al cromosoma: las rutas concatenadas forman la permutación y cada pedido toma el tipo de su unidad.

Medición con 5 réplicas y Ta = 1 s en 4 instancias: H no empeora en ninguna (baja de 9.6 a 9.2 en la de 4 h); S baja en 2, queda igual en 1 y sube 0.3 % en 1. Ninguna diferencia es significativa con n = 5 (Mann-Whitney, p ≥ 0.15). Sin la Inserción, H empeoraba en la instancia de 4 h, por eso se agregó. Se desactiva con `ag.busqueda_local=no`.

**8. ¿Cómo miden el tiempo hasta el colapso?**
Con el simulador (`Simulador`):
- el reloj avanza en minutos;
- los pedidos entran cuando llega su hora de registro;
- cada Sa = 60 min se planifican los pendientes con las unidades que están en el central;
- las rutas se despachan y la unidad vuelve a estar disponible a su hora de regreso.

El colapso se registra en el minuto exacto de la hora límite del primer pedido original no entregado a tiempo, aunque se detecte en un ciclo posterior. Lo verifica `SimuladorTest`. Sin colapso en 7 200 min, la corrida queda censurada.

**9. ¿Cómo definieron la carga?**
C_max = Σ n_k q_k ⌊21/t_k⌋ (ver `docs/capacidad_y_carga.md`). Los niveles son 30 / 60 / 90 % de C_max por día. Los pedidos se generan remuestreando el archivo con una semilla fija por nivel, así que ambos algoritmos reciben exactamente los mismos pedidos.

**10. ¿Cómo tratan las corridas censuradas?**
Con Kaplan-Meier y log-rank por nivel. El log-rank está validado con un caso a mano (χ² = 8/13) y coincide con lifelines. Si no hay censura: ANOVA de dos factores o ART.

**11. ¿Cómo garantizan una comparación justa y reproducible?**
- Mismo Ta por llamada; C&W y la población inicial se construyen dentro de ese Ta.
- Mismos pedidos por nivel, misma semilla por réplica, orden aleatorio.
- Reparto por nivel entre PCs.
- Modo por evaluaciones reproducible.

**12. ¿Por qué colapsa tan pronto con los datos actuales?**
Porque el archivo sintético concentra la carga entre 08:00 y 12:00. Además, los pedidos con plazo de 4 h lejos del central y de más de 8 paquetes solo los puede llevar un auto. Con el archivo oficial se espera otro comportamiento (AV-03, AV-04).

**13. ¿Manejan bloqueos, varios almacenes y replanificación en ruta?**
No todavía: son pendientes (SI-01, SI-03, SI-09).

**14. ¿Qué es configurable?**
`config/parametros.properties`:
- velocidades y semáforo;
- penalidad y horas de entrega;
- Ta, réplicas, ventanas;
- Sa, horizonte, C_max (fuente, horas efectivas), niveles, días y semilla de carga;
- parámetros de Tabú (duración, candidatos, pesos) y del AG (población, búsqueda local).
