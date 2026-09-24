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

    private ExperimentoSimulacion() {
    }

    private static class Nivel {
        String nombre;
        double fraccion;
        long semillaCarga;
        String archivoCarga;
        List<Pedido> pedidos;
        int originales, paquetes;
    }

    private static class Corrida {
        Nivel nivel;
        String algoritmo;
        int replica;
        long semilla;
    }

    static void ejecutar() throws IOException {
        String archivo = Experimento.ARCHIVO_EXPLICITO ? Experimento.ARCHIVO_VENTAS : ARCHIVO_MES;
        String bloqueos = ARCHIVO_BLOQUEOS != null ? ARCHIVO_BLOQUEOS
                : Files.exists(Paths.get(BLOQUEOS_MES)) ? BLOQUEOS_MES : "no";
        boolean sintetico = (archivo + bloqueos).toUpperCase(Locale.ROOT).contains("SINTETICO");
        List<UnidadTransporte> flota = Experimento.flotaOficial();
        Simulador.Config base = new Simulador.Config();
        base.escenario = ESCENARIO;
        if (Experimento.TA_EXPLICITO) base.taMs = Experimento.TA_MS;
        if (Experimento.SA_MIN != null) base.saMin = Experimento.SA_MIN;
        base.maxEvaluaciones = Experimento.MAX_EVALUACIONES;
        base.detenerEnColapso = !"no".equalsIgnoreCase(Parametros.texto("sim5d.detener_en_colapso", "si"));
        if (PARCIALES != null) base.estrategiaParciales = PARCIALES;
        base.mapa = "no".equalsIgnoreCase(bloqueos) || "no".equalsIgnoreCase(Parametros.texto("red.bloqueos", "si"))
                ? null : MapaVial.leer(bloqueos);
        if (ARCHIVO_MANTENIMIENTO != null) base.mantenimientos = Mantenimiento.leer(ARCHIVO_MANTENIMIENTO);
        String salida = Experimento.SALIDA_CSV != null ? Experimento.SALIDA_CSV
                : "resultados_simulacion" + (sintetico ? "_SINTETICO" : "") + ".csv";
        String modoParada = base.maxEvaluaciones > 0 ? "evaluaciones" : "tiempo";

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
        List<Nivel> niveles = new ArrayList<>();
        System.out.println("\n=== Niveles de carga (" + dias + " días; archivos en " + carpeta + ") ===");
        for (int k = 0; k < NIVELES.length; k++) {
            if (Experimento.FILTRO_NIVELES != null && !Experimento.FILTRO_NIVELES.contains(NIVELES[k])) continue;
            Nivel n = new Nivel();
            n.nombre = NIVELES[k];
            n.fraccion = Parametros.decimal("carga.nivel." + n.nombre, FRACCION_POR_DEFECTO[k]);
            n.semillaCarga = semillaBase + k + 1;
            Path destino = carpeta.resolve(GeneradorCarga.nombreArchivo(n.nombre, n.fraccion, n.semillaCarga, archivo));
            GeneradorCarga.generar(registros, n.fraccion * cmax, dias, n.semillaCarga, destino);
            n.archivoCarga = destino.getFileName().toString();
            n.pedidos = LectorPedidos.leerAbsoluto(destino.toString());
            Set<String> orig = new LinkedHashSet<>();
            for (Pedido p : n.pedidos) { orig.add(p.idOriginal); n.paquetes += p.cantidad; }
            n.originales = orig.size();
            niveles.add(n);
            System.out.printf(Locale.US, "  %-5s %3.0f%% de C_max = %.0f paquetes/día -> %d pedidos, %d paquetes en %d días (%s)%n",
                    n.nombre, n.fraccion * 100, n.fraccion * cmax, n.originales, n.paquetes, dias, n.archivoCarga);
        }

        // 3. Matriz de corridas, semillas registradas y orden aleatorio
        List<Corrida> corridas = new ArrayList<>();
        for (Nivel n : niveles)
            for (String alg : Experimento.ALGORITMOS) {
                if (Experimento.FILTRO_ALGORITMOS != null && !Experimento.FILTRO_ALGORITMOS.contains(alg)) continue;
                for (int r = 1; r <= Experimento.REPLICAS; r++) {
                    Corrida c = new Corrida();
                    c.nivel = n; c.algoritmo = alg; c.replica = r; c.semilla = Experimento.SEMILLA_BASE + r;
                    corridas.add(c);
                }
            }
        Collections.shuffle(corridas, new Random(Experimento.SEMILLA_ORDEN));
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
                    + "bloqueos_encontrados,averias_aplicadas,trasvases,parciales_creadas,tiempo_real_ms");
            int orden = 0;
            for (Corrida c : corridas) {
                orden++;
                Simulador.Config cfg = copiar(base);
                cfg.semilla = c.semilla;
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
                    r = Simulador.simular(c.nivel.pedidos, flota, plan, cfg);
                } finally {
                    if (eventos != null) eventos.close();
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;

                out.println(String.format(Locale.US,
                        "%d,simulacion,%s,%s,%.0f,%.0f,%s,%s,%s,%s,%s,%s,%d,%d,%s,%d,%.0f,%d,%s,%s,%d,%d,%.2f,%.4f,%s,%s,%s,%s,"
                                + "%.2f,%.0f,%d,%d,%d,%.2f,%d,%d,%d,%.1f,%.1f,%d,%d,%d,%d,%d,%.3f,%d,%d,%d,%d,%d,%d",
                        orden, ESCENARIO, c.nivel.nombre, c.nivel.fraccion * 100, cmax, CapacidadFlota.FUENTE,
                        c.nivel.archivoCarga, Paths.get(archivo).getFileName(),
                        base.mapa == null ? "" : Paths.get(bloqueos).getFileName(), archivoAverias, c.algoritmo,
                        c.replica, c.semilla, modoParada, cfg.taMs, cfg.saMin, cfg.maxEvaluaciones,
                        c.algoritmo.equals("AG") ? (AlgoritmoGenetico.BUSQUEDA_LOCAL ? "si" : "no") : "",
                        cfg.estrategiaParciales, c.nivel.originales, c.nivel.paquetes, r.colapsoMin, r.colapsoMin / 60.0,
                        r.censurada ? "si" : "no", r.pedidoColapso.replace(',', ';'), r.unidadColapso,
                        r.causaColapso.replace(',', ';'), r.costoAcumulado, r.kmAcumulados, r.pedidosLlegados,
                        r.pedidosEntregados, r.pedidosEvaluables, r.pctPedidosEnPlazo(), r.entregasTarde,
                        r.replanificaciones, r.replanPorEvento, r.planificadorMsMedio, r.planificadorMsMax,
                        r.iteracionesTotales, r.evaluacionesTotales, r.aplazamientos, r.cambiosDeUnidad,
                        r.viajesTotales, r.viajesMedio, r.viajesMax, r.bloqueosEncontrados, r.averiasAplicadas,
                        r.trasvases, r.parcialesCreadas, ms));
                out.flush();
                resumen.computeIfAbsent(c.nivel.nombre + " " + c.algoritmo, k -> new ArrayList<>()).add(r);
                System.out.printf(Locale.US, "  [%3d/%d] %-5s %-4s rep=%d  %s %s (%.1f h)  causa=%s  costo=%.0f  en plazo=%.1f%%  "
                                + "replan=%d (+%d por evento)  bloqueos=%d  (%d s)%n", orden, corridas.size(), c.nivel.nombre,
                        c.algoritmo, c.replica, r.censurada ? "CENSURADA en" : "colapso", Simulador.formatear(r.colapsoMin),
                        r.colapsoMin / 60.0, r.censurada ? "-" : r.causaColapso, r.costoAcumulado, r.pctPedidosEnPlazo(),
                        r.replanificaciones, r.replanPorEvento, r.bloqueosEncontrados, ms / 1000);
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
        c.umbralUrgenciaH = b.umbralUrgenciaH;
        c.tamanoParcial = b.tamanoParcial;
        c.averias = b.averias;
        c.mantenimientos = b.mantenimientos;
        return c;
    }
}
