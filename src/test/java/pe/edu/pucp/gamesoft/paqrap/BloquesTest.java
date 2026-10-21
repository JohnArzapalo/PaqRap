package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Etapa 33 (SI-30): planificación por bloques con adelanto. Cada bloque se planifica antes de
 * que empiece, sobre el estado proyectado a su inicio. Las pruebas fijan bloques de 2 h con 1 h
 * de adelanto (no el valor por defecto, 1 h y 30 min): con márgenes amplios los casos son más claros.
 */
class BloquesTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private static List<UnidadTransporte> flota() {
        return List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO), new UnidadTransporte("TA02", TipoUnidad.AUTO),
                new UnidadTransporte("TM01", TipoUnidad.MOTO));
    }

    /** Un pedido de 8 h cada 45 min durante 8 h, en destinos repartidos por el mapa (con plazos
     *  de 36 h, lo más barato es que una sola moto los entregue uno tras otro, en más de un día). */
    private static List<Pedido> pedidos() {
        int[][] destinos = {{40, 20}, {20, 30}, {50, 10}, {10, 10}, {35, 40}, {60, 30},
                {25, 5}, {45, 45}, {15, 25}, {55, 20}, {30, 30}, {5, 40}};
        List<Pedido> ps = new ArrayList<>();
        for (int i = 0; i < destinos.length; i++)
            ps.add(new Pedido("P" + i, "P" + i, destinos[i][0], destinos[i][1], 4 + i % 5, 8, i * 0.75));
        return ps;
    }

    private static Simulador.Config conBloques() {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 720;
        c.bloqueMin = 120;
        c.adelantoMin = 60;
        return c;
    }

    /** Sin excepciones, todos los bloques (menos el primero, que se planifica al empezar) usan
     *  el plan calculado con adelanto: el estado proyectado coincidió con el real cada vez. */
    @Test
    void cadaBloqueUsaElPlanCalculadoConAdelanto() {
        Simulador.Config c = conBloques();
        c.horizonteMin = 1440;   // para que se entreguen todos
        Simulador.Resultado r = Simulador.simular(pedidos(), flota(), Planificador.tabu(8), c);
        assertTrue(r.censurada, "sin colapso: " + r.causaColapso);
        assertEquals(12, r.pedidosEnPlazo);
        assertTrue(r.bloquesConAdelanto >= 4, "bloques con adelanto: " + r.bloquesConAdelanto);
        long insercciones = r.registro.stream().filter(e -> e.detalle.contains("inserción de los pedidos")).count();
        assertTrue(insercciones >= 1, "los pedidos llegados después de un corte se insertaron al empezar el bloque");
        assertEquals(r.bloquesConAdelanto + 1 + insercciones, r.replanificaciones,
                "solo el primer plan se calcula en el instante; los demás, con adelanto (más las inserciones)");
        assertEquals(0, r.replanPorEvento);
        assertTrue(r.registro.stream().noneMatch(e -> e.detalle.contains("no coincidió")));
    }

    /** Las unidades siempre avanzan a un nodo vecino: no hay saltos, con bloques ni sin ellos. */
    @Test
    void lasUnidadesNoSaltan() {
        assertEquals(0, Simulador.simular(pedidos(), flota(), Planificador.tabu(8), conBloques()).saltos);
        Simulador.Config sinBloques = SimuladorTest.config(60);
        sinBloques.horizonteMin = 720;
        assertEquals(0, Simulador.simular(pedidos(), flota(), Planificador.tabu(8), sinBloques).saltos);
    }

    /** Un pedido de 36 h que llega a los 61 min, después del corte del bloque de las 2 h (a la
     *  hora 1), no entra al plan adelantado, pero entra al plan al empezar ese bloque (por
     *  inserción o, si no había nada más que planificar, en el instante), sin esperar al de las
     *  4 h: la unidad libre lo entrega entre las 2 h y las 4 h. */
    @Test
    void unPedidoQueLlegaDespuesDelCorteEntraAlEmpezarElBloque() {
        List<Pedido> ps = List.of(new Pedido("A", "A", 65, 45, 5, 36, 0.0),
                new Pedido("B", "B", 27, 20, 5, 36, 61 / 60.0));
        Simulador.Resultado r = Simulador.simular(ps, List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO),
                new UnidadTransporte("TA02", TipoUnidad.AUTO)), Planificador.tabu(8), conBloques());
        double entregaB = r.registro.stream().filter(e -> e.tipo == Simulador.Evt.T.ENTREGA && e.entregas.contains("B"))
                .mapToDouble(e -> e.min).findFirst().orElseThrow();
        assertTrue(entregaB >= 120 && entregaB < 240, "B entró al plan al empezar el bloque de las 2 h: " + entregaB);
    }

    /** Un pedido de 4 h registrado a los 61 min cuyo destino se bloquea de las 2.5 h a las 8 h
     *  debe entregarse antes de las 2.5 h (SI-23). Al empezar el bloque de las 2 h le quedarían
     *  30 min: es urgente y replanifica al llegar (vía de excepción, pregunta 18). */
    @Test
    void unPedidoUrgenteRompeElBloque() {
        List<Pedido> ps = List.of(new Pedido("A", "A", 30, 20, 5, 36, 0.0),
                new Pedido("U", "U", 27, 20, 5, 4, 61 / 60.0));
        Simulador.Config c = conBloques();
        c.urgenteMin = 120;
        c.mapa = new MapaVial(List.of(new MapaVial.Bloqueo(2.5, 8.0, new int[]{MapaVial.nodo(27, 20)})));
        Simulador.Resultado r = Simulador.simular(ps, List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO)),
                Planificador.tabu(8), c);
        assertTrue(r.censurada, "sin colapso: " + r.causaColapso);
        assertTrue(r.replanPorEvento >= 1, "replanificó al llegar el urgente");
        assertTrue(r.registro.stream().anyMatch(e -> e.tipo == Simulador.Evt.T.REPLANIFICACION && e.min == 61
                && e.detalle.contains("entregas")), "plan nuevo a los 61 min");
    }

    /** En la operación día a día (ritmo real), un pedido que registra el operador a la hora 1 se
     *  planifica al registrarse, sin esperar al bloque de las 2 h: lo lleva el auto libre. */
    @Test
    void enLaOperacionDiaADiaElPedidoRegistradoSePlanificaAlRegistrarse() {
        Simulador.Config c = conBloques();
        c.escenario = Simulador.Escenario.DIA_A_DIA;
        c.horizonteMin = 600;
        Simulador[] s = new Simulador[1];
        boolean[] registrado = {false};
        c.reloj = t -> {   // el operador registra el pedido cuando el reloj llega a la hora 1
            if (t >= 60 && !registrado[0]) {
                registrado[0] = true;
                s[0].inyectarPedido(new Pedido("W-R1", "W-R1", 27, 17, 3, 36, 0));
            }
            return true;
        };
        s[0] = Simulador.crear(List.of(new Pedido("A", "A", 40, 20, 5, 36, 0.0)),
                List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO), new UnidadTransporte("TA02", TipoUnidad.AUTO)),
                Planificador.tabu(8), c);
        Simulador.Resultado r = s[0].ejecutar();
        double entregaW = r.registro.stream().filter(e -> e.tipo == Simulador.Evt.T.ENTREGA && e.entregas.contains("W-R1"))
                .mapToDouble(e -> e.min).findFirst().orElseThrow();
        assertTrue(entregaW < 120, "se entregó antes del bloque de las 2 h: " + entregaW);
    }

    /** En otro hilo el resultado es el mismo que en el hilo de la simulación (parada por evaluaciones). */
    @Test
    void enOtroHiloDaLoMismo() {
        Simulador.Resultado mismo = Simulador.simular(pedidos(), flota(), Planificador.tabu(8), conBloques());
        Simulador.Config c = conBloques();
        c.adelantoEnParalelo = true;
        Simulador.Resultado otro = Simulador.simular(pedidos(), flota(), Planificador.tabu(8), c);
        assertEquals(mismo.costoAcumulado, otro.costoAcumulado, 1e-9);
        assertEquals(mismo.kmAcumulados, otro.kmAcumulados, 1e-9);
        assertEquals(mismo.pedidosEnPlazo, otro.pedidosEnPlazo);
        assertEquals(mismo.cambiosDeUnidad, otro.cambiosDeUnidad);
        assertEquals(mismo.bloquesConAdelanto, otro.bloquesConAdelanto);
    }

    /**
     * Con reloj real (1 s real = 1 h simulada) y Ta = 150 ms, el plan de cada bloque está listo
     * antes de que empiece: el adelanto dura 1 s real. El reloj nunca espera al planificador.
     */
    @Test
    void elPlanDelBloqueEstaListoAntesDeSuInicio() {
        Simulador.Config c = conBloques();
        c.horizonteMin = 360;
        c.maxEvaluaciones = 0;
        c.taMs = 150;
        c.adelantoEnParalelo = true;
        c.reloj = Reloj.escalado(3600);
        Simulador.Resultado r = Simulador.simular(pedidos(), flota(), Planificador.tabu(8), c);
        assertTrue(r.bloquesConAdelanto >= 2, "bloques con adelanto: " + r.bloquesConAdelanto);
        assertEquals(0, r.bloquesTardios, "ningún bloque esperó al planificador");
        assertEquals(0, r.saltos);
    }

    /** El plan adelantado, que no detiene el reloj, usa su propio presupuesto (400 ms) y aun así
     *  llega a tiempo: el adelanto dura 1 s real. */
    @Test
    void elPlanAdelantadoUsaSuPropioPresupuesto() {
        Simulador.Config c = conBloques();
        c.horizonteMin = 360;
        c.maxEvaluaciones = 0;
        c.taMs = 150;
        c.taBloqueMs = 400;
        c.adelantoEnParalelo = true;
        c.reloj = Reloj.escalado(3600);
        Simulador.Resultado r = Simulador.simular(pedidos(), flota(), Planificador.tabu(8), c);
        assertTrue(r.planificadorMsMax >= 350, "usó el presupuesto del bloque: " + r.planificadorMsMax + " ms");
        assertEquals(0, r.bloquesTardios);
    }

    /** Presupuesto del bloque en la web: la mitad del adelanto real, entre Ta y el tope. */
    @Test
    void presupuestoDelBloqueEnLaWeb() {
        assertEquals(7_500, CorridaWeb.taBloqueMs(2_000, 15_000, 0.5, 10_000));   // 5 días en 60 min
        assertEquals(3_750, CorridaWeb.taBloqueMs(2_000, 7_500, 0.5, 10_000));    // 5 días en 30 min
        assertEquals(2_000, CorridaWeb.taBloqueMs(2_000, 3_000, 0.5, 10_000));    // nunca menos que Ta
        assertEquals(10_000, CorridaWeb.taBloqueMs(2_000, 1_800_000, 0.5, 10_000));   // día a día: tope
    }

    @Test
    void elAdelantoNoPuedeSuperarAlBloque() {
        Simulador.Config c = conBloques();
        c.adelantoMin = 180;
        assertThrows(IllegalArgumentException.class, () -> Simulador.crear(pedidos(), flota(), Planificador.tabu(8), c));
    }
}
