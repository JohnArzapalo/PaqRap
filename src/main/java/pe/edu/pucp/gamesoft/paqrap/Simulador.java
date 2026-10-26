package pe.edu.pucp.gamesoft.paqrap;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simulador de eventos discretos con replanificación con estado (Etapas 9 a 13).
 *
 * MODELO
 *  - Reloj en minutos absolutos desde el día 1 a las 00:00. Un pedido entra a
 *    pendientes en la primera replanificación posterior a su hora de registro.
 *  - Replanificación periódica cada Sa minutos y, además, INMEDIATA ante un
 *    evento: avería, bloqueo encontrado en el camino, fin de una avería.
 *  - Replanificar, no volver a planificar (P5, P6, P16): en cada ciclo se
 *    replanifican TODAS las unidades operativas, estén en un almacén o en ruta,
 *    partiendo de su estado real (nodo, hora desde la que pueden empezar,
 *    carga a bordo) y del plan vigente reparado. Una unidad que viaja entre
 *    dos nodos se compromete a llegar al siguiente nodo; una que está
 *    entregando o trasvasando termina esa actividad.
 *  - Las unidades se mueven NODO A NODO por el camino más corto con los
 *    bloqueos activos al salir de cada tramo (MapaVial). Si el siguiente nodo
 *    se bloquea antes de llegar (bloqueo que empezó después de planificar),
 *    la unidad se detiene en el nodo anterior y se replanifica en ese momento.
 *  - Almacenes: central infinito; Nor-Oeste y Este con stock (1000), repuesto
 *    a las 23:59:59; el stock se descuenta al cargar (RECARGA).
 *  - Averías (archivo): la unidad se detiene en su nodo y su carga queda como
 *    almacén temporal (TRASVASE) hasta que deja el lugar; luego la unidad y lo
 *    no trasvasado pasan al central (esos paquetes vuelven a pendientes) o,
 *    en el tipo 1, la unidad sigue con su carga. Encuentros entre dos
 *    unidades EN MOVIMIENTO: no implementados (mejora futura, 12.4).
 *  - Mantenimiento preventivo: la unidad no se planifica en su ventana.
 *  - Alimentación: 1 h por turno (03, 11 y 19 h), dondequiera que esté.
 *  - Entregas parciales flexibles (10.3): estrategia "urgentes" (ver dividirUrgentes).
 *  - Planificación POR BLOQUES con adelanto (Etapa 33, SI-30; activa si bloqueMin > 0):
 *    en vez de replanificar cada Sa, el tiempo se divide en bloques de bloqueMin
 *    minutos simulados. Cada bloque se planifica adelantoMin minutos ANTES de que
 *    empiece, sobre el estado PROYECTADO a su inicio: una copia del simulador
 *    sigue el plan vigente hasta ese instante (es exacto, porque el plan
 *    comprometido es determinista). Así el reloj no se detiene a esperar al
 *    planificador y cada unidad empieza el bloque donde terminó el anterior.
 *    Los pedidos que llegan después del corte se insertan en el plan al empezar
 *    el bloque (sin correr el algoritmo) y el bloque siguiente los optimiza.
 *    Vía de excepción (pregunta 18): un pedido urgente, una avería o un bloqueo
 *    en el camino replanifican en el instante.
 *
 * COLAPSO (P3 del profesor): el primer instante en que una unidad de producto
 * de cualquier pedido supera su hora límite sin haberse entregado (a bordo,
 * en una unidad averiada o sin asignar). Se registra el pedido, la unidad y
 * la causa, y la simulación termina (salvo SIM_5D con detenerEnColapso = no).
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-04, SI-07, SI-08, SI-09, SI-11, SI-15 (inentregables por bloqueo), SI-16 (estabilidad), SI-18 (averías externas), SI-19, SI-20 (holgura), SI-21 (tolerancia en el límite), SI-22 (ventana del tramo con alimentación).
 */
class Simulador {

    enum Escenario { EXPERIMENTO, SIM_5D, COLAPSO, DIA_A_DIA }

    static class Config {
        Escenario escenario = Escenario.EXPERIMENTO;
        double saMin = Parametros.decimal("simulacion.sa_min", 60);
        long taMs = Parametros.entero("simulacion.ta_ms", 2000);
        long maxEvaluaciones = 0;
        long semilla = 1;
        /** > 0: horizonte explícito (min); si no, el del escenario. */
        double horizonteMin = -1;
        boolean detenerEnColapso = true;
        MapaVial mapa = null;
        /** Regla para destinos bloqueados (Etapa 17; red.destino_bloqueado). */
        Contexto.ReglaDestino reglaDestino = Contexto.ReglaDestino.desde(Parametros.texto("red.destino_bloqueado", "esperar"));
        /** Soles por entrega que cambia de unidad frente al plan vigente (Etapa 18; 0 = sin penalidad). */
        double penalidadEstabilidad = Parametros.decimal("estabilidad.penalidad_por_cambio", 16);
        double holguraMin = Parametros.decimal("plan.holgura_min", 60);
        /** Minuto absoluto en que empieza la simulación (Etapa 22: ventanas de 5 días que no
         *  empiezan el día 1). El horizonte se cuenta desde aquí. */
        double inicioMin = 0;
        double penalidadHolgura = Parametros.decimal("plan.penalidad_holgura", 200);
        boolean almacenesIntermedios = !"no".equalsIgnoreCase(Parametros.texto("almacenes.intermedios", "si"));
        boolean alimentacion = !"no".equalsIgnoreCase(Parametros.texto("turnos.alimentacion", "si"));
        /** Etapa 32: un pedido cuya hora límite EFECTIVA (SI-23) queda a menos de estos minutos de su
         *  registro es urgente y su llegada dispara una replanificación (evento), sin esperar a Sa.
         *  0 = desactivado (así se corrió el experimento de las etapas 28 y 29). */
        double urgenteMin = Parametros.decimal("simulacion.urgente_min", 120);
        /** Etapa 33 (SI-30): minutos simulados de cada bloque; 0 = replanificación periódica cada Sa
         *  (así se corrió el experimento de las etapas 28 y 29). */
        double bloqueMin = 0;
        /** Minutos simulados antes del inicio de cada bloque en que se empieza a planificarlo. */
        double adelantoMin = Parametros.decimal("bloque.adelanto_min", 30);
        /** true: el bloque se planifica en otro hilo mientras la simulación sigue (visualizador);
         *  false: en el mismo hilo, en el instante del corte (experimento: mismo resultado, sin hilos). */
        boolean adelantoEnParalelo = false;
        /** Etapa 35 (decisión del equipo, SI-34): una unidad que queda sin ruta vuelve al almacén más cercano con
         *  stock en vez de quedarse donde está. Solo en las corridas del visualizador; el experimento no cambia. */
        boolean regresoSinTareas = false;
        /** Presupuesto (ms) de los planes calculados con adelanto, que no detienen el reloj; 0 = taMs.
         *  Las replanificaciones en el instante (excepciones) usan siempre taMs. */
        long taBloqueMs = 0;
        /** Etapa 35: minutos simulados por minuto real (0 = sin espera). Con el visualizador, un plan adelantado que
         *  se lanza tarde (después de una excepción) usa como mucho fraccionTaBloque del tiempo real que le queda. */
        double factorReloj = 0;
        double fraccionTaBloque = Parametros.decimal("bloque.fraccion_ta", 0.5);
        /** Reparto por productos (Etapa 31, SI-28): una entrega puede repartirse entre varias unidades. */
        boolean reparto = !"no".equalsIgnoreCase(Parametros.texto("reparto.productos", "si"));
        String estrategiaParciales = Parametros.texto("parciales.estrategia", "ninguna");
        double umbralUrgenciaH = Parametros.decimal("parciales.umbral_h", 8);
        int tamanoParcial = (int) Parametros.entero("parciales.tamano", 8);
        List<Averia> averias = new ArrayList<>();
        List<Mantenimiento> mantenimientos = new ArrayList<>();
        Reloj reloj = Reloj.sinEspera();
        PrintWriter eventos = null;

        double horizonte() {
            if (horizonteMin > 0) return horizonteMin;
            switch (escenario) {
                case SIM_5D: return 5 * 1440;
                case DIA_A_DIA: return Parametros.decimal("diaadia.horizonte_min", 1440);
                default: return Parametros.decimal("sim.horizonte_max_dias", 30) * 1440;
            }
        }
    }

    // ============================ Resultado ============================

    static class Resultado {
        boolean censurada;
        double colapsoMin;          // = fin de la simulación si es censurada
        String pedidoColapso = "", unidadColapso = "", causaColapso = "";
        double finMin;
        double costoAcumulado, kmAcumulados;
        int pedidosOriginalesTotal, pedidosLlegados, pedidosEntregados, pedidosEvaluables, pedidosEnPlazo;
        int replanificaciones, replanPorEvento;
        double planificadorMsMedio, planificadorMsMax;
        long iteracionesTotales, evaluacionesTotales, aplazamientos;
        /** Estabilidad (11.4): entregas que cambian de unidad entre ciclos consecutivos. */
        long cambiosDeUnidad;
        int viajesTotales, viajesMax;
        double viajesMedio;
        int averiasAplicadas, trasvases, bloqueosEncontrados, parcialesCreadas, entregasTarde;
        /** Pedidos originales inentregables por bloqueo (regla NO_EVALUABLE): excluidos del colapso. */
        int pedidosInentregablesBloqueo;
        /** Planificación por bloques (SI-30): bloques iniciados, los que usaron el plan hecho con
         *  adelanto y los que tuvieron que esperar al planificador (su plan no estaba listo a tiempo). */
        int bloques, bloquesConAdelanto, bloquesTardios;
        /** Pasos de una unidad a un nodo que no es vecino del anterior (debe ser 0: sin saltos). */
        int saltos;
        final List<Evt> registro = new ArrayList<>();   // eventos principales (para pruebas)

        double pctPedidosEnPlazo() {
            return pedidosEvaluables == 0 ? 100.0 : 100.0 * pedidosEnPlazo / pedidosEvaluables;
        }
    }

    /** Evento de la línea de tiempo de una unidad (y del registro de eventos). */
    static final class Evt {
        enum T { NODO, SALIDA, ENTREGA, RECARGA, TRASVASE, FIN, AVERIA, BLOQUEO, REPLANIFICACION, COLAPSO }

        final T tipo;
        final double min;
        final int x, y;
        final List<String> entregas;
        final ParadaAlg parada;
        final double finMin;
        final int cantidad;
        String unidad = "", detalle = "";
        /** NODO (Etapa 35): minuto en que la unidad sale del nodo anterior hacia este (llegada − 1 km / v).
         *  Con la llegada, el visualizador reproduce el desplazamiento sin interpolar a ojo. */
        double salidaMin = Double.NaN;

        Evt(T tipo, double min, int x, int y, List<String> entregas, ParadaAlg parada, double finMin, int cantidad) {
            this.tipo = tipo;
            this.min = min;
            this.x = x;
            this.y = y;
            this.entregas = entregas;
            this.parada = parada;
            this.finMin = finMin;
            this.cantidad = cantidad;
        }
    }

    private enum EstadoUnidad { OPERATIVA, AVERIADA, MANTENIMIENTO }

    private static final class Unidad {
        final UnidadTransporte u;
        EstadoUnidad estado = EstadoUnidad.OPERATIVA;
        int x = Compartido.ALMACEN_CENTRAL.x, y = Compartido.ALMACEN_CENTRAL.y;
        double ocupadoHasta = 0;
        final ArrayDeque<Evt> eventos = new ArrayDeque<>();
        List<ParadaAlg> rutaVigente = new ArrayList<>();
        final Set<String> aBordo = new LinkedHashSet<>();
        double encuentroMin = Double.POSITIVE_INFINITY;
        boolean vioBloqueo;
        Averia averia;
        boolean averiaDejoLugar;
        int viajes;
        double km;   // recorridos por esta unidad (para el visualizador y el reporte)
        // Seguimiento para el visualizador (Etapa 35, panel E06 a E17): no influye en la planificación.
        // Origen = última parada (almacén, cliente o trasvase) a la que llegó; salida = cuándo partió de ahí.
        String origenLugar = Compartido.ALMACEN_CENTRAL.codigo;
        int origenX = Compartido.ALMACEN_CENTRAL.x, origenY = Compartido.ALMACEN_CENTRAL.y;
        double origenLlegadaMin = Double.NaN, salidaMin = Double.NaN, kmEnSalida;
        /** Almacén de la última recarga con productos (filtro por origen, E11). */
        String ultimaRecarga = Compartido.ALMACEN_CENTRAL.codigo;
        /** Recorrido de la unidad (minuto y nodo de cada paso) para la ruta anterior de un envío (F02, F04).
         *  Solo lo llena el simulador real; la copia proyectada empieza vacía. */
        double[] rastroMin = new double[64];
        int[] rastroNodo = new int[64];
        int nRastro;

        void pisar(double min, int x, int y) {
            if (nRastro == rastroMin.length) {
                rastroMin = java.util.Arrays.copyOf(rastroMin, nRastro * 2);
                rastroNodo = java.util.Arrays.copyOf(rastroNodo, nRastro * 2);
            }
            rastroMin[nRastro] = min;
            rastroNodo[nRastro++] = MapaVial.nodo(x, y);
        }

        Unidad(UnidadTransporte u) {
            this.u = u;
        }

        /** Copia para la proyección de un bloque (comparte los eventos y paradas, que no se modifican). */
        Unidad copia() {
            Unidad c = new Unidad(u);
            c.estado = estado;
            c.x = x;
            c.y = y;
            c.ocupadoHasta = ocupadoHasta;
            c.eventos.addAll(eventos);
            c.rutaVigente = new ArrayList<>(rutaVigente);
            c.aBordo.addAll(aBordo);
            c.encuentroMin = encuentroMin;
            c.vioBloqueo = vioBloqueo;
            c.averia = averia;
            c.averiaDejoLugar = averiaDejoLugar;
            c.viajes = viajes;
            c.km = km;
            c.origenLugar = origenLugar;
            c.origenX = origenX;
            c.origenY = origenY;
            c.origenLlegadaMin = origenLlegadaMin;
            c.salidaMin = salidaMin;
            c.kmEnSalida = kmEnSalida;
            c.ultimaRecarga = ultimaRecarga;
            return c;
        }
    }

    /** Lo que recibe el planificador en un instante (Etapa 33: se arma igual en la copia proyectada y en el real). */
    private static final class Preparacion {
        Contexto cx;
        List<Unidad> operativas;
        Map<Unidad, Evt> compromiso;
        List<UnidadTransporte> disponibles;
        List<Pedido> aPlanificar;
        long semilla;
        /** Resumen del estado de partida: si el de la copia y el del real coinciden, el plan adelantado vale. */
        String firma;
    }

    /** Plan de un bloque calculado con adelanto sobre la copia proyectada (SI-30). */
    private static final class Adelanto {
        final double inicioBloque, corte;
        String firma;
        Contexto cx;
        Planificador.Plan plan;   // null: la copia no llegó al inicio del bloque o no había nada que planificar
        double ms;
        /** Etapa 35: líneas de tiempo del bloque siguiente (solo con el visualizador), para que lo tenga en cola. */
        Map<String, List<double[]>> trayectorias;
        Map<String, int[]> posiciones;

        Adelanto(double inicioBloque, double corte) {
            this.inicioBloque = inicioBloque;
            this.corte = corte;
        }
    }

    // ============================ Estado de la corrida ============================

