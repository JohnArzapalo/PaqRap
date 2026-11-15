package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

/**
 * Parámetros del sistema que se ajustan desde el visualizador, en la opción «Parámetros» (Etapa 38, SI-36;
 * indicación del profesor, 14-nov-2026): la flota, el ritmo de cada escenario y los umbrales del semáforo.
 * Se configuran una vez y valen para todas las corridas y todos los navegadores; por eso la pantalla de
 * inicio de cada escenario solo pide la fecha y la hora.
 *
 * Se guardan en un archivo aparte (web.parametros_sistema, por defecto config/sistema.properties), así
 * config/parametros.properties, que es la configuración del planificador y del experimento, no cambia.
 * Sin ese archivo rigen los valores por defecto (los de siempre).
 */
final class ParametrosSistema {

    /** Archivo donde se guardan; las pruebas lo cambian por uno temporal. */
    static Path archivo = Paths.get(Parametros.texto("web.parametros_sistema", "config/sistema.properties"));

    private static ParametrosSistema vigentes;

    int autos = 10, motos = 15, bicicletas = 12;
    /** Semáforo de llenado de unidades y almacenes (SI-31): bajo < llenadoBajo ≤ medio < llenadoAlto ≤ alto. */
    int llenadoBajo = 40, llenadoAlto = 75;
    /** Semáforo de los pedidos: holgura (min) desde la que el pedido está en verde o en ámbar. */
    int holguraVerde = 120, holguraAmbar = 60;
    /** Ritmo de cada escenario: minutos simulados por minuto real (×K). */
    double ritmoSim5d = 5 * 1440 / Parametros.decimal("sim5d.minutos_reales", 60);
    double ritmoColapso = Parametros.decimal("web.factor_colapso", 600);
    double ritmoDiaADia = 1;

    /** Los parámetros vigentes (los guardados o, si no hay, los valores por defecto). */
    static synchronized ParametrosSistema vigentes() {
        if (vigentes == null) vigentes = leer(archivo);
        return vigentes.copia();
    }

    /** Valida los cambios que llegan del visualizador, los guarda y pasan a ser los vigentes. */
    static synchronized ParametrosSistema actualizar(Map<String, String> cambios) throws IOException {
        ParametrosSistema p = vigentes().con(cambios);
        p.validar();
        p.guardar(archivo);
        vigentes = p;
        return p.copia();
    }

    /** Para las pruebas: olvida los vigentes y vuelve a leerlos del archivo. */
    static synchronized void recargar() {
        vigentes = null;
    }

    /** Ritmo (×K) del escenario. */
    double ritmo(Simulador.Escenario esc) {
        switch (esc) {
            case SIM_5D: return ritmoSim5d;
            case COLAPSO: return ritmoColapso;
            default: return ritmoDiaADia;
        }
    }

    private ParametrosSistema con(Map<String, String> c) {
        ParametrosSistema p = copia();
        p.autos = entero(c, "autos", p.autos);
        p.motos = entero(c, "motos", p.motos);
        p.bicicletas = entero(c, "bicicletas", p.bicicletas);
        p.llenadoBajo = entero(c, "llenado_bajo", p.llenadoBajo);
        p.llenadoAlto = entero(c, "llenado_alto", p.llenadoAlto);
        p.holguraVerde = entero(c, "holgura_verde", p.holguraVerde);
        p.holguraAmbar = entero(c, "holgura_ambar", p.holguraAmbar);
        p.ritmoSim5d = decimal(c, "ritmo_SIM_5D", p.ritmoSim5d);
        p.ritmoColapso = decimal(c, "ritmo_COLAPSO", p.ritmoColapso);
        p.ritmoDiaADia = decimal(c, "ritmo_DIA_A_DIA", p.ritmoDiaADia);
        return p;
    }

