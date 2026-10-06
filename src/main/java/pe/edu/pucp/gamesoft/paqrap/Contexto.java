package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contexto de planificación (Etapas 9 a 12): todo lo que el planificador
 * necesita saber del mundo en el instante en que se lo llama. Lo arma el
 * Simulador antes de cada replanificación; Main y el modo estático usan el
 * contexto por defecto, que reproduce exactamente el modelo anterior:
 * todas las unidades salen del central en la hora 0, sin carga, solo existe
 * el central, distancia Manhattan, sin alimentación y sin recargas en ruta.
 *
 * Base de tiempo: la hora 0 relativa es el instante de planificación
 * (instanteBaseH, en horas absolutas desde el día 1 00:00).
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-02 (ventana del tramo), SI-03 (espera en destino bloqueado), SI-06 (alimentación), SI-14 (nodo vecino), SI-16 (penalidad de estabilidad), SI-19 (contexto por hilo), SI-23 (hora límite efectiva).
 */
final class Contexto {

    /** Estado inicial de una unidad para el plan. */
    static final class Inicio {
        final int x, y;
        final double inicioH;              // relativo: cuándo puede empezar (llega al nodo / termina lo que hace)
        final double noDisponibleDesdeH;   // relativo: próximo mantenimiento (la ruta debe terminar antes)

        Inicio(int x, int y, double inicioH, double noDisponibleDesdeH) {
            this.x = x;
            this.y = y;
            this.inicioH = inicioH;
            this.noDisponibleDesdeH = noDisponibleDesdeH;
        }
    }

    /** Almacén con el stock que el plan puede usar (central: infinito). */
    static final class AlmacenPlan {
        final Almacen almacen;
        final double stock;

        AlmacenPlan(Almacen almacen, double stock) {
            this.almacen = almacen;
            this.stock = stock;
        }
    }

    /** Unidad averiada detenida: almacén temporal hasta hastaH (relativo). */
    static final class Averiada {
        final String codigo;
        final int x, y;
        final double hastaH;

        Averiada(String codigo, int x, int y, double hastaH) {
            this.codigo = codigo;
            this.x = x;
            this.y = y;
            this.hastaH = hastaH;
        }
    }

    /**
     * Qué hacer si el destino de una entrega está bloqueado (Etapa 17; pregunta 11 al profesor):
     *  - ESPERAR: la entrega se hace cuando el nodo se desbloquea (comportamiento original).
     *  - NODO_VECINO: la entrega se hace desde el nodo adyacente no bloqueado más cercano (SI-14).
     *  - NO_EVALUABLE: igual que ESPERAR al planificar, pero el Simulador marca como
     *    "inentregable por bloqueo" el pedido cuyo destino está bloqueado desde su registro
     *    hasta su hora límite, lo excluye del criterio de colapso y lo cuenta aparte.
     */
    enum ReglaDestino { ESPERAR, NODO_VECINO, NO_EVALUABLE;
        static ReglaDestino desde(String t) {
            return valueOf(t.trim().toUpperCase(java.util.Locale.ROOT));
        }
    }

    private static final Inicio CENTRAL_HORA_0 = new Inicio(Compartido.ALMACEN_CENTRAL.x,
            Compartido.ALMACEN_CENTRAL.y, 0, Double.POSITIVE_INFINITY);

    double instanteBaseH = 0;
    MapaVial mapa = null;
    final List<AlmacenPlan> almacenes = new ArrayList<>();
    final Map<String, Inicio> inicios = new HashMap<>();
    final Map<String, Averiada> averiadas = new HashMap<>();
    /** 1 h de alimentación por turno (a las 03, 11 y 19 h), dondequiera que esté la unidad. */
    boolean alimentacion = false;
    /** Simulación con estado: recargas en ruta, varios viajes, decodificador con estado, siembra. */
    boolean conEstado = false;
    /** Plan vigente reparado con los pedidos nuevos insertados (punto de partida de Tabú y siembra del AG). */
    Solucion planBase = null;
    /**
     * Estabilidad (Etapa 18; P6 "replanificar, no volver a planificar"): unidad a la
     * que el plan vigente asignaba cada entrega (id -> código). Si no es null, cada
     * entrega que el plan nuevo pone en OTRA unidad cuesta penalidadCambio soles, que
     * se suman a S (nunca a H: no se sacrifican plazos). La usan ambos algoritmos,
     * porque va dentro de Compartido.evaluarRuta.
     */
    Map<String, String> asignacionVigente = null;
    double penalidadCambio = Parametros.decimal("estabilidad.penalidad_por_cambio", 16);
    /**
     * Reparto por productos (Etapa 31, SI-28; indicación del profesor): las unidades
     * entregan productos, no pedidos. Con "si", una entrega en almacén puede repartirse
     * entre varias unidades: una lleva n productos y otra el resto. Con "no", cada
     * entrega va entera en una sola unidad (comportamiento hasta la etapa 30).
     */
    boolean reparto = Parametros.texto("reparto.productos", "si").equalsIgnoreCase("si");
    /** Partes creadas al repartir; el simulador lo conserva entre ciclos para que los ids no se repitan. */
    int partesCreadas = 0;

