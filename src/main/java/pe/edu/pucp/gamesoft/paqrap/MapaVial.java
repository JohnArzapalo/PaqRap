package pe.edu.pucp.gamesoft.paqrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Red vial con bloqueos (Etapa 9).
 *
 * - Retícula de 71 × 51 nodos (x = 0..70, y = 0..50), cada 1 km, calles de
 *   doble sentido y sin diagonales.
 * - Bloqueos del archivo aaaamm.bloqueadas, un registro por línea:
 *     ##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn
 *   Todos los nodos de cada tramo de la polilínea, incluidos los extremos,
 *   quedan bloqueados en [inicio, fin). Un nodo bloqueado no se atraviesa.
 * - Distancias por BFS que evitan los nodos bloqueados ACTIVOS. El conjunto
 *   activo solo cambia en los instantes de inicio y fin de los bloqueos, así
 *   que la retícula se divide en intervalos con estado constante y las
 *   distancias se guardan en caché por (intervalo, nodo de origen).
 *
 * SUPUESTOS:
 * - El nodo de origen puede estar bloqueado (la unidad ya está ahí) y se
 *   puede salir de él.
 * - Si el destino está bloqueado, el BFS le asigna distancia (se llega a un
 *   vecino y se entra), pero no se expande a través de él; la ENTREGA espera
 *   a que se desbloquee (lo resuelve Compartido.evaluarRuta con finBloqueo).
 * - Si un destino fuera inalcanzable (no debería: solo hay polígonos
 *   abiertos), se usa la distancia Manhattan.
 * - Tiempos en horas absolutas desde el día 1 a las 00:00.
 */
final class MapaVial {

    static final int ANCHO = 71, ALTO = 51, NODOS = ANCHO * ALTO;
    private static final Pattern REGISTRO =
            Pattern.compile("(\\d+)d(\\d+)h(\\d+)m-(\\d+)d(\\d+)h(\\d+)m:([\\d,]+)");
    private static final int MAX_CACHE = (int) Parametros.entero("red.cache_bfs", 10000);

    /** Un bloqueo: nodos bloqueados durante [inicioH, finH). */
    static final class Bloqueo {
        final double inicioH, finH;
        final int[] nodos;

        Bloqueo(double inicioH, double finH, int[] nodos) {
            this.inicioH = inicioH;
            this.finH = finH;
            this.nodos = nodos;
        }
    }

