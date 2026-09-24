package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Operadores de vecindario compartidos por Búsqueda Tabú (4.2) y por la
 * búsqueda local memética del AG (4.3). Cada operador trabaja sobre una
 * COPIA de la solución base, verifica la capacidad (restricción dura) con
 * Compartido.cumpleCapacidad, evalúa la copia y devuelve el Movimiento con
 * sus atributos tabú, o null si el vecino no es factible o no cambia nada.
 *
 * Operadores:
 *  - Reubicación, Intercambio e Inserción (ISA 4.2, primera iteración).
 *  - 2-opt y Cross-exchange: PROVISIONALES, verificar contra el ISA §4.2
 *    (el ISA no está en la carpeta del proyecto; se eligieron dos
 *    operadores estándar del VRP para completar el vecindario).
 */
final class OperadoresVecindario {

    /** Longitud máxima de los segmentos del cross-exchange. */
    static final int MAX_SEGMENTO_CROSS = 3;

    private OperadoresVecindario() {
    }

    /** Copia la solución y agrega como rutas vacías las unidades de la flota sin ruta. */
    static Solucion conUnidadesLibres(Solucion base, List<UnidadTransporte> flota) {
        Solucion s = base.copiar();
        Set<String> conRuta = new HashSet<>();
        for (RutaAlg r : s.rutas) conRuta.add(r.unidad.codigo);
        for (UnidadTransporte u : flota) {
            if (conRuta.add(u.codigo)) {
                RutaAlg vacia = new RutaAlg();
                vacia.unidad = u;
                s.rutas.add(vacia);
            }
        }
        return s;
    }

