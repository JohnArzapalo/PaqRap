# Evaluación del software: estado de los 126 ítems tras la etapa 35

Formulario `__c1inf54.98#.Eq5E.Eval.Sw`, hoja `91.EvaSw`. Esta tabla **no reemplaza el Excel**: el equipo hace el registro en la próxima revisión.

- **Estado 21-oct:** lo que anotó el equipo en la revisión del 21-oct-2026.
- **Esperado ahora:** Conforme, Parcial o No aplica en la práctica. En las preguntas de la sección A se mantiene la escala de la pregunta (Sí o No aplica).
- **Evidencia:**
  - el archivo y la función, la prueba JUnit o la captura;
  - las capturas están en `salidas/capturas_etapa35/` (local, no versionada);
  - las funciones del visualizador están en `web/index.html`.
- Rama `feature/etapa35-eval-sw`; detalle en `docs/avance_sem07.md` (etapa 35).

**Resumen:**

| Estado esperado | Ítems |
|---|---|
| Conforme (o Sí) | 104 |
| Parcial | 1 (A16, depende del equipo) |
| No aplica (en la práctica) | 21 (13 preguntas de la sección A, G05, G19 y la percepción del docente) |

## A. General

| Ítem | Estado 21-oct | Esperado ahora | Evidencia o motivo |
|---|---|---|---|
| A01 | Conforme | Conforme | Etapa 35, rama `feature/etapa35-eval-sw` |
| A02 | Conforme | Conforme | La máquina virtual sigue con la versión del 21-oct (etapa 34). La etapa 35 se despliega cuando el equipo fusione la rama (`docs/despliegue_aws.md` §2) |
| A03 | No aplica | No aplica | Pregunta al equipo. Los problemas anotados (avería por tipo, lista de envíos, envíos por almacén, orden y filtros de la flota) se resolvieron en las tareas 1 y 3 |
| A04 a A09 | No aplica | No aplica | No aplican en el avance |
| A10 | No aplica | No aplica | Pregunta al equipo: Búsqueda Tabú (SI-27) |
| A11 | No aplica | No aplica | Pregunta al equipo. Respuesta actualizada: SIM_5D, COLAPSO y operación día a día completos y verificados en corridas reales (etapa 35) |
| A12 a A15 | No aplica | No aplica | Preguntas al equipo (integrantes y tecnologías) |
| A16 | Parcial | **Parcial** | No es código: el equipo debe indicar con nombre y apellido quién revisó el video |
| A17 | Sí | Sí | Ta = 7,5 s en el plan adelantado y 2 s en una excepción (`bloque.fraccion_ta`, `simulacion.ta_ms`) |
| A18 | Sí | Sí | Corrección: Sa = 1 h simulada = **30 s reales a ×120** (título del diagrama A22) |
| A19 | Sí | Sí | Sc = 1 h simulada de pedidos, leídos hasta el corte |
| A20, A21 | Sí | Sí | http://1inf54-982-5e.inf.pucp.edu.pe/ (front y back en el mismo servicio) |
| A22 | Parcial | Conforme | `docs/diseno/diagramas_PaqRap.pdf`, página 1 (corregido), y lectura incremental de los pedidos (SI-33: `FuentePedidos`, `LectorVentasIncremental`, `LecturaIncrementalTest`) |
| A23 | Parcial | Conforme | `docs/diseno/diagramas_PaqRap.pdf`, página 2 (bloques, historial e independencia entre navegadores) |

## B. Configuración

| Ítem | Estado 21-oct | Esperado ahora | Evidencia o motivo |
|---|---|---|---|
| B01 | Sí | Sí | Retícula de 70 × 50 km, 1 km por lado |
| B04 | Conforme | Conforme | Cantidad por tipo en el formulario de inicio; velocidad en caliente (`POST …/velocidades`) |
| B05 | Conforme | Conforme | Archivos oficiales de bloqueos (`juego_de_datos/`) |
| B07 a B09 | Conforme | Conforme | Averías de tipos 1, 2 y 3 (`Averia`, `AveriaTest`) |
| B13 | No aplica | Conforme | Archivos del profesor leídos por partes (SI-33); en el día a día, registro individual y **carga masiva** en su fecha y hora (`POST …/pedidos/lote`, `DiaADiaTest`, captura `t6_01`) |
| B14 | Sí | Sí | 10/11/2026 y 16/09/2026 (5 días), fecha actual (día a día) y, para el colapso, **01/06/2027**: desde el 01/01/2027 no colapsa en 30 días (los pedidos de enero terminan el día 20) |
| B15 | Sí | Sí | Se leen todas las líneas, sin filtros; la lectura incremental solo cambia **cuándo** (SI-33) |
| B16 | Conforme | Conforme | Flota, almacenes, bloqueos y mantenimiento se cargan igual en los tres escenarios |

