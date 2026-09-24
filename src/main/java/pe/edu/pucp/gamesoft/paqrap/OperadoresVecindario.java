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
 * COPIA de la solución base, la evalúa y la descarta si viola una
 * restricción dura (Compartido.esFactible: capacidad en cada punto de carga,
 * entregas a bordo en su unidad, recargas y trasvases previos, stock de los
 * almacenes, mantenimiento). Devuelve el Movimiento con sus atributos tabú,
 * o null si el vecino no es factible o no cambia nada.
 *
 * Operadores:
 *  - Reubicación, Intercambio e Inserción (ISA 4.2, primera iteración).
 *  - 2-opt y Cross-exchange: PROVISIONALES, verificar contra el ISA §4.2.
 *  - Recarga (Etapa 10.2, solo en la simulación con estado): inserta una
 *    parada RECARGA, cambia su almacén o la elimina (varios viajes por unidad).
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

    private static List<Integer> posicionesEntrega(RutaAlg r) {
        List<Integer> p = new ArrayList<>();
        for (int i = 0; i < r.paradas.size(); i++) if (r.paradas.get(i).tipo == TipoParada.ENTREGA) p.add(i);
        return p;
    }

    private static List<Integer> rutasConEntregas(Solucion s) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < s.rutas.size(); i++) {
            for (ParadaAlg p : s.rutas.get(i).paradas) {
                if (p.tipo == TipoParada.ENTREGA) { idx.add(i); break; }
            }
        }
        return idx;
    }

    private static List<Integer> rutasNoVacias(Solucion s) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < s.rutas.size(); i++) if (!s.rutas.get(i).estaVacia()) idx.add(i);
        return idx;
    }

    /** Evalúa la copia modificada y la devuelve como movimiento si es factible. */
    private static Movimiento siFactible(Solucion s, Movimiento m) {
        Compartido.evaluarSolucion(s);
        if (!Compartido.esFactible(s)) return null;
        m.solucion = s;
        return m;
    }

    /** Reubicación: mueve una entrega a otra posición de su ruta o a otra ruta (incluidas las vacías). */
    static Movimiento reubicacion(Solucion base, Random azar) {
        List<Integer> origenes = rutasConEntregas(base);
        if (origenes.isEmpty()) return null;
        Solucion s = base.copiar();
        int io = origenes.get(azar.nextInt(origenes.size()));
        RutaAlg rutaOrigen = s.rutas.get(io);
        List<Integer> entregas = posicionesEntrega(rutaOrigen);
        int posOrigen = entregas.get(azar.nextInt(entregas.size()));
        int id = azar.nextInt(s.rutas.size());
        RutaAlg rutaDestino = s.rutas.get(id);

        ParadaAlg parada = rutaOrigen.paradas.remove(posOrigen);
        int posDestino = azar.nextInt(rutaDestino.paradas.size() + 1);
        if (io == id && posDestino == posOrigen) return null;   // no cambia nada
        rutaDestino.paradas.add(posDestino, parada);
        return siFactible(s, Movimiento.reubicacion(parada.id(), rutaOrigen.unidad.codigo, posOrigen,
                rutaDestino.unidad.codigo, posDestino));
    }

    /** Intercambio: intercambia dos entregas de rutas distintas o dos posiciones de la misma ruta. */
    static Movimiento intercambio(Solucion base, Random azar) {
        List<Integer> conEntregas = rutasConEntregas(base);
        if (conEntregas.isEmpty()) return null;
        Solucion s = base.copiar();
        int ia = conEntregas.get(azar.nextInt(conEntregas.size()));
        int ib = conEntregas.get(azar.nextInt(conEntregas.size()));
        RutaAlg ra = s.rutas.get(ia);
        RutaAlg rb = s.rutas.get(ib);
        List<Integer> ea = posicionesEntrega(ra), eb = posicionesEntrega(rb);
        int i = ea.get(azar.nextInt(ea.size()));
        int j = eb.get(azar.nextInt(eb.size()));
        if (ia == ib && i == j) return null;

        ParadaAlg pa = ra.paradas.get(i);
        ParadaAlg pb = rb.paradas.get(j);
        ra.paradas.set(i, pb);
        rb.paradas.set(j, pa);
        return siFactible(s, Movimiento.intercambio(pa.id(), ra.unidad.codigo, i, pb.id(), rb.unidad.codigo, j));
    }

    /** 2-opt (PROVISIONAL, verificar contra el ISA §4.2): invierte el tramo [i..j] de una ruta. */
    static Movimiento dosOpt(Solucion base, Random azar) {
        List<Integer> candidatas = new ArrayList<>();
        for (int k = 0; k < base.rutas.size(); k++) if (base.rutas.get(k).paradas.size() >= 2) candidatas.add(k);
        if (candidatas.isEmpty()) return null;
        Solucion s = base.copiar();
        RutaAlg r = s.rutas.get(candidatas.get(azar.nextInt(candidatas.size())));
        int n = r.paradas.size();
        int i = azar.nextInt(n - 1);
        int j = i + 1 + azar.nextInt(n - 1 - i);
        String p = r.paradas.get(i).id();
        String q = r.paradas.get(j).id();
        Collections.reverse(r.paradas.subList(i, j + 1));
        return siFactible(s, Movimiento.dosOpt(p, q, r.unidad.codigo, i, j));
    }

    /** Cross-exchange (PROVISIONAL, verificar contra el ISA §4.2): intercambia
     *  un segmento de 1 a MAX_SEGMENTO_CROSS paradas de una ruta con un
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
        return siFactible(s, Movimiento.crossExchange(ids(segA), ra.unidad.codigo, ids(segB), rb.unidad.codigo));
    }

    private static List<String> ids(List<ParadaAlg> paradas) {
        List<String> ids = new ArrayList<>();
        for (ParadaAlg p : paradas) ids.add(p.id());
        return ids;
    }

    /** Recarga (Etapa 10.2, simulación con estado): en una ruta al azar, inserta
     *  una parada RECARGA en un almacén al azar, o cambia el almacén de una
     *  RECARGA existente, o la elimina. Permite armar y deshacer viajes. */
    static Movimiento recarga(Solucion base, Random azar) {
        Contexto cx = Contexto.actual();
        List<Integer> noVacias = rutasNoVacias(base);
        if (noVacias.isEmpty() || cx.almacenes.isEmpty()) return null;
        Solucion s = base.copiar();
        RutaAlg r = s.rutas.get(noVacias.get(azar.nextInt(noVacias.size())));
        List<Integer> recargas = new ArrayList<>();
        for (int k = 0; k < r.paradas.size(); k++) if (r.paradas.get(k).tipo == TipoParada.RECARGA) recargas.add(k);
        Almacen otro = cx.almacenes.get(azar.nextInt(cx.almacenes.size())).almacen;
        if (!recargas.isEmpty() && azar.nextBoolean()) {
            int pos = recargas.get(azar.nextInt(recargas.size()));
            if (azar.nextBoolean() && otro != r.paradas.get(pos).almacen) r.paradas.set(pos, ParadaAlg.recarga(otro));
            else r.paradas.remove(pos);
        } else {
            r.paradas.add(azar.nextInt(r.paradas.size() + 1), ParadaAlg.recarga(otro));
        }
        return siFactible(s, Movimiento.recarga());
    }

    /** Inserción: pone una entrega sin asignar (elegida al azar) en la mejor
     *  posición factible de cualquier ruta, incluidas las vacías. */
    static Movimiento insercion(Solucion base, Random azar) {
        if (base.pedidosSinAsignar.isEmpty()) return null;
        Solucion s = base.copiar();
        Pedido p = s.pedidosSinAsignar.get(azar.nextInt(s.pedidosSinAsignar.size()));
        PosicionInsercion mejor = mejorInsercion(s, s.rutas, p);
        if (mejor == null) return null;   // no cabe en ninguna unidad
        mejor.aplicar();
        s.pedidosSinAsignar.remove(p);
        return siFactible(s, Movimiento.insercion());
    }

    /** Resultado de buscar dónde insertar una entrega. */
    static class PosicionInsercion {
        RutaAlg ruta;
        int posicion;
        List<ParadaAlg> paradas;   // la entrega, precedida de una RECARGA o un TRASVASE si hace falta
        int deltaTarde;
        double deltaCosto;

        void aplicar() {
            ruta.paradas.addAll(posicion, paradas);
        }
    }

    /**
     * Posición (ruta y lugar) que menos aumenta las entregas tarde de la ruta
     * y, a igualdad, su costo, entre las inserciones factibles (restricciones
     * duras y stock de almacenes). Variantes por posición:
     *  - solo la entrega;
     *  - TRASVASE en la unidad averiada + entrega (si los paquetes están en una averiada);
     *  - RECARGA en cada almacén + entrega (simulación con estado: permite abrir un
     *    viaje nuevo o cargar una unidad que está en ruta).
     * A igualdad gana la primera encontrada. Deja las rutas sin cambios.
     * La usan la Inserción de Tabú y el paso de inserción de Clarke & Wright.
     * @param s     solución a la que pertenecen (o se agregarán) las rutas, para el stock
     * @param rutas rutas candidatas (pueden incluir rutas nuevas que aún no están en s)
     */
    static PosicionInsercion mejorInsercion(Solucion s, List<RutaAlg> rutas, Pedido p) {
        Contexto cx = Contexto.actual();
        int[] usoTotal = new int[cx.almacenes.size()];
        for (RutaAlg r : s.rutas) {
            int[] u = Compartido.evaluarRuta(r).usoAlmacen;
            for (int i = 0; i < u.length; i++) usoTotal[i] += u[i];
        }
        List<List<ParadaAlg>> variantes = new ArrayList<>();
        variantes.add(List.of(ParadaAlg.entrega(p)));
        if (p.enAveriada != null) {
            Contexto.Averiada av = cx.averiadas.get(p.enAveriada);
            if (av != null) variantes.add(List.of(ParadaAlg.trasvase(av.codigo, av.x, av.y), ParadaAlg.entrega(p)));
        } else if (cx.conEstado && p.enAlmacen()) {
            for (Contexto.AlmacenPlan a : cx.almacenes)
                variantes.add(List.of(ParadaAlg.recarga(a.almacen), ParadaAlg.entrega(p)));
        }

        PosicionInsercion mejor = null;
        for (RutaAlg r : rutas) {
            Compartido.EvalRuta antes = Compartido.evaluarRuta(r);
            boolean enSolucion = s.rutas.contains(r);
            for (int pos = 0; pos <= r.paradas.size(); pos++) {
                for (List<ParadaAlg> v : variantes) {
                    r.paradas.addAll(pos, v);
                    Compartido.EvalRuta despues = Compartido.evaluarRuta(r);
                    r.paradas.subList(pos, pos + v.size()).clear();
                    if (!despues.factible) continue;
                    boolean stockOk = true;
                    for (int i = 0; i < usoTotal.length && stockOk; i++) {
                        int otros = usoTotal[i] - (enSolucion ? antes.usoAlmacen[i] : 0);
                        stockOk = otros + despues.usoAlmacen[i] <= cx.almacenes.get(i).stock;
                    }
                    if (!stockOk) continue;
                    int deltaTarde = despues.tarde - antes.tarde;
                    double deltaCosto = despues.costo - antes.costo;
                    if (mejor == null || deltaTarde < mejor.deltaTarde
                            || (deltaTarde == mejor.deltaTarde && deltaCosto < mejor.deltaCosto)) {
                        mejor = new PosicionInsercion();
                        mejor.ruta = r;
                        mejor.posicion = pos;
                        mejor.paradas = new ArrayList<>(v);
                        mejor.deltaTarde = deltaTarde;
                        mejor.deltaCosto = deltaCosto;
                    }
                }
            }
            Compartido.recalcularDistanciaYCosto(r);
        }
        return mejor;
    }
}
