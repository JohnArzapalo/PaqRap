package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.List;

/**
 * Un vecino ya evaluado, con sus atributos tabú. Lo generan los operadores
 * de OperadoresVecindario; lo usan Búsqueda Tabú (con lista tabú) y la
 * búsqueda local del AG (que ignora los atributos).
 */
class Movimiento {
    Solucion solucion;
    /** Atributos que se comprueban: si alguno está vigente, el movimiento es tabú. */
    final List<ParTabu> atributos = new ArrayList<>();
    /** Atributos que se prohíben al ACEPTAR el movimiento (impiden deshacerlo). */
    final List<ParTabu> prohibir = new ArrayList<>();

    boolean esTabu(ListaTabu lista, int iteracionActual) {
        for (ParTabu a : atributos) if (lista.contiene(a, iteracionActual)) return true;
        return false;
    }

    private void agregar(Movimiento otro) {
        atributos.addAll(otro.atributos);
        prohibir.addAll(otro.prohibir);
    }

    /** Reubicación del pedido p de (uOrigen, posOrigen) a (uDestino, posDestino).
     *  Entre rutas: es tabú si lleva p a una unidad de la que salió hace poco
     *  (se compara contra la unidad DESTINO); al aceptarlo se prohíbe (p, uOrigen).
     *  Dentro de la misma ruta: es tabú si devuelve p a una posición que dejó
     *  hace poco; al aceptarlo se prohíbe (p, U, posOrigen). */
    static Movimiento reubicacion(String p, String uOrigen, int posOrigen, String uDestino, int posDestino) {
        Movimiento m = new Movimiento();
        if (uOrigen.equals(uDestino)) {
            m.atributos.add(new ParTabu(p, uDestino, posDestino));
            m.prohibir.add(new ParTabu(p, uOrigen, posOrigen));
        } else {
            m.atributos.add(new ParTabu(p, uDestino));
            m.prohibir.add(new ParTabu(p, uOrigen));
        }
        return m;
    }

    /** Intercambio de p (en uA, posición i) con q (en uB, posición j).
     *  Es la composición de dos reubicaciones: p va a (uB, j) y q a (uA, i). */
    static Movimiento intercambio(String p, String uA, int i, String q, String uB, int j) {
        Movimiento m = new Movimiento();
        m.agregar(reubicacion(p, uA, i, uB, j));
        m.agregar(reubicacion(q, uB, j, uA, i));
        return m;
    }

    /** 2-opt en la ruta U: invierte el tramo [i..j]. Los extremos cambian de
     *  lugar (p pasa de i a j y q de j a i): es tabú devolverlos a su posición
     *  anterior, que es lo que haría repetir la misma inversión. */
    static Movimiento dosOpt(String p, String q, String u, int i, int j) {
        Movimiento m = new Movimiento();
        m.agregar(reubicacion(p, u, i, u, j));
        m.agregar(reubicacion(q, u, j, u, i));
        return m;
    }

    /** Cross-exchange: el segmento segA sale de uA hacia uB y segB sale de uB
     *  hacia uA. Cada pedido movido se trata como una reubicación entre rutas. */
    static Movimiento crossExchange(List<String> segA, String uA, List<String> segB, String uB) {
        Movimiento m = new Movimiento();
        for (String p : segA) m.agregar(reubicacion(p, uA, ParTabu.CUALQUIERA, uB, ParTabu.CUALQUIERA));
        for (String q : segB) m.agregar(reubicacion(q, uB, ParTabu.CUALQUIERA, uA, ParTabu.CUALQUIERA));
        return m;
    }

    /** Inserción desde "sin asignar": nunca es tabú y no prohíbe nada, porque
     *  ningún operador devuelve pedidos a "sin asignar" (no hay movimiento
     *  inverso que evitar). */
    static Movimiento insercion() {
        return new Movimiento();
    }

    /** Recarga (insertar, cambiar de almacén o quitar una parada RECARGA): no
     *  lleva atributo tabú; el costo del viaje extra ya desalienta repetirla sin
     *  motivo. Decisión documentada, a revisar si se observan ciclos. */
    static Movimiento recarga() {
        return new Movimiento();
    }
}
