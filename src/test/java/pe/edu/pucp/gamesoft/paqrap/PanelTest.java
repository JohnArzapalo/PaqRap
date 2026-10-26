package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Etapa 35 (sección E del formulario): la instantánea trae lo que necesita el panel para ordenar, filtrar y
 * buscar unidades, ver los envíos de cada almacén y la lista de envíos: origen y destino de cada unidad con
 * sus horas y km, almacén de la última recarga, hora estimada de cada parada, origen y horas de cada entrega,
 * y lo entregado en las últimas 24 h.
 */
class PanelTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private static List<Pedido> pedidos() {
        return List.of(new Pedido("A", "A", 40, 20, 5, 36, 0.0), new Pedido("B", "B", 10, 30, 3, 36, 0.0));
    }

    private static List<UnidadTransporte> flota() {
        return List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO), new UnidadTransporte("TM01", TipoUnidad.MOTO));
    }

    private static String instantanea(double horizonteMin) {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = horizonteMin;
        Simulador s = Simulador.crear(pedidos(), flota(), Planificador.tabu(8), c);
        s.ejecutar();
        return s.instantaneaJson();
    }

    private static double numero(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        assertTrue(m.find(), "no se encontró " + regex + " en " + json);
        return Double.parseDouble(m.group(1));
    }

    /** A los 30 min, la unidad que salió del central con los pedidos tiene origen AL-CEN con salida en el
     *  minuto 0 y destino = el cliente, con su hora de llegada y los km que faltan; la parada trae su hora
     *  exacta. La que quedó libre en el central no tiene destino. */
    @Test
    void unidadEnRutaTraeOrigenDestinoYHoras() {
        String json = instantanea(30);
        int enRuta = 0;
        for (String codigo : new String[]{"TA01", "TM01"}) {
            int i = json.indexOf("\"codigo\":\"" + codigo + "\"");
            String u = json.substring(i, json.indexOf("\"camino\"", i));
            if (u.contains("\"destino\":null")) {
                assertTrue(u.contains("\"km_por_recorrer\":0") && u.contains("\"origen\":{\"lugar\":\"AL-CEN\""), u);
                continue;
            }
            enRuta++;
            assertTrue(u.contains("\"ultima_recarga\":\"AL-CEN\""), u);
            assertTrue(u.contains("\"origen\":{\"lugar\":\"AL-CEN\",\"x\":27,\"y\":14"), u);
            assertTrue(u.contains("\"salida_min\":0.000"), "salió del central en el minuto 0: " + u);
            assertTrue(u.contains("\"destino\":{\"tipo\":\"ENTREGA\""), u);
            double llegada = numero(u, "\"destino\":\\{[^}]*\"llegada_min\":([\\d.]+)");
            double km = numero(u, "\"destino\":\\{[^}]*\"km\":(\\d+)");
            assertTrue(llegada > 30 && km > 0, "llega después de ahora y le faltan km: " + u);
            assertTrue(numero(u, "\"km_por_recorrer\":(\\d+)") > km, "lo que falta incluye el regreso al almacén");
            assertTrue(u.matches("(?s).*\"tipo\":\"ENTREGA\",\"id\":\"[AB]\",\"x\":\\d+,\"y\":\\d+,\"llegada_min\":[\\d.]+,\"fin_min\":[\\d.]+,\"pedido\":\"[AB]\",\"cantidad\":\\d.*"), u);
        }
        assertTrue(enRuta >= 1, "al menos una unidad salió a entregar");
        // Las entregas en curso saben de qué almacén salieron, cuándo y cuándo llegan
        assertTrue(json.matches("(?s).*\"id\":\"A\",[^}]*\"estado\":\"en_curso\"[^}]*\"origen\":\"AL-CEN\",\"salida_min\":0.000,\"llegada_min\":[\\d.]+.*"), json);
    }

    /** Al final, las dos entregas aparecen como entregadas (últimas 24 h), con su unidad, su origen y su hora. */
    @Test
    void entregadosConUnidadOrigenYHora() {
        String json = instantanea(600);
        String e = json.substring(json.indexOf("\"entregados\""));
        for (String id : new String[]{"A", "B"})
            assertTrue(e.matches("(?s).*\"id\":\"" + id + "\",[^}]*\"unidad\":\"T[AM]01\",\"origen\":\"AL-CEN\",\"salida_min\":0.000,\"entregado_min\":[\\d.]+.*"), e);
        // ya en el almacén y sin tareas: su origen es el almacén al que volvió y no tiene destino
        assertTrue(json.contains("\"destino\":null"), json);
    }
}
