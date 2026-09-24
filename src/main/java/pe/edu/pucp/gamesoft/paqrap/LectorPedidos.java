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
 * tiempo [inicio, inicio + duración).
 *
 * PLANIFICACIÓN AL FINAL DE LA VENTANA: la instancia representa una llamada
 * al planificador en el instante inicio + duración, cuando ya se conocen
 * todos los pedidos de la ventana (así funcionaría un planificador por lotes
 * que se ejecuta cada Sa). Ese instante es la hora 0 de la instancia, de modo
 * que la hora de registro de cada pedido queda negativa (el pedido ya esperó)
 * y su hora límite es registro + hl. Consecuencia: un pedido con plazo corto
 * registrado al inicio de la ventana puede estar ya vencido al planificar;
 * Compartido.vencidoAlPlanificar lo detecta para no atribuirlo al algoritmo.
 *
 * Cada línea es un pedido ORIGINAL (idOriginal = cliente-L<línea>). Si pasa
 * de 24 paquetes se divide en entregas parciales (id = idOriginal-1, -2, ...).
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

    /** Una línea válida del archivo (un pedido original). */
    static class Registro {
        int nroLinea;
        double llegadaHoras;   // horas desde el día 1 a las 00:00
        int x, y;
        String cliente;
        int cantidad;
        int hl;

        String idOriginal() {
            return cliente + "-L" + nroLinea;
        }

        /** Línea en el formato del archivo ventas2026mm (para escribir conjuntos generados). */
        String aLinea() {
            int minutos = (int) Math.round(llegadaHoras * 60);
            return String.format("%02dd%02dh%02dm:%d,%d,%s,%d,%d", minutos / 1440 + 1, (minutos % 1440) / 60,
                    minutos % 60, x, y, cliente, cantidad, hl);
        }
    }

    /** Lee todas las líneas válidas del archivo; omite (y avisa) las inválidas. */
    static List<Registro> leerRegistros(String archivo) throws IOException {
        List<Registro> registros = new ArrayList<>();
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
                Registro r = new Registro();
                r.nroLinea = nroLinea;
                r.llegadaHoras = hora(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                                      Integer.parseInt(m.group(3)));
                r.x = Integer.parseInt(m.group(4));
                r.y = Integer.parseInt(m.group(5));
                r.cliente = m.group(6);
                r.cantidad = Integer.parseInt(m.group(7));
                r.hl = Integer.parseInt(m.group(8));
                registros.add(r);
            }
        }
        return registros;
    }

    /** Entregas de un pedido original con la hora de registro indicada. Los
     *  pedidos mayores a 24 paquetes se dividen en entregas parciales. */
    private static List<Pedido> entregas(Registro r, double horaRegistro) {
        List<Pedido> pedidos = new ArrayList<>();
        int partes = (r.cantidad + MAX_PAQUETES_POR_ENTREGA - 1) / MAX_PAQUETES_POR_ENTREGA;
        int restante = r.cantidad;
        String idOriginal = r.idOriginal();
        for (int k = 1; k <= partes; k++) {
            int q = Math.min(restante, MAX_PAQUETES_POR_ENTREGA);
            restante -= q;
            String id = idOriginal + (partes > 1 ? "-" + k : "");
            pedidos.add(new Pedido(id, idOriginal, r.x, r.y, q, r.hl, horaRegistro));
        }
        return pedidos;
    }

    /**
     * Instancia estática: pedidos de la ventana, con la hora 0 en el instante
     * de planificación (fin de la ventana).
     * @param archivo      ruta del archivo ventas2026mm
     * @param horaInicio   inicio de la ventana, en horas desde el día 1 00:00 (usar hora(...))
     * @param horasVentana duración de la ventana en horas
     */
    static List<Pedido> leerVentana(String archivo, double horaInicio, double horasVentana) throws IOException {
        double horaPlanificacion = horaInicio + horasVentana;
        List<Pedido> pedidos = new ArrayList<>();
        for (Registro r : leerRegistros(archivo)) {
            if (r.llegadaHoras < horaInicio || r.llegadaHoras >= horaPlanificacion) continue;
            // negativo: ya esperó hasta planificar
            pedidos.addAll(entregas(r, r.llegadaHoras - horaPlanificacion));
        }
        return pedidos;
    }

    /**
     * Simulación: todas las entregas del archivo con la hora de registro
     * ABSOLUTA (horas desde el día 1 a las 00:00). El simulador las convierte
     * a horas relativas al instante de cada replanificación.
     */
    static List<Pedido> leerAbsoluto(String archivo) throws IOException {
        List<Pedido> pedidos = new ArrayList<>();
        for (Registro r : leerRegistros(archivo)) pedidos.addAll(entregas(r, r.llegadaHoras));
        return pedidos;
    }
}