    private final Config cfg;
    private final List<UnidadTransporte> flota;
    private final Planificador planificador;
    private final List<Unidad> unidades = new ArrayList<>();
    private final Map<String, Unidad> unidadPorCodigo = new HashMap<>();
    /** Pedidos ya leídos de la fuente que todavía no llegaron (Etapa 35, A22: como mucho un bloque por delante). */
    private final List<Pedido> porLlegar;
    /** De dónde salen los pedidos: una lista (experimento) o la lectura incremental de los archivos (web). null en la copia. */
    private final FuentePedidos fuente;
    /** Hora de registro y hora límite (min) de cada pedido original que llegó, para las métricas de cierre. */
    private final Map<String, double[]> registroDe = new HashMap<>();
    /** Pedidos originales leídos (solo con lectura incremental, que no conoce el total de antemano). */
    private final Set<String> originalesLeidos = new java.util.HashSet<>();
    private int siguiente = 0;
    private final Map<String, Pedido> entregas = new LinkedHashMap<>();
    /** Estado de cada entrega: P (pendiente en almacén), U:cod (a bordo), A:cod (en averiada), E (entregada), X (reemplazada por partes). */
    private final Map<String, String> estado = new HashMap<>();
    private final Map<String, Double> entregadaEn = new HashMap<>();
    private final Map<String, List<String>> partesDe = new LinkedHashMap<>();
    // Seguimiento de productos para el visualizador (Etapa 35, E20 a E22, E31, F02): almacén donde se cargó cada
    // entrega, cuándo, qué unidad la entregó y sus escalas. No influye en la planificación; la copia proyectada no los llena.
    private final Map<String, String> origenDe = new HashMap<>();
    private final Map<String, Double> cargadoEn = new HashMap<>();
    private final Map<String, String> entregadoPor = new HashMap<>();
    /** Escalas de cada entrega (carga, trasvase, avería, devolución y entrega) para su ruta anterior (F02, F04). */
    private final Map<String, List<Escala>> escalasDe = new HashMap<>();

    /** Una escala de un envío: qué le pasó, cuándo, dónde y con qué unidad (Etapa 35). */
    private static final class Escala {
        final double min;
        final String tipo, lugar, unidad;
        final int x, y;

        Escala(double min, String tipo, String lugar, String unidad, int x, int y) {
            this.min = min;
            this.tipo = tipo;
            this.lugar = lugar;
            this.unidad = unidad;
            this.x = x;
            this.y = y;
        }
    }

    private void escala(String id, double min, String tipo, String lugar, Unidad un, int x, int y) {
        if (!proyeccion) escalasDe.computeIfAbsent(id, k -> new ArrayList<>()).add(new Escala(min, tipo, lugar, un.u.codigo, x, y));
    }
    /** Entregas cuyo plazo ya venció sin entregarse (siguen planificándose, tarde). */
    private final Set<String> vencidas = new java.util.HashSet<>();
    /** Pedidos originales inentregables por bloqueo (regla NO_EVALUABLE). */
    private final Set<String> inentregables = new java.util.HashSet<>();
    private final PriorityQueue<Pedido> plazos = new PriorityQueue<>(Comparator.comparingDouble(Pedido::horaLimite));
    private final double[] stock = new double[3];   // central (infinito), Nor-Oeste, Este
    private final Resultado res = new Resultado();
    private Map<String, String> asignacionAnterior = new HashMap<>();
    private boolean primerPlan = true;
    private boolean colapsoRegistrado = false;
    private int ciclo = 0;
    /** Partes creadas al repartir productos (SI-28); se pasa a cada Contexto para que los ids no se repitan. */
    private int partesCreadas = 0;
    /** Minutos de llegada de los pedidos urgentes (ordenados); cada uno dispara una replanificación (Etapa 32). */
    private final List<Double> llegadasUrgentes = new ArrayList<>();
    private int siguienteUrgente = 0;
    private double sumaMs = 0;
    private double ultimaReplanEvento = -1;
    private int repeticionesEnInstante = 0;
    /** Averías del archivo, ordenadas, y la siguiente por aplicar. */
    private final List<Averia> averiasArchivo = new ArrayList<>();
    private int iAveria = 0;
    /** Próxima reposición de los intermedios (23:59:59). */
    private double proximaReposicion;

    // ===== Planificación por bloques con adelanto (Etapa 33, SI-30) =====
    /** Inicio del próximo bloque (minuto absoluto). */
    private double proximoBloque;
    /** true si ya se lanzó (o se descartó) la planificación adelantada del próximo bloque. */
    private boolean adelantoLanzado;
    /** Plan del próximo bloque en preparación (otro hilo) o ya listo. */
    private Future<Adelanto> pendiente;
    /** Último instante en que una excepción replanificó: los pedidos hasta ahí ya están en el plan. */
    private double ultimaExcepcion = Double.NEGATIVE_INFINITY;
    private ExecutorService ejecutor;
    /** Etapa 35 (SI-32): true mientras la simulación está detenida esperando al planificador (replanificación en el instante). */
    private volatile boolean pausado;
    /** Etapa 35: true cuando la corrida terminó (horizonte, colapso o detención): su reloj ya no avanza. */
    private volatile boolean terminada;
    /** Etapa 35: versión del plan aplicado (sube con cada plan nuevo) y minuto en que se aplicó; el visualizador
     *  vuelve a pedir las trayectorias cuando cambia. */
    private int planVersion;
    private double planAplicadoMin;
    /** true en la copia que proyecta el estado al inicio de un bloque: no registra eventos. */
    private boolean proyeccion = false;

    // ===== Integración con el visualizador (Etapa 21) =====
    /** Protege el estado mientras otro hilo arma una instantánea; se libera durante la
     *  espera del reloj y mientras el planificador trabaja. */
    private final ReentrantLock cerrojo = new ReentrantLock();
    /** Averías registradas desde fuera (visualizador): {código de unidad, tipo}. */
    private final ConcurrentLinkedQueue<Object[]> averiasExternas = new ConcurrentLinkedQueue<>();
    /** Pedidos registrados desde fuera (visualizador, operación día a día); llegan en el instante actual. */
    private final ConcurrentLinkedQueue<Pedido> pedidosExternos = new ConcurrentLinkedQueue<>();
    /** Pedidos de la carga masiva del día a día (etapa 35): llegan en SU hora de registro, que es futura. */
    private final ConcurrentLinkedQueue<Pedido> pedidosProgramados = new ConcurrentLinkedQueue<>();
    private final Set<String> idsProgramados = new java.util.HashSet<>();
    /** Cambios de velocidad pendientes (se aplican en la siguiente replanificación, P16). */
    private final ConcurrentLinkedQueue<Object[]> velocidadesPendientes = new ConcurrentLinkedQueue<>();
    /** Velocidades vigentes en ESTA simulación tras los cambios en caliente (Etapa 24). */
    private final EnumMap<TipoUnidad, Double> velocidades = new EnumMap<>(TipoUnidad.class);
    private volatile Thread hilo;
    private volatile boolean detenerSolicitado;
    private double tActual = 0;

    private Simulador(FuentePedidos fuente, List<UnidadTransporte> flota, Planificador planificador, Config cfg) {
        this.cfg = cfg;
        this.flota = flota;
        this.planificador = planificador;
        for (UnidadTransporte u : flota) {
            Unidad un = new Unidad(u);
            unidades.add(un);
            unidadPorCodigo.put(u.codigo, un);
        }
        if (cfg.bloqueMin > 0 && (cfg.adelantoMin <= 0 || cfg.adelantoMin > cfg.bloqueMin))
            throw new IllegalArgumentException("El adelanto (" + cfg.adelantoMin + " min) debe ser positivo y no mayor que el bloque ("
                    + cfg.bloqueMin + " min)");
        // Etapa 35 (A22, SI-33): los pedidos se leen de la fuente por partes, a medida que hacen falta
        // (leerPedidos); las llegadas urgentes (Etapa 32, esUrgente) se detectan al leerlos.
        this.fuente = fuente;
        tActual = cfg.inicioMin;   // antes de que arranque el hilo, «ahora» es el inicio de la corrida
        porLlegar = new ArrayList<>();
        res.pedidosOriginalesTotal = Math.max(0, fuente.originalesTotales());
        stock[0] = Double.POSITIVE_INFINITY;
        stock[1] = stock[2] = Compartido.STOCK_INTERMEDIO;
    }

    /**
     * Copia del estado para proyectar el inicio de un bloque (Etapa 33, SI-30). Copia
     * todo lo que cambia al avanzar la línea de tiempo; los pedidos, las paradas y los
     * eventos se comparten porque nadie los modifica. No registra eventos ni espera al reloj.
     */
    private Simulador(Simulador o) {
        cfg = o.cfg;
        flota = o.flota;
        planificador = o.planificador;
        for (Unidad un : o.unidades) {
            Unidad c = un.copia();
            unidades.add(c);
            unidadPorCodigo.put(c.u.codigo, c);
        }
        porLlegar = new ArrayList<>(o.porLlegar);   // solo lo ya leído: la copia no lee de la fuente
        fuente = null;
        siguiente = o.siguiente;
        entregas.putAll(o.entregas);
        estado.putAll(o.estado);
        entregadaEn.putAll(o.entregadaEn);
        for (Map.Entry<String, List<String>> e : o.partesDe.entrySet()) partesDe.put(e.getKey(), new ArrayList<>(e.getValue()));
        vencidas.addAll(o.vencidas);
        inentregables.addAll(o.inentregables);
        plazos.addAll(o.plazos);
        System.arraycopy(o.stock, 0, stock, 0, stock.length);
        asignacionAnterior = o.asignacionAnterior;   // se reemplaza (no se modifica) al comprometer un plan
        primerPlan = o.primerPlan;
        colapsoRegistrado = o.colapsoRegistrado;
        ciclo = o.ciclo;
        partesCreadas = o.partesCreadas;
        llegadasUrgentes.addAll(o.llegadasUrgentes);
        siguienteUrgente = o.siguienteUrgente;
        averiasArchivo.addAll(o.averiasArchivo);
        iAveria = o.iAveria;
        proximaReposicion = o.proximaReposicion;
        velocidades.putAll(o.velocidades);
        tActual = o.tActual;
        proyeccion = true;
    }

    /**
     * Etapa 32 (SI-29): un pedido es urgente si, cuando entre al plan, le quedarían menos de
     * urgenteMin minutos hasta su hora límite efectiva (SI-23); su llegada replanifica en el
     * instante. Sin bloques entra al plan al llegar; con bloques (SI-30), al inicio del bloque
     * siguiente (planificado con adelanto o, si llegó después del corte, por inserción), así
     * que la espera se descuenta del margen.
     */
    private boolean esUrgente(Pedido p, double llegadaMin) {
        if (cfg.urgenteMin <= 0) return false;
        double entraMin = cfg.bloqueMin > 0 ? inicioBloqueSiguiente(llegadaMin) : llegadaMin;
        return Contexto.limiteEfectivo(cfg.mapa, cfg.reglaDestino, p, 0) * 60 - entraMin < cfg.urgenteMin;
    }

    /** Inicio del primer bloque que empieza en la llegada o después (el primero, al empezar la simulación). */
    private double inicioBloqueSiguiente(double llegadaMin) {
        if (llegadaMin <= cfg.inicioMin) return cfg.inicioMin;
        return cfg.inicioMin + Math.ceil((llegadaMin - cfg.inicioMin) / cfg.bloqueMin) * cfg.bloqueMin;
    }

    /**
     * @param pedidos entregas con horaRegistro ABSOLUTA en horas (LectorPedidos.leerAbsoluto)
     */
    /**
     * Etapa 35 (A22): lee de la fuente los pedidos registrados hasta hastaMin (los que hagan falta para
     * decidir hasta el próximo bloque o replanificación) y detecta los urgentes. Descarta de la memoria
     * los que ya llegaron. Sin fuente (la copia proyectada) no hace nada.
     */
    private void leerPedidos(double hastaMin) {
        if (fuente == null) return;
        for (Pedido p : fuente.leerHasta(hastaMin)) {
            porLlegar.add(p);
            if (esUrgente(p, p.horaRegistro * 60)) llegadasUrgentes.add(p.horaRegistro * 60);
            if (fuente.originalesTotales() < 0 && originalesLeidos.add(p.idOriginal)) res.pedidosOriginalesTotal++;
        }
        if (siguiente > 256) {   // los que ya llegaron no hacen falta aquí (quedan en «entregas»)
            porLlegar.subList(0, siguiente).clear();
            siguiente = 0;
        }
        if (siguienteUrgente > 256) {
            llegadasUrgentes.subList(0, siguienteUrgente).clear();
            siguienteUrgente = 0;
        }
    }

    static Resultado simular(List<Pedido> pedidos, List<UnidadTransporte> flota, Planificador planificador,
                             Config cfg) {
        return crear(pedidos, flota, planificador, cfg).ejecutar();
    }

    /** Crea la simulación sin correrla (para ejecutarla en otro hilo: ServicioSimulacion). */
    static Simulador crear(List<Pedido> pedidos, List<UnidadTransporte> flota, Planificador planificador,
                           Config cfg) {
        return new Simulador(FuentePedidos.deLista(pedidos), flota, planificador, cfg);
    }

    /** Crea la simulación con otra fuente de pedidos (Etapa 35: lectura incremental de los archivos oficiales). */
    static Simulador crear(FuentePedidos fuente, List<UnidadTransporte> flota, Planificador planificador, Config cfg) {
        return new Simulador(fuente, flota, planificador, cfg);
    }

    /** Corre la simulación en el hilo actual hasta el colapso, el horizonte o una detención. */
    Resultado ejecutar() {
        hilo = Thread.currentThread();
        try {
            correr();
        } finally {
            Contexto.restablecer();
            Thread.interrupted();   // limpia una interrupción pendiente
        }
        return res;
    }

    /** Evento externo (visualizador): avería de la unidad AHORA. Seguro desde otro hilo. */
    void inyectarAveria(String codigoUnidad, int tipo) {
        averiasExternas.add(new Object[]{codigoUnidad, tipo});
        Thread h = hilo;
        if (h != null) h.interrupt();   // corta la espera del reloj para atenderla ya
    }

    /**
     * Pedido registrado desde el visualizador (operación día a día). Llega en el
     * instante simulado actual (su hora de registro se fija al atenderlo) y
     * dispara una replanificación inmediata. Seguro desde otro hilo.
     * @param p entrega con id, destino, cantidad y plazo (la hora de registro se ignora)
     */
    void inyectarPedido(Pedido p) {
        pedidosExternos.add(p);
        Thread h = hilo;
        if (h != null) h.interrupt();   // corta la espera del reloj para atenderlo ya
    }

    /** Minuto que marca el reloj de la corrida en este instante real (Etapa 35): el último instante procesado si
     *  está en pausa, terminada o sin espera; con ritmo escalado, lo que avanzó el reloj desde entonces. */
    double minutoDelReloj() {
        if (pausado || terminada) return tActual;
        return Math.max(tActual, cfg.reloj.ahora(tActual));
    }

    /**
     * Pedido de la carga masiva de la operación día a día (Etapa 35, Tarea 6): llega en SU hora de registro
     * (no antes de ahora), como los del archivo oficial: entra al plan del bloque que le corresponde y, si es
     * urgente, replanifica al llegar. Seguro desde otro hilo.
     */
    void programarPedido(Pedido p) {
        pedidosProgramados.add(p);
        Thread h = hilo;
        if (h != null) h.interrupt();   // para recalcular el próximo instante con el pedido nuevo
    }

    /** Pone el pedido programado entre los que faltan llegar, en su lugar por hora de registro. */
    private void programar(Pedido p, double t) {
        if (p.horaRegistro * 60 < t) p.horaRegistro = t / 60.0;   // no puede llegar en el pasado
        idsProgramados.add(p.id);
        int i = porLlegar.size();
        while (i > siguiente && porLlegar.get(i - 1).horaRegistro > p.horaRegistro) i--;
        porLlegar.add(i, p);
        double llegada = p.horaRegistro * 60;
        if (esUrgente(p, llegada)) {
            int k = llegadasUrgentes.size();
            while (k > siguienteUrgente && llegadasUrgentes.get(k - 1) > llegada) k--;
            llegadasUrgentes.add(k, llegada);
        }
        registrar(new Evt(Evt.T.REPLANIFICACION, t, p.x, p.y, List.of(p.id), null, t, p.cantidad), "",
                "pedido " + p.idOriginal + " programado para " + formatear(llegada) + ": " + p.cantidad + " paq. a (" + p.x + ","
                        + p.y + "), plazo " + p.plazoMaximoHoras + " h");
    }

