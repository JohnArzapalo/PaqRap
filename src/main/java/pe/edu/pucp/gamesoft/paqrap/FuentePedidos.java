package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * De dónde saca el simulador los pedidos (Etapa 35, A22; docs/diseno/propuesta_lectura_incremental.md).
 *
 * El simulador los pide por bloques: en cada vuelta, solo los registrados hasta el inicio del bloque
 * siguiente (o la próxima replanificación). Así no hace falta tener en memoria todos los pedidos del
 * horizonte, solo los de, como mucho, un bloque por delante.
 *  - deLista: una lista ya armada (el experimento, las pruebas y Main): mismos datos y mismo orden que antes.
 *  - LectorVentasIncremental: lee los archivos mensuales de ventas línea a línea (corridas del visualizador).
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-33 (lectura incremental de pedidos).
 */
interface FuentePedidos {

    /** Pedidos registrados hasta el minuto indicado que aún no entregó, en orden de registro. */
    List<Pedido> leerHasta(double minuto);

    /** Pedidos originales en total si se conocen de antemano (lista), o -1 (lectura incremental: se cuentan al leer). */
    default int originalesTotales() {
        return -1;
    }

    /** Fuente a partir de una lista: la ordena por hora de registro (orden estable) y la entrega por partes. */
    static FuentePedidos deLista(List<Pedido> pedidos) {
        List<Pedido> ordenados = new ArrayList<>(pedidos);
        ordenados.sort(Comparator.comparingDouble(p -> p.horaRegistro));
        Set<String> originales = new LinkedHashSet<>();
        for (Pedido p : ordenados) originales.add(p.idOriginal);
        return new FuentePedidos() {
            private int siguiente = 0;

            @Override
            public List<Pedido> leerHasta(double minuto) {
                List<Pedido> r = new ArrayList<>();
                while (siguiente < ordenados.size() && ordenados.get(siguiente).horaRegistro * 60 <= minuto)
                    r.add(ordenados.get(siguiente++));
                return r;
            }

            @Override
            public int originalesTotales() {
                return originales.size();
            }
        };
    }
}
