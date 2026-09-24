package pe.edu.pucp.gamesoft.paqrap;

import java.util.HashMap;
import java.util.Map;

/** 4.2.1 Lista tabú: atributo -> iteración en la que deja de ser tabú. */
class ListaTabu {
    private final Map<ParTabu, Integer> prohibidos = new HashMap<>();

    /** Prohíbe el atributo hasta la iteración "vence" (exclusiva). */
    void registrar(ParTabu atributo, int vence) {
        prohibidos.put(atributo, vence);
    }

    /** true solo si el atributo está registrado y su vencimiento es
     *  MAYOR que la iteración actual (entrada vigente). */
    boolean contiene(ParTabu atributo, int iteracionActual) {
        Integer vence = prohibidos.get(atributo);
        return vence != null && vence > iteracionActual;
    }

    void limpiarVencidos(int iteracionActual) {
        prohibidos.entrySet().removeIf(e -> e.getValue() <= iteracionActual);
    }
}