## C. Mapa base

| Ítem | Estado 21-oct | Esperado ahora | Evidencia o motivo |
|---|---|---|---|
| C02 | Conforme | Conforme | Fecha (dd/mm/aaaa) y hora (hh:mm) con campos propios (`leerInicio`), captura `t1_02` |
| C03, C04 | Conforme | Conforme | Sin cambios |
| C05 | Conforme | Conforme | Varios dispositivos sobre la misma corrida |
| C06 | Parcial | Conforme | Dos navegadores: selección, filtros y orden son locales, y la avería se ve en ambos (capturas `t4_06`, `t4_07`) |
| C07 | Conforme | Conforme | Todo en español, también los títulos del zoom |
| C08 a C11 | Conforme | Conforme | Sin cambios |
| C12 | Parcial | Conforme | «Fecha y hora simulada (día, hora y minuto)» en los tres escenarios (`pintarSimulacion`), capturas `t1_09`, `t1_11` |
| C13 | Conforme | Conforme | Tiempo simulado transcurrido, d días hh h mm min |
| C14 | Parcial | Conforme | Fecha **y** hora real al minuto |
| C15 | Parcial | Conforme | Tiempo real transcurrido al minuto |
| C16 | Parcial | Conforme | Los cuatro tiempos juntos, con su rótulo, en la sección Simulación |
| C17 a C20 | Conforme | Conforme | Sin cambios |
| C21 | Conforme | Conforme | Semáforo único con vacío, en el sentido que pidió la JP (SI-31: `semLlenado`) |
| C22, C23 | Conforme | Conforme | Sin cambios |
| C24 | Conforme | Conforme | Íconos SVG de 26 px |
| C25, C26 | Parcial | Conforme | Dibujo de auto, moto o bicicleta (`GLIFO`, `iconUni`), captura `t1_03` |
| C27 | Parcial | Conforme | Color del ícono por semáforo de llenado más vacío (SI-31) |
| C28 | Conforme | Conforme | Carga / capacidad al pasar el mouse, en la ficha y en la fila |
| C29 | Parcial | Conforme | Reproducción de las trayectorias del bloque y reloj con pausa (SI-32: `cargarTrayectorias`, `reproducir`, `TrayectoriasTest`). Medido: de unos 60 saltos en 20 s a ninguno, también con avería |
| C30 | Nada/Poco | Conforme | El ícono gira hacia donde avanza y lleva una flecha de dirección (`orientar`) |
| C31, C33 | Conforme | Conforme | Sin cambios |
| C34 | Parcial | Conforme | Lo recorrido desde la última parada se ve punteado; botón de rutas: pendientes, recorridas u ocultas (`acumularRastro`, `MODOS_RUTAS`), captura `t1_07` |

## D. Circunstancias

| Ítem | Estado 21-oct | Esperado ahora | Evidencia o motivo |
|---|---|---|---|
| D01 a D04 | Conforme | Conforme | Sin cambios |
| D10 | Parcial | Conforme | Un color por tipo de avería (violeta, fucsia y cian) y la marca con el número, captura `t1_05_averias_tres_tipos` |
| D11 | Nada/Poco | Conforme | Colores distintos de los del semáforo de llenado (`--av1` a `--av3`) |
| D12 | Parcial | Conforme | Halo y marca sobre el fondo oscuro |
| D13 | Conforme | Conforme | La instantánea trae hasta cuándo dura cada avería (`IntegracionTest.instantaneaTraeTipoYDuracionDeLaAveria`) |

## E. Panel

