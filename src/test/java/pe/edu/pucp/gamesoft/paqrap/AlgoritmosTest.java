package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Criterios de éxito de las etapas 1 y 2 sobre las instancias de Main y del archivo sintético. */
class AlgoritmosTest {

    /** Split sobre I1 de Main con la permutación que encontró el AG en la exposición. */
    @Test
    void splitEnI1DeMain() {
        List<Pedido> ped = Instancias.mainI1();
        List<UnidadTransporte> flota = Instancias.flotaMain();
        List<Pedido> perm = new java.util.ArrayList<>();
        for (String id : new String[]{"P5", "P1", "P4", "P2", "P6", "P3"}) perm.add(Instancias.buscar(ped, id));
        Map<String, TipoUnidad> tipos = new HashMap<>();
        for (Pedido p : perm) tipos.put(p.id, TipoUnidad.AUTO);
        tipos.put("P3", TipoUnidad.MOTO);

        Solucion s = AlgoritmoGenetico.split(new Cromosoma(perm, tipos), flota);
        assertEquals(0, s.H);
        assertTrue(s.rutas.size() <= flota.size());
        assertEquals(672.0, s.S, 1e-9);
    }

    @Test
    void agEnI1DeMainNoDejaPedidosFueraNiUsaMasVehiculosQueLaFlota() {
        List<UnidadTransporte> flota = Instancias.flotaMain();
        AlgoritmoGenetico.setSemilla(11);
        Solucion s = AlgoritmoGenetico.ejecutar(Instancias.mainI1(), flota, 0, 3000, 30);
        assertEquals(0, s.H);
        assertTrue(s.vehiculosUsados() <= flota.size());
    }

    /** Criterio 1.3: con Intercambio, Tabú llega a H = 0 en I2 (P3 <-> P5). */
    @Test
    void tabuLlegaAHCeroEnI2DeMain() {
        List<UnidadTransporte> flota = Instancias.flotaMain();
        Solucion inicial = Heuristicaconstructiva.construirSolucionInicial(Instancias.mainI2(), flota);
        BusquedaTabu.setSemilla(7);
        Solucion s = BusquedaTabu.ejecutar(inicial, flota, 0, 5000, 8, 300);
        assertEquals(0, s.H);
    }

    /** Criterio 1.5: C&W usa menos de 21 vehículos en I1_1h del archivo sintético. */
    @Test
    void clarkeWrightConsolidaEnI1Sintetico() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        Solucion s = Heuristicaconstructiva.construirSolucionInicial(ped, Experimento.flotaOficial());
        assertTrue(s.vehiculosUsados() < 21, "vehículos usados: " + s.vehiculosUsados());
        for (RutaAlg r : s.rutas) assertTrue(Compartido.evaluarRuta(r).factible);
    }

    /** Criterio 6.3: con la inserción por posición y ruta, C&W baja de H = 2 en I2 de Main. */
    @Test
    void clarkeWrightConInsercionMejoradaEnI2DeMain() {
        Solucion s = Heuristicaconstructiva.construirSolucionInicial(Instancias.mainI2(), Instancias.flotaMain());
        assertTrue(s.H < 2, "H = " + s.H);
    }

    /** 2-opt y cross-exchange conservan todas las entregas y nunca violan la capacidad. */
    @Test
    void operadoresProvisionalesConservanEntregasYCapacidad() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        Solucion s = OperadoresVecindario.conUnidadesLibres(
                Heuristicaconstructiva.construirSolucionInicial(ped, flota), flota);
        java.util.Random azar = new java.util.Random(5);
        int aplicados = 0;
        for (int k = 0; k < 2000; k++) {
            Movimiento m = (k % 2 == 0) ? OperadoresVecindario.dosOpt(s, azar)
                                        : OperadoresVecindario.crossExchange(s, azar);
            if (m == null) continue;
            aplicados++;
            s = m.solucion;
            assertEquals(ped.size(), s.todasLasEntregas().size());
            for (RutaAlg r : s.rutas) assertTrue(Compartido.evaluarRuta(r).factible);
        }
        assertTrue(aplicados > 100);
    }

    /** Tabú puede insertar entregas sin asignar en unidades libres (R3). */
    @Test
    void tabuInsertaPedidosSinAsignarEnUnidadesLibres() {
        List<Pedido> ped = Instancias.mainI1();
        List<UnidadTransporte> flota = Instancias.flotaMain();
        Solucion inicial = new Solucion();
        inicial.pedidosSinAsignar.addAll(ped);   // ninguna ruta: todo sin asignar
        Compartido.evaluarSolucion(inicial);
        BusquedaTabu.setSemilla(3);
        Solucion s = BusquedaTabu.ejecutar(inicial, flota, 0, 5000, 8, 300);
        assertEquals(0, s.pedidosSinAsignar.size());
        assertEquals(0, s.H);
    }
}