    /** Minuto simulado actual (para quien registra pedidos desde fuera). */
    double minutoActual() {
        return tActual;
    }

    /** Velocidad (km/h) del tipo en esta simulación: la configurada, salvo cambio en caliente. */
    double velocidad(TipoUnidad tipo) {
        return velocidades.getOrDefault(tipo, tipo.velocidadPromedio);
    }

    /** Cambio de velocidad en caliente: se aplica en la siguiente replanificación (P16). */
    void cambiarVelocidad(TipoUnidad tipo, double kmPorHora) {
        velocidadesPendientes.add(new Object[]{tipo, kmPorHora});
    }

    /** Pide terminar la simulación en el próximo instante procesado. */
    void detener() {
        detenerSolicitado = true;
        Thread h = hilo;
        if (h != null) h.interrupt();
    }

    private void correr() {
        if (cfg.eventos != null) cfg.eventos.println("tiempo_min,dia_hora,evento,unidad,pedido,x,y,detalle");
        double fin = cfg.inicioMin + cfg.horizonte();
        double t = cfg.inicioMin;
        tActual = t;
        double proximaReplan = t;
        proximoBloque = t;   // con bloques, el primero se planifica al empezar
        averiasArchivo.addAll(cfg.averias);
        averiasArchivo.sort(Comparator.comparingDouble(a -> a.tiempoMin));
        while (iAveria < averiasArchivo.size() && averiasArchivo.get(iAveria).tiempoMin < t) iAveria++;   // anteriores al inicio
        proximaReposicion = Math.floor(t / 1440) * 1440 + 1440 - 1.0 / 60;   // 23:59:59 del día de inicio
        boolean terminar = false;

        cerrojo.lock();
        try {
        while (!terminar) {
            // 0. Pedidos de la fuente hasta el próximo bloque (o replanificación): lo que hace falta para decidir
            leerPedidos(Math.max(t, cfg.bloqueMin > 0 ? proximoBloque : proximaReplan));
            // 1. Próximo instante
            double T = Math.min(cfg.bloqueMin > 0 ? proximoInstanteDeBloque() : proximaReplan, fin);
            for (Unidad un : unidades) {
                if (!un.eventos.isEmpty()) T = Math.min(T, un.eventos.peek().min);
                T = Math.min(T, un.encuentroMin);
                if (un.estado == EstadoUnidad.AVERIADA) {
                    if (!un.averiaDejoLugar) T = Math.min(T, un.averia.enLugarHastaMin());
                    T = Math.min(T, un.averia.disponibleDesdeMin());
                }
            }
            if (iAveria < averiasArchivo.size()) T = Math.min(T, averiasArchivo.get(iAveria).tiempoMin);
            while (siguienteUrgente < llegadasUrgentes.size() && llegadasUrgentes.get(siguienteUrgente) < t) siguienteUrgente++;
            if (siguienteUrgente < llegadasUrgentes.size()) T = Math.min(T, llegadasUrgentes.get(siguienteUrgente));
            for (Mantenimiento m : cfg.mantenimientos) {
                if (m.inicioMin > t) T = Math.min(T, m.inicioMin);
                if (m.finMin > t) T = Math.min(T, m.finMin);
            }
            Pedido prox = siguientePlazo();
            if (prox != null) T = Math.min(T, prox.horaLimite() * 60 + TOLERANCIA_MIN);
            if (cfg.almacenesIntermedios) T = Math.min(T, proximaReposicion);
            T = Math.max(T, t);
            cerrojo.unlock();   // la instantánea puede leerse mientras se espera
            boolean completo;
            try {
                completo = cfg.reloj.esperarHasta(T);
            } finally {
                cerrojo.lock();
            }
            if (!completo || !averiasExternas.isEmpty() || !pedidosExternos.isEmpty() || !pedidosProgramados.isEmpty() || detenerSolicitado) {
                Thread.interrupted();
                T = Math.max(t, Math.min(T, cfg.reloj.ahora(T)));   // se atiende en el instante real actual
            }
            t = T;
            tActual = t;
            if (detenerSolicitado) {
                aplicarEventosHasta(t);
                res.censurada = !colapsoRegistrado;
                if (res.censurada) res.colapsoMin = t;
                res.finMin = t;
                registrar(new Evt(Evt.T.REPLANIFICACION, t, 0, 0, List.of(), null, t, 0), "", "simulación detenida desde fuera");
                break;
            }

            // 2. Eventos de las unidades hasta T (entregas, cargas, trasvases, nodos)
            aplicarEventosHasta(t);

            // 3. Plazos: colapso (P3)
            if (verificarColapso(t) && (cfg.detenerEnColapso || cfg.escenario != Escenario.SIM_5D)) {
                res.finMin = res.colapsoMin;
                break;
            }
            if (t >= fin) {
                res.censurada = !colapsoRegistrado;
                if (res.censurada) res.colapsoMin = fin;
                res.finMin = fin;
                break;
            }

            // 4. Transiciones que disparan replanificación inmediata
            boolean replanEvento = false;
            for (Unidad un : unidades) {
                if (un.encuentroMin <= t) {   // bloqueo encontrado en el camino
                    un.eventos.clear();
                    un.encuentroMin = Double.POSITIVE_INFINITY;
                    un.ocupadoHasta = t;
                    un.vioBloqueo = true;
                    res.bloqueosEncontrados++;
                    registrar(new Evt(Evt.T.BLOQUEO, t, un.x, un.y, List.of(), null, t, 0), un.u.codigo,
                            "bloqueo en el siguiente nodo; se replanifica");
                    replanEvento = true;
                }
            }
            while (iAveria < averiasArchivo.size() && averiasArchivo.get(iAveria).tiempoMin <= t) {
                replanEvento |= aplicarAveria(averiasArchivo.get(iAveria++), t);
            }
            Object[] externa;
            while ((externa = averiasExternas.poll()) != null) {   // registradas desde el visualizador
                replanEvento |= aplicarAveria(new Averia(t, (String) externa[0], (Integer) externa[1]), t);
            }
            while (siguienteUrgente < llegadasUrgentes.size() && llegadasUrgentes.get(siguienteUrgente) <= t) {
                siguienteUrgente++;   // llegó un pedido urgente: se replanifica ya (evento)
                replanEvento = true;
            }
            Pedido nuevo;
            while ((nuevo = pedidosExternos.poll()) != null) {   // registrados desde el visualizador
                nuevo.horaRegistro = t / 60.0;
                // Se suma a los pedidos por llegar con su hora de registro. Sin bloques se planifica ahora
                // mismo; con bloques (también en la operación día a día, etapa 35), solo si es urgente;
                // si no, entra al plan del bloque siguiente (o se inserta al empezar, si llegó después del corte)
                int i = porLlegar.size();
                while (i > siguiente && porLlegar.get(i - 1).horaRegistro > nuevo.horaRegistro) i--;
                porLlegar.add(i, nuevo);
                registrar(new Evt(Evt.T.REPLANIFICACION, t, nuevo.x, nuevo.y, List.of(nuevo.id), null, t, nuevo.cantidad), "",
                        "pedido " + nuevo.idOriginal + " registrado: " + nuevo.cantidad + " paq. a (" + nuevo.x + ","
                                + nuevo.y + "), plazo " + nuevo.plazoMaximoHoras + " h");
                replanEvento |= cfg.bloqueMin <= 0 || esUrgente(nuevo, t);
            }
            while ((nuevo = pedidosProgramados.poll()) != null) programar(nuevo, t);   // carga masiva (etapa 35)
            replanEvento |= aplicarTransiciones(t);

            // 5. Replanificación periódica (cada Sa, o al empezar cada bloque) o por evento. Resguardo:
            //    si los eventos piden replanificar una y otra vez en el mismo instante, se omite y se registra.
            boolean periodica = cfg.bloqueMin > 0 ? t >= proximoBloque : t >= proximaReplan;
            if (replanEvento && !periodica) {
                repeticionesEnInstante = (t == ultimaReplanEvento) ? repeticionesEnInstante + 1 : 0;
                ultimaReplanEvento = t;
                if (repeticionesEnInstante > 3) {
                    replanEvento = false;
                    registrar(new Evt(Evt.T.REPLANIFICACION, t, 0, 0, List.of(), null, t, 0), "",
                            "omitida: replanificación repetida en el mismo instante");
                }
            }
            if (cfg.bloqueMin > 0) {
                planificarPorBloques(t, replanEvento);
            } else if (periodica || replanEvento) {
                if (!periodica) res.replanPorEvento++;
                replanificar(t, t);
                while (proximaReplan <= t) proximaReplan += cfg.saMin;
            }
        }
        } finally {
            terminada = true;
            cerrarMetricas();
            if (ejecutor != null) ejecutor.shutdownNow();
            cerrojo.unlock();
        }
    }

    /**
     * Cambios de estado programados en t: una averiada deja el lugar o vuelve a estar
     * disponible, empieza o termina un mantenimiento, se reponen los intermedios.
     * Devuelve true si hay que replanificar ya: siempre tras una avería; tras un
     * mantenimiento, solo sin bloques (con bloques, la unidad entra en el bloque siguiente).
     */
    private boolean aplicarTransiciones(double t) {
        boolean replan = false;
        for (Unidad un : unidades) {
            if (un.estado != EstadoUnidad.AVERIADA) continue;
            if (!un.averiaDejoLugar && un.averia.enLugarHastaMin() <= t) {
                un.averiaDejoLugar = true;
                replan = true;
                if (un.averia.tipo == 1) {
                    reactivar(un, t, true);
                } else {
                    // pasa "instantáneamente" al central; lo no trasvasado vuelve al stock del central
                    for (String id : new ArrayList<>(un.aBordo)) {
                        estado.put(id, "P");
                        escala(id, t, "DEVUELTO", Compartido.ALMACEN_CENTRAL.codigo, un, Compartido.ALMACEN_CENTRAL.x, Compartido.ALMACEN_CENTRAL.y);
                    }
                    un.aBordo.clear();
                    un.x = Compartido.ALMACEN_CENTRAL.x;
                    un.y = Compartido.ALMACEN_CENTRAL.y;
                    registrar(new Evt(Evt.T.AVERIA, t, un.x, un.y, List.of(), null, t, 0), un.u.codigo,
                            "deja el lugar y pasa al central");
                }
            } else if (un.averiaDejoLugar && un.averia.disponibleDesdeMin() <= t) {
                reactivar(un, t, false);
                replan = true;
            }
        }
        for (Mantenimiento m : cfg.mantenimientos) {
            Unidad un = unidadPorCodigo.get(m.unidad);
            if (un == null) continue;
            if (m.inicioMin <= t && t < m.finMin && un.estado == EstadoUnidad.OPERATIVA) {
                un.estado = EstadoUnidad.MANTENIMIENTO;
                un.eventos.clear();
                un.rutaVigente.clear();
                for (String id : un.aBordo) estado.put(id, "P");   // no debería ocurrir: el plan lo evita
                un.aBordo.clear();
                un.x = Compartido.ALMACEN_CENTRAL.x;
                un.y = Compartido.ALMACEN_CENTRAL.y;
            } else if (m.finMin <= t && un.estado == EstadoUnidad.MANTENIMIENTO) {
                un.estado = EstadoUnidad.OPERATIVA;
                un.ocupadoHasta = t;
                replan |= cfg.bloqueMin <= 0;
            }
        }
        if (cfg.almacenesIntermedios && t >= proximaReposicion) {
            stock[1] = stock[2] = Compartido.STOCK_INTERMEDIO;
            proximaReposicion += 1440;
        }
        return replan;
    }

    // ============================ Planificación por bloques (Etapa 33, SI-30) ============================

    /** Próximo instante de la planificación por bloques: el corte del próximo bloque (si aún
     *  no se planificó con adelanto) o su inicio. */
    private double proximoInstanteDeBloque() {
        return adelantoLanzado ? proximoBloque : proximoBloque - cfg.adelantoMin;
    }

    /**
     * Paso 5 con bloques. Al llegar el corte (inicio del bloque menos el adelanto) se lanza
     * la planificación del bloque sobre la copia proyectada; al llegar su inicio, se aplica.
     * Una excepción replanifica en el instante y descarta el plan adelantado, que se vuelve
     * a lanzar desde ese estado si ya pasó el corte.
     */
    private void planificarPorBloques(double t, boolean excepcion) {
        boolean inicio = t >= proximoBloque;
        if (excepcion) {
            descartarAdelanto();   // ya no sirve: el estado cambió (no se lo espera)
            adelantoLanzado = false;
            if (!inicio) res.replanPorEvento++;
            ultimaExcepcion = t;
            replanificar(t, t);
        } else if (inicio) {
            aplicarBloque(t);
        }
        if (inicio) {
            res.bloques++;
            while (proximoBloque <= t) proximoBloque += cfg.bloqueMin;
            adelantoLanzado = false;
        }
        if (!adelantoLanzado && t >= proximoBloque - cfg.adelantoMin) lanzarAdelanto(t);
    }

    /**
     * Corte del próximo bloque: se proyecta el estado a su inicio en una copia y se lo
     * planifica con lo que llegó hasta ahora (en otro hilo si adelantoEnParalelo). Los
     * cambios de velocidad pendientes rigen desde este bloque (P16).
     */
    private void lanzarAdelanto(double t) {
        adelantoLanzado = true;
        leerPedidos(proximoBloque);   // la copia necesita saber si antes del bloque llega un pedido urgente
        aplicarVelocidadesPendientes(t);
        Simulador copia = new Simulador(this);
        double inicioBloque = proximoBloque;
        if (cfg.adelantoEnParalelo) {
            // Un hilo por plan adelantado (Etapa 35): si una excepción descarta uno que se está calculando,
            // termina en segundo plano y no retrasa al siguiente ni a la replanificación en el instante
            if (ejecutor == null) ejecutor = Executors.newCachedThreadPool(r -> {
                Thread h = new Thread(r, "planificador-bloques");
                h.setDaemon(true);
                return h;
            });
            long presupuesto = presupuestoAdelanto(inicioBloque - t);
            pendiente = ejecutor.submit(() -> copia.planificarProyectado(inicioBloque, t, presupuesto));
        } else {
            pendiente = CompletableFuture.completedFuture(copia.planificarProyectado(inicioBloque, t, presupuestoAdelanto(inicioBloque - t)));
        }
    }

    /**
     * En la COPIA: sigue el plan vigente hasta el inicio del bloque y planifica desde ese
     * estado proyectado, con los pedidos que llegaron hasta el corte.
     */
    /**
     * Presupuesto (ms) del plan de un bloque que empieza dentro de faltanMin minutos simulados: el del bloque
     * (taBloqueMs, o Ta). Con reloj escalado (visualizador), como mucho fraccionTaBloque del tiempo real que
     * falta cuando se lanza después del corte (tras una excepción), para que esté listo a tiempo; al menos 0.5 s.
     * Lanzado en el corte es taBloqueMs, como antes; sin espera (experimento) no cambia.
     */
    private long presupuestoAdelanto(double faltanMin) {
        long base = cfg.taBloqueMs > 0 ? cfg.taBloqueMs : cfg.taMs;
        if (cfg.factorReloj <= 0 || faltanMin >= cfg.adelantoMin - 1e-9) return base;   // sin espera, o lanzado en el corte
        long restante = (long) (cfg.fraccionTaBloque * faltanMin / cfg.factorReloj * 60_000);
        return Math.min(base, Math.max(500, restante));
    }