    /** Las mismas reglas que pedía el formulario de inicio, más el límite del ritmo frente a Ta. */
    void validar() {
        for (int n : new int[]{autos, motos, bicicletas})
            if (n < 0 || n > 30) throw new IllegalArgumentException("Cada tipo de unidad debe tener entre 0 y 30 unidades");
        if (autos + motos + bicicletas == 0) throw new IllegalArgumentException("La flota está vacía");
        if (!(llenadoBajo >= 1 && llenadoBajo < llenadoAlto && llenadoAlto <= 100))
            throw new IllegalArgumentException("Los límites de llenado deben cumplir 1 ≤ bajo < alto ≤ 100");
        if (!(holguraAmbar >= 0 && holguraAmbar < holguraVerde))
            throw new IllegalArgumentException("La holgura ámbar debe ser menor que la verde (y no negativa)");
        // El plan de cada bloque se calcula durante el adelanto (30 min simulados): en tiempo real debe durar más que Ta
        double maximo = Parametros.decimal("bloque.adelanto_min", 30) * 60_000 / Parametros.entero("simulacion.ta_ms", 2000);
        for (double k : new double[]{ritmoSim5d, ritmoColapso, ritmoDiaADia})
            if (!(k > 0 && k < maximo))
                throw new IllegalArgumentException(String.format(java.util.Locale.US,
                        "El ritmo debe estar entre ×1 y ×%.0f: con más, el planificador no alcanza a planificar cada bloque", maximo));
    }

    String json() {
        return new EscritorJson().abrirObjeto(null)
                .valor("autos", autos).valor("motos", motos).valor("bicicletas", bicicletas)
                .valor("llenado_bajo", llenadoBajo).valor("llenado_alto", llenadoAlto)
                .valor("holgura_verde", holguraVerde).valor("holgura_ambar", holguraAmbar)
                .abrirObjeto("ritmo").valor("SIM_5D", ritmoSim5d).valor("COLAPSO", ritmoColapso).valor("DIA_A_DIA", ritmoDiaADia).cerrarObjeto()
                .cerrarObjeto().toString();
    }

    private ParametrosSistema copia() {
        ParametrosSistema p = new ParametrosSistema();
        p.autos = autos; p.motos = motos; p.bicicletas = bicicletas;
        p.llenadoBajo = llenadoBajo; p.llenadoAlto = llenadoAlto;
        p.holguraVerde = holguraVerde; p.holguraAmbar = holguraAmbar;
        p.ritmoSim5d = ritmoSim5d; p.ritmoColapso = ritmoColapso; p.ritmoDiaADia = ritmoDiaADia;
        return p;
    }

    private static ParametrosSistema leer(Path ruta) {
        ParametrosSistema p = new ParametrosSistema();
        if (!Files.exists(ruta)) return p;
        Properties pr = new Properties();
        try (Reader r = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
            pr.load(r);
            Map<String, String> m = new java.util.HashMap<>();
            for (String k : pr.stringPropertyNames()) m.put(k, pr.getProperty(k));
            ParametrosSistema leido = p.con(m);
            leido.validar();
            return leido;
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[sistema] No se pudo usar " + ruta.toAbsolutePath() + "; rigen los valores por defecto. " + e.getMessage());
            return p;
        }
    }

    private void guardar(Path ruta) throws IOException {
        Properties pr = new Properties();
        pr.setProperty("autos", "" + autos); pr.setProperty("motos", "" + motos); pr.setProperty("bicicletas", "" + bicicletas);
        pr.setProperty("llenado_bajo", "" + llenadoBajo); pr.setProperty("llenado_alto", "" + llenadoAlto);
        pr.setProperty("holgura_verde", "" + holguraVerde); pr.setProperty("holgura_ambar", "" + holguraAmbar);
        pr.setProperty("ritmo_SIM_5D", "" + ritmoSim5d); pr.setProperty("ritmo_COLAPSO", "" + ritmoColapso);
        pr.setProperty("ritmo_DIA_A_DIA", "" + ritmoDiaADia);
        if (ruta.getParent() != null) Files.createDirectories(ruta.getParent());
        try (Writer w = Files.newBufferedWriter(ruta, StandardCharsets.UTF_8)) {
            pr.store(w, "Parámetros del sistema (opción «Parámetros» del visualizador, etapa 38)");
        }
    }

    private static int entero(Map<String, String> c, String clave, int actual) {
        String v = c.get(clave);
        if (v == null || v.isBlank()) return actual;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido para " + clave + ": " + v);
        }
    }

    private static double decimal(Map<String, String> c, String clave, double actual) {
        String v = c.get(clave);
        if (v == null || v.isBlank()) return actual;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido para " + clave + ": " + v);
        }
    }
}
