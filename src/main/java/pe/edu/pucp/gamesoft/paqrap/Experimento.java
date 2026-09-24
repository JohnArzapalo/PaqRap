package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ejecutor de la experimentación numérica (IEN, secciones 9 y 10).
 *
 * Arma la matriz de corridas algoritmo × instancia × réplica, asigna una
 * semilla distinta y registrada a cada réplica, aleatoriza el orden de
 * ejecución, mide cada corrida y guarda todo en un CSV para el análisis
 * estadístico (analisis/analisis_experimento.py).
 *
 * Uso con argumentos posicionales (compatibilidad):
 *   archivoVentas  réplicas  Ta_ms
 *   p. ej.:  datos/ventas202609.txt 5 10000
 *
 * Uso con argumentos con nombre (todos opcionales), p. ej. para repartir
 * las corridas entre varias PCs:
 *   --modo estatico|simulacion   (por defecto estatico)
 *   --archivo datos/x.txt --replicas 5 --ta 10000 --algoritmos TABU
 *   --salida res_pc1.csv --max-evaluaciones 0 --busqueda-local si|no
 *   Solo modo estático:   --instancias I1,I2 --ventanas 1d08h00m/1,1d09h30m/1
 *   Solo modo simulación: --niveles BAJA,MEDIA,ALTA --sa 60
 *     --escenario EXPERIMENTO|SIM_5D|COLAPSO|DIA_A_DIA (implica --modo simulacion)
 *     --bloqueos archivo.bloqueadas|no --averias si|no --archivo-averias f.txt
 *     --mantenimiento mant.preventivo --parciales ninguna|urgentes
 *     --acelerado si|no (SIM_5D/DIA_A_DIA sin esperar) --eventos prefijo.csv
 *     --destino-bloqueado esperar|nodo_vecino|no_evaluable --excluir-destinos-bloqueados si|no
 *
 * Los valores por defecto salen de config/parametros.properties.
 *
 * Modo ESTÁTICO (preliminar): cada corrida resuelve una instancia estática
 * (los pedidos de una ventana de tiempo).
 * Modo SIMULACIÓN (diseño del IEN): algoritmo × nivel de carga × réplica;
 * cada corrida simula hasta el colapso o el horizonte (ExperimentoSimulacion).
 */
public class Experimento {

    // ===================== CONFIGURACIÓN =====================
    static String ARCHIVO_VENTAS = "datos/ventas202609_SINTETICO_08a12h.txt";
    static int REPLICAS = (int) Parametros.entero("experimento.replicas", 5);
    static long TA_MS = Parametros.entero("experimento.ta_ms", 10_000);   // igual para ambos algoritmos
    /** 0 = parada por tiempo Ta (principal). > 0 = parada por evaluaciones (reproducible). */
    static long MAX_EVALUACIONES = Parametros.entero("experimento.max_evaluaciones", 0);
    /** Instancias: ventanas DdHHhMMm/duración_h que no se superponen. */
    static String VENTANAS = Parametros.texto("experimento.ventanas", "1d08h00m/1,1d09h30m/1,1d11h00m/1");
    static long SEMILLA_BASE = 1000;           // réplica r usa la semilla SEMILLA_BASE + r
    static long SEMILLA_ORDEN = 20260923;      // semilla para aleatorizar el orden de ejecución
    static int TABU_DURACION = (int) Parametros.entero("tabu.duracion", 8);
    static int TABU_MAX_SIN_MEJORA = Integer.MAX_VALUE;   // sin corte anticipado: ambos usan todo Ta (comparación justa)
    static int AG_POBLACION = AlgoritmoGenetico.POBLACION;
    /** Si es null, se usa resultados_experimento[_SINTETICO].csv según el archivo de ventas. */
    static String SALIDA_CSV = null;
    static Set<String> FILTRO_INSTANCIAS = null;   // null = todas
    static Set<String> FILTRO_ALGORITMOS = null;   // null = ambos
    static final String[] ALGORITMOS = {"TABU", "AG"};
    /** "estatico" o "simulacion". */
    static String MODO = "estatico";
    /** true si se pasó --ta; si no, el modo simulación usa simulacion.ta_ms. */
    static boolean TA_EXPLICITO = false;
    static Set<String> FILTRO_NIVELES = null;      // null = todos (modo simulación)
    static Double SA_MIN = null;                   // null = simulacion.sa_min
    /** true si se pasó --archivo (o el posicional); si no, la simulación usa el archivo sintético del mes. */
    static boolean ARCHIVO_EXPLICITO = false;

