package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Etapa 31 (SI-28, indicación del profesor): las unidades entregan PRODUCTOS,
 * no pedidos. Un pedido puede repartirse entre varias unidades según su
 * capacidad libre: una lleva n productos y otra el resto.
 */
class RepartoProductosTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private static List<UnidadTransporte> dosMotos() {
        return List.of(new UnidadTransporte("TM01", TipoUnidad.MOTO), new UnidadTransporte("TM02", TipoUnidad.MOTO));
    }

    /** Productos por pedido original en toda la solución (rutas y sin asignar). */
    private static Map<String, Integer> productosPorPedido(Solucion s) {
        Map<String, Integer> m = new HashMap<>();
        for (Pedido p : s.todasLasEntregas()) m.merge(p.idOriginal, p.cantidad, Integer::sum);
        return m;
    }

    @Test
    void unPedidoDeDiezSeRepartePorCapacidadEntreDosMotos() {
        Contexto.restablecer();
        Solucion s = Heuristicaconstructiva.construirSolucionInicial(
                List.of(new Pedido("A", 30, 14, 10, 36)), dosMotos());
        assertEquals(0, s.H, "sin productos sin asignar ni tarde");
        assertTrue(s.pedidosSinAsignar.isEmpty());
        assertEquals(2, s.vehiculosUsados(), "no cabe en una moto: dos unidades");
        for (RutaAlg r : s.rutas) {
            int carga = 0;
            for (ParadaAlg pa : r.paradas) if (pa.tipo == TipoParada.ENTREGA) carga += pa.cantidad;
            assertTrue(carga <= TipoUnidad.MOTO.capacidadMaxima, "capacidad de " + r.unidad.codigo);
        }
        assertEquals(Map.of("A", 10), productosPorPedido(s), "se conservan los 10 productos");
    }

    @Test
    void sinRepartoElPedidoQueNoCabeQuedaSinAsignarYHCuentaSusProductos() {
        Contexto.restablecer();
        Contexto.actual().reparto = false;
        Solucion s = Heuristicaconstructiva.construirSolucionInicial(
                List.of(new Pedido("A", 30, 14, 10, 36)), dosMotos());
        assertEquals(1, s.pedidosSinAsignar.size());
        assertEquals(10, s.H, "H se cuenta en productos");
    }

    @Test
    void partesConsecutivasDelMismoPedidoSonUnaSolaVisita() {
        Pedido entero = new Pedido("A", 30, 14, 6, 36);
        RutaAlg una = new RutaAlg();
        una.unidad = new UnidadTransporte("TM01", TipoUnidad.MOTO);
        una.paradas.add(ParadaAlg.entrega(entero));

        RutaAlg dos = new RutaAlg();
        dos.unidad = una.unidad;
        dos.paradas.add(ParadaAlg.entrega(entero.conCantidad(4)));
        dos.paradas.add(ParadaAlg.entrega(entero.parte("A+1", 2)));

        Compartido.EvalRuta e1 = Compartido.evaluarRuta(una), e2 = Compartido.evaluarRuta(dos);
        assertEquals(e2.llegada[0], e2.llegada[1], 1e-12, "misma llegada");
        assertEquals(e1.finH, e2.finH, 1e-12, "una sola hora de entrega");
        assertEquals(e1.km, e2.km, 1e-12);
    }

    @Test
    void elOperadorRepartoConservaLosProductosDeCadaPedido() throws IOException {
        Contexto.restablecer();
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        Solucion s = OperadoresVecindario.conUnidadesLibres(
                Heuristicaconstructiva.construirSolucionInicial(Instancias.sinteticoI1(), flota), flota);
        Map<String, Integer> esperado = productosPorPedido(s);
        Random azar = new Random(31);
        int aceptados = 0;
        for (int i = 0; i < 400; i++) {
            Movimiento m = OperadoresVecindario.reparto(s, azar);
            if (m == null) continue;
            s = m.solucion;
            aceptados++;
            assertEquals(esperado, productosPorPedido(s), "iteración " + i);
            Set<String> ids = new HashSet<>();
            for (Pedido p : s.todasLasEntregas()) assertTrue(ids.add(p.id), "id repetido " + p.id);
            assertTrue(Compartido.esFactible(s));
        }
        assertTrue(aceptados > 0, "el operador produjo vecinos");
    }

    @Test
    void elSimuladorEntregaTodosLosProductosRepartidos() {
        Simulador.Config cfg = SimuladorTest.config(30);
        cfg.horizonteMin = 600;
        cfg.reparto = true;
        Simulador.Resultado r = Simulador.simular(List.of(new Pedido("G", "G", 30, 14, 10, 4, 0.0)),
                dosMotos(), Planificador.tabu(8), cfg);
        assertTrue(r.censurada, "sin colapso: " + r.causaColapso);
        assertEquals(1, r.pedidosEnPlazo);
        int entregados = 0, visitas = 0;
        for (Simulador.Evt e : r.registro) {
            if (e.tipo != Simulador.Evt.T.ENTREGA) continue;
            entregados += e.cantidad;
            visitas++;
        }
        assertEquals(10, entregados, "los 10 productos");
        assertTrue(visitas >= 2, "no caben en una moto: al menos dos entregas");
    }
}
