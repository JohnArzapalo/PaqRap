package pe.edu.pucp.gamesoft.paqrap;

import java.util.List;

/**
 * Capa de integración con el visualizador, INDEPENDIENTE de la tecnología web
 * (Etapa 21.2). Corre un Simulador en su propio hilo (escenarios SIM_5D,
 * COLAPSO o DIA_A_DIA) y ofrece lo que necesita cualquier frontend:
 *  - instantanea(): estado completo en JSON (reloj, semáforo, almacenes,
 *    unidades con posición/estado/carga/rutas, bloqueos activos, pedidos);
 *  - registrarAveria(unidad, tipo): evento externo, atendido de inmediato;
 *  - cambiarVelocidad(tipo, km/h): rige desde la siguiente replanificación (P16);
 *  - detener() y esperar().
 * Un servidor (REST + WebSocket, u otro) solo tiene que llamar a estos métodos
 * y difundir la instantánea a todos los dispositivos conectados (ver
 * docs/propuesta_arquitectura_integracion.md).
 *
 * SUPUESTOS: una simulación por servicio. El Contexto de planificación es por
 * hilo, pero los algoritmos aún guardan estado estático (semilla, contadores)
 * y la velocidad es un dato global del tipo de unidad (SI-19): para correr
 * varios escenarios a la vez, usar un proceso (JVM) por escenario.
 */
final class ServicioSimulacion {

    private final Simulador simulador;
    private Thread hilo;
    private volatile Simulador.Resultado resultado;
    private volatile Throwable error;

    ServicioSimulacion(List<Pedido> pedidos, List<UnidadTransporte> flota, Planificador planificador,
                       Simulador.Config cfg) {
        this.simulador = Simulador.crear(pedidos, flota, planificador, cfg);
    }

    /** Arranca la simulación en un hilo propio. */
    synchronized void iniciar() {
        if (hilo != null) throw new IllegalStateException("La simulación ya se inició");
        hilo = new Thread(() -> {
            try {
                resultado = simulador.ejecutar();
            } catch (Throwable e) {
                error = e;
            }
        }, "simulacion-paqrap");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Estado actual en JSON (se puede llamar en cualquier momento, desde cualquier hilo). */
    String instantanea() {
        return simulador.instantaneaJson();
    }

    /** Avería registrada desde el visualizador (tipos 1, 2 o 3), en el instante actual. */
    void registrarAveria(String codigoUnidad, int tipo) {
        if (tipo < 1 || tipo > 3) throw new IllegalArgumentException("Tipo de avería inválido: " + tipo);
        simulador.inyectarAveria(codigoUnidad, tipo);
    }

    /** Cambio de velocidad en caliente (rige desde la siguiente replanificación). */
    void cambiarVelocidad(TipoUnidad tipo, double kmPorHora) {
        if (kmPorHora <= 0) throw new IllegalArgumentException("Velocidad inválida: " + kmPorHora);
        simulador.cambiarVelocidad(tipo, kmPorHora);
    }

    void detener() {
        simulador.detener();
    }

    boolean enCurso() {
        return hilo != null && hilo.isAlive();
    }

    /** Espera a que termine (hasta maxMs) y devuelve el resultado (null si sigue corriendo). */
    Simulador.Resultado esperar(long maxMs) throws InterruptedException {
        hilo.join(maxMs);
        if (error != null) throw new IllegalStateException("La simulación falló", error);
        return resultado;
    }
}
