# Lectura de pedidos por bloques (A22, etapa 35): propuesta

**Estado:** aprobada por el equipo tal cual e **implementada en la etapa 35** (SI-33; pruebas en `LecturaIncrementalTest`).

## 1. Qué pide el profesor

- A22: «No confundir. Debe ser cómo el algoritmo consume y resuelve por bloques. **No se debe cargar todo en memoria.**»
- B15: «Cada equipo puede cambiar, en alguna medida, la estructura, pero en esencia no la cantidad de datos.»

## 2. Cómo es hoy

- `CorridaWeb` llama a `DatosOficiales.cargar`, que lee **todos** los pedidos de los meses del horizonte (hasta 30 días, unas 5 000 líneas por mes) y se los pasa completos a `Simulador.crear`.
- El simulador los guarda en `porLlegar`, ordenados por hora de registro, y avanza un índice (`siguiente`). En el constructor ya los recorre todos para:
  - las llegadas urgentes (`llegadasUrgentes`, SI-29);
  - el total de pedidos originales (`Resultado.pedidosOriginalesTotal`).
- El experimento (`ExperimentoSimulacion`) hace lo mismo con listas que arma él (ventanas reales o muestras por nivel).

## 3. Propuesta

### 3.1 Una «fuente de pedidos» en lugar de la lista

Interfaz nueva `FuentePedidos`, con dos implementaciones:

| Implementación | Quién la usa | Qué hace |
|---|---|---|
| `FuentePedidos.deLista(lista)` | `ExperimentoSimulacion`, las pruebas y `Main` | Entrega la lista que ya tienen, en orden. **El experimento no cambia de datos ni de orden.** |
| `LectorVentasIncremental` | `CorridaWeb` (visualizador) | Abre el archivo `ventas.aaaamm.txt` del mes de inicio con un `BufferedReader` y lee **una línea por vez, solo cuando hace falta**. Al terminar el mes, abre el del mes siguiente (con el mismo desplazamiento de tiempo y el prefijo de id que usa hoy `DatosOficiales`). |

Métodos (los dos de la interfaz):

- `List<Pedido> leerHasta(double minuto)`: devuelve los pedidos registrados hasta `minuto` que todavía no entregó. El lector guarda **una sola línea leída por adelantado**, para saber cuándo llega el siguiente pedido sin leer más.
- `int originalesLeidos()`: cuántos pedidos originales entregó hasta ahora (para `pedidosOriginalesTotal`).

El archivo del mes está ordenado por hora de registro (se verifica al leer; si una línea viene fuera de orden, se avisa, como hoy con las líneas inválidas).

### 3.2 Cuánto lee el simulador y cuándo

- En cada vuelta de `Simulador.correr`, antes de calcular el próximo instante, se pide a la fuente lo registrado **hasta el inicio del bloque siguiente** (con bloques) o hasta la próxima replanificación periódica (sin bloques). Es el horizonte que necesita el simulador para:
  - saber si antes de ese instante llega un pedido urgente (SI-29), que replanifica al llegar;
  - proyectar el estado en la copia del bloque (`avanzarProyeccion` mira las llegadas urgentes hasta el inicio del bloque).
- Así, **en memoria solo hay los pedidos de, como mucho, 1 h simulada por delante** (unos 10 a 20 pedidos con los datos de 2026-11), en lugar de los 5 000 a 10 000 de los meses del horizonte.
- Las llegadas urgentes y el total de originales se calculan **a medida que se leen** los pedidos, no en el constructor.
- `cerrarMetricas` deja de recorrer la lista completa: toma la hora de registro y el límite de cada pedido de las entregas registradas (`entregas`), que es lo único que usa.
- La copia que proyecta un bloque (constructor de copia) no lee de la fuente: solo necesita lo ya leído hasta el corte.
- Los pedidos registrados desde el visualizador (`inyectarPedido`) se siguen sumando a lo leído, en su lugar por hora de registro.