    /** Id nuevo y único (en la simulación) para una parte de la entrega p. */
    String nuevoIdParte(Pedido p) {
        return p.idOriginal + "+" + (++partesCreadas);
    }
    /**
     * Holgura de seguridad (Etapa 22, SI-20): una entrega que el plan hace llegar A TIEMPO
     * pero con menos de holguraH horas de margen paga penalidadHolgura soles por cada hora
     * que le falta de margen. Va a S, nunca a H: no cambia qué es "tarde", solo hace que,
     * entre planes sin tardanzas, se prefieran los que no dejan entregas al filo del plazo
     * (una bicicleta que llega justo a la hora límite es un plan frágil). Solo rige con
     * estado (simulador); el modo estático y Main no la usan.
     */
    double holguraH = Parametros.decimal("plan.holgura_min", 60) / 60.0;
    double penalidadHolgura = Parametros.decimal("plan.penalidad_holgura", 200);
    /**
     * Velocidades cambiadas en caliente en ESTA simulación (P16; Etapa 24). Un tipo que
     * no aparece usa su velocidad configurada (TipoUnidad.velocidadPromedio). Antes el
     * cambio modificaba el enum y afectaba a todas las simulaciones del proceso.
     */
    final Map<TipoUnidad, Double> velocidades = new EnumMap<>(TipoUnidad.class);

    /** Velocidad (km/h) del tipo de unidad en este contexto. */
    double velocidad(TipoUnidad t) {
        return velocidades.getOrDefault(t, t.velocidadPromedio);
    }

    /** Regla para destinos bloqueados (red.destino_bloqueado). */
    ReglaDestino reglaDestino = ReglaDestino.desde(Parametros.texto("red.destino_bloqueado", "esperar"));

    Contexto() {
        almacenes.add(new AlmacenPlan(Compartido.ALMACEN_CENTRAL, Double.POSITIVE_INFINITY));
    }

    /** Contexto vigente POR HILO (Etapa 21): cada simulación corre en su hilo y el
     *  planificador se ejecuta en ese mismo hilo, así dos simulaciones no se pisan.
     *  (Los algoritmos aún tienen estado estático propio: semilla y contadores; ver SI-19.) */
    private static final ThreadLocal<Contexto> ACTUAL = ThreadLocal.withInitial(Contexto::new);

    static Contexto actual() {
        return ACTUAL.get();
    }

    static void usar(Contexto c) {
        ACTUAL.set(c);
    }

    static void restablecer() {
        ACTUAL.set(new Contexto());
    }

    Inicio inicioDe(UnidadTransporte u) {
        return inicios.getOrDefault(u.codigo, CENTRAL_HORA_0);
    }

    /** Índice del almacén en (x, y), o -1. */
    int almacenEn(int x, int y) {
        for (int i = 0; i < almacenes.size(); i++) {
            Almacen a = almacenes.get(i).almacen;
            if (a.x == x && a.y == y) return i;
        }
        return -1;
    }

    int indiceAlmacen(Almacen a) {
        for (int i = 0; i < almacenes.size(); i++) if (almacenes.get(i).almacen == a) return i;
        return -1;
    }

    /** Distancia en km en el instante relativo t: Manhattan o por la red con los
     *  bloqueos activos en t (se usa para elegir el almacén más cercano). */
    double distancia(int x1, int y1, int x2, int y2, double tRelH) {
        if (mapa == null) return Compartido.distancia(x1, y1, x2, y2);
        return mapa.distancia(x1, y1, x2, y2, instanteBaseH + tRelH);
    }

    /** Distancia de un tramo que sale en t a la velocidad v: Manhattan o por la red.
     *  APROXIMACIÓN (9.3): cada tramo evita los bloqueos activos en su salida y los que
     *  empiezan antes de su llegada estimada (MapaVial.distanciaTramo). Así el
     *  planificador usa los bloqueos activos al planificar y los que empiezan antes
     *  de cada llegada estimada. */
    double distanciaTramo(int x1, int y1, int x2, int y2, double tRelH, double v) {
        if (mapa == null) return Compartido.distancia(x1, y1, x2, y2);
        return mapa.distanciaTramo(x1, y1, x2, y2, instanteBaseH + tRelH, v, alimentacion);
    }

