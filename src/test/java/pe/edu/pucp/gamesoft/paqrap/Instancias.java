package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Instancias compartidas por las pruebas (las mismas de Main y del archivo sintético). */
final class Instancias {

    static final String ARCHIVO_SINTETICO = "datos/ventas202609_SINTETICO_08a12h.txt";

    private Instancias() {
    }

    /** I1 de Main: plazos holgados (8, 12 y 36 h). */
    static List<Pedido> mainI1() {
        List<Pedido> p = new ArrayList<>();
        p.add(new Pedido("P1", 30, 18, 5, 36));
        p.add(new Pedido("P2", 10, 20, 3, 8));
        p.add(new Pedido("P3", 35, 10, 8, 36));
        p.add(new Pedido("P4", 20, 25, 2, 12));
        p.add(new Pedido("P5", 28, 16, 6, 36));
        p.add(new Pedido("P6", 15, 12, 4, 36));
        return p;
    }

    /** I2 de Main: P2 y P3 en 1 h y P4 en 3 h. */
    static List<Pedido> mainI2() {
        List<Pedido> p = new ArrayList<>();
        p.add(new Pedido("P1", 30, 18, 5, 36));
        p.add(new Pedido("P2", 10, 20, 3, 1));
        p.add(new Pedido("P3", 35, 10, 8, 1));
        p.add(new Pedido("P4", 20, 25, 2, 3));
        p.add(new Pedido("P5", 28, 16, 6, 36));
        p.add(new Pedido("P6", 15, 12, 4, 36));
        return p;
    }

    /** Flota de Main: un auto y una moto. */
    static List<UnidadTransporte> flotaMain() {
        List<UnidadTransporte> f = new ArrayList<>();
        f.add(new UnidadTransporte("U1", TipoUnidad.AUTO));
        f.add(new UnidadTransporte("U2", TipoUnidad.MOTO));
        return f;
    }

    /** I1_1h del archivo sintético: día 1, 08:00-09:00. */
    static List<Pedido> sinteticoI1() throws IOException {
        return LectorPedidos.leerVentana(ARCHIVO_SINTETICO, LectorPedidos.hora(1, 8, 0), 1);
    }

    static Pedido buscar(List<Pedido> pedidos, String id) {
        for (Pedido p : pedidos) if (p.id.equals(id)) return p;
        throw new IllegalArgumentException(id);
    }
}
