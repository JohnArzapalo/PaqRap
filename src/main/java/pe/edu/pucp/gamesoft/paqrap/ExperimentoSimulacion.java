package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Modo SIMULACIÓN del experimento (Etapa 14, alineado con el profesor):
 * algoritmo (TABU, AG) × carga (BAJA, MEDIA, ALTA) × réplicas, escenario
 * EXPERIMENTO por defecto (sin pantalla, hasta el colapso o el tope de
 * seguridad), con bloqueos, almacenes intermedios y replanificación con estado.
 *
 * Para cada nivel, ambos algoritmos reciben EXACTAMENTE los mismos pedidos,
 * bloqueos, flota, almacenes y parámetros (P1): los pedidos se generan con
 * semilla fija por nivel y todo se guarda en datos/generados/. La única
 * diferencia entre corridas comparadas es el algoritmo y la semilla de la réplica.
 * Averías: desactivadas por defecto (el IEN las excluía); con --averias si se
 * usa el MISMO archivo para todas las corridas.
 *
 * Argumentos propios (además de los de Experimento): --escenario, --bloqueos,
 * --averias si|no, --archivo-averias, --mantenimiento, --parciales,
 * --acelerado si|no (SIM_5D), --eventos prefijo.csv.
 */
final class ExperimentoSimulacion {

    static final String[] NIVELES = {"BAJA", "MEDIA", "ALTA"};
    private static final double[] FRACCION_POR_DEFECTO = {0.30, 0.60, 0.90};
    static final String ARCHIVO_MES = "datos/ventas202609_SINTETICO_MES.txt";
    static final String BLOQUEOS_MES = "datos/202609_SINTETICO.bloqueadas";

    // Opciones (las fija Experimento.leerArgumentos)
    static Simulador.Escenario ESCENARIO = Simulador.Escenario.EXPERIMENTO;
    static String ARCHIVO_BLOQUEOS = null;          // null = el del mes si existe; "no" = sin bloqueos
    static boolean AVERIAS = false;
    static String ARCHIVO_AVERIAS = null;           // null = generado con semilla
    static String ARCHIVO_MANTENIMIENTO = null;
    static String PARCIALES = null;                 // null = parámetro parciales.estrategia
    static boolean ACELERADO = false;
    static String PREFIJO_EVENTOS = null;
    static String DESTINO_BLOQUEADO = null;          // null = parámetro red.destino_bloqueado
    static boolean EXCLUIR_DESTINOS_BLOQUEADOS = false;   // solo datos sintéticos (Etapa 17.2)
    static Double PENALIDAD_ESTABILIDAD = null;      // null = parámetro estabilidad.penalidad_por_cambio
    static Double HOLGURA_MIN = null;                // null = parámetro plan.holgura_min (Etapa 22)
    /** Etapa 22: día del mes en que empieza la simulación (1 = inicio del archivo). Los pedidos
     *  registrados antes se descartan; el horizonte (p. ej., 5 días en SIM_5D) se cuenta desde ahí. */
    static int DIA_INICIO = 1;
    static Double PENALIDAD_HOLGURA = null;          // null = parámetro plan.penalidad_holgura
    /** Etapa 19.2: calibra un tope de evaluaciones por algoritmo equivalente a Ta en esta PC. */
    static boolean CALIBRAR_EVALUACIONES = false;
    /**
     * Etapa 24: corridas simultáneas en este proceso (--hilos N; 1 = una tras otra).
     * Con parada por evaluaciones el resultado es idéntico con cualquier N. Con parada por
     * tiempo (Ta), los hilos se reparten la CPU: usar N ≤ núcleos físicos, y mezclar
     * algoritmos en el orden aleatorio para que ninguno quede en desventaja.
     */
    static int HILOS = 1;
    /**
     * Etapa 25: situaciones del experimento. "por_replica" (por defecto): la réplica r de cada
     * nivel usa su propia muestra de pedidos (semilla de carga distinta), la misma para TABU y AG;
     * así el % de colapsos resume muchas situaciones y el diseño sigue pareado. "por_nivel": una
     * sola muestra por nivel para todas las réplicas (diseño anterior a la etapa 25).
     */
    static String SITUACIONES = Parametros.texto("experimento.situaciones", "por_replica");
    /** Etapa 25: niveles a medida (--cargas NOMBRE=fraccion,...), p. ej. para calibrar; null = BAJA, MEDIA, ALTA. */
    static String CARGAS = null;

