package pe.edu.pucp.gamesoft.paqrap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 4.1.3 Heurística constructiva inicial (Clarke & Wright clásico por ahorros,
 *  con control de capacidad y de plazos). Produce la solución inicial de
 *  Búsqueda Tabú y constituye, además, el componente de asignación de
 *  pedidos a unidades exigido para equipos de cuatro integrantes. Esta
 *  primera iteración trabaja con un único almacén central (ver
 *  Compartido.ALMACEN_CENTRAL); la selección entre varios almacenes se
 *  incorpora cuando el GestorInventario esté integrado. */
class Heuristicaconstructiva {

    static Solucion construirSolucionInicial(List<Pedido> pedidosPendientes,
                                              List<UnidadTransporte> flotaDisponible) {

        // Paso 1: pedidos ordenados por hora límite creciente (lo más urgente
        // primero). Con pedidos leídos del archivo, la hora de registro varía,
        // así que la urgencia real es horaLimite() y no el plazo hl.
        List<Pedido> ped = new ArrayList<>(pedidosPendientes);
        ped.sort(Comparator.comparingDouble(Pedido::horaLimite));
        int n = ped.size();
        List<TipoUnidad> tipos = tiposDeFlota(flotaDisponible);
        int capacidadMayor = 0;
        for (TipoUnidad t : tipos) capacidadMayor = Math.max(capacidadMayor, t.capacidadMaxima);

        // Paso 2: cada pedido arranca como una ruta de un solo cliente
        List<List<Integer>> rutas = new ArrayList<>();
        int[] rutaDe = new int[n];
        for (int i = 0; i < n; i++) {
            List<Integer> r = new ArrayList<>();
            r.add(i);
            rutas.add(r);
            rutaDe[i] = i;
        }

        // Paso 3: ahorro de unir i con j: s(i,j) = d(0,i) + d(0,j) - d(i,j).
        // Solo interesan los ahorros positivos.
        List<double[]> ahorros = new ArrayList<>();   // {ahorro, i, j}
        Almacen al = Compartido.ALMACEN_CENTRAL;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Pedido a = ped.get(i), b = ped.get(j);
                double s = Compartido.distancia(al.x, al.y, a.x, a.y)
                         + Compartido.distancia(al.x, al.y, b.x, b.y)
                         - Compartido.distancia(a.x, a.y, b.x, b.y);
                if (s > 0) ahorros.add(new double[]{s, i, j});
            }
        }
        ahorros.sort((p, q) -> Double.compare(q[0], p[0]));   // descendente (orden estable ante empates)

        // Paso 4: fusión clásica por EXTREMOS. Se une el final de una ruta con
        // el inicio de la otra (i al final de Ri y j al inicio de Rj, o al
        // revés). No se invierten rutas: con plazos, invertir cambia las horas
        // de llegada. La fusión se acepta si la carga cabe en algún tipo de la
        // flota y no aumenta las entregas fuera de plazo. Se recorren TODOS los
        // ahorros positivos, aunque ya haya menos rutas que vehículos.
        for (double[] a : ahorros) {
            int i = (int) a[1], j = (int) a[2];
            int ri = rutaDe[i], rj = rutaDe[j];
            if (ri == rj) continue;
            List<Integer> rutaI = rutas.get(ri), rutaJ = rutas.get(rj);

            List<List<Integer>> opciones = new ArrayList<>();
            if (ultimo(rutaI) == i && primero(rutaJ) == j) opciones.add(concatenar(rutaI, rutaJ));
            if (ultimo(rutaJ) == j && primero(rutaI) == i) opciones.add(concatenar(rutaJ, rutaI));
            if (opciones.isEmpty()) continue;   // i o j no están en un extremo útil
            if (cantidadTotal(opciones.get(0), ped) > capacidadMayor) continue;

            int tardeAntes = tardeMinimo(rutaI, ped, tipos) + tardeMinimo(rutaJ, ped, tipos);
            List<Integer> elegida = null;
            int tardeElegida = Integer.MAX_VALUE;
            double kmElegida = Double.MAX_VALUE;
            for (List<Integer> op : opciones) {
                int tarde = tardeMinimo(op, ped, tipos);
                double km = kilometros(op, ped);
                if (tarde <= tardeAntes && (tarde < tardeElegida || (tarde == tardeElegida && km < kmElegida))) {
                    elegida = op;
                    tardeElegida = tarde;
                    kmElegida = km;
                }
            }
            if (elegida == null) continue;   // la fusión empeoraría los plazos

            rutas.set(ri, elegida);
            rutas.set(rj, null);
            for (int k : rutaJ) rutaDe[k] = ri;
        }

        List<List<Pedido>> gruposFinales = new ArrayList<>();
        for (List<Integer> r : rutas) {
            if (r == null) continue;
            List<Pedido> g = new ArrayList<>();
            for (int k : r) g.add(ped.get(k));
            gruposFinales.add(g);
        }
        gruposFinales.sort((g1, g2) -> Integer.compare(cantidadTotal(g2), cantidadTotal(g1)));

        // Paso 5: cada grupo, del más cargado al menos cargado, se asigna a la
        // unidad libre donde cabe que da menos entregas tarde y, a igualdad,
        // menor costo. Los grupos más cargados eligen primero, así que se
        // quedan con las unidades de mayor capacidad.
        List<UnidadTransporte> libres = new ArrayList<>(flotaDisponible);
        Solucion solucion = new Solucion();
        List<Pedido> sinAsignar = new ArrayList<>();

        for (List<Pedido> g : gruposFinales) {
            RutaAlg mejor = null;
            for (UnidadTransporte u : libres) {
                if (cantidadTotal(g) > u.tipo.capacidadMaxima) continue;
                RutaAlg r = construirRuta(u, g);
                if (mejor == null || esMejorRuta(r, mejor)) mejor = r;
            }
            if (mejor != null) {
                libres.remove(mejor.unidad);
                solucion.rutas.add(mejor);
            } else {
                sinAsignar.addAll(g);
            }
        }

        // Paso de inserción (si quedaron más grupos que vehículos): cada pedido
        // restante, del más urgente al menos urgente, va a la ruta y POSICIÓN
        // que menos aumentan las entregas tarde y, a igualdad, el costo. Se
        // consideran las rutas ya armadas y, como opción, abrir una ruta nueva
        // en una unidad libre (una por tipo). A igualdad gana una ruta existente.
        sinAsignar.sort(Comparator.comparingDouble(Pedido::horaLimite));
        List<Pedido> definitivamenteSinAsignar = new ArrayList<>();
        for (Pedido p : sinAsignar) {
            List<RutaAlg> candidatas = new ArrayList<>(solucion.rutas);
            List<TipoUnidad> tiposLibres = new ArrayList<>();
            for (UnidadTransporte u : libres) {
                if (tiposLibres.contains(u.tipo)) continue;
                tiposLibres.add(u.tipo);
                RutaAlg nueva = new RutaAlg();
                nueva.unidad = u;
                candidatas.add(nueva);
            }
            OperadoresVecindario.PosicionInsercion mejor = OperadoresVecindario.mejorInsercion(solucion, candidatas, p);
            if (mejor == null) {
                definitivamenteSinAsignar.add(p);
                continue;
            }
            mejor.aplicar();
            if (!solucion.rutas.contains(mejor.ruta)) {   // se abrió una ruta en una unidad libre
                libres.remove(mejor.ruta.unidad);
                solucion.rutas.add(mejor.ruta);
            }
        }

        solucion.pedidosSinAsignar = definitivamenteSinAsignar;
        Compartido.evaluarSolucion(solucion);
        return solucion;
    }

    private static RutaAlg construirRuta(UnidadTransporte u, List<Pedido> pedidos) {
        RutaAlg r = new RutaAlg();
        r.unidad = u;
        for (Pedido p : pedidos) r.paradas.add(ParadaAlg.entrega(p));
        Compartido.recalcularDistanciaYCosto(r);
        return r;
    }

    /** true si "a" tiene menos entregas tarde que "b" o, a igualdad, menor costo. */
    private static boolean esMejorRuta(RutaAlg a, RutaAlg b) {
        int ta = Compartido.pedidosTarde(a), tb = Compartido.pedidosTarde(b);
        if (ta != tb) return ta < tb;
        return a.costo < b.costo;
    }

    /** Entregas tarde de la secuencia con el tipo de la flota (donde quepa) que
     *  menos tardanzas produce. Todavía no se sabe qué unidad recibirá la
     *  ruta, así que se usa el mejor caso posible. */
    private static int tardeMinimo(List<Integer> secuencia, List<Pedido> ped, List<TipoUnidad> tipos) {
        List<Pedido> g = new ArrayList<>();
        for (int k : secuencia) g.add(ped.get(k));
        int carga = cantidadTotal(g);
        int mejor = Integer.MAX_VALUE;
        for (TipoUnidad t : tipos) {
            if (carga > t.capacidadMaxima) continue;
            RutaAlg r = construirRuta(new UnidadTransporte("tmp", t), g);
            mejor = Math.min(mejor, Compartido.pedidosTarde(r));
        }
        return mejor;
    }

    private static double kilometros(List<Integer> secuencia, List<Pedido> ped) {
        Almacen al = Compartido.ALMACEN_CENTRAL;
        double km = 0;
        int px = al.x, py = al.y;
        for (int k : secuencia) {
            Pedido p = ped.get(k);
            km += Compartido.distancia(px, py, p.x, p.y);
            px = p.x;
            py = p.y;
        }
        return km + Compartido.distancia(px, py, al.x, al.y);
    }

    private static List<TipoUnidad> tiposDeFlota(List<UnidadTransporte> flota) {
        List<TipoUnidad> tipos = new ArrayList<>();
        for (UnidadTransporte u : flota) if (!tipos.contains(u.tipo)) tipos.add(u.tipo);
        return tipos;
    }

    private static int primero(List<Integer> r) {
        return r.get(0);
    }

    private static int ultimo(List<Integer> r) {
        return r.get(r.size() - 1);
    }

    private static List<Integer> concatenar(List<Integer> a, List<Integer> b) {
        List<Integer> c = new ArrayList<>(a);
        c.addAll(b);
        return c;
    }

    private static int cantidadTotal(List<Integer> secuencia, List<Pedido> ped) {
        int t = 0;
        for (int k : secuencia) t += ped.get(k).cantidad;
        return t;
    }

    private static int cantidadTotal(List<Pedido> g) {
        int t = 0;
        for (Pedido p : g) t += p.cantidad;
        return t;
    }
}
