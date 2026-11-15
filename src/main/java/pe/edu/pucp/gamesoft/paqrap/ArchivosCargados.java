package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carga de archivos desde el visualizador, en la opción «Carga de archivos» (Etapa 38, SI-37; indicación del
 * profesor, 14-nov-2026): para correr en otra fecha se cargan antes los archivos y luego solo se inicia la
 * simulación. Se cargan las ventas y los bloqueos de un mes y el mantenimiento preventivo, en el formato del
 * profesor.
 *
 * Cada archivo se valida con ValidadorEntradas (más el día dentro del mes) y solo se guarda si no tiene
 * errores, en una carpeta aparte (web.carpeta_cargados, por defecto datos_cargados/) y con el nombre del
 * formato del profesor. juego_de_datos/ no se modifica: DatosOficiales usa el archivo cargado de un mes si
 * existe y, si no, el oficial. Eliminar el cargado devuelve el oficial. El experimento no usa esta carpeta.
 */
final class ArchivosCargados {

    enum Tipo { VENTAS, BLOQUEOS, MANTENIMIENTO }

    /** Carpeta de los archivos cargados; las pruebas la cambian por una temporal. */
    static Path carpeta = Paths.get(Parametros.texto("web.carpeta_cargados", "datos_cargados"));

    /** Tamaño máximo de un archivo cargado (un mes del profesor tiene unas 5 000 líneas, ~150 KB). */
    static final int MAX_BYTES = 5_000_000;

    private static final Pattern DIA = Pattern.compile("^(\\d+)d");

    private ArchivosCargados() {
    }

    static Path ventas(YearMonth mes) {
        return carpeta.resolve("ventas").resolve(String.format("ventas.%04d%02d.txt", mes.getYear(), mes.getMonthValue()));
    }

    static Path bloqueos(YearMonth mes) {
        return carpeta.resolve("bloqueos").resolve(String.format("bloqueo.%02d%02d.txt", mes.getYear() % 100, mes.getMonthValue()));
    }

    static Path mantenimiento() {
        return carpeta.resolve("mant.preventivo.txt");
    }

    static Path destino(Tipo tipo, YearMonth mes) {
        switch (tipo) {
            case VENTAS: return ventas(requerirMes(mes));
            case BLOQUEOS: return bloqueos(requerirMes(mes));
            default: return mantenimiento();
        }
    }

    private static YearMonth requerirMes(YearMonth mes) {
        if (mes == null) throw new IllegalArgumentException("Indica el mes del archivo");
        return mes;
    }

    static Tipo tipo(String texto) {
        try {
            return Tipo.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de archivo desconocido: " + texto + " (ventas, bloqueos o mantenimiento)");
        }
    }

    /**
     * Valida el texto y, si no tiene errores, lo guarda como el archivo del tipo y mes. Devuelve el informe
     * de la validación (con errores, el archivo no se guarda y el anterior queda como estaba).
     */
    static synchronized ValidadorEntradas.Informe cargar(Tipo tipo, YearMonth mes, String texto) throws IOException {
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("El archivo está vacío");
        if (texto.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("El archivo supera " + MAX_BYTES / 1_000_000 + " MB");
        Path destino = destino(tipo, mes);
        Files.createDirectories(destino.getParent());
        Path temporal = Files.createTempFile(destino.getParent(), ".subida-", ".tmp");
        try {
            Files.writeString(temporal, texto, StandardCharsets.UTF_8);
            ValidadorEntradas.Informe inf = tipo == Tipo.VENTAS ? ValidadorEntradas.validarVentas(temporal.toString())
                    : tipo == Tipo.BLOQUEOS ? ValidadorEntradas.validarBloqueos(temporal.toString())
                    : ValidadorEntradas.validarMantenimiento(temporal.toString());
            if (tipo != Tipo.MANTENIMIENTO) revisarDias(inf, texto, mes);
            if (inf.validas == 0 && inf.errores.isEmpty()) inf.error(0, "el archivo no tiene líneas", null);
            if (inf.errores.isEmpty()) Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return inf;
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    /** Los días de registro (ventas) o de inicio (bloqueos) deben caer dentro del mes elegido. */
    private static void revisarDias(ValidadorEntradas.Informe inf, String texto, YearMonth mes) {
        String[] lineas = texto.split("\\R");
        for (int i = 0; i < lineas.length; i++) {
            Matcher m = DIA.matcher(lineas[i].trim());
            if (!m.find()) continue;
            int dia = Integer.parseInt(m.group(1));
            if (dia < 1 || dia > mes.lengthOfMonth())
                inf.error(i + 1, "el día " + dia + " no existe en " + mes, lineas[i].trim());
        }
    }

    /** Quita el archivo cargado: vuelve a regir el oficial. Devuelve false si no había uno cargado. */
    static synchronized boolean eliminar(Tipo tipo, YearMonth mes) throws IOException {
        return Files.deleteIfExists(destino(tipo, mes));
    }

    /** Meses con datos (oficiales o cargados) y de dónde sale cada archivo, para la opción del visualizador. */
    static String listadoJson() {
        EscritorJson j = new EscritorJson().abrirObjeto(null).valor("carpeta", carpeta.toString().replace('\\', '/'))
                .valor("mantenimiento", fuente(mantenimiento(), Paths.get(DatosOficiales.ARCHIVO_MANTENIMIENTO)))
                .abrirArreglo("meses");
        for (YearMonth m = YearMonth.of(2024, 1); m.isBefore(YearMonth.of(2031, 1)); m = m.plusMonths(1)) {
            String v = fuente(ventas(m), DatosOficiales.ventasOficiales(m)), b = fuente(bloqueos(m), DatosOficiales.bloqueosOficiales(m));
            if (v == null && b == null) continue;
            j.abrirObjeto(null).valor("mes", m.toString()).valor("ventas", v).valor("bloqueos", b).cerrarObjeto();
        }
        return j.cerrarArreglo().cerrarObjeto().toString();
    }

    /** "cargado" si hay archivo cargado, "oficial" si solo está el del profesor y null si no hay ninguno. */
    private static String fuente(Path cargado, Path oficial) {
        return Files.exists(cargado) ? "cargado" : Files.exists(oficial) ? "oficial" : null;
    }

    /** Informe de una carga, para el visualizador. */
    static String informeJson(ValidadorEntradas.Informe inf, Tipo tipo, YearMonth mes) {
        EscritorJson j = new EscritorJson().abrirObjeto(null).valor("guardado", inf.errores.isEmpty())
                .valor("archivo", destino(tipo, mes).getFileName().toString())
                .valor("lineas", inf.lineas).valor("validas", inf.validas).abrirArreglo("errores");
        for (int i = 0; i < Math.min(20, inf.errores.size()); i++) j.valor(null, inf.errores.get(i));
        j.cerrarArreglo().valor("errores_total", inf.errores.size()).abrirArreglo("avisos");
        for (int i = 0; i < Math.min(20, inf.avisos.size()); i++) j.valor(null, inf.avisos.get(i));
        return j.cerrarArreglo().cerrarObjeto().toString();
    }
}
