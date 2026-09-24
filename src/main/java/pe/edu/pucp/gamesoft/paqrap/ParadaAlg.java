package pe.edu.pucp.gamesoft.paqrap;

/** Estructura de datos compartida por ambos algoritmos (ISA apartado 4.1.1).
 *  ENTREGA: deja los paquetes de un pedido. RECARGA: carga en un almacén los
 *  paquetes de las entregas siguientes que están en almacén (hasta la próxima
 *  RECARGA). TRASVASE: recoge en una unidad averiada detenida los paquetes
 *  que guarda y que esta ruta entrega después. */
class ParadaAlg {
    TipoParada tipo;
    Pedido pedido;          // solo ENTREGA
    Almacen almacen;        // solo RECARGA
    String unidadAveriada;  // solo TRASVASE
    int xTrasvase, yTrasvase;
    int cantidad;

    static ParadaAlg entrega(Pedido p) {
        ParadaAlg pa = new ParadaAlg();
        pa.tipo = TipoParada.ENTREGA;
        pa.pedido = p;
        pa.cantidad = p.cantidad;
        return pa;
    }

    static ParadaAlg recarga(Almacen a) {
        ParadaAlg pa = new ParadaAlg();
        pa.tipo = TipoParada.RECARGA;
        pa.almacen = a;
        return pa;
    }

    static ParadaAlg trasvase(String unidadAveriada, int x, int y) {
        ParadaAlg pa = new ParadaAlg();
        pa.tipo = TipoParada.TRASVASE;
        pa.unidadAveriada = unidadAveriada;
        pa.xTrasvase = x;
        pa.yTrasvase = y;
        return pa;
    }

    int x() {
        switch (tipo) {
            case ENTREGA: return pedido.x;
            case RECARGA: return almacen.x;
            default: return xTrasvase;
        }
    }

    int y() {
        switch (tipo) {
            case ENTREGA: return pedido.y;
            case RECARGA: return almacen.y;
            default: return yTrasvase;
        }
    }

    /** Identificador para atributos tabú y comparaciones. */
    String id() {
        switch (tipo) {
            case ENTREGA: return pedido.id;
            case RECARGA: return "RECARGA@" + almacen.codigo;
            default: return "TRASVASE@" + unidadAveriada;
        }
    }
}
