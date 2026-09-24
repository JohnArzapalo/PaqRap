package pe.edu.pucp.gamesoft.paqrap;

import java.util.List;
import java.util.Random;

/**
 * 4.2.2 Búsqueda Tabú, ya traducida a Java.
 *
 * En cada iteración se genera una LISTA DE CANDIDATOS de N vecinos y se
 * acepta el mejor que no sea tabú (o el mejor tabú que cumpla aspiración).
 * Si todos son tabú y ninguno aspira, se acepta el menos malo para no
 * quedar bloqueado.
 *
 * Operadores (en OperadoresVecindario, compartidos con el AG):
 *  - Reubicación, Intercambio, 2-opt y Cross-exchange, elegidos al azar con
 *    pesos configurables (tabu.peso_*). 2-opt y Cross-exchange son
 *    PROVISIONALES: verificar contra el ISA §4.2.
 *  - Inserción: si hay entregas sin asignar, el primer candidato de cada
 *    iteración es una Inserción en la mejor posición factible.
 *
 * La solución de trabajo contiene TODAS las unidades de la flota: las que
 * no tienen pedidos aparecen como rutas vacías (0 km, S/ 0, no cuentan como
 * vehículos usados) para poder recibir entregas.
 *
 * Parada: por presupuesto de tiempo Ta (criterio principal, hace justa la
 * comparación entre algoritmos con costos por evaluación distintos) o, si
 * maxEvaluaciones > 0, por número de evaluaciones (modo reproducible: con
 * la misma semilla da exactamente la misma solución). Una "evaluación" es
 * una llamada a Compartido.evaluarSolucion sobre una solución completa.
 */
class BusquedaTabu {

    private static Random AZAR = new Random(7);   // semilla por defecto (reproducible)

    /** Vecinos candidatos por iteración (tabu.candidatos). */
    static int CANDIDATOS = (int) Parametros.entero("tabu.candidatos", 20);
    /** Pesos relativos de los operadores (no necesitan sumar 1). */
    static double PESO_REUBICACION = Parametros.decimal("tabu.peso_reubicacion", 0.35);
    static double PESO_INTERCAMBIO = Parametros.decimal("tabu.peso_intercambio", 0.35);
    static double PESO_2OPT = Parametros.decimal("tabu.peso_2opt", 0.15);
    static double PESO_CROSS = Parametros.decimal("tabu.peso_cross", 0.15);
    /** Peso del operador Recarga (solo en la simulación con estado; Etapa 10.2). */
    static double PESO_RECARGA = Parametros.decimal("tabu.peso_recarga", 0.10);

    // ===== Contadores de la última ejecución (R6) =====
    static long ultimasIteraciones;
    static long ultimasEvaluaciones;
    /** Milisegundos desde el inicio hasta que se encontró la mejor solución. */
    static long ultimoTiempoMejorMs;

    /** Fija la semilla antes de ejecutar. Cada réplica del experimento usa una distinta. */
    static void setSemilla(long semilla) {
        AZAR = new Random(semilla);
    }

    /**
     * Corrida del experimento: obtiene la solución inicial y busca. El
     * cronómetro empieza ANTES de obtenerla, de modo que consume parte del
     * mismo Ta, igual que la población inicial del AG consume parte del suyo (R10).
     * Solución inicial (Etapa 11.3, "replanificar, no volver a planificar"):
     * el plan vigente reparado del contexto (con los pedidos nuevos ya
     * insertados) si existe; si no (primer ciclo o modo estático), Clarke & Wright.
     */
    static Solucion ejecutarDesdeCero(List<Pedido> pedidos, List<UnidadTransporte> flota,
                                      long presupuestoMs, long maxEvaluaciones,
                                      int duracionTabu, int maxSinMejora) {
        long inicio = System.currentTimeMillis();
        Solucion planBase = Contexto.actual().planBase;
        Solucion inicial = planBase != null ? planBase.copiar()
                : Heuristicaconstructiva.construirSolucionInicial(pedidos, flota);
        return buscar(inicial, flota, inicio, presupuestoMs, maxEvaluaciones, duracionTabu, maxSinMejora);
    }

    /** Busca a partir de una solución inicial ya construida (demo de Main). */
    static Solucion ejecutar(Solucion solucionInicial, List<UnidadTransporte> flota,
                             long presupuestoMs, long maxEvaluaciones,
                             int duracionTabu, int maxSinMejora) {
        return buscar(solucionInicial, flota, System.currentTimeMillis(), presupuestoMs,
                      maxEvaluaciones, duracionTabu, maxSinMejora);
    }

