package pe.edu.pucp.gamesoft.paqrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Avería de una unidad (Etapa 12; P3 de la hoja de preguntas).
 *
 * Formato PROVISIONAL del archivo, POR CONFIRMAR CON EL PROFESOR (las averías
 * se registrarán desde el visualizador y las reglas de generación aún no están
 * publicadas): una por línea,  ##d##h##m:TTNN:tipo   (ej. 03d10h15m:TA04:1)
 *
 * Reglas por tipo (tiempos en minutos absolutos):
 *  - Tipo 1: no disponible 2 h, en el lugar; luego sigue con su carga.
 *  - Tipo 2: no disponible hasta el final del turno siguiente (turnos 07, 15 y
 *    23 h); permanece en el lugar como máximo 4 h y luego pasa al central.
 *  - Tipo 3: no disponible al menos 2 días; vuelve a operar en el turno de
 *    15:00 a 23:00; permanece en el lugar 4 h y luego pasa al central.
 * Mientras está en el lugar es un almacén temporal (TRASVASE); los paquetes
 * no trasvasados viajan con la unidad al central y vuelven a su stock.
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-05 (trasvase 30 min), SI-09 (se detiene en el último nodo; formato provisional), SI-18 (avería externa en el instante actual).
 */
final class Averia {
    private static final Pattern REGISTRO = Pattern.compile("(\\d+)d(\\d+)h(\\d+)m:(\\w+):([123])");

    final double tiempoMin;
    final String unidad;
    final int tipo;

    Averia(double tiempoMin, String unidad, int tipo) {
        this.tiempoMin = tiempoMin;
        this.unidad = unidad;
        this.tipo = tipo;
    }

    /** Minuto en que la unidad deja el lugar (pasa al central o sigue, si es tipo 1). */
    double enLugarHastaMin() {
        return tipo == 1 ? tiempoMin + 120 : Math.min(tiempoMin + 240, disponibleDesdeMin());
    }

    /** Minuto desde el que la unidad vuelve a estar disponible. */
    double disponibleDesdeMin() {
        if (tipo == 1) return tiempoMin + 120;
        if (tipo == 2) {
            double inicioTurno = Math.floor((tiempoMin - 420) / 480.0) * 480 + 420;   // turnos 07, 15, 23 h
            return inicioTurno + 960;   // fin del turno siguiente
        }
        // tipo 3: primer inicio del turno de 15:00 al menos 2 días después
        double minimo = tiempoMin + 2 * 1440;
        double dia = Math.floor(minimo / 1440.0) * 1440;
        double t = dia + 900;
        return t >= minimo ? t : t + 1440;
    }

    static List<Averia> leer(String archivo) throws IOException {
        List<Averia> lista = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(archivo), StandardCharsets.UTF_8)) {
            String linea;
            int nro = 0;
            while ((linea = br.readLine()) != null) {
                nro++;
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                Matcher m = REGISTRO.matcher(linea);
                if (!m.matches()) {
                    System.err.println("  Avería en línea " + nro + " con formato inválido, se omite: " + linea);
                    continue;
                }
                double t = LectorPedidos.hora(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))) * 60;
                lista.add(new Averia(t, m.group(4), Integer.parseInt(m.group(5))));
            }
        }
        lista.sort(Comparator.comparingDouble(a -> a.tiempoMin));
        return lista;
    }

    /**
     * Generador SINTÉTICO con semilla (las reglas oficiales aún no están publicadas):
     * averías por día ~ uniforme entre 0 y 2·media, hora uniforme, unidad al azar
     * de la flota y tipo con probabilidades p1, p2, p3.
     */
    static void generar(List<UnidadTransporte> flota, int dias, double mediaPorDia, double[] probTipo,
                        long semilla, Path destino) throws IOException {
        Random r = new Random(semilla);
        List<String> lineas = new ArrayList<>();
        for (int d = 0; d < dias; d++) {
            int n = (int) Math.round(r.nextDouble() * 2 * mediaPorDia);
            for (int k = 0; k < n; k++) {
                int minuto = d * 1440 + r.nextInt(1440);
                UnidadTransporte u = flota.get(r.nextInt(flota.size()));
                double x = r.nextDouble();
                int tipo = x < probTipo[0] ? 1 : x < probTipo[0] + probTipo[1] ? 2 : 3;
                lineas.add(String.format(Locale.US, "%02dd%02dh%02dm:%s:%d", minuto / 1440 + 1,
                        (minuto % 1440) / 60, minuto % 60, u.codigo, tipo));
            }
        }
        lineas.sort(Comparator.naturalOrder());
        Files.createDirectories(destino.toAbsolutePath().getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(destino, StandardCharsets.UTF_8))) {
            for (String l : lineas) out.println(l);
        }
    }
}
