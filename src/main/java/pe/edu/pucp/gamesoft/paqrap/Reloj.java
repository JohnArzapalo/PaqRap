package pe.edu.pucp.gamesoft.paqrap;

/**
 * Ritmo de la simulación respecto del tiempo real (Etapa 13.2).
 *  - sinEspera: EXPERIMENTO y COLAPSO (lo más rápido posible).
 *  - escalado: SIM_5D (p. ej., 5 días simulados en unos 30 min reales).
 *  - real: DIA_A_DIA (1 minuto simulado = 1 minuto real).
 * El simulador llama a esperarHasta antes de procesar cada instante simulado.
 * La espera se puede INTERRUMPIR (Etapa 21): si llega un evento externo (una
 * avería registrada desde el visualizador), el hilo de la simulación se
 * interrumpe, esperarHasta devuelve false y ahora() da el minuto simulado que
 * corresponde al tiempo real transcurrido.
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-18 (el evento externo se aplica en el minuto simulado que corresponde al tiempo real).
 */
interface Reloj {

    /** Espera hasta el minuto simulado indicado; devuelve false si la espera se interrumpió. */
    boolean esperarHasta(double minutoSimulado);

    /** Minuto simulado que corresponde al instante real actual (sin espera: el objetivo). */
    default double ahora(double minutoObjetivo) {
        return minutoObjetivo;
    }

    static Reloj sinEspera() {
        return t -> true;
    }

    /** Ritmo fijo: factor minutos simulados por minuto real, a partir del primer instante recibido. */
    static Reloj escalado(double factor) {
        return new Reloj() {
            private double inicioSim = Double.NaN;
            private long inicioReal;

            @Override
            public boolean esperarHasta(double t) {
                if (Double.isNaN(inicioSim)) {
                    inicioSim = t;
                    inicioReal = System.currentTimeMillis();
                    return true;
                }
                long objetivo = inicioReal + (long) ((t - inicioSim) / factor * 60_000);
                long espera = objetivo - System.currentTimeMillis();
                if (espera > 0) {
                    try {
                        Thread.sleep(espera);
                    } catch (InterruptedException e) {
                        return false;   // evento externo: el simulador lo atiende en ahora()
                    }
                }
                return true;
            }

            @Override
            public double ahora(double minutoObjetivo) {
                if (Double.isNaN(inicioSim)) return minutoObjetivo;
                return inicioSim + (System.currentTimeMillis() - inicioReal) * factor / 60_000.0;
            }
        };
    }

    static Reloj real() {
        return escalado(1.0);
    }
}
