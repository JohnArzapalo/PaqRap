package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R6: en el modo por evaluaciones, la misma semilla da exactamente la misma solución. */
class ReproducibilidadTest {

    @Test
    void tabuEsReproducibleEnModoEvaluaciones() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        List<UnidadTransporte> flota = Experimento.flotaOficial();

        BusquedaTabu.setSemilla(1001);
        Solucion a = BusquedaTabu.ejecutarDesdeCero(ped, flota, 0, 20_000, 8, Integer.MAX_VALUE);
        long evalA = BusquedaTabu.ultimasEvaluaciones, iterA = BusquedaTabu.ultimasIteraciones;

        BusquedaTabu.setSemilla(1001);
        Solucion b = BusquedaTabu.ejecutarDesdeCero(ped, flota, 0, 20_000, 8, Integer.MAX_VALUE);

        assertEquals(a.firma(), b.firma());
        assertEquals(a.S, b.S);
        assertEquals(a.H, b.H);
        assertEquals(evalA, BusquedaTabu.ultimasEvaluaciones);
        assertEquals(iterA, BusquedaTabu.ultimasIteraciones);
    }

    @Test
    void agEsReproducibleEnModoEvaluaciones() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        List<UnidadTransporte> flota = Experimento.flotaOficial();

        AlgoritmoGenetico.setSemilla(1001);
        Solucion a = AlgoritmoGenetico.ejecutar(ped, flota, 0, 5_000, 30);
        long evalA = AlgoritmoGenetico.ultimasEvaluaciones, genA = AlgoritmoGenetico.ultimasGeneraciones;

        AlgoritmoGenetico.setSemilla(1001);
        Solucion b = AlgoritmoGenetico.ejecutar(ped, flota, 0, 5_000, 30);

        assertEquals(a.firma(), b.firma());
        assertEquals(a.S, b.S);
        assertEquals(a.H, b.H);
        assertEquals(evalA, AlgoritmoGenetico.ultimasEvaluaciones);
        assertEquals(genA, AlgoritmoGenetico.ultimasGeneraciones);
    }
}
