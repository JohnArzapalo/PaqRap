package pe.edu.pucp.gamesoft.paqrap;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Genera los pedidos de varios días para un nivel de carga del IEN,
 * remuestreando el archivo de pedidos base.
 *
 * Método: para cada día se sortean líneas del archivo base al azar y con
 * reposición (bootstrap de pedidos completos) hasta llegar al objetivo de
 * paquetes del día (el último pedido puede exceder el objetivo en menos de
 * un pedido). Cada pedido sorteado conserva su posición, cliente, cantidad,
 * plazo hl y su HORA DEL DÍA; solo cambia el día. Así se mantiene la
 * distribución espacial y temporal (y su correlación) del archivo base.
 *
 * Con el archivo sintético (08:00-12:00) toda la carga de cada día llega en
 * esa franja; con el archivo oficial se repartirá según su propio perfil.
 *
 * La semilla es fija por nivel: ambos algoritmos reciben EXACTAMENTE los
 * mismos pedidos. El conjunto se guarda en datos/generados/ para trazabilidad.
 */
final class GeneradorCarga {

    private GeneradorCarga() {
    }

    /**
     * @param base            líneas del archivo de pedidos base
     * @param paquetesPorDia  objetivo de paquetes por día (nivel × C_max)
     * @param dias            días a generar (desde el día 1)
     * @param semilla         semilla fija del nivel
     * @param destino         archivo de salida (formato ventas2026mm)
     */
    static void generar(List<LectorPedidos.Registro> base, double paquetesPorDia, int dias, long semilla,
                        Path destino) throws IOException {
        generar(base, paquetesPorDia, dias, semilla, destino, null);
    }

    /**
     * Igual que el anterior; si excluirBloqueados no es null (Etapa 17.2, SOLO con
     * datos sintéticos), descarta los pedidos sorteados cuyo destino queda
     * bloqueado durante TODA su ventana [registro, hora límite] y sortea otro.
     * @return pedidos descartados
     */
    static int generar(List<LectorPedidos.Registro> base, double paquetesPorDia, int dias, long semilla,
                       Path destino, MapaVial excluirBloqueados) throws IOException {
        if (base.isEmpty()) throw new IllegalArgumentException("El archivo base no tiene pedidos válidos");
        Random azar = new Random(semilla);
        List<LectorPedidos.Registro> generados = new ArrayList<>();
        int descartados = 0;
        for (int dia = 1; dia <= dias; dia++) {
            long paquetes = 0;
            while (paquetes < paquetesPorDia) {
                LectorPedidos.Registro o = base.get(azar.nextInt(base.size()));
                LectorPedidos.Registro r = new LectorPedidos.Registro();
                double horaDelDia = o.llegadaHoras % 24.0;
                r.llegadaHoras = (dia - 1) * 24.0 + horaDelDia;
                r.x = o.x;
                r.y = o.y;
                r.cliente = o.cliente;
                r.cantidad = o.cantidad;
                r.hl = o.hl;
                if (excluirBloqueados != null
                        && excluirBloqueados.bloqueadoDurante(r.x, r.y, r.llegadaHoras, r.llegadaHoras + r.hl)) {
                    if (++descartados > 1_000_000)
                        throw new IllegalStateException("Casi todos los destinos quedan bloqueados: revisar los bloqueos");
                    continue;
                }
                generados.add(r);
                paquetes += r.cantidad;
            }
        }
        generados.sort(Comparator.comparingDouble(r -> r.llegadaHoras));   // orden estable: reproducible

        Files.createDirectories(destino.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(destino, StandardCharsets.UTF_8))) {
            for (LectorPedidos.Registro r : generados) out.println(r.aLinea());
        }
        return descartados;
    }

    /** Nombre trazable del archivo generado; conserva el nombre del archivo base
     *  (así la marca SINTETICO se propaga a todas las salidas). */
    static String nombreArchivo(String nivel, double fraccion, long semilla, String archivoBase) {
        return nombreArchivo(nivel, fraccion, semilla, archivoBase, false);
    }

    /** Con sinDestinosBloqueados, el nombre lo indica ("_sinDestBloq") para no confundir conjuntos. */
    static String nombreArchivo(String nivel, double fraccion, long semilla, String archivoBase,
                                boolean sinDestinosBloqueados) {
        String base = Path.of(archivoBase).getFileName().toString().replaceFirst("\\.txt$", "");
        return String.format(Locale.US, "carga_%s_%.0fpct_semilla%d%s_de_%s.txt", nivel, fraccion * 100, semilla,
                sinDestinosBloqueados ? "_sinDestBloq" : "", base);
    }
}
