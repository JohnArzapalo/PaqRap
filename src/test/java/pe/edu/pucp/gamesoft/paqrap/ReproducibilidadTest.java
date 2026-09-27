package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** R6: en el modo por evaluaciones, la misma semilla da exactamente la misma solución.
 *  Etapa 24: sin estado estático, también en simulaciones que corren en paralelo. */
class ReproducibilidadTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    @Test
    void tabuEsReproducibleEnModoEvaluaciones() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        List<UnidadTransporte> flota = Experimento.flotaOficial();

        BusquedaTabu ta = new BusquedaTabu(1001);
        Solucion a = ta.ejecutarDesdeCero(ped, flota, 0, 20_000, 8, Integer.MAX_VALUE);

        BusquedaTabu tb = new BusquedaTabu(1001);
        Solucion b = tb.ejecutarDesdeCero(ped, flota, 0, 20_000, 8, Integer.MAX_VALUE);

        assertEquals(a.firma(), b.firma());
        assertEquals(a.S, b.S);
        assertEquals(a.H, b.H);
        assertEquals(ta.ultimasEvaluaciones, tb.ultimasEvaluaciones);
        assertEquals(ta.ultimasIteraciones, tb.ultimasIteraciones);
    }

    @Test
    void agEsReproducibleEnModoEvaluaciones() throws IOException {
        List<Pedido> ped = Instancias.sinteticoI1();
        List<UnidadTransporte> flota = Experimento.flotaOficial();

        AlgoritmoGenetico ga = new AlgoritmoGenetico(1001);
        Solucion a = ga.ejecutar(ped, flota, 0, 5_000, 30);

        AlgoritmoGenetico gb = new AlgoritmoGenetico(1001);
        Solucion b = gb.ejecutar(ped, flota, 0, 5_000, 30);

        assertEquals(a.firma(), b.firma());
        assertEquals(a.S, b.S);
        assertEquals(a.H, b.H);
        assertEquals(ga.ultimasEvaluaciones, gb.ultimasEvaluaciones);
        assertEquals(ga.ultimasGeneraciones, gb.ultimasGeneraciones);
    }

    // ===================== Etapa 24: simulaciones en paralelo =====================

    /** 12 pedidos en 3 horas, repartidos por el mapa, con plazos de 4 a 36 h. */
    private static List<Pedido> pedidos() {
        int[][] datos = {{35, 20, 5, 36}, {12, 30, 3, 8}, {50, 10, 7, 12}, {27, 40, 2, 4},
                         {60, 30, 10, 18}, {20, 8, 4, 36}, {45, 45, 6, 12}, {5, 5, 1, 8},
                         {40, 25, 8, 36}, {30, 14, 12, 18}, {55, 40, 3, 4}, {15, 20, 9, 12}};
        List<Pedido> ps = new ArrayList<>();
        for (int i = 0; i < datos.length; i++) {
            int[] d = datos[i];
            ps.add(new Pedido("P" + i, "P" + i, d[0], d[1], d[2], d[3], i * 0.25));
        }
        return ps;
    }

    private static List<UnidadTransporte> flota() {
        return List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO), new UnidadTransporte("TA02", TipoUnidad.AUTO),
                new UnidadTransporte("TM01", TipoUnidad.MOTO), new UnidadTransporte("TM02", TipoUnidad.MOTO),
                new UnidadTransporte("TB01", TipoUnidad.BICICLETA));
    }

    /** Todo lo que el experimento guarda de una corrida, en una sola cadena comparable. */
    private static String firma(Simulador.Resultado r) {
        return String.format("censurada=%s colapso=%.9f costo=%.9f km=%.9f entregados=%d enPlazo=%d "
                        + "evaluaciones=%d iteraciones=%d cambios=%d viajes=%d replan=%d",
                r.censurada, r.colapsoMin, r.costoAcumulado, r.kmAcumulados, r.pedidosEntregados,
                r.pedidosEnPlazo, r.evaluacionesTotales, r.iteracionesTotales, r.cambiosDeUnidad,
                r.viajesTotales, r.replanificaciones);
    }

    private static String simular(String algoritmo) {
        Simulador.Config c = SimuladorTest.config(30);
        c.horizonteMin = 900;
        c.semilla = 1003;
        Planificador p = algoritmo.equals("TABU") ? Planificador.tabu(8) : Planificador.genetico(10);
        return firma(Simulador.simular(pedidos(), flota(), p, c));
    }

    /**
     * Cuatro simulaciones a la vez (Tabú, AG, Tabú, AG), cada una en su hilo, dan
     * exactamente el mismo resultado que corridas una tras otra. Antes de la Etapa 24
     * compartían el generador aleatorio y los contadores estáticos de los algoritmos.
     */
    @Test
    void simulacionesEnParaleloDanLoMismoQueEnSerie() throws Exception {
        String tabuSerie = simular("TABU");
        String agSerie = simular("AG");

        ExecutorService hilos = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> tabu = new ArrayList<>(), ag = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                tabu.add(hilos.submit(() -> simular("TABU")));
                ag.add(hilos.submit(() -> simular("AG")));
            }
            for (Future<String> f : tabu) assertEquals(tabuSerie, f.get());
            for (Future<String> f : ag) assertEquals(agSerie, f.get());
        } finally {
            hilos.shutdownNow();
        }
    }
}
