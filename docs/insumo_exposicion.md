# Insumo para la exposición: PaqRap, Búsqueda Tabú frente al Algoritmo Genético

Documento fuente para preparar las diapositivas. Reúne todo lo necesario, desde la elección de los dos algoritmos hasta la experimentación numérica completa: diseño, realización y conclusión. Los números vienen de `Resultados/` (datos oficiales del profesor) y de `docs/avance_sem07.md`.

- Curso: 1INF54 Proyecto de Diseño y Desarrollo de Software, PUCP, 2026-2.
- Equipo Eq5E:
  - Búsqueda Tabú: Alcca y Arzapalo.
  - Algoritmo Genético Híbrido + Split: Alvarado y Torres.
  - Los cuatro deben poder explicar ambos algoritmos.
- Estado: rama `feature/JL`. **Algoritmo elegido: Búsqueda Tabú** (§9, SI-27).
- Las marcas **[CONTRASTAR CON EL ISA]** señalan lo que no está en el repositorio y hay que verificar con el documento de diseño (ISA) antes de exponer.

---

## 1. El problema

**Qué es:** planificar y simular en tiempo real el reparto de paquetes de una empresa (PaqRap) en una ciudad en retícula. Es un problema de ruteo de vehículos **MD-HVRP-TW dinámico**:
- **MD, varios depósitos:** un almacén central y dos intermedios.
- **HVRP, flota heterogénea:** autos, motos y bicicletas con distinta capacidad, velocidad y costo.
- **TW, ventanas de tiempo:** cada pedido tiene una hora límite.
- **Dinámico:** los pedidos llegan mientras la flota ya está en ruta, y hay bloqueos, averías y mantenimientos.

**Datos del caso:**

| Elemento | Valor |
|---|---|
| Mapa | Retícula de 70 × 50 km, nodos cada 1 km, calles de doble sentido |
| Almacén central | (27,14), inventario infinito |
| Almacenes intermedios | Nor-Oeste (12,38) y Este (57,27), 1 000 unidades cada uno, se recargan a las 23:59:59 |
| Flota | 10 autos (24 paquetes, 40 km/h, S/ 8/km); 15 motos (8 paq., 25 km/h, S/ 6/km); 12 bicicletas (4 paq., 12 km/h, S/ 3/km) |
| Plazos | Normal de 36 h; priorizados de 4, 8, 12 o 18 h. Hora límite = registro + plazo |
| Entrega | 1 h en el cliente (no cuenta dentro del plazo) |
| Bloqueos | Planificados y conocidos de antemano; un nodo bloqueado no se atraviesa |
| Averías | Tipos 1, 2 y 3; otra unidad recoge la carga con un trasvase de 30 min (las unidades son almacenes móviles) |
| Turnos | 1 h de alimentación por turno (03, 11 y 19 h) |

**Colapso logístico** (definición del profesor): basta **un pedido no entregado dentro de su plazo** para que la simulación termine.

**Escenarios del enunciado:**
1. **Día a día:** operación en tiempo real con el visualizador (demostración).
2. **Simulación de 5 días:** 5 días simulados; la corrida colapsa o no. Es la base del experimento.
3. **Hasta el colapso:** se simula hasta que colapsa y se mide cuánto dura.

---

## 2. Por qué dos metaheurísticas y por qué estas dos

**[CONTRASTAR CON EL ISA]** Los argumentos siguientes salen del problema y del código; verifiquen que coinciden con la justificación del ISA.

**Por qué metaheurísticas y no un método exacto:**
- El VRP con ventanas de tiempo y flota heterogénea es NP-difícil, y en esta versión además es dinámico.
- El planificador debe responder cada vez que llegan pedidos o hay eventos, con un presupuesto fijo: **Ta = 2 s por replanificación**, cada 60 minutos simulados.
- Un método exacto no garantiza terminar en ese tiempo con cientos de pedidos; una metaheurística siempre devuelve la mejor solución encontrada dentro del presupuesto.

