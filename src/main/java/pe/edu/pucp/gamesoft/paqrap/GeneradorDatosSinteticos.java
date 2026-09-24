package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Genera los datos SINTÉTICOS de un mes (Etapa 8.2), mientras no estén los
 * archivos oficiales. Parámetros documentados en docs/datos_sinteticos.md.
 *
 *   java ... GeneradorDatosSinteticos [carpetaDatos]
 *
 * - datos/ventas202609_SINTETICO_MES.txt: 30 días, pedidos repartidos en las
 *   24 h con más densidad en horario diurno, en toda la retícula.
 * - datos/202609_SINTETICO.bloqueadas: polilíneas ABIERTAS de 2 a 6 tramos
 *   horizontales/verticales, de 2 a 12 h, que nunca bloquean un almacén ni
 *   dejan un nodo libre inalcanzable (se verifica con BFS contra todos los
 *   bloqueos que se superponen en el tiempo).
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-13 (datos sintéticos, siempre marcados).
 */
final class GeneradorDatosSinteticos {

    static final long SEMILLA = 20260901L;
    static final int DIAS = 30;
    static final int PEDIDOS_POR_DIA = 120;
    /** Peso relativo de llegada de pedidos por hora del día (0..23). */
    static final double[] PESO_HORA = {
        0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.5, 0.8,   // 00-07
        1.3, 1.3, 1.3, 1.3, 1.1, 1.1, 1.3, 1.3,         // 08-15
        1.3, 1.3, 1.0, 1.0, 0.7, 0.7, 0.4, 0.4          // 16-23
    };
    /** Zonas de mayor densidad (x, y) y su desviación (km). */
    static final int[][] ZONAS = {{15, 40}, {50, 35}, {35, 10}, {60, 10}};
    static final double DESVIACION_ZONA = 5.0;
    static final double PROB_ZONA = 0.35;
    /** Cantidad: rangos [mín, máx] y probabilidad. */
    static final int[][] RANGO_CANTIDAD = {{1, 4}, {5, 10}, {11, 24}, {25, 50}};
    static final double[] PROB_CANTIDAD = {0.45, 0.30, 0.20, 0.05};
    /** Plazos hl (h) y probabilidad. */
    static final int[] PLAZOS = {36, 18, 12, 8, 4};
    static final double[] PROB_PLAZO = {0.55, 0.12, 0.12, 0.13, 0.08};
    /** Bloqueos por día (mín, máx), tramos por polilínea, largo de tramo (km), duración (min). */
    static final int BLOQUEOS_DIA_MIN = 5, BLOQUEOS_DIA_MAX = 11;
    static final int TRAMOS_MIN = 2, TRAMOS_MAX = 6, LARGO_TRAMO_MIN = 1, LARGO_TRAMO_MAX = 6;
    static final int DURACION_MIN_MIN = 120, DURACION_MAX_MIN = 720;

    private GeneradorDatosSinteticos() {
    }

    public static void main(String[] args) throws IOException {
        Path carpeta = Paths.get(args.length > 0 ? args[0] : "datos");
        Path ventas = carpeta.resolve("ventas202609_SINTETICO_MES.txt");
        Path bloqueos = carpeta.resolve("202609_SINTETICO.bloqueadas");
        int n = generarPedidos(ventas, new Random(SEMILLA));
        int b = generarBloqueos(bloqueos, new Random(SEMILLA + 1));
        System.out.println("*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***");
        System.out.println(n + " pedidos -> " + ventas.toAbsolutePath());
        System.out.println(b + " bloqueos -> " + bloqueos.toAbsolutePath());
    }

    private static int elegir(double[] prob, Random r) {
        double u = r.nextDouble(), acum = 0;
        for (int i = 0; i < prob.length; i++) {
            acum += prob[i];
            if (u < acum) return i;
        }
        return prob.length - 1;
    }

    private static boolean esAlmacen(int x, int y) {
        return (x == 27 && y == 14) || (x == 12 && y == 38) || (x == 57 && y == 27);
    }

