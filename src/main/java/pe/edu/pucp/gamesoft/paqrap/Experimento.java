package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Ejecutor de la experimentación numérica (IEN, secciones 9 y 10).
 *
 * Arma la matriz de corridas algoritmo × instancia × réplica, asigna una
 * semilla distinta y registrada a cada réplica, aleatoriza el orden de
 * ejecución, mide cada corrida y guarda todo en un CSV para el análisis
 * estadístico (Excel, R o Python).
 *
 * Uso (argumentos opcionales, en este orden):
 *   archivoVentas  réplicas  Ta_ms
 *   p. ej.:  datos/ventas202609.txt 30 10000
 *
 * NOTA: hoy cada corrida resuelve una INSTANCIA ESTÁTICA (los pedidos de una
 * ventana de tiempo). Cuando exista el simulador, basta con reemplazar el
 * método ejecutarCorrida(...) por "simular hasta el colapso" y agregar la
 * columna del tiempo hasta el colapso; el resto del ejecutor se reutiliza.
 */
public class Experimento {

    // ===================== CONFIGURACIÓN =====================
    static String ARCHIVO_VENTAS = "datos/ventas202609_SINTETICO_08a12h.txt";
    static int DIA = 1;                        // día del mes de las instancias
    static int HORA_INICIO = 8;                // hora de inicio de las ventanas
    static int[] VENTANAS_HORAS = {1, 2, 4};   // tamaños de instancia (I1, I2, I3)
    static int REPLICAS = 5;
    static long TA_MS = 10_000;                // presupuesto de tiempo por corrida (igual para ambos)
    static long SEMILLA_BASE = 1000;           // réplica r usa la semilla SEMILLA_BASE + r
    static long SEMILLA_ORDEN = 20260923;      // semilla para aleatorizar el orden de ejecución
    static int TABU_DURACION = 8;
    static int TABU_MAX_SIN_MEJORA = Integer.MAX_VALUE;   // sin corte anticipado: ambos usan todo Ta (comparación justa)
    static int AG_POBLACION = 30;
    static String SALIDA_CSV = "resultados_experimento.csv";

    /** Flota oficial (preguntas 17 y 18): TA01–TA10, TM01–TM15, TB01–TB12. */
    static List<UnidadTransporte> flotaOficial() {
        List<UnidadTransporte> flota = new ArrayList<>();
        for (int i = 1; i <= 10; i++) flota.add(new UnidadTransporte(String.format("TA%02d", i), TipoUnidad.AUTO));
        for (int i = 1; i <= 15; i++) flota.add(new UnidadTransporte(String.format("TM%02d", i), TipoUnidad.MOTO));
        for (int i = 1; i <= 12; i++) flota.add(new UnidadTransporte(String.format("TB%02d", i), TipoUnidad.BICICLETA));
        return flota;
    }

    private static class Instancia {
        String nombre;
        List<Pedido> pedidos;
        Solucion inicialCW;   // Clarke & Wright es determinista: se calcula una sola vez
        int paquetes() { int t = 0; for (Pedido p : pedidos) t += p.cantidad; return t; }
    }

    private static class Corrida {
        Instancia inst;
        String algoritmo;
        int replica;
        long semilla;
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0) ARCHIVO_VENTAS = args[0];
        if (args.length > 1) REPLICAS = Integer.parseInt(args[1]);
        if (args.length > 2) TA_MS = Long.parseLong(args[2]);

        List<UnidadTransporte> flota = flotaOficial();

        // 1. Instancias
        System.out.println("=== Instancias (archivo: " + ARCHIVO_VENTAS + ") ===");
        List<Instancia> instancias = new ArrayList<>();
        for (int v = 0; v < VENTANAS_HORAS.length; v++) {
            Instancia in = new Instancia();
            in.nombre = "I" + (v + 1) + "_" + VENTANAS_HORAS[v] + "h";
            in.pedidos = LectorPedidos.leerVentana(ARCHIVO_VENTAS,
                    LectorPedidos.hora(DIA, HORA_INICIO, 0), VENTANAS_HORAS[v]);
            in.inicialCW = Heuristicaconstructiva.construirSolucionInicial(in.pedidos, flota);
            instancias.add(in);
            System.out.printf("  %s: día %d desde las %02d:00, %d pedidos, %d paquetes%n",
                    in.nombre, DIA, HORA_INICIO, in.pedidos.size(), in.paquetes());
        }