**Por qué estas dos: representan las dos grandes familias.**

| | Búsqueda Tabú | Algoritmo Genético Híbrido + Split |
|---|---|---|
| Familia | De trayectoria: mejora una solución paso a paso | Poblacional: evoluciona un conjunto de soluciones |
| Fortaleza esperada | Mejora localmente el plan vigente, lo que encaja con «replanificar, no planificar desde cero» | Explora más ampliamente el espacio de soluciones |
| Cómo trata la flota heterogénea | Operadores que mueven pedidos entre unidades de cualquier tipo | Cada gen lleva el tipo de vehículo; el Split (Prins, 2004) corta la secuencia en rutas por vehículo |
| Referencia clásica | Glover (1986); aplicaciones a VRP de Gendreau y Taillard | Prins (2004), AG con Split para VRP; versión memética con búsqueda local |

Compararlas en las mismas condiciones responde qué tipo de método le conviene a PaqRap.

---

## 3. La base común: lo que comparten ambos algoritmos

Para que la comparación sea justa, todo lo que decide qué plan es mejor está en un **evaluador único** (`Compartido.evaluarRuta` y `Contexto`). Ningún algoritmo tiene reglas propias.

**Función objetivo jerárquica:**
1. Primero **H** = pedidos sin asignar + pedidos fuera de plazo. Se minimiza.
2. Luego **S** = costo en soles (km × costo/km de cada tipo), más dos penalidades internas que solo desempatan:
   - **estabilidad:** S/ 16 por cada pedido que el plan nuevo pone en otra unidad (equivale a 2 km en auto);
   - **holgura de seguridad:** S/ 200 por hora que le falte a una entrega para tener 60 min de margen antes de su hora límite efectiva.
3. **Ningún ahorro en S compensa un pedido tarde.**

**El evaluador de rutas simula cada ruta paso a paso:**
- respeta la posición y la hora real de la unidad;
- considera la carga a bordo, la capacidad, el stock de los almacenes, las recargas y los trasvases;
- evita los bloqueos de toda la ventana del tramo;
- descuenta la hora de alimentación y termina la ruta antes del mantenimiento de la unidad.

**Replanificación:**
- Cada 60 minutos simulados (Sa) y ante eventos, el simulador arma el **estado real**: dónde está cada unidad, qué lleva, el stock y las averiadas.
- Repara el plan vigente (le inserta los pedidos nuevos) y se lo entrega al algoritmo, que tiene **Ta = 2 s** para mejorarlo.
- Ambos algoritmos **parten del plan vigente reparado**. Ninguno puede devolver un plan peor que ese.

**Simulador de eventos discretos:**
- mueve las unidades nodo a nodo y aplica entregas, recargas, averías, mantenimientos y bloqueos;
- detecta el colapso en cuanto un pedido supera su hora límite sin entregarse.

---

## 4. Búsqueda Tabú (Alcca, Arzapalo)

| Componente | Diseño |
|---|---|
| Solución inicial | El plan vigente reparado; en el primer ciclo, Clarke & Wright |
| Vecindario | 20 candidatos por iteración. Si hay pedidos sin asignar, el primero es una **Inserción**; el resto, un operador al azar por pesos: Reubicación (0.35), Intercambio (0.35), 2-opt (0.15), Cross-exchange (0.15) y Recarga (0.10) |
| Lista tabú | Prohíbe deshacer el movimiento aceptado (pares pedido-unidad o pedido-posición) durante **8 iteraciones** |
| Aspiración | Se acepta un movimiento tabú si mejora la mejor solución global (primero H, luego S) |
| Si todos son tabú | Se acepta el menos malo, para no quedar bloqueado |
| Parada | Al agotar Ta = 2 s. Sin corte por estancamiento, para usar el mismo presupuesto que el AG |
| Resultado | La mejor solución global encontrada |

