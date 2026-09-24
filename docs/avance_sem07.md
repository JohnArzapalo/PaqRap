# Avance semana 07: etapas 8 a 15 (indicaciones nuevas del profesor)

Cada etapa se cierra con: compilación, todas las pruebas JUnit y un resumen aquí.
Donde una indicación del profesor choca con el IEN v01 o con una decisión anterior, **prevalece la del profesor** y se anota en la sección "Choques resueltos".

## Etapa 8: preparación y datos

- **8.1** Copia congelada en `DP1/PaqRap_version_referencia_sem07` con `LEEME.md` (tabla de Main vigente: I1 708 / 672 / 672; I2 816 / 816 / 816, todas con H = 0).
- **8.2** No hay archivos oficiales en `datos/`. Se generaron `datos/ventas202609_SINTETICO_MES.txt` (3 600 pedidos, 30 días, 24 h con más densidad de día) y `datos/202609_SINTETICO.bloqueadas` (241 polilíneas abiertas; se descartaron 48 candidatos por alcanzabilidad o por tocar un almacén). Parámetros en `docs/datos_sinteticos.md`. Generador: `GeneradorDatosSinteticos` (semilla fija).
- **8.3** `GeneradorCarga` ya remuestrea conservando la hora del día y la posición. Con el archivo mensual, la carga se reparte en las 24 h. El modo simulación usa el archivo mensual por defecto y genera 30 días por nivel (tope de seguridad).
- `MapaVial` (Etapa 9.1) se escribió antes para que el generador valide los bloqueos con el mismo BFS del simulador.
- Pruebas: 28 de 28 pasan.

## Etapas 9 a 12 (núcleo con estado)

Se implementaron juntas porque comparten el mismo cambio de fondo: **una sola función de evaluación de rutas** (`Compartido.evaluarRuta`) que usa un **contexto de planificación** (`Contexto`). Con el contexto por defecto, reproduce exactamente el modelo anterior; se verificó con `Main`, que da los mismos 708 / 672 / 672 y 816 / 816 / 816.

**Etapa 9: red vial con bloqueos**
- `MapaVial`:
  - retícula de 71 × 51 nodos y lector de `aaaamm.bloqueadas` (todos los nodos de cada tramo, extremos incluidos);
  - consulta `bloqueado(x, y, t)`;
  - BFS que no atraviesa nodos bloqueados;
  - caché LRU por (intervalo de estado constante, origen), guardada como `short[]`.
- **Destino bloqueado:** la entrega espera a que el nodo se libere (`finBloqueo`).
- **Aproximación del planificador (9.3):** cada tramo evita los bloqueos activos al salir **y los que empiezan antes de su llegada estimada**: la unión de los estados de la ventana del tramo, que se amplía si el rodeo la alarga. Es conservador.
- **Movimiento (9.4):** el simulador mueve las unidades nodo a nodo por ese mismo camino. Si un nodo se bloquea antes de llegar (por ejemplo, porque la alimentación alargó el tramo), la unidad se detiene en el nodo anterior y se replanifica en ese instante.
- **Resguardos:**
  - Si el bloqueo aparece justo en el instante en que se compromete el plan, la unidad espera en su nodo; replanificar en ese momento repetía el mismo plan y **se detectó un ciclo infinito en una prueba de humo, ya corregido**.
  - Una replanificación por evento que se repite más de 3 veces en el mismo instante se omite y queda registrada.
- **Parámetro:** `red.bloqueos=si|no`.

**Etapa 10: almacenes, recargas y entregas parciales**
- **Stock:** central infinito; Nor-Oeste y Este con 1 000, repuestos a las 23:59:59. El stock se descuenta al cargar. El plan no puede cargar más que el stock de un almacén: es una restricción dura en `evaluarSolucion`, y `mejorInsercion` descarta esas opciones.
- **Varios viajes:** la ruta es una secuencia de viajes; cada viaje empieza con una parada `RECARGA`, o con la salida desde un almacén (carga implícita).
  - **AG:** `splitConEstado` corta la permutación en tramos y elige, para cada tramo, la unidad y el almacén o trasvase que minimizan (tardanzas, costo) reales desde el estado de la unidad.
  - **Tabú:** operador `recarga` (insertar, cambiar de almacén o quitar una RECARGA).
- **Reparto del stock dentro de un plan:**
  - **AG:** reserva en el orden en que se asignan los tramos.
  - **Tabú y C&W:** se verifica la suma de lo cargado en todo el plan.
  - **Simulador:** descuenta cuando la unidad efectivamente carga.
- **Entregas parciales flexibles (10.3):** estrategia `urgentes`.
  - Las entregas pendientes de más de 8 paquetes con plazo de 8 h o menos se ordenan por urgencia.
  - Las primeras quedan enteras, tantas como autos libres haya en la próxima hora; el resto se divide en partes de hasta 8 (lo que cabe en una moto).
  - Cada parte puede ir en cualquier unidad y suma 1 h de acondicionamiento.
  - **Efecto:** medido en la Etapa 14.

**Etapa 11: replanificación con estado (P5, P6, P16)**
- **Planificador:** recibe `EstadoPlanificacion` (contexto, entregas, flota) y devuelve el plan de **todas** las unidades operativas. El contexto lleva el instante, la posición, la hora de inicio y la carga de cada unidad, los almacenes con stock, las unidades averiadas y el plan vigente.
- **Restricciones:**
  - una entrega a bordo solo la entrega su unidad (salvo trasvase);
  - una unidad en ruta empieza en el siguiente nodo de su camino, o termina la actividad en curso.
