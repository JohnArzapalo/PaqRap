package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que el Simulador necesita de un algoritmo de planificación.
 *
 * Base de tiempo: la hora 0 es el instante de la replanificación. Cada
 * pedido llega con su horaRegistro RELATIVA a ese instante (negativa si ya
 * esperó), igual que en LectorPedidos.leerVentana; las unidades disponibles
 * salen del central en la hora 0. Así Compartido.horasLlegada y el Split
 * usan la misma base que en el experimento estático, sin cambiar los
 * algoritmos.
 */
interface Planificador {

    /**
     * @param pendientes      entregas aún no despachadas (horas relativas al instante)
     * @param disponibles     unidades que están en el central en este instante
     * @param presupuestoMs   Ta (ms); se ignora si maxEvaluaciones > 0
     * @param maxEvaluaciones 0 = parada por tiempo
     * @param semilla         semilla de esta llamada
     */
    Plan planificar(List<Pedido> pendientes, List<UnidadTransporte> disponibles,
                    long presupuestoMs, long maxEvaluaciones, long semilla);

    /** Resultado de una llamada: rutas no vacías, entregas sin asignar y contadores. */
    class Plan {
        final List<RutaAlg> rutas = new ArrayList<>();
        List<Pedido> sinAsignar = new ArrayList<>();
        long iteraciones, evaluaciones;

        static Plan de(Solucion s, long iteraciones, long evaluaciones) {
            Plan p = new Plan();
            for (RutaAlg r : s.rutas) if (!r.estaVacia()) p.rutas.add(r);
            p.sinAsignar = new ArrayList<>(s.pedidosSinAsignar);
            p.iteraciones = iteraciones;
            p.evaluaciones = evaluaciones;
            return p;
        }
    }

    /** Búsqueda Tabú: C&W + búsqueda, usando todo el presupuesto (sin corte por estancamiento). */
    static Planificador tabu(int duracionTabu) {
        return (pendientes, disponibles, ta, maxEval, semilla) -> {
            BusquedaTabu.setSemilla(semilla);
            Solucion s = BusquedaTabu.ejecutarDesdeCero(pendientes, disponibles, ta, maxEval,
                    duracionTabu, Integer.MAX_VALUE);
            return Plan.de(s, BusquedaTabu.ultimasIteraciones, BusquedaTabu.ultimasEvaluaciones);
        };
    }

    /** Algoritmo Genético + Split (+ búsqueda local memética si está activa). */
    static Planificador genetico(int poblacion) {
        return (pendientes, disponibles, ta, maxEval, semilla) -> {
            AlgoritmoGenetico.setSemilla(semilla);
            Solucion s = AlgoritmoGenetico.ejecutar(pendientes, disponibles, ta, maxEval, poblacion);
            return Plan.de(s, AlgoritmoGenetico.ultimasGeneraciones, AlgoritmoGenetico.ultimasEvaluaciones);
        };
    }
}