Clases: `BusquedaTabu`, `ListaTabu`, `ParTabu`, `Movimiento`, `OperadoresVecindario` y `Heuristicaconstructiva`. 2-opt y Cross-exchange siguen marcados como provisionales frente al ISA §4.2.

---

## 5. Algoritmo Genético Híbrido + Split (Alvarado, Torres)

| Componente | Diseño |
|---|---|
| Cromosoma | Permutación de los pedidos (orden de atención) + tipo de vehículo de cada pedido |
| Decodificador (Split con estado) | Fija los pedidos a bordo en su unidad; corta la permutación en tramos que caben en el tipo del gen; asigna cada tramo a la unidad y al punto de carga (almacén con stock, carga en el sitio o trasvase) que menos aumenta tardanzas y costo, evaluado con el evaluador común |
| Población inicial | 30 individuos: 30 % sembrado con el plan vigente y variaciones suyas, el resto aleatorio |
| Selección | Torneo binario |
| Cruce | OX (cruce ordenado), probabilidad 0.85 |
| Mutación | Probabilidad 0.15: intercambia dos pedidos y, la mitad de las veces, cambia el tipo de vehículo de uno |
| Elitismo | El mejor pasa a la siguiente generación |
| Búsqueda local memética | Sobre el 20 % de los hijos y siempre sobre el mejor: hasta 30 movimientos de Reubicación, Intercambio o Inserción, solo mejoras |
| Parada | Al agotar Ta = 2 s. Desde la etapa 27 revisa el reloj **antes de cada hijo** |
| Resultado | El mejor decodificado; si el plan vigente es mejor, devuelve el plan vigente |

Clases: `AlgoritmoGenetico`, `Cromosoma`, `Poblacion` y la búsqueda local con `OperadoresVecindario`.

---

## 6. Diseño del experimento

### 6.1 Pregunta y criterio

**¿Qué algoritmo es mejor para PaqRap?** El profesor indicó el criterio:
- **Es normal que algunas corridas de 5 días lleguen al colapso.**
- **Gana el algoritmo con menor porcentaje de corridas con colapso.**
- Si empatan, deciden en este orden: **el tiempo hasta el colapso, la estabilidad** (pedidos que cambian de unidad al replanificar) **y el costo por pedido entregado**. El orden estabilidad-costo lo fijó el equipo (SI-27, §9).

### 6.2 Datos: los oficiales del profesor

- `juego_de_datos/`: ventas y bloqueos **mensuales de enero de 2026 a diciembre de 2028** (36 meses), más el mantenimiento preventivo de septiembre y octubre de 2026.
- Los 36 meses pasan el validador (`ValidadorEntradas`) **sin errores**: formato, coordenadas, plazos, tramos y nodos alcanzables.
- **Hallazgo clave:** cada mes trae 5 000 pedidos concentrados en cada vez menos días, así que **la demanda diaria crece mes a mes**. La capacidad teórica de la flota es **C_max = 1 296 paquetes/día**, calculada con los pedidos oficiales: Σ unidades × capacidad × viajes por día, con 21 h útiles.

| Mes | Días con pedidos | Paquetes/día | % de C_max |
|---|---|---|---|
| 2026-01 | 31 | 111 | 9 % |
| 2026-06 | 30 | 583 | 45 % |
| 2026-09 | 29 | 948 | 73 % |
| 2026-10 | 27 | 1 014 | 78 % |
| 2026-11 | 23 | 1 194 | 92 % |
| 2026-12 | 22 | 1 248 | 96 % |
| 2027-01 | 20 | 1 372 | 106 % |
| 2027-02 | 18 | 1 558 | 120 % |
| 2027-06 | 13 | 2 121 | 164 % |
| 2028-12 | 6 | 4 536 | 350 % |

### 6.3 Factores y niveles

- **Factor 1: algoritmo**, con dos niveles: Búsqueda Tabú y AG.
- **Factor 2: carga.** No se inventaron niveles: son **grupos de meses reales** con exigencia creciente.

