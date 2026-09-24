package pe.edu.pucp.gamesoft.paqrap;

/**
 * Una ENTREGA a planificar. Si el pedido original (una línea del archivo de
 * ventas) supera los 24 paquetes, LectorPedidos lo divide en varias entregas
 * parciales (P13-P14); todas comparten idOriginal, posición y hora límite.
 * El simulador puede dividirla más (Etapa 10.3, entregas parciales flexibles).
 *
 * Origen de los paquetes al planificar (Etapas 11 y 12):
 *  - en un almacén (aBordoDe == null y enAveriada == null): hay que cargarlos
 *    en un punto de carga (RECARGA o la salida desde un almacén);
 *  - a bordo de una unidad operativa (aBordoDe = código): solo esa unidad
 *    puede entregarlos;
 *  - en una unidad averiada detenida (enAveriada = código): otra unidad debe
 *    recogerlos con una parada TRASVASE antes de entregarlos.
 */
class Pedido {
    String id;
    String idOriginal;      // pedido original (línea del archivo); = id si no se dividió
    int x, y;
    int cantidad;
    int plazoMaximoHoras;   // hl del archivo de ventas: horas límite desde el registro
    double horaRegistro;    // hora (en horas) en que llegó el pedido; 0 = inicio de la instancia
    String aBordoDe;        // unidad que ya lleva los paquetes (null si no)
    String enAveriada;      // unidad averiada que guarda los paquetes (null si no)

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

    /** true si los paquetes están en un almacén (hay que cargarlos en una RECARGA). */
    boolean enAlmacen() {
        return aBordoDe == null && enAveriada == null;
    }

    /** Copia con la hora de registro desplazada (base de tiempo relativa al instante de planificación). */
    Pedido copiaRelativa(double instanteH) {
        Pedido p = new Pedido(id, idOriginal, x, y, cantidad, plazoMaximoHoras, horaRegistro - instanteH);
        p.aBordoDe = aBordoDe;
        p.enAveriada = enAveriada;
        return p;
    }

    @Override
    public String toString() {
        return id;
    }
}
