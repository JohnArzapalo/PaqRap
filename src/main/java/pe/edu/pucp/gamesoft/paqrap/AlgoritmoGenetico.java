package pe.edu.pucp.gamesoft.paqrap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** 4.3.1 Estructuras específicas del Algoritmo Genético. */
class Cromosoma {
    List<Pedido> permutacion;
    Map<String, TipoUnidad> tipoAsignado;   // por id de pedido
    double aptH;
    double aptS;

    Cromosoma(List<Pedido> permutacion, Map<String, TipoUnidad> tipoAsignado) {
        this.permutacion = permutacion;
        this.tipoAsignado = tipoAsignado;
    }

    Cromosoma copiar() {
        Cromosoma c = new Cromosoma(new ArrayList<>(permutacion), new HashMap<>(tipoAsignado));
        c.aptH = aptH;   // la copia conserva su aptitud; sin esto el élite
        c.aptS = aptS;   // quedaba con (0, 0) y parecía "perfecto"
        return c;
    }
}

class Poblacion {
    List<Cromosoma> individuos = new ArrayList<>();
}

/** 4.3.2 Pseudocódigo, ya traducido a Java. Primera iteración: implementa
 *  selección por torneo, cruce OX, mutación simple y Split; la búsqueda
 *  local memética del apartado 4.3 se incorpora en la segunda iteración,
 *  reutilizando el operador Reubicación de BusquedaTabu. */
class AlgoritmoGenetico {

    private static Random AZAR = new Random(11);   // semilla por defecto (reproducible)

    /** Fija la semilla antes de ejecutar. Cada réplica del experimento usa una distinta. */
    static void setSemilla(long semilla) {
        AZAR = new Random(semilla);
    }

    /** Tipo de vehículo aleatorio de la flota en el que quepa el pedido. */
    private static TipoUnidad tipoQueQuepa(Pedido p) {
        List<TipoUnidad> validos = new ArrayList<>();
        for (TipoUnidad t : tiposFlota) if (p.cantidad <= t.capacidadMaxima) validos.add(t);
        if (validos.isEmpty()) return tiposFlota[0];
        return validos.get(AZAR.nextInt(validos.size()));
    }

    /** Tipos de vehículo que realmente existen en la flota de esta ejecución. */
    private static TipoUnidad[] tiposFlota = TipoUnidad.values();

    /** Mejor solución de la generación 0 (antes de evolucionar), para el visualizador. */
    static Solucion solucionGeneracion0;

    /** Costo S del mejor individuo en cada generación (curva de convergencia). */
    static final List<Double> historialConvergencia = new ArrayList<>();

    private static TipoUnidad[] tiposDisponibles(List<UnidadTransporte> flota) {
        List<TipoUnidad> t = new ArrayList<>();
        for (UnidadTransporte u : flota) if (!t.contains(u.tipo)) t.add(u.tipo);
        return t.toArray(new TipoUnidad[0]);
    }

    static Solucion ejecutar(List<Pedido> pedidosPendientes, List<UnidadTransporte> flotaDisponible,
                              long presupuestoMs, int tamPoblacion) {
        tiposFlota = tiposDisponibles(flotaDisponible);
        Poblacion poblacion = inicializarPoblacion(pedidosPendientes, tamPoblacion);
        for (Cromosoma c : poblacion.individuos) evaluar(c, flotaDisponible);

        Cromosoma mejorGlobal = mejorDe(poblacion).copiar();
        solucionGeneracion0 = split(mejorGlobal, flotaDisponible);   // para comparar inicio vs. final
        historialConvergencia.clear();
        historialConvergencia.add(mejorGlobal.aptS);
        long inicioEjecucion = System.currentTimeMillis();

        while (!Compartido.debeDetenerse(inicioEjecucion, presupuestoMs)) {
            poblacion.individuos.sort(AlgoritmoGenetico::comparar);

            List<Cromosoma> nuevaGeneracion = new ArrayList<>();
            nuevaGeneracion.add(poblacion.individuos.get(0).copiar());   // elitismo simple

            while (nuevaGeneracion.size() < tamPoblacion) {
                Cromosoma padreA = seleccionTorneo(poblacion);
                Cromosoma padreB = seleccionTorneo(poblacion);

                Cromosoma hijo = (AZAR.nextDouble() < 0.85)
                        ? cruceOX(padreA, padreB)
                        : padreA.copiar();

                if (AZAR.nextDouble() < 0.15) mutar(hijo);

                evaluar(hijo, flotaDisponible);
                nuevaGeneracion.add(hijo);
            }

            poblacion.individuos = nuevaGeneracion;
            Cromosoma candidato = mejorDe(poblacion);
            if (comparar(candidato, mejorGlobal) < 0) mejorGlobal = candidato.copiar();
            historialConvergencia.add(mejorGlobal.aptS);
        }

        return split(mejorGlobal, flotaDisponible);
    }