| Nivel | Meses reales | Carga media | Situaciones (tramos de 5 días) |
|---|---|---|---|
| BAJA | 2026-09 y 2026-10 | 78 % de C_max | 40 |
| MEDIA | 2026-11 y 2026-12 | 98 % | 35 |
| ALTA | 2027-01 y 2027-02 | 115 % | 28 |

- **Por qué esos meses:**
  - Con menos del ~75 % de C_max casi no hay colapsos en 5 días (la demanda real de septiembre, días 1-5, dio 0 de 8).
  - Por encima del ~130 % colapsa todo.
  - Solo en esa zona intermedia el porcentaje puede distinguir algoritmos. Lo confirmó la calibración: con carga al 90-115 % colapsa entre 38 % y 75 % de las corridas.
- Los niveles del IEN original (30/60/90 % de C_max) no producían ningún colapso en 5 días.

### 6.4 Situaciones: ventanas reales de 5 días (diseño pareado)

- Cada **situación** es un tramo real de 5 días de un mes del profesor, con **sus pedidos, sus bloqueos y sus mantenimientos**. No se inventa ningún pedido.
- Los tramos empiezan cada día y deben terminar antes del último pedido del archivo. Se eligen al azar, con semilla fija, hasta 40 por nivel.
- **Tabú y AG corren exactamente el mismo tramo**: mismos pedidos, bloqueos a las mismas horas, flota, almacenes y parámetros. Es un **diseño pareado**, como pidió el profesor.

### 6.5 Réplicas y semillas

- **3 corridas completas** con semillas distintas para el azar interno de los algoritmos (1000, 2000 y 3000). Los tramos son los mismos en las tres.
- Por qué 3: en cerca del 18 % de los tramos colapsa uno solo de los dos algoritmos, así que el azar pesa. Tres corridas muestran si el veredicto se repite, y juntas (**309 pares**) permiten detectar diferencias de unos 10 puntos en el % de colapsos.
- Total: **3 × 103 tramos × 2 algoritmos = 618 simulaciones de 5 días**.

### 6.6 Controles de justicia de la comparación

| Riesgo | Control |
|---|---|
| Que un algoritmo tenga situaciones más fáciles | Mismo tramo para ambos (pareado) |
| Que un algoritmo tenga más cómputo | Mismo Ta = 2 s. Tiempo medio medido por replanificación: Tabú 2 004 ms, AG 2 046 ms |
| Que el orden o la máquina favorezcan a uno | Corridas en orden aleatorio, ambos algoritmos en la misma PC, hasta 7 simulaciones en paralelo en 8 núcleos físicos |
| Reglas distintas | Evaluador único (`Compartido`/`Contexto`) y los mismos parámetros para ambos |
| Datos alterados | Hash SHA-256 de cada archivo de entrada en cada corrida |
| Azar de una sola corrida | 3 semillas y análisis combinado |

### 6.7 Análisis estadístico

- **Variable principal (sí/no por corrida):**
  - % de colapsos con intervalo de confianza exacto (Clopper-Pearson);
  - **prueba de McNemar exacta**, la adecuada para proporciones pareadas, que solo usa los pares donde colapsa uno de los dos, por nivel y global;
  - **regresión logística GEE agrupada por tramo** para combinar las 3 corridas sin contar dos veces el mismo tramo.
- **Desempates:**
  - tiempo hasta el colapso con Kaplan-Meier y log-rank;
  - costo por pedido, km por pedido y estabilidad con pruebas pareadas (t pareada si las diferencias son normales; si no, Wilcoxon).
- **Por qué costo POR PEDIDO:** el costo acumulado es menor en una corrida que colapsa antes, y premiaría al algoritmo que colapsa.
- **Potencia:** calculada con `analisis/potencia.py` (fórmula de Connor para McNemar, verificada con la potencia exacta y con Monte Carlo).
- Nivel de significancia: α = 0.05.

