package pe.edu.pucp.gamesoft.paqrap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 4.1.3 Heurística constructiva inicial (Clarke & Wright adaptado por
 *  holgura crítica). Produce la primera Solución factible y constituye,
 *  además, el componente de asignación de pedidos a unidades exigido para
 *  equipos de cuatro integrantes. Esta primera iteración trabaja con un
 *  único almacén central (ver Compartido.ALMACEN_CENTRAL); la selección
 *  entre varios almacenes se incorpora cuando el GestorInventario esté
 *  integrado. */
class Heuristicaconstructiva {

    static Solucion construirSolucionInicial(List<Pedido> pedidosPendientes,
                                              List<UnidadTransporte> flotaDisponible) {

        // Paso 1 y 2: cada pedido arranca como un grupo de un solo punto,
        // procesado primero por holgura creciente (lo más urgente primero)
        List<Pedido> ordenados = new ArrayList<>(pedidosPendientes);
        ordenados.sort(Comparator.comparingInt(p -> p.plazoMaximoHoras));

        List<List<Pedido>> grupos = new ArrayList<>();
        for (Pedido p : ordenados) {
            List<Pedido> g = new ArrayList<>();
            g.add(p);
            grupos.add(g);
        }

        // Paso 3: ahorro de fusionar cada par de grupos candidatos (Clarke & Wright)
        List<double[]> ahorros = new ArrayList<>();   // {ahorro, i, j}
        Almacen al = Compartido.ALMACEN_CENTRAL;
        for (int i = 0; i < grupos.size(); i++) {
            for (int j = i + 1; j < grupos.size(); j++) {
                Pedido a = grupos.get(i).get(0);
                Pedido b = grupos.get(j).get(0);
                double s = Compartido.distancia(al.x, al.y, a.x, a.y)
                         + Compartido.distancia(al.x, al.y, b.x, b.y)
                         - Compartido.distancia(a.x, a.y, b.x, b.y);
                ahorros.add(new double[]{s, i, j});
            }
        }
        ahorros.sort((p, q) -> Double.compare(q[0], p[0]));   // descendente

        int[] padre = new int[grupos.size()];
        for (int i = 0; i < padre.length; i++) padre[i] = i;

        // Paso 4: fusionar mientras se mantenga factible, sin bajar del
        // número de unidades disponibles (así queda un grupo por vehículo)
        int activos = grupos.size();
        for (double[] a : ahorros) {
            if (activos <= flotaDisponible.size()) break;
            int i = (int) a[1], j = (int) a[2];
            int ri = raiz(padre, i), rj = raiz(padre, j);
            if (ri == rj || grupos.get(ri) == null || grupos.get(rj) == null) continue;

            List<Pedido> fusion = new ArrayList<>(grupos.get(ri));
            fusion.addAll(grupos.get(rj));
            if (cabeEnAlgunTipo(fusion, flotaDisponible)) {
                grupos.set(ri, fusion);
                grupos.set(rj, null);
                padre[rj] = ri;
                activos--;
            }
        }

        List<List<Pedido>> gruposFinales = new ArrayList<>();
        for (List<Pedido> g : grupos) if (g != null) gruposFinales.add(g);
        gruposFinales.sort((g1, g2) -> Integer.compare(cantidadTotal(g2), cantidadTotal(g1)));

        // Paso 5: cada grupo final se asigna a una unidad concreta,
        // priorizando emparejar los grupos más cargados con las
        // unidades de mayor capacidad
        List<UnidadTransporte> libres = new ArrayList<>(flotaDisponible);
        libres.sort((u1, u2) -> Integer.compare(u2.tipo.capacidadMaxima, u1.tipo.capacidadMaxima));

        Solucion solucion = new Solucion();
        List<Pedido> sinAsignar = new ArrayList<>();

        for (List<Pedido> g : gruposFinales) {
            UnidadTransporte elegida = null;
            for (UnidadTransporte u : libres) {
                if (cantidadTotal(g) <= u.tipo.capacidadMaxima) {
                    elegida = u;
                    break;
                }
            }
            if (elegida != null) {
                libres.remove(elegida);
                solucion.rutas.add(construirRuta(elegida, g));
            } else {
                sinAsignar.addAll(g);
            }
        }

        // Paso de inserción: ubica cada pedido restante en la ruta con
        // espacio y menor costo adicional, o arranca una ruta nueva en
        // una unidad todavía libre
        sinAsignar.sort(Comparator.comparingInt(p -> p.plazoMaximoHoras));
        List<Pedido> definitivamenteSinAsignar = new ArrayList<>();
        for (Pedido p : sinAsignar) {
            RutaAlg mejorRuta = null;
            double mejorExtra = Double.MAX_VALUE;
            for (RutaAlg r : solucion.rutas) {
                if (r.cargaTotal() + p.cantidad <= r.unidad.tipo.capacidadMaxima) {
                    ParadaAlg ultima = r.paradas.get(r.paradas.size() - 1);
                    double extra = Compartido.distancia(ultima.x(), ultima.y(), p.x, p.y)
                                 + Compartido.distancia(p.x, p.y, al.x, al.y)
                                 - Compartido.distancia(ultima.x(), ultima.y(), al.x, al.y);
                    if (extra < mejorExtra) {
                        mejorExtra = extra;
                        mejorRuta = r;
                    }
                }
            }
            if (mejorRuta != null) {
                mejorRuta.paradas.add(ParadaAlg.entrega(p));
            } else {
                UnidadTransporte nueva = null;
                for (UnidadTransporte u : libres) {
                    if (p.cantidad <= u.tipo.capacidadMaxima) {
                        nueva = u;
                        break;
                    }
                }
                if (nueva != null) {
                    libres.remove(nueva);
                    List<Pedido> g = new ArrayList<>();
                    g.add(p);
                    solucion.rutas.add(construirRuta(nueva, g));
                } else {
                    definitivamenteSinAsignar.add(p);
                }
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

    private static int cantidadTotal(List<Pedido> g) {
        int t = 0;
        for (Pedido p : g) t += p.cantidad;
        return t;
    }

    private static boolean cabeEnAlgunTipo(List<Pedido> g, List<UnidadTransporte> flota) {
        int total = cantidadTotal(g);
        for (UnidadTransporte u : flota) {
            if (total <= u.tipo.capacidadMaxima) return true;
        }
        return false;
    }

    private static int raiz(int[] padre, int i) {
        while (padre[i] != i) i = padre[i];
        return i;
    }
}
