package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R1: la lista tabú debe impedir deshacer un movimiento mientras la entrada esté vigente. */
class ListaTabuTest {

    private static void aceptar(ListaTabu lista, Movimiento m, int iteracion, int duracion) {
        for (ParTabu p : m.prohibir) lista.registrar(p, iteracion + duracion);
    }

    @Test
    void elMovimientoInversoEntreRutasEsTabu() {
        ListaTabu lista = new ListaTabu();
        // Iteración 0: P3 pasa de U1 a U2 (se prohíbe que vuelva a U1)
        aceptar(lista, Movimiento.reubicacion("P3", "U1", 0, "U2", 1), 0, 8);
        // Iteración 1: devolver P3 de U2 a U1 es tabú (se compara contra el DESTINO U1)
        Movimiento inverso = Movimiento.reubicacion("P3", "U2", 1, "U1", 0);
        assertTrue(inverso.esTabu(lista, 1));
        // Llevar P3 a otra unidad (U3) no es tabú
        assertFalse(Movimiento.reubicacion("P3", "U2", 1, "U3", 0).esTabu(lista, 1));
    }

    @Test
    void laEntradaVencidaNoEsTabu() {
        ListaTabu lista = new ListaTabu();
        aceptar(lista, Movimiento.reubicacion("P3", "U1", 0, "U2", 1), 0, 8);
        Movimiento inverso = Movimiento.reubicacion("P3", "U2", 1, "U1", 0);
        assertTrue(inverso.esTabu(lista, 7));     // vence en la iteración 8
        assertFalse(inverso.esTabu(lista, 8));    // ya vencida
        assertFalse(inverso.esTabu(lista, 100));  // aunque no se haya limpiado la lista
    }

    @Test
    void dentroDeLaMismaRutaSeProhibeVolverALaPosicionAnterior() {
        ListaTabu lista = new ListaTabu();
        // P3 pasa de la posición 2 a la 0 dentro de U1
        aceptar(lista, Movimiento.reubicacion("P3", "U1", 2, "U1", 0), 0, 8);
        assertTrue(Movimiento.reubicacion("P3", "U1", 0, "U1", 2).esTabu(lista, 1));
        assertFalse(Movimiento.reubicacion("P3", "U1", 0, "U1", 1).esTabu(lista, 1));
    }

    @Test
    void elIntercambioInversoEsTabu() {
        ListaTabu lista = new ListaTabu();
        // P3 (U1, pos 0) <-> P5 (U2, pos 0)
        aceptar(lista, Movimiento.intercambio("P3", "U1", 0, "P5", "U2", 0), 0, 8);
        // Deshacerlo: P3 (ahora en U2) <-> P5 (ahora en U1)
        assertTrue(Movimiento.intercambio("P3", "U2", 0, "P5", "U1", 0).esTabu(lista, 1));
    }
}
