package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
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
    /** Regla para destinos bloqueados (red.destino_bloqueado). */
    ReglaDestino reglaDestino = ReglaDestino.desde(Parametros.texto("red.destino_bloqueado", "esperar"));

    Contexto() {
        almacenes.add(new AlmacenPlan(Compartido.ALMACEN_CENTRAL, Double.POSITIVE_INFINITY));
    }

    private static Contexto actual = new Contexto();

    static Contexto actual() {
        return actual;
    }

    static void usar(Contexto c) {
        actual = c;
    }

    static void restablecer() {
        actual = new Contexto();
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
        return mapa.distanciaTramo(x1, y1, x2, y2, instanteBaseH + tRelH, v);
    }

    /** Hora relativa en que se puede atender un destino al que se llega en t:
     *  si el nodo está bloqueado, se espera a que se desbloquee (9.2). Con
     *  NODO_VECINO no se espera: se entrega desde el nodo vecino (puntoDeEntrega). */
    double esperaDestino(int x, int y, double tRelH) {
        if (mapa == null || reglaDestino == ReglaDestino.NODO_VECINO) return tRelH;
        return mapa.finBloqueo(x, y, instanteBaseH + tRelH) - instanteBaseH;
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
        double llegada = salida + mapa.distanciaTramo(px, py, x, y, salida, v) / v;
        if (!mapa.bloqueado(x, y, salida) && !mapa.bloqueado(x, y, llegada)) return new int[]{x, y};
        int[] mejor = {x, y};
        double dMin = Double.MAX_VALUE;
        int[][] vecinos = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
        for (int[] n : vecinos) {
            if (n[0] < 0 || n[0] >= MapaVial.ANCHO || n[1] < 0 || n[1] >= MapaVial.ALTO) continue;
            if (mapa.bloqueado(n[0], n[1], salida) || mapa.bloqueado(n[0], n[1], llegada)) continue;
            double d = mapa.distanciaTramo(px, py, n[0], n[1], salida, v);
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
        double inicio = instanteBaseH + tRelH, fin = inicio + duracionH;
        double comida = Math.ceil((inicio - 3) / 8.0) * 8 + 3;   // primera de 03, 11, 19 h >= inicio
        while (comida < fin) {
            fin += 1;
            comida += 8;
        }
        return fin - instanteBaseH;
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