    private Adelanto planificarProyectado(double inicioBloque, double corte, long presupuestoMs) {
        Adelanto a = new Adelanto(inicioBloque, corte);
        if (!avanzarProyeccion(inicioBloque)) return a;
        Preparacion pr = preparar(inicioBloque, corte);
        if (pr == null) return a;
        try {
            a.firma = pr.firma;
            a.cx = pr.cx;
            long t0 = System.nanoTime();
            a.plan = calcular(pr, presupuestoMs);
            a.ms = (System.nanoTime() - t0) / 1e6;
            if (cfg.adelantoEnParalelo) {
                // Solo para el visualizador (Etapa 35): la COPIA aplica el plan y guarda las líneas de tiempo
                // del bloque siguiente, que el visualizador tiene en cola. No cambia el plan ni el estado real.
                aplicarPlan(inicioBloque, pr, a.plan, a.cx, a.ms, "");
                a.posiciones = new HashMap<>();
                for (Unidad un : unidades) a.posiciones.put(un.u.codigo, new int[]{un.x, un.y});
                a.trayectorias = trayectorias(inicioBloque + 2 * cfg.bloqueMin);
            }
        } finally {
            Contexto.restablecer();
        }
        return a;
    }

    /**
     * En la COPIA: aplica la línea de tiempo comprometida (entregas, cargas, nodos,
     * mantenimientos, reposición) hasta tk. Devuelve false si antes de tk ocurrirá una
     * excepción (avería, bloqueo en el camino, pedido urgente), porque replanificará igual
     * y el bloque se volverá a planificar desde ese estado.
     */
    private boolean avanzarProyeccion(double tk) {
        for (Unidad un : unidades) {
            if (un.encuentroMin <= tk) return false;
            if (un.estado == EstadoUnidad.AVERIADA
                    && (un.averiaDejoLugar ? un.averia.disponibleDesdeMin() : un.averia.enLugarHastaMin()) <= tk) return false;
        }
        if (iAveria < averiasArchivo.size() && averiasArchivo.get(iAveria).tiempoMin <= tk) return false;
        while (siguienteUrgente < llegadasUrgentes.size() && llegadasUrgentes.get(siguienteUrgente) <= tActual) siguienteUrgente++;
        if (siguienteUrgente < llegadasUrgentes.size() && llegadasUrgentes.get(siguienteUrgente) <= tk) return false;
        double t = tActual;
        while (t < tk) {
            double T = tk;
            for (Unidad un : unidades) if (!un.eventos.isEmpty()) T = Math.min(T, un.eventos.peek().min);
            for (Mantenimiento m : cfg.mantenimientos) {
                if (m.inicioMin > t) T = Math.min(T, m.inicioMin);
                if (m.finMin > t) T = Math.min(T, m.finMin);
            }
            if (cfg.almacenesIntermedios) T = Math.min(T, proximaReposicion);
            t = Math.max(T, t);
            aplicarEventosHasta(t);
            aplicarTransiciones(t);
        }
        tActual = tk;
        return true;
    }

    /**
     * Inicio de un bloque: si el plan adelantado partió de un estado igual al real (misma
     * firma), se aplica tal cual; si no (o no hubo adelanto), se planifica en el instante
     * con los pedidos que llegaron hasta el corte.
     */
    private void aplicarBloque(double t) {
        Adelanto a = recogerAdelanto();
        if (a != null && a.plan != null && a.inicioBloque == t) {
            Preparacion pr = preparar(t, a.corte);
            boolean aplicado = false;
            try {
                if (pr != null && pr.firma.equals(a.firma)) {
                    res.bloquesConAdelanto++;
                    aplicarPlan(t, pr, a.plan, a.cx, a.ms, ", planificado con adelanto desde " + formatear(a.corte));
                    aplicado = true;
                } else {
                    registrar(new Evt(Evt.T.REPLANIFICACION, t, 0, 0, List.of(), null, t, 0), "",
                            "el estado proyectado no coincidió con el real: se planifica en el instante");
                }
            } finally {
                Contexto.restablecer();
            }
            if (aplicado) {
                insertarLlegadasTardias(t);
                return;
            }
        }
        replanificar(t, t);   // sin plan adelantado: se planifica en el instante con todo lo llegado
    }

    /**
     * Pedidos que llegaron entre el corte y el inicio del bloque: se insertan en el plan recién
     * aplicado con la misma inserción que repara el plan vigente (milisegundos, sin correr el
     * algoritmo), y el bloque siguiente los optimiza con el resto. Así un pedido espera a lo sumo
     * un bloque para tener unidad; sin esto esperaba un bloque más el adelanto, y con la flota
     * cerca de su capacidad eso basta para colapsar (etapa 33, datos oficiales).
     */
    private void insertarLlegadasTardias(double t) {
        if (siguiente >= porLlegar.size() || porLlegar.get(siguiente).horaRegistro * 60 > t) return;
        long t0 = System.nanoTime();
        Preparacion pr = preparar(t, t);
        if (pr == null) return;
        try {
            if (pr.cx.planBase == null) return;   // sin plan vigente que completar (solo antes del primer plan)
            aplicarPlan(t, pr, Planificador.Plan.de(pr.cx.planBase, 0, 0), pr.cx, (System.nanoTime() - t0) / 1e6,
                    ", inserción de los pedidos llegados después del corte");
        } finally {
            Contexto.restablecer();
        }
    }

    /** Descarta el plan adelantado pendiente sin esperarlo (Etapa 35): una excepción cambió el estado, así que
     *  ese plan ya no sirve. Antes se lo esperaba (hasta el presupuesto del bloque) y la pantalla quedaba detenida. */
    private void descartarAdelanto() {
        if (pendiente == null) return;
        pendiente.cancel(false);
        pendiente = null;
    }