        // 2. Matriz de corridas con semillas registradas
        List<Corrida> corridas = new ArrayList<>();
        for (Instancia in : instancias)
            for (String alg : new String[]{"TABU", "AG"})
                for (int r = 1; r <= REPLICAS; r++) {
                    Corrida c = new Corrida();
                    c.inst = in; c.algoritmo = alg; c.replica = r; c.semilla = SEMILLA_BASE + r;
                    corridas.add(c);
                }

        // 3. Orden aleatorio (evita que la carga del equipo se confunda con el algoritmo)
        Collections.shuffle(corridas, new Random(SEMILLA_ORDEN));

        long totalSeg = corridas.size() * TA_MS / 1000;
        System.out.printf("%n=== Ejecutando %d corridas (Ta = %d ms) — duración estimada: %d min %d s ===%n",
                corridas.size(), TA_MS, totalSeg / 60, totalSeg % 60);

        // 4. Ejecución y registro
        Map<String, List<double[]>> resumen = new LinkedHashMap<>();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(SALIDA_CSV), StandardCharsets.UTF_8))) {
            out.println("orden,instancia,algoritmo,replica,semilla,pedidos,paquetes,H,sin_asignar,fuera_plazo,"
                      + "pct_en_plazo,S_costo,km,vehiculos_usados,tiempo_ms");
            int orden = 0;
            for (Corrida c : corridas) {
                orden++;
                long t0 = System.nanoTime();
                Solucion s = ejecutarCorrida(c, flota);
                long ms = (System.nanoTime() - t0) / 1_000_000;

                int n = c.inst.pedidos.size();
                int fueraPlazo = s.H - s.pedidosSinAsignar.size();
                double pct = n == 0 ? 100.0 : 100.0 * (n - s.H) / n;
                double km = 0;
                for (RutaAlg r : s.rutas) km += r.distanciaKm;

                out.println(String.format(Locale.US, "%d,%s,%s,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.0f,%d,%d",
                        orden, c.inst.nombre, c.algoritmo, c.replica, c.semilla, n, c.inst.paquetes(),
                        s.H, s.pedidosSinAsignar.size(), fueraPlazo, pct, s.S, km, s.rutas.size(), ms));
                out.flush();   // si se interrumpe, lo ya corrido queda guardado

                resumen.computeIfAbsent(c.inst.nombre + " " + c.algoritmo, k -> new ArrayList<>())
                       .add(new double[]{s.H, s.S, pct});
                System.out.printf(Locale.US, "  [%3d/%d] %-6s %-4s rep=%2d semilla=%d  H=%d  S=%.2f  en plazo=%.1f%%  (%d ms)%n",
                        orden, corridas.size(), c.inst.nombre, c.algoritmo, c.replica, c.semilla, s.H, s.S, pct, ms);
            }
        }

        // 5. Resumen descriptivo (el análisis inferencial se hace sobre el CSV)
        System.out.println("\n=== Resumen por combinación ===");
        System.out.printf("%-12s %6s %8s %12s %12s %12s %10s%n", "Combinación", "n", "H medio", "S media", "S desv.", "S mín", "% plazo");
        for (Map.Entry<String, List<double[]>> e : resumen.entrySet()) {
            List<double[]> v = e.getValue();
            double hMed = 0, sMed = 0, pMed = 0, sMin = Double.MAX_VALUE;
            for (double[] x : v) { hMed += x[0]; sMed += x[1]; pMed += x[2]; sMin = Math.min(sMin, x[1]); }
            hMed /= v.size(); sMed /= v.size(); pMed /= v.size();
            double var = 0;
            for (double[] x : v) var += (x[1] - sMed) * (x[1] - sMed);
            double sd = v.size() > 1 ? Math.sqrt(var / (v.size() - 1)) : 0;
            System.out.printf(Locale.US, "%-12s %6d %8.2f %12.2f %12.2f %12.2f %9.1f%%%n",
                    e.getKey(), v.size(), hMed, sMed, sd, sMin, pMed);
        }
        System.out.println("\nResultados guardados en: " + Paths.get(SALIDA_CSV).toAbsolutePath());
    }

    /** Una corrida = un algoritmo, una instancia, una semilla, el mismo presupuesto Ta. */
    private static Solucion ejecutarCorrida(Corrida c, List<UnidadTransporte> flota) {
        if (c.algoritmo.equals("TABU")) {
            BusquedaTabu.setSemilla(c.semilla);
            return BusquedaTabu.ejecutar(c.inst.inicialCW, TA_MS, TABU_DURACION, TABU_MAX_SIN_MEJORA);
        } else {
            AlgoritmoGenetico.setSemilla(c.semilla);
            return AlgoritmoGenetico.ejecutar(c.inst.pedidos, flota, TA_MS, AG_POBLACION);
        }
    }
}