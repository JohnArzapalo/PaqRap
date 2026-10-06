package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Etapas 5, 10.3 y 13: simulador, capacidad teórica, generación de carga, parciales y escenarios. */
class SimuladorTest {

    @TempDir
    Path carpeta;

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    static Simulador.Config config(double saMin) {
        Simulador.Config c = new Simulador.Config();
        c.saMin = saMin;
        c.horizonteMin = 7200;
        c.maxEvaluaciones = 500;   // rápido y determinista
        c.semilla = 1;
        c.alimentacion = false;
        return c;
    }

    /** Un pedido que no se puede completar a tiempo (20 paquetes y una sola
     *  bicicleta: aun repartiendo en viajes de 4, con 1.5 h por viaje, solo
     *  llegan 12 antes de las 4 h) colapsa en el minuto exacto de su hora
     *  límite (hl = 4 h -> 240 min), aunque la replanificación sea cada 7 min.
     *  Hasta la etapa 30 eran 12 paquetes, que entonces no cabían en la
     *  bicicleta; con el reparto por productos (SI-28) sí llegan a tiempo. */
    @Test
    void pedidoVencidoSinEntregaColapsaEnElMinutoExacto() {
        List<Pedido> pedidos = List.of(new Pedido("A", "A", 30, 14, 20, 4, 0.0));
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TB01", TipoUnidad.BICICLETA));
        Simulador.Resultado r = Simulador.simular(pedidos, flota, Planificador.tabu(8), config(7));
        assertFalse(r.censurada);
        assertEquals(240.0, r.colapsoMin, 1e-9);
        assertTrue(r.pedidoColapso.startsWith("A"));
        assertEquals(0, r.pedidosEnPlazo);
    }

    /** Una moto (8 paquetes) y dos pedidos de 8 a 1 km del central: el segundo
     *  se carga cuando la moto vuelve al central (1/25 h + 1 h de entrega + 1/25 h
     *  = 64.8 min), y no antes (con Sa = 1 min, a más tardar en el minuto 65.8). */
    @Test
    void vehiculoQueVuelveQuedaDisponibleDesdeSuRegreso() {
        assumeTrue(TipoUnidad.MOTO.velocidadPromedio == 25.0 && Compartido.HORAS_ENTREGA == 1.0);
        List<Pedido> pedidos = List.of(new Pedido("A", "A", 28, 14, 8, 36, 0.0),
                                       new Pedido("B", "B", 28, 14, 8, 36, 0.0));
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TM01", TipoUnidad.MOTO));
        Simulador.Config cfg = config(1);
        cfg.horizonteMin = 300;
        Simulador.Resultado r = Simulador.simular(pedidos, flota, Planificador.tabu(8), cfg);

        List<Double> cargas = new ArrayList<>();
        for (Simulador.Evt e : r.registro) if (e.tipo == Simulador.Evt.T.RECARGA && e.cantidad > 0) cargas.add(e.min);
        assertEquals(2, cargas.size(), "dos viajes");
        assertEquals(0.0, cargas.get(0), 1e-9);
        assertTrue(cargas.get(1) >= 64.8 - 1e-9 && cargas.get(1) < 64.8 + 1.0, "segunda carga en " + cargas.get(1));
        assertEquals(2, r.viajesMax);
        assertEquals(2, r.pedidosEntregados);
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

    /** Misma semilla y modo por evaluaciones: exactamente el mismo resultado (con bloqueos y almacenes). */
    @Test
    void simulacionReproducibleEnModoEvaluaciones() throws IOException {
        List<Pedido> pedidos = LectorPedidos.leerAbsoluto(Instancias.ARCHIVO_SINTETICO);
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        MapaVial mapa = MapaVial.leer("datos/202609_SINTETICO.bloqueadas");
        for (Planificador p : List.of(Planificador.tabu(8), Planificador.genetico(20))) {
            Simulador.Config c1 = config(60), c2 = config(60);
            c1.mapa = c2.mapa = mapa;
            c1.horizonteMin = c2.horizonteMin = 1440;
            Simulador.Resultado a = Simulador.simular(pedidos, flota, p, c1);
            Simulador.Resultado b = Simulador.simular(pedidos, flota, p, c2);
            assertEquals(a.colapsoMin, b.colapsoMin);
            assertEquals(a.costoAcumulado, b.costoAcumulado);
            assertEquals(a.evaluacionesTotales, b.evaluacionesTotales);
            assertEquals(a.registro.size(), b.registro.size());
            for (int i = 0; i < a.registro.size(); i++) {
                assertEquals(a.registro.get(i).tipo, b.registro.get(i).tipo);
                assertEquals(a.registro.get(i).min, b.registro.get(i).min);
                assertEquals(a.registro.get(i).unidad, b.registro.get(i).unidad);
            }
        }
    }

    /** 10.3: un pedido urgente de 20 paquetes sin autos colapsa por capacidad si no se
     *  divide; con la estrategia "urgentes" se reparte entre motos y se entrega a tiempo.
     *  Etapa 31 (SI-28): con el reparto por productos (lo vigente) también se entrega a
     *  tiempo, sin estrategia "urgentes": el planificador reparte según la capacidad. */
    @Test
    void entregasParcialesEvitanElColapsoPorCapacidad() {
        List<Pedido> pedidos = List.of(new Pedido("G", "G", 30, 14, 20, 4, 0.0));
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TM01", TipoUnidad.MOTO),
                                               new UnidadTransporte("TM02", TipoUnidad.MOTO));
        Simulador.Config sin = config(30);
        sin.horizonteMin = 600;
        sin.estrategiaParciales = "ninguna";
        sin.reparto = false;
        Simulador.Resultado r1 = Simulador.simular(pedidos, flota, Planificador.tabu(8), sin);
        assertFalse(r1.censurada);
        assertEquals("capacidad", r1.causaColapso);

        Simulador.Config con = config(30);
        con.horizonteMin = 600;
        con.estrategiaParciales = "urgentes";
        con.reparto = false;
        Simulador.Resultado r2 = Simulador.simular(pedidos, flota, Planificador.tabu(8), con);
        assertTrue(r2.censurada, "sin colapso: " + r2.causaColapso);
        assertEquals(1, r2.parcialesCreadas);
        assertEquals(1, r2.pedidosEnPlazo);

        Simulador.Config porProductos = config(30);
        porProductos.horizonteMin = 600;
        porProductos.estrategiaParciales = "ninguna";
        porProductos.reparto = true;
        Simulador.Resultado r3 = Simulador.simular(pedidos, flota, Planificador.tabu(8), porProductos);
        assertTrue(r3.censurada, "sin colapso: " + r3.causaColapso);
        assertTrue(r3.parcialesCreadas >= 1, "el pedido se repartió");
        assertEquals(1, r3.pedidosEnPlazo);
    }

