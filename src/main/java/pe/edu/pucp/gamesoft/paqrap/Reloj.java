package pe.edu.pucp.gamesoft.paqrap;

/**
 * Ritmo de la simulación respecto del tiempo real (Etapa 13.2).
 *  - sinEspera: EXPERIMENTO y COLAPSO (lo más rápido posible).
 *  - escalado: SIM_5D (p. ej., 5 días simulados en unos 30 min reales).
 *  - real: DIA_A_DIA (1 minuto simulado = 1 minuto real).
 * El simulador llama a esperarHasta antes de procesar cada instante simulado.
 */
interface Reloj {

    void esperarHasta(double minutoSimulado);

    static Reloj sinEspera() {
        return t -> { };
    }

    /** Ritmo fijo: factor minutos simulados por minuto real, a partir del primer instante recibido. */
    static Reloj escalado(double factor) {
        return new Reloj() {
            private double inicioSim = Double.NaN;
            private long inicioReal;

            @Override
            public void esperarHasta(double t) {
                if (Double.isNaN(inicioSim)) {
                    inicioSim = t;
                    inicioReal = System.currentTimeMillis();
                    return;
                }
                long objetivo = inicioReal + (long) ((t - inicioSim) / factor * 60_000);
                long espera = objetivo - System.currentTimeMillis();
                if (espera > 0) {
                    try {
                        Thread.sleep(espera);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
    }

    static Reloj real() {
        return escalado(1.0);
    }
}