    /** Flota oficial (preguntas 17 y 18): TA01–TA10, TM01–TM15, TB01–TB12. */
    static List<UnidadTransporte> flotaOficial() {
        List<UnidadTransporte> flota = new ArrayList<>();
        for (int i = 1; i <= 10; i++) flota.add(new UnidadTransporte(String.format("TA%02d", i), TipoUnidad.AUTO));
        for (int i = 1; i <= 15; i++) flota.add(new UnidadTransporte(String.format("TM%02d", i), TipoUnidad.MOTO));
        for (int i = 1; i <= 12; i++) flota.add(new UnidadTransporte(String.format("TB%02d", i), TipoUnidad.BICICLETA));
        return flota;
    }

    /** Ventana de pedidos: desde el día/hora indicados, durante duracionHoras. */
    static class Ventana {
        private static final Pattern FORMATO = Pattern.compile("(\\d+)d(\\d+)h(\\d+)m/(\\d+(?:\\.\\d+)?)");
        final int dia, hora, minuto;
        final double duracionHoras;
        final String texto;

        Ventana(int dia, int hora, int minuto, double duracionHoras, String texto) {
            this.dia = dia;
            this.hora = hora;
            this.minuto = minuto;
            this.duracionHoras = duracionHoras;
            this.texto = texto;
        }

        double inicio() {
            return LectorPedidos.hora(dia, hora, minuto);
        }

        double fin() {
            return inicio() + duracionHoras;
        }

        static Ventana leer(String t) {
            Matcher m = FORMATO.matcher(t.trim());
            if (!m.matches())
                throw new IllegalArgumentException("Ventana con formato inválido (use DdHHhMMm/horas): " + t);
            return new Ventana(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Double.parseDouble(m.group(4)), t.trim());
        }

        /** Lee la lista y verifica que las ventanas no se superpongan (R8, R9). */
        static List<Ventana> leerLista(String lista) {
            List<Ventana> v = new ArrayList<>();
            for (String t : lista.split(",")) if (!t.isBlank()) v.add(leer(t));
            List<Ventana> ordenadas = new ArrayList<>(v);
            ordenadas.sort(Comparator.comparingDouble(Ventana::inicio));
            for (int i = 1; i < ordenadas.size(); i++) {
                if (ordenadas.get(i).inicio() < ordenadas.get(i - 1).fin())
                    throw new IllegalArgumentException("Las ventanas " + ordenadas.get(i - 1).texto + " y "
                            + ordenadas.get(i).texto + " se superponen: las instancias deben ser independientes.");
            }
            return v;
        }
    }

    private static class Instancia {
        String nombre;
        Ventana ventana;
        List<Pedido> pedidos;   // entregas (partes de hasta 24 paquetes)
        int pedidosOriginales;
        int vencidosAlPlanificar;
        int paquetes() { int t = 0; for (Pedido p : pedidos) t += p.cantidad; return t; }
    }

    private static class Corrida {
        Instancia inst;
        String algoritmo;
        int replica;
        long semilla;
    }

    /** Resultado de una corrida con los contadores del algoritmo. */
    private static class Resultado {
        Solucion solucion;
        long iteraciones, evaluaciones, tiempoMejorMs;
        Integer tramosCambioTipo;   // solo AG
    }