    /** Espera (sin retener el estado) el plan adelantado pendiente y lo devuelve; null si no hay. */
    private Adelanto recogerAdelanto() {
        if (pendiente == null) return null;
        Future<Adelanto> f = pendiente;
        pendiente = null;
        boolean tarde = !f.isDone();
        if (tarde) res.bloquesTardios++;   // el planificador tardó más que el adelanto
        boolean interrumpido = false;
        long r0 = System.currentTimeMillis();
        pausado = tarde;
        try {
            while (true) {
                cerrojo.unlock();   // la instantánea puede leerse mientras se espera
                try {
                    return f.get();
                } catch (InterruptedException e) {
                    interrumpido = true;   // evento externo: se atiende en la siguiente vuelta
                } finally {
                    cerrojo.lock();
                }
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException("Falló la planificación con adelanto", e.getCause());
        } finally {
            if (tarde) {   // la simulación esperó al planificador: ese tiempo real no es tiempo simulado (SI-32)
                pausado = false;
                cfg.reloj.pausa(System.currentTimeMillis() - r0);
            }
            if (interrumpido) Thread.currentThread().interrupt();
        }
    }

    // ============================ Eventos ============================

    private void aplicarEventosHasta(double t) {
        while (true) {
            Unidad elegida = null;
            for (Unidad un : unidades) {
                Evt e = un.eventos.peek();
                if (e != null && e.min <= t && (elegida == null || e.min < elegida.eventos.peek().min)) elegida = un;
            }
            if (elegida == null) return;
            aplicar(elegida, elegida.eventos.poll());
        }
    }

    private void aplicar(Unidad un, Evt e) {
        switch (e.tipo) {
            case NODO:
                if (Math.abs(e.x - un.x) + Math.abs(e.y - un.y) != 1) res.saltos++;   // debe avanzar a un vecino
                un.x = e.x;
                un.y = e.y;
                un.km += 1;
                res.kmAcumulados += 1;
                res.costoAcumulado += un.u.tipo.costoPorKilometro;
                if (!proyeccion) un.pisar(e.min, e.x, e.y);
                break;
            case SALIDA:
                un.salidaMin = e.min;   // sale del origen (seguimiento, etapa 35)
                un.kmEnSalida = un.km;
                registrar(e, un.u.codigo, "hacia (" + e.cantidad / 1000 + "," + e.cantidad % 1000 + ")");
                break;
            case ENTREGA:
                for (String id : e.entregas) {
                    if (!("U:" + un.u.codigo).equals(estado.get(id))) continue;
                    estado.put(id, "E");
                    entregadaEn.put(id, e.min);
                    if (!proyeccion) entregadoPor.put(id, un.u.codigo);
                    escala(id, e.min, "ENTREGA", "cliente (" + e.x + "," + e.y + ")", un, e.x, e.y);
                    un.aBordo.remove(id);
                    if (e.min > entregas.get(id).horaLimite() * 60 + TOLERANCIA_MIN) res.entregasTarde++;
                }
                un.ocupadoHasta = e.finMin;
                un.rutaVigente.remove(e.parada);
                llegoA(un, "cliente (" + e.x + "," + e.y + ")", e);
                registrar(e, un.u.codigo, "");
                break;
            case RECARGA: {
                int cargados = 0;
                for (String id : e.entregas) {
                    if (!"P".equals(estado.get(id))) continue;
                    estado.put(id, "U:" + un.u.codigo);
                    un.aBordo.add(id);
                    cargados += entregas.get(id).cantidad;
                    if (!proyeccion) {
                        origenDe.put(id, e.parada.almacen.codigo);
                        cargadoEn.put(id, e.min);
                    }
                    escala(id, e.min, "CARGA", e.parada.almacen.codigo, un, e.x, e.y);
                }
                int a = indiceAlmacen(e.parada.almacen);
                if (a > 0) stock[a] = Math.max(0, stock[a] - cargados);
                if (cargados > 0) {
                    un.viajes++;
                    un.ultimaRecarga = e.parada.almacen.codigo;
                }
                un.ocupadoHasta = e.finMin;
                un.rutaVigente.remove(e.parada);
                llegoA(un, e.parada.almacen.codigo, e);
                registrar(e, un.u.codigo, e.parada.almacen.codigo + " carga " + cargados);
                break;
            }
            case TRASVASE: {
                int movidos = 0;
                Unidad averiada = unidadPorCodigo.get(e.parada.unidadAveriada);
                for (String id : e.entregas) {
                    if (!("A:" + e.parada.unidadAveriada).equals(estado.get(id))) continue;
                    estado.put(id, "U:" + un.u.codigo);
                    un.aBordo.add(id);
                    if (averiada != null) averiada.aBordo.remove(id);
                    escala(id, e.min, "TRASVASE", "desde " + e.parada.unidadAveriada, un, e.x, e.y);
                    movidos += entregas.get(id).cantidad;
                }
                if (movidos > 0) res.trasvases++;
                un.ocupadoHasta = e.finMin;
                un.rutaVigente.remove(e.parada);
                llegoA(un, "trasvase de " + e.parada.unidadAveriada, e);
                registrar(e, un.u.codigo, "desde " + e.parada.unidadAveriada + ": " + movidos + " paquetes");
                break;
            }
            case FIN:
                un.x = e.x;
                un.y = e.y;
                un.ocupadoHasta = e.min;
                Almacen alm = almacenEn(e.x, e.y);
                llegoA(un, alm != null ? alm.codigo : "(" + e.x + "," + e.y + ")", e);
                registrar(e, un.u.codigo, "llega al almacén");
                break;
            default:
                break;
        }
    }

    /** Seguimiento (etapa 35): la unidad llegó a una parada, que pasa a ser su origen; aún no sale de ella. */
    private static void llegoA(Unidad un, String lugar, Evt e) {
        un.origenLugar = lugar;
        un.origenX = e.x;
        un.origenY = e.y;
        un.origenLlegadaMin = e.min;
        un.salidaMin = Double.NaN;
    }

    private static Almacen almacenEn(int x, int y) {
        for (Almacen a : new Almacen[]{Compartido.ALMACEN_CENTRAL, Compartido.ALMACEN_NOROESTE, Compartido.ALMACEN_ESTE})
            if (a.x == x && a.y == y) return a;
        return null;
    }

    private static int indiceAlmacen(Almacen a) {
        return a == Compartido.ALMACEN_CENTRAL ? 0 : a == Compartido.ALMACEN_NOROESTE ? 1 : 2;
    }

    // ============================ Colapso ============================

    private Pedido siguientePlazo() {
        while (!plazos.isEmpty()) {
            String id = plazos.peek().id;
            String est = estado.get(id);
            if ("E".equals(est) || "X".equals(est) || vencidas.contains(id)) plazos.poll();
            else return plazos.peek();
        }
        return null;
    }

    /** true si en t hay (o ya hubo) colapso. Solo el primero se registra; en
     *  SIM_5D sin detención, los siguientes vencimientos se cuentan como tarde. */
    /** Tolerancia numérica (min): una entrega que llega en el mismo instante que la hora
     *  límite (salvo error de redondeo en la conversión horas-minutos) cuenta como a tiempo.
     *  Sin ella, un plan con holgura exactamente 0 colapsaba por 1e-12 min (Etapa 22). */
    static final double TOLERANCIA_MIN = 1e-6;

    /** true si en el minuto t ya pasó la hora límite (con la tolerancia numérica). */
    static boolean vencido(double limiteMin, double t) {
        return limiteMin + TOLERANCIA_MIN <= t;
    }

    private boolean verificarColapso(double t) {
        boolean hay = false;
        Pedido p;
        while ((p = siguientePlazo()) != null && vencido(p.horaLimite() * 60, t)) {
            plazos.poll();
            if (!colapsoRegistrado) {
                colapsoRegistrado = true;
                res.colapsoMin = p.horaLimite() * 60;
                res.pedidoColapso = p.idOriginal + " (" + p.id + ")";
                String est = estado.get(p.id);
                res.unidadColapso = est.length() > 2 ? est.substring(2) : "";
                res.causaColapso = causa(p, est);
                registrar(new Evt(Evt.T.COLAPSO, res.colapsoMin, p.x, p.y, List.of(p.id), null, res.colapsoMin, 0),
                        res.unidadColapso, res.causaColapso);
            }
            vencidas.add(p.id);   // sigue en su estado y se sigue planificando (llegará tarde)
            hay = true;
        }
        return hay || colapsoRegistrado;
    }

    private String causa(Pedido p, String est) {
        if (est.startsWith("A:")) return "a bordo de unidad averiada";
        // Destino bloqueado en la hora límite: con el supuesto de esperar al desbloqueo,
        // nadie puede entregar a tiempo (colapso atribuible a los datos, no al algoritmo)
        if (cfg.mapa != null && cfg.mapa.bloqueado(p.x, p.y, p.horaLimite())) return "destino bloqueado";
        if (est.startsWith("U:")) {
            Unidad un = unidadPorCodigo.get(est.substring(2));
            return un.vioBloqueo ? "bloqueo" : "llegada tardía (a bordo)";
        }
        for (Unidad un : unidades) {
            for (ParadaAlg pa : un.rutaVigente) {
                if (pa.tipo == TipoParada.ENTREGA && pa.pedido.id.equals(p.id))
                    return un.vioBloqueo ? "bloqueo" : "llegada tardía (planificada)";
            }
        }
        int capMax = 0;
        for (Unidad un : unidades) if (un.estado == EstadoUnidad.OPERATIVA) capMax = Math.max(capMax, un.u.tipo.capacidadMaxima);
        return p.cantidad > capMax ? "capacidad" : "sin asignar";
    }

    // ============================ Averías ============================

    /** Aplica la avería si la unidad está operativa; devuelve true si hay que replanificar. */
    private boolean aplicarAveria(Averia a, double t) {
        Unidad un = unidadPorCodigo.get(a.unidad);
        if (un == null || un.estado != EstadoUnidad.OPERATIVA) return false;
        un.estado = EstadoUnidad.AVERIADA;
        un.averia = a;
        un.averiaDejoLugar = false;
        un.eventos.clear();
        un.encuentroMin = Double.POSITIVE_INFINITY;
        un.rutaVigente.clear();
        for (String id : un.aBordo) {
            estado.put(id, "A:" + un.u.codigo);
            escala(id, t, "AVERIA", "avería tipo " + a.tipo, un, un.x, un.y);
        }
        res.averiasAplicadas++;
        registrar(new Evt(Evt.T.AVERIA, t, un.x, un.y, new ArrayList<>(un.aBordo), null, t, 0), un.u.codigo,
                "tipo " + a.tipo + ", en el lugar hasta " + formatear(a.enLugarHastaMin())
                        + ", disponible desde " + formatear(a.disponibleDesdeMin()));
        return true;
    }

    private void reactivar(Unidad un, double t, boolean conCarga) {
        un.estado = EstadoUnidad.OPERATIVA;
        un.ocupadoHasta = t;
        if (conCarga) for (String id : un.aBordo) estado.put(id, "U:" + un.u.codigo);
        un.averia = null;
    }

    // ============================ Replanificación ============================

    /** Llegada de una entrega: pasa a pendiente (en almacén) y su plazo entra a la cola del colapso. */
    private void registrarLlegada(Pedido p, double t) {
        if (cfg.mapa != null && cfg.reglaDestino == Contexto.ReglaDestino.NO_EVALUABLE
                && cfg.mapa.bloqueadoDurante(p.x, p.y, p.horaRegistro, p.horaLimite())) {
            // Destino bloqueado desde el registro hasta la hora límite: nadie puede cumplir.
            // Se excluye del colapso y se cuenta aparte (Etapa 17.1).
            if (inentregables.add(p.idOriginal)) res.pedidosInentregablesBloqueo++;
            registrar(new Evt(Evt.T.BLOQUEO, t, p.x, p.y, List.of(p.id), null, t, 0), "",
                    "pedido inentregable por bloqueo (excluido del colapso)");
            return;
        }
        entregas.put(p.id, p);
        estado.put(p.id, "P");
        partesDe.computeIfAbsent(p.idOriginal, k -> new ArrayList<>()).add(p.id);
        registroDe.putIfAbsent(p.idOriginal, new double[]{p.horaRegistro * 60, p.horaLimite() * 60});
        plazos.add(p);
    }

    /** Planifica en el instante t con los pedidos que llegaron hasta "corte" y aplica el plan. */
    private void replanificar(double t, double corte) {
        // Etapa 35 (SI-32): mientras se planifica en el instante la simulación está detenida; al terminar,
        // el reloj descuenta ese tiempo real para no saltar hacia adelante (solo con ritmo escalado).
        long r0 = System.currentTimeMillis();
        pausado = true;
        aplicarVelocidadesPendientes(t);
        Preparacion pr = preparar(t, corte);
        if (pr == null) {   // nada que cambiar: sigue el plan vigente (no hubo pausa que descontar)
            pausado = false;
            return;
        }
        try {
            try {
                long t0 = System.nanoTime();
                Planificador.Plan plan;
                cerrojo.unlock();   // la instantánea puede leerse mientras el planificador trabaja
                try {
                    plan = calcular(pr, cfg.taMs);
                } finally {
                    cerrojo.lock();
                }
                aplicarPlan(t, pr, plan, pr.cx, (System.nanoTime() - t0) / 1e6, "");
            } finally {
                Contexto.restablecer();
            }
        } finally {
            pausado = false;
            cfg.reloj.pausa(System.currentTimeMillis() - r0);
        }
    }

    /** Cambios de velocidad en caliente: rigen desde la planificación que empieza ahora (P16). */
    private void aplicarVelocidadesPendientes(double t) {
        Object[] v;
        while ((v = velocidadesPendientes.poll()) != null) {
            velocidades.put((TipoUnidad) v[0], (Double) v[1]);
            registrar(new Evt(Evt.T.REPLANIFICACION, t, 0, 0, List.of(), null, t, 0), "",
                    "velocidad de " + v[0] + " = " + v[1] + " km/h");
        }
    }

    /**
     * Arma lo que recibe el planificador en t: registra los pedidos que llegaron hasta
     * "corte", toma el estado de cada unidad operativa y repara el plan vigente. Deja
     * activo su Contexto (quien llama lo restablece). null si no hay nada que planificar.
     */
    private Preparacion preparar(double t, double corte) {
        // Llegada de pedidos
        while (siguiente < porLlegar.size() && porLlegar.get(siguiente).horaRegistro * 60 <= corte)
            registrarLlegada(porLlegar.get(siguiente++), t);
        List<Unidad> operativas = new ArrayList<>();
        for (Unidad un : unidades) {
            if (un.estado == EstadoUnidad.OPERATIVA && !enMantenimiento(un, t)) operativas.add(un);
        }
        dividirUrgentes(t, operativas);

        // Contexto del ciclo
        Contexto cx = new Contexto();
        cx.instanteBaseH = t / 60.0;
        cx.mapa = cfg.mapa;
        cx.reglaDestino = cfg.reglaDestino;
        cx.penalidadCambio = cfg.penalidadEstabilidad;
        cx.holguraH = cfg.holguraMin / 60.0;
        cx.penalidadHolgura = cfg.penalidadHolgura;
        cx.alimentacion = cfg.alimentacion;
        cx.reparto = cfg.reparto;
        cx.velocidades.putAll(velocidades);   // cambios en caliente de ESTA simulación (P16)
        cx.conEstado = true;
        if (cfg.almacenesIntermedios) {
            cx.almacenes.add(new Contexto.AlmacenPlan(Compartido.ALMACEN_NOROESTE, stock[1]));
            cx.almacenes.add(new Contexto.AlmacenPlan(Compartido.ALMACEN_ESTE, stock[2]));
        }
        Map<Unidad, Evt> compromiso = new HashMap<>();
        List<UnidadTransporte> disponibles = new ArrayList<>();
        for (Unidad un : operativas) {
            Evt prox = un.eventos.peek();
            Contexto.Inicio ini;
            if (prox != null && prox.tipo == Evt.T.NODO && prox.min > t) {   // viajando: llega al siguiente nodo
                ini = new Contexto.Inicio(prox.x, prox.y, (prox.min - t) / 60.0, noDisponibleDesde(un, t));
                compromiso.put(un, prox);
            } else {
                ini = new Contexto.Inicio(un.x, un.y, Math.max(0, un.ocupadoHasta - t) / 60.0, noDisponibleDesde(un, t));
            }
            cx.inicios.put(un.u.codigo, ini);
            disponibles.add(un.u);
        }
        for (Unidad un : unidades) {
            if (un.estado == EstadoUnidad.AVERIADA && !un.averiaDejoLugar && !un.aBordo.isEmpty())
                cx.averiadas.put(un.u.codigo, new Contexto.Averiada(un.u.codigo, un.x, un.y,
                        (un.averia.enLugarHastaMin() - t) / 60.0));
        }
        // Entregas a planificar (copias relativas al instante)
        List<Pedido> aPlanificar = new ArrayList<>();
        for (Map.Entry<String, String> e : estado.entrySet()) {
            String est = e.getValue();
            Pedido abs = entregas.get(e.getKey());
            Pedido rel;
            if (est.equals("P")) {
                rel = abs.copiaRelativa(t / 60.0);
            } else if (est.startsWith("U:") && cx.inicios.containsKey(est.substring(2))) {
                rel = abs.copiaRelativa(t / 60.0);
                rel.aBordoDe = est.substring(2);
            } else if (est.startsWith("A:") && cx.averiadas.containsKey(est.substring(2))) {
                rel = abs.copiaRelativa(t / 60.0);
                rel.enAveriada = est.substring(2);
            } else {
                continue;
            }
            aPlanificar.add(rel);
        }
        aPlanificar.sort(Comparator.comparing(p -> p.id));   // orden estable (reproducible)
        if (aPlanificar.isEmpty() || disponibles.isEmpty()) return null;

        cx.partesCreadas = partesCreadas;   // ids de partes únicos en toda la simulación (SI-28)
        Contexto.usar(cx);
        // Estabilidad (Etapa 18): el costo de cambiar entregas de unidad se mide contra el plan vigente
        if (!primerPlan) cx.asignacionVigente = asignacionAnterior;
        cx.planBase = primerPlan ? null : planVigenteReparado(operativas, aPlanificar, disponibles);
        primerPlan = false;
        if (cx.planBase != null) {
            // La reparación pudo repartir pedidos nuevos (SI-28): ambos algoritmos
            // parten de esas mismas partes
            adoptarReparto(cx.planBase.todasLasEntregas(), t);
            aPlanificar = cx.planBase.todasLasEntregas();
            aPlanificar.sort(Comparator.comparing(p -> p.id));
        }
        Preparacion pr = new Preparacion();
        pr.cx = cx;
        pr.operativas = operativas;
        pr.compromiso = compromiso;
        pr.disponibles = disponibles;
        pr.aPlanificar = aPlanificar;
        pr.semilla = cfg.semilla * 1_000_003L + ciclo++;
        pr.firma = firma(t, pr);
        return pr;
    }

    /** Llama al algoritmo (Búsqueda Tabú en la solución) con el presupuesto indicado (Ta). */
    private Planificador.Plan calcular(Preparacion pr, long presupuestoMs) {
        return planificador.planificar(new Planificador.EstadoPlanificacion(
                pr.cx, pr.aPlanificar, pr.disponibles, presupuestoMs, cfg.maxEvaluaciones, pr.semilla));
    }

    /**
     * Resumen del estado de partida del planificador: instante, punto y hora de partida de
     * cada unidad, entregas (y dónde están), stock y plan vigente reparado. Si la copia
     * proyectada y el simulador real dan la misma firma, el plan de la copia vale para el real.
     */
    private static String firma(double t, Preparacion pr) {
        StringBuilder f = new StringBuilder(String.format(Locale.US, "%.6f", t));
        for (UnidadTransporte u : pr.disponibles) {
            Contexto.Inicio i = pr.cx.inicios.get(u.codigo);
            f.append(String.format(Locale.US, "|%s@%d,%d,%.6f,%.6f", u.codigo, i.x, i.y, i.inicioH, i.noDisponibleDesdeH));
        }
        for (Pedido p : pr.aPlanificar) f.append('|').append(p.id).append(':').append(p.cantidad).append(':')
                .append(p.aBordoDe).append(':').append(p.enAveriada);
        for (Contexto.AlmacenPlan a : pr.cx.almacenes) f.append("|").append(a.almacen.codigo).append('=').append(a.stock);
        for (Contexto.Averiada a : pr.cx.averiadas.values()) f.append("|").append(a.codigo).append('@').append(a.x).append(',').append(a.y);
        if (pr.cx.planBase != null)
            for (RutaAlg r : pr.cx.planBase.rutas) {
                f.append("|ruta ").append(r.unidad.codigo).append(':');
                for (ParadaAlg pa : r.paradas) f.append(pa.id()).append(',');
            }
        return f.toString();
    }

    /**
     * Aplica el plan en t: adopta el reparto de productos, reemplaza la línea de tiempo de
     * cada unidad operativa (la que viaja entre dos nodos llega primero al siguiente) y
     * registra la replanificación. cxPlan es el contexto en que se calculó el plan.
     */
    private void aplicarPlan(double t, Preparacion pr, Planificador.Plan plan, Contexto cxPlan, double ms, String nota) {
        Contexto.usar(cxPlan);   // el planificador pudo cambiarlo
        partesCreadas = cxPlan.partesCreadas;
        if (!proyeccion) {
            planVersion++;
            planAplicadoMin = t;
        }
        List<Pedido> entregasPlan = new ArrayList<>(plan.sinAsignar);
        for (RutaAlg r : plan.rutas)
            for (ParadaAlg pa : r.paradas) if (pa.tipo == TipoParada.ENTREGA) entregasPlan.add(pa.pedido);
        adoptarReparto(entregasPlan, t);   // partes que creó o unió el planificador (SI-28)
        res.replanificaciones++;
        sumaMs += ms;
        res.planificadorMsMax = Math.max(res.planificadorMsMax, ms);
        res.iteracionesTotales += plan.iteraciones;
        res.evaluacionesTotales += plan.evaluaciones;
        res.aplazamientos += plan.sinAsignar.size();
        for (Unidad un : pr.operativas) {   // se reemplazan las líneas de tiempo por las del plan nuevo
            un.eventos.clear();
            if (pr.compromiso.containsKey(un)) un.eventos.add(pr.compromiso.get(un));
            un.encuentroMin = Double.POSITIVE_INFINITY;
        }
        comprometer(t, cxPlan, plan, pr.operativas);
        registrar(new Evt(Evt.T.REPLANIFICACION, t, 0, 0, List.of(), null, t, 0), "",
                pr.aPlanificar.size() + " entregas, " + pr.disponibles.size() + " unidades, "
                        + plan.sinAsignar.size() + " sin asignar, " + String.format(Locale.US, "%.0f ms", ms) + nota);
    }

    private boolean enMantenimiento(Unidad un, double t) {
        for (Mantenimiento m : cfg.mantenimientos)
            if (m.unidad.equals(un.u.codigo) && m.inicioMin <= t && t < m.finMin) return true;
        return false;
    }

    private double noDisponibleDesde(Unidad un, double t) {
        double prox = Double.POSITIVE_INFINITY;
        for (Mantenimiento m : cfg.mantenimientos)
            if (m.unidad.equals(un.u.codigo) && m.inicioMin > t) prox = Math.min(prox, (m.inicioMin - t) / 60.0);
        return prox;
    }

    /**
     * Entregas parciales flexibles (10.3), estrategia "urgentes": las entregas
     * pendientes de más de tamanoParcial paquetes (lo que cabe en una moto)
     * con plazo dentro de umbralUrgenciaH se ordenan por urgencia; las primeras
     * tantas como autos operativos libres en la próxima hora se dejan enteras y
     * el resto se divide en partes de hasta tamanoParcial paquetes, que pueden
     * ir en unidades distintas de cualquier tipo (cada parte suma 1 h de
     * acondicionamiento). Estrategia "ninguna": solo los bloques de 24.
     */
    /** Hora límite efectiva (absoluta, en horas) de una entrega: la urgencia real (SI-23). */
    private double limiteEfectivoH(Pedido p) {
        return Contexto.limiteEfectivo(cfg.mapa, cfg.reglaDestino, p, 0);
    }

    private void dividirUrgentes(double t, List<Unidad> operativas) {
        if (!"urgentes".equalsIgnoreCase(cfg.estrategiaParciales)) return;
        List<Pedido> urgentes = new ArrayList<>();
        for (Map.Entry<String, String> e : estado.entrySet()) {
            if (!"P".equals(e.getValue())) continue;
            Pedido p = entregas.get(e.getKey());
            if (p.cantidad > cfg.tamanoParcial && limiteEfectivoH(p) * 60 - t <= cfg.umbralUrgenciaH * 60) urgentes.add(p);
        }
        if (urgentes.isEmpty()) return;
        urgentes.sort(Comparator.<Pedido>comparingDouble(this::limiteEfectivoH).thenComparing(p -> p.id));
        int autosLibres = 0;
        for (Unidad un : operativas) {
            double libre = un.eventos.isEmpty() ? un.ocupadoHasta : un.eventos.peekLast().min;
            if (un.u.tipo == TipoUnidad.AUTO && libre <= t + 60) autosLibres++;
        }
        for (int k = autosLibres; k < urgentes.size(); k++) {
            Pedido p = urgentes.get(k);
            int partes = (p.cantidad + cfg.tamanoParcial - 1) / cfg.tamanoParcial;
            int restante = p.cantidad;
            estado.put(p.id, "X");
            List<String> ids = partesDe.get(p.idOriginal);
            ids.remove(p.id);
            for (int j = 1; j <= partes; j++) {
                int q = Math.min(restante, cfg.tamanoParcial);
                restante -= q;
                Pedido parte = new Pedido(p.id + "~" + j, p.idOriginal, p.x, p.y, q, p.plazoMaximoHoras, p.horaRegistro);
                entregas.put(parte.id, parte);
                estado.put(parte.id, "P");
                ids.add(parte.id);
                plazos.add(parte);
            }
            res.parcialesCreadas++;
        }
    }

    /**
     * Plan vigente reparado (11.3): la ruta que le quedaba a cada unidad, sin lo
     * ya hecho, con las entregas mapeadas a las copias relativas de este
     * ciclo; las entregas a bordo que falten se agregan al inicio de su ruta.
     * Los pedidos que no están en ninguna ruta (nuevos o sin asignar) se
     * insertan con la inserción de Clarke & Wright (mejor ruta y posición).
     * Si el resultado viola una restricción dura (cambió el estado), se
     * reconstruye desde las entregas a bordo.
     */
    private Solucion planVigenteReparado(List<Unidad> operativas, List<Pedido> aPlanificar,
                                         List<UnidadTransporte> disponibles) {
        Contexto cx = Contexto.actual();
        Solucion s = armarReparado(operativas, aPlanificar, true);
        Compartido.evaluarSolucion(s);
        if (!Compartido.esFactible(s)) {
            s = armarReparado(operativas, aPlanificar, false);
            Compartido.evaluarSolucion(s);
        }
        return OperadoresVecindario.conUnidadesLibres(s, disponibles);
    }

    private Solucion armarReparado(List<Unidad> operativas, List<Pedido> aPlanificar, boolean usarVigente) {
        Contexto cx = Contexto.actual();
        Map<String, Pedido> porId = new HashMap<>();
        for (Pedido p : aPlanificar) porId.put(p.id, p);
        Set<String> usados = new LinkedHashSet<>();
        Solucion s = new Solucion();
        for (Unidad un : operativas) {
            RutaAlg r = new RutaAlg();
            r.unidad = un.u;
            for (Pedido p : aPlanificar) {   // a bordo primero si faltan
                if (un.u.codigo.equals(p.aBordoDe) && (!usarVigente || !enRuta(un.rutaVigente, p.id))) {
                    r.paradas.add(ParadaAlg.entrega(p));
                    usados.add(p.id);
                }
            }
            if (usarVigente) {
                for (ParadaAlg pa : un.rutaVigente) {
                    if (pa.tipo == TipoParada.ENTREGA) {
                        Pedido q = porId.get(pa.pedido.id);
                        if (q == null || usados.contains(q.id)) continue;
                        if (q.aBordoDe != null && !q.aBordoDe.equals(un.u.codigo)) continue;
                        r.paradas.add(ParadaAlg.entrega(q));
                        usados.add(q.id);
                    } else if (pa.tipo == TipoParada.RECARGA) {
                        if (cx.indiceAlmacen(pa.almacen) >= 0) r.paradas.add(ParadaAlg.recarga(pa.almacen));
                    } else if (cx.averiadas.containsKey(pa.unidadAveriada)) {
                        r.paradas.add(ParadaAlg.trasvase(pa.unidadAveriada, pa.xTrasvase, pa.yTrasvase));
                    }
                }
            }
            s.rutas.add(r);
        }
        List<Pedido> faltantes = new ArrayList<>();
        for (Pedido p : aPlanificar) if (!usados.contains(p.id)) faltantes.add(p);
        faltantes.sort(Comparator.<Pedido>comparingDouble(cx::limiteEfectivo).thenComparing(p -> p.id));   // urgencia real (SI-23)
        for (Pedido pendiente : faltantes) {
            // Reparto por productos (SI-28): si no cabe entero a tiempo, una unidad
            // lleva los productos que le caben y el resto va a otra
            Pedido p = pendiente;
            while (p != null) {
                OperadoresVecindario.Pieza pieza = OperadoresVecindario.mejorPieza(s, s.rutas, p);
                if (pieza == null) {
                    s.pedidosSinAsignar.add(p);
                    break;
                }
                pieza.posicion.aplicar();
                p = pieza.resto;
            }
        }
        return s;
    }

    /**
     * Reparto por productos (Etapa 31, SI-28): el planificador puede repartir entre
     * unidades los productos en almacén de un pedido (partes nuevas) o volver a
     * unir partes. El simulador adopta ese reparto: registra las partes nuevas,
     * actualiza la cantidad de las que cambiaron y retira (estado "X") las que ya
     * no existen. Los productos de cada pedido deben conservarse; si no, es un
     * error del planificador y se detiene la simulación.
     * @param plan entregas del plan (copias relativas al instante t)
     */
    private void adoptarReparto(List<Pedido> plan, double t) {
        Map<String, Pedido> enPlan = new LinkedHashMap<>();
        Set<String> cambiados = new LinkedHashSet<>();
        for (Pedido p : plan) {
            if (!p.enAlmacen()) continue;   // lo que va a bordo o en una averiada no se reparte
            enPlan.put(p.id, p);
            Pedido actual = entregas.get(p.id);
            if (actual == null || actual.cantidad != p.cantidad) cambiados.add(p.idOriginal);
        }
        for (Map.Entry<String, String> e : estado.entrySet())
            if ("P".equals(e.getValue()) && !enPlan.containsKey(e.getKey())) cambiados.add(entregas.get(e.getKey()).idOriginal);
        for (String original : cambiados) {
            List<String> ids = partesDe.get(original);
            Pedido base = entregas.get(ids.get(0));   // registro, plazo y destino (absolutos) del pedido
            int antes = 0, despues = 0;
            for (String id : ids) if ("P".equals(estado.get(id))) antes += entregas.get(id).cantidad;
            for (Pedido p : enPlan.values()) if (p.idOriginal.equals(original)) despues += p.cantidad;
            if (antes != despues)
                throw new IllegalStateException("Reparto inconsistente del pedido " + original + ": "
                        + antes + " productos en almacén antes y " + despues + " en el plan");
            for (String id : new ArrayList<>(ids)) {
                if ("P".equals(estado.get(id)) && !enPlan.containsKey(id)) {   // se unió a otra parte
                    estado.put(id, "X");
                    ids.remove(id);
                }
            }
            for (Pedido p : enPlan.values()) {
                if (!p.idOriginal.equals(original)) continue;
                Pedido abs = new Pedido(p.id, original, base.x, base.y, p.cantidad, base.plazoMaximoHoras, base.horaRegistro);
                boolean nueva = !entregas.containsKey(p.id);
                entregas.put(p.id, abs);
                if (nueva) {
                    estado.put(p.id, "P");
                    ids.add(p.id);
                    plazos.add(abs);
                    res.parcialesCreadas++;
                }
            }
        }
    }

    private static boolean enRuta(List<ParadaAlg> ruta, String id) {
        for (ParadaAlg pa : ruta) if (pa.tipo == TipoParada.ENTREGA && pa.pedido.id.equals(id)) return true;
        return false;
    }

    /** Convierte el plan en líneas de tiempo nodo a nodo de cada unidad. */
    private void comprometer(double t, Contexto cx, Planificador.Plan plan, List<Unidad> operativas) {
        Map<String, RutaAlg> rutaDe = new HashMap<>();
        for (RutaAlg r : plan.rutas) rutaDe.put(r.unidad.codigo, r);
        Map<String, String> asignacion = new HashMap<>();
        for (Unidad un : operativas) {
            RutaAlg r = rutaDe.get(un.u.codigo);
            un.vioBloqueo = false;
            if (r == null) {
                un.rutaVigente = new ArrayList<>();
                if (cfg.regresoSinTareas) comprometerRegreso(t, cx, un);
                continue;
            }
            un.rutaVigente = new ArrayList<>(r.paradas);
            for (ParadaAlg pa : r.paradas) if (pa.tipo == TipoParada.ENTREGA) asignacion.put(pa.pedido.id, un.u.codigo);
            List<Hito> hitos = new ArrayList<>();
            Compartido.evaluarRuta(r, hitos);
            double v = velocidad(un.u.tipo);
            double retraso = 0;   // minutos de espera agregados (resguardo ante un bloqueo inmediato)
            for (Hito h : hitos) {
                double ini = t + 60 * h.inicioH + retraso, fin = t + 60 * h.finH + retraso;
                switch (h.tipo) {
                    case TRAMO: {
                        // Mismo camino que midió el planificador (distanciaTramo)
                        List<Integer> camino = cx.mapa != null
                                ? cx.mapa.caminoTramo(h.x1, h.y1, h.x2, h.y2, cx.instanteBaseH + h.inicioH, v, cx.alimentacion)
                                : caminoManhattan(h.x1, h.y1, h.x2, h.y2);
                        un.eventos.add(new Evt(Evt.T.SALIDA, ini, h.x1, h.y1, List.of(), null, ini, h.x2 * 1000 + h.y2));
                        double anterior = ini;
                        for (int i = 1; i < camino.size(); i++) {
                            int nodo = camino.get(i);
                            double tMin = t + 60 * cx.avanzar(h.inicioH, i / v) + retraso;
                            int nx = MapaVial.x(nodo), ny = MapaVial.y(nodo);
                            if (cx.mapa != null && i < camino.size() - 1 && un.encuentroMin == Double.POSITIVE_INFINITY
                                    && cx.mapa.bloqueado(nx, ny, tMin / 60.0)) {
                                if (anterior <= t + 1e-9) {
                                    // Resguardo: el bloqueo aparece justo al comprometer el plan (el
                                    // planificador ya lo consideró); replanificar ahora repetiría el
                                    // mismo plan, así que la unidad espera a que el nodo se libere.
                                    double libre = cx.mapa.finBloqueo(nx, ny, tMin / 60.0) * 60;
                                    retraso += libre - tMin;
                                    tMin = libre;
                                } else {
                                    un.encuentroMin = anterior;   // se detiene en el nodo anterior y se replanifica
                                }
                            }
                            Evt paso = new Evt(Evt.T.NODO, tMin, nx, ny, null, null, tMin, 0);
                            paso.salidaMin = tMin - 60.0 / v;   // recorre 1 km a la velocidad v
                            un.eventos.add(paso);
                            anterior = tMin;
                        }
                        break;
                    }
                    case ENTREGA:
                        un.eventos.add(new Evt(Evt.T.ENTREGA, ini, h.x1, h.y1, List.of(h.parada.pedido.id), h.parada, fin, h.cantidad));
                        break;
                    case RECARGA:
                        un.eventos.add(new Evt(Evt.T.RECARGA, ini, h.x1, h.y1, segmentoCarga(r, h.parada), h.parada, fin, h.cantidad));
                        break;
                    case TRASVASE:
                        un.eventos.add(new Evt(Evt.T.TRASVASE, ini, h.x1, h.y1, segmentoTrasvase(r, h.parada), h.parada, fin, h.cantidad));
                        break;
                    default:
                        un.eventos.add(new Evt(Evt.T.FIN, ini, h.x1, h.y1, List.of(), null, ini, 0));
                }
            }
        }
        // Estabilidad (11.4): entregas presentes en ambos planes que cambian de unidad
        for (Map.Entry<String, String> e : asignacion.entrySet()) {
            String antes = asignacionAnterior.get(e.getKey());
            if (antes != null && !antes.equals(e.getValue())) res.cambiosDeUnidad++;
        }
        asignacionAnterior = asignacion;
    }

    /**
     * Unidad sin tareas en el plan nuevo (Etapa 35, SI-34): vuelve al almacén más cercano por la red que tenga
     * stock (el central siempre), saliendo de donde está o del nodo al que va llegando. Si ya está en un
     * almacén, se queda. La línea de tiempo es la de cualquier tramo: nodo a nodo, con la alimentación.
     */
    private void comprometerRegreso(double t, Contexto cx, Unidad un) {
        Evt compromiso = un.eventos.peekLast();   // si viaja entre dos nodos, primero llega al siguiente
        int x = compromiso != null ? compromiso.x : un.x, y = compromiso != null ? compromiso.y : un.y;
        double salida = compromiso != null ? compromiso.min : Math.max(t, un.ocupadoHasta);
        if (almacenEn(x, y) != null) return;
        Almacen destino = null;
        double dMin = Double.MAX_VALUE;
        Almacen[] candidatos = {Compartido.ALMACEN_CENTRAL, Compartido.ALMACEN_NOROESTE, Compartido.ALMACEN_ESTE};
        for (int i = 0; i < candidatos.length; i++) {
            if (i > 0 && (!cfg.almacenesIntermedios || stock[i] <= 0)) continue;
            Almacen a = candidatos[i];
            double d = cx.mapa != null ? cx.mapa.distancia(x, y, a.x, a.y, salida / 60.0) : Compartido.distancia(x, y, a.x, a.y);
            if (d < dMin) { dMin = d; destino = a; }
        }
        double v = velocidad(un.u.tipo), inicioH = salida / 60.0;
        List<Integer> camino = cx.mapa != null ? cx.mapa.caminoTramo(x, y, destino.x, destino.y, inicioH, v, cx.alimentacion)
                : caminoManhattan(x, y, destino.x, destino.y);
        un.eventos.add(new Evt(Evt.T.SALIDA, salida, x, y, List.of(), null, salida, destino.x * 1000 + destino.y));
        double anterior = salida, llegada = salida;
        for (int i = 1; i < camino.size(); i++) {
            int nx = MapaVial.x(camino.get(i)), ny = MapaVial.y(camino.get(i));
            llegada = 60 * (cx.alimentacion ? Contexto.finConAlimentacion(inicioH, i / v) : inicioH + i / v);
            if (cx.mapa != null && i < camino.size() - 1 && un.encuentroMin == Double.POSITIVE_INFINITY
                    && cx.mapa.bloqueado(nx, ny, llegada / 60.0)) un.encuentroMin = anterior;   // se detiene y se replanifica
            Evt paso = new Evt(Evt.T.NODO, llegada, nx, ny, null, null, llegada, 0);
            paso.salidaMin = llegada - 60.0 / v;
            un.eventos.add(paso);
            anterior = llegada;
        }
        un.eventos.add(new Evt(Evt.T.FIN, llegada, destino.x, destino.y, List.of(), null, llegada, 0));
    }

    /** Entregas en almacén que carga una RECARGA: las siguientes hasta la próxima
     *  RECARGA (si la parada no está en la ruta, es la carga implícita de la salida). */
    private static List<String> segmentoCarga(RutaAlg r, ParadaAlg recarga) {
        int desde = 0;
        for (int i = 0; i < r.paradas.size(); i++) if (r.paradas.get(i) == recarga) { desde = i + 1; break; }
        List<String> ids = new ArrayList<>();
        for (int k = desde; k < r.paradas.size(); k++) {
            ParadaAlg q = r.paradas.get(k);
            if (q.tipo == TipoParada.RECARGA) break;
            if (q.tipo == TipoParada.ENTREGA && q.pedido.enAlmacen()) ids.add(q.pedido.id);
        }
        return ids;
    }

    private static List<String> segmentoTrasvase(RutaAlg r, ParadaAlg trasvase) {
        List<String> ids = new ArrayList<>();
        boolean despues = false;
        for (ParadaAlg q : r.paradas) {
            if (q == trasvase) { despues = true; continue; }
            if (despues && q.tipo == TipoParada.ENTREGA && trasvase.unidadAveriada.equals(q.pedido.enAveriada))
                ids.add(q.pedido.id);
        }
        return ids;
    }

    private static List<Integer> caminoManhattan(int x1, int y1, int x2, int y2) {
        List<Integer> c = new ArrayList<>();
        int x = x1, y = y1;
        c.add(MapaVial.nodo(x, y));
        while (x != x2) { x += Integer.signum(x2 - x); c.add(MapaVial.nodo(x, y)); }
        while (y != y2) { y += Integer.signum(y2 - y); c.add(MapaVial.nodo(x, y)); }
        return c;
    }

    // ============================ Métricas y registro ============================

    private void cerrarMetricas() {
        double fin = res.finMin;
        for (Map.Entry<String, List<String>> e : partesDe.entrySet()) {
            double[] ol = registroDe.get(e.getKey());   // {registro, límite} del pedido original
            if (ol == null || ol[0] > fin) continue;
            res.pedidosLlegados++;
            boolean entregado = !e.getValue().isEmpty();
            double ultima = 0;
            for (String id : e.getValue()) {
                Double en = entregadaEn.get(id);
                if (en == null || en > fin) { entregado = false; break; }
                ultima = Math.max(ultima, en);
            }
            if (entregado) res.pedidosEntregados++;
            if (entregado || ol[1] <= fin) {
                res.pedidosEvaluables++;
                if (entregado && ultima <= ol[1] + TOLERANCIA_MIN) res.pedidosEnPlazo++;
            }
        }
        res.planificadorMsMedio = res.replanificaciones == 0 ? 0 : sumaMs / res.replanificaciones;
        for (Unidad un : unidades) {
            res.viajesTotales += un.viajes;
            res.viajesMax = Math.max(res.viajesMax, un.viajes);
        }
        res.viajesMedio = unidades.isEmpty() ? 0 : (double) res.viajesTotales / unidades.size();
    }

    private void registrar(Evt e, String unidad, String detalle) {
        if (proyeccion) return;   // la copia proyectada no registra (comparte los eventos con el real)
        e.unidad = unidad;
        e.detalle = detalle;
        if (e.tipo != Evt.T.SALIDA) res.registro.add(e);
        if (cfg.eventos == null) return;
        String pedidos = e.entregas == null ? "" : String.join("|", e.entregas);
        cfg.eventos.println(String.format(Locale.US, "%.2f,%s,%s,%s,%s,%d,%d,%s", e.min, formatear(e.min), e.tipo,
                unidad, pedidos, e.x, e.y, detalle.replace(',', ';')));
    }

    // ============================ Instantánea (Etapa 21) ============================

    /**
     * Estado completo en JSON, en el instante actual, para cualquier visualizador:
     * reloj simulado y real; semáforo; colapso; almacenes con stock; unidades con
     * posición, estado, carga, próximas paradas y camino restante; bloqueos
     * activos; pedidos pendientes, asignados y en curso; indicadores. Seguro
     * desde otro hilo. Los minutos se cuentan desde el día 1 a las 00:00 del
     * primer mes de datos (Etapa 32: es el formato que lee el visualizador web).
     */
    String instantaneaJson() {
        cerrojo.lock();
        try {
            double t = tActual;
            EscritorJson j = new EscritorJson().abrirObjeto(null);
            j.abrirObjeto("reloj").valor("simulado_min", t).valor("simulado", formatear(t))
             .valor("real_ms", System.currentTimeMillis()).valor("escenario", cfg.escenario.name())
             .valor("inicio_min", cfg.inicioMin).valor("horizonte_min", cfg.horizonte()).valor("pausado", pausado)
             // Etapa 35: minuto que marca el reloj de la corrida AHORA. simulado_min es el último instante procesado
             // (va atrás hasta el siguiente evento); el visualizador usa ahora_min para mover las unidades sin saltos.
             .valor("ahora_min", minutoDelReloj()).cerrarObjeto();
            // Etapa 35: versión del plan vigente y bloque en curso; si cambia la versión, el visualizador pide /bloques
            j.abrirObjeto("plan").valor("version", planVersion).valor("aplicado_min", planAplicadoMin);
            if (cfg.bloqueMin > 0) j.valor("bloque_inicio_min", proximoBloque - cfg.bloqueMin).valor("bloque_fin_min", proximoBloque)
                    .valor("siguiente_listo", adelantoListo() != null);
            j.cerrarObjeto();
            // Semáforo: % de entregas cuyo resultado ya se conoce que llegaron en plazo
            int enPlazo = 0, conocidas = 0;
            for (Map.Entry<String, Double> e : entregadaEn.entrySet()) {
                conocidas++;
                if (e.getValue() <= entregas.get(e.getKey()).horaLimite() * 60) enPlazo++;
            }
            for (String id : vencidas) if (!entregadaEn.containsKey(id)) conocidas++;
            double pct = conocidas == 0 ? 100.0 : 100.0 * enPlazo / conocidas;
            String color = pct >= Visualizadorrutas.UMBRAL_VERDE ? "verde" : pct >= Visualizadorrutas.UMBRAL_AMBAR ? "ambar" : "rojo";
            j.abrirObjeto("semaforo").valor("pct_entregas_en_plazo", pct).valor("color", color)
             .valor("umbral_verde", Visualizadorrutas.UMBRAL_VERDE).valor("umbral_ambar", Visualizadorrutas.UMBRAL_AMBAR)
             .cerrarObjeto();
            j.abrirObjeto("colapso").valor("ocurrio", colapsoRegistrado);
            if (colapsoRegistrado) j.valor("simulado_min", res.colapsoMin).valor("simulado", formatear(res.colapsoMin))
                    .valor("limite_min", res.colapsoMin).valor("pedido", res.pedidoColapso)
                    .valor("unidad", res.unidadColapso).valor("causa", res.causaColapso);
            j.cerrarObjeto();
            j.abrirArreglo("almacenes");
            Almacen[] alm = {Compartido.ALMACEN_CENTRAL, Compartido.ALMACEN_NOROESTE, Compartido.ALMACEN_ESTE};
            for (int i = 0; i < alm.length; i++) {
                if (i > 0 && !cfg.almacenesIntermedios) continue;
                j.abrirObjeto(null).valor("codigo", alm[i].codigo).valor("x", alm[i].x).valor("y", alm[i].y)
                 .valor("stock", stock[i]);   // stock null = infinito (central)
                if (i == 0) j.valor("capacidad", (String) null);
                else j.valor("capacidad", Compartido.STOCK_INTERMEDIO);
                j.cerrarObjeto();
            }
            j.cerrarArreglo();
            Map<String, String> asignadaA = new HashMap<>();   // entregas en almacén que ya tienen unidad en el plan
            // Etapa 35 (E20, E21, E31): lo que dice el plan vigente de cada entrega: de qué almacén y cuándo
            // sale (su RECARGA) y cuándo llega al cliente (su ENTREGA)
            Map<String, Evt> saleEn = new HashMap<>(), llegaEn = new HashMap<>();
            for (Unidad un : unidades)
                for (Evt e : un.eventos) {
                    if (e.tipo == Evt.T.RECARGA) for (String id : e.entregas) saleEn.putIfAbsent(id, e);
                    if (e.tipo == Evt.T.ENTREGA) for (String id : e.entregas) llegaEn.putIfAbsent(id, e);
                }
            j.abrirArreglo("unidades");
            for (Unidad un : unidades) {
                int carga = 0;
                for (String id : un.aBordo) carga += entregas.get(id).cantidad;
                for (ParadaAlg pa : un.rutaVigente)
                    if (pa.tipo == TipoParada.ENTREGA) asignadaA.put(pa.pedido.id, un.u.codigo);
                j.abrirObjeto(null).valor("codigo", un.u.codigo).valor("tipo", un.u.tipo.name())
                 .valor("estado", un.estado.name()).valor("x", un.x).valor("y", un.y)
                 .valor("carga", carga).valor("capacidad", un.u.tipo.capacidadMaxima)
                 .valor("velocidad", velocidad(un.u.tipo)).valor("ocupado_hasta_min", un.ocupadoHasta)
                 .valor("km", un.km).valor("ultima_recarga", un.ultimaRecarga);
                if (un.estado == EstadoUnidad.AVERIADA && un.averia != null)
                    j.valor("averia_tipo", un.averia.tipo).valor("averia_en_lugar_hasta_min", un.averia.enLugarHastaMin())
                     .valor("averia_disponible_min", un.averia.disponibleDesdeMin());
                else j.valor("averia_tipo", (String) null);
                escribirOrigenYDestino(j, un, t);
                j.abrirArreglo("a_bordo");
                for (String id : un.aBordo) j.valor(null, id);
                j.cerrarArreglo();
                // Paradas del plan con su hora estimada exacta (la de la línea de tiempo, con bloqueos y alimentación)
                Map<ParadaAlg, Evt> evtDe = new java.util.IdentityHashMap<>();
                for (Evt e : un.eventos) if (e.parada != null) evtDe.putIfAbsent(e.parada, e);
                j.abrirArreglo("paradas");
                for (ParadaAlg pa : un.rutaVigente) {
                    Evt e = evtDe.get(pa);
                    j.abrirObjeto(null).valor("tipo", pa.tipo.name()).valor("id", pa.id())
                     .valor("x", pa.x()).valor("y", pa.y())
                     .valor("llegada_min", e != null ? e.min : Double.NaN).valor("fin_min", e != null ? e.finMin : Double.NaN);
                    if (pa.tipo == TipoParada.ENTREGA) j.valor("pedido", pa.pedido.idOriginal).valor("cantidad", pa.pedido.cantidad);
                    j.cerrarObjeto();
                }
                j.cerrarArreglo();
                j.abrirArreglo("camino");
                for (Evt e : un.eventos) if (e.tipo == Evt.T.NODO) j.punto(e.x, e.y);
                j.cerrarArreglo();
                j.cerrarObjeto();
            }
            j.cerrarArreglo();
            j.abrirArreglo("bloqueos_activos");
            if (cfg.mapa != null) {
                double h = t / 60.0;
                for (MapaVial.Bloqueo b : cfg.mapa.bloqueos) {
                    if (b.inicioH > h || h >= b.finH) continue;
                    j.abrirObjeto(null).valor("inicio_min", b.inicioH * 60).valor("fin_min", b.finH * 60);
                    j.abrirArreglo("nodos");
                    for (int n : b.nodos) j.punto(MapaVial.x(n), MapaVial.y(n));
                    j.cerrarArreglo().cerrarObjeto();
                }
            }
            j.cerrarArreglo();
            j.abrirArreglo("pedidos");
            for (Map.Entry<String, String> e : estado.entrySet()) {
                String est = e.getValue();
                if (est.equals("E") || est.equals("X")) continue;
                Pedido p = entregas.get(e.getKey());
                String unidad = est.length() > 2 ? est.substring(2) : asignadaA.get(p.id);
                String situacion = est.startsWith("U:") ? "en_curso" : est.startsWith("A:") ? "en_averiada"
                        : unidad != null ? "asignado" : "pendiente";
                Evt sale = saleEn.get(p.id), llega = llegaEn.get(p.id);
                j.abrirObjeto(null).valor("id", p.id).valor("pedido", p.idOriginal).valor("x", p.x).valor("y", p.y)
                 .valor("cantidad", p.cantidad).valor("limite_min", p.horaLimite() * 60).valor("estado", situacion)
                 .valor("unidad", unidad).valor("vencida", vencidas.contains(p.id))
                 .valor("plazo_h", p.plazoMaximoHoras).valor("registro_min", p.horaRegistro * 60)
                 // Etapa 35: almacén de origen (ya cargado, o el de la recarga planificada) y horas de salida y llegada
                 .valor("origen", origenDe.containsKey(p.id) ? origenDe.get(p.id) : sale != null ? sale.parada.almacen.codigo : null)
                 .valor("salida_min", cargadoEn.containsKey(p.id) ? cargadoEn.get(p.id) : sale != null ? sale.min : Double.NaN)
                 .valor("llegada_min", llega != null ? llega.min : Double.NaN).cerrarObjeto();
            }
            j.cerrarArreglo();
            // Etapa 35 (E22, E32): entregas hechas en las últimas 24 h, con su origen, unidad y hora
            j.abrirArreglo("entregados");
            for (Map.Entry<String, Double> e : entregadaEn.entrySet()) {
                if (e.getValue() < t - 1440) continue;
                Pedido p = entregas.get(e.getKey());
                j.abrirObjeto(null).valor("id", p.id).valor("pedido", p.idOriginal).valor("x", p.x).valor("y", p.y)
                 .valor("cantidad", p.cantidad).valor("limite_min", p.horaLimite() * 60).valor("plazo_h", p.plazoMaximoHoras)
                 .valor("unidad", entregadoPor.get(p.id)).valor("origen", origenDe.get(p.id))
                 .valor("salida_min", cargadoEn.containsKey(p.id) ? cargadoEn.get(p.id) : Double.NaN)
                 .valor("entregado_min", e.getValue()).cerrarObjeto();
            }
            j.cerrarArreglo();
            j.abrirObjeto("indicadores").valor("entregas_realizadas", entregadaEn.size())
             .valor("replanificaciones", res.replanificaciones).valor("costo_acumulado", res.costoAcumulado)
             .valor("km_acumulados", res.kmAcumulados).valor("averias", res.averiasAplicadas)
             .valor("trasvases", res.trasvases).valor("pedidos_inentregables_bloqueo", res.pedidosInentregablesBloqueo);
            j.abrirObjeto("entregas_por_plazo");   // por plazo comprometido (4, 8, 12, 18 y 36 h)
            Map<Integer, int[]> porPlazo = new java.util.TreeMap<>();
            for (int h : new int[]{4, 8, 12, 18, 36}) porPlazo.put(h, new int[2]);
            for (Map.Entry<String, Double> e : entregadaEn.entrySet()) {
                Pedido p = entregas.get(e.getKey());
                int[] c = porPlazo.computeIfAbsent(p.plazoMaximoHoras, k -> new int[2]);
                c[0]++;
                if (e.getValue() <= p.horaLimite() * 60 + TOLERANCIA_MIN) c[1]++;
            }
            for (Map.Entry<Integer, int[]> e : porPlazo.entrySet())
                j.abrirObjeto(String.valueOf(e.getKey())).valor("entregas", e.getValue()[0])
                 .valor("en_plazo", e.getValue()[1]).cerrarObjeto();
            j.cerrarObjeto();
            // Utilización: horas en movimiento / horas transcurridas, por tipo de unidad
            j.abrirObjeto("utilizacion_por_tipo");
            double horas = Math.max(1e-9, (t - cfg.inicioMin) / 60.0);
            for (TipoUnidad tipo : TipoUnidad.values()) {
                double mov = 0;
                int n = 0;
                for (Unidad un : unidades) if (un.u.tipo == tipo) { mov += un.km / velocidad(tipo); n++; }
                j.valor(tipo.name(), n == 0 ? 0 : Math.round(1000 * mov / (n * horas)) / 10.0);
            }
            j.cerrarObjeto();
            j.cerrarObjeto();
            return j.cerrarObjeto().toString();
        } finally {
            cerrojo.unlock();
        }
    }

    /**
     * Origen y destino de una unidad para el panel (Etapa 35, E06 a E09 y E14 a E17):
     *  - origen: la última parada a la que llegó (almacén, cliente o trasvase), cuándo llegó, cuándo salió de
     *    ella (null si aún no sale) y los km recorridos desde entonces;
     *  - destino: la próxima parada del plan, cuándo sale hacia ella (si aún no salió), cuándo llega y los km
     *    del camino que falta (con los desvíos por bloqueos);
     *  - km_por_recorrer: todo lo que le queda del plan, incluido el regreso al almacén.
     */
    private void escribirOrigenYDestino(EscritorJson j, Unidad un, double t) {
        boolean salio = !Double.isNaN(un.salidaMin);
        j.abrirObjeto("origen").valor("lugar", un.origenLugar).valor("x", un.origenX).valor("y", un.origenY)
         .valor("llegada_min", un.origenLlegadaMin).valor("salida_min", un.salidaMin)
         .valor("km", salio ? un.km - un.kmEnSalida : 0).cerrarObjeto();
        Evt destino = null;
        int km = 0, kmTotal = 0;
        double salida = salio ? un.salidaMin : Double.NaN;
        for (Evt e : un.eventos) {
            if (e.tipo == Evt.T.NODO) {
                kmTotal++;
                if (destino == null) km++;
            } else if (e.tipo == Evt.T.SALIDA) {
                if (destino == null && km == 0) salida = e.min;   // todavía no sale hacia el destino
            } else if (destino == null && (km > 0 || e.x != un.x || e.y != un.y)) {
                destino = e;   // primera parada que no es la actividad en curso aquí mismo
            }
        }
        if (destino == null) j.valor("destino", (String) null);
        else {
            String lugar = destino.tipo == Evt.T.ENTREGA ? "cliente (" + destino.x + "," + destino.y + ")"
                    : destino.tipo == Evt.T.TRASVASE ? "trasvase de " + destino.parada.unidadAveriada
                    : destino.parada != null && destino.parada.almacen != null ? destino.parada.almacen.codigo
                    : almacenEn(destino.x, destino.y) != null ? almacenEn(destino.x, destino.y).codigo : "(" + destino.x + "," + destino.y + ")";
            j.abrirObjeto("destino").valor("tipo", destino.tipo == Evt.T.FIN ? "REGRESO" : destino.tipo.name()).valor("lugar", lugar)
             .valor("x", destino.x).valor("y", destino.y).valor("salida_min", salida).valor("llegada_min", destino.min)
             .valor("km", km).cerrarObjeto();
        }
        j.valor("km_por_recorrer", kmTotal);
    }

    // ============================ Ruta anterior de un envío (Etapa 35, F02 a F04) ============================

    /**
     * Historial de un pedido (o de la parte o el producto indicados, p. ej. «c1234-L5+3·p2»): sus partes con
     * su estado, sus escalas (registro, carga en un almacén, trasvase, avería, devolución y entrega) y los
     * tramos que recorrió cada parte a bordo de cada unidad, nodo a nodo. null si no se conoce el id.
     * Seguro desde otro hilo.
     */
    String historialJson(String consulta) {
        cerrojo.lock();
        try {
            String id = consulta.trim().replaceFirst("[·.]p\\d+$", "");
            String original = partesDe.containsKey(id) ? id : entregas.containsKey(id) ? entregas.get(id).idOriginal : null;
            if (original == null || partesDe.get(original).isEmpty()) return null;
            List<String> ids = partesDe.get(original);
            Pedido base = entregas.get(ids.get(0));
            EscritorJson j = new EscritorJson().abrirObjeto(null).valor("consulta", consulta).valor("pedido", original)
                    .valor("x", base.x).valor("y", base.y).valor("registro_min", base.horaRegistro * 60)
                    .valor("limite_min", base.horaLimite() * 60).valor("ahora_min", tActual).abrirArreglo("partes");
            for (String pid : ids) {
                Pedido p = entregas.get(pid);
                String est = estado.getOrDefault(pid, "");
                List<Escala> es = escalasDe.getOrDefault(pid, List.of());
                j.abrirObjeto(null).valor("id", pid).valor("cantidad", p.cantidad).valor("seleccionada", pid.equals(id))
                 .valor("estado", est.equals("E") ? "entregado" : est.startsWith("U:") ? "en_curso" : est.startsWith("A:") ? "en_averiada" : "en_almacen")
                 .valor("unidad", est.length() > 2 ? est.substring(2) : entregadoPor.get(pid)).abrirArreglo("escalas");
                j.abrirObjeto(null).valor("min", p.horaRegistro * 60).valor("tipo", "REGISTRO").valor("lugar", "pedido registrado")
                 .valor("unidad", (String) null).valor("x", p.x).valor("y", p.y).cerrarObjeto();
                for (Escala e : es)
                    j.abrirObjeto(null).valor("min", e.min).valor("tipo", e.tipo).valor("lugar", e.lugar).valor("unidad", e.unidad)
                     .valor("x", e.x).valor("y", e.y).cerrarObjeto();
                j.cerrarArreglo().abrirArreglo("tramos");
                // Cada vez que sube a una unidad (carga o trasvase), el tramo va hasta la siguiente escala (o hasta ahora)
                for (int k = 0; k < es.size(); k++) {
                    Escala e = es.get(k);
                    if (!e.tipo.equals("CARGA") && !e.tipo.equals("TRASVASE")) continue;
                    double hasta = k + 1 < es.size() ? es.get(k + 1).min : tActual;
                    Unidad un = unidadPorCodigo.get(e.unidad);
                    j.abrirObjeto(null).valor("unidad", e.unidad).valor("desde_min", e.min).valor("hasta_min", hasta).abrirArreglo("puntos");
                    j.punto(e.x, e.y);
                    for (int i = 0; i < un.nRastro; i++)
                        if (un.rastroMin[i] > e.min && un.rastroMin[i] <= hasta)
                            j.punto(MapaVial.x(un.rastroNodo[i]), MapaVial.y(un.rastroNodo[i]));
                    j.cerrarArreglo().cerrarObjeto();
                }
                j.cerrarArreglo().cerrarObjeto();
            }
            return j.cerrarArreglo().cerrarObjeto().toString();
        } finally {
            cerrojo.unlock();
        }
    }

    // ============================ Trayectorias por bloques (Etapa 35) ============================

    /** Líneas de tiempo comprometidas de las unidades operativas hasta hastaMin: por cada nodo,
     *  {salida del nodo anterior, llegada, x, y}. Es lo que la unidad hará si nada cambia el plan. */
    private Map<String, List<double[]>> trayectorias(double hastaMin) {
        Map<String, List<double[]>> m = new LinkedHashMap<>();
        for (Unidad un : unidades) {
            if (un.estado != EstadoUnidad.OPERATIVA) continue;
            List<double[]> pasos = new ArrayList<>();
            for (Evt e : un.eventos) {
                if (e.min > hastaMin) break;
                if (e.tipo == Evt.T.NODO) pasos.add(new double[]{e.salidaMin, e.min, e.x, e.y});
            }
            m.put(un.u.codigo, pasos);
        }
        return m;
    }

    /** Plan del bloque siguiente ya calculado (con sus trayectorias), o null si todavía no está listo. */
    private Adelanto adelantoListo() {
        Future<Adelanto> f = pendiente;
        if (f == null || !f.isDone()) return null;
        try {
            Adelanto a = f.get();
            return a.trayectorias != null ? a : null;
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    /**
     * Trayectorias para el visualizador (GET /api/corridas/{ESC}/bloques; SI-30, propuesta_bloques.md §6):
     * la línea de tiempo nodo a nodo del bloque en curso y del siguiente según el plan vigente y, si ya
     * está calculado, el plan del bloque siguiente (en cola). Con ellas el visualizador reproduce el
     * desplazamiento según el reloj, sin saltos: cada plan nuevo empieza donde está cada unidad.
     * Seguro desde otro hilo.
     */
    String bloquesJson() {
        cerrojo.lock();
        try {
            double hasta = cfg.bloqueMin > 0 ? proximoBloque + cfg.bloqueMin : tActual + 3 * cfg.saMin;
            EscritorJson j = new EscritorJson().abrirObjeto(null).valor("version", planVersion).valor("ahora_min", tActual).valor("reloj_min", minutoDelReloj())
                    .valor("pausado", pausado).valor("hasta_min", hasta);
            if (cfg.bloqueMin > 0) j.valor("bloque_inicio_min", proximoBloque - cfg.bloqueMin).valor("bloque_fin_min", proximoBloque);
            j.abrirArreglo("unidades");
            Map<String, List<double[]>> tr = trayectorias(hasta);
            for (Unidad un : unidades) {
                j.abrirObjeto(null).valor("codigo", un.u.codigo).valor("x", un.x).valor("y", un.y)
                 .valor("velocidad", velocidad(un.u.tipo)).abrirArreglo("pasos");
                for (double[] p : tr.getOrDefault(un.u.codigo, List.of())) j.paso(p[0], p[1], (int) p[2], (int) p[3]);
                j.cerrarArreglo().cerrarObjeto();
            }
            j.cerrarArreglo();
            Adelanto a = adelantoListo();
            if (a == null) j.valor("siguiente", (String) null);
            else {
                j.abrirObjeto("siguiente").valor("inicio_min", a.inicioBloque).abrirArreglo("unidades");
                for (Map.Entry<String, List<double[]>> e : a.trayectorias.entrySet()) {
                    int[] pos = a.posiciones.get(e.getKey());
                    j.abrirObjeto(null).valor("codigo", e.getKey()).valor("x", pos[0]).valor("y", pos[1]).abrirArreglo("pasos");
                    for (double[] p : e.getValue()) j.paso(p[0], p[1], (int) p[2], (int) p[3]);
                    j.cerrarArreglo().cerrarObjeto();
                }
                j.cerrarArreglo().cerrarObjeto();
            }
            return j.cerrarObjeto().toString();
        } finally {
            cerrojo.unlock();
        }
    }

    /** Máximo de eventos que se envían en una consulta (el primero que se conecta tarde no recibe miles). */
    static final int MAX_EVENTOS_POR_CONSULTA = 300;

    /**
     * Bitácora para el visualizador: eventos del registro a partir de la posición
     * "desde" (el cliente guarda "total" y lo envía en la siguiente consulta).
     * Seguro desde otro hilo.
     */
    String eventosJson(int desde) {
        cerrojo.lock();
        try {
            List<Evt> reg = res.registro;
            EscritorJson j = new EscritorJson().abrirObjeto(null).valor("total", reg.size()).abrirArreglo("eventos");
            for (int i = Math.max(Math.max(0, desde), reg.size() - MAX_EVENTOS_POR_CONSULTA); i < reg.size(); i++) {
                Evt e = reg.get(i);
                j.abrirObjeto(null).valor("t", e.tipo.name()).valor("min", e.min).valor("simulado", formatear(e.min))
                 .valor("unidad", e.unidad.isEmpty() ? null : e.unidad).valor("detalle", textoEvento(e)).cerrarObjeto();
            }
            return j.cerrarArreglo().cerrarObjeto().toString();
        } finally {
            cerrojo.unlock();
        }
    }

    /** Texto legible de un evento para la bitácora. */
    private static String textoEvento(Evt e) {
        String quien = e.unidad.isEmpty() ? "" : e.unidad + " ";
        switch (e.tipo) {
            case ENTREGA:
                return quien + "entrega " + e.cantidad + " paq. de " + String.join(", ", e.entregas)
                        + " en (" + e.x + "," + e.y + ")";
            case COLAPSO:
                return "Colapso logístico: " + String.join(", ", e.entregas) + " no llegó a tiempo (" + e.detalle + ")";
            default:
                return (quien + e.detalle).trim();
        }
    }

    /** Resumen por tipo de unidad y plan vigente para el reporte de cierre del visualizador. */
    String reporteJson() {
        cerrojo.lock();
        try {
            double horas = Math.max(0, (tActual - cfg.inicioMin) / 60.0);
            EscritorJson j = new EscritorJson().abrirObjeto(null).valor("escenario", cfg.escenario.name())
                    .valor("simulado_min", tActual).valor("horas", horas).abrirArreglo("porTipo");
            for (TipoUnidad tipo : TipoUnidad.values()) {
                double km = 0, mov = 0;
                int n = 0;
                for (Unidad un : unidades) if (un.u.tipo == tipo) { km += un.km; mov += un.km / velocidad(tipo); n++; }
                if (n == 0) continue;
                j.abrirObjeto(null).valor("tipo", tipo.name()).valor("unidades", n).valor("km", km)
                 .valor("costo", km * tipo.costoPorKilometro)
                 .valor("utilizacion", horas == 0 ? 0 : 100 * mov / (n * horas)).cerrarObjeto();
            }
            j.cerrarArreglo().abrirArreglo("ultimoPlan");
            for (Unidad un : unidades)
                if (!un.rutaVigente.isEmpty())
                    j.abrirObjeto(null).valor("unidad", un.u.codigo).valor("paradas", un.rutaVigente.size()).cerrarObjeto();
            int vigentes = 0, sinUnidad = 0;
            java.util.Set<String> enRuta = new java.util.HashSet<>();
            for (Unidad un : unidades) for (ParadaAlg pa : un.rutaVigente) if (pa.tipo == TipoParada.ENTREGA) enRuta.add(pa.pedido.id);
            for (Map.Entry<String, String> e : estado.entrySet()) {
                if (e.getValue().equals("E") || e.getValue().equals("X")) continue;
                vigentes++;
                if (e.getValue().equals("P") && !enRuta.contains(e.getKey())) sinUnidad++;
            }
            j.cerrarArreglo().valor("pedidos_vigentes", vigentes);
            // Etapa 35 (G08 a G10): la última planificación estable completa: el plan vigente al cierre, con cada
            // parada de cada unidad, su carga y su hora estimada de llegada, y el regreso al almacén
            // Pedidos que ya llegaron (o se registraron) pero aún esperan al bloque siguiente, y los programados
            int porPlanificar = 0, programados = 0;
            for (int i = siguiente; i < porLlegar.size(); i++) {
                Pedido p = porLlegar.get(i);
                if (p.horaRegistro * 60 <= tActual) porPlanificar++;
                else if (idsProgramados.contains(p.id)) programados++;   // de la carga masiva (no los del archivo leídos por adelantado)
            }
            j.abrirObjeto("plan").valor("aplicado_min", planVersion > 0 ? planAplicadoMin : Double.NaN).valor("pendientes", vigentes)
             .valor("sin_unidad", sinUnidad).valor("por_planificar", porPlanificar).valor("programados", programados)
             .abrirArreglo("unidades");
            for (Unidad un : unidades) {
                if (un.rutaVigente.isEmpty() || un.estado != EstadoUnidad.OPERATIVA) continue;
                Map<ParadaAlg, Evt> evtDe = new java.util.IdentityHashMap<>();
                Evt fin = null;
                for (Evt e : un.eventos) {
                    if (e.parada != null) evtDe.putIfAbsent(e.parada, e);
                    if (e.tipo == Evt.T.FIN) fin = e;
                }
                j.abrirObjeto(null).valor("codigo", un.u.codigo).valor("tipo", un.u.tipo.name()).abrirArreglo("paradas");
                for (ParadaAlg pa : un.rutaVigente) {
                    Evt e = evtDe.get(pa);
                    j.abrirObjeto(null).valor("tipo", pa.tipo.name()).valor("x", pa.x()).valor("y", pa.y())
                     .valor("lugar", pa.tipo == TipoParada.RECARGA ? pa.almacen.codigo : pa.tipo == TipoParada.TRASVASE ? "trasvase de " + pa.unidadAveriada
                             : "cliente (" + pa.x() + "," + pa.y() + ")")
                     .valor("llegada_min", e != null ? e.min : Double.NaN);
                    if (pa.tipo == TipoParada.ENTREGA) j.valor("pedido", pa.pedido.id).valor("cantidad", pa.pedido.cantidad);
                    j.cerrarObjeto();
                }
                if (fin != null) {
                    Almacen a = almacenEn(fin.x, fin.y);
                    j.abrirObjeto(null).valor("tipo", "REGRESO").valor("x", fin.x).valor("y", fin.y)
                     .valor("lugar", a != null ? a.codigo : "(" + fin.x + "," + fin.y + ")").valor("llegada_min", fin.min).cerrarObjeto();
                }
                j.cerrarArreglo().cerrarObjeto();
            }
            return j.cerrarArreglo().cerrarObjeto().cerrarObjeto().toString();
        } finally {
            cerrojo.unlock();
        }
    }

    static String formatear(double min) {
        int m = (int) Math.floor(min);
        return String.format(Locale.US, "%02dd%02dh%02dm", m / 1440 + 1, (m % 1440) / 60, m % 60);
    }
}
