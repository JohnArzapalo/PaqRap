# CLAUDE.md — PaqRap (1INF54, PUCP, Eq5E)

Contexto para Claude Code. Léelo completo antes de tocar el código. Responde y documenta **en español**.

## 1. Qué es el proyecto

Planificador y simulador de rutas de reparto (MD-HVRP-TW dinámico) del curso **1INF54 Proyecto de Diseño y Desarrollo de Software**, semestre 2026-2, equipo **Eq5E**.

- **Integrantes y algoritmo que defiende cada uno:**
  - Alcca y Arzapalo: **Búsqueda Tabú**.
  - Alvarado y Torres: **Algoritmo Genético Híbrido + Split**.
  - Los cuatro deben poder explicar ambos algoritmos, porque hay evaluación individual de programación.
- **Objetivo de esta fase:** construir, probar y comparar ambos algoritmos con **experimentación numérica y análisis estadístico** para **elegir el mejor**.
- El código debe ser **simple y explicable** en una defensa oral ante el JP. Se prefiere claridad sobre sofisticación.

## 2. Reglas del problema que no se negocian

Fuentes: enunciado, hoja de preguntas y respuestas (P&R) e indicaciones del profesor en clase.

- **Plazos:** normal de 36 h; priorizados de 4, 8, 12 o 18 h. **Hora límite = registro + plazo.** La hora de entrega en el cliente (1 h) no cuenta dentro del plazo.
- **Mapa:** retícula de 70 × 50 km, nodos cada 1 km, origen (0,0) abajo a la izquierda y calles de doble sentido.
- **Almacenes:**
  - Central en **(27,14)**, con inventario infinito.
  - Intermedios Nor-Oeste en **(12,38)** y Este en **(57,27)**, de 1 000 unidades cada uno, que se recargan a las 23:59:59.
  - Las unidades salen del central la primera vez y recargan en cualquier almacén con stock.
- **Flota:**
  - 10 autos (24 paquetes, 40 km/h, S/ 8 por km).
  - 15 motos (8 paquetes, 25 km/h, S/ 6 por km).
  - 12 bicicletas (4 paquetes, 12 km/h, S/ 3 por km).
  - Las velocidades 40/25/12 están por confirmar frente a la hoja auxiliar, que dice 20/40/14.
- **Bloqueos:** solo planificados y **conocidos de antemano** (archivo `aaaamm.bloqueadas`). Un nodo bloqueado no se atraviesa. Solo hay polígonos abiertos.
- **Averías:** tipos 1, 2 y 3. Con trasvase, otra unidad recoge la carga en 30 min. **Las unidades son almacenes móviles.**
- **Colapso logístico (definición binaria del profesor):** basta **un producto no entregado dentro de su plazo** para que la simulación termine. La definición multivariable del ISA quedó descartada.
- **Función objetivo jerárquica, igual para ambos algoritmos:**
  - Primero **H** = productos sin asignar + productos fuera de plazo.
  - Luego **S** = costo en soles, más las penalidades internas del planificador.
  - Ningún ahorro en S compensa un producto tarde.
- **Replanificar, no planificar desde cero:** se parte del plan vigente y del estado real. Se replanifica cada **Sa** (60 min) con un presupuesto **Ta** y ante eventos. En cada iteración se replanifican todas las unidades.
- **El planificador nunca se detiene por un pedido tarde**; el colapso lo detecta el **simulador**.
- **Experimento (indicación del profesor):**
  - Ambos algoritmos se comparan en **las mismas situaciones**: mismos pedidos, mismos bloqueos a las mismas horas, misma flota, mismos almacenes y mismos parámetros.
  - Incluye bloqueos y almacenes intermedios con stock.
  - **Es normal que algunas corridas colapsen.** El mejor algoritmo es el que tiene **menor porcentaje de corridas con colapso** sobre el total de corridas. Ese es el criterio principal de la comparación; el costo y la estabilidad solo desempatan.
- **Escenarios:**
  - **Día a día (DIA_A_DIA):** operación en tiempo real con el visualizador. Es la demostración del sistema, no se usa para el experimento.
  - **Simulación de 5 días (SIM_5D):** 5 días simulados en 30 a 60 min reales. Cada corrida termina en «colapsó» o «no colapsó» dentro de los 5 días.
  - **Hasta el colapso:** se simula hasta que colapsa (tope de 30 días). Toda corrida termina colapsando; lo que se mide es **cuánto dura**.
