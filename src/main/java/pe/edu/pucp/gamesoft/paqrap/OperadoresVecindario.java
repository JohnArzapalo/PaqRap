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
 *  - Reparto (Etapa 31, SI-28, solo Tabú): divide o une partes de un pedido
 *    entre unidades, porque las unidades entregan productos y no pedidos.
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
     *  posición factible de cualquier ruta, incluidas las vacías. La entrega va
     *  entera (la usa la búsqueda local del AG, cuyo cromosoma no reparte). */
    static Movimiento insercion(Solucion base, Random azar) {
        return insercion(base, azar, false);
    }

    /** Inserción; con conReparto (Búsqueda Tabú, SI-28), si la entrega no cabe
     *  entera a tiempo, se inserta la pieza más grande que sí cabe y el resto de
     *  sus productos queda sin asignar para la siguiente inserción (mejorPieza). */
    static Movimiento insercion(Solucion base, Random azar, boolean conReparto) {
        if (base.pedidosSinAsignar.isEmpty()) return null;
        Solucion s = base.copiar();
        Pedido p = s.pedidosSinAsignar.get(azar.nextInt(s.pedidosSinAsignar.size()));
        Pieza pieza = conReparto ? mejorPieza(s, s.rutas, p) : Pieza.entera(mejorInsercion(s, s.rutas, p));
        if (pieza == null) return null;   // no cabe en ninguna unidad
        pieza.posicion.aplicar();
        s.pedidosSinAsignar.remove(p);
        if (pieza.resto != null) s.pedidosSinAsignar.add(pieza.resto);
        return siFactible(s, Movimiento.insercion());
    }

    /** Una pieza de una entrega lista para insertar y los productos que quedan sin asignar. */
    static final class Pieza {
        PosicionInsercion posicion;   // dónde va la pieza
        Pedido resto;                 // productos que no entraron (null si la entrega va entera)

        static Pieza entera(PosicionInsercion pos) {
            if (pos == null) return null;
            Pieza p = new Pieza();
            p.posicion = pos;
            return p;
        }
    }

    /**
     * Reparto por productos (Etapa 31, SI-28; indicación del profesor): las
     * unidades entregan productos, no pedidos. Si la entrega cabe entera y a
     * tiempo en alguna unidad, va entera. Si no, una unidad lleva n productos
     * (la pieza más grande que llega a tiempo, en su mejor posición) y el resto
     * queda en una parte nueva para otra unidad. Si ninguna pieza llega a
     * tiempo, va entera aunque sea tarde; si no cabe entera, la pieza más grande
     * que quepa. Devuelve null si no cabe ni un producto.
     * La pieza conserva el id de la entrega (estabilidad) y el resto recibe uno nuevo.
     * Sin reparto (reparto.productos=no) o con los productos ya a bordo, solo la entrega entera.
     */
    static Pieza mejorPieza(Solucion s, List<RutaAlg> rutas, Pedido p) {
        Contexto cx = Contexto.actual();
        PosicionInsercion entera = mejorInsercion(s, rutas, p);
        if (!cx.reparto || !p.enAlmacen() || p.cantidad < 2 || (entera != null && entera.deltaTarde == 0))
            return Pieza.entera(entera);
        PosicionInsercion cualquiera = null;
        int nCualquiera = 0;
        for (int n = p.cantidad - 1; n >= 1; n--) {
            PosicionInsercion pos = mejorInsercion(s, rutas, p.conCantidad(n));
            if (pos == null) continue;
            if (pos.deltaTarde == 0) return conResto(pos, p, n, cx);
            if (cualquiera == null) { cualquiera = pos; nCualquiera = n; }
        }
        if (entera != null) return Pieza.entera(entera);
        return cualquiera == null ? null : conResto(cualquiera, p, nCualquiera, cx);
    }

    private static Pieza conResto(PosicionInsercion pos, Pedido p, int n, Contexto cx) {
        Pieza pieza = Pieza.entera(pos);
        pieza.resto = p.parte(cx.nuevoIdParte(p), p.cantidad - n);
        return pieza;
    }

    /**
     * Reparto (Etapa 31, SI-28; solo Búsqueda Tabú): mueve PRODUCTOS entre unidades.
     *  - Dividir: k productos (al azar) de una entrega en almacén pasan a otra
     *    unidad, en su mejor posición; en la original queda el resto.
     *  - Unir: una parte se suma a otra parte del mismo pedido (en otra unidad o
     *    en otra posición), lo que deshace repartos que ya no convienen.
     * Solo con productos en almacén: los que ya van a bordo no cambian de unidad
     * (salvo por trasvase). Atributo tabú: (pedido original, unidad).
     */
    static Movimiento reparto(Solucion base, Random azar) {
        List<int[]> candidatas = new ArrayList<>();   // {ruta, posición} de entregas en almacén
        for (int i = 0; i < base.rutas.size(); i++) {
            List<ParadaAlg> ps = base.rutas.get(i).paradas;
            for (int j = 0; j < ps.size(); j++)
                if (ps.get(j).tipo == TipoParada.ENTREGA && ps.get(j).pedido.enAlmacen()) candidatas.add(new int[]{i, j});
        }
        if (candidatas.isEmpty()) return null;
        Solucion s = base.copiar();
        int[] c = candidatas.get(azar.nextInt(candidatas.size()));
        RutaAlg ra = s.rutas.get(c[0]);
        Pedido p = ra.paradas.get(c[1]).pedido;

        List<int[]> hermanas = new ArrayList<>();   // otras partes del mismo pedido, también en almacén
        for (int[] o : candidatas)
            if ((o[0] != c[0] || o[1] != c[1]) && s.rutas.get(o[0]).paradas.get(o[1]).pedido.idOriginal.equals(p.idOriginal))
                hermanas.add(o);

        if (!hermanas.isEmpty() && (p.cantidad < 2 || azar.nextBoolean())) {   // unir
            int[] h = hermanas.get(azar.nextInt(hermanas.size()));
            RutaAlg rb = s.rutas.get(h[0]);
            Pedido q = rb.paradas.get(h[1]).pedido;
            rb.paradas.set(h[1], ParadaAlg.entrega(q.conCantidad(q.cantidad + p.cantidad)));
            ra.paradas.remove(c[1]);   // después del set: si es la misma ruta, h[1] sigue siendo válido
            return siFactible(s, Movimiento.reparto(p.idOriginal, ra.unidad.codigo, rb.unidad.codigo));
        }
        if (p.cantidad < 2) return null;
        RutaAlg rb = s.rutas.get(azar.nextInt(s.rutas.size()));   // dividir
        if (rb == ra) return null;
        int k = 1 + azar.nextInt(p.cantidad - 1);
        ra.paradas.set(c[1], ParadaAlg.entrega(p.conCantidad(p.cantidad - k)));
        PosicionInsercion pos = mejorInsercion(s, List.of(rb), p.parte(Contexto.actual().nuevoIdParte(p), k));
        if (pos == null) return null;
        pos.aplicar();
        return siFactible(s, Movimiento.reparto(p.idOriginal, ra.unidad.codigo, rb.unidad.codigo));
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
