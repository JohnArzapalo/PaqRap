package pe.edu.pucp.gamesoft.paqrap;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 4.1.2 Modelo de red, 4.1.4 función objetivo, 4.1.5 motor de factibilidad,
 * 4.1.6 evaluación (recálculo directo) y 4.1.7 control de tiempo.
 *
 * Toda la evaluación de rutas pasa por evaluarRuta, que usa el Contexto de
 * planificación vigente (Contexto.actual()): posición y hora de inicio de
 * cada unidad, carga a bordo, almacenes con stock, recargas, trasvases,
 * bloqueos (MapaVial) y alimentación. Con el contexto por defecto
 * reproduce exactamente el modelo anterior (salida del central en la hora 0,
 * Manhattan, regreso al central), que es el que usan Main y el modo estático.
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-02, SI-03, SI-04, SI-05, SI-07, SI-14, SI-16 (la penalidad de estabilidad va a S, nunca a H), SI-20 (holgura de seguridad, también solo en S), SI-23 (la holgura se mide hasta la hora límite efectiva).
 */
class Compartido {

    static final Almacen ALMACEN_CENTRAL = new Almacen("AL-CEN", 27, 14);
    static final Almacen ALMACEN_NOROESTE = new Almacen("AL-NO", 12, 38);
    static final Almacen ALMACEN_ESTE = new Almacen("AL-ES", 57, 27);
    /** Stock inicial y de reposición diaria (23:59:59) de los almacenes intermedios. */
    static final double STOCK_INTERMEDIO = Parametros.decimal("almacenes.stock_intermedio", 1000);

    /** Tiempo de acondicionamiento en el cliente: 1 hora por entrega (preguntas 11 y 14).
     *  No cuenta dentro del plazo del pedido, pero retrasa las entregas siguientes.
     *  Configurable con entrega.horas. */
    static final double HORAS_ENTREGA = Parametros.decimal("entrega.horas", 1.0);
    /** Tiempo de carga en un almacén (no está en el enunciado: 0 por defecto). */
    static final double HORAS_RECARGA = Parametros.decimal("recarga.minutos", 0) / 60.0;
    /** Tiempo de trasvase desde una unidad averiada (nota del profesor: 30 min, por confirmar). */
    static final double HORAS_TRASVASE = Parametros.decimal("trasvase.minutos", 30) / 60.0;

    /** Penalidad por pedido tarde dentro del Split: lo bastante grande para que
     *  cumplir plazos siempre tenga prioridad sobre el costo en soles.
     *  Configurable con objetivo.penalidad_tardanza. */
    static final double PENALIDAD_TARDANZA = Parametros.decimal("objetivo.penalidad_tardanza", 1_000_000.0);