    final List<Bloqueo> bloqueos;
    /** Instantes (h) en que cambia el conjunto activo, ordenados. */
    private final double[] cambios;
    /** estados[i] = nodos bloqueados en [cambios[i-1], cambios[i]); estados[0] = antes del primer cambio. */
    private final BitSet[] estados;
    /** Distancias BFS (short: la máxima es < 3621) por (rango de intervalos, origen), LRU. */
    private final Map<Long, short[]> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, short[]> e) {
            return size() > MAX_CACHE;
        }
    };
    /** Unión de estados por rango de intervalos [i1, i2]. */
    private final Map<Long, BitSet> uniones = new HashMap<>();

    MapaVial(List<Bloqueo> bloqueos) {
        this.bloqueos = new ArrayList<>(bloqueos);
        TreeSet<Double> t = new TreeSet<>();
        for (Bloqueo b : bloqueos) { t.add(b.inicioH); t.add(b.finH); }
        cambios = t.stream().mapToDouble(Double::doubleValue).toArray();
        estados = new BitSet[cambios.length + 1];
        for (int i = 0; i <= cambios.length; i++) {
            // estado en un punto representativo del intervalo i
            double punto = i == 0 ? Double.NEGATIVE_INFINITY : cambios[i - 1];
            BitSet s = new BitSet(NODOS);
            for (Bloqueo b : bloqueos) {
                if (b.inicioH <= punto && punto < b.finH) for (int n : b.nodos) s.set(n);
            }
            estados[i] = s;
        }
    }

    static int nodo(int x, int y) {
        return y * ANCHO + x;
    }

    static int x(int nodo) {
        return nodo % ANCHO;
    }

    static int y(int nodo) {
        return nodo / ANCHO;
    }

    /** Índice del intervalo de estado constante que contiene el instante t (h). */
    int intervalo(double tH) {
        int i = Arrays.binarySearch(cambios, tH);
        return i >= 0 ? i + 1 : -i - 1;
    }

    BitSet bloqueadosEn(double tH) {
        return estados[intervalo(tH)];
    }

    boolean bloqueado(int x, int y, double tH) {
        return bloqueadosEn(tH).get(nodo(x, y));
    }

    /** true si el nodo está bloqueado durante TODO el intervalo [t1, t2]. */
    boolean bloqueadoDurante(int x, int y, double t1, double t2) {
        return bloqueado(x, y, t1) && finBloqueo(x, y, t1) >= t2;
    }

    /** Primer instante >= tH en que el nodo queda libre (tH si ya lo está). */
    double finBloqueo(int x, int y, double tH) {
        int n = nodo(x, y);
        double t = tH;
        // Los bloqueos pueden encadenarse o superponerse: avanzar hasta un intervalo libre
        for (int i = intervalo(t); i < estados.length && estados[i].get(n); i++) {
            t = cambios[i];   // fin del intervalo i
        }
        return t;
    }

    /** Distancia en km (aristas) por el camino más corto que evita los bloqueos activos en tH. */
    double distancia(int x1, int y1, int x2, int y2, double tH) {
        int i = intervalo(tH);
        return distancia(x1, y1, x2, y2, i, i);
    }

    /**
     * Distancia de un TRAMO que sale en tH a la velocidad v (km/h), evitando los
     * bloqueos activos en CUALQUIER momento entre la salida y la llegada
     * estimada (9.3: los activos al salir y los que empiezan antes de llegar).
     * La ventana se estima con el propio recorrido y se amplía si el rodeo la
     * alarga (hasta 3 veces). Es conservador: un nodo bloqueado solo una parte
     * de la ventana se evita en todo el tramo.
     */
    double distanciaTramo(int x1, int y1, int x2, int y2, double tH, double v) {
        int[] rango = rangoTramo(x1, y1, x2, y2, tH, v);
        return distancia(x1, y1, x2, y2, rango[0], rango[1]);
    }

    /** Camino del tramo (mismo criterio que distanciaTramo), origen y destino incluidos. */
    List<Integer> caminoTramo(int x1, int y1, int x2, int y2, double tH, double v) {
        int[] rango = rangoTramo(x1, y1, x2, y2, tH, v);
        return camino(x1, y1, x2, y2, rango[0], rango[1]);
    }

    private int[] rangoTramo(int x1, int y1, int x2, int y2, double tH, double v) {
        int i1 = intervalo(tH), i2 = i1;
        double d = distancia(x1, y1, x2, y2, i1, i2);
        for (int k = 0; k < 3; k++) {
            int nuevo = intervalo(tH + d / v);
            if (nuevo <= i2) break;
            i2 = nuevo;
            d = distancia(x1, y1, x2, y2, i1, i2);
        }
        return new int[]{i1, i2};
    }

    private double distancia(int x1, int y1, int x2, int y2, int i1, int i2) {
        if (x1 == x2 && y1 == y2) return 0;
        short[] d = distancias(nodo(x1, y1), i1, i2);
        int v = d[nodo(x2, y2)];
        return v < 0 ? Math.abs(x1 - x2) + Math.abs(y1 - y2) : v;
    }

    /** Camino (lista de nodos, incluye origen y destino) que evita los bloqueos activos en tH. */
    List<Integer> camino(int x1, int y1, int x2, int y2, double tH) {
        int i = intervalo(tH);
        return camino(x1, y1, x2, y2, i, i);
    }

    private List<Integer> camino(int x1, int y1, int x2, int y2, int i1, int i2) {
        short[] d = distancias(nodo(x1, y1), i1, i2);
        int destino = nodo(x2, y2);
        List<Integer> inverso = new ArrayList<>();
        if (d[destino] < 0) {   // inalcanzable (no debería ocurrir): camino Manhattan
            return caminoManhattan(x1, y1, x2, y2);
        }
        int actual = destino;
        inverso.add(actual);
        BitSet bloq = union(i1, i2);
        while (d[actual] > 0) {
            int siguiente = -1;
            for (int vecino : vecinos(actual)) {
                // se retrocede por nodos expandibles (no bloqueados), salvo el origen
                if (d[vecino] == d[actual] - 1 && (d[vecino] == 0 || !bloq.get(vecino))) { siguiente = vecino; break; }
            }
            actual = siguiente;
            inverso.add(actual);
        }
        List<Integer> c = new ArrayList<>();
        for (int i = inverso.size() - 1; i >= 0; i--) c.add(inverso.get(i));
        return c;
    }

    private static List<Integer> caminoManhattan(int x1, int y1, int x2, int y2) {
        List<Integer> c = new ArrayList<>();
        int x = x1, y = y1;
        c.add(nodo(x, y));
        while (x != x2) { x += Integer.signum(x2 - x); c.add(nodo(x, y)); }
        while (y != y2) { y += Integer.signum(y2 - y); c.add(nodo(x, y)); }
        return c;
    }

    private static int[] vecinos(int n) {
        int x = x(n), y = y(n);
        int[] v = new int[4];
        int k = 0;
        if (x > 0) v[k++] = n - 1;
        if (x < ANCHO - 1) v[k++] = n + 1;
        if (y > 0) v[k++] = n - ANCHO;
        if (y < ALTO - 1) v[k++] = n + ANCHO;
        return Arrays.copyOf(v, k);
    }

    /** Nodos bloqueados en algún momento de los intervalos i1..i2. */
    private BitSet union(int i1, int i2) {
        if (i1 == i2) return estados[i1];
        long clave = (long) i1 * 100_000L + i2;
        BitSet u = uniones.get(clave);
        if (u == null) {
            u = new BitSet(NODOS);
            for (int i = i1; i <= i2; i++) u.or(estados[i]);
            uniones.put(clave, u);
        }
        return u;
    }

    /** Distancias BFS desde el origen con los bloqueos de los intervalos i1..i2 (en caché). -1 = inalcanzable. */
    private short[] distancias(int origen, int i1, int i2) {
        long clave = ((long) i1 * 100_000L + i2) * NODOS + origen;
        short[] d = cache.get(clave);
        if (d == null) {
            int[] completo = bfs(origen, union(i1, i2));
            d = new short[NODOS];
            for (int i = 0; i < NODOS; i++) d[i] = (short) completo[i];
            cache.put(clave, d);
        }
        return d;
    }

    /** BFS: los nodos bloqueados reciben distancia (se puede llegar a ellos como
     *  destino) pero no se expanden; el origen siempre se expande. */
    int[] bfs(int origen, BitSet bloqueados) {
        int[] d = new int[NODOS];
        Arrays.fill(d, -1);
        ArrayDeque<Integer> cola = new ArrayDeque<>();
        d[origen] = 0;
        cola.add(origen);
        while (!cola.isEmpty()) {
            int n = cola.poll();
            if (n != origen && bloqueados.get(n)) continue;   // no se atraviesa
            int x = x(n), y = y(n);
            if (x > 0 && d[n - 1] < 0) { d[n - 1] = d[n] + 1; cola.add(n - 1); }
            if (x < ANCHO - 1 && d[n + 1] < 0) { d[n + 1] = d[n] + 1; cola.add(n + 1); }
            if (y > 0 && d[n - ANCHO] < 0) { d[n - ANCHO] = d[n] + 1; cola.add(n - ANCHO); }
            if (y < ALTO - 1 && d[n + ANCHO] < 0) { d[n + ANCHO] = d[n] + 1; cola.add(n + ANCHO); }
        }
        return d;
    }

    /** Nodos de una polilínea: todos los nodos de cada tramo, extremos incluidos. */
    static int[] nodosPolilinea(int[] coords) {
        List<Integer> nodos = new ArrayList<>();
        for (int i = 0; i + 3 < coords.length; i += 2) {
            int x1 = coords[i], y1 = coords[i + 1], x2 = coords[i + 2], y2 = coords[i + 3];
            if (x1 != x2 && y1 != y2)
                throw new IllegalArgumentException("Tramo diagonal en bloqueo: (" + x1 + "," + y1 + ")-(" + x2 + "," + y2 + ")");
            for (int n : caminoManhattan(x1, y1, x2, y2)) if (!nodos.contains(n)) nodos.add(n);
        }
        if (coords.length == 2) nodos.add(nodo(coords[0], coords[1]));
        return nodos.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Lee el archivo aaaamm.bloqueadas; omite (y avisa) las líneas inválidas. */
    static MapaVial leer(String archivo) throws IOException {
        List<Bloqueo> lista = new ArrayList<>();
        int nro = 0;
        try (BufferedReader br = Files.newBufferedReader(Paths.get(archivo), StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = br.readLine()) != null) {
                nro++;
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                Matcher m = REGISTRO.matcher(linea);
                if (!m.matches()) {
                    System.err.println("  Bloqueo en línea " + nro + " con formato inválido, se omite: " + linea);
                    continue;
                }
                double ini = LectorPedidos.hora(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
                double fin = LectorPedidos.hora(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
                        Integer.parseInt(m.group(6)));
                int[] coords = Arrays.stream(m.group(7).split(",")).mapToInt(Integer::parseInt).toArray();
                lista.add(new Bloqueo(ini, fin, nodosPolilinea(coords)));
            }
        }
        return new MapaVial(lista);
    }
}
