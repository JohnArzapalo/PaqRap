package pe.edu.pucp.gamesoft.paqrap;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 4.1.2 Modelo de red simplificado (un único almacén central; ver Main),
 * 4.1.4 función objetivo, 4.1.5 motor de factibilidad, 4.1.6 evaluación
 * incremental (recalculo directo en esta primera iteración; ver nota) y
 * 4.1.7 control de tiempo.
 */
class Compartido {

    static final Almacen ALMACEN_CENTRAL = new Almacen("AL-CEN", 27, 14);

    /** Tiempo de acondicionamiento en el cliente: 1 hora por entrega (preguntas 11 y 14).
     *  No cuenta dentro del plazo del pedido, pero retrasa las entregas siguientes.
     *  Configurable con entrega.horas. */
    static final double HORAS_ENTREGA = Parametros.decimal("entrega.horas", 1.0);

    /** Penalidad por pedido tarde dentro del Split: lo bastante grande para que
     *  cumplir plazos siempre tenga prioridad sobre el costo en soles.
     *  Configurable con objetivo.penalidad_tardanza. */
    static final double PENALIDAD_TARDANZA = Parametros.decimal("objetivo.penalidad_tardanza", 1_000_000.0);

    /** Distancia Manhattan: la ciudad es una retícula sin calles diagonales
     *  (enunciado de la situación auténtica). */
    static double distancia(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    /** Distancia de la ruta saliendo y volviendo al almacén central. Una ruta
     *  vacía mide 0 km (la unidad no sale). */
    private static double distanciaRuta(RutaAlg r) {
        double d = 0;
        int px = ALMACEN_CENTRAL.x, py = ALMACEN_CENTRAL.y;
        for (ParadaAlg p : r.paradas) {
            d += distancia(px, py, p.x(), p.y());
            px = p.x();
            py = p.y();
        }
        d += distancia(px, py, ALMACEN_CENTRAL.x, ALMACEN_CENTRAL.y);
        return d;
    }

    /** Recalcula distancia y costo de una ruta completa. En esta primera
     *  iteración se usa recálculo directo, más simple de verificar; la
     *  versión incremental (delta) del apartado 4.1.6 queda para la
     *  segunda iteración, cuando el tamaño de vecindario lo justifique. */
    static void recalcularDistanciaYCosto(RutaAlg r) {
        r.distanciaKm = distanciaRuta(r);
        r.costo = r.distanciaKm * r.unidad.tipo.costoPorKilometro;
    }

    /** 4.1.5 Motor de comprobación de factibilidad — primera iteración:
     *  restricción DURA de capacidad de carga. Los plazos no son una
     *  restricción dura: un pedido tarde se permite y se penaliza en H.
     *  El turno y la hora de alimentación, los bloqueos y el stock de
     *  almacén se incorporan cuando el CalculadorRecorridos y el
     *  GestorInventario reales estén integrados (ver apartado 5, supuesto SP-06).
     *  La usan los operadores de Búsqueda Tabú para descartar vecinos. */
    static boolean cumpleCapacidad(RutaAlg r) {
        return r.cargaTotal() <= r.unidad.tipo.capacidadMaxima;
    }

    /** Hora de llegada (en horas desde el inicio) a cada parada de la ruta.
     *  La unidad sale del almacén central en la hora 0, viaja a la velocidad
     *  promedio de su tipo y permanece HORAS_ENTREGA en cada cliente. */
    static double[] horasLlegada(RutaAlg r) {
        double[] llegada = new double[r.paradas.size()];
        double reloj = 0;
        int px = ALMACEN_CENTRAL.x, py = ALMACEN_CENTRAL.y;
        for (int i = 0; i < r.paradas.size(); i++) {
            ParadaAlg p = r.paradas.get(i);
            reloj += distancia(px, py, p.x(), p.y()) / r.unidad.tipo.velocidadPromedio;
            llegada[i] = reloj;
            if (p.tipo == TipoParada.ENTREGA) reloj += HORAS_ENTREGA;
            px = p.x();
            py = p.y();
        }
        return llegada;
    }

    /** Horas desde la salida del central hasta el regreso al central: viaje,
     *  HORAS_ENTREGA en cada cliente y retorno. Una ruta vacía dura 0 h.
     *  Misma base de tiempo que horasLlegada (la salida es la hora 0). */
    static double duracionRuta(RutaAlg r) {
        if (r.paradas.isEmpty()) return 0;
        double[] llegada = horasLlegada(r);
        int ultima = r.paradas.size() - 1;
        ParadaAlg p = r.paradas.get(ultima);
        double reloj = llegada[ultima] + (p.tipo == TipoParada.ENTREGA ? HORAS_ENTREGA : 0);
        return reloj + distancia(p.x(), p.y(), ALMACEN_CENTRAL.x, ALMACEN_CENTRAL.y) / r.unidad.tipo.velocidadPromedio;
    }

    /** true si la parada es una entrega y se llega después de la hora límite del pedido. */
    static boolean llegaTarde(ParadaAlg p, double horaLlegada) {
        return p.tipo == TipoParada.ENTREGA && horaLlegada > p.pedido.horaLimite();
    }

    /** Número de pedidos de la ruta que llegan fuera de plazo. */
    static int pedidosTarde(RutaAlg r) {
        double[] llegada = horasLlegada(r);
        int tarde = 0;
        for (int i = 0; i < llegada.length; i++) {
            if (llegaTarde(r.paradas.get(i), llegada[i])) tarde++;
        }
        return tarde;
    }

    /** 4.1.4 Función objetivo jerárquica de dos niveles: recalcula H y S
     *  de una Solución completa a partir de sus rutas.
     *  H = entregas sin asignar + entregas fuera de plazo.
     *  S = costo total en soles. Se compara primero H y luego S.
     *  Siempre usa la unidad REAL de cada ruta (tipo, velocidad y costo). */
    static void evaluarSolucion(Solucion s) {
        int h = s.pedidosSinAsignar.size();
        double total = 0;
        for (RutaAlg r : s.rutas) {
            recalcularDistanciaYCosto(r);
            total += r.costo;
            h += pedidosTarde(r);   // un pedido fuera de plazo incumple la política: cuenta en H
        }
        s.H = h;
        s.S = total;
    }

    /** true si "a" es mejor que "b": primero por H, luego por S. */
    static boolean mejorQue(Solucion a, Solucion b) {
        if (a.H != b.H) return a.H < b.H;
        return a.S < b.S;
    }

    /** 4.1.7 Módulo de parada por presupuesto de tiempo. */
    static boolean debeDetenerse(long inicioEjecucionMs, long presupuestoMs) {
        return (System.currentTimeMillis() - inicioEjecucionMs) >= presupuestoMs;
    }

    // ===================== Métricas por pedido original (R9) =====================

    /** Ids de las entregas que fallan: sin asignar o que llegan fuera de plazo. */
    static Set<String> entregasFallidas(Solucion s) {
        Set<String> fallidas = new HashSet<>();
        for (Pedido p : s.pedidosSinAsignar) fallidas.add(p.id);
        for (RutaAlg r : s.rutas) {
            double[] llegada = horasLlegada(r);
            for (int i = 0; i < llegada.length; i++) {
                ParadaAlg pa = r.paradas.get(i);
                if (llegaTarde(pa, llegada[i])) fallidas.add(pa.pedido.id);
            }
        }
        return fallidas;
    }

    /** {pedidos originales en plazo, pedidos originales totales}. Un pedido
     *  original está en plazo solo si TODAS sus entregas parciales están
     *  asignadas y llegan en plazo. */
    static int[] pedidosOriginalesEnPlazo(Solucion s) {
        Set<String> fallidas = entregasFallidas(s);
        Map<String, Boolean> enPlazo = new LinkedHashMap<>();
        for (Pedido p : s.todasLasEntregas()) {
            boolean ok = !fallidas.contains(p.id);
            enPlazo.merge(p.idOriginal, ok, Boolean::logicalAnd);
        }
        int ok = 0;
        for (boolean b : enPlazo.values()) if (b) ok++;
        return new int[]{ok, enPlazo.size()};
    }

    /** true si la hora límite del pedido ya es imposible de cumplir en el
     *  instante de planificación (hora 0), incluso yendo directo desde el
     *  almacén central con el tipo de vehículo más rápido de la flota (R8).
     *  Ese incumplimiento no es atribuible al algoritmo. */
    static boolean vencidoAlPlanificar(Pedido p, List<UnidadTransporte> flota) {
        double vMax = 0;
        for (UnidadTransporte u : flota) vMax = Math.max(vMax, u.tipo.velocidadPromedio);
        double llegadaMasTemprana = distancia(ALMACEN_CENTRAL.x, ALMACEN_CENTRAL.y, p.x, p.y) / vMax;
        return llegadaMasTemprana > p.horaLimite();
    }

    /** Número de pedidos ORIGINALES vencidos al planificar. */
    static int pedidosOriginalesVencidos(List<Pedido> entregas, List<UnidadTransporte> flota) {
        Set<String> vencidos = new HashSet<>();
        for (Pedido p : entregas) if (vencidoAlPlanificar(p, flota)) vencidos.add(p.idOriginal);
        return vencidos.size();
    }
}
