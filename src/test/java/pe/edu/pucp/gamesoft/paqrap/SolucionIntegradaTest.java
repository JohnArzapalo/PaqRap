package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Etapa 32: lo que necesita la solución integrada (planificador + visualizador web). */
class SolucionIntegradaTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private static List<UnidadTransporte> unAuto() {
        return List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO));
    }

    /** Un pedido registrado desde el visualizador (día a día) llega en el instante actual y se entrega. */
    @Test
    void pedidoRegistradoDesdeFueraSeEntrega() {
        Simulador.Config c = SimuladorTest.config(60);
        c.escenario = Simulador.Escenario.DIA_A_DIA;
        c.horizonteMin = 600;
        Simulador s = Simulador.crear(List.of(), unAuto(), Planificador.tabu(8), c);
        s.inyectarPedido(new Pedido("W-R1", "W-R1", 30, 14, 5, 4, 0));   // antes de arrancar: llega en el minuto 0
        Simulador.Resultado r = s.ejecutar();
        assertTrue(r.censurada, "sin colapso: " + r.causaColapso);
        assertTrue(r.registro.stream().anyMatch(e -> e.tipo == Simulador.Evt.T.ENTREGA && e.entregas.contains("W-R1")));
    }

    /**
     * Pedido de 4 h registrado a los 30 min en (30,14), a 3 km del central; su destino se
     * bloquea de la hora 1 a la 6, así que hay que entregar antes de la hora 1 (SI-23).
     * Sin replanificar al llegar, entra al plan en la hora 1 (Sa = 60 min) y ya no alcanza.
     * Con la replanificación por pedido urgente (Etapa 32) se planifica a los 30 min y llega a tiempo.
     */
    @Test
    void pedidoUrgentePorBloqueoSeReplanificaAlLlegar() {
        int[] nodos = {MapaVial.nodo(30, 14), MapaVial.nodo(30, 15)};
        List<Pedido> pedidos = List.of(new Pedido("U", "U", 30, 14, 5, 4, 0.5));

        Simulador.Config sin = SimuladorTest.config(60);
        sin.horizonteMin = 600;
        sin.mapa = new MapaVial(List.of(new MapaVial.Bloqueo(1.0, 6.0, nodos)));
        sin.urgenteMin = 0;
        Simulador.Resultado r1 = Simulador.simular(pedidos, unAuto(), Planificador.tabu(8), sin);
        assertFalse(r1.censurada, "sin la replanificación por urgencia, colapsa");

        Simulador.Config con = SimuladorTest.config(60);
        con.horizonteMin = 600;
        con.mapa = new MapaVial(List.of(new MapaVial.Bloqueo(1.0, 6.0, nodos)));
        con.urgenteMin = 120;
        Simulador.Resultado r2 = Simulador.simular(pedidos, unAuto(), Planificador.tabu(8), con);
        assertTrue(r2.censurada, "sin colapso: " + r2.causaColapso);
        assertEquals(1, r2.pedidosEnPlazo);
        assertTrue(r2.replanPorEvento >= 1, "replanificó al llegar el pedido");
    }

    /** Una corrida que empieza el 28 de septiembre usa también los datos de octubre,
     *  desplazados 30 días y con el mes en el id (los números de línea se repiten). */
    @Test
    void losDatosOficialesCruzanElFinDeMes() throws IOException {
        DatosOficiales d = DatosOficiales.cargar(YearMonth.of(2026, 9), 27 * 1440.0, 5 * 1440.0);
        assertTrue(d.pedidos.stream().allMatch(p -> p.horaRegistro * 60 >= 27 * 1440.0), "solo desde el inicio");
        assertTrue(d.pedidos.stream().anyMatch(p -> p.id.startsWith("202610/") && p.horaRegistro >= 30 * 24.0), "pedidos de octubre");
        assertTrue(d.pedidos.stream().noneMatch(p -> !p.id.startsWith("202610/") && p.horaRegistro >= 30 * 24.0));
        assertTrue(d.bloqueos.stream().anyMatch(b -> b.inicioH >= 30 * 24.0), "bloqueos de octubre desplazados");
        assertFalse(d.mantenimientos.isEmpty(), "mantenimientos de septiembre y octubre");
    }

    /** La instantánea, la bitácora y el reporte traen lo que dibuja el visualizador. */
    @Test
    void elVisualizadorRecibeLoQueDibuja() {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 300;
        Simulador s = Simulador.crear(List.of(new Pedido("A", "A", 40, 20, 5, 36, 0.0)), unAuto(), Planificador.tabu(8), c);
        s.ejecutar();
        String json = s.instantaneaJson();
        for (String clave : new String[]{"\"inicio_min\"", "\"horizonte_min\"", "\"capacidad\"", "\"km\"", "\"averia_tipo\"",
                "\"entregas_por_plazo\"", "\"utilizacion_por_tipo\"", "\"36\""})
            assertTrue(json.contains(clave), "falta " + clave + " en la instantánea");
        String eventos = s.eventosJson(0);
        assertTrue(eventos.contains("\"total\"") && eventos.contains("\"ENTREGA\"") && eventos.contains("TA01 entrega 5 paq."));
        assertTrue(s.eventosJson(Integer.MAX_VALUE).contains("\"eventos\":[]"), "nada nuevo desde el final");
        assertTrue(s.reporteJson().contains("\"porTipo\"") && s.reporteJson().contains("\"AUTO\""));
    }
}
