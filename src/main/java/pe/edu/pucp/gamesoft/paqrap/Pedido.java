package pe.edu.pucp.gamesoft.paqrap;

/**
 * Una ENTREGA a planificar. Si el pedido original (una línea del archivo de
 * ventas) supera los 24 paquetes, LectorPedidos lo divide en varias entregas
 * parciales (P13-P14); todas comparten idOriginal, posición y hora límite.
 */
class Pedido {
    String id;
    String idOriginal;      // pedido original (línea del archivo); = id si no se dividió
    int x, y;
    int cantidad;
    int plazoMaximoHoras;   // hl del archivo de ventas: horas límite desde el registro
    double horaRegistro;    // hora (en horas) en que llegó el pedido; 0 = inicio de la instancia

    Pedido(String id, int x, int y, int cantidad, int plazoMaximoHoras) {
        this(id, x, y, cantidad, plazoMaximoHoras, 0.0);
    }

    Pedido(String id, int x, int y, int cantidad, int plazoMaximoHoras, double horaRegistro) {
        this(id, id, x, y, cantidad, plazoMaximoHoras, horaRegistro);
    }

    Pedido(String id, String idOriginal, int x, int y, int cantidad, int plazoMaximoHoras, double horaRegistro) {
        this.id = id;
        this.idOriginal = idOriginal;
        this.x = x;
        this.y = y;
        this.cantidad = cantidad;
        this.plazoMaximoHoras = plazoMaximoHoras;
        this.horaRegistro = horaRegistro;
    }

    /** Hora límite de entrega = hora de registro + plazo (hl). */
    double horaLimite() {
        return horaRegistro + plazoMaximoHoras;
    }

    @Override
    public String toString() {
        return id;
    }
}
