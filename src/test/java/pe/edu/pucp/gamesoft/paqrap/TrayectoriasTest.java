package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Etapa 35 (C29, A22; SI-32): el reloj no avanza mientras la simulación espera al planificador,
 * y el visualizador recibe las trayectorias del bloque en curso y del siguiente
 * (GET /api/corridas/{ESC}/bloques) para reproducir el desplazamiento sin saltos.
 */
class TrayectoriasTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    // ============================ Reloj con pausa ============================

    /** Con ritmo escalado, una pausa de N ms no hace avanzar el tiempo simulado; después el reloj
     *  sigue al ritmo K. Sin la pausa, el reloj habría saltado N ms × K. */
    @Test
    void elRelojEscaladoDescuentaLaPausa() throws InterruptedException {
        Reloj r = Reloj.escalado(6000);   // 100 min simulados por segundo real
        assertTrue(r.esperarHasta(0));
        Thread.sleep(400);                // la simulación estuvo detenida 400 ms (sin pausa: 40 min simulados)
        r.pausa(400);
        assertTrue(r.ahora(0) < 5, "tras la pausa casi no pasó tiempo simulado: " + r.ahora(0));
        long t0 = System.currentTimeMillis();
        assertTrue(r.esperarHasta(20));   // 20 min simulados = 200 ms reales al ritmo K
        long ms = System.currentTimeMillis() - t0;
        assertTrue(ms >= 150 && ms < 400, "después de la pausa sigue al ritmo K: " + ms + " ms");
    }

    /** Al terminar la corrida, su reloj se detiene en el último instante: la hora que muestra el visualizador
     *  no sigue avanzando después del fin de la SIM_5D o del colapso. */
    @Test
    void alTerminarElRelojSeDetiene() throws InterruptedException {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 30;
        c.reloj = Reloj.escalado(6000);   // 100 min simulados por segundo real
        Simulador s = Simulador.crear(List.of(new Pedido("A", "A", 30, 14, 2, 36, 0.0)),
                List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO)), Planificador.tabu(8), c);
        s.ejecutar();
        double fin = s.minutoDelReloj();
        assertEquals(30, fin, 1e-9, "termina en el horizonte");
        Thread.sleep(300);                // sin la corrección, 30 min simulados más
        assertEquals(fin, s.minutoDelReloj(), 1e-9, "el reloj de una corrida terminada no avanza");
    }

    /** Sin espera (experimento), la pausa no cambia nada. */
    @Test
    void sinEsperaLaPausaNoCambiaNada() {
        Reloj r = Reloj.sinEspera();
        r.pausa(10_000);
        assertTrue(r.esperarHasta(123));
        assertEquals(123.0, r.ahora(123), 0);
    }

    /** Reloj del experimento que anota las pausas. */
    private static final class RelojQueAnota implements Reloj {
        int pausas;

        @Override
        public boolean esperarHasta(double minutoSimulado) {
            return true;
        }

        @Override
        public void pausa(long ms) {
            pausas++;
        }
    }

    private static List<UnidadTransporte> flota() {
        return List.of(new UnidadTransporte("TA01", TipoUnidad.AUTO), new UnidadTransporte("TA02", TipoUnidad.AUTO),
                new UnidadTransporte("TM01", TipoUnidad.MOTO));
    }

    /** Pedidos de 36 h, todos registrados al inicio: ninguno es urgente ni llega después de un corte. */
    private static List<Pedido> pedidos() {
        int[][] destinos = {{40, 20}, {20, 30}, {50, 10}, {10, 10}, {35, 40}, {60, 30}, {25, 5}, {45, 45}};
        List<Pedido> ps = new ArrayList<>();
        for (int i = 0; i < destinos.length; i++)
            ps.add(new Pedido("P" + i, "P" + i, destinos[i][0], destinos[i][1], 3 + i % 4, 36, 0.0));
        return ps;
    }

    private static Simulador.Config conBloques() {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 600;
        c.bloqueMin = 120;
        c.adelantoMin = 60;
        return c;
    }

    /** Solo detienen el reloj las planificaciones en el instante (el primer bloque y las excepciones,
     *  como una avería); los bloques planificados con adelanto no lo detienen. */
    @Test
    void soloLasReplanificacionesEnElInstantePausanElReloj() {
        Simulador.Config c = conBloques();
        RelojQueAnota reloj = new RelojQueAnota();
        c.reloj = reloj;
        Simulador.Resultado r = Simulador.simular(pedidos(), flota(), Planificador.tabu(8), c);
        assertTrue(r.bloquesConAdelanto >= 3, "bloques con adelanto: " + r.bloquesConAdelanto);
        assertEquals(0, r.replanPorEvento);
        assertEquals(1, reloj.pausas, "solo el primer bloque se planificó en el instante");

        Simulador.Config c2 = conBloques();
        RelojQueAnota reloj2 = new RelojQueAnota();
        c2.reloj = reloj2;
        c2.averias = List.of(new Averia(200, "TA01", 1));
        Simulador.Resultado r2 = Simulador.simular(pedidos(), flota(), Planificador.tabu(8), c2);
        assertTrue(r2.replanPorEvento >= 1, "la avería replanifica en el instante");
        assertEquals(1 + r2.replanPorEvento, reloj2.pausas, "cada replanificación en el instante es una pausa");
    }

    /** Reloj escalado que anota cuánto duró cada pausa. */
    private static final class RelojQueMide implements Reloj {
        final Reloj base = Reloj.escalado(1200);   // 20 min simulados por segundo real
        final List<Long> pausas = new ArrayList<>();

        @Override
        public boolean esperarHasta(double t) {
            return base.esperarHasta(t);
        }

        @Override
        public double ahora(double t) {
            return base.ahora(t);
        }

        @Override
        public void pausa(long ms) {
            pausas.add(ms);
            base.pausa(ms);
        }
    }

    /** Con el visualizador, una avería que llega mientras se calcula el plan del bloque siguiente
     *  replanifica en el instante SIN esperar a ese cálculo (que ya no sirve): la pausa dura lo que
     *  dura su propia replanificación, no lo que le faltaba al plan adelantado. */
    @Test
    void unaExcepcionNoEsperaAlPlanAdelantado() {
        Simulador.Config c = conBloques();
        c.adelantoEnParalelo = true;
        RelojQueMide reloj = new RelojQueMide();
        c.reloj = reloj;
        c.horizonteMin = 200;
        c.averias = List.of(new Averia(70, "TA01", 1));   // 10 min después del corte del bloque de las 2 h
        Planificador tabu = Planificador.tabu(8);
        Planificador lentoConAdelanto = e -> {   // el plan adelantado tarda 2 s reales; el del instante, lo normal
            if (Thread.currentThread().getName().startsWith("planificador-bloques")) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return tabu.planificar(e);
        };
        Simulador.Resultado r = Simulador.simular(pedidos(), flota(), lentoConAdelanto, c);
        assertTrue(r.replanPorEvento >= 1, "la avería replanificó en el instante");
        assertTrue(reloj.pausas.size() >= 2, "pausas: " + reloj.pausas);
        long maxima = reloj.pausas.stream().mapToLong(Long::longValue).max().orElse(0);
        assertTrue(maxima < 1000, "ninguna pausa esperó al plan adelantado de 2 s: " + reloj.pausas);
    }

    /** Un plan adelantado que se vuelve a lanzar tarde (por una avería 12 min simulados antes del bloque) usa
     *  la mitad del tiempo real que le queda, para estar listo a tiempo; lanzado en el corte usa el del bloque. */
    @Test
    void elPlanLanzadoTardeUsaElTiempoQueLeQueda() {
        Simulador.Config c = conBloques();
        c.adelantoEnParalelo = true;
        c.reloj = Reloj.escalado(600);   // 1 s real = 10 min simulados (ritmo del escenario hasta el colapso)
        c.factorReloj = 600;
        c.taBloqueMs = 7500;
        c.horizonteMin = 125;
        c.maxEvaluaciones = 0;   // parada por tiempo: se mide el presupuesto
        c.taMs = 300;
        c.averias = List.of(new Averia(108, "TA01", 1));   // 12 min simulados (1.2 s reales) antes del bloque de las 2 h;
                                                            // con los 7.5 s del bloque, el plan relanzado llegaría tarde
        Simulador.Resultado r = Simulador.simular(pedidos(), flota(), Planificador.tabu(8), c);
        assertEquals(0, r.bloquesTardios, "el plan relanzado llegó a tiempo");
        assertTrue(r.registro.stream().anyMatch(e -> e.min == 120 && e.detalle.contains("con adelanto desde 01d01h48m")),
                "el bloque de las 2 h usó el plan relanzado tras la avería");
    }

    // ============================ Trayectorias por bloques ============================

    /** Reloj que deja avanzar la simulación hasta un minuto límite y la detiene ahí (para mirarla). */
    private static final class RelojConCompuerta implements Reloj {
        private double limite;

        synchronized void abrirHasta(double minuto) {
            limite = minuto;
            notifyAll();
        }

        @Override
        public synchronized boolean esperarHasta(double t) {
            while (t > limite) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final Pattern UNIDAD = Pattern.compile("\\{\"codigo\":\"(\\w+)\",\"x\":(\\d+),\"y\":(\\d+)(?:,\"velocidad\":[\\d.]+)?,\"pasos\":\\[(.*?)\\]\\}");
    private static final Pattern PASO = Pattern.compile("\\[([\\d.]+),([\\d.]+),(\\d+),(\\d+)\\]");

    /** Pasos {salida, llegada, x, y} por unidad, más su posición de partida {x, y} con la clave "@codigo". */
    private static Map<String, List<double[]>> leer(String json) {
        Map<String, List<double[]>> m = new LinkedHashMap<>();
        Matcher u = UNIDAD.matcher(json);
        while (u.find()) {
            List<double[]> pasos = new ArrayList<>();
            Matcher p = PASO.matcher(u.group(4));
            while (p.find()) pasos.add(new double[]{Double.parseDouble(p.group(1)), Double.parseDouble(p.group(2)),
                    Integer.parseInt(p.group(3)), Integer.parseInt(p.group(4))});
            m.put(u.group(1), pasos);
            m.put("@" + u.group(1), List.of(new double[]{Integer.parseInt(u.group(2)), Integer.parseInt(u.group(3))}));
        }
        return m;
    }

    /** Cada paso va a un nodo vecino del anterior, sale después de llegar al anterior y llega después de salir. */
    private static void verificarContinuidad(Map<String, List<double[]>> tr) {
        for (Map.Entry<String, List<double[]>> e : tr.entrySet()) {
            if (e.getKey().startsWith("@")) continue;
            double[] pos = tr.get("@" + e.getKey()).get(0);
            double x = pos[0], y = pos[1], llegada = Double.NEGATIVE_INFINITY;
            for (double[] p : e.getValue()) {
                assertEquals(1, Math.abs(p[2] - x) + Math.abs(p[3] - y), 1e-9, e.getKey() + ": paso a un nodo no vecino");
                assertTrue(p[0] <= p[1] && p[0] >= llegada - 0.01, e.getKey() + ": horas incoherentes");
                x = p[2];
                y = p[3];
                llegada = p[1];
            }
        }
    }

    /**
     * Con el visualizador (plan adelantado en otro hilo), /bloques trae la trayectoria vigente y, ya
     * calculado, el plan del bloque siguiente en cola. Al empezar ese bloque se aplica exactamente lo
     * que estaba en cola (no hubo pedidos después del corte): la transición es exacta, sin saltos.
     */
    @Test
    void elBloqueEnColaEsElQueSeAplicaAlEmpezar() throws Exception {
        Simulador.Config c = conBloques();
        c.adelantoEnParalelo = true;
        RelojConCompuerta reloj = new RelojConCompuerta();
        c.reloj = reloj;
        Simulador s = Simulador.crear(pedidos(), flota(), Planificador.tabu(8), c);
        Thread hilo = new Thread(s::ejecutar);
        hilo.start();
        try {
            // Después del corte del bloque de las 2 h (minuto 60) y antes de que empiece
            reloj.abrirHasta(70);
            String antes = esperar(s, 60, true);
            assertTrue(antes.contains("\"bloque_fin_min\":120.000"), antes);
            String cola = antes.substring(antes.indexOf("\"siguiente\":"));
            assertTrue(cola.contains("\"inicio_min\":120.000"), cola);
            verificarContinuidad(leer(antes.substring(0, antes.indexOf("\"siguiente\":"))));
            Map<String, List<double[]>> enCola = leer(cola);
            verificarContinuidad(enCola);
            int version = version(antes);

            // Ya empezó el bloque de las 2 h: el plan aplicado es el que estaba en cola
            reloj.abrirHasta(125);
            String despues = esperarVersion(s, version);
            assertTrue(despues.contains("\"bloque_inicio_min\":120.000"), "ya empezó el bloque de las 2 h: " + despues);
            double ahora = Double.parseDouble(despues.replaceAll("(?s).*?\"ahora_min\":([\\d.]+).*", "$1"));
            Map<String, List<double[]>> aplicado = leer(despues.substring(0, despues.indexOf("\"siguiente\":")));
            verificarContinuidad(aplicado);
            int comparados = 0;
            for (Map.Entry<String, List<double[]>> e : aplicado.entrySet()) {
                if (e.getKey().startsWith("@")) continue;
                List<double[]> esperado = new ArrayList<>();
                for (double[] p : enCola.getOrDefault(e.getKey(), List.of())) if (p[1] > ahora + 1e-9) esperado.add(p);
                assertEquals(esperado.size(), e.getValue().size(), e.getKey() + ": distinta cantidad de pasos");
                for (int i = 0; i < esperado.size(); i++) {
                    for (int k = 0; k < 4; k++) assertEquals(esperado.get(i)[k], e.getValue().get(i)[k], 0.011, e.getKey() + " paso " + i);
                    comparados++;
                }
            }
            assertTrue(comparados > 10, "se compararon " + comparados + " pasos");
        } finally {
            s.detener();
            reloj.abrirHasta(Double.POSITIVE_INFINITY);
            hilo.join(10_000);
        }
        assertFalse(hilo.isAlive());
    }

    private static int version(String json) {
        return Integer.parseInt(json.replaceAll("(?s).*?\"version\":(\\d+).*", "$1"));
    }

    /** Espera a que se aplique un plan nuevo (versión mayor que la indicada). */
    private static String esperarVersion(Simulador s, int version) throws InterruptedException {
        long limite = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < limite) {
            String j = s.bloquesJson();
            if (version(j) > version) return j;
            Thread.sleep(20);
        }
        throw new AssertionError("no se aplicó un plan nuevo");
    }

    /** Espera a que la simulación pase del minuto indicado (y, si se pide, a que el bloque siguiente esté listo). */
    private static String esperar(Simulador s, double minuto, boolean conCola) throws InterruptedException {
        long limite = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < limite) {
            if (s.minutoActual() < minuto) { Thread.sleep(20); continue; }
            String j = s.bloquesJson();   // espera el cerrojo: el instante ya se procesó
            if (!conCola || j.contains("\"siguiente\":{")) return j;
            Thread.sleep(20);
        }
        throw new AssertionError("la simulación no llegó al minuto " + minuto);
    }
}
