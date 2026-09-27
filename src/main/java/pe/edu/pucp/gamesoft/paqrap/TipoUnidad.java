package pe.edu.pucp.gamesoft.paqrap;

/**
 * Modelo simplificado del dominio para esta primera iteración de verificación
 * de factibilidad (ISA apartado 4.1.2). En próximas iteraciones estas clases
 * se sustituyen por las del Modelo de Clases de Análisis (13.ana.clases.analisis.v01)
 * y las coordenadas se resuelven contra el CalculadorRecorridos real.
 *
 * Capacidad, velocidad y costo por kilómetro de cada tipo de vehículo.
 * Capacidad y costo son datos del caso (fijos). La velocidad se lee de
 * config/parametros.properties (clave velocidad.TIPO); por defecto, la del
 * enunciado. Es final: un cambio en caliente (P16) rige solo en su simulación
 * (Simulador.velocidad y Contexto.velocidad; Etapa 24), no en todo el proceso.
 */
enum TipoUnidad {
    AUTO(24, 40, 8.00),
    MOTO(8, 25, 6.00),
    BICICLETA(4, 12, 3.00);

    final int capacidadMaxima;
    final double velocidadPromedio;
    final double costoPorKilometro;

    TipoUnidad(int capacidadMaxima, double velocidadDelEnunciado, double costoPorKilometro) {
        this.capacidadMaxima = capacidadMaxima;
        this.velocidadPromedio = Parametros.decimal("velocidad." + name(), velocidadDelEnunciado);
        this.costoPorKilometro = costoPorKilometro;
    }
}
