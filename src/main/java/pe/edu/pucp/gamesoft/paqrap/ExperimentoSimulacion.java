package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Modo SIMULACIÓN del experimento (diseño del IEN): algoritmo (TABU, AG) ×
 * carga (BAJA, MEDIA, ALTA) × réplicas. Cada corrida simula hasta el colapso
 * o hasta el horizonte (censurada). Usa los argumentos y filtros de
 * Experimento (--algoritmos, --niveles, --replicas, --ta, --sa,
 * --max-evaluaciones, --salida, --archivo).
 *
 * Niveles de carga: fracción de C_max por día (CapacidadFlota), con pedidos
 * generados por GeneradorCarga con semilla fija por nivel: ambos algoritmos
 * y todas las réplicas de un nivel reciben EXACTAMENTE los mismos pedidos.
 * Las réplicas solo cambian la semilla del algoritmo.
 */
final class ExperimentoSimulacion {

    static final String[] NIVELES = {"BAJA", "MEDIA", "ALTA"};
    private static final double[] FRACCION_POR_DEFECTO = {0.30, 0.60, 0.90};

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
        String archivo = Experimento.ARCHIVO_VENTAS;
        boolean sintetico = archivo.toUpperCase(Locale.ROOT).contains("SINTETICO");
        Simulador.Config base = new Simulador.Config();
        if (Experimento.TA_EXPLICITO) base.taMs = Experimento.TA_MS;
        if (Experimento.SA_MIN != null) base.saMin = Experimento.SA_MIN;
        base.maxEvaluaciones = Experimento.MAX_EVALUACIONES;
        String salida = Experimento.SALIDA_CSV != null ? Experimento.SALIDA_CSV
                : "resultados_simulacion" + (sintetico ? "_SINTETICO" : "") + ".csv";
        String modoParada = base.maxEvaluaciones > 0 ? "evaluaciones" : "tiempo";
        List<UnidadTransporte> flota = Experimento.flotaOficial();

        if (sintetico) System.out.println("*** DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME ***");
        System.out.println("=== Modo simulación (archivo base: " + archivo + ") ===");
        System.out.printf(Locale.US, "Sa = %.0f min, Ta = %d ms%s, horizonte = %.0f min; AG con búsqueda local: %s%n",
                base.saMin, base.taMs, base.maxEvaluaciones > 0 ? " (ignorado: " + base.maxEvaluaciones
                        + " evaluaciones por llamada)" : "", base.horizonteMin,
                AlgoritmoGenetico.BUSQUEDA_LOCAL ? "sí" : "no");

        // 1. Capacidad teórica diaria (C_max)
        List<LectorPedidos.Registro> registros = LectorPedidos.leerRegistros(archivo);
        CapacidadFlota.Resultado cap = CapacidadFlota.calcular(LectorPedidos.leerAbsoluto(archivo), flota);
        System.out.println("\n=== Capacidad teórica diaria de la flota ===");
        System.out.print(cap.reporte());
        double cmax = cap.cmaxElegido();

        // 2. Conjuntos de pedidos por nivel (semilla fija por nivel)
        int dias = (int) Parametros.entero("carga.dias", 5);
        long semillaBase = Parametros.entero("carga.semilla_base", 2026);
        Path carpeta = Paths.get(archivo).toAbsolutePath().getParent().resolve("generados");
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
            System.out.printf(Locale.US, "  %-5s %3.0f%% de C_max = %.0f paquetes/día -> %d pedidos, %d entregas, "
                            + "%d paquetes en %d días (%s)%n", n.nombre, n.fraccion * 100, n.fraccion * cmax,
                    n.originales, n.pedidos.size(), n.paquetes, dias, n.archivoCarga);
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
        int ciclosMax = (int) Math.ceil(base.horizonteMin / base.saMin) + 1;
        if (base.maxEvaluaciones == 0) {
            double peorCasoMin = corridas.size() * (double) ciclosMax * base.taMs / 60000.0;
            System.out.printf(Locale.US, "%n=== Ejecutando %d corridas; peor caso %.0f min (hasta %d replanificaciones "
                    + "de %d ms por corrida; menos si colapsa antes) ===%n", corridas.size(), peorCasoMin, ciclosMax, base.taMs);
        } else {
            System.out.printf("%n=== Ejecutando %d corridas (parada por evaluaciones) ===%n", corridas.size());
        }