    public static void main(String[] args) throws IOException {
        leerArgumentos(args);
        if (MODO.equals("simulacion")) {
            ExperimentoSimulacion.ejecutar();
            return;
        }
        List<UnidadTransporte> flota = flotaOficial();
        boolean sintetico = ARCHIVO_VENTAS.toUpperCase(Locale.ROOT).contains("SINTETICO");
        if (SALIDA_CSV == null) SALIDA_CSV = "resultados_experimento" + (sintetico ? "_SINTETICO" : "") + ".csv";
        String modoParada = MAX_EVALUACIONES > 0 ? "evaluaciones" : "tiempo";
        String nombreArchivo = Paths.get(ARCHIVO_VENTAS).getFileName().toString();

        if (sintetico) System.out.println("*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***");
        System.out.println("AG: búsqueda local memética " + (AlgoritmoGenetico.BUSQUEDA_LOCAL ? "ACTIVADA" : "desactivada"));

        // 1. Instancias (ventanas que no se superponen)
        System.out.println("=== Instancias (archivo: " + ARCHIVO_VENTAS + ") ===");
        List<Ventana> ventanas = Ventana.leerLista(VENTANAS);
        List<Instancia> instancias = new ArrayList<>();
        for (int v = 0; v < ventanas.size(); v++) {
            Instancia in = new Instancia();
            in.nombre = "I" + (v + 1);
            in.ventana = ventanas.get(v);
            if (FILTRO_INSTANCIAS != null && !FILTRO_INSTANCIAS.contains(in.nombre)) continue;
            in.pedidos = LectorPedidos.leerVentana(ARCHIVO_VENTAS, in.ventana.inicio(), in.ventana.duracionHoras);
            Set<String> originales = new LinkedHashSet<>();
            for (Pedido p : in.pedidos) originales.add(p.idOriginal);
            in.pedidosOriginales = originales.size();
            in.vencidosAlPlanificar = Compartido.pedidosOriginalesVencidos(in.pedidos, flota);
            instancias.add(in);
            // Solo informativo: C&W es determinista; en cada corrida de Tabú se vuelve a construir dentro de su Ta
            Solucion cw = Heuristicaconstructiva.construirSolucionInicial(in.pedidos, flota);
            System.out.printf(Locale.US, "  %s: ventana %s (planifica al final), %d pedidos originales, %d entregas, "
                            + "%d paquetes, %d vencidos al planificar | C&W: H=%d S=%.2f vehículos=%d%n",
                    in.nombre, in.ventana.texto, in.pedidosOriginales, in.pedidos.size(), in.paquetes(),
                    in.vencidosAlPlanificar, cw.H, cw.S, cw.vehiculosUsados());
        }

        // 2. Matriz de corridas con semillas registradas
        List<Corrida> corridas = new ArrayList<>();
        for (Instancia in : instancias)
            for (String alg : ALGORITMOS) {
                if (FILTRO_ALGORITMOS != null && !FILTRO_ALGORITMOS.contains(alg)) continue;
                for (int r = 1; r <= REPLICAS; r++) {
                    Corrida c = new Corrida();
                    c.inst = in; c.algoritmo = alg; c.replica = r; c.semilla = SEMILLA_BASE + r;
                    corridas.add(c);
                }
            }

        // 3. Orden aleatorio (evita que la carga del equipo se confunda con el algoritmo)
        Collections.shuffle(corridas, new Random(SEMILLA_ORDEN));

        if (MAX_EVALUACIONES > 0) {
            System.out.printf("%n=== Ejecutando %d corridas (parada por %d evaluaciones; se ignora Ta) ===%n",
                    corridas.size(), MAX_EVALUACIONES);
        } else {
            long totalSeg = corridas.size() * TA_MS / 1000;
            System.out.printf("%n=== Ejecutando %d corridas (Ta = %d ms) — duración estimada: %d min %d s ===%n",
                    corridas.size(), TA_MS, totalSeg / 60, totalSeg % 60);
        }

        // 4. Ejecución y registro
        Map<String, List<double[]>> resumen = new LinkedHashMap<>();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(SALIDA_CSV), StandardCharsets.UTF_8))) {
            out.println("orden,instancia,ventana,archivo_ventas,algoritmo,replica,semilla,modo_parada,ta_ms,"
                      + "max_evaluaciones,pedidos_originales,entregas,paquetes,vencidos_al_planificar,H,"
                      + "sin_asignar,fuera_plazo,pct_entregas_en_plazo,pct_pedidos_en_plazo,S_costo,km,"
                      + "vehiculos_usados,iteraciones,evaluaciones,tramos_cambio_tipo,tiempo_ms,tiempo_mejor_ms,busqueda_local");
            int orden = 0;
            for (Corrida c : corridas) {
                orden++;
                long t0 = System.nanoTime();
                Resultado res = ejecutarCorrida(c, flota);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                Solucion s = res.solucion;

                int entregas = c.inst.pedidos.size();
                int fueraPlazo = s.H - s.pedidosSinAsignar.size();
                double pctEntregas = entregas == 0 ? 100.0 : 100.0 * (entregas - s.H) / entregas;
                int[] ped = Compartido.pedidosOriginalesEnPlazo(s);
                double pctPedidos = ped[1] == 0 ? 100.0 : 100.0 * ped[0] / ped[1];
                double km = 0;
                for (RutaAlg r : s.rutas) km += r.distanciaKm;

                out.println(String.format(Locale.US,
                        "%d,%s,%s,%s,%s,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.0f,%d,%d,%d,%s,%d,%d,%s",
                        orden, c.inst.nombre, c.inst.ventana.texto, nombreArchivo, c.algoritmo, c.replica, c.semilla,
                        modoParada, TA_MS, MAX_EVALUACIONES, c.inst.pedidosOriginales, entregas, c.inst.paquetes(),
                        c.inst.vencidosAlPlanificar, s.H, s.pedidosSinAsignar.size(), fueraPlazo, pctEntregas,
                        pctPedidos, s.S, km, s.vehiculosUsados(), res.iteraciones, res.evaluaciones,
                        res.tramosCambioTipo == null ? "" : res.tramosCambioTipo.toString(), ms, res.tiempoMejorMs,
                        c.algoritmo.equals("AG") ? (AlgoritmoGenetico.BUSQUEDA_LOCAL ? "si" : "no") : ""));
                out.flush();   // si se interrumpe, lo ya corrido queda guardado

                resumen.computeIfAbsent(c.inst.nombre + " " + c.algoritmo, k -> new ArrayList<>())
                       .add(new double[]{s.H, s.S, pctPedidos});
                System.out.printf(Locale.US, "  [%3d/%d] %-3s %-4s rep=%2d semilla=%d  H=%d  S=%.2f  pedidos en plazo=%.1f%%"
                                + "  vehículos=%d  iter=%d  eval=%d  (%d ms)%n",
                        orden, corridas.size(), c.inst.nombre, c.algoritmo, c.replica, c.semilla, s.H, s.S, pctPedidos,
                        s.vehiculosUsados(), res.iteraciones, res.evaluaciones, ms);
            }
        }

        // 5. Resumen descriptivo (el análisis inferencial se hace sobre el CSV)
        System.out.println("\n=== Resumen por combinación ===");
        System.out.printf("%-12s %6s %8s %12s %12s %12s %10s%n", "Combinación", "n", "H medio", "S media", "S desv.", "S mín", "% pedidos");
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
        if (sintetico) System.out.println("*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***");
        System.out.println("\nResultados guardados en: " + Paths.get(SALIDA_CSV).toAbsolutePath());
    }

    /** Argumentos posicionales (archivo réplicas Ta_ms) o con nombre (--clave valor). */
    static void leerArgumentos(String[] args) {
        if (args.length == 0) return;
        if (!args[0].startsWith("--")) {
            ARCHIVO_VENTAS = args[0];
            ARCHIVO_EXPLICITO = true;
            if (args.length > 1) REPLICAS = Integer.parseInt(args[1]);
            if (args.length > 2) TA_MS = Long.parseLong(args[2]);
            return;
        }
        for (int i = 0; i < args.length; i += 2) {
            String clave = args[i];
            if (i + 1 >= args.length) throw new IllegalArgumentException("Falta el valor de " + clave);
            String valor = args[i + 1];
            switch (clave) {
                case "--archivo": ARCHIVO_VENTAS = valor; ARCHIVO_EXPLICITO = true; break;
                case "--escenario":
                    ExperimentoSimulacion.ESCENARIO = Simulador.Escenario.valueOf(valor.toUpperCase(Locale.ROOT));
                    MODO = "simulacion";
                    break;
                case "--bloqueos": ExperimentoSimulacion.ARCHIVO_BLOQUEOS = valor; break;
                case "--averias": ExperimentoSimulacion.AVERIAS = !"no".equalsIgnoreCase(valor); break;
                case "--archivo-averias": ExperimentoSimulacion.ARCHIVO_AVERIAS = valor; ExperimentoSimulacion.AVERIAS = true; break;
                case "--mantenimiento": ExperimentoSimulacion.ARCHIVO_MANTENIMIENTO = valor; break;
                case "--parciales": ExperimentoSimulacion.PARCIALES = valor; break;
                case "--acelerado": ExperimentoSimulacion.ACELERADO = !"no".equalsIgnoreCase(valor); break;
                case "--eventos": ExperimentoSimulacion.PREFIJO_EVENTOS = valor; break;
                case "--destino-bloqueado": ExperimentoSimulacion.DESTINO_BLOQUEADO = valor; break;
                case "--excluir-destinos-bloqueados":
                    ExperimentoSimulacion.EXCLUIR_DESTINOS_BLOQUEADOS = !"no".equalsIgnoreCase(valor);
                    break;
                case "--replicas": REPLICAS = Integer.parseInt(valor); break;
                case "--ta": TA_MS = Long.parseLong(valor); TA_EXPLICITO = true; break;
                case "--max-evaluaciones": MAX_EVALUACIONES = Long.parseLong(valor); break;
                case "--ventanas": VENTANAS = valor; break;
                case "--instancias": FILTRO_INSTANCIAS = lista(valor); break;
                case "--algoritmos": FILTRO_ALGORITMOS = lista(valor); break;
                case "--salida": SALIDA_CSV = valor; break;
                case "--modo":
                    MODO = valor.toLowerCase(Locale.ROOT);
                    if (!MODO.equals("estatico") && !MODO.equals("simulacion"))
                        throw new IllegalArgumentException("--modo debe ser estatico o simulacion: " + valor);
                    break;
                case "--niveles": FILTRO_NIVELES = lista(valor); break;
                case "--sa": SA_MIN = Double.parseDouble(valor); break;
                case "--busqueda-local": AlgoritmoGenetico.BUSQUEDA_LOCAL = !"no".equalsIgnoreCase(valor); break;
                default:
                    throw new IllegalArgumentException("Argumento desconocido: " + clave + ". Válidos: --modo --archivo "
                            + "--replicas --ta --max-evaluaciones --ventanas --instancias --algoritmos --salida "
                            + "--niveles --sa --busqueda-local --escenario --bloqueos --averias --archivo-averias "
                            + "--mantenimiento --parciales --acelerado --eventos --destino-bloqueado --excluir-destinos-bloqueados");
            }
        }
    }

    static Set<String> lista(String valor) {
        Set<String> s = new LinkedHashSet<>();
        for (String t : valor.split(",")) if (!t.isBlank()) s.add(t.trim().toUpperCase(Locale.ROOT));
        return s;
    }

    /** Una corrida = un algoritmo, una instancia, una semilla, el mismo presupuesto Ta.
     *  Tabú construye su solución inicial (C&W) DENTRO de su Ta, igual que el AG
     *  crea y evalúa su población inicial dentro del suyo (R10). */
    private static Resultado ejecutarCorrida(Corrida c, List<UnidadTransporte> flota) {
        Resultado r = new Resultado();
        if (c.algoritmo.equals("TABU")) {
            BusquedaTabu.setSemilla(c.semilla);
            r.solucion = BusquedaTabu.ejecutarDesdeCero(c.inst.pedidos, flota, TA_MS, MAX_EVALUACIONES,
                    TABU_DURACION, TABU_MAX_SIN_MEJORA);
            r.iteraciones = BusquedaTabu.ultimasIteraciones;
            r.evaluaciones = BusquedaTabu.ultimasEvaluaciones;
            r.tiempoMejorMs = BusquedaTabu.ultimoTiempoMejorMs;
        } else {
            AlgoritmoGenetico.setSemilla(c.semilla);
            r.solucion = AlgoritmoGenetico.ejecutar(c.inst.pedidos, flota, TA_MS, MAX_EVALUACIONES, AG_POBLACION);
            r.iteraciones = AlgoritmoGenetico.ultimasGeneraciones;
            r.evaluaciones = AlgoritmoGenetico.ultimasEvaluaciones;
            r.tiempoMejorMs = AlgoritmoGenetico.ultimoTiempoMejorMs;
            r.tramosCambioTipo = AlgoritmoGenetico.ultimosTramosCambioTipo;
        }
        return r;
    }
}