---

## 7. Realización

### 7.1 Cómo se hizo (etapas 23 a 29)

| Etapa | Qué se hizo | Por qué importa para el experimento |
|---|---|---|
| 23 | **Hora límite efectiva:** si el destino se bloquea antes del plazo y hasta después, el margen se mide hasta el inicio del bloqueo | Evita planes que llegan «a tiempo» justo antes de que se cierre el destino |
| 24 | **Algoritmos sin estado estático:** cada ejecución es un objeto con su semilla; `--hilos N` | Permite correr simulaciones en paralelo con resultados idénticos a correrlas en serie |
| 25 | **Diseño pareado** y análisis de proporciones (McNemar, potencia) | El criterio del profesor es un porcentaje; hace falta la prueba correcta |
| 26 | Ensayo completo con datos sintéticos (240 corridas) | Probó todo el procedimiento y **detectó que el AG usaba 38 % más tiempo** del permitido |
| 27 | **El AG respeta Ta** dentro de la generación (de 2 601 a 2 050 ms con 7 hilos) | Sin esto la comparación favorecía al AG |
| 28 | **Datos oficiales**, ventanas reales y experimento completo. Se corrigieron dos errores: mapa compartido entre hilos y mantenimiento sin filtro de mes | Resultados válidos para el informe |
| 29 | **3 corridas** con semillas distintas y análisis combinado | Veredicto estable |

### 7.2 Ejecución

- PC: AMD Ryzen 7 5700U (8 núcleos físicos, 16 lógicos), 14 GB de RAM; Java 21 (JDK 22), 7 simulaciones en paralelo.
- Unas **1.7 h por corrida completa** (206 simulaciones de 5 días); unas 5 h las tres.
- Pruebas automáticas: **79 pruebas JUnit, todas pasan.**

---

## 8. Resultados

### 8.1 Variable principal: % de corridas con colapso

**Por corrida (cada una con 103 pares):**

| Corrida (semilla) | Tabú | AG | Solo Tabú colapsa | Solo AG colapsa | p (McNemar) |
|---|---|---|---|---|---|
| 1 (1000) | 46.6 % | 49.5 % | 8 | 11 | 0.65 |
| 2 (2000) | 49.5 % | 44.7 % | 14 | 9 | 0.40 |
| 3 (3000) | 42.7 % | 50.5 % | 8 | 16 | 0.15 |
| **Combinadas (309 pares)** | **46.3 %** | **48.2 %** | 30 | 36 | **GEE p = 0.46** (odds ratio Tabú/AG = 0.90) |

**Por nivel (las 3 corridas combinadas):**

| Nivel | Pares | Tabú | AG | Solo Tabú / solo AG | p |
|---|---|---|---|---|---|
| BAJA (78 %) | 120 | 27.5 % [20, 36] | 26.7 % [19, 36] | 12 / 11 | 1.00 |
| MEDIA (98 %) | 105 | 35.2 % [26, 45] | 43.8 % [34, 54] | 13 / 22 | 0.18 |
| ALTA (115 %) | 84 | 86.9 % [78, 93] | 84.5 % [75, 91] | 5 / 3 | 0.73 |

Entre corchetes, el intervalo de confianza al 95 %.

**Lectura:**
- **Ninguna corrida ni ningún nivel muestra diferencia significativa.**
- **La dirección cambia de una corrida a otra**: en la corrida 2 colapsa más Tabú; en la 1 y la 3, más el AG.
- **En el % de colapsos, los dos algoritmos son equivalentes.**
- Como dijo el profesor, con la demanda creciente se llega al colapso: en ALTA colapsa cerca del 85 % de las corridas.

**Causas de colapso (3 corridas):**

| Causa | Tabú | AG |
|---|---|---|
| Llegada tardía de un pedido planificado | 50 | 61 |
| Llegada tardía de un pedido a bordo | 33 | 30 |
| Destino bloqueado | 60 | 58 |

