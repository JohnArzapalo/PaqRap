package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Etapa 27: el AG respeta su presupuesto dentro de la generación, no solo al terminarla.
 * Antes, con cargas altas, usaba 2.75 s de media con Ta = 2 s (más cómputo que Tabú).
 */
class PresupuestoAgTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    /** Modo por evaluaciones (determinista): con tope 45 y población 30, se detiene a mitad de la
     *  primera generación. Antes la terminaba entera (29 hijos más búsqueda local) y se pasaba. */
    @Test
    void noSuperaElTopeDeEvaluacionesAMitadDeGeneracion() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        AlgoritmoGenetico ag = new AlgoritmoGenetico(1001);
        Solucion s = ag.ejecutar(ped, flota, 0, 45, 30);
        // Margen de 2: la búsqueda local puede sumar una decodificación y una evaluación
        // después de la última revisión del tope
        assertTrue(ag.ultimasEvaluaciones <= 45 + 2, "evaluaciones = " + ag.ultimasEvaluaciones);
        assertEquals(ped.size(), s.todasLasEntregas().size());   // la solución sigue completa
    }

    /** Si el tope se agota durante la población inicial, se usan los individuos ya evaluados. */
    @Test
    void presupuestoAgotadoEnLaPoblacionInicialDevuelveUnaSolucionValida() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        AlgoritmoGenetico ag = new AlgoritmoGenetico(1001);
        Solucion s = ag.ejecutar(ped, flota, 0, 5, 30);
        assertTrue(ag.ultimasEvaluaciones <= 5 + 2, "evaluaciones = " + ag.ultimasEvaluaciones);
        assertEquals(0, ag.ultimasGeneraciones);
        assertEquals(ped.size(), s.todasLasEntregas().size());
    }

    /** Modo por tiempo: el tiempo por llamada no supera el presupuesto más un margen pequeño
     *  (la decodificación final del mejor cromosoma, fuera del ciclo). */
    @Test
    void respetaElPresupuestoDeTiempo() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        AlgoritmoGenetico ag = new AlgoritmoGenetico(1001);
        long t0 = System.currentTimeMillis();
        ag.ejecutar(ped, flota, 300, 0, 30);
        long ms = System.currentTimeMillis() - t0;
        assertTrue(ms < 300 + 200, "tardó " + ms + " ms con presupuesto de 300 ms");
    }
}
