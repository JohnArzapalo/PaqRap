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

/** 4.3.2 Pseudocódigo, ya traducido a Java: selección por torneo, cruce OX,
 *  mutación simple, Split y (2da iteración) búsqueda local memética.
 *
 *  Búsqueda local memética: se aplica al hijo evaluado con probabilidad
 *  PROB_BUSQUEDA_LOCAL y SIEMPRE al mejor de cada generación. Decodifica
 *  el cromosoma con Split, aplica hasta MOVIMIENTOS_BUSQUEDA_LOCAL
 *  movimientos de Reubicación o Intercambio (los mismos operadores de
 *  Búsqueda Tabú, en OperadoresVecindario) aceptando solo mejoras, y
 *  (si hay entregas sin asignar e INSERCION_EN_BL) Inserción en 1 de cada
 *  2 movimientos: sin ella la búsqueda local solo pulía el costo y empeoraba
 *  H en instancias con más carga que la flota (medición de la Etapa 6.1). Luego
 *  devuelve la mejora al cromosoma: concatena las rutas en una permutación
 *  y asigna a cada pedido el tipo de su unidad, para que se herede.
 *  Se desactiva con ag.busqueda_local=no.
 *
 *  Parada: por presupuesto de tiempo Ta (criterio principal) o, si
 *  maxEvaluaciones > 0, por número de evaluaciones (modo reproducible).
 *  Una "evaluación" es una decodificación Split de un cromosoma o una
 *  evaluación de un vecino de la búsqueda local. El criterio se revisa al
 *  terminar cada generación y en cada movimiento de la búsqueda local. */
class AlgoritmoGenetico {

    private static Random AZAR = new Random(11);   // semilla por defecto (reproducible)

    /** Tamaño de población por defecto (ag.poblacion). */
    static int POBLACION = (int) Parametros.entero("ag.poblacion", 30);
    /** Búsqueda local memética activada (ag.busqueda_local = si | no). */
    static boolean BUSQUEDA_LOCAL = !"no".equalsIgnoreCase(Parametros.texto("ag.busqueda_local", "si"));
    static double PROB_BUSQUEDA_LOCAL = Parametros.decimal("ag.prob_busqueda_local", 0.2);
    static int MOVIMIENTOS_BUSQUEDA_LOCAL = (int) Parametros.entero("ag.movimientos_busqueda_local", 30);
    /** Si hay entregas sin asignar, uno de cada dos movimientos de la búsqueda
     *  local es una Inserción (ag.insercion_en_busqueda_local = si | no). */
    static boolean INSERCION_EN_BL = !"no".equalsIgnoreCase(Parametros.texto("ag.insercion_en_busqueda_local", "si"));

    // Presupuesto de la ejecución en curso (lo consulta también la búsqueda local)
    private static long inicioEjecucion, presupuestoMs, maxEvaluaciones;

    /** Fija la semilla antes de ejecutar. Cada réplica del experimento usa una distinta. */
    static void setSemilla(long semilla) {
        AZAR = new Random(semilla);
    }

    /** Tipos de vehículo que realmente existen en la flota de esta ejecución. */
    private static TipoUnidad[] tiposFlota = TipoUnidad.values();

    /** Mejor solución de la generación 0 (antes de evolucionar), para el visualizador. */
    static Solucion solucionGeneracion0;

    /** {H, S} del mejor individuo en cada generación (curva de convergencia, R11). */
    static final List<double[]> historialConvergencia = new ArrayList<>();

    // ===== Contadores de la última ejecución (R6) =====
    static long ultimasGeneraciones;
    static long ultimasEvaluaciones;
    /** Milisegundos desde el inicio hasta que se encontró la mejor solución. */
    static long ultimoTiempoMejorMs;
    /** Tramos de la solución devuelta que no pudieron usar el tipo pedido por
     *  el cromosoma (no quedaba una unidad libre de ese tipo) (R5). */
    static int ultimosTramosCambioTipo;

    /** Tramos con cambio de tipo en la última llamada a split(). */
    private static int tramosCambioTipoUltimoSplit;
    private static long evaluaciones;
    /** Veces que se aplicó la búsqueda local y veces que mejoró el cromosoma. */
    static long ultimasAplicacionesBL, ultimasMejorasBL;
    private static long aplicacionesBL, mejorasBL;

    /** Tipo de vehículo aleatorio de la flota en el que quepa el pedido. Si
     *  ningún tipo alcanza, devuelve el de MAYOR capacidad de la flota. */
    private static TipoUnidad tipoQueQuepa(Pedido p) {
        List<TipoUnidad> validos = new ArrayList<>();
        for (TipoUnidad t : tiposFlota) if (p.cantidad <= t.capacidadMaxima) validos.add(t);
        if (validos.isEmpty()) {
            TipoUnidad mayor = tiposFlota[0];
            for (TipoUnidad t : tiposFlota) if (t.capacidadMaxima > mayor.capacidadMaxima) mayor = t;
            return mayor;
        }
        return validos.get(AZAR.nextInt(validos.size()));
    }

