package pe.edu.pucp.gamesoft.paqrap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Componente Planificador desplegado como servicio web (Etapa 32).
 *
 * Usa el servidor HTTP que trae Java (com.sun.net.httpserver): no necesita
 * dependencias. Sirve dos cosas:
 *  1. El visualizador (archivos de la carpeta web/), para abrirlo desde
 *     cualquier dispositivo con un navegador.
 *  2. Una API REST con una corrida por escenario (SIM_5D, COLAPSO, DIA_A_DIA).
 *     Las tres pueden correr a la vez; todos los dispositivos ven las mismas.
 *
 * API (respuestas en JSON; datos de entrada como formulario o en la URL):
 *   GET  /api/datos                          meses con datos oficiales
 *   GET  /api/corridas                       estado de las tres corridas
 *   POST /api/corridas/{ESC}                 inicia (inicio=aaaa-mm-ddThh:mm, inicio_ms, factor, autos, motos, bicicletas)
 *   GET  /api/corridas/{ESC}/estado          instantánea (el visualizador la consulta cada segundo)
 *   GET  /api/corridas/{ESC}/eventos?desde=n bitácora desde el evento n
 *   GET  /api/corridas/{ESC}/reporte         resumen de cierre
 *   POST /api/corridas/{ESC}/finalizacion    detiene la corrida
 *   POST /api/corridas/{ESC}/averias         unidad, tipo (1, 2 o 3)
 *   POST /api/corridas/{ESC}/velocidades     tipo (AUTO, MOTO, BICICLETA), kmh
 *   POST /api/corridas/DIA_A_DIA/pedidos     cliente, x, y, cantidad, plazo
 *
 * Uso: java -cp target/classes pe.edu.pucp.gamesoft.paqrap.ServidorWeb [puerto]
 * (o la variable de entorno PORT; por defecto web.puerto = 8080). Se ejecuta
 * desde la raíz del proyecto, donde están config/, juego_de_datos/ y web/.
 */
public final class ServidorWeb {

    private static final Map<Simulador.Escenario, CorridaWeb> CORRIDAS = new ConcurrentHashMap<>();
    private static final Path CARPETA_WEB = Paths.get(Parametros.texto("web.carpeta", "web")).toAbsolutePath().normalize();

    private ServidorWeb() {
    }

    public static void main(String[] args) throws IOException {
        // Windows: el servidor HTTP abre un socket local en la carpeta temporal y falla si su
        // ruta tiene espacios o el nombre corto 8.3 (C:\Users\MARIAG~1\...), como cuando el
        // usuario tiene espacios en el nombre. En ese caso se usa la carpeta pública.
        String tmp = System.getProperty("java.io.tmpdir");
        String publica = System.getenv("PUBLIC") != null ? System.getenv("PUBLIC") : "C:\\Users\\Public";
        if (System.getProperty("jdk.net.unixdomain.tmpdir") == null && (tmp.contains(" ") || tmp.contains("~"))
                && !publica.contains(" ") && Files.isDirectory(Paths.get(publica)))
            System.setProperty("jdk.net.unixdomain.tmpdir", publica);
        String puerto = System.getenv("PORT") != null ? System.getenv("PORT")
                : args.length > 0 ? args[0] : Parametros.texto("web.puerto", "8080");
        HttpServer servidor = HttpServer.create(new InetSocketAddress(Integer.parseInt(puerto)), 0);
        servidor.createContext("/api/", ServidorWeb::api);
        servidor.createContext("/", ServidorWeb::archivo);
        servidor.setExecutor(Executors.newFixedThreadPool(16));
        servidor.start();
        System.out.println("PaqRap: visualizador en http://localhost:" + puerto + "/  (archivos de " + CARPETA_WEB + ")");
    }

    // ============================ API ============================

