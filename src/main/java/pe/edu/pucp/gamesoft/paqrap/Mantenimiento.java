package pe.edu.pucp.gamesoft.paqrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mantenimiento preventivo (Etapa 12.5). Archivo mant.preventivo.m1.m2, un
 * registro por línea:  aaaammdd:TTNN  -> la unidad no está disponible ese día
 * de 00:00 a 23:59 (en el central). Solo se usa el día (dd) dentro del mes
 * simulado. La duración por tipo aún está pendiente en la hoja del profesor:
 * parámetro mantenimiento.horas.TIPO (24 h por defecto, POR CONFIRMAR).
 * El planificador no asigna rutas que terminen después del inicio del
 * mantenimiento (Contexto.Inicio.noDisponibleDesdeH).
 */
final class Mantenimiento {
    private static final Pattern REGISTRO = Pattern.compile("(\\d{4})(\\d{2})(\\d{2}):(\\w+)");

    final String unidad;
    final double inicioMin, finMin;

    Mantenimiento(String unidad, double inicioMin, double finMin) {
        this.unidad = unidad;
        this.inicioMin = inicioMin;
        this.finMin = finMin;
    }

    static double horasPorTipo(String codigoUnidad) {
        String tipo = codigoUnidad.startsWith("TA") ? "AUTO" : codigoUnidad.startsWith("TM") ? "MOTO" : "BICICLETA";
        return Parametros.decimal("mantenimiento.horas." + tipo, 24);
    }

    static List<Mantenimiento> leer(String archivo) throws IOException {
        List<Mantenimiento> lista = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(archivo), StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                Matcher m = REGISTRO.matcher(linea);
                if (!m.matches()) {
                    System.err.println("  Mantenimiento con formato inválido, se omite: " + linea);
                    continue;
                }
                int dia = Integer.parseInt(m.group(3));
                String unidad = m.group(4);
                double inicio = (dia - 1) * 1440.0;
                lista.add(new Mantenimiento(unidad, inicio, inicio + horasPorTipo(unidad) * 60));
            }
        }
        return lista;
    }
}
