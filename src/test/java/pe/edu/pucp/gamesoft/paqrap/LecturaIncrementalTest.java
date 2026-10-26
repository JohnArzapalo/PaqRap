package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Etapa 35 (A22, SI-33; docs/diseno/propuesta_lectura_incremental.md): los pedidos se leen por partes,
 * línea a línea, solo hasta el próximo bloque. Debe dar EXACTAMENTE lo mismo que la lectura completa.
 */
class LecturaIncrementalTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private static String clave(Pedido p) {
        return p.id + "|" + p.idOriginal + "|" + p.x + "," + p.y + "|" + p.cantidad + "|" + p.plazoMaximoHoras + "|" + p.horaRegistro;
    }

    /** Desde el 28/09/2026 por 5 días (cruza el fin de mes): el lector incremental entrega los mismos
     *  pedidos que DatosOficiales.cargar, con los mismos ids (los de octubre con «202610/»), destinos,
     *  cantidades, plazos y horas de registro, tanto de una vez como por bloques de 1 h. */
    @Test
    void elLectorDaLosMismosPedidosQueLaLecturaCompleta() throws IOException {
        double desde = 27 * 1440, fin = desde + 5 * 1440;
        List<String> completa = new ArrayList<>();
        for (Pedido p : DatosOficiales.cargar(YearMonth.of(2026, 9), desde, 5 * 1440).pedidos) completa.add(clave(p));

        List<String> deUnaVez = new ArrayList<>();
        for (Pedido p : new LectorVentasIncremental(YearMonth.of(2026, 9), desde, fin).leerHasta(Double.POSITIVE_INFINITY)) deUnaVez.add(clave(p));
        assertEquals(completa, deUnaVez);

        LectorVentasIncremental lector = new LectorVentasIncremental(YearMonth.of(2026, 9), desde, fin);
        List<String> porBloques = new ArrayList<>();
        for (double t = desde; porBloques.size() < completa.size() && t < fin + 60 * 1440; t += 60) {
            for (Pedido p : lector.leerHasta(t)) {
                assertTrue(p.horaRegistro * 60 <= t, "solo lo registrado hasta el corte");
                porBloques.add(clave(p));
            }
        }
        assertEquals(completa, porBloques);
        assertTrue(completa.stream().anyMatch(k -> k.startsWith("202610/")), "cruza a octubre");
    }

    /** Fuente que anota cuánto por delante del reloj de la simulación le piden leer. */
    private static final class FuenteQueMide implements FuentePedidos {
        final FuentePedidos base;
        Simulador sim;
        double maxAdelanto = Double.NEGATIVE_INFINITY;

        FuenteQueMide(FuentePedidos base) {
            this.base = base;
        }

        @Override
        public List<Pedido> leerHasta(double minuto) {
            if (sim != null) maxAdelanto = Math.max(maxAdelanto, minuto - sim.minutoActual());
            return base.leerHasta(minuto);
        }
    }

    private static Simulador.Resultado correr(boolean incremental, boolean bloques, FuenteQueMide[] medida) throws IOException {
        double desde = 9 * 1440;   // 10/11/2026 00:00 (MEDIA, datos oficiales)
        DatosOficiales datos = DatosOficiales.cargar(YearMonth.of(2026, 11), desde, 1440);
        Simulador.Config c = SimuladorTest.config(60);
        c.inicioMin = desde;
        c.horizonteMin = 1440;
        c.maxEvaluaciones = 150;
        c.alimentacion = true;
        c.mapa = new MapaVial(datos.bloqueos);
        c.mantenimientos = datos.mantenimientos;
        if (bloques) {
            c.bloqueMin = 60;
            c.adelantoMin = 30;
        }
        FuentePedidos fuente = incremental ? new LectorVentasIncremental(YearMonth.of(2026, 11), desde, desde + 1440)
                : FuentePedidos.deLista(datos.pedidos);
        FuenteQueMide mide = new FuenteQueMide(fuente);
        Simulador s = Simulador.crear(mide, CorridaWeb.flota(10, 15, 12), Planificador.tabu(8), c);
        mide.sim = s;
        if (medida != null) medida[0] = mide;
        return s.ejecutar();
    }

    private static List<String> eventos(Simulador.Resultado r) {
        List<String> l = new ArrayList<>();
        for (Simulador.Evt e : r.registro) l.add(e.tipo + "@" + e.min + "|" + e.unidad + "|" + e.detalle.replaceAll("\\d+ ms", "_ ms"));
        return l;
    }

    /** Un día de datos oficiales (10/11/2026), misma semilla y parada por evaluaciones: con la lista completa y
     *  con la lectura incremental el resultado es el mismo, evento por evento, con bloques (como la web) y con
     *  replanificación periódica (como el experimento). Y nunca se lee más allá del bloque siguiente. */
    @Test
    void laSimulacionEsLaMismaConListaYConLectura() throws IOException {
        for (boolean bloques : new boolean[]{true, false}) {
            FuenteQueMide[] medida = new FuenteQueMide[1];
            Simulador.Resultado lista = correr(false, bloques, null), lectura = correr(true, bloques, medida);
            String modo = bloques ? "bloques: " : "periódico: ";
            assertEquals(lista.censurada, lectura.censurada, modo + "censurada");
            assertEquals(lista.colapsoMin, lectura.colapsoMin, 0, modo + "colapso");
            assertEquals(lista.costoAcumulado, lectura.costoAcumulado, 0, modo + "costo");
            assertEquals(lista.kmAcumulados, lectura.kmAcumulados, 0, modo + "km");
            assertEquals(lista.pedidosLlegados, lectura.pedidosLlegados, modo + "llegados");
            assertEquals(lista.pedidosEntregados, lectura.pedidosEntregados, modo + "entregados");
            assertEquals(lista.pedidosEnPlazo, lectura.pedidosEnPlazo, modo + "en plazo");
            assertEquals(lista.replanificaciones, lectura.replanificaciones, modo + "replanificaciones");
            assertEquals(lista.replanPorEvento, lectura.replanPorEvento, modo + "por evento");
            assertEquals(lista.cambiosDeUnidad, lectura.cambiosDeUnidad, modo + "cambios de unidad");
            assertEquals(lista.bloquesConAdelanto, lectura.bloquesConAdelanto, modo + "bloques con adelanto");
            assertEquals(eventos(lista), eventos(lectura), modo + "eventos");
            assertTrue(lista.pedidosEntregados > 50, modo + "la prueba entrega pedidos de verdad: " + lista.pedidosEntregados);
            assertTrue(medida[0].maxAdelanto <= 60 + 1e-9, modo + "lee como mucho un bloque por delante: " + medida[0].maxAdelanto + " min");
        }
    }
}