    /** Distancia Manhattan: la ciudad es una retícula sin calles diagonales
     *  (enunciado de la situación auténtica). */
    static double distancia(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    // ============================ Evaluación de una ruta ============================

    /** Resultado de evaluar una ruta en el contexto vigente. */
    static final class EvalRuta {
        double km, costo, finH;   // costo = km × costo/km + penalidad de estabilidad (si hay plan vigente)
        int tarde;                // PRODUCTOS que llegan después de su hora límite (SI-28)
        int cambiosDeUnidad;     // entregas que cambian de unidad frente al plan vigente
        double faltaHolguraH;     // horas de margen que faltan (entregas a tiempo con margen < holgura)
        boolean factible = true;
        String motivo;
        double[] llegada;     // por parada: llegada (en ENTREGA, cuando se puede atender)
        int[] usoAlmacen;     // paquetes cargados por almacén del contexto

        void infactible(String m) {
            if (factible) { factible = false; motivo = m; }
        }
    }

    static EvalRuta evaluarRuta(RutaAlg r) {
        return evaluarRuta(r, null);
    }

    /**
     * Evalúa la ruta de una sola pasada: horas de llegada, km, costo, entregas
     * tarde y factibilidad (restricciones duras):
     *  - una entrega a bordo solo la hace la unidad que la lleva;
     *  - una entrega en almacén necesita un punto de carga antes: la salida
     *    desde un almacén (carga implícita) o una parada RECARGA;
     *  - una entrega guardada en una unidad averiada necesita antes una parada
     *    TRASVASE en esa unidad, que debe terminar antes de que la averiada deje el lugar;
     *  - la carga no supera la capacidad después de cada punto de carga;
     *  - la ruta termina antes del próximo mantenimiento de la unidad.
     * Los plazos NO son restricción dura: una entrega tarde suma en H.
     * Al final la unidad vuelve al almacén más cercano (en el contexto por
     * defecto, el central). Una ruta vacía no se mueve y cuesta 0.
     * Si registro no es null, agrega la línea de tiempo (Hito) de la ruta.
     */
    static EvalRuta evaluarRuta(RutaAlg r, List<Hito> registro) {
        Contexto cx = Contexto.actual();
        Contexto.Inicio ini = cx.inicioDe(r.unidad);
        TipoUnidad tipo = r.unidad.tipo;
        int n = r.paradas.size();
        EvalRuta e = new EvalRuta();
        e.llegada = new double[n];
        e.usoAlmacen = new int[cx.almacenes.size()];
        double reloj = ini.inicioH;
        int px = ini.x, py = ini.y;

        // Carga a bordo al inicio
        int carga = 0;
        for (ParadaAlg p : r.paradas) {
            if (p.tipo != TipoParada.ENTREGA || p.pedido.aBordoDe == null) continue;
            if (p.pedido.aBordoDe.equals(r.unidad.codigo)) carga += p.cantidad;
            else e.infactible("entrega a bordo de otra unidad");
        }
        // Carga implícita si la unidad arranca en un almacén
        int segmento = cargaSegmento(r, 0);
        if (segmento > 0) {
            int a = cx.almacenEn(px, py);
            if (a < 0) {
                e.infactible("entrega en almacén sin punto de carga");
            } else {
                carga += segmento;
                e.usoAlmacen[a] += segmento;
                if (registro != null)
                    registro.add(new Hito(Hito.Tipo.RECARGA, reloj, reloj, px, py, px, py,
                            ParadaAlg.recarga(cx.almacenes.get(a).almacen), segmento));
            }
        }
        if (carga > tipo.capacidadMaxima) e.infactible("capacidad");
        if (n == 0) {
            e.finH = reloj;
            return e;
        }

        Set<String> trasvasados = null;
        double km = 0;
        for (int i = 0; i < n; i++) {
            ParadaAlg p = r.paradas.get(i);
            if (i > 0 && mismaVisita(r.paradas.get(i - 1), p)) {
                // Reparto por productos (SI-28): partes consecutivas del mismo pedido son UNA
                // visita al cliente, con la misma llegada y una sola hora de entrega
                Pedido ped = p.pedido;
                if (ped.enAveriada != null && (trasvasados == null || !trasvasados.contains(ped.enAveriada)))
                    e.infactible("entrega sin trasvase previo");
                e.llegada[i] = e.llegada[i - 1];
                registrarLlegada(e, cx, ped, e.llegada[i]);
                if (registro != null) registro.add(new Hito(Hito.Tipo.ENTREGA, e.llegada[i], reloj, px, py, px, py, p, p.cantidad));
                carga -= p.cantidad;
                continue;
            }
            int qx = p.x(), qy = p.y();
            if (p.tipo == TipoParada.ENTREGA) {   // destino bloqueado con la regla NODO_VECINO (Etapa 17)
                int[] punto = cx.puntoDeEntrega(px, py, qx, qy, reloj, cx.velocidad(tipo));
                qx = punto[0];
                qy = punto[1];
            }
            double d = cx.distanciaTramo(px, py, qx, qy, reloj, cx.velocidad(tipo));
            double salida = reloj;
            reloj = cx.avanzar(reloj, d / cx.velocidad(tipo));
            km += d;
            if (registro != null && d > 0)
                registro.add(new Hito(Hito.Tipo.TRAMO, salida, reloj, px, py, qx, qy, null, 0));
            switch (p.tipo) {
                case ENTREGA: {
                    Pedido ped = p.pedido;
                    if (ped.enAveriada != null && (trasvasados == null || !trasvasados.contains(ped.enAveriada)))
                        e.infactible("entrega sin trasvase previo");
                    reloj = cx.esperaDestino(qx, qy, reloj);
                    e.llegada[i] = reloj;
                    registrarLlegada(e, cx, ped, reloj);
                    double fin = cx.avanzar(reloj, HORAS_ENTREGA);
                    if (registro != null) registro.add(new Hito(Hito.Tipo.ENTREGA, reloj, fin, qx, qy, qx, qy, p, p.cantidad));
                    reloj = fin;
                    carga -= p.cantidad;
                    break;
                }
                case RECARGA: {
                    e.llegada[i] = reloj;
                    int a = cx.indiceAlmacen(p.almacen);
                    int seg = cargaSegmento(r, i + 1);
                    if (a < 0) e.infactible("almacén fuera del contexto");
                    else e.usoAlmacen[a] += seg;
                    carga += seg;
                    double fin = cx.avanzar(reloj, HORAS_RECARGA);
                    if (registro != null) registro.add(new Hito(Hito.Tipo.RECARGA, reloj, fin, qx, qy, qx, qy, p, seg));
                    reloj = fin;
                    if (carga > tipo.capacidadMaxima) e.infactible("capacidad");
                    break;
                }
                default: {   // TRASVASE
                    e.llegada[i] = reloj;
                    Contexto.Averiada av = cx.averiadas.get(p.unidadAveriada);
                    double fin = cx.avanzar(reloj, HORAS_TRASVASE);
                    if (av == null || fin > av.hastaH) e.infactible("trasvase fuera de tiempo");
                    if (trasvasados == null) trasvasados = new HashSet<>();
                    int seg = 0;
                    if (trasvasados.add(p.unidadAveriada)) {
                        for (int k = i + 1; k < n; k++) {
                            ParadaAlg q = r.paradas.get(k);
                            if (q.tipo == TipoParada.ENTREGA && p.unidadAveriada.equals(q.pedido.enAveriada)) seg += q.cantidad;
                        }
                    }
                    carga += seg;
                    if (registro != null) registro.add(new Hito(Hito.Tipo.TRASVASE, reloj, fin, qx, qy, qx, qy, p, seg));
                    reloj = fin;
                    if (carga > tipo.capacidadMaxima) e.infactible("capacidad");
                }
            }
            px = qx;
            py = qy;
        }
        // Regreso al almacén más cercano
        Contexto.AlmacenPlan fin = cx.almacenMasCercano(px, py, reloj);
        double d = cx.distanciaTramo(px, py, fin.almacen.x, fin.almacen.y, reloj, cx.velocidad(tipo));
        double salida = reloj;
        reloj = cx.avanzar(reloj, d / cx.velocidad(tipo));
        km += d;
        if (registro != null) {
            if (d > 0) registro.add(new Hito(Hito.Tipo.TRAMO, salida, reloj, px, py, fin.almacen.x, fin.almacen.y, null, 0));
            registro.add(new Hito(Hito.Tipo.FIN, reloj, reloj, fin.almacen.x, fin.almacen.y,
                    fin.almacen.x, fin.almacen.y, null, 0));
        }
        if (reloj > ini.noDisponibleDesdeH) e.infactible("mantenimiento");
        e.finH = reloj;
        e.km = km;
        e.costo = km * tipo.costoPorKilometro;
        // Costo de estabilidad (Etapa 18): entregas que el plan vigente tenía en otra unidad
        if (cx.asignacionVigente != null && cx.penalidadCambio > 0) {
            for (ParadaAlg p : r.paradas) {
                if (p.tipo != TipoParada.ENTREGA) continue;
                String antes = cx.asignacionVigente.get(p.pedido.id);
                // Parte nueva de este ciclo (SI-28): se compara con la unidad de la entrega de la que salió
                if (antes == null && p.pedido.idPadre != null) antes = cx.asignacionVigente.get(p.pedido.idPadre);
                if (antes != null && !antes.equals(r.unidad.codigo)) e.cambiosDeUnidad++;
            }
            e.costo += e.cambiosDeUnidad * cx.penalidadCambio;
        }
        // Holgura de seguridad (Etapa 22): solo S
        e.costo += e.faltaHolguraH * cx.penalidadHolgura;
        return e;
    }

    /** Plazo de una entrega a la que se llega en "llegada": los productos tarde suman en
     *  tarde (H se cuenta en productos, SI-28); si llega a tiempo, el margen que le falta
     *  para la holgura de seguridad suma en faltaHolguraH. */
    private static void registrarLlegada(EvalRuta e, Contexto cx, Pedido ped, double llegada) {
        if (llegada > ped.horaLimite()) e.tarde += ped.cantidad;
        else if (cx.conEstado && cx.holguraH > 0) {
            // El margen se mide hasta la hora límite EFECTIVA (SI-23): si el destino se
            // bloquea antes del plazo y hasta después, el margen real acaba cuando empieza
            // el bloqueo (un pequeño retraso obligaría a esperar y la entrega saldría tarde)
            double margen = cx.limiteEfectivo(ped) - llegada;
            if (margen < cx.holguraH) e.faltaHolguraH += cx.holguraH - margen;
        }
    }

    /** true si "b" entrega otra parte del mismo pedido justo después de "a": es la
     *  misma visita al cliente (reparto por productos, SI-28). */
    static boolean mismaVisita(ParadaAlg a, ParadaAlg b) {
        return a.tipo == TipoParada.ENTREGA && b.tipo == TipoParada.ENTREGA
                && a.pedido.idOriginal.equals(b.pedido.idOriginal) && a.x() == b.x() && a.y() == b.y();
    }

    /** Paquetes en almacén de las entregas desde "desde" hasta la próxima RECARGA. */
    private static int cargaSegmento(RutaAlg r, int desde) {
        int s = 0;
        for (int k = desde; k < r.paradas.size(); k++) {
            ParadaAlg q = r.paradas.get(k);
            if (q.tipo == TipoParada.RECARGA) break;
            if (q.tipo == TipoParada.ENTREGA && q.pedido.enAlmacen()) s += q.cantidad;
        }
        return s;
    }

    /** Recalcula distancia y costo de una ruta completa (recálculo directo). */
    static void recalcularDistanciaYCosto(RutaAlg r) {
        EvalRuta e = evaluarRuta(r);
        r.distanciaKm = e.km;
        r.costo = e.costo;
    }

    /** Hora de llegada (relativa al instante de planificación) a cada parada de la ruta. */
    static double[] horasLlegada(RutaAlg r) {
        return evaluarRuta(r).llegada;
    }

    /** true si la parada es una entrega y se llega después de la hora límite del pedido. */
    static boolean llegaTarde(ParadaAlg p, double horaLlegada) {
        return p.tipo == TipoParada.ENTREGA && horaLlegada > p.pedido.horaLimite();
    }

    /** Productos de la ruta que llegan fuera de plazo (SI-28). */
    static int pedidosTarde(RutaAlg r) {
        return evaluarRuta(r).tarde;
    }

    /** 4.1.4 Función objetivo jerárquica de dos niveles: recalcula H y S
     *  de una Solución completa a partir de sus rutas.
     *  H = productos sin asignar + productos fuera de plazo (SI-28: las unidades
     *  entregan productos; basta un producto tarde para el colapso).
     *  S = costo total en soles. Se compara primero H y luego S.
     *  Siempre usa la unidad REAL de cada ruta (tipo, velocidad y costo).
     *  Además registra las violaciones de restricciones duras (rutas
     *  infactibles, stock excedido); los operadores rechazan esos vecinos y,
     *  como resguardo, cada violación suma en H. */
    static void evaluarSolucion(Solucion s) {
        Contexto cx = Contexto.actual();
        int h = 0;
        for (Pedido p : s.pedidosSinAsignar) h += p.cantidad;
        double total = 0;
        int[] uso = new int[cx.almacenes.size()];
        int infactibles = 0;
        for (RutaAlg r : s.rutas) {
            EvalRuta e = evaluarRuta(r);
            r.distanciaKm = e.km;
            r.costo = e.costo;
            total += e.costo;
            h += e.tarde;   // un producto fuera de plazo incumple la política: cuenta en H
            if (!e.factible) { infactibles++; h += r.paradas.size() + 1; }
            for (int i = 0; i < uso.length; i++) uso[i] += e.usoAlmacen[i];
        }
        int exceso = 0;
        for (int i = 0; i < uso.length; i++) {
            double stock = cx.almacenes.get(i).stock;
            if (uso[i] > stock) exceso += (int) Math.ceil(uso[i] - stock);
        }
        s.rutasInfactibles = infactibles;
        s.excesoStock = exceso;
        s.H = h + exceso;
        s.S = total;
    }

    /** true si la solución (ya evaluada) cumple todas las restricciones duras. */
    static boolean esFactible(Solucion s) {
        return s.rutasInfactibles == 0 && s.excesoStock == 0;
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
        for (UnidadTransporte u : flota) vMax = Math.max(vMax, Contexto.actual().velocidad(u.tipo));
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