    private static Solucion buscar(Solucion solucionInicial, List<UnidadTransporte> flota, long inicio,
                                   long presupuestoMs, long maxEvaluaciones,
                                   int duracionTabu, int maxSinMejora) {
        Solucion actual = OperadoresVecindario.conUnidadesLibres(solucionInicial, flota);
        Compartido.evaluarSolucion(actual);
        long evaluaciones = 1;
        Solucion mejorGlobal = actual.copiar();
        long tiempoMejor = System.currentTimeMillis() - inicio;

        ListaTabu tabu = new ListaTabu();
        int iteracion = 0;
        int iteracionesSinMejora = 0;

        while (iteracionesSinMejora < maxSinMejora
                && !debeDetenerse(inicio, presupuestoMs, maxEvaluaciones, evaluaciones, iteracion)) {

            Movimiento mejorPermitido = null;   // mejor no tabú, o tabú que cumple aspiración
            Movimiento menosMaloTabu = null;    // respaldo si todos son tabú
            for (int c = 0; c < CANDIDATOS; c++) {
                Movimiento m = generarVecino(actual, c == 0);
                if (m == null) continue;
                evaluaciones++;
                boolean aspira = Compartido.mejorQue(m.solucion, mejorGlobal);
                if (!m.esTabu(tabu, iteracion) || aspira) {
                    if (mejorPermitido == null || Compartido.mejorQue(m.solucion, mejorPermitido.solucion))
                        mejorPermitido = m;
                } else if (menosMaloTabu == null || Compartido.mejorQue(m.solucion, menosMaloTabu.solucion)) {
                    menosMaloTabu = m;
                }
            }

            Movimiento aceptado = mejorPermitido != null ? mejorPermitido : menosMaloTabu;
            if (aceptado != null) {
                actual = aceptado.solucion;
                for (ParTabu p : aceptado.prohibir) tabu.registrar(p, iteracion + duracionTabu);
                tabu.limpiarVencidos(iteracion);
                if (Compartido.mejorQue(actual, mejorGlobal)) {
                    mejorGlobal = actual.copiar();
                    iteracionesSinMejora = 0;
                    tiempoMejor = System.currentTimeMillis() - inicio;
                } else {
                    iteracionesSinMejora++;
                }
            } else {
                iteracionesSinMejora++;   // ningún vecino factible en esta iteración
            }
            iteracion++;
        }

        ultimasIteraciones = iteracion;
        ultimasEvaluaciones = evaluaciones;
        ultimoTiempoMejorMs = tiempoMejor;
        return mejorGlobal;
    }

    /** Modo por evaluaciones (si maxEvaluaciones > 0; se ignora el tiempo) o por Ta.
     *  El tope de iteraciones evita un ciclo infinito si en el modo por
     *  evaluaciones ninguna iteración produjera vecinos factibles. */
    private static boolean debeDetenerse(long inicio, long presupuestoMs, long maxEvaluaciones,
                                         long evaluaciones, int iteracion) {
        if (maxEvaluaciones > 0) return evaluaciones >= maxEvaluaciones || iteracion >= maxEvaluaciones;
        return Compartido.debeDetenerse(inicio, presupuestoMs);
    }

    /** Genera un vecino evaluado, o null si no hay uno factible. Si hay entregas
     *  sin asignar, el primer candidato de cada iteración es una Inserción; los
     *  demás, un operador elegido al azar según sus pesos. */
    private static Movimiento generarVecino(Solucion base, boolean primerCandidato) {
        if (primerCandidato && !base.pedidosSinAsignar.isEmpty()) return OperadoresVecindario.insercion(base, AZAR);
        boolean conEstado = Contexto.actual().conEstado;
        double total = PESO_REUBICACION + PESO_INTERCAMBIO + PESO_2OPT + PESO_CROSS + (conEstado ? PESO_RECARGA : 0);
        double r = AZAR.nextDouble() * total;
        if (r < PESO_REUBICACION) return OperadoresVecindario.reubicacion(base, AZAR);
        r -= PESO_REUBICACION;
        if (r < PESO_INTERCAMBIO) return OperadoresVecindario.intercambio(base, AZAR);
        r -= PESO_INTERCAMBIO;
        if (r < PESO_2OPT) return OperadoresVecindario.dosOpt(base, AZAR);
        r -= PESO_2OPT;
        if (!conEstado || r < PESO_CROSS) return OperadoresVecindario.crossExchange(base, AZAR);
        return OperadoresVecindario.recarga(base, AZAR);
    }
}
