# Diseño del simulador mínimo (Etapa 4)

> **Actualización (etapas 8 a 15):** el simulador se reescribió como simulación de eventos discretos con bloqueos, almacenes intermedios, replanificación con estado de todas las unidades, averías y trasvase, mantenimiento, escenarios y registro de eventos. La descripción vigente está en el comentario de la clase `Simulador` y en `docs/avance_sem07.md`; lo que sigue es el diseño original de la versión mínima (etapa 5).
>
> **Estado de la versión mínima: IMPLEMENTADO, con la confirmación del equipo.** Clases `Simulador`, `Planificador`, `CapacidadFlota`, `GeneradorCarga` y `ExperimentoSimulacion`; pruebas en `SimuladorTest`. Se usa con `Experimento --modo simulacion`.
> Objetivo: medir la variable principal del IEN (tiempo hasta el colapso) reutilizando sin cambios los dos algoritmos actuales.
>
> **Diferencias entre este diseño y lo implementado:**
> - La interfaz `Planificador.planificar` no recibe el instante absoluto: no hacía falta, porque los pedidos ya llegan con horas relativas a ese instante.
> - El colapso se define sobre el pedido **original** (todas sus entregas parciales), según el criterio acordado.
> - Métricas agregadas: `pedido_colapso`, `pedidos_evaluables` y `aplazamientos` (entregas que un plan dejó sin asignar).
> - Semilla por llamada: `semillaReplica × 1 000 003 + nroCiclo`.
> - Niveles de carga, C_max y reparto entre PCs: ver `docs/capacidad_y_carga.md`.
> - **Mejora pendiente (P16 del profesor):** replanificar en cada iteración también las unidades en ruta (sus paradas no visitadas). Hoy una ruta despachada no cambia.

## 1. Alcance

| Incluido (mínimo) | Excluido por ahora (igual que en el IEN) |
|---|---|
| Reloj simulado y llegada de pedidos según su hora de registro | Bloqueos (se agregan cambiando `Compartido.distancia` por BFS) |
| Replanificación cada Sa con presupuesto Ta | Varios almacenes, stock y recargas |
| Estado de cada vehículo y **varios viajes** (vuelve al central y queda disponible) | Turnos y alimentación, averías, mantenimiento |
| Detección de colapso con el criterio operativo (primer pedido vencido sin entregar) | Cancelaciones, cambio de velocidad en caliente |
| Registro de tiempo hasta el colapso, costo acumulado y % en plazo | Integración con el visualizador web |

## 2. Idea central

Hoy los algoritmos resuelven una instancia en la que **todas las unidades salen del central en la hora 0** y los pedidos tienen horas relativas a ese instante (`LectorPedidos`: registro negativo, límite = registro + hl). El simulador conserva esa convención: en cada replanificación, en el instante *t*:

1. convierte los pedidos pendientes a horas relativas a *t* (registro − *t*);
2. pasa como flota solo las unidades **disponibles en el central** en *t*;
3. ejecuta el algoritmo (sin cambios) y **compromete** las rutas devueltas.

Los varios viajes aparecen solos: una unidad que termina su ruta vuelve al central y participa en la siguiente replanificación. Así desaparece el tope artificial de 408 paquetes (supuesto SI-02).

## 3. Componentes

```
Experimento ──► Simulador.simular(corrida)
                   │
                   ├─ RelojSimulado        t (horas), paso Sa, horizonte (5 días = 120 h)
                   ├─ FuentePedidos        pedidos del archivo ordenados por hora de llegada
                   ├─ EstadoFlota          EstadoVehiculo por unidad
                   ├─ Planificador         interfaz; adaptadores PlanificadorTabu / PlanificadorAG
                   ├─ DetectorColapso      criterio operativo
                   └─ RegistroMetricas     costo acumulado, % en plazo, tiempos por ciclo
```

### 3.1 EstadoVehiculo

| Campo | Significado |
|---|---|
| `unidad` | `UnidadTransporte` |
| `estado` | `DISPONIBLE` (en el central) o `EN_RUTA` |
| `rutaEnCurso` | `RutaAlg` comprometida (null si está disponible) |
| `horaSalida`, `horaFin` | horas absolutas de salida y de regreso al central |
| `entregas` | lista de (pedido, hora absoluta de llegada) de la ruta en curso |
| `posicion(t)` | calculada: interpola la ruta Manhattan según *t* (solo para el visualizador y los reportes) |