Cerca del 40 % de los colapsos se deben a un destino bloqueado, que afecta a ambos por igual (ruido). **Sin esos colapsos el resultado es el mismo**: solo Tabú 25 pares frente a solo AG 26.

### 8.2 Desempates (3 corridas combinadas)

| Criterio | BAJA | MEDIA | ALTA |
|---|---|---|---|
| 1. Tiempo hasta el colapso (log-rank) | igual (p = 0.88) | igual (p = 0.19) | igual (p = 0.91) |
| 2. Pedidos que cambian de unidad al replanificar (mediana en 5 días, Tabú / AG) | 27 / 289.5: **Tabú, 10 veces menos** | 27 / 175: **Tabú, 6 veces menos** | 20 / 87: **Tabú, 4 veces menos** |
| 3. Costo por pedido entregado (mediana, Tabú / AG) | S/ 160.1 / 153.1: **AG 4.5 % menos** | S/ 162.5 / 160.3: **AG 1.4 % menos** | S/ 164.2 / 164.0: igual |
| km por pedido | AG 5.8 % menos | AG 2.4 % menos | AG 0.7 % menos |

Estos desempates **se repiten igual en las tres corridas**. Con varias corridas los p-valores son orientativos, porque se repiten tramos; las diferencias marcadas tienen p < 10⁻⁹.

### 8.3 Gráficos disponibles

En `Resultados/combinado/analisis/`:
- `pct_colapso.png`: % de colapsos por nivel y algoritmo, con intervalos de confianza. **Es la diapositiva central.**
- `kaplan_meier_BAJA.png`, `kaplan_meier_MEDIA.png` y `kaplan_meier_ALTA.png`: proporción sin colapso a lo largo de las 120 h.
- `caja_costo_por_pedido.png`, `caja_cambios_de_unidad.png` y `caja_km_por_pedido.png`: los desempates.
- `caja_planificador_ms_medio.png`: el tiempo por replanificación, como prueba de que ambos usan el mismo presupuesto.

Tablas en `.md` y `.csv` en la misma carpeta: `colapsos`, `colapsos_por_corrida`, `comparaciones`, `decision`, `descriptiva` y `causas_colapso`. También está el resumen de una página: `Resultados/corrida1_semilla1000/resumen_corrida1.html`.

---

## 9. Conclusión

1. **Criterio principal (% de colapsos): empate.** Tabú 46.3 % y AG 48.2 %, sin diferencia significativa en ninguna corrida ni nivel.
2. **Tiempo hasta el colapso: empate.**
3. **Costo por pedido:** el AG es algo más barato con carga baja y media (1.4 a 4.5 %); con carga alta no hay diferencia.
4. **Estabilidad:** Tabú reasigna de 4 a 10 veces menos pedidos entre unidades al replanificar, en todos los niveles.

### Algoritmo elegido: **Búsqueda Tabú** (decisión del equipo, SI-27)

**Regla de decisión:** % de colapsos → tiempo hasta el colapso → **estabilidad** → costo por pedido. Con esa regla, **Tabú gana en los tres niveles, en las tres corridas y en el combinado**, siempre por la estabilidad.

**Por qué la estabilidad va antes que el costo.** Son argumentos que ya estaban en el proyecto antes del experimento:
1. **El profesor pidió «replanificar, no planificar desde cero».** Por eso la estabilidad ya era parte de la función objetivo de ambos algoritmos: S/ 16 por pedido que cambia de unidad (SI-16).
2. **Diferencia práctica:** un 1-5 % de costo es poco frente a cambiar de unidad a cientos de pedidos. Cada cambio significa reasignar choferes y carga ya asignada en la operación real.
3. **Con carga alta el AG pierde incluso su ventaja de costo**, y es justo donde el sistema está más exigido.

