package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Etapa 17: regla configurable para destinos bloqueados (red.destino_bloqueado). */
class DestinoBloqueadoTest {

    @TempDir
    Path carpeta;

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    /** Muro vertical en x = 10, y = 0..9, activo de 00:00 a 10:00 del día 1. */
    private MapaVial muro() throws IOException {
        Path f = carpeta.resolve("muro.bloqueadas");
        Files.writeString(f, "01d00h00m-01d10h00m:10,0,10,9\n", StandardCharsets.UTF_8);
        return MapaVial.leer(f.toString());
    }

    private static double llegadaA(int x, int y, MapaVial m, Contexto.ReglaDestino regla) {
        Contexto cx = new Contexto();
        cx.mapa = m;
        cx.reglaDestino = regla;
        Contexto.usar(cx);
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("TA01", TipoUnidad.AUTO);
        r.paradas.add(ParadaAlg.entrega(new Pedido("A", x, y, 1, 36)));
        return Compartido.evaluarRuta(r).llegada[0];
    }

    @Test
    void esperarAtiendeCuandoElNodoSeDesbloquea() throws IOException {
        assertEquals(10.0, llegadaA(10, 5, muro(), Contexto.ReglaDestino.ESPERAR), 1e-9);
    }

    /** NODO_VECINO: del central (27,14) al vecino libre más cercano de (10,5), que es (11,5):
     *  16 + 9 = 25 km a 40 km/h = 0.625 h, sin esperar. */
    @Test
    void nodoVecinoEntregaDesdeElAdyacenteLibre() throws IOException {
        assumeTrue(TipoUnidad.AUTO.velocidadPromedio == 40.0);
        assertEquals(0.625, llegadaA(10, 5, muro(), Contexto.ReglaDestino.NODO_VECINO), 1e-9);
        // Un destino libre no cambia
        assertEquals(llegadaA(20, 20, muro(), Contexto.ReglaDestino.ESPERAR),
                llegadaA(20, 20, muro(), Contexto.ReglaDestino.NODO_VECINO), 1e-9);
    }

    /** NO_EVALUABLE: un pedido con destino bloqueado desde su registro hasta su hora
     *  límite (plazo 4 h, bloqueo hasta las 10:00) no provoca colapso y se cuenta aparte;
     *  con ESPERAR el mismo caso colapsa por "destino bloqueado". */
    @Test
    void noEvaluableExcluyeDelColapsoYCuentaAparte() throws IOException {
        MapaVial m = muro();
        List<Pedido> pedidos = List.of(new Pedido("B", "B", 10, 5, 3, 4, 0.0),
                                       new Pedido("C", "C", 30, 14, 3, 36, 0.0));
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO));

        Simulador.Config esperar = SimuladorTest.config(60);
        esperar.mapa = m;
        esperar.horizonteMin = 600;
        esperar.reglaDestino = Contexto.ReglaDestino.ESPERAR;
        Simulador.Resultado r1 = Simulador.simular(pedidos, flota, Planificador.tabu(8), esperar);
        assertFalse(r1.censurada);
        assertEquals(240.0, r1.colapsoMin, 1e-9);
        assertEquals("destino bloqueado", r1.causaColapso);

        Simulador.Config noEval = SimuladorTest.config(60);
        noEval.mapa = m;
        noEval.horizonteMin = 600;
        noEval.reglaDestino = Contexto.ReglaDestino.NO_EVALUABLE;
        Simulador.Resultado r2 = Simulador.simular(pedidos, flota, Planificador.tabu(8), noEval);
        assertTrue(r2.censurada);
        assertEquals(1, r2.pedidosInentregablesBloqueo);
        assertEquals(1, r2.pedidosEntregados);   // C se entrega; B no cuenta
        assertEquals(100.0, r2.pctPedidosEnPlazo(), 1e-9);
    }

    /** 17.2: el generador no ubica pedidos en nodos bloqueados durante toda su ventana. */
    @Test
    void generadorExcluyeDestinosBloqueados() throws IOException {
        MapaVial m = muro();
        LectorPedidos.Registro bloqueado = new LectorPedidos.Registro();
        bloqueado.llegadaHoras = 1;   // 01:00, plazo 4 h -> [1, 5] dentro del bloqueo
        bloqueado.x = 10;
        bloqueado.y = 5;
        bloqueado.cliente = "c1";
        bloqueado.cantidad = 2;
        bloqueado.hl = 4;
        LectorPedidos.Registro libre = new LectorPedidos.Registro();
        libre.llegadaHoras = 1;
        libre.x = 20;
        libre.y = 20;
        libre.cliente = "c2";
        libre.cantidad = 2;
        libre.hl = 4;
        Path destino = carpeta.resolve("gen.txt");
        int descartados = GeneradorCarga.generar(List.of(bloqueado, libre), 40, 1, 5, destino, m);
        assertTrue(descartados > 0);
        for (LectorPedidos.Registro r : LectorPedidos.leerRegistros(destino.toString()))
            assertFalse(r.x == 10 && r.y == 5, "pedido en destino bloqueado");
    }
}
