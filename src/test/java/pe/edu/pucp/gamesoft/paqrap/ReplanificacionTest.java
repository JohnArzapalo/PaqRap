package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Etapas 10 y 11: recargas, stock, estado de las unidades y replanificación. */
class ReplanificacionTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private static final UnidadTransporte U1 = new UnidadTransporte("TA01", TipoUnidad.AUTO);
    private static final UnidadTransporte U2 = new UnidadTransporte("TA02", TipoUnidad.AUTO);

    /** U1 va en ruta por (40,20) (llega a ese nodo en 0.5 h) con la entrega E a bordo;
     *  U2 está libre en el central. Hay un pedido nuevo N en almacén. */
    private static Contexto contexto() {
        Contexto cx = new Contexto();
        cx.conEstado = true;
        cx.almacenes.add(new Contexto.AlmacenPlan(Compartido.ALMACEN_NOROESTE, 1000));
        cx.almacenes.add(new Contexto.AlmacenPlan(Compartido.ALMACEN_ESTE, 1000));
        cx.inicios.put("TA01", new Contexto.Inicio(40, 20, 0.5, Double.POSITIVE_INFINITY));
        return cx;
    }

    private static List<Pedido> pedidos() {
        Pedido e = new Pedido("E", "E", 45, 20, 10, 36, -1.0);
        e.aBordoDe = "TA01";
        Pedido n = new Pedido("N", "N", 30, 30, 5, 36, 0.0);
        return new ArrayList<>(List.of(e, n));
    }

    @Test
    void unaUnidadEnRutaRecibeUnPlanQueEmpiezaEnSuPosicion() {
        Contexto cx = contexto();
        Planificador.Plan plan = Planificador.tabu(8).planificar(new Planificador.EstadoPlanificacion(
                cx, pedidos(), List.of(U1, U2), 0, 2000, 5));
        RutaAlg r1 = null;
        for (RutaAlg r : plan.rutas) if (r.unidad == U1) r1 = r;
        List<Hito> hitos = new ArrayList<>();
        Compartido.evaluarRuta(r1, hitos);
        Hito primero = hitos.get(0);
        assertEquals(Hito.Tipo.TRAMO, primero.tipo);
        assertEquals(40, primero.x1);
        assertEquals(20, primero.y1);
        assertEquals(0.5, primero.inicioH, 1e-9);
    }

    @Test
    void laCargaABordoSeRespeta() {
        Contexto cx = contexto();
        Contexto.usar(cx);
        List<Pedido> ps = pedidos();
        // La entrega a bordo de TA01 no la puede hacer TA02
        RutaAlg r2 = new RutaAlg();
        r2.unidad = U2;
        r2.paradas.add(ParadaAlg.entrega(ps.get(0)));
        assertFalse(Compartido.evaluarRuta(r2).factible);
        // Y una entrega en almacén no la puede hacer una unidad en ruta sin pasar antes por una RECARGA
        RutaAlg r1 = new RutaAlg();
        r1.unidad = U1;
        r1.paradas.add(ParadaAlg.entrega(ps.get(1)));
        assertFalse(Compartido.evaluarRuta(r1).factible);
        r1.paradas.add(0, ParadaAlg.recarga(Compartido.ALMACEN_CENTRAL));
        assertTrue(Compartido.evaluarRuta(r1).factible);
        // Ambos algoritmos mantienen E en TA01
        for (Planificador p : List.of(Planificador.tabu(8), Planificador.genetico(20))) {
            Planificador.Plan plan = p.planificar(new Planificador.EstadoPlanificacion(
                    contexto(), pedidos(), List.of(U1, U2), 0, 2000, 5));
            boolean enU1 = false;
            for (RutaAlg r : plan.rutas)
                for (ParadaAlg pa : r.paradas)
                    if (pa.tipo == TipoParada.ENTREGA && pa.pedido.id.equals("E")) enU1 = r.unidad == U1;
            assertTrue(enU1);
        }
    }

    @Test
    void noSePlanificaUnaRecargaEnUnAlmacenSinStock() {
        Contexto cx = new Contexto();
        cx.conEstado = true;
        cx.almacenes.add(new Contexto.AlmacenPlan(Compartido.ALMACEN_NOROESTE, 3));   // solo 3 paquetes
        cx.inicios.put("TA01", new Contexto.Inicio(12, 30, 0, Double.POSITIVE_INFINITY));
        Contexto.usar(cx);
        Solucion s = new Solucion();
        RutaAlg r = new RutaAlg();
        r.unidad = U1;
        r.paradas.add(ParadaAlg.recarga(Compartido.ALMACEN_NOROESTE));
        r.paradas.add(ParadaAlg.entrega(new Pedido("N", 10, 40, 5, 36)));
        s.rutas.add(r);
        Compartido.evaluarSolucion(s);
        assertFalse(Compartido.esFactible(s));   // 5 > 3
        // La inserción elige el central (infinito) aunque Nor-Oeste esté más cerca
        Solucion vacia = new Solucion();
        RutaAlg libre = new RutaAlg();
        libre.unidad = U1;
        vacia.rutas.add(libre);
        OperadoresVecindario.PosicionInsercion pos = OperadoresVecindario.mejorInsercion(vacia, vacia.rutas,
                new Pedido("N", 10, 40, 5, 36));
        assertEquals(Compartido.ALMACEN_CENTRAL, pos.paradas.get(0).almacen);
    }

    /** 11.5: replanificar desde el plan vigente no empeora (H, S) frente al plan vigente reparado. */
    @Test
    void replanificarNoEmpeoraElPlanVigenteReparado() {
        for (Planificador p : List.of(Planificador.tabu(8), Planificador.genetico(20))) {
            Contexto cx = contexto();
            Contexto.usar(cx);
            List<Pedido> ps = pedidos();
            // Plan vigente reparado: E en TA01; N insertado en la mejor posición
            Solucion base = new Solucion();
            RutaAlg r1 = new RutaAlg();
            r1.unidad = U1;
            r1.paradas.add(ParadaAlg.entrega(ps.get(0)));
            RutaAlg r2 = new RutaAlg();
            r2.unidad = U2;
            base.rutas.add(r1);
            base.rutas.add(r2);
            OperadoresVecindario.mejorInsercion(base, base.rutas, ps.get(1)).aplicar();
            Compartido.evaluarSolucion(base);
            cx.planBase = base;
            Planificador.Plan plan = p.planificar(new Planificador.EstadoPlanificacion(
                    cx, ps, List.of(U1, U2), 0, 3000, 9));
            Contexto.usar(cx);
            Solucion nuevo = new Solucion();
            nuevo.rutas.addAll(plan.rutas);
            nuevo.pedidosSinAsignar.addAll(plan.sinAsignar);
            Compartido.evaluarSolucion(nuevo);
            assertFalse(Compartido.mejorQue(base, nuevo), "el plan nuevo empeora el vigente");
        }
    }

    /** 10.2: el AG con estado arma varios viajes con RECARGA cuando la carga supera la capacidad. */
    @Test
    void splitConEstadoArmaVariosViajes() {
        Contexto cx = new Contexto();
        cx.conEstado = true;
        Contexto.usar(cx);
        UnidadTransporte moto = new UnidadTransporte("TM01", TipoUnidad.MOTO);
        List<Pedido> ps = List.of(new Pedido("A", 29, 14, 8, 36), new Pedido("B", 25, 14, 8, 36));
        Planificador.Plan plan = Planificador.genetico(10).planificar(new Planificador.EstadoPlanificacion(
                cx, new ArrayList<>(ps), List.of(moto), 0, 1000, 3));
        assertTrue(plan.sinAsignar.isEmpty());
        RutaAlg r = plan.rutas.get(0);
        int entregas = 0, recargas = 0;
        for (ParadaAlg pa : r.paradas) {
            if (pa.tipo == TipoParada.ENTREGA) entregas++;
            if (pa.tipo == TipoParada.RECARGA) recargas++;
        }
        assertEquals(2, entregas);
        assertEquals(1, recargas);   // el primer viaje sale cargado del central; el segundo recarga
        assertTrue(Compartido.evaluarRuta(r).factible);
    }
}
