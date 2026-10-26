package pe.edu.pucp.gamesoft.paqrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Lectura incremental de los archivos oficiales de ventas (Etapa 35, A22; SI-33).
 *
 * Abre el archivo ventas.aaaamm.txt del mes de inicio y lee UNA línea por vez, solo cuando el
 * simulador pide los pedidos hasta cierto minuto. Guarda leída por adelantado una sola línea (para
 * saber cuándo llega el siguiente pedido). Al terminar el mes, sigue con el del mes siguiente.
 *
 * Da exactamente los mismos pedidos que DatosOficiales.cargar (prueba en LecturaIncrementalTest):
 *  - los tiempos se cuentan desde el día 1 a las 00:00 del PRIMER mes (los del mes k se desplazan
 *    en los días de los meses anteriores);
 *  - los ids de los meses siguientes llevan el mes (p. ej. 202611/c1234-L5);
 *  - se omiten los pedidos registrados antes del inicio de la corrida;
 *  - se leen los meses que cubren el horizonte.
 * Los archivos están ordenados por hora de registro; si una línea viniera fuera de orden, se avisa.
 */
final class LectorVentasIncremental implements FuentePedidos {

    private final double desdeMin, finMin;
    private YearMonth mes;
    private double offsetMin = 0;
    private String prefijo = "";
    private BufferedReader lector;
    private int nroLinea;
    private double ultimaHora = Double.NEGATIVE_INFINITY;
    /** Entregas de la última línea leída que todavía no se entregaron al simulador. */
    private final ArrayDeque<Pedido> leidos = new ArrayDeque<>();
    /** Líneas leídas en total (para las pruebas y el registro). */
    int lineasLeidas;

    /**
     * @param primero  mes del día 1 a las 00:00 (minuto 0)
     * @param desdeMin minuto de inicio de la corrida
     * @param finMin   minuto final del horizonte (se leen los meses que lo cubren)
     */
    LectorVentasIncremental(YearMonth primero, double desdeMin, double finMin) {
        if (!Files.exists(DatosOficiales.archivoVentas(primero)))
            throw new IllegalArgumentException("No hay datos de ventas para " + primero + " (" + DatosOficiales.archivoVentas(primero) + ")");
        this.desdeMin = desdeMin;
        this.finMin = finMin;
        this.mes = primero;
    }

    @Override
    public List<Pedido> leerHasta(double minuto) {
        List<Pedido> r = new ArrayList<>();
        while ((!leidos.isEmpty() || leerLinea()) && leidos.peek().horaRegistro * 60 <= minuto) r.add(leidos.poll());
        return r;
    }

    /** Lee la próxima línea válida (abriendo el mes siguiente si hace falta) y deja sus entregas en «leidos». */
    private boolean leerLinea() {
        try {
            while (true) {
                if (lector == null && !abrirMes()) return false;
                String linea = lector.readLine();
                if (linea == null) {   // fin del mes: se pasa al siguiente
                    lector.close();
                    lector = null;
                    offsetMin += mes.lengthOfMonth() * 1440.0;
                    mes = mes.plusMonths(1);
                    prefijo = String.format("%04d%02d/", mes.getYear(), mes.getMonthValue());
                    nroLinea = 0;
                    continue;
                }
                LectorPedidos.Registro reg = LectorPedidos.leerLinea(linea, ++nroLinea);
                if (reg == null) continue;
                lineasLeidas++;
                double offsetH = offsetMin / 60.0;
                List<Pedido> ps = LectorPedidos.entregas(reg, reg.llegadaHoras);
                for (Pedido p : ps) p.horaRegistro += offsetH;   // mismas operaciones que DatosOficiales.cargar
                if (ps.get(0).horaRegistro * 60 < desdeMin) continue;
                if (ps.get(0).horaRegistro < ultimaHora)
                    System.err.println("  Aviso: la línea " + nroLinea + " de " + DatosOficiales.archivoVentas(mes) + " está fuera de orden");
                ultimaHora = ps.get(0).horaRegistro;
                for (Pedido p : ps) {
                    p.id = prefijo + p.id;
                    p.idOriginal = prefijo + p.idOriginal;
                    leidos.add(p);
                }
                return true;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Abre el archivo del mes actual si cubre el horizonte y existe. */
    private boolean abrirMes() throws IOException {
        if (offsetMin >= finMin || !Files.exists(DatosOficiales.archivoVentas(mes))) return false;
        lector = Files.newBufferedReader(DatosOficiales.archivoVentas(mes), StandardCharsets.UTF_8);
        return true;
    }
}
