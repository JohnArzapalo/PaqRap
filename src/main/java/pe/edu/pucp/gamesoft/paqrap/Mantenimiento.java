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
 * de 00:00 a 23:59 (en el central). Se usan los registros del mes simulado (etapa 28:
 * el archivo oficial trae dos meses) y el día (dd) dentro de ese mes. La duración por
 * tipo aún está pendiente en la hoja del profesor:
 * parámetro mantenimiento.horas.TIPO (24 h por defecto, POR CONFIRMAR).
 * El planificador no asigna rutas que terminen después del inicio del
 * mantenimiento (Contexto.Inicio.noDisponibleDesdeH).
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-10 (24 h por defecto, en el central; las rutas terminan antes).
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

    /** Todos los registros del archivo, sin mirar el mes (solo para archivos de un mes). */
    static List<Mantenimiento> leer(String archivo) throws IOException {
        return leer(archivo, -1);
    }

    /**
     * Etapa 28: solo los registros del mes aaaamm (p. ej. 202609). El archivo oficial
     * mant.preventivo.09.10 trae septiembre Y octubre; sin este filtro, al simular septiembre
     * también se aplicaban los días de octubre (20261003:TB10 dejaba a TB10 fuera el 3 de
     * septiembre). Con aaaamm <= 0 se leen todos.
     */
    static List<Mantenimiento> leer(String archivo, int aaaamm) throws IOException {
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
                int mes = Integer.parseInt(m.group(1)) * 100 + Integer.parseInt(m.group(2));
                if (aaaamm > 0 && mes != aaaamm) continue;   // otro mes del archivo
                int dia = Integer.parseInt(m.group(3));
                String unidad = m.group(4);
                double inicio = (dia - 1) * 1440.0;
                lista.add(new Mantenimiento(unidad, inicio, inicio + horasPorTipo(unidad) * 60));
            }
        }
        return lista;
    }

    private static final Pattern MES_EN_NOMBRE = Pattern.compile("(20\\d{2})(0[1-9]|1[0-2])");

    /** Mes aaaamm que aparece en el nombre de un archivo de ventas (ventas.202609.txt,
     *  ventas202609_SINTETICO_MES.txt), o -1 si no aparece. */
    static int mesDeArchivo(String nombre) {
        Matcher m = MES_EN_NOMBRE.matcher(Paths.get(nombre).getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) * 100 + Integer.parseInt(m.group(2)) : -1;
    }
}
