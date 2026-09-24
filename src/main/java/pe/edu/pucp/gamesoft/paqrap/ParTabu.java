package pe.edu.pucp.gamesoft.paqrap;

import java.util.Objects;

/**
 * 4.2.1 Atributo tabú: (pedido, unidad, posición).
 *
 * - Movimiento entre rutas distintas: posicion = CUALQUIERA. Al sacar el
 *   pedido p de la unidad U se registra (p, U, CUALQUIERA): durante la
 *   duración tabú, p no puede volver a U en ninguna posición.
 * - Movimiento dentro de la misma ruta: posicion = índice anterior del
 *   pedido. Se registra (p, U, posAnterior): p no puede volver a esa
 *   misma posición de U, pero sí moverse a otras posiciones.
 */
class ParTabu {
    static final int CUALQUIERA = -1;

    final String idPedido;
    final String idUnidad;
    final int posicion;

    ParTabu(String idPedido, String idUnidad) {
        this(idPedido, idUnidad, CUALQUIERA);
    }

    ParTabu(String idPedido, String idUnidad, int posicion) {
        this.idPedido = idPedido;
        this.idUnidad = idUnidad;
        this.posicion = posicion;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ParTabu)) return false;
        ParTabu p = (ParTabu) o;
        return posicion == p.posicion && idPedido.equals(p.idPedido) && idUnidad.equals(p.idUnidad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idPedido, idUnidad, posicion);
    }

    @Override
    public String toString() {
        return "(" + idPedido + ", " + idUnidad + (posicion == CUALQUIERA ? "" : ", pos " + posicion) + ")";
    }
}