- **Las reglas dudosas las decide el profesor**, porque de él depende la nota. Mientras no responda, se usa un supuesto configurable anotado en `docs/preguntas_para_el_profesor.md`; cuando responda, rige su respuesta.

## 3. Estado del código (etapa 27)

- Java 21, Maven, NetBeans. Paquete `pe.edu.pucp.gamesoft.paqrap` (ruta `src/main/java/pe/edu/pucp/gamesoft/paqrap/`).
- Guía detallada de clases: `docs/guia_del_codigo.md`. Diagramas: `docs/diseno/`.
- **Resultado de la etapa 22 (datos sintéticos):** 42 de 42 corridas de SIM_5D sin colapso (`docs/configuracion_5_dias.md`). Es decir, 0 % frente a 0 %: con esas condiciones el % de colapsos todavía **no distingue** entre algoritmos (ver §5).

| | Búsqueda Tabú | Algoritmo Genético |
|---|---|---|
| Costo por pedido | S/ 171.7 | S/ 162.4 (5.4 % menos) |
| km por pedido | 28.3 | 25.9 |
| Cambios de unidad al replanificar (5 días) | ~36 | ~343 |

- **Cambios de configuración de la etapa 22:**
  - tolerancia de 10⁻⁶ min en el límite (SI-21);
  - `plan.holgura_min=60` y `plan.penalidad_holgura=200` (SI-20);
  - la ventana del tramo incluye la hora de alimentación (SI-22);
  - `red.destino_bloqueado=no_evaluable` (SI-15), pendiente de confirmar con el profesor (pregunta 11).
- **Etapa 23:** hora límite efectiva (SI-23). La holgura se mide hasta el inicio del bloqueo del destino que cubre la hora límite, y la urgencia de los pedidos usa esa hora. Hay 69 pruebas (`docs/avance_sem07.md`).
- **Etapa 24:** sin estado estático en los algoritmos (SI-19 superado). Cada ejecución es un objeto (`new BusquedaTabu(semilla)`, `new AlgoritmoGenetico(semilla)`), la velocidad cambiada en caliente es por simulación y `--hilos N` corre N simulaciones a la vez con resultados idénticos a correrlas en serie. Hay 70 pruebas.
- **Etapa 25:** situaciones por réplica (SI-24): la réplica r de cada nivel es una muestra de pedidos propia, la misma para TABU y AG (diseño pareado). Se agregaron `--cargas` para niveles a medida, el análisis del % de colapsos con McNemar exacta y `potencia.py` para proporciones pareadas. Calibración sintética: la zona útil está entre 100 % y 115 % de C_max (`docs/avance_sem07.md`). Hay 72 pruebas.
- **Etapa 26 (ensayo SINTETICO, 240 corridas):** niveles provisionales BAJA 95 %, MEDIA 105 % y ALTA 110 % (SI-25). % de colapsos: TABU 51.7 % frente a AG 57.5 %, **sin diferencia significativa** (McNemar global p = 0.26). Los desempates dan TABU por estabilidad (unos 13 cambios de unidad frente a unos 160); el costo por pedido no difiere y el AG hace de 1.4 a 1.6 % menos km en BAJA y MEDIA. **El AG excede Ta: 2 752 ms de media frente a 2 000** (`docs/avance_sem07.md`).
- **Etapa 27:** el AG revisa Ta antes de cada hijo (antes, solo al terminar la generación). Con 7 hilos pasó de 2 601 ms de media (máximo 3 823) a 2 050 ms (máximo 2 200); Tabú usa 2 003 ms. Hay 75 pruebas. El ensayo de la etapa 26 se hizo con el AG excedido y hay que repetirlo.
- **Supuestos** SI-01 a SI-25 y cambios al IEN: `docs/propuesta_cambios_IEN.md`.
- **Los datos de `datos/` son SINTÉTICOS.** Toda salida hecha con ellos lleva la marca «SINTETICO» y **no sirve para el informe**. Cuando lleguen los oficiales, seguir `docs/protocolo_experimento.md`.

