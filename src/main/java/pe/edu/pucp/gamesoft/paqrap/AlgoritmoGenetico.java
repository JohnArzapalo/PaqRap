package pe.edu.pucp.gamesoft.paqrap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *  evaluación de un vecino de la búsqueda local. El criterio se revisa antes de
 *  cada hijo (Etapa 27; antes, solo al terminar la generación) y en cada
 *  movimiento de la búsqueda local.
 *
 *  Sin estado estático (Etapa 24): cada ejecución usa su propio objeto
 *  (new AlgoritmoGenetico(semilla)), con su generador aleatorio, su
 *  presupuesto y sus contadores. Así varias simulaciones pueden correr en
 *  paralelo sin pisarse. Los parámetros son constantes leídas una vez del
 *  archivo; BUSQUEDA_LOCAL puede cambiarse con --busqueda-local al iniciar
 *  el experimento (antes de cualquier corrida, igual para todas). */
class AlgoritmoGenetico {

    /** Generador aleatorio de ESTA ejecución: la misma semilla da la misma evolución. */
    private final Random azar;

    /** Cada réplica del experimento (y cada ciclo de replanificación) usa su propia semilla. */
    AlgoritmoGenetico(long semilla) {
        azar = new Random(semilla);
    }

    /** Semilla por defecto (reproducible), para la demostración de Main. */
    AlgoritmoGenetico() {
        this(11);
    }

    /** Tamaño de población por defecto (ag.poblacion). */
    static final int POBLACION = (int) Parametros.entero("ag.poblacion", 30);
    /** Búsqueda local memética activada (ag.busqueda_local = si | no; --busqueda-local al iniciar). */
    static boolean BUSQUEDA_LOCAL = !"no".equalsIgnoreCase(Parametros.texto("ag.busqueda_local", "si"));
    static final double PROB_BUSQUEDA_LOCAL = Parametros.decimal("ag.prob_busqueda_local", 0.2);
    static final int MOVIMIENTOS_BUSQUEDA_LOCAL = (int) Parametros.entero("ag.movimientos_busqueda_local", 30);
    /** Si hay entregas sin asignar, uno de cada dos movimientos de la búsqueda
     *  local es una Inserción (ag.insercion_en_busqueda_local = si | no). */
    static final boolean INSERCION_EN_BL = !"no".equalsIgnoreCase(Parametros.texto("ag.insercion_en_busqueda_local", "si"));

    // Presupuesto de la ejecución en curso (lo consulta también la búsqueda local)
    private long inicioEjecucion, presupuestoMs, maxEvaluaciones;

    /** Tipos de vehículo que realmente existen en la flota de esta ejecución. */
    private TipoUnidad[] tiposFlota = TipoUnidad.values();

    /** Mejor solución de la generación 0 (antes de evolucionar), para el visualizador. */
    Solucion solucionGeneracion0;

    /** {H, S} del mejor individuo en cada generación (curva de convergencia, R11). */
    final List<double[]> historialConvergencia = new ArrayList<>();

    // ===== Contadores de la última ejecución (R6) =====
    long ultimasGeneraciones;
    long ultimasEvaluaciones;
    /** Milisegundos desde el inicio hasta que se encontró la mejor solución. */
    long ultimoTiempoMejorMs;
    /** Tramos de la solución devuelta que no pudieron usar el tipo pedido por
     *  el cromosoma (no quedaba una unidad libre de ese tipo) (R5). */
    int ultimosTramosCambioTipo;

    /** Tramos con cambio de tipo en la última llamada a split(). */
    private int tramosCambioTipoUltimoSplit;
    private long evaluaciones;
    /** Veces que se aplicó la búsqueda local y veces que mejoró el cromosoma. */
    long ultimasAplicacionesBL, ultimasMejorasBL;
    private long aplicacionesBL, mejorasBL;