    private static Poblacion inicializarPoblacion(List<Pedido> pedidos, int tam) {
        Poblacion pob = new Poblacion();
        for (int k = 0; k < tam; k++) {
            List<Pedido> perm = new ArrayList<>(pedidos);
            Collections.shuffle(perm, AZAR);
            Map<String, TipoUnidad> tipos = new HashMap<>();
            for (Pedido p : perm) tipos.put(p.id, tipoQueQuepa(p));   // solo tipos de la flota donde quepa
            pob.individuos.add(new Cromosoma(perm, tipos));
        }
        return pob;
    }

    private static Cromosoma seleccionTorneo(Poblacion pob) {
        Cromosoma a = pob.individuos.get(AZAR.nextInt(pob.individuos.size()));
        Cromosoma b = pob.individuos.get(AZAR.nextInt(pob.individuos.size()));
        return comparar(a, b) <= 0 ? a : b;
    }

    /** Cruce ordenado (OX): conserva el tramo de un padre tal cual y
     *  completa el resto con el orden del otro padre, sin repetir pedidos. */
    private static Cromosoma cruceOX(Cromosoma padreA, Cromosoma padreB) {
        int n = padreA.permutacion.size();
        int c1 = AZAR.nextInt(n);
        int c2 = AZAR.nextInt(n);
        if (c1 > c2) { int t = c1; c1 = c2; c2 = t; }

        Pedido[] hijoArr = new Pedido[n];
        Set<String> usados = new HashSet<>();
        for (int k = c1; k < c2; k++) {
            hijoArr[k] = padreA.permutacion.get(k);
            usados.add(hijoArr[k].id);
        }
        int pos = c2 % n;
        for (int k = 0; k < n; k++) {
            Pedido cand = padreB.permutacion.get((c2 + k) % n);
            if (!usados.contains(cand.id)) {
                hijoArr[pos] = cand;
                usados.add(cand.id);
                pos = (pos + 1) % n;
            }
        }
        List<Pedido> hijoPerm = new ArrayList<>(Arrays.asList(hijoArr));
        Map<String, TipoUnidad> hijoTipo = new HashMap<>();
        for (Pedido p : hijoPerm) {
            hijoTipo.put(p.id, AZAR.nextBoolean() ? padreA.tipoAsignado.get(p.id) : padreB.tipoAsignado.get(p.id));
        }
        return new Cromosoma(hijoPerm, hijoTipo);
    }

    /** Muta: permuta dos pedidos de posición y, con probabilidad 0.5,
     *  cambia el tipo de vehículo asignado a un pedido (entre los tipos
     *  de la flota disponible). */
    private static void mutar(Cromosoma c) {
        int n = c.permutacion.size();
        int i = AZAR.nextInt(n);
        int j = AZAR.nextInt(n);
        Collections.swap(c.permutacion, i, j);
        if (AZAR.nextDouble() < 0.5) {
            Pedido p = c.permutacion.get(AZAR.nextInt(n));
            c.tipoAsignado.put(p.id, tipoQueQuepa(p));
        }
    }

    private static void evaluar(Cromosoma c, List<UnidadTransporte> flota) {
        Solucion s = split(c, flota);
        c.aptH = s.H;
        c.aptS = s.S;
    }

    private static int comparar(Cromosoma a, Cromosoma b) {
        if (a.aptH != b.aptH) return Double.compare(a.aptH, b.aptH);
        return Double.compare(a.aptS, b.aptS);
    }

    private static Cromosoma mejorDe(Poblacion pob) {
        Cromosoma mejor = pob.individuos.get(0);
        for (Cromosoma c : pob.individuos) {
            if (comparar(c, mejor) < 0) mejor = c;
        }
        return mejor;
    }

