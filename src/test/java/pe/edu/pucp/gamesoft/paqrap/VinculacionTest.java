package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Etapa 35 (F02 a F04): la ruta anterior de un envío, con sus escalas y los tramos que recorrió a bordo
 * de cada unidad (GET /api/corridas/{ESC}/historial?id=...).
 */
class VinculacionTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private static List<int[]> puntos(String tramo) {
        List<int[]> ps = new ArrayList<>();
        Matcher m = Pattern.compile("\\[(\\d+),(\\d+)\\]").matcher(tramo);
        while (m.find()) ps.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
        return ps;
    }

    /** Ejemplo del profesor (AveriaTest): la moto carga E en el central, se avería en (42,14), el auto la
     *  trasvasa y la entrega en (57,14) a los 111 min. El historial tiene esas escalas en orden y dos tramos
     *  continuos: el de la moto hasta la avería y el del auto desde el trasvase hasta el cliente. Se puede
     *  pedir por el pedido o por un producto (E·p3). */
    @Test
    void rutaAnteriorConEscalasYTramos() {
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TM01", TipoUnidad.MOTO), new UnidadTransporte("TA01", TipoUnidad.AUTO));
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 600;
        c.almacenesIntermedios = false;
        c.averias = List.of(new Averia(36, "TM01", 1));
        c.mantenimientos = List.of(new Mantenimiento("TA01", 0, 30));
        Simulador s = Simulador.crear(List.of(new Pedido("E", "E", 57, 14, 8, 3, 0.0)), flota, Planificador.tabu(8), c);
        s.ejecutar();

        String h = s.historialJson("E·p3");
        assertTrue(h.contains("\"pedido\":\"E\"") && h.contains("\"estado\":\"entregado\"") && h.contains("\"unidad\":\"TA01\""), h);
        String escalas = h.substring(h.indexOf("\"escalas\""), h.indexOf("\"tramos\""));
        String[] orden = {"REGISTRO", "CARGA", "AVERIA", "TRASVASE", "ENTREGA"};
        int desde = 0;
        for (String tipo : orden) {
            int i = escalas.indexOf("\"tipo\":\"" + tipo + "\"", desde);
            assertTrue(i >= 0, "falta la escala " + tipo + " en orden: " + escalas);
            desde = i;
        }
        assertTrue(escalas.contains("\"min\":111.000,\"tipo\":\"ENTREGA\""), escalas);

        String tramos = h.substring(h.indexOf("\"tramos\""));
        String[] porUnidad = tramos.split("\\{\"unidad\":");
        assertEquals(3, porUnidad.length, "dos tramos: moto y auto: " + tramos);
        List<int[]> moto = puntos(porUnidad[1].substring(porUnidad[1].indexOf("\"puntos\""))), auto = puntos(porUnidad[2].substring(porUnidad[2].indexOf("\"puntos\"")));
        assertTrue(porUnidad[1].startsWith("\"TM01\"") && porUnidad[2].startsWith("\"TA01\""), tramos);
        assertTrue(moto.get(0)[0] == 27 && moto.get(0)[1] == 14, "la moto sale del central");
        assertTrue(moto.get(moto.size() - 1)[0] == 42 && moto.get(moto.size() - 1)[1] == 14, "y se detiene donde se averió");
        assertTrue(auto.get(0)[0] == 42 && auto.get(auto.size() - 1)[0] == 57 && auto.get(auto.size() - 1)[1] == 14, "el auto lo lleva del trasvase al cliente");
        for (List<int[]> t : List.of(moto, auto))
            for (int i = 1; i < t.size(); i++)
                assertEquals(1, Math.abs(t.get(i)[0] - t.get(i - 1)[0]) + Math.abs(t.get(i)[1] - t.get(i - 1)[1]), "tramo continuo, nodo a nodo");

        assertEquals(h.replace("\"consulta\":\"E·p3\"", ""), s.historialJson("E").replace("\"consulta\":\"E\"", ""), "por pedido o por producto da lo mismo");
        assertNull(s.historialJson("no-existe"));
    }
}
