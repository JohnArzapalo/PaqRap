package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.List;

/** Solución completa (ISA apartado 4.1.1). Puede contener rutas vacías
 *  (unidades libres que Búsqueda Tabú puede usar). */
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

    /** Vehículos realmente usados: rutas con al menos una parada. */
    int vehiculosUsados() {
        int n = 0;
        for (RutaAlg r : rutas) if (!r.estaVacia()) n++;
        return n;
    }

    /** Todas las entregas de la solución (en rutas y sin asignar). */
    List<Pedido> todasLasEntregas() {
        List<Pedido> todas = new ArrayList<>();
        for (RutaAlg r : rutas)
            for (ParadaAlg p : r.paradas)
                if (p.tipo == TipoParada.ENTREGA) todas.add(p.pedido);
        todas.addAll(pedidosSinAsignar);
        return todas;
    }

    /** Texto compacto de las rutas no vacías, para comparar soluciones en pruebas. */
    String firma() {
        StringBuilder sb = new StringBuilder();
        for (RutaAlg r : rutas) {
            if (r.estaVacia()) continue;
            sb.append(r.unidad.codigo).append(':');
            for (ParadaAlg p : r.paradas) sb.append(p.pedido.id).append(',');
            sb.append(';');
        }
        sb.append("sinAsignar=").append(pedidosSinAsignar);
        return sb.toString();
    }
}
