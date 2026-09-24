package pe.edu.pucp.gamesoft.paqrap;

/** Estructura de datos compartida por ambos algoritmos (ISA apartado 4.1.1). */
class ParadaAlg {
    TipoParada tipo;
    Pedido pedido;     // null si es RECARGA
    Almacen almacen;   // null si es ENTREGA
    int cantidad;

    static ParadaAlg entrega(Pedido p) {
        ParadaAlg pa = new ParadaAlg();
        pa.tipo = TipoParada.ENTREGA;
        pa.pedido = p;
        pa.cantidad = p.cantidad;
        return pa;
    }

    int x() {
        return pedido != null ? pedido.x : almacen.x;
    }

    int y() {
        return pedido != null ? pedido.y : almacen.y;
    }
}