    /** Procedimiento Split: programación dinámica que decide dónde cortar
     *  la permutación para formar rutas de un solo vehículo cada una,
     *  respetando capacidad y número de vehículos, y priorizando el
     *  cumplimiento de plazos: cada tramo cuesta (km × costo/km) más una
     *  penalidad por pedido tarde, así el Split prefiere siempre el corte
     *  que cumple más plazos y, a igualdad, el más barato. Recorre cada
     *  tramo hacia adelante acumulando carga, km y hora: O(m·n²).
     *  Decodifica el resultado en una Solución evaluable. */
    private static Solucion split(Cromosoma c, List<UnidadTransporte> flotaDisponible) {
        List<Pedido> perm = c.permutacion;
        int n = perm.size();
        int m = flotaDisponible.size();   // no puede haber más rutas que vehículos

        // costoMinimo[k][j]: menor costo para atender los primeros j pedidos con exactamente k rutas
        double[][] costoMinimo = new double[m + 1][n + 1];
        int[][] predecesor = new int[m + 1][n + 1];
        for (double[] fila : costoMinimo) Arrays.fill(fila, Double.MAX_VALUE);
        costoMinimo[0][0] = 0;

        Almacen al = Compartido.ALMACEN_CENTRAL;
        for (int k = 1; k <= m; k++) {
            for (int i = 0; i < n; i++) {
                if (costoMinimo[k - 1][i] == Double.MAX_VALUE) continue;
                // El tramo empieza en el pedido i; su tipo de vehículo es el asignado a ese pedido.
                TipoUnidad tipo = c.tipoAsignado.get(perm.get(i).id);
                int carga = 0, tarde = 0;
                double km = 0, reloj = 0;
                int px = al.x, py = al.y;
                // Se extiende el tramo pedido a pedido (i..j-1), acumulando carga, km, hora y tardanzas.
                for (int j = i + 1; j <= n; j++) {
                    Pedido p = perm.get(j - 1);
                    carga += p.cantidad;
                    if (carga > tipo.capacidadMaxima) break;   // tramos más largos tampoco caben
                    double d = Compartido.distancia(px, py, p.x, p.y);
                    km += d;
                    reloj += d / tipo.velocidadPromedio;           // hora de llegada al cliente
                    if (reloj > p.horaLimite()) tarde++;           // fuera de plazo
                    reloj += Compartido.HORAS_ENTREGA;             // 1 h de acondicionamiento
                    px = p.x;
                    py = p.y;
                    double kmConRetorno = km + Compartido.distancia(px, py, al.x, al.y);
                    double costoTramo = kmConRetorno * tipo.costoPorKilometro
                                      + tarde * Compartido.PENALIDAD_TARDANZA;
                    if (costoMinimo[k - 1][i] + costoTramo < costoMinimo[k][j]) {
                        costoMinimo[k][j] = costoMinimo[k - 1][i] + costoTramo;
                        predecesor[k][j] = i;
                    }
                }
            }
        }

        int mejorK = -1;
        for (int k = 1; k <= m; k++) {
            if (costoMinimo[k][n] < Double.MAX_VALUE && (mejorK == -1 || costoMinimo[k][n] < costoMinimo[mejorK][n])) {
                mejorK = k;
            }
        }

        Solucion s = new Solucion();
        if (mejorK == -1) {
            s.pedidosSinAsignar = new ArrayList<>(perm);
            Compartido.evaluarSolucion(s);
            return s;
        }

        List<List<Pedido>> tramos = new ArrayList<>();
        int j = n;
        for (int k = mejorK; k > 0; k--) {
            int i = predecesor[k][j];
            tramos.add(0, new ArrayList<>(perm.subList(i, j)));
            j = i;
        }

        List<UnidadTransporte> libres = new ArrayList<>(flotaDisponible);
        List<Pedido> sinAsignar = new ArrayList<>();
        for (List<Pedido> tramo : tramos) {
            TipoUnidad tipoTramo = c.tipoAsignado.get(tramo.get(0).id);
            UnidadTransporte u = null;
            for (UnidadTransporte cand : libres) {
                if (cand.tipo == tipoTramo) { u = cand; break; }
            }
            if (u == null) {
                int cant = 0;
                for (Pedido p : tramo) cant += p.cantidad;
                for (UnidadTransporte cand : libres) {
                    if (cant <= cand.tipo.capacidadMaxima) { u = cand; break; }
                }
            }
            if (u != null) {
                libres.remove(u);
                RutaAlg r = new RutaAlg();
                r.unidad = u;
                for (Pedido p : tramo) r.paradas.add(ParadaAlg.entrega(p));
                Compartido.recalcularDistanciaYCosto(r);
                s.rutas.add(r);
            } else {
                sinAsignar.addAll(tramo);
            }
        }
        s.pedidosSinAsignar = sinAsignar;
        Compartido.evaluarSolucion(s);
        return s;
    }
}