    private ExperimentoSimulacion() {
    }

    /** Resultado de una corrida, listo para escribirse en el CSV y en la consola. */
    private static class Fila {
        String nombre, csv, consola;
        Simulador.Resultado r;
    }

    /**
     * Una situación: un conjunto de pedidos (archivo generado o el de ventas tal cual).
     * TABU y AG corren EXACTAMENTE las mismas situaciones (diseño pareado).
     */
    private static class Situacion {
        long semillaCarga;       // 0 = archivo de ventas tal cual
        Path ruta;
        String archivoCarga;
        int originales, paquetes;

        /** Los pedidos se leen al empezar la corrida (en su hilo), no se guardan todos en memoria. */
        List<Pedido> pedidos() throws IOException {
            return LectorPedidos.leerAbsoluto(ruta.toString());
        }
    }

    private static class Nivel {
        String nombre;
        double fraccion;
        /** Con situaciones por réplica, la réplica r usa situaciones.get(r - 1); si no, siempre la única. */
        final List<Situacion> situaciones = new ArrayList<>();

        Situacion situacion(int replica) {
            return situaciones.size() == 1 ? situaciones.get(0) : situaciones.get(replica - 1);
        }
    }

    private static class Corrida {
        Nivel nivel;
        Situacion situacion;
        String algoritmo;
        int replica;
        long semilla;
    }

