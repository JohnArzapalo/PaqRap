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

## 3. Estado del código (etapa 23)

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
- **Supuestos** SI-01 a SI-23 y cambios al IEN: `docs/propuesta_cambios_IEN.md`.
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
- Experimento hasta el colapso: `ejecutar_pc1/2/3.bat` (una PC por nivel de carga).
- Análisis en Python: `analisis/analisis_experimento.py`, `analisis/potencia.py` y `analisis/unir_csv.py`. Requieren pandas, scipy, matplotlib, statsmodels y lifelines.
- **Una simulación por JVM** (SI-19): los algoritmos tienen estado estático, así que no se deben correr dos simulaciones en el mismo proceso.

## 5. Diseño del experimento vigente

- **Factores:** algoritmo (TABU, AG) × carga (BAJA 30 %, MEDIA 60 %, ALTA 90 % de C_max; C_max = 1 536 paquetes/día). Los niveles pueden recalibrarse (ver el punto sobre calibración).
- **Variable principal (indicación del profesor): % de corridas con colapso.**
  - Cada corrida da un resultado sí/no. El **horizonte** es el tiempo simulado dentro del cual se mira si hubo colapso; en SIM_5D son **los 5 días**.
  - Supuesto provisional: la variable principal es el % de corridas de **SIM_5D** que colapsan dentro de los 5 días. Pendiente de confirmar con el profesor (pregunta 13).
  - El escenario **hasta el colapso** no sirve para el porcentaje (siempre llega al 100 %), pero da una variable complementaria: **tiempo hasta el colapso**, con censura a los 30 días (Kaplan-Meier y log-rank).
- **Calibración de las condiciones:** para que el porcentaje compare algo, las condiciones deben producir colapsos en una parte de las corridas (idealmente entre 20 % y 80 %). Con 0 % en ambos algoritmos no se puede elegir. Si hace falta, se sube la carga o se agregan averías, **igual para ambos algoritmos**.
- **Regla de destino bloqueado (pregunta 11):** con `no_evaluable`, los pedidos imposibles por bloqueo no cuentan como colapso, lo que cambia directamente el porcentaje. Se reportan aparte y se ajusta la regla según lo que responda el profesor.
- **Variables secundarias (desempate):**
  - tiempo hasta el colapso (escenario hasta el colapso);
  - costo por pedido y km por pedido;
  - cambios de unidad (estabilidad);
  - % en plazo;
  - pedidos no entregables por bloqueo.
- **Reproducibilidad:** semillas 1000 + r, orden aleatorio de corridas y el mismo archivo de pedidos y bloqueos por nivel. El reparto entre PCs es **por nivel, no por algoritmo**.
- **Análisis:**
  - Variable principal: diseño **pareado**, porque TABU y AG corren exactamente las mismas situaciones (misma réplica = misma situación). Se usa la prueba de **McNemar exacta** por nivel de carga y la **regresión logística** con algoritmo × carga. Se reportan los intervalos de confianza de cada porcentaje.
  - Potencia: las proporciones requieren **muchas más réplicas** que una variable continua (orden de decenas por nivel). El número de réplicas se justifica con `analisis/potencia.py`, adaptado a proporciones pareadas.
  - Variables secundarias: descriptiva; Shapiro-Wilk y Levene; t de Welch o Mann-Whitney; ANOVA de dos factores o ART; Kaplan-Meier y log-rank para el tiempo hasta el colapso.

## 6. Próximo trabajo (en orden sugerido)

1. **Hora límite efectiva** (código hecho en la etapa 23; falta verificar SIM_5D y recalcular la potencia): anticipar los bloqueos conocidos del destino. Si el destino se bloquea antes de la hora límite y el bloqueo dura más allá de ella, hay que entregar antes de que empiece. Los bloqueos pueden durar más que el plazo del pedido; para esos casos está la replanificación. Debe hacerse en ambos algoritmos, en el evaluador común `Compartido.evaluarRuta`, sin lógica duplicada. Después, recalcular la potencia y el número de réplicas **para proporciones pareadas** (§5).
2. **Quitar el estado estático** de los algoritmos, para correr varias simulaciones en paralelo y los 3 escenarios en un servidor. Es necesario porque el % de colapsos exige muchas réplicas.
3. **Calibrar las condiciones de SIM_5D** para que haya colapsos en una parte de las corridas (§5), y actualizar `docs/protocolo_experimento.md` y el análisis en Python con la variable principal nueva.
4. **Experimento comparativo completo** con réplicas suficientes, CSV unidos, análisis estadístico y una **conclusión sobre qué algoritmo elegir**: gana el de **menor % de colapsos**. Si no hay diferencia significativa, desempatan el tiempo hasta el colapso, el costo y la estabilidad.
5. Verificar **2-opt y cross-exchange** frente al §4.2 del ISA, porque están marcados como provisionales.
6. `inyectarPedido` para el escenario DIA_A_DIA, cuando el equipo defina el formato.

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