## 4. Cómo compilar, probar y ejecutar

Todos los comandos van en la raíz del repositorio.

```bash
mvn -o test                      # todas las pruebas JUnit 5; deben pasar antes de cada commit
mvn -o -q compile
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Main          # demostración con instancias pequeñas
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --escenario SIM_5D --acelerado si --archivo datos/ventas202609_SINTETICO_MES.txt --bloqueos datos/202609_SINTETICO.bloqueadas --niveles ARCHIVO --replicas 3 --salida cinco_dias_SINTETICO.csv
```

- Los parámetros están comentados en `config/parametros.properties`.
- Experimento: `ejecutar_pc1/2/3.bat` (una PC por nivel de carga). Por defecto corren SIM_5D con situaciones por réplica, `REPLICAS=40` y `HILOS=1`; con `ESCENARIO=COLAPSO` corren hasta el colapso. Pasos completos en `docs/protocolo_experimento.md`.
- Análisis en Python: `analisis/analisis_experimento.py`, `analisis/potencia.py` y `analisis/unir_csv.py`. Requieren pandas, scipy, matplotlib, statsmodels y lifelines.
- **Varias simulaciones por proceso** (etapa 24): `--hilos N` las corre en paralelo. Con parada por evaluaciones el resultado no depende de N. Con parada por tiempo (Ta), usar N ≤ núcleos físicos, porque los hilos se reparten la CPU.

## 5. Diseño del experimento vigente

- **Factores:** algoritmo (TABU, AG) × carga. Los niveles del IEN (BAJA 30 %, MEDIA 60 %, ALTA 90 % de C_max; C_max = 1 536 paquetes/día) dan 0 % de colapsos en 5 días. **Niveles provisionales (SI-25): BAJA 95 %, MEDIA 105 %, ALTA 110 %**, calibrados con los sintéticos (pregunta 14). Con los sintéticos, la zona útil está entre 100 % y 115 % de C_max. Los niveles definitivos se fijan con los datos oficiales (`docs/protocolo_experimento.md` §4).
- **Variable principal (indicación del profesor): % de corridas con colapso.**
  - Cada corrida da un resultado sí/no. El **horizonte** es el tiempo simulado dentro del cual se mira si hubo colapso; en SIM_5D son **los 5 días**.
  - **Decisión del equipo:** se usa **SIM_5D**, porque todos trabajan con ese escenario. Falta la confirmación del profesor (pregunta 13).
  - El escenario **hasta el colapso** no sirve para el porcentaje (siempre llega al 100 %), pero da una variable complementaria: **tiempo hasta el colapso**, con censura a los 30 días (Kaplan-Meier y log-rank).
- **Situaciones por réplica (SI-24):** en cada nivel, la réplica r es una muestra de pedidos propia, y **TABU y AG corren exactamente la misma**: mismos pedidos, bloqueos, flota, almacenes y parámetros. Así el porcentaje resume muchas situaciones y el diseño es pareado.
- **Calibración de las condiciones:** para que el porcentaje compare algo, las condiciones deben producir colapsos en una parte de las corridas (idealmente entre 20 % y 80 %). Con 0 % en ambos algoritmos no se puede elegir. Si hace falta, se sube la carga o se agregan averías, **igual para ambos algoritmos**.
- **Regla de destino bloqueado (pregunta 11):** con `no_evaluable`, los pedidos imposibles por bloqueo no cuentan como colapso, lo que cambia directamente el porcentaje. Se reportan aparte y se ajusta la regla según lo que responda el profesor.
- **Variables secundarias (desempate, en este orden):**
  - tiempo hasta el colapso;
  - costo por pedido entregado (no el acumulado, que premia al que colapsa antes);
  - cambios de unidad (estabilidad).
  - También se reportan km por pedido, % en plazo y pedidos no entregables por bloqueo.
- **Reproducibilidad:**
  - Semillas del algoritmo: 1000 + r. Semilla de la situación: `semilla_base + 1000·(k+1) + r`.
  - Orden aleatorio de las corridas y hashes de cada archivo de entrada.
  - El reparto entre PCs es **por nivel, no por algoritmo**.
