package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompartidoTest {

    /**
     * Caso calculado a mano (ruta C&W de I2 en la versión expuesta):
     * auto a 40 km/h sale de (27,14).
     *  - P3 en (35,10): 8 + 4 = 12 km -> llega a 12/40 = 0.30 h (límite 1 h: OK).
     *  - +1 h de entrega; P2 en (10,20): 25 + 10 = 35 km -> 0.875 h -> llega a 2.175 h (límite 1 h: TARDE).
     */
    @Test
    void horasLlegadaYLlegaTardeCalculadasAMano() {
        assumeTrue(TipoUnidad.AUTO.velocidadPromedio == 40.0, "el caso asume la velocidad del enunciado");
        assumeTrue(Compartido.HORAS_ENTREGA == 1.0, "el caso asume 1 h de entrega");
        List<Pedido> ped = Instancias.mainI2();
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("U1", TipoUnidad.AUTO);
        r.paradas.add(ParadaAlg.entrega(Instancias.buscar(ped, "P3")));
        r.paradas.add(ParadaAlg.entrega(Instancias.buscar(ped, "P2")));

        double[] llegada = Compartido.horasLlegada(r);
        assertArrayEquals(new double[]{0.30, 2.175}, llegada, 1e-9);
        assertFalse(Compartido.llegaTarde(r.paradas.get(0), llegada[0]));
        assertTrue(Compartido.llegaTarde(r.paradas.get(1), llegada[1]));
        assertEquals(1, Compartido.pedidosTarde(r));
    }

    @Test
    void rutaVaciaCuestaCeroYNoCuentaComoVehiculo() {
        Solucion s = new Solucion();
        RutaAlg vacia = new RutaAlg();
        vacia.unidad = new UnidadTransporte("TB01", TipoUnidad.BICICLETA);
        s.rutas.add(vacia);
        Compartido.evaluarSolucion(s);
        assertEquals(0.0, s.S);
        assertEquals(0, s.H);
        assertEquals(0, s.vehiculosUsados());
    }

    @Test
    void cumpleCapacidad() {
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("TM01", TipoUnidad.MOTO);
        r.paradas.add(ParadaAlg.entrega(new Pedido("A", 1, 1, 8, 36)));
        assertTrue(Compartido.cumpleCapacidad(r));
        r.paradas.add(ParadaAlg.entrega(new Pedido("B", 1, 1, 1, 36)));
        assertFalse(Compartido.cumpleCapacidad(r));
    }

    /** Un pedido original está en plazo solo si TODAS sus partes llegan en plazo. */
    @Test
    void pedidoOriginalConUnaParteSinAsignarNoEstaEnPlazo() {
        Pedido parte1 = new Pedido("c1-L1-1", "c1-L1", 28, 14, 24, 36, 0);
        Pedido parte2 = new Pedido("c1-L1-2", "c1-L1", 28, 14, 6, 36, 0);
        Pedido otro = new Pedido("c2-L2", "c2-L2", 27, 15, 2, 36, 0);
        Solucion s = new Solucion();
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("TA01", TipoUnidad.AUTO);
        r.paradas.add(ParadaAlg.entrega(parte1));
        s.rutas.add(r);
        RutaAlg r2 = new RutaAlg();
        r2.unidad = new UnidadTransporte("TM01", TipoUnidad.MOTO);
        r2.paradas.add(ParadaAlg.entrega(otro));
        s.rutas.add(r2);
        s.pedidosSinAsignar = new ArrayList<>(List.of(parte2));
        Compartido.evaluarSolucion(s);
        assertArrayEquals(new int[]{1, 2}, Compartido.pedidosOriginalesEnPlazo(s));
    }

    @Test
    void vencidoAlPlanificar() {
        assumeTrue(TipoUnidad.AUTO.velocidadPromedio == 40.0, "el caso asume la velocidad del enunciado");
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO));
        // 40 km del central: el auto tarda al menos 1 h; hora límite 0.5 -> vencido
        assertTrue(Compartido.vencidoAlPlanificar(new Pedido("A", "A", 67, 14, 1, 4, -3.5), flota));
        // hora límite 3.5 -> todavía se puede cumplir
        assertFalse(Compartido.vencidoAlPlanificar(new Pedido("B", "B", 67, 14, 1, 4, -0.5), flota));
    }
}
