package pe.edu.pucp.gamesoft.paqrap;

/**
 * Un paso de la línea de tiempo de una ruta, tal como la calcula
 * Compartido.evaluarRuta (horas relativas al instante de planificación).
 * El Simulador lo convierte a minutos absolutos y lo ejecuta.
 *  - TRAMO: viaje de (x1,y1) a (x2,y2) entre inicioH y finH.
 *  - ENTREGA: llegada a la entrega en inicioH (cuenta para el plazo) y fin del acondicionamiento en finH.
 *  - RECARGA / TRASVASE: carga de "cantidad" paquetes, entre inicioH y finH.
 *  - FIN: la unidad llega al almacén final (queda libre en finH).
 */
final class Hito {
    enum Tipo { TRAMO, ENTREGA, RECARGA, TRASVASE, FIN }

    final Tipo tipo;
    final double inicioH, finH;
    final int x1, y1, x2, y2;
    final ParadaAlg parada;   // null en TRAMO y FIN
    final int cantidad;

    Hito(Tipo tipo, double inicioH, double finH, int x1, int y1, int x2, int y2, ParadaAlg parada, int cantidad) {
        this.tipo = tipo;
        this.inicioH = inicioH;
        this.finH = finH;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.parada = parada;
        this.cantidad = cantidad;
    }
}
