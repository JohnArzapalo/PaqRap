package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 4.3.1 Estructuras específicas del Algoritmo Genético. */
class Cromosoma {
    List<Pedido> permutacion;
    Map<String, TipoUnidad> tipoAsignado;   // por id de pedido
    double aptH;
    double aptS;

    Cromosoma(List<Pedido> permutacion, Map<String, TipoUnidad> tipoAsignado) {
        this.permutacion = permutacion;
        this.tipoAsignado = tipoAsignado;
    }

    Cromosoma copiar() {
        Cromosoma c = new Cromosoma(new ArrayList<>(permutacion), new HashMap<>(tipoAsignado));
        c.aptH = aptH;   // la copia conserva su aptitud; sin esto el élite
        c.aptS = aptS;   // quedaba con (0, 0) y parecía "perfecto"
        return c;
    }
}