- **Tabú** parte del plan vigente reparado (se quita lo hecho y se insertan los pedidos nuevos con la inserción de C&W). C&W solo se usa en el primer ciclo.
- **AG:** siembra el 30 % de la población con el plan vigente codificado y variaciones suyas. Nunca devuelve algo peor que ese plan.
- **Estabilidad:** `cambios_de_unidad`, las entregas que cambian de unidad entre ciclos consecutivos.

**Etapa 12: averías y trasvase**
- **Formato provisional (por confirmar):** `##d##h##m:TTNN:tipo`, con lector y generador con semilla.
- **Reglas por tipo** (sección 2 del pedido):
  - la unidad averiada queda como almacén temporal en su nodo;
  - otra unidad puede hacer `TRASVASE` (30 min, parámetro `trasvase.minutos`) si le alcanza la capacidad;
  - al dejar el lugar, lo no trasvasado vuelve al stock del central (tipos 2 y 3); en el tipo 1, la unidad sigue con su carga.
- Una avería dispara una replanificación inmediata.
- **Mantenimiento preventivo:** lector de `mant.preventivo`. La unidad no se planifica en su ventana y sus rutas deben terminar antes. Duración por tipo: parámetro pendiente de confirmar.
- **Encuentros entre unidades en movimiento:** no implementados (mejora futura).

**Pruebas nuevas (19):** `MapaVialTest` (7), `ReplanificacionTest` (5), `AveriaTest` (4, incluido el ejemplo del profesor) y 3 nuevas en `SimuladorTest` (parciales, registro de eventos, DIA_A_DIA).

En la prueba del ejemplo del profesor, la moto se avería en el minuto 36; el auto sale del central, trasvasa y entrega en el minuto 111, antes del límite de 180, sin colapso. Si nadie puede llegar, el colapso ocurre exactamente en el minuto 180, con la causa "a bordo de unidad averiada".

Total: **47 pruebas, todas pasan.**

## Etapa 13: colapso y escenarios

- **Colapso (P3):** el primer instante en que una entrega (unidad de producto) supera su hora límite sin entregarse, esté a bordo, en una averiada o sin asignar. Se detecta en el minuto exacto con una cola de plazos; la simulación termina ahí.
  - Se registran el pedido, la unidad y la causa: `sin asignar`, `a bordo de unidad averiada`, `bloqueo`, `capacidad` o `llegada tardía (a bordo | planificada)`.
- **Escenarios:** `--escenario EXPERIMENTO | SIM_5D | COLAPSO | DIA_A_DIA`.
  - `EXPERIMENTO` y `COLAPSO`: sin esperas, hasta el colapso o el tope `sim.horizonte_max_dias` (30); si no colapsa, queda censurada.
  - `SIM_5D`: 5 días con ritmo escalado (`sim5d.minutos_reales=30`). Se detiene al colapsar según `sim5d.detener_en_colapso=si`; con `no`, registra el primer colapso y sigue. `--acelerado si` quita las esperas.
  - `DIA_A_DIA`: reloj 1:1. Por ahora hay estructura (interfaz `Reloj`) y una prueba corta con un reloj de prueba.
- **Registro de eventos (13.3):** con `--eventos prefijo.csv` se escribe un CSV por corrida con salidas, entregas, recargas, trasvases, averías, bloqueos encontrados, replanificaciones y el colapso. Los pasos nodo a nodo no se escriben, para no inflar el archivo.

## Etapa 14: experimento alineado con el profesor

- **Modo simulación:** algoritmo × carga × réplicas en escenario EXPERIMENTO, con bloqueos del mes, almacenes intermedios, replanificación con estado, averías opcionales (`--averias si`, el mismo archivo para todas las corridas) y los mismos pedidos y bloqueos por nivel (`datos/generados/`).
- **Corrida corta (1 réplica, Ta = 1000 ms):**
  - colapsos entre el día 5 y el día 10; no hizo falta recalibrar;
  - **3 de 6 colapsos por "destino bloqueado"** (inevitables con la regla actual). Se agregó esa causa y se anotó como pregunta al profesor;
  - resultados en `docs/resultados_para_exposicion.md`.
- **Tiempo estimado:** ≈ 3 h para las 30 corridas en 1 PC; reparto por nivel en 3 PCs (`docs/capacidad_y_carga.md`).
- **Efecto de las parciales:** +27 h en MEDIA y +3.8 h en ALTA (1 réplica).

## Etapa 15: documentos

- `docs/propuesta_cambios_IEN.md`: reescrito con las indicaciones del profesor (C-01 a C-12, supuestos SI-01 a SI-13, amenazas AV-01 a AV-10). Mantiene la nota "Pendiente verificar contra el IEN v01".
- `docs/preguntas_para_el_profesor.md`: 12 preguntas con el supuesto vigente de cada una.
- `docs/resultados_para_exposicion.md`, `docs/capacidad_y_carga.md`, `docs/datos_sinteticos.md` y `docs/diseno_simulador.md` actualizados.
- Pruebas al cierre: **47 de 47 pasan.**

## Choques resueltos (prevalece el profesor)

| Tema | Antes (IEN v01 / decisión previa) | Ahora (indicación del profesor) |
|---|---|---|
| Colapso | Pedido original no entregado en plazo (criterio operativo propuesto) | Al menos una **unidad de producto** fuera de plazo (P3). Equivale a la primera entrega parcial tardía; la simulación termina en ese instante. |
| Bloqueos | IEN v01: una configuración estática (la ventana de 24 h con más tramos) | Bloqueos por archivo y **por horario** (P1) |
| Almacenes | Solo el central en el simulador mínimo | Almacenes intermedios con stock y recargas (P2, P10) |
| Replanificación | Rutas despachadas fijas (P16 pendiente) | Replanificar todas las unidades desde el plan vigente y el estado real (P5, P6, P16) |
| Entregas parciales | Solo bloques de 24 paquetes | División flexible entre unidades de cualquier tipo (P13-P14) |
