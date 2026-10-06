package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos oficiales del profesor para una corrida del visualizador (Etapa 32):
 * pedidos, bloqueos y mantenimientos de VARIOS meses seguidos, para que una
 * corrida pueda cruzar el fin de mes (la simulación hasta el colapso dura
 * hasta 30 días y la operación día a día no tiene fin fijo).
 *
 * Todos los tiempos se cuentan desde el día 1 a las 00:00 del PRIMER mes: los
 * del mes k se desplazan en los días de los meses anteriores. Los pedidos de
 * los meses siguientes llevan el mes en el id (p. ej. 202611/c1234-L5), porque
 * los números de línea se repiten entre archivos.
 *
 * Carpetas configurables en config/parametros.properties (web.*).
 */
final class DatosOficiales {

    static final String CARPETA_VENTAS = Parametros.texto("web.carpeta_ventas",
            "juego_de_datos/ventas.v20260909-20260928T011657Z-1-001/ventas.v20260909");
    static final String CARPETA_BLOQUEOS = Parametros.texto("web.carpeta_bloqueos",
            "juego_de_datos/bloqueos.v20260909-20260928T011612Z-1-001/bloqueos.v20260909");
    static final String ARCHIVO_MANTENIMIENTO = Parametros.texto("web.mantenimiento",
            "juego_de_datos/mant.preventivo.09.10.txt");

    final List<Pedido> pedidos = new ArrayList<>();
    final List<MapaVial.Bloqueo> bloqueos = new ArrayList<>();
    final List<Mantenimiento> mantenimientos = new ArrayList<>();

    private DatosOficiales() {
    }

    /** Archivo de ventas del mes (formato del profesor: ventas.202609.txt). */
    static Path archivoVentas(YearMonth mes) {
        return Paths.get(CARPETA_VENTAS, String.format("ventas.%04d%02d.txt", mes.getYear(), mes.getMonthValue()));
    }

    /** Archivo de bloqueos del mes (formato del profesor: bloqueo.2609.txt). */
    static Path archivoBloqueos(YearMonth mes) {
        return Paths.get(CARPETA_BLOQUEOS, String.format("bloqueo.%02d%02d.txt", mes.getYear() % 100, mes.getMonthValue()));
    }

    /**
     * Carga los meses necesarios para cubrir [desdeMin, desdeMin + horizonteMin).
     * Solo se conservan los pedidos registrados desde desdeMin (como en el experimento).
     * @param primero   mes del día 1 a las 00:00 (minuto 0)
     * @param desdeMin  minuto de inicio de la corrida
     */
    static DatosOficiales cargar(YearMonth primero, double desdeMin, double horizonteMin) throws IOException {
        if (!Files.exists(archivoVentas(primero)))
            throw new IllegalArgumentException("No hay datos de ventas para " + primero + " (" + archivoVentas(primero) + ")");
        DatosOficiales d = new DatosOficiales();
        double finMin = desdeMin + horizonteMin;
        double offsetMin = 0;
        for (YearMonth mes = primero; offsetMin < finMin && Files.exists(archivoVentas(mes)); mes = mes.plusMonths(1)) {
            double offsetH = offsetMin / 60.0;
            String prefijo = mes.equals(primero) ? "" : String.format("%04d%02d/", mes.getYear(), mes.getMonthValue());
            for (Pedido p : LectorPedidos.leerAbsoluto(archivoVentas(mes).toString())) {
                p.horaRegistro += offsetH;
                if (p.horaRegistro * 60 < desdeMin) continue;
                p.id = prefijo + p.id;
                p.idOriginal = prefijo + p.idOriginal;
                d.pedidos.add(p);
            }
            if (Files.exists(archivoBloqueos(mes)))
                for (MapaVial.Bloqueo b : MapaVial.leer(archivoBloqueos(mes).toString()).bloqueos)
                    d.bloqueos.add(new MapaVial.Bloqueo(b.inicioH + offsetH, b.finH + offsetH, b.nodos));
            if (Files.exists(Paths.get(ARCHIVO_MANTENIMIENTO)))
                for (Mantenimiento m : Mantenimiento.leer(ARCHIVO_MANTENIMIENTO, mes.getYear() * 100 + mes.getMonthValue()))
                    d.mantenimientos.add(new Mantenimiento(m.unidad, m.inicioMin + offsetMin, m.finMin + offsetMin));
            offsetMin += mes.lengthOfMonth() * 1440.0;
        }
        return d;
    }
}