        // 4. Ejecución y registro
        Map<String, List<Simulador.Resultado>> resumen = new LinkedHashMap<>();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(salida), StandardCharsets.UTF_8))) {
            out.println("orden,modo,nivel,pct_cmax,cmax_paquetes_dia,fuente_cmax,archivo_carga,archivo_ventas,algoritmo,"
                    + "replica,semilla,modo_parada,ta_ms,sa_min,max_evaluaciones,busqueda_local,pedidos_originales_total,"
                    + "paquetes_total,colapso_min,colapso_h,censurada,pedido_colapso,costo_acumulado,km_acumulados,"
                    + "pedidos_llegados,pedidos_entregados,pedidos_evaluables,pct_pedidos_en_plazo,replanificaciones,"
                    + "planificador_ms_medio,planificador_ms_max,iteraciones_totales,evaluaciones_totales,aplazamientos,"
                    + "viajes_totales,viajes_por_vehiculo_medio,viajes_por_vehiculo_max,tiempo_real_ms");
            int orden = 0;
            for (Corrida c : corridas) {
                orden++;
                Simulador.Config cfg = new Simulador.Config();
                cfg.saMin = base.saMin;
                cfg.horizonteMin = base.horizonteMin;
                cfg.taMs = base.taMs;
                cfg.maxEvaluaciones = base.maxEvaluaciones;
                cfg.semilla = c.semilla;
                Planificador plan = c.algoritmo.equals("TABU")
                        ? Planificador.tabu(Experimento.TABU_DURACION)
                        : Planificador.genetico(Experimento.AG_POBLACION);
                long t0 = System.nanoTime();
                Simulador.Resultado r = Simulador.simular(c.nivel.pedidos, flota, plan, cfg);
                long ms = (System.nanoTime() - t0) / 1_000_000;

                out.println(String.format(Locale.US,
                        "%d,simulacion,%s,%.0f,%.0f,%s,%s,%s,%s,%d,%d,%s,%d,%.0f,%d,%s,%d,%d,%.2f,%.4f,%s,%s,%.2f,%.0f,"
                                + "%d,%d,%d,%.2f,%d,%.1f,%.1f,%d,%d,%d,%d,%.3f,%d,%d",
                        orden, c.nivel.nombre, c.nivel.fraccion * 100, cmax, CapacidadFlota.FUENTE, c.nivel.archivoCarga,
                        Paths.get(archivo).getFileName(), c.algoritmo, c.replica, c.semilla, modoParada, cfg.taMs,
                        cfg.saMin, cfg.maxEvaluaciones,
                        c.algoritmo.equals("AG") ? (AlgoritmoGenetico.BUSQUEDA_LOCAL ? "si" : "no") : "",
                        c.nivel.originales, c.nivel.paquetes, r.colapsoMin, r.colapsoMin / 60.0,
                        r.censurada ? "si" : "no", r.pedidoColapso == null ? "" : r.pedidoColapso,
                        r.costoAcumulado, r.kmAcumulados, r.pedidosLlegados, r.pedidosEntregados, r.pedidosEvaluables,
                        r.pctPedidosEnPlazo(), r.replanificaciones, r.planificadorMsMedio, r.planificadorMsMax,
                        r.iteracionesTotales, r.evaluacionesTotales, r.aplazamientos, r.viajesTotales, r.viajesMedio,
                        r.viajesMax, ms));
                out.flush();
                resumen.computeIfAbsent(c.nivel.nombre + " " + c.algoritmo, k -> new ArrayList<>()).add(r);
                System.out.printf(Locale.US, "  [%3d/%d] %-5s %-4s rep=%d  %s %.2f h  costo=%.0f  en plazo=%.1f%%  "
                                + "replan=%d  viajes=%d  (%d ms)%n", orden, corridas.size(), c.nivel.nombre, c.algoritmo,
                        c.replica, r.censurada ? "CENSURADA en" : "colapso a las", r.colapsoMin / 60.0, r.costoAcumulado,
                        r.pctPedidosEnPlazo(), r.replanificaciones, r.viajesTotales, ms);
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
}