    private static TipoUnidad[] tiposDisponibles(List<UnidadTransporte> flota) {
        List<TipoUnidad> t = new ArrayList<>();
        for (UnidadTransporte u : flota) if (!t.contains(u.tipo)) t.add(u.tipo);
        return t.toArray(new TipoUnidad[0]);
    }

    static Solucion ejecutar(List<Pedido> pedidosPendientes, List<UnidadTransporte> flotaDisponible,
                              long presupuestoMs, long maxEvaluaciones, int tamPoblacion) {
        // El cronómetro empieza ANTES de crear y evaluar la población inicial (R10)
        AlgoritmoGenetico.inicioEjecucion = System.currentTimeMillis();
        AlgoritmoGenetico.presupuestoMs = presupuestoMs;
        AlgoritmoGenetico.maxEvaluaciones = maxEvaluaciones;
        evaluaciones = 0;
        aplicacionesBL = 0;
        mejorasBL = 0;
        tiposFlota = tiposDisponibles(flotaDisponible);
        Poblacion poblacion = inicializarPoblacion(pedidosPendientes, tamPoblacion);
        for (Cromosoma c : poblacion.individuos) evaluar(c, flotaDisponible);

        Cromosoma mejorGlobal = mejorDe(poblacion.individuos).copiar();
        long tiempoMejor = System.currentTimeMillis() - inicioEjecucion;
        solucionGeneracion0 = split(mejorGlobal, flotaDisponible);   // para comparar inicio vs. final
        historialConvergencia.clear();
        historialConvergencia.add(new double[]{mejorGlobal.aptH, mejorGlobal.aptS});
        long generacion = 0;

        while (!debeDetenerse()) {
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
                if (BUSQUEDA_LOCAL && AZAR.nextDouble() < PROB_BUSQUEDA_LOCAL)
                    hijo = busquedaLocal(hijo, flotaDisponible);
                nuevaGeneracion.add(hijo);
            }

            if (BUSQUEDA_LOCAL) {   // siempre al mejor de la generación
                int iMejor = nuevaGeneracion.indexOf(mejorDe(nuevaGeneracion));
                nuevaGeneracion.set(iMejor, busquedaLocal(nuevaGeneracion.get(iMejor), flotaDisponible));
            }

            poblacion.individuos = nuevaGeneracion;
            generacion++;
            Cromosoma candidato = mejorDe(poblacion.individuos);
            if (comparar(candidato, mejorGlobal) < 0) {
                mejorGlobal = candidato.copiar();
                tiempoMejor = System.currentTimeMillis() - inicioEjecucion;
            }
            historialConvergencia.add(new double[]{mejorGlobal.aptH, mejorGlobal.aptS});
        }