    private static List<Integer> rutasNoVacias(Solucion s) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < s.rutas.size(); i++) if (!s.rutas.get(i).estaVacia()) idx.add(i);
        return idx;
    }

    /** Reubicación: mueve una entrega a otra posición de su ruta o a otra ruta (incluidas las vacías). */
    static Movimiento reubicacion(Solucion base, Random azar) {
        List<Integer> origenes = rutasNoVacias(base);
        if (origenes.isEmpty()) return null;
        Solucion s = base.copiar();
        int io = origenes.get(azar.nextInt(origenes.size()));
        RutaAlg rutaOrigen = s.rutas.get(io);
        int posOrigen = azar.nextInt(rutaOrigen.paradas.size());
        int id = azar.nextInt(s.rutas.size());
        RutaAlg rutaDestino = s.rutas.get(id);

        ParadaAlg parada = rutaOrigen.paradas.remove(posOrigen);
        int posDestino = azar.nextInt(rutaDestino.paradas.size() + 1);
        if (io == id && posDestino == posOrigen) return null;   // no cambia nada
        rutaDestino.paradas.add(posDestino, parada);
        if (!Compartido.cumpleCapacidad(rutaDestino)) return null;

        Compartido.evaluarSolucion(s);
        Movimiento m = Movimiento.reubicacion(parada.pedido.id, rutaOrigen.unidad.codigo, posOrigen,
                                              rutaDestino.unidad.codigo, posDestino);
        m.solucion = s;
        return m;
    }

    /** Intercambio: intercambia dos entregas de rutas distintas o dos posiciones de la misma ruta. */
    static Movimiento intercambio(Solucion base, Random azar) {
        List<Integer> noVacias = rutasNoVacias(base);
        if (noVacias.isEmpty()) return null;
        Solucion s = base.copiar();
        int ia = noVacias.get(azar.nextInt(noVacias.size()));
        int ib = noVacias.get(azar.nextInt(noVacias.size()));
        RutaAlg ra = s.rutas.get(ia);
        RutaAlg rb = s.rutas.get(ib);
        int i = azar.nextInt(ra.paradas.size());
        int j = azar.nextInt(rb.paradas.size());
        if (ia == ib && i == j) return null;

        ParadaAlg pa = ra.paradas.get(i);
        ParadaAlg pb = rb.paradas.get(j);
        ra.paradas.set(i, pb);
        rb.paradas.set(j, pa);
        if (!Compartido.cumpleCapacidad(ra) || !Compartido.cumpleCapacidad(rb)) return null;

        Compartido.evaluarSolucion(s);
        Movimiento m = Movimiento.intercambio(pa.pedido.id, ra.unidad.codigo, i,
                                              pb.pedido.id, rb.unidad.codigo, j);
        m.solucion = s;
        return m;
    }

    /** 2-opt (PROVISIONAL, verificar contra el ISA §4.2): invierte el tramo
     *  [i..j] de una ruta. No cambia la carga, así que siempre cumple capacidad. */
    static Movimiento dosOpt(Solucion base, Random azar) {
        List<Integer> candidatas = new ArrayList<>();
        for (int k = 0; k < base.rutas.size(); k++) if (base.rutas.get(k).paradas.size() >= 2) candidatas.add(k);
        if (candidatas.isEmpty()) return null;
        Solucion s = base.copiar();
        RutaAlg r = s.rutas.get(candidatas.get(azar.nextInt(candidatas.size())));
        int n = r.paradas.size();
        int i = azar.nextInt(n - 1);
        int j = i + 1 + azar.nextInt(n - 1 - i);
        String p = r.paradas.get(i).pedido.id;
        String q = r.paradas.get(j).pedido.id;
        Collections.reverse(r.paradas.subList(i, j + 1));

        Compartido.evaluarSolucion(s);
        Movimiento m = Movimiento.dosOpt(p, q, r.unidad.codigo, i, j);
        m.solucion = s;
        return m;
    }

    /** Cross-exchange (PROVISIONAL, verificar contra el ISA §4.2): intercambia
     *  un segmento de 1 a MAX_SEGMENTO_CROSS entregas de una ruta con un
     *  segmento de otra ruta, conservando el orden interno de cada segmento. */
    static Movimiento crossExchange(Solucion base, Random azar) {
        List<Integer> noVacias = rutasNoVacias(base);
        if (noVacias.size() < 2) return null;
        Solucion s = base.copiar();
        int ia = noVacias.get(azar.nextInt(noVacias.size()));
        int ib = noVacias.get(azar.nextInt(noVacias.size()));
        if (ia == ib) return null;
        RutaAlg ra = s.rutas.get(ia);
        RutaAlg rb = s.rutas.get(ib);
        int la = 1 + azar.nextInt(Math.min(MAX_SEGMENTO_CROSS, ra.paradas.size()));
        int lb = 1 + azar.nextInt(Math.min(MAX_SEGMENTO_CROSS, rb.paradas.size()));
        int i = azar.nextInt(ra.paradas.size() - la + 1);
        int j = azar.nextInt(rb.paradas.size() - lb + 1);

        List<ParadaAlg> segA = new ArrayList<>(ra.paradas.subList(i, i + la));
        List<ParadaAlg> segB = new ArrayList<>(rb.paradas.subList(j, j + lb));
        ra.paradas.subList(i, i + la).clear();
        ra.paradas.addAll(i, segB);
        rb.paradas.subList(j, j + lb).clear();
        rb.paradas.addAll(j, segA);
        if (!Compartido.cumpleCapacidad(ra) || !Compartido.cumpleCapacidad(rb)) return null;

        Compartido.evaluarSolucion(s);
        Movimiento m = Movimiento.crossExchange(ids(segA), ra.unidad.codigo, ids(segB), rb.unidad.codigo);
        m.solucion = s;
        return m;
    }

    private static List<String> ids(List<ParadaAlg> paradas) {
        List<String> ids = new ArrayList<>();
        for (ParadaAlg p : paradas) ids.add(p.pedido.id);
        return ids;
    }

    /** Inserción: pone una entrega sin asignar (elegida al azar) en la mejor
     *  posición factible por capacidad de cualquier ruta, incluidas las vacías. */
    static Movimiento insercion(Solucion base, Random azar) {
        if (base.pedidosSinAsignar.isEmpty()) return null;
        Solucion s = base.copiar();
        Pedido p = s.pedidosSinAsignar.get(azar.nextInt(s.pedidosSinAsignar.size()));
        PosicionInsercion mejor = mejorInsercion(s.rutas, p);
        if (mejor == null) return null;   // no cabe en ninguna unidad

        mejor.ruta.paradas.add(mejor.posicion, ParadaAlg.entrega(p));
        s.pedidosSinAsignar.remove(p);
        Compartido.evaluarSolucion(s);
        Movimiento m = Movimiento.insercion();
        m.solucion = s;
        return m;
    }

    /** Resultado de buscar dónde insertar una entrega. */
    static class PosicionInsercion {
        RutaAlg ruta;
        int posicion;
        int deltaTarde;
        double deltaCosto;
    }

    /** Posición (ruta y lugar) que menos aumenta las entregas tarde de la ruta
     *  y, a igualdad, su costo, entre las rutas donde cabe la entrega. La usan
     *  la Inserción de Tabú y el paso de inserción de Clarke & Wright.
     *  Deja las rutas sin cambios (recalcula su distancia y costo). */
    static PosicionInsercion mejorInsercion(List<RutaAlg> rutas, Pedido p) {
        ParadaAlg nueva = ParadaAlg.entrega(p);
        PosicionInsercion mejor = null;
        for (RutaAlg r : rutas) {
            if (r.cargaTotal() + p.cantidad > r.unidad.tipo.capacidadMaxima) continue;
            int tardeAntes = Compartido.pedidosTarde(r);
            Compartido.recalcularDistanciaYCosto(r);
            double costoAntes = r.costo;
            for (int pos = 0; pos <= r.paradas.size(); pos++) {
                r.paradas.add(pos, nueva);
                int deltaTarde = Compartido.pedidosTarde(r) - tardeAntes;
                Compartido.recalcularDistanciaYCosto(r);
                double deltaCosto = r.costo - costoAntes;
                r.paradas.remove(pos);
                if (mejor == null || deltaTarde < mejor.deltaTarde
                        || (deltaTarde == mejor.deltaTarde && deltaCosto < mejor.deltaCosto)) {
                    mejor = new PosicionInsercion();
                    mejor.ruta = r;
                    mejor.posicion = pos;
                    mejor.deltaTarde = deltaTarde;
                    mejor.deltaCosto = deltaCosto;
                }
            }
            Compartido.recalcularDistanciaYCosto(r);
        }
        return mejor;
    }
}