    /** Hora relativa en que se puede atender un destino al que se llega en t:
     *  si el nodo está bloqueado, se espera a que se desbloquee (9.2). Con
     *  NODO_VECINO no se espera: se entrega desde el nodo vecino (puntoDeEntrega). */
    double esperaDestino(int x, int y, double tRelH) {
        if (mapa == null || reglaDestino == ReglaDestino.NODO_VECINO) return tRelH;
        return mapa.finBloqueo(x, y, instanteBaseH + tRelH) - instanteBaseH;
    }

    /**
     * Hora límite efectiva (relativa) del pedido (Etapa 23, SI-23): su hora límite, salvo
     * que el destino esté bloqueado en ella por un bloqueo que dura más allá; entonces es el
     * inicio de ese bloqueo (MapaVial.limiteEfectivo). Llegar en [efectiva, límite] obliga a
     * esperar el desbloqueo y la entrega sale tarde. Con NODO_VECINO no se espera en el destino,
     * así que rige la hora límite.
     */
    double limiteEfectivo(Pedido p) {
        return limiteEfectivo(mapa, reglaDestino, p, instanteBaseH);
    }

    /** Misma regla para quien trabaja con horas absolutas (el Simulador: instanteBaseH = 0). */
    static double limiteEfectivo(MapaVial mapa, ReglaDestino regla, Pedido p, double instanteBaseH) {
        if (mapa == null || regla == ReglaDestino.NODO_VECINO) return p.horaLimite();
        return mapa.limiteEfectivo(p.x, p.y, instanteBaseH + p.horaLimite()) - instanteBaseH;
    }

    /**
     * Nodo desde el que se hace una entrega en (x, y) saliendo de (px, py) en t.
     * Con NODO_VECINO, si el destino está bloqueado al salir o al llegar (estimado),
     * se usa el nodo adyacente no bloqueado más cercano por la red (SI-14); en
     * otro caso, el propio destino.
     */
    int[] puntoDeEntrega(int px, int py, int x, int y, double tRelH, double v) {
        if (mapa == null || reglaDestino != ReglaDestino.NODO_VECINO) return new int[]{x, y};
        double salida = instanteBaseH + tRelH;
        double llegada = salida + mapa.distanciaTramo(px, py, x, y, salida, v, alimentacion) / v;
        if (!mapa.bloqueado(x, y, salida) && !mapa.bloqueado(x, y, llegada)) return new int[]{x, y};
        int[] mejor = {x, y};
        double dMin = Double.MAX_VALUE;
        int[][] vecinos = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
        for (int[] n : vecinos) {
            if (n[0] < 0 || n[0] >= MapaVial.ANCHO || n[1] < 0 || n[1] >= MapaVial.ALTO) continue;
            if (mapa.bloqueado(n[0], n[1], salida) || mapa.bloqueado(n[0], n[1], llegada)) continue;
            double d = mapa.distanciaTramo(px, py, n[0], n[1], salida, v, alimentacion);
            if (d < dMin) { dMin = d; mejor = n; }
        }
        return mejor;
    }

    /** Hora relativa al terminar una actividad de duracionH que empieza en t,
     *  sumando 1 h por cada inicio de alimentación (03, 11 y 19 h) que ocurra
     *  durante ella (1 h por turno, 4 h después del cambio de turno: cumple
     *  la regla de al menos 1 h antes o después del cambio). */
    double avanzar(double tRelH, double duracionH) {
        if (!alimentacion) return tRelH + duracionH;
        return finConAlimentacion(instanteBaseH + tRelH, duracionH) - instanteBaseH;
    }

    /** Hora absoluta en que termina una actividad de duracionH que empieza en inicioH,
     *  sumando 1 h por cada parada de alimentación (03, 11 y 19 h) que caiga en medio (SI-06). */
    static double finConAlimentacion(double inicioH, double duracionH) {
        double fin = inicioH + duracionH;
        double comida = Math.ceil((inicioH - 3) / 8.0) * 8 + 3;   // primera de 03, 11, 19 h >= inicio
        while (comida < fin) {
            fin += 1;
            comida += 8;
        }
        return fin;
    }

    /** Almacén más cercano (por la red) al punto, para el regreso al final de la ruta. */
    AlmacenPlan almacenMasCercano(int x, int y, double tRelH) {
        AlmacenPlan mejor = null;
        double dMin = Double.MAX_VALUE;
        for (AlmacenPlan a : almacenes) {
            double d = distancia(x, y, a.almacen.x, a.almacen.y, tRelH);
            if (d < dMin) { dMin = d; mejor = a; }
        }
        return mejor;
    }
}