    static int generarPedidos(Path destino, Random r) throws IOException {
        double totalPeso = 0;
        for (double p : PESO_HORA) totalPeso += p;
        double[] probHora = new double[24];
        for (int h = 0; h < 24; h++) probHora[h] = PESO_HORA[h] / totalPeso;

        List<LectorPedidos.Registro> regs = new ArrayList<>();
        for (int dia = 1; dia <= DIAS; dia++) {
            for (int k = 0; k < PEDIDOS_POR_DIA; k++) {
                LectorPedidos.Registro reg = new LectorPedidos.Registro();
                int hora = elegir(probHora, r);
                reg.llegadaHoras = (dia - 1) * 24.0 + hora + r.nextInt(60) / 60.0;
                int x, y;
                do {
                    if (r.nextDouble() < PROB_ZONA) {
                        int[] z = ZONAS[r.nextInt(ZONAS.length)];
                        x = (int) Math.round(z[0] + r.nextGaussian() * DESVIACION_ZONA);
                        y = (int) Math.round(z[1] + r.nextGaussian() * DESVIACION_ZONA);
                    } else {
                        x = r.nextInt(MapaVial.ANCHO);
                        y = r.nextInt(MapaVial.ALTO);
                    }
                } while (x < 0 || x >= MapaVial.ANCHO || y < 0 || y >= MapaVial.ALTO || esAlmacen(x, y));
                reg.x = x;
                reg.y = y;
                reg.cliente = "c" + (1000 + r.nextInt(9000));
                int[] rango = RANGO_CANTIDAD[elegir(PROB_CANTIDAD, r)];
                reg.cantidad = rango[0] + r.nextInt(rango[1] - rango[0] + 1);
                reg.hl = PLAZOS[elegir(PROB_PLAZO, r)];
                regs.add(reg);
            }
        }
        regs.sort(Comparator.comparingDouble(g -> g.llegadaHoras));
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(destino, StandardCharsets.UTF_8))) {
            for (LectorPedidos.Registro g : regs) out.println(g.aLinea());
        }
        return regs.size();
    }

    static int generarBloqueos(Path destino, Random r) throws IOException {
        List<MapaVial.Bloqueo> aceptados = new ArrayList<>();
        List<String> lineas = new ArrayList<>();
        int rechazados = 0;
        for (int dia = 1; dia <= DIAS; dia++) {
            int cuantos = BLOQUEOS_DIA_MIN + r.nextInt(BLOQUEOS_DIA_MAX - BLOQUEOS_DIA_MIN + 1);
            for (int k = 0; k < cuantos; k++) {
                for (int intento = 0; intento < 50; intento++) {
                    int inicioMin = (dia - 1) * 1440 + r.nextInt(1440);
                    int finMin = inicioMin + DURACION_MIN_MIN + r.nextInt(DURACION_MAX_MIN - DURACION_MIN_MIN + 1);
                    int[] coords = polilinea(r);
                    if (coords == null) continue;
                    int[] nodos = MapaVial.nodosPolilinea(coords);
                    MapaVial.Bloqueo b = new MapaVial.Bloqueo(inicioMin / 60.0, finMin / 60.0, nodos);
                    if (!mantieneAlcanzabilidad(b, aceptados)) { rechazados++; continue; }
                    aceptados.add(b);
                    lineas.add(formatear(inicioMin) + "-" + formatear(finMin) + ":" + unir(coords));
                    break;
                }
            }
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(destino, StandardCharsets.UTF_8))) {
            for (String l : lineas) out.println(l);
        }
        System.out.println("  Bloqueos rechazados por alcanzabilidad o por tocar un almacén: " + rechazados);
        return lineas.size();
    }

    /** Polilínea abierta de 2 a 6 tramos que alternan horizontal/vertical, dentro
     *  de la retícula y sin pasar por un almacén; null si no se pudo armar. */
    private static int[] polilinea(Random r) {
        int tramos = TRAMOS_MIN + r.nextInt(TRAMOS_MAX - TRAMOS_MIN + 1);
        int x = r.nextInt(MapaVial.ANCHO), y = r.nextInt(MapaVial.ALTO);
        List<Integer> c = new ArrayList<>(List.of(x, y));
        boolean horizontal = r.nextBoolean();
        for (int t = 0; t < tramos; t++) {
            int largo = LARGO_TRAMO_MIN + r.nextInt(LARGO_TRAMO_MAX - LARGO_TRAMO_MIN + 1);
            int signo = r.nextBoolean() ? 1 : -1;
            int nx = horizontal ? x + signo * largo : x, ny = horizontal ? y : y + signo * largo;
            if (nx < 0 || nx >= MapaVial.ANCHO || ny < 0 || ny >= MapaVial.ALTO) {
                signo = -signo;
                nx = horizontal ? x + signo * largo : x;
                ny = horizontal ? y : y + signo * largo;
                if (nx < 0 || nx >= MapaVial.ANCHO || ny < 0 || ny >= MapaVial.ALTO) return null;
            }
            x = nx;
            y = ny;
            c.add(x);
            c.add(y);
            horizontal = !horizontal;
        }
        int[] coords = c.stream().mapToInt(Integer::intValue).toArray();
        for (int n : MapaVial.nodosPolilinea(coords)) {
            if (esAlmacen(MapaVial.x(n), MapaVial.y(n))) return null;
        }
        return coords;
    }

    /** true si, con el nuevo bloqueo y todos los que se superponen con él en el
     *  tiempo activos a la vez (caso más restrictivo), todo nodo no bloqueado
     *  sigue alcanzable desde el almacén central. */
    static boolean mantieneAlcanzabilidad(MapaVial.Bloqueo nuevo, List<MapaVial.Bloqueo> existentes) {
        BitSet bloq = new BitSet(MapaVial.NODOS);
        for (int n : nuevo.nodos) bloq.set(n);
        for (MapaVial.Bloqueo b : existentes) {
            if (b.inicioH < nuevo.finH && nuevo.inicioH < b.finH) for (int n : b.nodos) bloq.set(n);
        }
        int[] d = new MapaVial(List.of()).bfs(MapaVial.nodo(27, 14), bloq);
        for (int n = 0; n < MapaVial.NODOS; n++) {
            if (!bloq.get(n) && d[n] < 0) return false;
        }
        return true;
    }

    private static String formatear(int minutos) {
        return String.format(Locale.US, "%02dd%02dh%02dm", minutos / 1440 + 1, (minutos % 1440) / 60, minutos % 60);
    }

    private static String unir(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) sb.append(i > 0 ? "," : "").append(v[i]);
        return sb.toString();
    }
}
