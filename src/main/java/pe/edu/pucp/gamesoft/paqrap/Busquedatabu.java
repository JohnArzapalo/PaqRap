package pe.edu.pucp.gamesoft.paqrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** 4.2.1 Estructuras específicas de Búsqueda Tabú. */
class ParTabu {
    String idPedido;
    String idUnidadAnterior;

    ParTabu(String idPedido, String idUnidadAnterior) {
        this.idPedido = idPedido;
        this.idUnidadAnterior = idUnidadAnterior;
    }

    public boolean equals(Object o) {
        if (!(o instanceof ParTabu)) return false;
        ParTabu p = (ParTabu) o;
        return idPedido.equals(p.idPedido) && idUnidadAnterior.equals(p.idUnidadAnterior);
    }

    public int hashCode() {
        return Objects.hash(idPedido, idUnidadAnterior);
    }
}

class ListaTabu {
    Map<ParTabu, Integer> prohibidos = new HashMap<>();   // par -> iteración en que vence

    boolean contiene(ParTabu mov) {
        return prohibidos.getOrDefault(mov, -1) > 0;
    }

    void limpiarVencidos(int iteracion) {
        prohibidos.entrySet().removeIf(e -> e.getValue() <= iteracion);
    }
}

/** 4.2.2 Pseudocódigo, ya traducido a Java. Primera iteración: implementa
 *  el operador Reubicación (apartado 4.2, punto 1 de 5); los otros cuatro
 *  operadores del ISA siguen la misma lógica de copiar-modificar-evaluar
 *  y se incorporan en la segunda iteración. */
class BusquedaTabu {

    private static Random AZAR = new Random(7);   // semilla por defecto (reproducible)

    /** Fija la semilla antes de ejecutar. Cada réplica del experimento usa una distinta. */
    static void setSemilla(long semilla) {
        AZAR = new Random(semilla);
    }

    static Solucion ejecutar(Solucion solucionInicial, long presupuestoMs,
                              int duracionTabu, int maxSinMejora) {
        Solucion actual = solucionInicial.copiar();
        Solucion mejorGlobal = solucionInicial.copiar();
        ListaTabu tabu = new ListaTabu();
        int iteracion = 0;
        int iteracionesSinMejora = 0;
        long inicioEjecucion = System.currentTimeMillis();

        while (!Compartido.debeDetenerse(inicioEjecucion, presupuestoMs)
                && iteracionesSinMejora < maxSinMejora) {

            Movimiento mov = generarVecino(actual);
            if (mov == null) {
                iteracion++;
                continue;
            }

            boolean esTabu = tabu.contiene(mov.par);
            boolean cumpleAspiracion = Compartido.mejorQue(mov.solucion, mejorGlobal);

            if (esTabu && !cumpleAspiracion) {
                iteracionesSinMejora++;
                iteracion++;
                continue;
            }

            actual = mov.solucion;
            tabu.prohibidos.put(mov.par, iteracion + duracionTabu);
            tabu.limpiarVencidos(iteracion);

            if (Compartido.mejorQue(actual, mejorGlobal)) {
                mejorGlobal = actual.copiar();
                iteracionesSinMejora = 0;
            } else {
                iteracionesSinMejora++;
            }
            iteracion++;
        }
        return mejorGlobal;
    }

    private static class Movimiento {
        Solucion solucion;
        ParTabu par;
    }

    /** Reubicación: mueve una parada de entrega hacia otra posición, dentro
     *  de la misma ruta o hacia otra unidad. Si origen y destino resultan
     *  ser la misma ruta, ambas variables apuntan al mismo objeto y el
     *  movimiento simplemente reordena dentro de esa ruta. */
    private static Movimiento generarVecino(Solucion base) {
        if (base.rutas.isEmpty()) return null;
        Solucion s = base.copiar();

        int io = AZAR.nextInt(s.rutas.size());
        RutaAlg rutaOrigen = s.rutas.get(io);
        List<ParadaAlg> entregas = new ArrayList<>();
        for (ParadaAlg p : rutaOrigen.paradas) {
            if (p.tipo == TipoParada.ENTREGA) entregas.add(p);
        }
        if (entregas.isEmpty()) return null;
        ParadaAlg parada = entregas.get(AZAR.nextInt(entregas.size()));

        int id = AZAR.nextInt(s.rutas.size());
        RutaAlg rutaDestino = s.rutas.get(id);

        int cargaDestino = rutaDestino.cargaTotal() - (id == io ? parada.cantidad : 0);
        if (cargaDestino + parada.cantidad > rutaDestino.unidad.tipo.capacidadMaxima) return null;

        rutaOrigen.paradas.remove(parada);
        int posDestino = AZAR.nextInt(rutaDestino.paradas.size() + 1);
        rutaDestino.paradas.add(Math.min(posDestino, rutaDestino.paradas.size()), parada);

        Compartido.recalcularDistanciaYCosto(rutaOrigen);
        Compartido.recalcularDistanciaYCosto(rutaDestino);
        Compartido.evaluarSolucion(s);

        Movimiento m = new Movimiento();
        m.solucion = s;
        m.par = new ParTabu(parada.pedido.id, rutaOrigen.unidad.codigo);
        return m;
    }
}