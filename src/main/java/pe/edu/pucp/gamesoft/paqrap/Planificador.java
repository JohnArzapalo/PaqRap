package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que el Simulador necesita de un algoritmo de planificación (Etapa 11.2).
 *
 * En cada ciclo recibe el estado completo (EstadoPlanificacion):
 *  - el instante actual (contexto.instanteBaseH) y la base de tiempo: la hora
 *    0 relativa es ese instante; cada pedido llega con su hora de registro
 *    relativa (negativa si ya esperó), igual que en LectorPedidos.leerVentana;
 *  - todas las unidades operativas con su posición, la hora desde la que
 *    pueden empezar y su carga a bordo: cada unidad es un "almacén móvil";
 *  - los almacenes fijos con su stock y las unidades averiadas detenidas con
 *    su carga (almacenes temporales para TRASVASE);
 *  - las entregas a planificar: pendientes en almacén, a bordo de una unidad
 *    (Pedido.aBordoDe) o guardadas en una averiada (Pedido.enAveriada);
 *  - el plan vigente reparado con los pedidos nuevos insertados (contexto.planBase).
 * Devuelve el plan nuevo para TODAS las unidades operativas, incluidas las que
 * están en ruta (su ruta empieza en su posición actual). Restricción: una
 * entrega a bordo solo la entrega la unidad que la lleva (salvo trasvase).
 */
interface Planificador {

    /** Estado de un ciclo de planificación. */
    final class EstadoPlanificacion {
        final Contexto contexto;
        final List<Pedido> pedidos;
        final List<UnidadTransporte> flota;
        final long presupuestoMs, maxEvaluaciones, semilla;

        EstadoPlanificacion(Contexto contexto, List<Pedido> pedidos, List<UnidadTransporte> flota,
                            long presupuestoMs, long maxEvaluaciones, long semilla) {
            this.contexto = contexto;
            this.pedidos = pedidos;
            this.flota = flota;
            this.presupuestoMs = presupuestoMs;
            this.maxEvaluaciones = maxEvaluaciones;
            this.semilla = semilla;
        }
    }

    /** Planifica con el contexto del estado activo (Contexto.usar). */
    Plan planificar(EstadoPlanificacion estado);

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

    /** Búsqueda Tabú: parte del plan vigente reparado (o de C&W en el primer
     *  ciclo) y usa todo el presupuesto (sin corte por estancamiento). */
    static Planificador tabu(int duracionTabu) {
        return e -> {
            Contexto.usar(e.contexto);
            BusquedaTabu.setSemilla(e.semilla);
            Solucion s = BusquedaTabu.ejecutarDesdeCero(e.pedidos, e.flota, e.presupuestoMs, e.maxEvaluaciones,
                    duracionTabu, Integer.MAX_VALUE);
            return Plan.de(s, BusquedaTabu.ultimasIteraciones, BusquedaTabu.ultimasEvaluaciones);
        };
    }

    /** Algoritmo Genético + Split con estado (+ búsqueda local memética), con
     *  población sembrada desde el plan vigente. */
    static Planificador genetico(int poblacion) {
        return e -> {
            Contexto.usar(e.contexto);
            AlgoritmoGenetico.setSemilla(e.semilla);
            Solucion s = AlgoritmoGenetico.ejecutar(e.pedidos, e.flota, e.presupuestoMs, e.maxEvaluaciones, poblacion);
            return Plan.de(s, AlgoritmoGenetico.ultimasGeneraciones, AlgoritmoGenetico.ultimasEvaluaciones);
        };
    }
}