### 3.2 Ciclo principal (pseudocódigo)

```
t = inicio; pendientes = {}; colapso = null
mientras t < fin_horizonte y colapso == null:
    pendientes += FuentePedidos.llegadosHasta(t)
    EstadoFlota.avanzar(t)                         # marca entregas hechas; unidades con horaFin <= t -> DISPONIBLE
    colapso = DetectorColapso.revisar(t, pendientes, EstadoFlota)
    si colapso: salir
    disponibles = EstadoFlota.disponibles()
    si pendientes no vacío y disponibles no vacío:
        plan = planificador.planificar(pendientes, disponibles, t, Ta, semillaCiclo)
        comprometer(plan.rutas, t)                 # EN_RUTA; entregas con hora absoluta
        pendientes -= pedidos de plan.rutas        # plan.sinAsignar sigue pendiente
        RegistroMetricas.ciclo(plan, tiempo_real_ms)
    t += Sa
# al terminar: colapso (tiempo hasta el colapso) o censurado (llegó al horizonte sin colapsar)
```

**Política de compromiso (mínima):** una ruta comprometida no se replanifica; los pedidos no asignados vuelven a la cola. Una versión posterior puede replanificar las paradas aún no visitadas (reasignación dinámica).

### 3.3 DetectorColapso (criterio operativo, propuesta C-02)

El colapso ocurre en el primer instante en que la hora límite de un pedido pasa sin que haya sido entregado. En cada ciclo se revisa:

1. **Pedido pendiente vencido:** algún pedido pendiente (sin asignar) con `horaLimite ≤ t` → colapso en `min(horaLimite)`.
2. **Entrega comprometida tarde:** alguna entrega comprometida cuya llegada > `horaLimite` → colapso en `horaLimite` de esa entrega. Se detecta al comprometer, porque con la política mínima la ruta no cambia.

El tiempo hasta el colapso es el **mínimo** de esos instantes. Como el paso es Sa, se registra el instante exacto (la hora límite), no el del ciclo. Con entregas parciales, basta que una parte incumpla.

### 3.4 Semillas y reproducibilidad

- `semillaCiclo = semillaReplica * 1_000_003 + nroCiclo`. Se fija con `setSemilla` antes de cada llamada.
- **Parada por Ta** (principal) o **por evaluaciones** (`maxEvaluaciones`, reproducible), igual que en el experimento estático.

## 4. Interfaz que deben cumplir ambos algoritmos

```java
/** Lo que el simulador necesita de un algoritmo de planificación. */
interface Planificador {
    /**
     * @param pendientes   pedidos aún no asignados, con horaRegistro RELATIVA al instante
     *                     (registro_absoluto - instante), igual que LectorPedidos
     * @param disponibles  unidades en el central en este instante
     * @param instante     hora absoluta de la simulación (para registro y trazas)
     * @param presupuestoMs Ta (ms); ignorado si maxEvaluaciones > 0
     * @param maxEvaluaciones 0 = parada por tiempo
     * @param semilla      semilla del ciclo
     * @return plan: rutas (horas relativas al instante) y pedidos sin asignar
     */
    Plan planificar(List<Pedido> pendientes, List<UnidadTransporte> disponibles, double instante,
                    long presupuestoMs, long maxEvaluaciones, long semilla);
}

class Plan {
    List<RutaAlg> rutas;          // solo rutas no vacías
    List<Pedido> sinAsignar;
    long iteraciones, evaluaciones, tiempoMs;
}
```

Adaptadores (sin tocar los algoritmos):

| Adaptador | Implementación |
|---|---|
| `PlanificadorTabu` | `BusquedaTabu.setSemilla(semilla); BusquedaTabu.ejecutarDesdeCero(pendientes, disponibles, Ta, maxEval, duracion, Integer.MAX_VALUE)` |
| `PlanificadorAG` | `AlgoritmoGenetico.setSemilla(semilla); AlgoritmoGenetico.ejecutar(pendientes, disponibles, Ta, maxEval, 30)` |

**Mejora posible:** si el plan de un ciclo deja pedidos sin asignar que tienen holgura, conviene no comprometerlos (esperar a que vuelva una unidad). La política queda en el simulador, no en el algoritmo.

## 5. Métricas y columnas nuevas del CSV

