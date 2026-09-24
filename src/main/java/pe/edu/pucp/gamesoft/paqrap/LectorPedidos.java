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
 * Lector del archivo mensual de pedidos (ventas2026mm), pregunta 8:
 *   ##d##h##m:posX,posY,cIdCliente,qq,hl      ej. 11d13h31m:45,43,c9167,12,36
 *
 * Arma una instancia con los pedidos que llegaron dentro de una ventana de
 * tiempo. La instancia representa una llamada al planificador al FINAL de
 * la ventana: ese instante es la hora 0 de la instancia, de modo que la hora
 * de registro de cada pedido queda negativa (el pedido ya esperó) y su hora
 * límite es registro + hl.
 */
class LectorPedidos {

    private static final Pattern REGISTRO =
            Pattern.compile("(\\d+)d(\\d+)h(\\d+)m:(\\d+),(\\d+),(\\w+),(\\d+),(\\d+)");

    /** Capacidad del vehículo más grande: los pedidos mayores se dividen en
     *  entregas parciales (preguntas 13 y 14). */
    static final int MAX_PAQUETES_POR_ENTREGA = 24;

    /** Horas transcurridas desde el día 1 a las 00:00 del mes. */
    static double hora(int dia, int hora, int minuto) {
        return (dia - 1) * 24.0 + hora + minuto / 60.0;
    }

    /**
     * @param archivo      ruta del archivo ventas2026mm
     * @param horaInicio   inicio de la ventana, en horas desde el día 1 00:00 (usar hora(...))
     * @param horasVentana duración de la ventana en horas
     */
    static List<Pedido> leerVentana(String archivo, double horaInicio, double horasVentana) throws IOException {
        double horaPlanificacion = horaInicio + horasVentana;
        List<Pedido> pedidos = new ArrayList<>();
        int nroLinea = 0;
        try (BufferedReader br = Files.newBufferedReader(Paths.get(archivo), StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = br.readLine()) != null) {
                nroLinea++;
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                Matcher m = REGISTRO.matcher(linea);
                if (!m.matches()) {
                    System.err.println("  Línea " + nroLinea + " con formato inválido, se omite: " + linea);
                    continue;
                }
                double llegada = hora(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                                      Integer.parseInt(m.group(3)));
                if (llegada < horaInicio || llegada >= horaPlanificacion) continue;

                int x = Integer.parseInt(m.group(4));
                int y = Integer.parseInt(m.group(5));
                String cliente = m.group(6);
                int cantidad = Integer.parseInt(m.group(7));
                int hl = Integer.parseInt(m.group(8));
                double registro = llegada - horaPlanificacion;   // negativo: ya esperó hasta planificar

                // Entregas parciales: pedidos mayores a 24 paquetes se dividen
                int partes = (cantidad + MAX_PAQUETES_POR_ENTREGA - 1) / MAX_PAQUETES_POR_ENTREGA;
                int restante = cantidad;
                for (int k = 1; k <= partes; k++) {
                    int q = Math.min(restante, MAX_PAQUETES_POR_ENTREGA);
                    restante -= q;
                    String id = cliente + "-L" + nroLinea + (partes > 1 ? "-" + k : "");
                    pedidos.add(new Pedido(id, x, y, q, hl, registro));
                }
            }
        }
        return pedidos;
    }
}