        Solucion resultado = split(mejorGlobal, flotaDisponible);
        ultimasGeneraciones = generacion;
        ultimasEvaluaciones = evaluaciones;
        ultimoTiempoMejorMs = tiempoMejor;
        ultimosTramosCambioTipo = tramosCambioTipoUltimoSplit;
        ultimasAplicacionesBL = aplicacionesBL;
        ultimasMejorasBL = mejorasBL;
        return resultado;
    }

    /** Modo por evaluaciones si maxEvaluaciones > 0 (se ignora el tiempo); si no, por Ta. */
    private static boolean debeDetenerse() {
        if (maxEvaluaciones > 0) return evaluaciones >= maxEvaluaciones;
        return Compartido.debeDetenerse(inicioEjecucion, presupuestoMs);
    }

    /** Búsqueda local memética (primera mejora) sobre la decodificación del
     *  cromosoma. Devuelve el cromosoma mejorado (ya evaluado) o el original
     *  si no hubo mejora; nunca uno peor. Respeta el presupuesto de la corrida. */
    private static Cromosoma busquedaLocal(Cromosoma c, List<UnidadTransporte> flota) {
        if (debeDetenerse()) return c;
        aplicacionesBL++;
        Solucion s = OperadoresVecindario.conUnidadesLibres(split(c, flota), flota);
        evaluaciones++;
        boolean mejoro = false;
        for (int k = 0; k < MOVIMIENTOS_BUSQUEDA_LOCAL && !debeDetenerse(); k++) {
            Movimiento m;
            if (INSERCION_EN_BL && !s.pedidosSinAsignar.isEmpty() && k % 2 == 0) {
                m = OperadoresVecindario.insercion(s, AZAR);
            } else {
                m = AZAR.nextBoolean()
                        ? OperadoresVecindario.reubicacion(s, AZAR)
                        : OperadoresVecindario.intercambio(s, AZAR);
            }
            if (m == null) continue;
            evaluaciones++;
            if (Compartido.mejorQue(m.solucion, s)) {
                s = m.solucion;
                mejoro = true;
            }
        }
        if (!mejoro) return c;

        Cromosoma nuevo = aCromosoma(s, c);
        evaluar(nuevo, flota);
        // La aptitud se mide con Split sobre la nueva permutación: si el
        // decodificador no reproduce la mejora (p. ej., por falta de unidades
        // de un tipo), se conserva el cromosoma original.
        if (comparar(nuevo, c) < 0) {
            mejorasBL++;
            return nuevo;
        }
        return c;
    }

    /** Solución -> cromosoma: las rutas no vacías concatenadas (en su orden)
     *  forman la permutación, seguidas de las entregas sin asignar; cada pedido
     *  en ruta toma el tipo de su unidad. Split puede reproducir los mismos cortes. */
    private static Cromosoma aCromosoma(Solucion s, Cromosoma base) {
        List<Pedido> perm = new ArrayList<>();
        Map<String, TipoUnidad> tipos = new HashMap<>(base.tipoAsignado);
        for (RutaAlg r : s.rutas) {
            for (ParadaAlg p : r.paradas) {
                perm.add(p.pedido);
                tipos.put(p.pedido.id, r.unidad.tipo);
            }
        }
        perm.addAll(s.pedidosSinAsignar);
        return new Cromosoma(perm, tipos);
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
        evaluaciones++;
        c.aptH = s.H;
        c.aptS = s.S;
    }

    private static int comparar(Cromosoma a, Cromosoma b) {
        if (a.aptH != b.aptH) return Double.compare(a.aptH, b.aptH);
        return Double.compare(a.aptS, b.aptS);
    }

    private static Cromosoma mejorDe(List<Cromosoma> individuos) {
        Cromosoma mejor = individuos.get(0);
        for (Cromosoma c : individuos) {
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
     *  Decodifica el resultado en una Solución evaluable.
     *
     *  LIMITACIÓN CONOCIDA (R5): la programación dinámica limita el TOTAL de
     *  rutas al número de vehículos, pero NO controla cuántas unidades hay de
     *  cada tipo (p. ej., solo 10 autos). Por eso, al decodificar, un tramo
     *  puede quedarse sin una unidad libre de su tipo; entonces se usa, entre
     *  las unidades libres donde cabe, la que da menos entregas tarde y menor
     *  costo REALES, y se cuenta como "tramo con cambio de tipo". En ese caso
     *  el costo que optimizó la programación dinámica no coincide con el real.
     *  La aptitud del cromosoma y la solución final SIEMPRE se calculan con
     *  Compartido.evaluarSolucion sobre la unidad real de cada ruta. */
    static Solucion split(Cromosoma c, List<UnidadTransporte> flotaDisponible) {
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
        tramosCambioTipoUltimoSplit = 0;
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
            RutaAlg ruta = null;
            for (UnidadTransporte cand : libres) {
                if (cand.tipo == tipoTramo) { ruta = rutaCon(cand, tramo); break; }
            }
            if (ruta == null) {
                // No queda unidad del tipo pedido: la mejor libre donde cabe, con valores reales
                int cant = 0;
                for (Pedido p : tramo) cant += p.cantidad;
                for (UnidadTransporte cand : libres) {
                    if (cant > cand.tipo.capacidadMaxima) continue;
                    RutaAlg r = rutaCon(cand, tramo);
                    if (ruta == null || esMejorRuta(r, ruta)) ruta = r;
                }
                if (ruta != null) tramosCambioTipoUltimoSplit++;
            }
            if (ruta != null) {
                libres.remove(ruta.unidad);
                s.rutas.add(ruta);
            } else {
                sinAsignar.addAll(tramo);
            }
        }
        s.pedidosSinAsignar = sinAsignar;
        Compartido.evaluarSolucion(s);
        return s;
    }

    private static RutaAlg rutaCon(UnidadTransporte u, List<Pedido> tramo) {
        RutaAlg r = new RutaAlg();
        r.unidad = u;
        for (Pedido p : tramo) r.paradas.add(ParadaAlg.entrega(p));
        Compartido.recalcularDistanciaYCosto(r);
        return r;
    }

    /** true si "a" tiene menos entregas tarde que "b" o, a igualdad, menor costo. */
    private static boolean esMejorRuta(RutaAlg a, RutaAlg b) {
        int ta = Compartido.pedidosTarde(a), tb = Compartido.pedidosTarde(b);
        if (ta != tb) return ta < tb;
        return a.costo < b.costo;
    }
}
