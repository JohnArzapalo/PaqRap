package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo de la primera iteración (verificación de factibilidad): construye
 * una instancia de juguete de 6 pedidos y 2 unidades, y ejecuta la
 * heurística constructiva, Búsqueda Tabú y el Algoritmo Genético, tal
 * como se describen en el apartado 4 del ISA. Sirve para verificar que
 * ambos algoritmos producen soluciones factibles y para explicar su
 * funcionamiento con un ejemplo trazable a mano.
 */
public class Main {

    public static void main(String[] args) {
        // ================= INSTANCIA 1: plazos holgados (8, 12 y 36 h) =================
        List<Pedido> pedidos = new ArrayList<>();
        pedidos.add(new Pedido("P1", 30, 18, 5, 36));
        pedidos.add(new Pedido("P2", 10, 20, 3, 8));
        pedidos.add(new Pedido("P3", 35, 10, 8, 36));
        pedidos.add(new Pedido("P4", 20, 25, 2, 12));
        pedidos.add(new Pedido("P5", 28, 16, 6, 36));
        pedidos.add(new Pedido("P6", 15, 12, 4, 36));

        List<UnidadTransporte> flota = new ArrayList<>();
        flota.add(new UnidadTransporte("U1", TipoUnidad.AUTO));
        flota.add(new UnidadTransporte("U2", TipoUnidad.MOTO));

        System.out.println("################ INSTANCIA 1: plazos holgados ################");
        Solucion[] r1 = ejecutarTodo(pedidos, flota);

        // ================= INSTANCIA 2: plazos ajustados =================
        // Mismos puntos y cantidades, pero P2 y P3 deben llegar en 1 hora y P4 en 3 horas.
        // Con solo 2 vehículos, los dos pedidos urgentes tienen que ser la PRIMERA
        // parada de vehículos distintos: el planificador está obligado a considerar los plazos.
        List<Pedido> urgentes = new ArrayList<>();
        urgentes.add(new Pedido("P1", 30, 18, 5, 36));
        urgentes.add(new Pedido("P2", 10, 20, 3, 1));
        urgentes.add(new Pedido("P3", 35, 10, 8, 1));
        urgentes.add(new Pedido("P4", 20, 25, 2, 3));
        urgentes.add(new Pedido("P5", 28, 16, 6, 36));
        urgentes.add(new Pedido("P6", 15, 12, 4, 36));

        System.out.println();
        System.out.println("################ INSTANCIA 2: plazos ajustados ################");
        Solucion[] r2 = ejecutarTodo(urgentes, flota);

        // ================= IMÁGENES Y VENTANAS =================
        System.out.println();
        System.out.println("=== Imágenes (se guardan en la carpeta del proyecto) ===");
        Visualizadorrutas.guardarPNG(r1[0], "I1 - Solución inicial (Clarke & Wright)", "01_inicial.png");
        Visualizadorrutas.guardarPNG(r1[1], "I1 - Búsqueda Tabú", "02_tabu.png");
        Visualizadorrutas.guardarPNG(r1[3], "I1 - AG + Split - generación 0", "03_ag_generacion0.png");
        Visualizadorrutas.guardarPNG(r1[2], "I1 - AG + Split - final", "04_ag_final.png");
        Visualizadorrutas.guardarPNG(r2[0], "I2 (plazos ajustados) - Clarke & Wright", "06_urgente_inicial.png");
        Visualizadorrutas.guardarPNG(r2[1], "I2 (plazos ajustados) - Búsqueda Tabú", "07_urgente_tabu.png");
        Visualizadorrutas.guardarPNG(r2[2], "I2 (plazos ajustados) - AG + Split", "08_urgente_ag.png");
        Visualizadorrutas.guardarConvergenciaPNG(AlgoritmoGenetico.historialConvergencia,
                "Convergencia del AG en I2 (mejor H y S por generación)", "05_ag_convergencia.png");

        Visualizadorrutas.mostrar(r2[1], "I2 (plazos ajustados) - Búsqueda Tabú");
        Visualizadorrutas.mostrar(r2[2], "I2 (plazos ajustados) - AG + Split");
    }

    /** Ejecuta C&W, Tabú y AG sobre una instancia y devuelve
     *  {inicial, tabu, genetico, mejor de la generación 0 del AG}. */
    private static Solucion[] ejecutarTodo(List<Pedido> pedidos, List<UnidadTransporte> flota) {
        System.out.println("=== Heurística constructiva (Clarke & Wright) ===");
        Solucion inicial = Heuristicaconstructiva.construirSolucionInicial(pedidos, flota);
        imprimir(inicial);

        System.out.println();
        System.out.println("=== Búsqueda Tabú (2 s de presupuesto) ===");
        Solucion tabu = BusquedaTabu.ejecutar(inicial, flota, 2000, 0, 8, 300);
        imprimir(tabu);
        System.out.printf("  iteraciones=%d  evaluaciones=%d%n",
                BusquedaTabu.ultimasIteraciones, BusquedaTabu.ultimasEvaluaciones);

        System.out.println();
        System.out.println("=== Algoritmo Genético + Split (2 s de presupuesto) ===");
        Solucion genetico = AlgoritmoGenetico.ejecutar(pedidos, flota, 2000, 0, 30);
        imprimir(genetico);
        System.out.printf("  generaciones=%d  evaluaciones=%d  tramos con cambio de tipo=%d  "
                        + "búsqueda local: %d aplicaciones, %d mejoras%n",
                AlgoritmoGenetico.ultimasGeneraciones, AlgoritmoGenetico.ultimasEvaluaciones,
                AlgoritmoGenetico.ultimosTramosCambioTipo, AlgoritmoGenetico.ultimasAplicacionesBL,
                AlgoritmoGenetico.ultimasMejorasBL);
        Solucion generacion0 = AlgoritmoGenetico.solucionGeneracion0;
        System.out.printf("  generación 0: H=%d S=S/.%.2f%n", generacion0.H, generacion0.S);

        System.out.println();
        Visualizadorrutas.imprimirReporte(inicial, "Clarke & Wright");
        Visualizadorrutas.imprimirReporte(tabu, "Búsqueda Tabú");
        Visualizadorrutas.imprimirReporte(genetico, "AG + Split");
        return new Solucion[]{inicial, tabu, genetico, generacion0};
    }

    private static void imprimir(Solucion s) {
        for (RutaAlg r : s.rutas) {
            if (r.estaVacia()) continue;   // unidad libre: no se usa
            StringBuilder sb = new StringBuilder();
            for (ParadaAlg p : r.paradas) sb.append(p.pedido.id).append(" ");
            System.out.printf("  %s (%s): %-24s carga=%d/%d  dist=%.0fkm  costo=S/.%.2f  tarde=%d%n",
                    r.unidad.codigo, r.unidad.tipo, sb.toString().trim(),
                    r.cargaTotal(), r.unidad.tipo.capacidadMaxima, r.distanciaKm, r.costo,
                    Compartido.pedidosTarde(r));
        }
        if (!s.pedidosSinAsignar.isEmpty()) {
            System.out.println("  sin asignar: " + s.pedidosSinAsignar);
        }
        System.out.printf("  evaluar(solucion) = (H=%d, S=S/.%.2f)   H = sin asignar + fuera de plazo%n", s.H, s.S);
    }
}