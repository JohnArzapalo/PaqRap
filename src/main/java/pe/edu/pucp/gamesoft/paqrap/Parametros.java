package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Parámetros configurables del planificador (R13 y semáforo, RNF d).
 *
 * Se leen una sola vez de config/parametros.properties (ruta relativa a la
 * carpeta desde donde se ejecuta, que en NetBeans es la carpeta del
 * proyecto). Se puede indicar otra ruta con -Dpaqrap.config=ruta.
 * Si el archivo no existe o falta una clave, se usa el valor por defecto
 * (los del enunciado de la situación auténtica).
 *
 * Las capacidades y los costos por km NO son configurables: son datos del caso.
 */
final class Parametros {

    static final String RUTA_POR_DEFECTO = "config/parametros.properties";

    private static final Properties PROPS = cargar();

    private Parametros() {
    }

    private static Properties cargar() {
        Properties p = new Properties();
        Path ruta = Paths.get(System.getProperty("paqrap.config", RUTA_POR_DEFECTO));
        if (Files.exists(ruta)) {
            try (Reader r = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                System.err.println("[config] No se pudo leer " + ruta.toAbsolutePath()
                        + "; se usan los valores del enunciado. " + e.getMessage());
            }
        } else {
            System.err.println("[config] No existe " + ruta.toAbsolutePath()
                    + "; se usan los valores del enunciado.");
        }
        return p;
    }

    static String texto(String clave, String porDefecto) {
        String v = PROPS.getProperty(clave);
        return v == null || v.isBlank() ? porDefecto : v.trim();
    }

    static double decimal(String clave, double porDefecto) {
        String v = PROPS.getProperty(clave);
        if (v == null || v.isBlank()) return porDefecto;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            System.err.println("[config] Valor inválido para " + clave + ": '" + v + "'; se usa " + porDefecto);
            return porDefecto;
        }
    }

    static long entero(String clave, long porDefecto) {
        String v = PROPS.getProperty(clave);
        if (v == null || v.isBlank()) return porDefecto;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            System.err.println("[config] Valor inválido para " + clave + ": '" + v + "'; se usa " + porDefecto);
            return porDefecto;
        }
    }
}