    private static void api(HttpExchange ex) throws IOException {
        try {
            if ("OPTIONS".equals(ex.getRequestMethod())) { responder(ex, 204, ""); return; }   // CORS
            String[] p = ex.getRequestURI().getPath().replaceAll("/+$", "").split("/");   // "", "api", recurso, ...
            Map<String, String> datos = parametros(ex);
            String metodo = ex.getRequestMethod();
            if (p.length == 3 && p[2].equals("datos")) { responder(ex, 200, datosJson()); return; }
            if (p.length < 3 || !p[2].equals("corridas")) { error(ex, 404, "Recurso no encontrado"); return; }
            if (p.length == 3) { responder(ex, 200, resumenJson()); return; }
            Simulador.Escenario esc = escenario(p[3]);
            CorridaWeb c = CORRIDAS.get(esc);
            String accion = p.length > 4 ? p[4] : "";
            if (accion.isEmpty() && metodo.equals("POST")) {
                if (c != null && c.enCurso()) { error(ex, 409, "Ya hay una corrida de " + esc + " en ejecución; finalícela primero"); return; }
                c = iniciar(esc, datos);
                CORRIDAS.put(esc, c);
                responder(ex, 200, c.estadoJson());
                return;
            }
            if (c == null) { error(ex, 404, "No hay corrida de " + esc); return; }
            switch (metodo + " " + accion) {
                case "GET estado":
                    responder(ex, 200, c.estadoJson());
                    break;
                case "GET eventos":
                    responder(ex, 200, c.eventosJson(entero(datos, "desde", 0)));
                    break;
                case "GET reporte":
                    responder(ex, 200, c.reporteJson());
                    break;
                case "POST finalizacion":
                    c.finalizar();
                    responder(ex, 200, "{\"ok\":true}");
                    break;
                case "POST averias":
                    c.registrarAveria(requerido(datos, "unidad"), entero(datos, "tipo", 1));
                    responder(ex, 200, "{\"ok\":true}");
                    break;
                case "POST velocidades":
                    c.cambiarVelocidad(TipoUnidad.valueOf(requerido(datos, "tipo").toUpperCase(Locale.ROOT)),
                            Double.parseDouble(requerido(datos, "kmh")));
                    responder(ex, 200, "{\"ok\":true}");
                    break;
                case "POST pedidos": {
                    int plazo = entero(datos, "plazo", 36);
                    String id = c.registrarPedido(datos.get("cliente"), entero(datos, "x", -1), entero(datos, "y", -1),
                            entero(datos, "cantidad", 0), plazo);
                    double ahora = c.minutoActual();
                    responder(ex, 200, new EscritorJson().abrirObjeto(null).valor("id", id).valor("registro_min", ahora)
                            .valor("limite_min", ahora + plazo * 60).cerrarObjeto().toString());
                    break;
                }
                default:
                    error(ex, 404, "Acción no encontrada: " + metodo + " " + accion);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            error(ex, 400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            error(ex, 500, "Error interno: " + e);
        }
    }

    private static CorridaWeb iniciar(Simulador.Escenario esc, Map<String, String> d) throws IOException {
        LocalDateTime inicio = LocalDateTime.parse(requerido(d, "inicio"));   // aaaa-mm-ddThh:mm
        long inicioMs = Long.parseLong(d.getOrDefault("inicio_ms", "0"));
        double porDefecto = esc == Simulador.Escenario.SIM_5D ? 5 * 1440 / Parametros.decimal("sim5d.minutos_reales", 30)
                : esc == Simulador.Escenario.COLAPSO ? Parametros.decimal("web.factor_colapso", 600) : 1;
        double factor = d.containsKey("factor") ? Double.parseDouble(d.get("factor")) : porDefecto;
        return CorridaWeb.iniciar(esc, inicio, inicioMs, factor, entero(d, "autos", 10), entero(d, "motos", 15),
                entero(d, "bicicletas", 12));
    }

    private static Simulador.Escenario escenario(String texto) {
        try {
            Simulador.Escenario e = Simulador.Escenario.valueOf(texto.toUpperCase(Locale.ROOT));
            if (e != Simulador.Escenario.EXPERIMENTO) return e;
        } catch (IllegalArgumentException ignorada) {
            // se informa abajo
        }
        throw new IllegalArgumentException("Escenario desconocido: " + texto + " (SIM_5D, COLAPSO o DIA_A_DIA)");
    }

    /** Estado de las tres corridas, para las pestañas del visualizador. */
    private static String resumenJson() {
        EscritorJson j = new EscritorJson().abrirArreglo(null);
        for (Simulador.Escenario e : new Simulador.Escenario[]{Simulador.Escenario.DIA_A_DIA,
                Simulador.Escenario.SIM_5D, Simulador.Escenario.COLAPSO}) {
            CorridaWeb c = CORRIDAS.get(e);
            j.abrirObjeto(null).valor("escenario", e.name()).valor("estado", c == null ? null : c.estadoCorrida()).cerrarObjeto();
        }
        return j.cerrarArreglo().toString();
    }

    /** Primer y último mes con archivo de ventas oficial. */
    private static String datosJson() {
        YearMonth desde = null, hasta = null;
        for (YearMonth m = YearMonth.of(2024, 1); m.isBefore(YearMonth.of(2031, 1)); m = m.plusMonths(1)) {
            if (!Files.exists(DatosOficiales.archivoVentas(m))) continue;
            if (desde == null) desde = m;
            hasta = m;
        }
        return new EscritorJson().abrirObjeto(null).valor("desde", desde == null ? null : desde.toString())
                .valor("hasta", hasta == null ? null : hasta.toString()).cerrarObjeto().toString();
    }

    // ============================ Archivos del visualizador ============================

    private static void archivo(HttpExchange ex) throws IOException {
        String ruta = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (ruta.equals("/")) ruta = "/index.html";
        Path f = CARPETA_WEB.resolve(ruta.substring(1)).normalize();
        if (!f.startsWith(CARPETA_WEB) || !Files.isRegularFile(f)) { error(ex, 404, "No existe " + ruta); return; }
        String nombre = f.getFileName().toString();
        String tipo = nombre.endsWith(".html") ? "text/html; charset=utf-8" : nombre.endsWith(".js") ? "text/javascript; charset=utf-8"
                : nombre.endsWith(".css") ? "text/css; charset=utf-8" : nombre.endsWith(".svg") ? "image/svg+xml"
                : nombre.endsWith(".png") ? "image/png" : "application/octet-stream";
        byte[] cuerpo = Files.readAllBytes(f);
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(200, cuerpo.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(cuerpo);
        }
    }

    // ============================ Utilidades ============================

    /** Parámetros de la URL y del cuerpo (formulario application/x-www-form-urlencoded). */
    private static Map<String, String> parametros(HttpExchange ex) throws IOException {
        Map<String, String> m = new HashMap<>();
        leerFormulario(ex.getRequestURI().getRawQuery(), m);
        try (InputStream in = ex.getRequestBody()) {
            leerFormulario(new String(in.readAllBytes(), StandardCharsets.UTF_8), m);
        }
        return m;
    }

    private static void leerFormulario(String texto, Map<String, String> m) {
        if (texto == null || texto.isBlank()) return;
        for (String par : texto.split("&")) {
            int i = par.indexOf('=');
            if (i <= 0) continue;
            m.put(URLDecoder.decode(par.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8));
        }
    }

    private static String requerido(Map<String, String> d, String clave) {
        String v = d.get(clave);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Falta el dato " + clave);
        return v.trim();
    }

    private static int entero(Map<String, String> d, String clave, int porDefecto) {
        String v = d.get(clave);
        if (v == null || v.isBlank()) return porDefecto;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido para " + clave + ": " + v);
        }
    }

    private static void error(HttpExchange ex, int codigo, String mensaje) throws IOException {
        responder(ex, codigo, new EscritorJson().abrirObjeto(null).valor("error", mensaje).cerrarObjeto().toString());
    }

    /** Respuesta JSON; permite que un visualizador servido desde otro dominio la lea (CORS). */
    private static void responder(HttpExchange ex, int codigo, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (codigo == 204) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(cuerpo);
        }
    }
}