- **Análisis** (`analisis/analisis_experimento.py`):
  - Variable principal: % de colapsos con IC de Clopper-Pearson, **McNemar exacta** por nivel y global sobre los pares discordantes, y regresión logística **GEE** con los pares como grupos.
  - Variables secundarias: pruebas **pareadas** (t pareada o Wilcoxon) y Kaplan-Meier con log-rank.
- **Réplicas** (`analisis/potencia.py`, McNemar exacta):
  - Con los sintéticos, la discordancia es alta (psi = 0.56), así que detectar 20 puntos requiere 118 pares.
  - Eso equivale a **40 réplicas por nivel** para la decisión global con 3 niveles, o 118 por nivel para decidir en cada uno.
  - Hay que recalcularlo con los datos oficiales.

## 6. Próximo trabajo (en orden sugerido)

1. **Hora límite efectiva** (hecha en la etapa 23; potencia recalculada en la etapa 25): anticipar los bloqueos conocidos del destino. Si el destino se bloquea antes de la hora límite y el bloqueo dura más allá de ella, hay que entregar antes de que empiece. Los bloqueos pueden durar más que el plazo del pedido; para esos casos está la replanificación. Debe hacerse en ambos algoritmos, en el evaluador común `Compartido.evaluarRuta`, sin lógica duplicada. Después, recalcular la potencia y el número de réplicas **para proporciones pareadas** (§5).
2. **Quitar el estado estático** (hecho en la etapa 24) de los algoritmos, para correr varias simulaciones en paralelo y los 3 escenarios en un servidor. Es necesario porque el % de colapsos exige muchas réplicas.
3. **Calibrar las condiciones de SIM_5D** (herramientas y calibración sintética hechas en la etapa 25; falta repetirla con los datos oficiales y fijar los niveles, pregunta 14) para que haya colapsos en una parte de las corridas (§5), y actualizar `docs/protocolo_experimento.md` y el análisis en Python con la variable principal nueva.
4. **Corregir el exceso de tiempo del AG** (hecho en la etapa 27): el AG revisa Ta solo al terminar cada generación y usa 2.75 s de media con Ta = 2 s. Debe revisar el reloj también dentro de la generación (antes de evaluar cada hijo) y agregar una prueba que verifique que el tiempo por llamada no supera Ta más un margen pequeño. Coordinar con Torres, porque cambia código que defiende.
5. **Experimento comparativo completo** con réplicas suficientes, CSV unidos, análisis estadístico y una **conclusión sobre qué algoritmo elegir**: gana el de **menor % de colapsos**. Si no hay diferencia significativa, desempatan el tiempo hasta el colapso, el costo y la estabilidad.
6. Verificar **2-opt y cross-exchange** frente al §4.2 del ISA, porque están marcados como provisionales.
7. `inyectarPedido` para el escenario DIA_A_DIA, cuando el equipo defina el formato.

## 7. Convenciones de trabajo

- **Rama de trabajo:** `feature/JL` (Alvarado). No hacer commit en `main` ni forzar pushes.
- **Commits:**
  - Mensajes en español con el formato `Etapa N: <qué se hizo>`.
  - Commit y push solo cuando el usuario lo pida.
- **Pruebas:** cada cambio de comportamiento lleva su prueba JUnit, y todas deben pasar con `mvn -o test`.
- **Evaluador único:**
  - Toda regla que afecte la factibilidad o el costo va en `Compartido` o `Contexto`.
  - Nunca en un solo algoritmo, porque la comparación debe ser justa.
  - Los mismos parámetros valen para ambos algoritmos.
- **Documentación:**
  - Cada etapa actualiza `docs/avance_sem0X.md`.
  - Si cambia un supuesto, se registra como SI-nn en `docs/propuesta_cambios_IEN.md`.
  - Si cambia una clase, se actualiza `docs/guia_del_codigo.md`.
- **Resultados:**
  - Tablas con antes y después, y la ruta del CSV de origen.
  - Marcar siempre «SINTETICO» cuando corresponda.
- **Dudas del enunciado:** no inventar. Anotarlas en `docs/preguntas_para_el_profesor.md` y elegir un supuesto explícito y configurable.
