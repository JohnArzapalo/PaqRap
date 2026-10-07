package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Una corrida de un escenario lanzada desde el visualizador web (Etapa 32).
 * Arma la simulación con los datos oficiales a partir de la fecha elegida, la
 * corre en su propio hilo (ServicioSimulacion) y le da al servidor el estado
 * en JSON. Planifica con Búsqueda Tabú, el algoritmo elegido (etapa 30).
 *
 * Escenarios:
 *  - SIM_5D: 5 días a ritmo escalado (por defecto, 5 días en 30 min reales).
 *  - COLAPSO: hasta el primer pedido tarde (tope de sim.horizonte_max_dias días).
 *  - DIA_A_DIA: reloj real; además de los pedidos del archivo, admite pedidos
 *    registrados desde el visualizador.
 * En los tres, la corrida termina al primer colapso (definición del profesor).
 */
final class CorridaWeb {

    final Simulador.Escenario escenario;
    /** Fecha y hora simuladas de inicio. */
    final LocalDateTime inicio;
    /** La misma fecha en milisegundos del navegador que la pidió (las fechas se muestran en su zona horaria). */
    final long inicioMs;
    final long inicioRealMs = System.currentTimeMillis();
    /** Minutos simulados por minuto real. */
    final double factor;
    final int autos, motos, bicicletas;
    private final ServicioSimulacion servicio;
    private volatile long finRealMs = 0;
    private int pedidosRegistrados = 0;

    private CorridaWeb(Simulador.Escenario escenario, LocalDateTime inicio, long inicioMs, double factor,
                       int autos, int motos, int bicicletas, ServicioSimulacion servicio) {
        this.escenario = escenario;
        this.inicio = inicio;
        this.inicioMs = inicioMs;
        this.factor = factor;
        this.autos = autos;
        this.motos = motos;
        this.bicicletas = bicicletas;
        this.servicio = servicio;
    }

    /** Horizonte (min) de cada escenario. */
    static double horizonteMin(Simulador.Escenario esc) {
        switch (esc) {
            case SIM_5D: return 5 * 1440;
            case DIA_A_DIA: return Parametros.decimal("web.diaadia_dias", 30) * 1440;
            default: return Parametros.decimal("sim.horizonte_max_dias", 30) * 1440;
        }
    }

    /** Flota con códigos como los del profesor: TA01.., TM01.., TB01... */
    static List<UnidadTransporte> flota(int autos, int motos, int bicicletas) {
        List<UnidadTransporte> f = new ArrayList<>();
        for (int i = 1; i <= autos; i++) f.add(new UnidadTransporte(String.format("TA%02d", i), TipoUnidad.AUTO));
        for (int i = 1; i <= motos; i++) f.add(new UnidadTransporte(String.format("TM%02d", i), TipoUnidad.MOTO));
        for (int i = 1; i <= bicicletas; i++) f.add(new UnidadTransporte(String.format("TB%02d", i), TipoUnidad.BICICLETA));
        return f;
    }

    /** Arma la simulación con los datos oficiales desde "inicio" y la arranca en su hilo. */
    static CorridaWeb iniciar(Simulador.Escenario esc, LocalDateTime inicio, long inicioMs, double factor,
                              int autos, int motos, int bicicletas) throws IOException {
        if (esc == Simulador.Escenario.EXPERIMENTO) throw new IllegalArgumentException("Escenario no disponible en la web");
        if (factor <= 0) throw new IllegalArgumentException("El ritmo debe ser positivo");
        if (autos + motos + bicicletas == 0) throw new IllegalArgumentException("La flota está vacía");
        Simulador.Config cfg = new Simulador.Config();
        cfg.escenario = esc;
        cfg.inicioMin = (inicio.getDayOfMonth() - 1) * 1440.0 + inicio.getHour() * 60 + inicio.getMinute();
        cfg.horizonteMin = horizonteMin(esc);
        cfg.detenerEnColapso = true;
        cfg.reloj = Reloj.escalado(factor);
        DatosOficiales datos = DatosOficiales.cargar(YearMonth.from(inicio), cfg.inicioMin, cfg.horizonteMin);
        cfg.mapa = new MapaVial(datos.bloqueos);
        cfg.mantenimientos = datos.mantenimientos;
        ServicioSimulacion servicio = new ServicioSimulacion(datos.pedidos, flota(autos, motos, bicicletas),
                Planificador.tabu((int) Parametros.entero("tabu.duracion", 8)), cfg);
        CorridaWeb c = new CorridaWeb(esc, inicio, inicioMs, factor, autos, motos, bicicletas, servicio);
        servicio.iniciar();
        return c;
    }

    boolean enCurso() {
        return servicio.enCurso();
    }

    /** EN_EJECUCION, COLAPSO, FINALIZADA (horizonte cumplido o detenida) o ERROR. */
    String estadoCorrida() {
        if (servicio.enCurso()) return "EN_EJECUCION";
        if (finRealMs == 0) finRealMs = System.currentTimeMillis();
        if (servicio.error() != null) return "ERROR";
        Simulador.Resultado r = servicio.resultado();
        return r != null && !r.censurada ? "COLAPSO" : "FINALIZADA";
    }

    /** Datos de la corrida (no de la simulación) para el visualizador. */
    String corridaJson() {
        String estado = estadoCorrida();
        EscritorJson j = new EscritorJson().abrirObjeto(null).valor("escenario", escenario.name())
                .valor("estado", estado).valor("inicio", inicio.toString()).valor("inicio_ms", inicioMs)
                .valor("inicio_real_ms", inicioRealMs).valor("fin_real_ms", finRealMs).valor("factor", factor)
                .valor("autos", autos).valor("motos", motos).valor("bicicletas", bicicletas)
                .valor("algoritmo", "Búsqueda Tabú");
        if (servicio.error() != null) j.valor("error", String.valueOf(servicio.error()));
        return j.cerrarObjeto().toString();
    }

    /** {"corrida": {...}, "estado": instantánea de la simulación}. */
    String estadoJson() {
        return "{\"corrida\":" + corridaJson() + ",\"estado\":" + servicio.instantanea() + "}";
    }

    String eventosJson(int desde) {
        return servicio.eventos(desde);
    }

    String reporteJson() {
        return servicio.reporte();
    }

    void finalizar() {
        servicio.detener();
    }

    void registrarAveria(String unidad, int tipo) {
        servicio.registrarAveria(unidad, tipo);
    }

    void cambiarVelocidad(TipoUnidad tipo, double kmPorHora) {
        servicio.cambiarVelocidad(tipo, kmPorHora);
    }

    /** Pedido nuevo en la operación día a día: llega en el instante actual. Devuelve su id. */
    synchronized String registrarPedido(String cliente, int x, int y, int cantidad, int plazoHoras) {
        if (escenario != Simulador.Escenario.DIA_A_DIA)
            throw new IllegalArgumentException("Solo la operación día a día admite pedidos nuevos");
        if (x < 0 || x >= MapaVial.ANCHO || y < 0 || y >= MapaVial.ALTO)   // ANCHO y ALTO cuentan nodos (71 × 51)
            throw new IllegalArgumentException("Destino fuera del mapa");
        if (cantidad < 1) throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        if (plazoHoras < 1) throw new IllegalArgumentException("Plazo inválido");
        String id = (cliente == null || cliente.isBlank() ? "web" : cliente.trim()) + "-R" + (++pedidosRegistrados);
        servicio.registrarPedido(new Pedido(id, id, x, y, cantidad, plazoHoras, 0));
        return id;
    }

    double minutoActual() {
        return servicio.minutoActual();
    }
}