| Columna | Definición |
|---|---|
| `tiempo_hasta_colapso_h` | Horas simuladas desde el inicio hasta el colapso (variable principal) |
| `censurado` | 1 si se llegó al horizonte sin colapso (el tiempo real de colapso es mayor que el horizonte) |
| `costo_acumulado` | Σ S de las rutas comprometidas |
| `pct_pedidos_en_plazo` | Sobre pedidos originales llegados hasta el colapso o hasta el horizonte |
| `pedidos_llegados`, `pedidos_entregados` | Conteos |
| `ciclos` | Número de replanificaciones |
| `ta_medio_ms`, `ta_max_ms` | Tiempo real por replanificación (variable secundaria "tiempo por iteración") |
| `iteraciones_totales`, `evaluaciones_totales` | Suma de todos los ciclos |
| `aplazamientos` | Veces que un pedido quedó sin asignar y volvió a la cola (indicador de estabilidad) |

**Análisis:** si hay corridas censuradas, el ANOVA sobre el tiempo hasta el colapso está sesgado. En ese caso conviene un análisis de supervivencia (Kaplan-Meier con log-rank por algoritmo) o reportar la proporción de censuradas. Esto debe declararse en el IEN.

## 6. Compresión de tiempo (5 días en 30-60 min reales)

Horizonte: 120 h simuladas. Tiempo real ≈ ciclos × Ta (la simulación entre ciclos es despreciable).

| Sa (min simulados) | Ciclos | Ta máximo para 30 min reales | Ta máximo para 60 min reales |
|---|---|---|---|
| 15 | 480 | 3.75 s | 7.5 s |
| 30 | 240 | 7.5 s | 15 s |
| 60 | 120 | 15 s | 30 s |

Con el diseño factorial del IEN (30 corridas), el cómputo es de 15-30 h: usar `--instancias` y `--algoritmos` para repartir entre PCs.

## 7. Cómo encaja en `Experimento`

1. `Experimento.ejecutarCorrida(c, flota)` pasa a llamar a `Simulador.simular(c.inst.pedidos, flota, planificador(c.algoritmo), parámetros)` y devuelve un `ResultadoSimulacion`.
2. `Instancia` deja de ser una ventana y pasa a ser un **conjunto de pedidos para un nivel de carga** (30 / 60 / 90-100 %) sobre el horizonte. Los filtros `--instancias` y `--algoritmos`, las semillas, el orden aleatorio y la salida CSV se reutilizan.
3. Se agregan las columnas de la sección 5 y se actualiza `analisis_experimento.py` (variable principal y censura).

## 8. Estimación de esfuerzo

| # | Componente | Horas-persona | Responsable sugerido |
|---|---|---|---|
| 1 | `EstadoVehiculo` y `EstadoFlota` (avance, disponibilidad, entregas hechas) | 3 | Alvarado |
| 2 | `RelojSimulado`, `FuentePedidos` (horizonte de varios días, horas absolutas) | 2 | Torres |
| 3 | Interfaz `Planificador` y adaptadores Tabú y AG | 2 | Arzapalo + Alvarado |
| 4 | Ciclo principal y política de compromiso | 3 | Alvarado + Torres |
| 5 | `DetectorColapso` y pruebas con casos a mano | 2 | Alcca |
| 6 | Métricas, CSV y adaptación de `Experimento.ejecutarCorrida` | 2 | Arzapalo |
| 7 | Generador de niveles de carga (30/60/90 %) — **requiere el archivo oficial** y la definición de capacidad teórica | 3-4 | Torres |
| 8 | Análisis con censura en `analisis_experimento.py` | 2 | Alcca |
| 9 | Pruebas JUnit (colapso, varios viajes, reproducibilidad por evaluaciones) | 3 | Todos |
| | **Total** | **22-23 h** | |

## 9. Riesgos del diseño

| Riesgo | Mitigación |
|---|---|
| Con la política mínima de compromiso no hay reasignación dinámica (el IEN la promete) | Declararlo; la reasignación de paradas no visitadas es la siguiente iteración |
| El tiempo real total excede la ventana de 30-60 min | Ajustar Sa y Ta con la tabla de la sección 6 |
| Corridas censuradas si la carga es baja | Análisis de supervivencia o proporción de censuradas |
| La "capacidad teórica" para los niveles de carga no está definida con datos oficiales | Pedir el archivo oficial y la fuente del dato de 1 536 paquetes/día antes de calibrar |