    /** Tipo de vehículo aleatorio de la flota en el que quepa el pedido. Si
     *  ningún tipo alcanza, devuelve el de MAYOR capacidad de la flota. */
    private TipoUnidad tipoQueQuepa(Pedido p) {
        List<TipoUnidad> validos = new ArrayList<>();
        for (TipoUnidad t : tiposFlota) if (p.cantidad <= t.capacidadMaxima) validos.add(t);
        if (validos.isEmpty()) {
            TipoUnidad mayor = tiposFlota[0];
            for (TipoUnidad t : tiposFlota) if (t.capacidadMaxima > mayor.capacidadMaxima) mayor = t;
            return mayor;
        }
        return validos.get(azar.nextInt(validos.size()));
    }

    private static TipoUnidad[] tiposDisponibles(List<UnidadTransporte> flota) {
        List<TipoUnidad> t = new ArrayList<>();
        for (UnidadTransporte u : flota) if (!t.contains(u.tipo)) t.add(u.tipo);
        return t.toArray(new TipoUnidad[0]);
    }

    Solucion ejecutar(List<Pedido> pedidosPendientes, List<UnidadTransporte> flotaDisponible,
                              long presupuestoMs, long maxEvaluaciones, int tamPoblacion) {
        // El cronómetro empieza ANTES de crear y evaluar la población inicial (R10)
        this.inicioEjecucion = System.currentTimeMillis();
        this.presupuestoMs = presupuestoMs;
        this.maxEvaluaciones = maxEvaluaciones;
        evaluaciones = 0;
        aplicacionesBL = 0;
        mejorasBL = 0;
        tiposFlota = tiposDisponibles(flotaDisponible);
        Solucion planBase = Contexto.actual().planBase;
        Poblacion poblacion = inicializarPoblacion(pedidosPendientes, tamPoblacion, planBase);
        // Etapa 27: el presupuesto se revisa también aquí. Si se agota, la población se queda
        // con los individuos ya evaluados (al menos uno: el primero es el plan vigente sembrado)
        List<Cromosoma> evaluados = new ArrayList<>();
        for (Cromosoma c : poblacion.individuos) {
            if (!evaluados.isEmpty() && debeDetenerse()) break;
            evaluar(c, flotaDisponible);
            evaluados.add(c);
        }
        poblacion.individuos = evaluados;

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
                // Etapa 27: el presupuesto se revisa antes de cada hijo, no solo al terminar la
                // generación. Con cargas altas una generación dura más de un segundo y el AG
                // excedía Ta (2.75 s de media con Ta = 2 s), con más cómputo que Tabú.
                // Si se agota, la generación queda incompleta: sus hijos ya evaluados cuentan.
                if (debeDetenerse()) break;
                Cromosoma padreA = seleccionTorneo(poblacion);
                Cromosoma padreB = seleccionTorneo(poblacion);

                Cromosoma hijo = (azar.nextDouble() < 0.85)
                        ? cruceOX(padreA, padreB)
                        : padreA.copiar();

                if (azar.nextDouble() < 0.15) mutar(hijo);

                evaluar(hijo, flotaDisponible);
                if (BUSQUEDA_LOCAL && azar.nextDouble() < PROB_BUSQUEDA_LOCAL)
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
        // Replanificación (11.3): nunca se devuelve algo peor que el plan vigente reparado
        if (planBase != null && Compartido.mejorQue(planBase, resultado)) {
            resultado = OperadoresVecindario.conUnidadesLibres(planBase, flotaDisponible);
            Compartido.evaluarSolucion(resultado);
        }
        ultimasGeneraciones = generacion;
        ultimasEvaluaciones = evaluaciones;
        ultimoTiempoMejorMs = tiempoMejor;
        ultimosTramosCambioTipo = tramosCambioTipoUltimoSplit;
        ultimasAplicacionesBL = aplicacionesBL;
        ultimasMejorasBL = mejorasBL;
        return resultado;
    }

    /** Modo por evaluaciones si maxEvaluaciones > 0 (se ignora el tiempo); si no, por Ta. */
    private boolean debeDetenerse() {
        if (maxEvaluaciones > 0) return evaluaciones >= maxEvaluaciones;
        return Compartido.debeDetenerse(inicioEjecucion, presupuestoMs);
    }

