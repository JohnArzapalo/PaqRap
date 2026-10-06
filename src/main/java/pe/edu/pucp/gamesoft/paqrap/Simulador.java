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
import java.util.concurrent.ConcurrentLinkedQueue;
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

        Unidad(UnidadTransporte u) {
            this.u = u;
        }
    }

    // ============================ Estado de la corrida ============================

    private final Config cfg;
    private final List<UnidadTransporte> flota;
    private final Planificador planificador;
    private final List<Unidad> unidades = new ArrayList<>();
    private final Map<String, Unidad> unidadPorCodigo = new HashMap<>();
    private final List<Pedido> porLlegar;
    private int siguiente = 0;
    private final Map<String, Pedido> entregas = new LinkedHashMap<>();
    /** Estado de cada entrega: P (pendiente en almacén), U:cod (a bordo), A:cod (en averiada), E (entregada), X (reemplazada por partes). */
    private final Map<String, String> estado = new HashMap<>();
    private final Map<String, Double> entregadaEn = new HashMap<>();
    private final Map<String, List<String>> partesDe = new LinkedHashMap<>();
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

    // ===== Integración con el visualizador (Etapa 21) =====
    /** Protege el estado mientras otro hilo arma una instantánea; se libera durante la
     *  espera del reloj y mientras el planificador trabaja. */
    private final ReentrantLock cerrojo = new ReentrantLock();
    /** Averías registradas desde fuera (visualizador): {código de unidad, tipo}. */
    private final ConcurrentLinkedQueue<Object[]> averiasExternas = new ConcurrentLinkedQueue<>();
    /** Pedidos registrados desde fuera (visualizador, operación día a día); llegan en el instante actual. */
    private final ConcurrentLinkedQueue<Pedido> pedidosExternos = new ConcurrentLinkedQueue<>();
    /** Cambios de velocidad pendientes (se aplican en la siguiente replanificación, P16). */
    private final ConcurrentLinkedQueue<Object[]> velocidadesPendientes = new ConcurrentLinkedQueue<>();
    /** Velocidades vigentes en ESTA simulación tras los cambios en caliente (Etapa 24). */
    private final EnumMap<TipoUnidad, Double> velocidades = new EnumMap<>(TipoUnidad.class);
    private volatile Thread hilo;
    private volatile boolean detenerSolicitado;
    private double tActual = 0;

    private Simulador(List<Pedido> pedidos, List<UnidadTransporte> flota, Planificador planificador, Config cfg) {
        this.cfg = cfg;
        this.flota = flota;
        this.planificador = planificador;
        for (UnidadTransporte u : flota) {
            Unidad un = new Unidad(u);
            unidades.add(un);
            unidadPorCodigo.put(u.codigo, un);
        }
        porLlegar = new ArrayList<>(pedidos);
        porLlegar.sort(Comparator.comparingDouble(p -> p.horaRegistro));
        // Etapa 32: llegadas urgentes (hora límite efectiva a menos de urgenteMin del registro)
        for (Pedido p : porLlegar)
            if (cfg.urgenteMin > 0 && (Contexto.limiteEfectivo(cfg.mapa, cfg.reglaDestino, p, 0) - p.horaRegistro) * 60 < cfg.urgenteMin)
                llegadasUrgentes.add(p.horaRegistro * 60);
        Set<String> originales = new LinkedHashSet<>();
        for (Pedido p : porLlegar) originales.add(p.idOriginal);
        res.pedidosOriginalesTotal = originales.size();
        stock[0] = Double.POSITIVE_INFINITY;
        stock[1] = stock[2] = Compartido.STOCK_INTERMEDIO;
    }

    /**
     * @param pedidos entregas con horaRegistro ABSOLUTA en horas (LectorPedidos.leerAbsoluto)
     */
    static Resultado simular(List<Pedido> pedidos, List<UnidadTransporte> flota, Planificador planificador,
                             Config cfg) {
        return crear(pedidos, flota, planificador, cfg).ejecutar();
    }

    /** Crea la simulación sin correrla (para ejecutarla en otro hilo: ServicioSimulacion). */
    static Simulador crear(List<Pedido> pedidos, List<UnidadTransporte> flota, Planificador planificador,
                           Config cfg) {
        return new Simulador(pedidos, flota, planificador, cfg);
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
        int iAveria = 0;
        List<Averia> averias = new ArrayList<>(cfg.averias);
        averias.sort(Comparator.comparingDouble(a -> a.tiempoMin));
        while (iAveria < averias.size() && averias.get(iAveria).tiempoMin < t) iAveria++;   // anteriores al inicio
        double proximaReposicion = Math.floor(t / 1440) * 1440 + 1440 - 1.0 / 60;   // 23:59:59 del día de inicio
        boolean terminar = false;

        cerrojo.lock();
        try {
        while (!terminar) {
            // 1. Próximo instante
            double T = Math.min(proximaReplan, fin);
            for (Unidad un : unidades) {
                if (!un.eventos.isEmpty()) T = Math.min(T, un.eventos.peek().min);
                T = Math.min(T, un.encuentroMin);
                if (un.estado == EstadoUnidad.AVERIADA) {
                    if (!un.averiaDejoLugar) T = Math.min(T, un.averia.enLugarHastaMin());
                    T = Math.min(T, un.averia.disponibleDesdeMin());
                }
            }
            if (iAveria < averias.size()) T = Math.min(T, averias.get(iAveria).tiempoMin);
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
            if (!completo || !averiasExternas.isEmpty() || !pedidosExternos.isEmpty() || detenerSolicitado) {
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
            while (iAveria < averias.size() && averias.get(iAveria).tiempoMin <= t) {
                replanEvento |= aplicarAveria(averias.get(iAveria++), t);
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
                registrarLlegada(nuevo, t);
                registrar(new Evt(Evt.T.REPLANIFICACION, t, nuevo.x, nuevo.y, List.of(nuevo.id), null, t, nuevo.cantidad), "",
                        "pedido " + nuevo.idOriginal + " registrado: " + nuevo.cantidad + " paq. a (" + nuevo.x + ","
                                + nuevo.y + "), plazo " + nuevo.plazoMaximoHoras + " h");
                replanEvento = true;
            }
            for (Unidad un : unidades) {
                if (un.estado != EstadoUnidad.AVERIADA) continue;
                if (!un.averiaDejoLugar && un.averia.enLugarHastaMin() <= t) {
                    un.averiaDejoLugar = true;
                    replanEvento = true;
                    if (un.averia.tipo == 1) {
                        reactivar(un, t, true);
                    } else {
                        // pasa "instantáneamente" al central; lo no trasvasado vuelve al stock del central
                        for (String id : new ArrayList<>(un.aBordo)) estado.put(id, "P");
                        un.aBordo.clear();
                        un.x = Compartido.ALMACEN_CENTRAL.x;
                        un.y = Compartido.ALMACEN_CENTRAL.y;
                        registrar(new Evt(Evt.T.AVERIA, t, un.x, un.y, List.of(), null, t, 0), un.u.codigo,
                                "deja el lugar y pasa al central");
                    }
                } else if (un.averiaDejoLugar && un.averia.disponibleDesdeMin() <= t) {
                    reactivar(un, t, false);
                    replanEvento = true;
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
                    replanEvento = true;
                }
            }
            if (cfg.almacenesIntermedios && t >= proximaReposicion) {
                stock[1] = stock[2] = Compartido.STOCK_INTERMEDIO;
                proximaReposicion += 1440;
            }

            // 5. Replanificación periódica (cada Sa) o por evento. Resguardo: si los eventos
            //    piden replanificar una y otra vez en el mismo instante, se omite y se registra.
            boolean periodica = t >= proximaReplan;
            if (replanEvento && !periodica) {
                repeticionesEnInstante = (t == ultimaReplanEvento) ? repeticionesEnInstante + 1 : 0;
                ultimaReplanEvento = t;
                if (repeticionesEnInstante > 3) {
                    replanEvento = false;
                    registrar(new Evt(Evt.T.REPLANIFICACION, t, 0, 0, List.of(), null, t, 0), "",
                            "omitida: replanificación repetida en el mismo instante");
                }
            }
            if (periodica || replanEvento) {
                if (!periodica) res.replanPorEvento++;
                replanificar(t);
                while (proximaReplan <= t) proximaReplan += cfg.saMin;
            }
        }
        } finally {
            cerrarMetricas();
            cerrojo.unlock();
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
                un.x = e.x;
                un.y = e.y;
                un.km += 1;
                res.kmAcumulados += 1;
                res.costoAcumulado += un.u.tipo.costoPorKilometro;
                break;
            case SALIDA:
                registrar(e, un.u.codigo, "hacia (" + e.cantidad / 1000 + "," + e.cantidad % 1000 + ")");
                break;
            case ENTREGA:
                for (String id : e.entregas) {
                    if (!("U:" + un.u.codigo).equals(estado.get(id))) continue;
                    estado.put(id, "E");
                    entregadaEn.put(id, e.min);
                    un.aBordo.remove(id);
                    if (e.min > entregas.get(id).horaLimite() * 60 + TOLERANCIA_MIN) res.entregasTarde++;
                }
                un.ocupadoHasta = e.finMin;
                un.rutaVigente.remove(e.parada);
                registrar(e, un.u.codigo, "");
                break;
            case RECARGA: {
                int cargados = 0;
                for (String id : e.entregas) {
                    if (!"P".equals(estado.get(id))) continue;
                    estado.put(id, "U:" + un.u.codigo);
                    un.aBordo.add(id);
                    cargados += entregas.get(id).cantidad;
                }
                int a = indiceAlmacen(e.parada.almacen);
                if (a > 0) stock[a] = Math.max(0, stock[a] - cargados);
                if (cargados > 0) un.viajes++;
                un.ocupadoHasta = e.finMin;
                un.rutaVigente.remove(e.parada);
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
                    movidos += entregas.get(id).cantidad;
                }
                if (movidos > 0) res.trasvases++;
                un.ocupadoHasta = e.finMin;
                un.rutaVigente.remove(e.parada);
                registrar(e, un.u.codigo, "desde " + e.parada.unidadAveriada + ": " + movidos + " paquetes");
                break;
            }
            case FIN:
                un.x = e.x;
                un.y = e.y;
                un.ocupadoHasta = e.min;
                registrar(e, un.u.codigo, "llega al almacén");
                break;
            default:
                break;
        }
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
        for (String id : un.aBordo) estado.put(id, "A:" + un.u.codigo);
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
        plazos.add(p);
    }

    private void replanificar(double t) {
        // Cambios de velocidad en caliente: rigen desde esta planificación (P16)
        Object[] v;
        while ((v = velocidadesPendientes.poll()) != null) {
            velocidades.put((TipoUnidad) v[0], (Double) v[1]);
            registrar(new Evt(Evt.T.REPLANIFICACION, t, 0, 0, List.of(), null, t, 0), "",
                    "velocidad de " + v[0] + " = " + v[1] + " km/h");
        }
        // Llegada de pedidos
        while (siguiente < porLlegar.size() && porLlegar.get(siguiente).horaRegistro * 60 <= t)
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
        if (aPlanificar.isEmpty() || disponibles.isEmpty()) return;   // nada que cambiar: sigue el plan vigente

        cx.partesCreadas = partesCreadas;   // ids de partes únicos en toda la simulación (SI-28)
        Contexto.usar(cx);
        try {
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
            long semillaCiclo = cfg.semilla * 1_000_003L + ciclo++;
            long t0 = System.nanoTime();
            Planificador.Plan plan;
            cerrojo.unlock();   // la instantánea puede leerse mientras el planificador trabaja
            try {
                plan = planificador.planificar(new Planificador.EstadoPlanificacion(
                        cx, aPlanificar, disponibles, cfg.taMs, cfg.maxEvaluaciones, semillaCiclo));
            } finally {
                cerrojo.lock();
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            Contexto.usar(cx);   // el planificador pudo cambiarlo
            partesCreadas = cx.partesCreadas;
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
            for (Unidad un : operativas) {   // se reemplazan las líneas de tiempo por las del plan nuevo
                un.eventos.clear();
                if (compromiso.containsKey(un)) un.eventos.add(compromiso.get(un));
                un.encuentroMin = Double.POSITIVE_INFINITY;
            }
            comprometer(t, cx, plan, operativas);
            registrar(new Evt(Evt.T.REPLANIFICACION, t, 0, 0, List.of(), null, t, 0), "",
                    aPlanificar.size() + " entregas, " + disponibles.size() + " unidades, "
                            + plan.sinAsignar.size() + " sin asignar, " + String.format(Locale.US, "%.0f ms", ms));
        } finally {
            Contexto.restablecer();
        }
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
                            un.eventos.add(new Evt(Evt.T.NODO, tMin, nx, ny, null, null, tMin, 0));
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
        Map<String, double[]> original = new HashMap<>();   // idOriginal -> {registro, límite}
        for (Pedido p : porLlegar) original.putIfAbsent(p.idOriginal, new double[]{p.horaRegistro * 60, p.horaLimite() * 60});
        for (Map.Entry<String, List<String>> e : partesDe.entrySet()) {
            double[] ol = original.get(e.getKey());
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
             .valor("inicio_min", cfg.inicioMin).valor("horizonte_min", cfg.horizonte()).cerrarObjeto();
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
                 .valor("km", un.km);
                if (un.estado == EstadoUnidad.AVERIADA && un.averia != null) j.valor("averia_tipo", un.averia.tipo);
                else j.valor("averia_tipo", (String) null);
                j.abrirArreglo("a_bordo");
                for (String id : un.aBordo) j.valor(null, id);
                j.cerrarArreglo();
                j.abrirArreglo("paradas");
                for (ParadaAlg pa : un.rutaVigente)
                    j.abrirObjeto(null).valor("tipo", pa.tipo.name()).valor("id", pa.id())
                     .valor("x", pa.x()).valor("y", pa.y()).cerrarObjeto();
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
                j.abrirObjeto(null).valor("id", p.id).valor("pedido", p.idOriginal).valor("x", p.x).valor("y", p.y)
                 .valor("cantidad", p.cantidad).valor("limite_min", p.horaLimite() * 60).valor("estado", situacion)
                 .valor("unidad", unidad).valor("vencida", vencidas.contains(p.id))
                 .valor("plazo_h", p.plazoMaximoHoras).cerrarObjeto();
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
            int vigentes = 0;
            for (String est : estado.values()) if (!est.equals("E") && !est.equals("X")) vigentes++;
            return j.cerrarArreglo().valor("pedidos_vigentes", vigentes).cerrarObjeto().toString();
        } finally {
            cerrojo.unlock();
        }
    }

    static String formatear(double min) {
        int m = (int) Math.floor(min);
        return String.format(Locale.US, "%02dd%02dh%02dm", m / 1440 + 1, (m % 1440) / 60, m % 60);
    }
}
