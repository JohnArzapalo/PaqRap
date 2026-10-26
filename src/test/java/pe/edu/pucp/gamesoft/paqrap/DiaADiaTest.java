package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Etapa 35 (Tarea 6; A11, B13, G09): operación día a día completa. La carga masiva respeta la fecha y hora de
 * cada línea, y al cerrar las operaciones el reporte trae la última planificación estable completa.
 */
class DiaADiaTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    /** Un pedido programado para el minuto 200 (carga masiva) no aparece antes: llega en su hora y se entrega
     *  después; la bitácora dice para cuándo quedó programado. */
    @Test
    void unPedidoProgramadoLlegaEnSuHora() {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 600;
        c.bloqueMin = 60;
        c.adelantoMin = 30;
        Simulador[] s = new Simulador[1];
        boolean[] cargado = {false};
        c.reloj = t -> {
            if (t >= 10 && !cargado[0]) {   // el operador sube el archivo a los 10 min
                cargado[0] = true;
                s[0].programarPedido(new Pedido("c1-R1", "c1-R1", 30, 14, 4, 36, 200 / 60.0));
            }
            return true;
        };
        s[0] = Simulador.crear(List.of(new Pedido("A", "A", 40, 20, 5, 36, 0.0)),
                List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO)), Planificador.tabu(8), c);
        Simulador.Resultado r = s[0].ejecutar();
        assertTrue(r.registro.stream().anyMatch(e -> e.detalle.contains("c1-R1 programado para 01d03h20m")), "bitácora");
        double entrega = r.registro.stream().filter(e -> e.tipo == Simulador.Evt.T.ENTREGA && e.entregas.contains("c1-R1"))
                .mapToDouble(e -> e.min).findFirst().orElseThrow();
        assertTrue(entrega >= 200, "se entrega después de su hora de registro: " + entrega);
        assertTrue(r.pedidosEnPlazo == 2, "ambos en plazo");
    }

    /** Carga masiva en una operación día a día real (datos oficiales, 10/11/2026 08:00): se aceptan las líneas
     *  futuras en su fecha y hora, y se rechazan con su motivo las pasadas, las de fecha inválida, las de fuera
     *  del mapa y las de formato inválido. */
    @Test
    void laCargaMasivaRespetaLaFechaYHoraDeCadaLinea() throws Exception {
        CorridaWeb c = CorridaWeb.iniciar(Simulador.Escenario.DIA_A_DIA, LocalDateTime.of(2026, 11, 10, 8, 0), 0, 600, 10, 15, 12);
        try {
            String r = c.registrarLote("10d07h50m:31,17,c9167,4,36\n10d23h30m:63,35,c8232,3,12\n31d10h00m:12,5,c9451,2,8\n"
                    + "11d09h00m:75,20,c1022,2,36\nesto no es una línea\n12d06h15m:44,41,c3310,30,18");
            String acept = r.substring(r.indexOf("\"aceptados\""), r.indexOf("\"rechazados\"")), rech = r.substring(r.indexOf("\"rechazados\""));
            assertTrue(acept.contains("\"linea\":2,\"id\":\"c8232-R1\"") && acept.contains("\"linea\":6,\"id\":\"c3310-R2\""), r);
            // 10/11 23:30 = minuto 9·1440 + 23·60 + 30 desde el 01/11 00:00; hora límite 12 h después
            assertTrue(acept.contains("\"registro_min\":14370.000,\"limite_min\":15090.000"), r);
            assertTrue(rech.contains("\"linea\":1") && rech.contains("Hora anterior a la actual"), r);
            assertTrue(rech.contains("\"linea\":3") && rech.contains("Fecha u hora inválida para 2026-11"), r);
            assertTrue(rech.contains("\"linea\":4") && rech.contains("Destino fuera del mapa"), r);
            assertTrue(rech.contains("\"linea\":5") && rech.contains("Formato inválido"), r);
        } finally {
            c.finalizar();
        }
    }

    /** El reporte de cierre trae la última planificación estable: el plan vigente al cierre, con cada parada de
     *  cada unidad, su carga, su hora estimada de llegada y el regreso al almacén (G08 a G10). */
    @Test
    void elReporteTraeLaUltimaPlanificacionEstable() {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 30;
        Simulador s = Simulador.crear(List.of(new Pedido("A", "A", 40, 20, 5, 36, 0.0), new Pedido("B", "B", 10, 30, 3, 36, 0.0)),
                List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO), new UnidadTransporte("TM01", TipoUnidad.MOTO)), Planificador.tabu(8), c);
        s.ejecutar();
        String r = s.reporteJson();
        assertTrue(r.contains("\"plan\":{\"aplicado_min\":0.000,\"pendientes\":2,\"sin_unidad\":0,\"por_planificar\":0,\"programados\":0,\"unidades\":[{\"codigo\":\"T"), r);
        assertTrue(r.matches("(?s).*\"tipo\":\"ENTREGA\",\"x\":\\d+,\"y\":\\d+,\"lugar\":\"cliente \\(\\d+,\\d+\\)\",\"llegada_min\":[\\d.]+,\"pedido\":\"[AB]\",\"cantidad\":\\d.*"), r);
        assertTrue(r.matches("(?s).*\"tipo\":\"REGRESO\",\"x\":\\d+,\"y\":\\d+,\"lugar\":\"AL-(CEN|NO|ES)\",\"llegada_min\":[\\d.]+.*"), r);
    }
}