**Cómo decirlo con honestidad en la exposición:** el criterio del profesor (% de colapsos) empató. El orden de los desempates se precisó entonces, con argumentos del caso, y quedó registrado como supuesto SI-27. Si se hubiera puesto el costo antes, el AG ganaría en BAJA y MEDIA, con un ahorro de 1.4 a 4.5 %.

**Qué pierde PaqRap al elegir Tabú:** entre 1.4 y 4.5 % más de costo por pedido con carga baja y media (unos S/ 2 a 7 por pedido), y de 0.7 a 5.8 % más km. No pierde nada en colapsos ni en tiempo hasta el colapso.

---

## 10. Límites y amenazas a la validez

- **Tramos solapados:** empiezan cada día y se superponen, así que no son del todo independientes. La GEE agrupada por tramo lo tiene en cuenta.
- **Potencia:** con 309 pares se detectan diferencias de unos 10 puntos en el % de colapsos. Si existe una diferencia menor, este experimento no la ve.
- **Una sola PC y parada por tiempo (Ta):** los resultados dependen de la velocidad de la máquina, pero igual para ambos algoritmos.
- **Supuestos por confirmar con el profesor:** medir el % de colapsos en 5 días (pregunta 13), usar meses como niveles (SI-26), la regla de destino bloqueado (pregunta 11) y la duración del mantenimiento (24 h).
- **Averías:** no se incluyeron en el experimento (el profesor no dio archivo de averías).
- **Provisionales:** 2-opt y Cross-exchange están pendientes de verificar frente al ISA §4.2.

---

## 11. Supuestos principales (SI) para mencionar

| Código | Supuesto |
|---|---|
| SI-06 | Alimentación: 1 h a las 03, 11 y 19 h, dondequiera que esté la unidad |
| SI-10 | Mantenimiento de 24 h en el central; las rutas terminan antes |
| SI-12 | Velocidades del enunciado, 40/25/12 km/h (decisión del equipo) |
| SI-15 | Destino bloqueado durante todo el plazo: pedido «inentregable por bloqueo», no cuenta como colapso |
| SI-16 | Penalidad de estabilidad: S/ 16 por pedido que cambia de unidad (solo en S) |
| SI-20 | Holgura de seguridad: 60 min de margen deseado, penalidad solo en S |
| SI-21 | Tolerancia de 10⁻⁶ min en la hora límite |
| SI-23 | Hora límite efectiva ante bloqueos del destino |
| SI-24 | Variable principal binaria y diseño pareado |
| SI-26 | Ventanas reales como situaciones y niveles como grupos de meses |
| SI-27 | Orden de desempates: tiempo hasta el colapso → estabilidad → costo; se elige la Búsqueda Tabú |

La lista completa (SI-01 a SI-27) está en `docs/propuesta_cambios_IEN.md`.

---

## 12. Preguntas probables del JP y respuestas cortas

1. **¿Por qué no eligieron un solo algoritmo desde el inicio?**
   El objetivo de la fase es comparar con evidencia. Tabú y el AG representan las dos familias de metaheurísticas (trayectoria y población).
2. **¿Cómo garantizan que la comparación es justa?**
   Mismo tramo para ambos, mismo Ta medido (2 004 frente a 2 046 ms), evaluador único, mismos parámetros, orden aleatorio y hashes de los datos.
3. **¿Por qué McNemar y no chi-cuadrado?**
   Porque las corridas están pareadas (mismo tramo): solo informan los pares en que colapsa uno de los dos.
4. **¿Por qué los niveles son meses y no 30/60/90 %?**
   Con 30/60/90 % no colapsa nada en 5 días. Los datos del profesor ya traen demanda creciente, así que usamos meses reales sin inventar pedidos.
5. **¿Por qué 3 corridas?**
   El azar interno de los algoritmos pesa (18 % de pares discordantes). Tres semillas muestran que el veredicto se repite y bajan la diferencia detectable a unos 10 puntos.