| Ítem | Estado 21-oct | Esperado ahora | Evidencia o motivo |
|---|---|---|---|
| E01 | Conforme | Conforme | Secciones desplegables (Simulación, Resumen, Flota, Almacenes, Envíos y Bitácora) |
| E02 | Parcial | Conforme | Carga en número en cada fila (`filaUnidad`) |
| E03 | Nada/Poco | Conforme | Semáforo invertido (pedido de la JP) más vacío (SI-31) |
| E04, E05 | Conforme | Conforme | Ficha de la unidad: envíos y productos a bordo, enlazados a su envío |
| E06, E07 | Nada/Poco | Conforme | Búsqueda por minutos al destino o al origen, distinguiendo llegada y salida (`metricasUnidad`, `pasaFiltroUnidad`; `PanelTest`) |
| E08, E09 | Nada/Poco | Conforme | Igual, en km |
| E10 | Conforme | Conforme | Filtro por tipo (`#flTipo`), además del agrupamiento |
| E11 | Nada/Poco | Conforme | Filtro por origen = almacén de la última recarga (`#flOrigen`) |
| E12 | Nada/Poco | Conforme | Filtro por semáforo con vacío (`#flSem`) |
| E13 a E17 | Nada/Poco | Conforme | Orden por ocupación, salida, llegada, distancia por recorrer y recorrida, ascendente o descendente (`pintarFlota`), capturas `t3_01`, `t3_02` |
| E18 | Conforme | Conforme | Sección Almacenes: stock / capacidad y % |
| E19 | Parcial | Conforme | Semáforo con vacío |
| E20 a E22 | Nada/Poco | Conforme | Por almacén: en camino, por salir en X h y entregados en las últimas X h (`pintarAlmacenes`), captura `t3_04` |
| E31 | Nada/Poco | Conforme | Lista de envíos con todas sus partes: unidad, ruta, productos, salida, llegada y hora límite (`pintarEnvios`), captura `t3_05` |
| E32, E33 | Nada/Poco | Conforme | Filtros de entregados en las últimas X h y por entregar en las siguientes X h (X = 4 por defecto), captura `t3_06` |

## F. Vinculación mapa-panel

| Ítem | Estado 21-oct | Esperado ahora | Evidencia o motivo |
|---|---|---|---|
| F02, F04 | sin llenar | Conforme | Ruta anterior a pedido, con escalas (registro, carga, avería, trasvase, entrega) y tramos (`GET …/historial`, `VinculacionTest`), captura `t4_02` |
| F03 | sin llenar | Conforme | Buscador por envío, parte o producto: muestra todas sus rutas y las enfoca (`buscarEnCorrida`, `seleccionarEnvio`), captura `t4_01` |
| F05, F06 | sin llenar | Conforme | Tarjeta del almacén → mapa, y su ícono → tarjeta (`enfocarAlmacen`), captura `t4_04` |
| F07, F08 | sin llenar | Conforme | Fila de la unidad → mapa, y su ícono → ficha y fila en Flota (`unidadDesdeMapa`), captura `t4_05` |
| F09 | sin llenar | Conforme | Una parte del envío enfoca su unidad, o el destino si no tiene, captura `t4_03` |
| F15, F16 | sin llenar | Conforme | Filtros de semáforo con vacío de almacenes y unidades, reflejados en el mapa, captura `t3_03` |
| F17, F18 | sin llenar | Conforme | Los demás filtros de almacenes (tipo) y unidades (tipo, origen, estado, distancia) también atenúan el mapa |

## G. Cierre

| Ítem | Estado 21-oct | Esperado ahora | Evidencia o motivo |
|---|---|---|---|
| G01, G02 | sin llenar | Conforme | Indicador «Ocupación de la flota» (suma de cargas / suma de capacidades de la flota operativa) con semáforo (`indicadoresGlobales`, `pintarHud`), captura `t7_01` |
| G03, G04 | sin llenar | Conforme | Indicador «Llenado de almacenes» (intermedios en conjunto) con semáforo, captura `t7_01` |
| G05 | sin llenar | No aplica en la práctica | Es del dominio aéreo. Su equivalente en PaqRap, la hora de acondicionamiento en el cliente, ya se cumple (`entrega.horas = 1`, `Compartido.HORAS_ENTREGA`) |
| G06 | sin llenar | Conforme | Dos navegadores independientes sobre la misma SIM_5D (capturas `t4_06`, `t4_07`) |
| G08 | sin llenar | Conforme | Reporte con la última planificación estable al terminar la SIM_5D (`tablaUltimoPlan`, `Simulador.reporteJson`). Verificado desde el 10/11/2026: 22 unidades con ruta y 179 paradas, captura `t7_03` |
| G09 | sin llenar | Conforme | «Cerrar operaciones» en el día a día y el mismo reporte (`DiaADiaTest`), capturas `t6_03` y `t7_06` |
| G10 | sin llenar | Conforme | El mismo reporte al llegar al colapso. Verificado desde el 01/06/2027: colapsó a las 26.4 h, con el plan de las 02:00 (32 unidades y 262 paradas), captura `t7_04` |
| G11 a G14, G17, G18 | sin llenar | No aplica en la práctica | Percepción del docente: no requieren código |
| G19 | sin llenar | No aplica | No aplica en el avance |
