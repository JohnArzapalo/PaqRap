package pe.edu.pucp.gamesoft.paqrap;
import java.util.ArrayList;
import java.util.List;

/** Estructura de datos compartida por ambos algoritmos (ISA apartado 4.1.1). */

enum TipoParada { ENTREGA, RECARGA }

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
}

class Solucion {
    List<RutaAlg> rutas = new ArrayList<>();
    List<Pedido> pedidosSinAsignar = new ArrayList<>();
    int H;       // nivel 1: pedidos sin asignar + pedidos fuera de plazo
    double S;    // nivel 2: costo total en soles

    Solucion copiar() {
        Solucion s = new Solucion();
        s.H = H;
        s.S = S;
        s.pedidosSinAsignar = new ArrayList<>(pedidosSinAsignar);
        for (RutaAlg r : rutas) {
            RutaAlg nr = new RutaAlg();
            nr.unidad = r.unidad;
            nr.paradas = new ArrayList<>(r.paradas);
            nr.distanciaKm = r.distanciaKm;
            nr.costo = r.costo;
            s.rutas.add(nr);
        }
        return s;
    }
}