    /** Búsqueda local memética (primera mejora) sobre la decodificación del
     *  cromosoma. Devuelve el cromosoma mejorado (ya evaluado) o el original
     *  si no hubo mejora; nunca uno peor. Respeta el presupuesto de la corrida. */
    private Cromosoma busquedaLocal(Cromosoma c, List<UnidadTransporte> flota) {
        if (debeDetenerse()) return c;
        aplicacionesBL++;
        Solucion s = OperadoresVecindario.conUnidadesLibres(split(c, flota), flota);
        evaluaciones++;
        boolean mejoro = false;
        for (int k = 0; k < MOVIMIENTOS_BUSQUEDA_LOCAL && !debeDetenerse(); k++) {
            Movimiento m;
            if (INSERCION_EN_BL && !s.pedidosSinAsignar.isEmpty() && k % 2 == 0) {
                m = OperadoresVecindario.insercion(s, azar);
            } else {
                m = azar.nextBoolean()
                        ? OperadoresVecindario.reubicacion(s, azar)
                        : OperadoresVecindario.intercambio(s, azar);
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
    private Cromosoma aCromosoma(Solucion s, Cromosoma base) {
        List<Pedido> perm = new ArrayList<>();
        Map<String, TipoUnidad> tipos = base == null ? new HashMap<>() : new HashMap<>(base.tipoAsignado);
        for (RutaAlg r : s.rutas) {
            for (ParadaAlg p : r.paradas) {
                if (p.tipo != TipoParada.ENTREGA) continue;   // RECARGA/TRASVASE las reconstruye el decodificador
                perm.add(p.pedido);
                tipos.put(p.pedido.id, r.unidad.tipo);
            }
        }
        for (Pedido p : s.pedidosSinAsignar) {
            perm.add(p);
            tipos.putIfAbsent(p.id, tipoQueQuepa(p));
        }
        return new Cromosoma(perm, tipos);
    }

    /** Proporción de la población sembrada con el plan vigente y variaciones suyas (Etapa 11.3). */
    static final double PROPORCION_SEMBRADA = Parametros.decimal("ag.proporcion_sembrada", 0.3);

    /** Población inicial. Si hay plan vigente (replanificación), una parte se siembra
     *  con él codificado (permutación + tipos; el almacén de cada viaje lo elige el
     *  decodificador) y con variaciones suyas (1 a 3 mutaciones); el resto es aleatorio. */
    private Poblacion inicializarPoblacion(List<Pedido> pedidos, int tam, Solucion planBase) {
        Poblacion pob = new Poblacion();
        if (planBase != null && !pedidos.isEmpty()) {
            int sembrados = Math.max(1, (int) Math.round(PROPORCION_SEMBRADA * tam));
            Cromosoma plan = aCromosoma(planBase, null);
            pob.individuos.add(plan);
            for (int k = 1; k < sembrados; k++) {
                Cromosoma v = plan.copiar();
                int mutaciones = 1 + azar.nextInt(3);
                for (int m = 0; m < mutaciones; m++) mutar(v);
                pob.individuos.add(v);
            }
        }
        while (pob.individuos.size() < tam) {
            List<Pedido> perm = new ArrayList<>(pedidos);
            Collections.shuffle(perm, azar);
            Map<String, TipoUnidad> tipos = new HashMap<>();
            for (Pedido p : perm) tipos.put(p.id, tipoQueQuepa(p));   // solo tipos de la flota donde quepa
            pob.individuos.add(new Cromosoma(perm, tipos));
        }
        return pob;
    }

    private Cromosoma seleccionTorneo(Poblacion pob) {
        Cromosoma a = pob.individuos.get(azar.nextInt(pob.individuos.size()));
        Cromosoma b = pob.individuos.get(azar.nextInt(pob.individuos.size()));
        return comparar(a, b) <= 0 ? a : b;
    }

    /** Cruce ordenado (OX): conserva el tramo de un padre tal cual y
     *  completa el resto con el orden del otro padre, sin repetir pedidos. */
    private Cromosoma cruceOX(Cromosoma padreA, Cromosoma padreB) {
        int n = padreA.permutacion.size();
        int c1 = azar.nextInt(n);
        int c2 = azar.nextInt(n);
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
            hijoTipo.put(p.id, azar.nextBoolean() ? padreA.tipoAsignado.get(p.id) : padreB.tipoAsignado.get(p.id));
        }
        return new Cromosoma(hijoPerm, hijoTipo);
    }

    /** Muta: permuta dos pedidos de posición y, con probabilidad 0.5,
     *  cambia el tipo de vehículo asignado a un pedido (entre los tipos
     *  de la flota disponible). */
    private void mutar(Cromosoma c) {
        int n = c.permutacion.size();
        int i = azar.nextInt(n);
        int j = azar.nextInt(n);
        Collections.swap(c.permutacion, i, j);
        if (azar.nextDouble() < 0.5) {
            Pedido p = c.permutacion.get(azar.nextInt(n));
            c.tipoAsignado.put(p.id, tipoQueQuepa(p));
        }
    }

    private void evaluar(Cromosoma c, List<UnidadTransporte> flota) {
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

    /** Decodificador: Split clásico (Main y modo estático) o Split con estado
     *  (simulación: unidades en ruta, carga a bordo, varios viajes y almacenes). */
    Solucion split(Cromosoma c, List<UnidadTransporte> flotaDisponible) {
        return Contexto.actual().conEstado ? splitConEstado(c, flotaDisponible) : splitClasico(c, flotaDisponible);
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
    Solucion splitClasico(Cromosoma c, List<UnidadTransporte> flotaDisponible) {
        List<Pedido> perm = c.permutacion;
        int n = perm.size();
        int m = flotaDisponible.size();   // no puede haber más rutas que vehículos

        // costoMinimo[k][j]: menor costo para atender los primeros j pedidos con exactamente k rutas
        double[][] costoMinimo = new double[m + 1][n + 1];
        int[][] predecesor = new int[m + 1][n + 1];
        for (double[] fila : costoMinimo) Arrays.fill(fila, Double.MAX_VALUE);
        costoMinimo[0][0] = 0;

        Almacen al = Compartido.ALMACEN_CENTRAL;
        Contexto cx = Contexto.actual();
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
                    reloj += d / cx.velocidad(tipo);           // hora de llegada al cliente
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

    // ================= Split con estado (simulación; Etapas 10.2 y 11) =================

    /**
     * Decodificador para la simulación con estado. Cada unidad parte de su
     * posición y hora reales (Contexto), puede llevar carga a bordo y hacer
     * varios viajes; cada viaje empieza con una RECARGA en un almacén con
     * stock o con un TRASVASE en una unidad averiada.
     *  1. Las entregas a bordo van en su unidad, en el orden de la permutación.
     *  2. El resto se corta en tramos (programación dinámica de una dimensión):
     *     un tramo tiene un solo origen (almacén o una misma averiada) y cabe en
     *     el tipo de su primer pedido; su costo se estima saliendo en la hora 0
     *     desde el mejor punto de carga (aproximación: la hora real de salida
     *     depende de la unidad, que aún no se conoce).
     *  3. Cada tramo, en orden, se agrega al final de la ruta de la unidad (de
     *     su tipo; si ninguna sirve, de cualquier tipo, contado como cambio de
     *     tipo) y con el punto de carga (almacén o trasvase) que minimizan
     *     (entregas tarde, costo) REALES desde el estado de la unidad.
     * STOCK: se reserva en el orden de asignación de los tramos; un almacén
     * sin stock suficiente para el tramo no se considera.
     */
    Solucion splitConEstado(Cromosoma c, List<UnidadTransporte> flota) {
        Contexto cx = Contexto.actual();
        Solucion s = new Solucion();
        Map<String, RutaAlg> rutaDe = new HashMap<>();
        for (UnidadTransporte u : flota) {
            RutaAlg r = new RutaAlg();
            r.unidad = u;
            rutaDe.put(u.codigo, r);
            s.rutas.add(r);
        }
        tramosCambioTipoUltimoSplit = 0;
        List<Pedido> resto = new ArrayList<>();
        for (Pedido p : c.permutacion) {
            if (p.aBordoDe == null) resto.add(p);
            else if (rutaDe.containsKey(p.aBordoDe)) rutaDe.get(p.aBordoDe).paradas.add(ParadaAlg.entrega(p));
            else s.pedidosSinAsignar.add(p);   // su unidad no está operativa (no debería ocurrir)
        }
        double[] stock = new double[cx.almacenes.size()];
        for (int i = 0; i < stock.length; i++) stock[i] = cx.almacenes.get(i).stock;
        for (List<Pedido> tramo : cortar(resto, c, cx)) {
            if (!asignarTramo(tramo, c.tipoAsignado.get(tramo.get(0).id), flota, rutaDe, stock, cx, true)
                    && !asignarTramo(tramo, null, flota, rutaDe, stock, cx, false)) {
                s.pedidosSinAsignar.addAll(tramo);
            }
        }
        Compartido.evaluarSolucion(s);
        return s;
    }

    /** Programación dinámica de cortes (una dimensión, sin límite de tramos). */
    private List<List<Pedido>> cortar(List<Pedido> resto, Cromosoma c, Contexto cx) {
        int n = resto.size();
        double[] f = new double[n + 1];
        int[] pred = new int[n + 1];
        Arrays.fill(f, Double.MAX_VALUE);
        f[0] = 0;
        for (int i = 0; i < n; i++) {
            if (f[i] == Double.MAX_VALUE) continue;
            Pedido primero = resto.get(i);
            TipoUnidad tipo = c.tipoAsignado.getOrDefault(primero.id, tiposFlota[0]);
            // Puntos de carga posibles para el tramo: almacenes, o la unidad averiada
            List<int[]> inicios = new ArrayList<>();
            double relojInicio = 0;
            if (primero.enAveriada != null) {
                Contexto.Averiada av = cx.averiadas.get(primero.enAveriada);
                if (av != null) inicios.add(new int[]{av.x, av.y});
                relojInicio = Compartido.HORAS_TRASVASE;
            } else {
                for (Contexto.AlmacenPlan a : cx.almacenes) inicios.add(new int[]{a.almacen.x, a.almacen.y});
            }
            if (inicios.isEmpty()) inicios.add(new int[]{primero.x, primero.y});
            int k = inicios.size();
            double[] km = new double[k], reloj = new double[k];
            int[] tarde = new int[k], px = new int[k], py = new int[k];
            for (int a = 0; a < k; a++) { px[a] = inicios.get(a)[0]; py[a] = inicios.get(a)[1]; reloj[a] = relojInicio; }
            int carga = 0;
            for (int j = i + 1; j <= n; j++) {
                Pedido p = resto.get(j - 1);
                if (j > i + 1 && !Objects.equals(p.enAveriada, primero.enAveriada)) break;
                carga += p.cantidad;
                if (carga > tipo.capacidadMaxima && j > i + 1) break;   // tramos más largos tampoco caben
                double mejor = Double.MAX_VALUE;
                for (int a = 0; a < k; a++) {
                    double d = cx.distanciaTramo(px[a], py[a], p.x, p.y, reloj[a], cx.velocidad(tipo));
                    km[a] += d;
                    reloj[a] = cx.esperaDestino(p.x, p.y, cx.avanzar(reloj[a], d / cx.velocidad(tipo)));
                    if (reloj[a] > p.horaLimite()) tarde[a]++;
                    reloj[a] = cx.avanzar(reloj[a], Compartido.HORAS_ENTREGA);
                    px[a] = p.x;
                    py[a] = p.y;
                    Contexto.AlmacenPlan vuelta = cx.almacenMasCercano(px[a], py[a], reloj[a]);
                    double kmTotal = km[a] + cx.distanciaTramo(px[a], py[a], vuelta.almacen.x, vuelta.almacen.y, reloj[a], cx.velocidad(tipo));
                    mejor = Math.min(mejor, kmTotal * tipo.costoPorKilometro + tarde[a] * Compartido.PENALIDAD_TARDANZA);
                }
                if (f[i] + mejor < f[j]) {
                    f[j] = f[i] + mejor;
                    pred[j] = i;
                }
            }
        }
        List<List<Pedido>> tramos = new ArrayList<>();
        for (int j = n; j > 0; j = pred[j]) tramos.add(0, new ArrayList<>(resto.subList(pred[j], j)));
        return tramos;
    }

    /**
     * Agrega el tramo al final de la ruta de la mejor unidad (tipo indicado, o
     * cualquiera si tipo es null) con el mejor punto de carga. Devuelve false
     * si ninguna opción es factible.
     */
    private boolean asignarTramo(List<Pedido> tramo, TipoUnidad tipo, List<UnidadTransporte> flota,
                                        Map<String, RutaAlg> rutaDe, double[] stock, Contexto cx, boolean tipoPedido) {
        int carga = 0;
        for (Pedido p : tramo) carga += p.cantidad;
        String averiada = tramo.get(0).enAveriada;
        RutaAlg mejorRuta = null;
        List<ParadaAlg> mejorPrefijo = null;
        int mejorAlmacen = -1, mejorDeltaTarde = Integer.MAX_VALUE, mejorPrevias = -1;
        double mejorDeltaCosto = Double.MAX_VALUE;
        for (UnidadTransporte u : flota) {
            if ((tipo != null && u.tipo != tipo) || u.tipo.capacidadMaxima < carga) continue;
            RutaAlg r = rutaDe.get(u.codigo);
            // Estabilidad (18.2): entregas del tramo que el plan vigente ya tenía en esta unidad
            int previas = 0;
            if (cx.asignacionVigente != null)
                for (Pedido p : tramo) if (u.codigo.equals(cx.asignacionVigente.get(p.id))) previas++;
            Compartido.EvalRuta antes = Compartido.evaluarRuta(r);
            // Puntos de carga posibles: {prefijo de paradas, índice del almacén (-1 = trasvase)}
            List<List<ParadaAlg>> prefijos = new ArrayList<>();
            List<Integer> almacenDelPrefijo = new ArrayList<>();
            if (averiada != null) {
                Contexto.Averiada av = cx.averiadas.get(averiada);
                if (av == null) continue;
                prefijos.add(List.of(ParadaAlg.trasvase(av.codigo, av.x, av.y)));
                almacenDelPrefijo.add(-1);
            } else {
                Contexto.Inicio ini = cx.inicioDe(u);
                int aqui = cx.almacenEn(ini.x, ini.y);
                if (r.paradas.isEmpty() && aqui >= 0 && stock[aqui] >= carga) {   // sale cargada de donde está
                    prefijos.add(List.of());
                    almacenDelPrefijo.add(aqui);
                }
                for (int a = 0; a < stock.length; a++) {
                    if (stock[a] < carga) continue;
                    prefijos.add(List.of(ParadaAlg.recarga(cx.almacenes.get(a).almacen)));
                    almacenDelPrefijo.add(a);
                }
            }
            for (int k = 0; k < prefijos.size(); k++) {
                int tam = r.paradas.size();
                r.paradas.addAll(prefijos.get(k));
                for (Pedido p : tramo) r.paradas.add(ParadaAlg.entrega(p));
                Compartido.EvalRuta e = Compartido.evaluarRuta(r);
                r.paradas.subList(tam, r.paradas.size()).clear();
                if (!e.factible) continue;
                int dT = e.tarde - antes.tarde;
                double dC = e.costo - antes.costo;
                // (H, S) primero; a igualdad exacta, la unidad que ya tenía más entregas del tramo
                if (dT < mejorDeltaTarde || (dT == mejorDeltaTarde && dC < mejorDeltaCosto)
                        || (dT == mejorDeltaTarde && dC == mejorDeltaCosto && previas > mejorPrevias)) {
                    mejorDeltaTarde = dT;
                    mejorDeltaCosto = dC;
                    mejorPrevias = previas;
                    mejorRuta = r;
                    mejorPrefijo = prefijos.get(k);
                    mejorAlmacen = almacenDelPrefijo.get(k);
                }
            }
        }
        if (mejorRuta == null) return false;
        mejorRuta.paradas.addAll(mejorPrefijo);
        for (Pedido p : tramo) mejorRuta.paradas.add(ParadaAlg.entrega(p));
        if (mejorAlmacen >= 0) stock[mejorAlmacen] -= carga;
        if (!tipoPedido) tramosCambioTipoUltimoSplit++;
        return true;
    }
}
