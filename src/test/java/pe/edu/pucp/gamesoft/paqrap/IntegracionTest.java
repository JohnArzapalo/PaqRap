package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Etapa 21.2: instantánea JSON y eventos externos (capa independiente de la tecnología web). */
class IntegracionTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
        TipoUnidad.AUTO.velocidadPromedio = Parametros.decimal("velocidad.AUTO", 40);
    }

    private static List<Pedido> pedidos() {
        return List.of(new Pedido("A", "A", 40, 20, 5, 36, 0.0), new Pedido("B", "B", 10, 30, 3, 36, 0.5));
    }

    private static List<UnidadTransporte> flota() {
        return List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO), new UnidadTransporte("TM01", TipoUnidad.MOTO));
    }

    /** La instantánea tiene todo lo que pide la guía de evaluación: almacenes, unidades, rutas,
     *  bloqueos, tiempos y semáforo; y es JSON bien formado (llaves y corchetes balanceados). */
    @Test
    void instantaneaTieneTodoLoNecesario() throws IOException {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 300;
        c.mapa = MapaVial.leer("datos/202609_SINTETICO.bloqueadas");
        Simulador s = Simulador.crear(pedidos(), flota(), Planificador.tabu(8), c);
        s.ejecutar();
        String json = s.instantaneaJson();
        for (String clave : new String[]{"\"reloj\"", "\"simulado_min\"", "\"real_ms\"", "\"semaforo\"", "\"color\"",
                "\"colapso\"", "\"almacenes\"", "\"stock\"", "\"unidades\"", "\"estado\"", "\"carga\"", "\"paradas\"",
                "\"camino\"", "\"bloqueos_activos\"", "\"pedidos\"", "\"indicadores\""}) {
            assertTrue(json.contains(clave), "falta " + clave);
        }
        int llaves = 0, corchetes = 0;
        boolean enCadena = false;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"' && json.charAt(i - 1) != '\\') enCadena = !enCadena;
            if (enCadena) continue;
            if (ch == '{') llaves++;
            if (ch == '}') llaves--;
            if (ch == '[') corchetes++;
            if (ch == ']') corchetes--;
            assertTrue(llaves >= 0 && corchetes >= 0);
        }
        assertEquals(0, llaves);
        assertEquals(0, corchetes);
        assertTrue(json.contains("\"TA01\"") && json.contains("\"AL-NO\""));
    }

    /** Una avería registrada desde fuera se aplica en el instante actual y dispara replanificación. */
    @Test
    void averiaExternaSeAplica() {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 600;
        Simulador s = Simulador.crear(pedidos(), flota(), Planificador.tabu(8), c);
        s.inyectarAveria("TA01", 1);   // antes de arrancar: se atiende en el minuto 0
        Simulador.Resultado r = s.ejecutar();
        assertEquals(1, r.averiasAplicadas);
        assertTrue(r.registro.stream().anyMatch(e -> e.tipo == Simulador.Evt.T.AVERIA && e.unidad.equals("TA01")));
    }

    /** El cambio de velocidad rige desde la siguiente replanificación (P16). */
    @Test
    void cambioDeVelocidadEnLaSiguienteReplanificacion() {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 120;
        Simulador s = Simulador.crear(pedidos(), flota(), Planificador.tabu(8), c);
        s.cambiarVelocidad(TipoUnidad.AUTO, 20);
        Simulador.Resultado r = s.ejecutar();
        assertEquals(20.0, TipoUnidad.AUTO.velocidadPromedio, 1e-9);
        assertTrue(r.registro.stream().anyMatch(e -> e.detalle.contains("velocidad de AUTO = 20")));
    }

    /** Servicio en otro hilo con ritmo escalado: la instantánea avanza mientras corre, y una
     *  avería registrada durante la corrida se atiende sin esperar al siguiente evento. */
    @Test
    void servicioEnOtroHiloConAveriaEnCaliente() throws Exception {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 600;
        c.reloj = Reloj.escalado(6000);   // 100 min simulados por segundo real
        ServicioSimulacion srv = new ServicioSimulacion(pedidos(), flota(), Planificador.tabu(8), c);
        srv.iniciar();
        Thread.sleep(1500);
        String json = srv.instantanea();
        assertTrue(srv.enCurso(), "debería seguir corriendo");
        assertTrue(json.contains("\"simulado_min\""));
        srv.registrarAveria("TM01", 1);
        Simulador.Resultado r = srv.esperar(30_000);
        assertNotNull(r);
        assertEquals(1, r.averiasAplicadas);
        assertEquals(600.0, r.finMin, 1e-9);
    }
}
