package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.List;

/** Ruta de una unidad (ISA apartado 4.1.1). Una ruta sin paradas representa
 *  una unidad libre: cuesta 0 y no cuenta como vehículo usado. */
class RutaAlg {
    UnidadTransporte unidad;
    List<ParadaAlg> paradas = new ArrayList<>();
    double distanciaKm;
    double costo;

    int cargaTotal() {
        int c = 0;
        for (ParadaAlg p : paradas) {
            if (p.tipo == TipoParada.ENTREGA) c += p.cantidad;
        }
        return c;
    }

    boolean estaVacia() {
        return paradas.isEmpty();
    }
}
