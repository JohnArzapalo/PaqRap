/**
 * Modelo simplificado del dominio para esta primera iteración de verificación
 * de factibilidad (ISA apartado 4.1.2). En próximas iteraciones estas clases
 * se sustituyen por las del Modelo de Clases de Análisis (13.ana.clases.analisis.v01)
 * y las coordenadas se resuelven contra el CalculadorRecorridos real.
 */

/** Capacidad, velocidad y costo por kilómetro de cada tipo de vehículo
 *  (valores del enunciado de la situación auténtica). */
package pe.edu.pucp.gamesoft.paqrap;
enum TipoUnidad {
    AUTO(24, 40, 8.00),
    MOTO(8, 25, 6.00),
    BICICLETA(4, 12, 3.00);

    final int capacidadMaxima;
    final double velocidadPromedio;
    final double costoPorKilometro;

    TipoUnidad(int capacidadMaxima, double velocidadPromedio, double costoPorKilometro) {
        this.capacidadMaxima = capacidadMaxima;
        this.velocidadPromedio = velocidadPromedio;
        this.costoPorKilometro = costoPorKilometro;
    }
}

class Pedido {
    String id;
    int x, y;
    int cantidad;
    int plazoMaximoHoras;   // hl del archivo de ventas: horas límite desde el registro
    double horaRegistro;    // hora (en horas) en que llegó el pedido; 0 = inicio de la instancia

    Pedido(String id, int x, int y, int cantidad, int plazoMaximoHoras) {
        this(id, x, y, cantidad, plazoMaximoHoras, 0.0);
    }

    Pedido(String id, int x, int y, int cantidad, int plazoMaximoHoras, double horaRegistro) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.cantidad = cantidad;
        this.plazoMaximoHoras = plazoMaximoHoras;
        this.horaRegistro = horaRegistro;
    }

    /** Hora límite de entrega = hora de registro + plazo (hl). */
    double horaLimite() {
        return horaRegistro + plazoMaximoHoras;
    }

    public String toString() {
        return id;
    }
}

class UnidadTransporte {
    String codigo;
    TipoUnidad tipo;

    UnidadTransporte(String codigo, TipoUnidad tipo) {
        this.codigo = codigo;
        this.tipo = tipo;
    }
}

class Almacen {
    String codigo;
    int x, y;

    Almacen(String codigo, int x, int y) {
        this.codigo = codigo;
        this.x = x;
        this.y = y;
    }
}