    static void ejecutar() throws IOException {
        String archivo = Experimento.ARCHIVO_EXPLICITO ? Experimento.ARCHIVO_VENTAS : ARCHIVO_MES;
        String bloqueos = ARCHIVO_BLOQUEOS != null ? ARCHIVO_BLOQUEOS
                : Files.exists(Paths.get(BLOQUEOS_MES)) ? BLOQUEOS_MES : "no";
        boolean sintetico = (archivo + bloqueos).toUpperCase(Locale.ROOT).contains("SINTETICO");
        boolean ventasSinteticas = archivo.toUpperCase(Locale.ROOT).contains("SINTETICO");
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        Simulador.Config base = new Simulador.Config();
        base.escenario = ESCENARIO;
        if (Experimento.TA_EXPLICITO) base.taMs = Experimento.TA_MS;
        if (Experimento.SA_MIN != null) base.saMin = Experimento.SA_MIN;
        base.maxEvaluaciones = Experimento.MAX_EVALUACIONES;
        base.detenerEnColapso = !"no".equalsIgnoreCase(Parametros.texto("sim5d.detener_en_colapso", "si"));
        if (PARCIALES != null) base.estrategiaParciales = PARCIALES;
        if (DESTINO_BLOQUEADO != null) base.reglaDestino = Contexto.ReglaDestino.desde(DESTINO_BLOQUEADO);
        if (PENALIDAD_ESTABILIDAD != null) base.penalidadEstabilidad = PENALIDAD_ESTABILIDAD;
        if (HOLGURA_MIN != null) base.holguraMin = HOLGURA_MIN;
        base.inicioMin = (DIA_INICIO - 1) * 1440.0;
        if (PENALIDAD_HOLGURA != null) base.penalidadHolgura = PENALIDAD_HOLGURA;
        base.mapa = "no".equalsIgnoreCase(bloqueos) || "no".equalsIgnoreCase(Parametros.texto("red.bloqueos", "si"))
                ? null : MapaVial.leer(bloqueos);
        if (ARCHIVO_MANTENIMIENTO != null) base.mantenimientos = Mantenimiento.leer(ARCHIVO_MANTENIMIENTO);
        String salida = Experimento.SALIDA_CSV != null ? Experimento.SALIDA_CSV
                : "resultados_simulacion" + (sintetico ? "_SINTETICO" : "") + ".csv";
        String modoParada = CALIBRAR_EVALUACIONES ? "evaluaciones_calibradas"
                : base.maxEvaluaciones > 0 ? "evaluaciones" : "tiempo";

        if (sintetico) System.out.println("*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***");
        System.out.println("=== Modo simulación, escenario " + ESCENARIO + " (pedidos base: " + archivo + ") ===");
        System.out.printf(Locale.US, "Sa = %.0f min, Ta = %d ms%s, horizonte = %.0f min; bloqueos: %s (%d); almacenes "
                        + "intermedios: %s; averías: %s; parciales: %s; alimentación: %s; AG con búsqueda local: %s%n",
                base.saMin, base.taMs, base.maxEvaluaciones > 0 ? " (ignorado: " + base.maxEvaluaciones
                        + " evaluaciones por llamada)" : "", base.horizonte(), bloqueos,
                base.mapa == null ? 0 : base.mapa.bloqueos.size(), base.almacenesIntermedios ? "sí" : "no",
                AVERIAS ? "sí" : "no", base.estrategiaParciales, base.alimentacion ? "sí" : "no",
                AlgoritmoGenetico.BUSQUEDA_LOCAL ? "sí" : "no");

        // 1. Capacidad teórica diaria (C_max) a partir del archivo base
        List<LectorPedidos.Registro> registros = LectorPedidos.leerRegistros(archivo);
        CapacidadFlota.Resultado cap = CapacidadFlota.calcular(LectorPedidos.leerAbsoluto(archivo), flota);
        System.out.println("\n=== Capacidad teórica diaria de la flota ===");
        System.out.print(cap.reporte());
        double cmax = cap.cmaxElegido();

        // 2. Conjuntos por nivel (semilla fija por nivel) y bloqueos/averías comunes, en datos/generados
        int dias = (int) Parametros.entero("carga.dias", 30);
        long semillaBase = Parametros.entero("carga.semilla_base", 2026);
        Path carpeta = Paths.get(archivo).toAbsolutePath().getParent().resolve("generados");
        Files.createDirectories(carpeta);
        if (base.mapa != null)
            Files.copy(Paths.get(bloqueos), carpeta.resolve(Paths.get(bloqueos).getFileName()), StandardCopyOption.REPLACE_EXISTING);
        String archivoAverias = "";
        if (AVERIAS) {
            if (ARCHIVO_AVERIAS == null) {
                long sem = Parametros.entero("averias.semilla", 777);
                Path destino = carpeta.resolve("averias_SINTETICO_semilla" + sem + ".txt");
                Averia.generar(flota, dias, Parametros.decimal("averias.por_dia", 2),
                        new double[]{Parametros.decimal("averias.prob_tipo1", 0.6),
                                     Parametros.decimal("averias.prob_tipo2", 0.3),
                                     Parametros.decimal("averias.prob_tipo3", 0.1)}, sem, destino);
                ARCHIVO_AVERIAS = destino.toString();
            }
            base.averias = Averia.leer(ARCHIVO_AVERIAS);
            archivoAverias = Paths.get(ARCHIVO_AVERIAS).getFileName().toString();
            System.out.println("Averías: " + base.averias.size() + " eventos (" + ARCHIVO_AVERIAS + ")");
        }
        if (EXCLUIR_DESTINOS_BLOQUEADOS && !ventasSinteticas)
            System.out.println("AVISO: --excluir-destinos-bloqueados se ignora con datos oficiales (se usa red.destino_bloqueado)");
        List<Nivel> niveles = new ArrayList<>();
        System.out.println("\n=== Niveles de carga (" + dias + " días; archivos en " + carpeta + ") ===");
        // Niveles: BAJA, MEDIA y ALTA (fracciones de parametros.properties) o a medida con --cargas.
        List<String> nombres = new ArrayList<>();
        List<Double> fracciones = new ArrayList<>();
        if (CARGAS != null) {
            for (String par : CARGAS.split(",")) {
                String[] nv = par.trim().split("=");
                nombres.add(nv[0].trim().toUpperCase(Locale.ROOT));
                fracciones.add(Double.parseDouble(nv[1].trim()));
            }
        } else {
            for (int k = 0; k < NIVELES.length; k++) {
                nombres.add(NIVELES[k]);
                fracciones.add(Parametros.decimal("carga.nivel." + NIVELES[k], FRACCION_POR_DEFECTO[k]));
            }
        }
        boolean porReplica = !"por_nivel".equalsIgnoreCase(SITUACIONES);
        boolean excluir = EXCLUIR_DESTINOS_BLOQUEADOS && ventasSinteticas && base.mapa != null;
        System.out.println("Situaciones: " + (porReplica
                ? "una muestra de pedidos por réplica (la misma para TABU y AG)"
                : "una muestra de pedidos por nivel (todas las réplicas)"));
        for (int k = 0; k < nombres.size(); k++) {
            if (Experimento.FILTRO_NIVELES != null && !Experimento.FILTRO_NIVELES.contains(nombres.get(k))) continue;
            Nivel n = new Nivel();
            n.nombre = nombres.get(k);
            n.fraccion = fracciones.get(k);
            int descartados = 0;
            for (int r = 1; r <= (porReplica ? Experimento.REPLICAS : 1); r++) {
                // Semilla de carga: por nivel, semilla_base + k + 1 (como antes de la etapa 25);
                // por réplica, semilla_base + 1000·(k + 1) + r. Mismas opciones -> mismos archivos en cualquier PC.
                long semilla = porReplica ? semillaBase + 1000L * (k + 1) + r : semillaBase + k + 1;
                Path destino = carpeta.resolve(GeneradorCarga.nombreArchivo(n.nombre, n.fraccion, semilla, archivo, excluir));
                descartados += GeneradorCarga.generar(registros, n.fraccion * cmax, dias, semilla, destino,
                        excluir ? base.mapa : null);
                n.situaciones.add(situacion(destino, semilla));
            }
            if (excluir) System.out.println("  " + n.nombre + ": " + descartados
                    + " pedidos sorteados descartados por destino bloqueado en toda su ventana");
            niveles.add(n);
            Situacion s1 = n.situaciones.get(0);
            System.out.printf(Locale.US, "  %-5s %3.0f%% de C_max = %.0f paquetes/día -> %d situación(es) de %d días; la 1.ª: %d pedidos, %d paquetes (%s)%n",
                    n.nombre, n.fraccion * 100, n.fraccion * cmax, n.situaciones.size(), dias, s1.originales, s1.paquetes,
                    s1.archivoCarga);
        }
        // Nivel ARCHIVO (solo si se pide con --niveles): el archivo de ventas TAL CUAL, sin remuestreo.
        // Sirve para verificar la condición de 5 días sin colapso con la demanda del propio archivo.
        // Es una sola situación para todas las réplicas.
        if (Experimento.FILTRO_NIVELES != null && Experimento.FILTRO_NIVELES.contains("ARCHIVO")) {
            Nivel n = new Nivel();
            n.nombre = "ARCHIVO";
            // copia en generados/ (como las cargas de los otros niveles) para el registro de hashes
            Path copia = carpeta.resolve(Paths.get(archivo).getFileName());
            Files.copy(Paths.get(archivo), copia, StandardCopyOption.REPLACE_EXISTING);
            Situacion s = situacion(copia, 0);
            n.situaciones.add(s);
            double ultimaHora = 0;
            for (Pedido p : s.pedidos()) ultimaHora = Math.max(ultimaHora, p.horaRegistro);
            double diasArchivo = Math.max(1, Math.ceil(ultimaHora / 24.0));
            n.fraccion = s.paquetes / diasArchivo / cmax;
            niveles.add(n);
            System.out.printf(Locale.US, "  %-7s archivo tal cual: %.0f paquetes/día (%.0f%% de C_max) -> %d pedidos, %d paquetes%n",
                    n.nombre, s.paquetes / diasArchivo, n.fraccion * 100, s.originales, s.paquetes);
        }

        // 3. Matriz de corridas, semillas registradas y orden aleatorio
        List<Corrida> corridas = new ArrayList<>();
        for (Nivel n : niveles)
            for (String alg : Experimento.ALGORITMOS) {
                if (Experimento.FILTRO_ALGORITMOS != null && !Experimento.FILTRO_ALGORITMOS.contains(alg)) continue;
                for (int r = 1; r <= Experimento.REPLICAS; r++) {
                    Corrida c = new Corrida();
                    c.nivel = n; c.situacion = n.situacion(r);
                    c.algoritmo = alg; c.replica = r; c.semilla = Experimento.SEMILLA_BASE + r;
                    corridas.add(c);
                }
            }
        Collections.shuffle(corridas, new Random(Experimento.SEMILLA_ORDEN));

        // Trazabilidad (19.1): hash SHA-256 de todos los archivos de entrada
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put(archivo, sha256(Paths.get(archivo)));
        if (base.mapa != null) hashes.put(bloqueos, sha256(Paths.get(bloqueos)));
        if (AVERIAS) hashes.put(ARCHIVO_AVERIAS, sha256(Paths.get(ARCHIVO_AVERIAS)));
        if (ARCHIVO_MANTENIMIENTO != null) hashes.put(ARCHIVO_MANTENIMIENTO, sha256(Paths.get(ARCHIVO_MANTENIMIENTO)));
        String config = System.getProperty("paqrap.config", Parametros.RUTA_POR_DEFECTO);
        if (Files.exists(Paths.get(config))) hashes.put(config, sha256(Paths.get(config)));
        for (Nivel n : niveles)
            for (Situacion s : n.situaciones) hashes.put(s.ruta.toString(), sha256(s.ruta));
        String pc = System.getenv().getOrDefault("COMPUTERNAME", "desconocida");
        Path archivoHashes = Paths.get(salida.replaceFirst("[.]csv$", "") + "_hashes.txt");
        try (PrintWriter h = new PrintWriter(Files.newBufferedWriter(archivoHashes, StandardCharsets.UTF_8))) {
            h.println("# SHA-256 de los archivos de entrada (compare entre PCs: deben coincidir)");
            h.println("# PC: " + pc);
            for (Map.Entry<String, String> e : hashes.entrySet()) h.println(e.getValue() + "  " + e.getKey());
        }
        System.out.println("\nHashes de entrada guardados en " + archivoHashes.toAbsolutePath());
        String hashVentas = hashes.get(archivo), hashBloqueos = base.mapa == null ? "" : hashes.get(bloqueos);

        // Calibración opcional (19.2): tope de evaluaciones por algoritmo equivalente a Ta en esta PC
        Map<String, Long> topes = new LinkedHashMap<>();
        if (CALIBRAR_EVALUACIONES) {
            topes = calibrar(niveles.get(0).nombre, niveles.get(0).situaciones.get(0).pedidos(), flota, base);
            Path archivoCal = Paths.get(salida.replaceFirst("[.]csv$", "") + "_calibracion.txt");
            try (PrintWriter c = new PrintWriter(Files.newBufferedWriter(archivoCal, StandardCharsets.UTF_8))) {
                c.println("# Topes de evaluaciones equivalentes a Ta = " + base.taMs + " ms en la PC " + pc);
                for (Map.Entry<String, Long> e : topes.entrySet()) c.println(e.getKey() + "=" + e.getValue());
            }
            System.out.println("Topes calibrados: " + topes + " (" + archivoCal.toAbsolutePath() + ")");
        }
        System.out.printf("%n=== Ejecutando %d corridas ===%n", corridas.size());

        // 4. Ejecución y registro
        Map<String, List<Simulador.Resultado>> resumen = new LinkedHashMap<>();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(salida), StandardCharsets.UTF_8))) {
            out.println("orden,modo,escenario,nivel,pct_cmax,cmax_paquetes_dia,fuente_cmax,archivo_carga,archivo_ventas,"
                    + "archivo_bloqueos,archivo_averias,algoritmo,replica,semilla,modo_parada,ta_ms,sa_min,max_evaluaciones,"
                    + "busqueda_local,parciales,pedidos_originales_total,paquetes_total,colapso_min,colapso_h,censurada,"
                    + "pedido_colapso,unidad_colapso,causa_colapso,costo_acumulado,km_acumulados,pedidos_llegados,"
                    + "pedidos_entregados,pedidos_evaluables,pct_pedidos_en_plazo,entregas_tarde,replanificaciones,"
                    + "replan_por_evento,planificador_ms_medio,planificador_ms_max,iteraciones_totales,evaluaciones_totales,"
                    + "aplazamientos,cambios_de_unidad,viajes_totales,viajes_por_vehiculo_medio,viajes_por_vehiculo_max,"
                    + "bloqueos_encontrados,averias_aplicadas,trasvases,parciales_creadas,tiempo_real_ms,regla_destino,"
                    + "pedidos_inentregables_bloqueo,penalidad_estabilidad,pc,sha256_ventas,sha256_bloqueos,"
                    + "holgura_min,penalidad_holgura,dia_inicio,horas_desde_inicio,situaciones,semilla_carga");
            // Etapa 24: sin estado estático, las corridas pueden ir en paralelo (--hilos N), cada
            // una en su hilo. Con N = 1 (por defecto) van una tras otra, como antes. Las filas del
            // CSV se escriben siempre en el orden aleatorio de la matriz, sea cual sea N.
            final Map<String, Long> topesCorrida = topes;
            final String averiasCorrida = archivoAverias;
            ExecutorService hilos = Executors.newFixedThreadPool(Math.max(1, HILOS));
            List<Future<Fila>> filas = new ArrayList<>();
            for (int i = 0; i < corridas.size(); i++) {
                final int orden = i + 1;
                final Corrida c = corridas.get(i);
                filas.add(hilos.submit(() -> {
                    Simulador.Config cfg = copiar(base);
                    cfg.semilla = c.semilla;
                    if (CALIBRAR_EVALUACIONES) cfg.maxEvaluaciones = topesCorrida.get(c.algoritmo);
                    if (ESCENARIO == Simulador.Escenario.SIM_5D && !ACELERADO)
                        cfg.reloj = Reloj.escalado(5 * 1440 / Parametros.decimal("sim5d.minutos_reales", 30));
                    if (ESCENARIO == Simulador.Escenario.DIA_A_DIA && !ACELERADO) cfg.reloj = Reloj.real();
                    PrintWriter eventos = null;
                    if (PREFIJO_EVENTOS != null) {
                        String nombre = PREFIJO_EVENTOS.replaceFirst("\\.csv$", "") + "_" + c.nivel.nombre + "_"
                                + c.algoritmo + "_r" + c.replica + (sintetico ? "_SINTETICO" : "") + ".csv";
                        eventos = new PrintWriter(Files.newBufferedWriter(Paths.get(nombre), StandardCharsets.UTF_8));
                        cfg.eventos = eventos;
                    }
                    Planificador plan = c.algoritmo.equals("TABU")
                            ? Planificador.tabu(Experimento.TABU_DURACION)
                            : Planificador.genetico(Experimento.AG_POBLACION);
                    long t0 = System.nanoTime();
                    Simulador.Resultado r;
                    try {
                        List<Pedido> todos = c.situacion.pedidos(), pedidosCorrida = todos;
                        if (cfg.inicioMin > 0) {   // ventana que empieza el día DIA_INICIO
                            pedidosCorrida = new ArrayList<>();
                            for (Pedido p : todos) if (p.horaRegistro * 60 >= cfg.inicioMin) pedidosCorrida.add(p);
                        }
                        r = Simulador.simular(pedidosCorrida, flota, plan, cfg);
                    } finally {
                        if (eventos != null) eventos.close();
                    }
                    long ms = (System.nanoTime() - t0) / 1_000_000;
    
                    Fila fila = new Fila();
                    fila.nombre = c.nivel.nombre + " " + c.algoritmo;
                    fila.r = r;
                    fila.csv = String.format(Locale.US,
                            "%d,simulacion,%s,%s,%.0f,%.0f,%s,%s,%s,%s,%s,%s,%d,%d,%s,%d,%.0f,%d,%s,%s,%d,%d,%.2f,%.4f,%s,%s,%s,%s,"
                                    + "%.2f,%.0f,%d,%d,%d,%.2f,%d,%d,%d,%.1f,%.1f,%d,%d,%d,%d,%d,%.3f,%d,%d,%d,%d,%d,%d,%s,%d,%.1f,%s,%s,%s,%.1f,%.1f,%d,%.4f,%s,%d",
                            orden, ESCENARIO, c.nivel.nombre, c.nivel.fraccion * 100, cmax, CapacidadFlota.FUENTE,
                            c.situacion.archivoCarga, Paths.get(archivo).getFileName(),
                            base.mapa == null ? "" : Paths.get(bloqueos).getFileName(), averiasCorrida, c.algoritmo,
                            c.replica, c.semilla, modoParada, cfg.taMs, cfg.saMin, cfg.maxEvaluaciones,
                            c.algoritmo.equals("AG") ? (AlgoritmoGenetico.BUSQUEDA_LOCAL ? "si" : "no") : "",
                            cfg.estrategiaParciales, c.situacion.originales, c.situacion.paquetes, r.colapsoMin, r.colapsoMin / 60.0,
                            r.censurada ? "si" : "no", r.pedidoColapso.replace(',', ';'), r.unidadColapso,
                            r.causaColapso.replace(',', ';'), r.costoAcumulado, r.kmAcumulados, r.pedidosLlegados,
                            r.pedidosEntregados, r.pedidosEvaluables, r.pctPedidosEnPlazo(), r.entregasTarde,
                            r.replanificaciones, r.replanPorEvento, r.planificadorMsMedio, r.planificadorMsMax,
                            r.iteracionesTotales, r.evaluacionesTotales, r.aplazamientos, r.cambiosDeUnidad,
                            r.viajesTotales, r.viajesMedio, r.viajesMax, r.bloqueosEncontrados, r.averiasAplicadas,
                            r.trasvases, r.parcialesCreadas, ms, cfg.reglaDestino, r.pedidosInentregablesBloqueo,
                            cfg.penalidadEstabilidad, pc, hashVentas, hashBloqueos, cfg.holguraMin, cfg.penalidadHolgura,
                            DIA_INICIO, (r.colapsoMin - cfg.inicioMin) / 60.0, porReplica ? "por_replica" : "por_nivel",
                            c.situacion.semillaCarga);
                    fila.consola = String.format(Locale.US, "  [%3d/%d] %-5s %-4s rep=%d  %s %s (%.1f h desde el inicio)  causa=%s  costo=%.0f  en plazo=%.1f%%  "
                                    + "replan=%d (+%d por evento)  bloqueos=%d  (%d s)", orden, corridas.size(), c.nivel.nombre,
                            c.algoritmo, c.replica, r.censurada ? "CENSURADA en" : "colapso", Simulador.formatear(r.colapsoMin),
                            (r.colapsoMin - cfg.inicioMin) / 60.0, r.censurada ? "-" : r.causaColapso, r.costoAcumulado, r.pctPedidosEnPlazo(),
                            r.replanificaciones, r.replanPorEvento, r.bloqueosEncontrados, ms / 1000);
                    return fila;
                }));
            }
            // Se escribe cada fila en el orden de la matriz, a medida que se completan
            try {
                for (Future<Fila> f : filas) {
                    Fila fila = f.get();
                    out.println(fila.csv);
                    out.flush();
                    resumen.computeIfAbsent(fila.nombre, k -> new ArrayList<>()).add(fila.r);
                    System.out.println(fila.consola);
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException("falló una corrida del experimento", e);
            } finally {
                hilos.shutdownNow();
            }
        }

        // 5. Resumen descriptivo
        System.out.println("\n=== Resumen por combinación ===");
        System.out.printf("%-11s %3s %12s %10s %14s %10s%n", "Combinación", "n", "colapso (h)", "censuradas",
                "costo medio", "% plazo");
        for (Map.Entry<String, List<Simulador.Resultado>> e : resumen.entrySet()) {
            double h = 0, costo = 0, pct = 0;
            int cens = 0;
            for (Simulador.Resultado r : e.getValue()) {
                h += r.colapsoMin / 60.0; costo += r.costoAcumulado; pct += r.pctPedidosEnPlazo();
                if (r.censurada) cens++;
            }
            int n = e.getValue().size();
            System.out.printf(Locale.US, "%-11s %3d %12.2f %10d %14.0f %9.1f%%%n", e.getKey(), n, h / n, cens, costo / n, pct / n);
        }
        if (sintetico) System.out.println("*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***");
        System.out.println("\nResultados guardados en: " + Paths.get(salida).toAbsolutePath());
    }

    /** Situación a partir de un archivo de pedidos: cuenta pedidos originales y paquetes. */
    private static Situacion situacion(Path ruta, long semilla) throws IOException {
        Situacion s = new Situacion();
        s.semillaCarga = semilla;
        s.ruta = ruta;
        s.archivoCarga = ruta.getFileName().toString();
        Set<String> orig = new LinkedHashSet<>();
        for (Pedido p : s.pedidos()) { orig.add(p.idOriginal); s.paquetes += p.cantidad; }
        s.originales = orig.size();
        return s;
    }

    /** SHA-256 del archivo, en hexadecimal. */
    static String sha256(Path archivo) throws IOException {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivo));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Calibración (19.2): cuántas evaluaciones hace cada algoritmo en Ta en ESTA PC,
     * sobre una instancia representativa del nivel: los pedidos que llegaron el
     * día 2 entre las 08:00 y las 12:00, planificados a las 12:00 con toda la flota
     * en el central, con bloqueos, almacenes y alimentación. Se toma la media de 3
     * corridas por algoritmo. Es una aproximación: el tamaño de la instancia varía
     * durante la simulación. El modo por tiempo sigue siendo el principal del IEN.
     */
    static Map<String, Long> calibrar(String nombreNivel, List<Pedido> pedidosNivel, List<UnidadTransporte> flota,
                                      Simulador.Config base) {
        double instante = 36.0;   // día 2, 12:00
        List<Pedido> instancia = new ArrayList<>();
        for (Pedido p : pedidosNivel)
            if (p.horaRegistro >= 32.0 && p.horaRegistro < instante) instancia.add(p.copiaRelativa(instante));
        Map<String, Long> topes = new LinkedHashMap<>();
        for (String alg : Experimento.ALGORITMOS) {
            long suma = 0;
            for (int rep = 1; rep <= 3; rep++) {
                Contexto cx = new Contexto();
                cx.conEstado = true;
                cx.instanteBaseH = instante;
                cx.mapa = base.mapa;
                cx.alimentacion = base.alimentacion;
                cx.reglaDestino = base.reglaDestino;
                if (base.almacenesIntermedios) {
                    cx.almacenes.add(new Contexto.AlmacenPlan(Compartido.ALMACEN_NOROESTE, Compartido.STOCK_INTERMEDIO));
                    cx.almacenes.add(new Contexto.AlmacenPlan(Compartido.ALMACEN_ESTE, Compartido.STOCK_INTERMEDIO));
                }
                Planificador p = alg.equals("TABU") ? Planificador.tabu(Experimento.TABU_DURACION)
                        : Planificador.genetico(Experimento.AG_POBLACION);
                suma += p.planificar(new Planificador.EstadoPlanificacion(cx, new ArrayList<>(instancia), flota,
                        base.taMs, 0, rep)).evaluaciones;
                Contexto.restablecer();
            }
            topes.put(alg, Math.max(1, suma / 3));
        }
        System.out.println("Calibración sobre " + instancia.size() + " entregas (día 2, 08:00-12:00, nivel "
                + nombreNivel + ")");
        return topes;
    }

    private static Simulador.Config copiar(Simulador.Config b) {
        Simulador.Config c = new Simulador.Config();
        c.escenario = b.escenario;
        c.saMin = b.saMin;
        c.taMs = b.taMs;
        c.maxEvaluaciones = b.maxEvaluaciones;
        c.horizonteMin = b.horizonteMin;
        c.detenerEnColapso = b.detenerEnColapso;
        c.mapa = b.mapa;
        c.almacenesIntermedios = b.almacenesIntermedios;
        c.alimentacion = b.alimentacion;
        c.estrategiaParciales = b.estrategiaParciales;
        c.reglaDestino = b.reglaDestino;
        c.penalidadEstabilidad = b.penalidadEstabilidad;
        c.holguraMin = b.holguraMin;
        c.inicioMin = b.inicioMin;
        c.penalidadHolgura = b.penalidadHolgura;
        c.umbralUrgenciaH = b.umbralUrgenciaH;
        c.tamanoParcial = b.tamanoParcial;
        c.averias = b.averias;
        c.mantenimientos = b.mantenimientos;
        return c;
    }
}