**Lo que sí queda en memoria:** los pedidos ya registrados (pendientes, en ruta y entregados), porque son el estado de la operación y de ellos salen las métricas (% en plazo, reporte). No es «cargar todo»: es lo que ya pasó.

### 3.3 Bloqueos y mantenimientos: se leen por mes, completos

- Los bloqueos son **planificados y conocidos de antemano** (enunciado; `CLAUDE.md` §2). Tabú los necesita **hacia adelante**: la hora límite efectiva (SI-23) mira si el destino estará bloqueado en la hora límite (hasta 36 h después), y cada tramo evita los bloqueos que empiecen antes de su llegada estimada (SI-02).
- `MapaVial` precalcula los intervalos con bloqueos constantes y guarda en caché las distancias por intervalo. Agregar bloqueos a mitad de la corrida obligaría a rehacer los intervalos e invalidar la caché mientras el planificador la usa desde otro hilo: más código delicado, sin beneficio real (un mes son unas 560 líneas, unos 25 KB).
- **Propuesta:** se siguen leyendo los archivos de bloqueos de los meses que cubre la corrida (como hoy), y lo mismo el mantenimiento. Son datos de planificación conocidos de antemano, como el mapa y la flota; A22 se refiere al consumo de los **pedidos**.
- **Alternativa (si el equipo la prefiere):** leer los bloqueos del mes siguiente cuando falten 48 h para que empiece, armando un `MapaVial` nuevo y cambiándolo entre bloques. Es más código en el núcleo y la recomiendo solo si el profesor lo pide expresamente.

### 3.4 Qué no cambia

- Las reglas del planificador y del evaluador (`Compartido`, `Contexto`, `BusquedaTabu`).
- El experimento: sigue pasando listas (`FuentePedidos.deLista`), con los mismos datos, el mismo orden y la misma semilla. **Debe dar exactamente los mismos resultados.**
- La cantidad de datos (B15): se leen todas las líneas de los archivos, sin filtros; solo cambia **cuándo** se leen.

## 4. Pruebas JUnit (`LecturaIncrementalTest`)

1. El lector incremental entrega exactamente los mismos pedidos (id, cantidad, destino, plazo y hora de registro) que la lectura completa de `DatosOficiales`, incluido el **cruce de fin de mes** (prefijo `aaaamm/` en los ids del mes siguiente).
2. **Igualdad del resultado:** la misma corrida (datos oficiales de 2026-11, misma semilla y parada por evaluaciones para que sea determinista) con la lista completa y con la lectura incremental da el mismo `Resultado`: colapso, costo, km, entregas, replanificaciones, cambios de unidad y la misma secuencia de eventos registrados. Se prueba con bloques (como la web) y con replanificación periódica (como el experimento). La única diferencia admitida es `pedidosOriginalesTotal`, que con la lectura incremental cuenta los pedidos leídos hasta el final de la corrida y no los del archivo completo (el experimento no usa la lectura incremental, así que su CSV no cambia).
3. En ningún momento hay en el búfer pedidos registrados más allá del inicio del bloque siguiente.
4. Las pruebas existentes (`ReproducibilidadTest`, `ExperimentoSimulacionTest`, `BloquesTest`) siguen pasando sin cambios.

## 5. Archivos que cambiarían

- Nuevos: `FuentePedidos.java`, `LectorVentasIncremental.java`, `LecturaIncrementalTest.java`.
- `LectorPedidos`: separar la lectura de **una** línea (`leerLinea`) para usarla en ambos lectores.
- `Simulador`: `porLlegar` pasa a ser el búfer que se llena desde la fuente; llegadas urgentes y total de originales, al leer; `cerrarMetricas` sin la lista completa.
- `DatosOficiales` y `CorridaWeb`: la corrida web usa el lector incremental; bloqueos y mantenimientos, como hoy.
- Documentación: `docs/guia_del_codigo.md`, SI nuevo en `docs/propuesta_cambios_IEN.md`, diagrama A22.
