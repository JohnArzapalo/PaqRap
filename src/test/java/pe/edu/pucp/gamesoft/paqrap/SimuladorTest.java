package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Etapa 5: simulador mínimo, capacidad teórica y generación de carga. */
class SimuladorTest {

    @TempDir
    Path carpeta;

    private static Simulador.Config config(double saMin) {
        Simulador.Config c = new Simulador.Config();
        c.saMin = saMin;
        c.horizonteMin = 7200;
        c.maxEvaluaciones = 500;   // rápido y determinista
        c.semilla = 1;
        return c;
    }

    /** Un pedido que nadie puede llevar (12 paquetes, solo hay una bicicleta)
     *  colapsa en el minuto exacto de su hora límite (hl = 4 h -> 240 min),
     *  aunque el ciclo que lo detecta sea posterior (Sa = 7 -> t = 245). */
    @Test
    void pedidoVencidoSinEntregaColapsaEnElMinutoExacto() {
        List<Pedido> pedidos = List.of(new Pedido("A", "A", 30, 14, 12, 4, 0.0));
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TB01", TipoUnidad.BICICLETA));
        Simulador.Resultado r = Simulador.simular(pedidos, flota, Planificador.tabu(8), config(7));
        assertFalse(r.censurada);
        assertEquals(240.0, r.colapsoMin, 1e-9);
        assertEquals("A", r.pedidoColapso);
        assertEquals(0, r.pedidosEnPlazo);
    }

    /** Una moto (8 paquetes) y dos pedidos de 8 a 1 km del central: el segundo
     *  sale cuando la moto vuelve (1/25 h + 1 h de entrega + 1/25 h = 64.8 min),
     *  en el primer ciclo posterior (Sa = 1 -> minuto 65). */
    @Test
    void vehiculoQueVuelveQuedaDisponibleDesdeSuRegreso() {
        assumeTrue(TipoUnidad.MOTO.velocidadPromedio == 25.0 && Compartido.HORAS_ENTREGA == 1.0);
        List<Pedido> pedidos = List.of(new Pedido("A", "A", 28, 14, 8, 36, 0.0),
                                       new Pedido("B", "B", 28, 14, 8, 36, 0.0));
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TM01", TipoUnidad.MOTO));
        Simulador.Resultado r = Simulador.simular(pedidos, flota, Planificador.tabu(8), config(1));

        assertEquals(2, r.despachos.size());
        Simulador.Despacho primero = r.despachos.get(0), segundo = r.despachos.get(1);
        assertEquals(0.0, primero.salidaMin, 1e-9);
        assertEquals(64.8, primero.regresoMin, 1e-9);
        assertEquals(65.0, segundo.salidaMin, 1e-9);
        assertEquals(2, r.replanificaciones);   // mientras la moto está en ruta no se llama al planificador
        assertEquals(2, r.viajesMax);
    }

    /** Con carga muy baja (un pedido holgado) no hay colapso: censurada en 7 200 min. */
    @Test
    void cargaMuyBajaTerminaCensurada() {
        List<Pedido> pedidos = List.of(new Pedido("A", "A", 30, 14, 5, 36, 0.0));
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO));
        Simulador.Resultado r = Simulador.simular(pedidos, flota, Planificador.genetico(10), config(60));
        assertTrue(r.censurada);
        assertEquals(7200.0, r.colapsoMin, 1e-9);
        assertEquals(1, r.pedidosEntregados);
        assertEquals(100.0, r.pctPedidosEnPlazo(), 1e-9);
    }

    /** Misma semilla y modo por evaluaciones: exactamente el mismo resultado. */
    @Test
    void simulacionReproducibleEnModoEvaluaciones() throws IOException {
        List<Pedido> pedidos = LectorPedidos.leerAbsoluto(Instancias.ARCHIVO_SINTETICO);
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        for (Planificador p : List.of(Planificador.tabu(8), Planificador.genetico(20))) {
            Simulador.Resultado a = Simulador.simular(pedidos, flota, p, config(60));
            Simulador.Resultado b = Simulador.simular(pedidos, flota, p, config(60));
            assertEquals(a.colapsoMin, b.colapsoMin);
            assertEquals(a.costoAcumulado, b.costoAcumulado);
            assertEquals(a.evaluacionesTotales, b.evaluacionesTotales);
            assertEquals(a.despachos.size(), b.despachos.size());
            for (int i = 0; i < a.despachos.size(); i++) {
                assertEquals(a.despachos.get(i).unidad.codigo, b.despachos.get(i).unidad.codigo);
                assertEquals(a.despachos.get(i).entregas, b.despachos.get(i).entregas);
            }
        }
    }

    /** C_max calculado a mano: una entrega de 8 paquetes a 10 km del central y un auto.
     *  d̄ = 20 km; e = 24/8 = 3; t = 20/40 + 3 = 3.5 h; ⌊21/3.5⌋ = 6 viajes; 24·6 = 144. */
    @Test
    void capacidadTeoricaCalculadaAMano() {
        assumeTrue(TipoUnidad.AUTO.velocidadPromedio == 40.0 && Compartido.HORAS_ENTREGA == 1.0
                && CapacidadFlota.HORAS_EFECTIVAS == 21.0);
        CapacidadFlota.Resultado r = CapacidadFlota.calcular(List.of(new Pedido("A", 37, 14, 8, 36)),
                List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO)));
        assertEquals(20.0, r.distanciaIdaVueltaKm, 1e-9);
        assertEquals(3.5, r.tipos.get(0).horasViaje, 1e-9);
        assertEquals(6, r.tipos.get(0).viajesPorDia);
        assertEquals(144, r.cmaxPropio);
    }

    /** Misma semilla -> mismo archivo; cada día alcanza el objetivo de paquetes. */
    @Test
    void generadorDeCargaEsDeterministaYAlcanzaElObjetivo() throws IOException {
        List<LectorPedidos.Registro> base = LectorPedidos.leerRegistros(Instancias.ARCHIVO_SINTETICO);
        Path a = carpeta.resolve("a.txt"), b = carpeta.resolve("b.txt");
        GeneradorCarga.generar(base, 300, 2, 99, a);
        GeneradorCarga.generar(base, 300, 2, 99, b);
        assertEquals(Files.readAllLines(a), Files.readAllLines(b));

        List<LectorPedidos.Registro> gen = LectorPedidos.leerRegistros(a.toString());
        int[] paquetesDia = new int[2];
        for (LectorPedidos.Registro r : gen) paquetesDia[(int) (r.llegadaHoras / 24)] += r.cantidad;
        assertTrue(paquetesDia[0] >= 300 && paquetesDia[1] >= 300);
    }
}