6. **Si empatan, ¿no da igual cuál usar?**
   En colapsos sí, pero no en operación: el AG ahorra un poco en costo con carga baja, y Tabú reasigna muchos menos pedidos al replanificar.
7. **¿Qué es el Split?**
   El decodificador del AG: convierte la secuencia de pedidos del cromosoma en rutas, cortándola en tramos que caben en cada tipo de vehículo y asignando cada tramo a la mejor unidad y punto de carga.
8. **¿Qué es el criterio de aspiración?**
   Permite aceptar un movimiento tabú si produce la mejor solución encontrada hasta el momento.
9. **¿Qué pasa si el destino de un pedido está bloqueado?**
   Si está bloqueado todo el plazo, el pedido es inentregable y no cuenta como colapso. Si se bloquea antes del plazo, el planificador intenta llegar antes de que empiece el bloqueo (hora límite efectiva).
10. **¿Qué errores encontraron en el camino?**
    El AG se pasaba del tiempo asignado, el mapa se compartía entre simulaciones paralelas y el mantenimiento ignoraba el mes. Los tres se corrigieron con pruebas automáticas antes del experimento final.

---

## 13. Propuesta de estructura de diapositivas

1. **Portada:** PaqRap, Búsqueda Tabú frente al AG Híbrido + Split; Eq5E; integrantes.
2. **El problema:** MD-HVRP-TW dinámico, datos del caso (mapa, almacenes, flota, plazos) y definición de colapso.
3. **Por qué metaheurísticas y por qué estas dos:** tabla de la §2.
4. **Base común justa:** función objetivo jerárquica (H y luego S), evaluador único, replanificación cada 60 min con Ta = 2 s.
5. **Búsqueda Tabú:** tabla de la §4 (a cargo de Alcca y Arzapalo).
6. **AG Híbrido + Split:** tabla de la §5 y un esquema del Split (a cargo de Alvarado y Torres).
7. **Pregunta y criterio del experimento:** el criterio del profesor y los desempates.
8. **Datos oficiales y demanda creciente:** tabla de la §6.2, con un gráfico de % de C_max por mes.
9. **Diseño:** factores, niveles como meses, ventanas reales de 5 días, diseño pareado, 3 semillas, 618 simulaciones.
10. **Controles de justicia:** tabla de la §6.6.
11. **Análisis estadístico:** McNemar exacta, GEE, Kaplan-Meier y pruebas pareadas; potencia.
12. **Resultado principal:** `pct_colapso.png` + tabla por nivel. Mensaje: empatan.
13. **Estabilidad del veredicto:** tabla por corrida. Mensaje: la dirección cambia, así que es azar.
14. **Desempates:** tabla de la §8.2 + `caja_cambios_de_unidad.png` y `caja_costo_por_pedido.png`.
15. **Conclusión y elección: Búsqueda Tabú.** Empate en colapsos y en tiempo; gana por estabilidad (de 4 a 10 veces menos reasignaciones) con las tres razones de la §9, y lo que se cede en costo.
16. **Límites y trabajo futuro:** §10; averías, escenario hasta el colapso con datos oficiales, 2-opt y cross-exchange.
17. **Preguntas.**

---

## 14. Archivos de respaldo

| Qué | Dónde |
|---|---|
| Datos oficiales | `juego_de_datos/` |
| Resultados por corrida y combinados | `Resultados/` (ver `Resultados/LEEME.md`) |
| Detalle de cada etapa | `docs/avance_sem07.md` (etapas 23 a 29) |
| Supuestos | `docs/propuesta_cambios_IEN.md` |
| Cómo repetir el experimento | `docs/protocolo_experimento.md` y `Resultados/LEEME.md` |
| Código explicado clase por clase | `docs/guia_del_codigo.md` |
| Preguntas de defensa por módulo | `docs/modulos_por_integrante.md` |
| Preguntas pendientes para el profesor | `docs/preguntas_para_el_profesor.md` |