    /** 13.3: el registro de eventos incluye replanificaciones, cargas, entregas y el colapso. */
    @Test
    void registroDeEventos() {
        StringWriter sw = new StringWriter();
        Simulador.Config c = config(60);
        c.eventos = new PrintWriter(sw);
        List<Pedido> pedidos = List.of(new Pedido("A", "A", 30, 14, 3, 36, 0.0),
                                       new Pedido("B", "B", 60, 45, 12, 4, 0.0));
        Simulador.simular(pedidos, List.of(new UnidadTransporte("TB01", TipoUnidad.BICICLETA)),
                Planificador.tabu(8), c);
        c.eventos.flush();
        String log = sw.toString();
        assertTrue(log.startsWith("tiempo_min,dia_hora,evento"));
        for (String ev : new String[]{"REPLANIFICACION", "RECARGA", "ENTREGA", "COLAPSO"})
            assertTrue(log.contains("," + ev + ","), "falta " + ev);
    }

    /** 13.2: DIA_A_DIA sigue el reloj (aquí uno de prueba que no duerme): instantes crecientes. */
    @Test
    void escenarioDiaADiaUsaElReloj() {
        List<Double> instantes = new ArrayList<>();
        Simulador.Config c = config(30);
        c.escenario = Simulador.Escenario.DIA_A_DIA;
        c.horizonteMin = 120;
        c.reloj = instantes::add;
        Simulador.Resultado r = Simulador.simular(List.of(new Pedido("A", "A", 30, 14, 5, 36, 0.5)),
                List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO)), Planificador.tabu(8), c);
        assertTrue(r.censurada);
        assertEquals(120.0, r.finMin, 1e-9);
        for (int i = 1; i < instantes.size(); i++) assertTrue(instantes.get(i) >= instantes.get(i - 1));
        assertEquals(120.0, instantes.get(instantes.size() - 1), 1e-9);
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
