package pe.edu.pucp.gamesoft.paqrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validador de archivos de entrada (Etapa 19.5). Revisa, ANTES de correr el
 * experimento, el formato de los archivos oficiales (o sintéticos):
 *  - ventas2026mm:     ##d##h##m:posX,posY,cIdCliente,qq,hl
 *  - aaaamm.bloqueadas: ##d##h##m-##d##h##m:x1,y1,...,xn,yn
 *  - mant.preventivo:  aaaammdd:TTNN
 * Reporta líneas inválidas, coordenadas fuera de la retícula (0..70 × 0..50),
 * tramos diagonales, bloqueos que tocan un almacén y bloqueos que dejan nodos
 * libres inalcanzables desde el central en algún intervalo en que el conjunto
 * de nodos bloqueados es constante (igual que el simulador). Solo LEE: no corrige nada.
 *
 * Uso:  java ... ValidadorEntradas --ventas f.txt --bloqueos f.bloqueadas --mantenimiento f
 * Código de salida: 0 si no hay errores; 1 si hay al menos uno.
 *
 * Supuestos (docs/propuesta_cambios_IEN.md): SI-01 (retícula), SI-13 (valida también los datos sintéticos).
 */
final class ValidadorEntradas {

    private static final Pattern VENTA = Pattern.compile("(\\d+)d(\\d+)h(\\d+)m:(-?\\d+),(-?\\d+),(\\w+),(-?\\d+),(-?\\d+)");
    private static final Pattern BLOQUEO = Pattern.compile("(\\d+)d(\\d+)h(\\d+)m-(\\d+)d(\\d+)h(\\d+)m:([-\\d,]+)");
    private static final Pattern MANTENIMIENTO = Pattern.compile("(\\d{4})(\\d{2})(\\d{2}):(T[ABM])(\\d{2})");
    private static final Set<Integer> PLAZOS_OFICIALES = Set.of(4, 8, 12, 18, 36);
    private static final int MAX_DETALLE = 20;

    /** Resultado de validar un archivo. */
    static final class Informe {
        final String archivo;
        int lineas, validas;
        final List<String> errores = new ArrayList<>();
        final List<String> avisos = new ArrayList<>();

        Informe(String archivo) {
            this.archivo = archivo;
        }

        void error(int linea, String motivo, String texto) {
            errores.add((linea > 0 ? "línea " + linea + ": " : "") + motivo + (texto == null ? "" : " -> " + texto));
        }

        void aviso(String texto) {
            avisos.add(texto);
        }

        String resumen() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s: %d líneas, %d válidas, %d errores, %d avisos%n", archivo, lineas, validas,
                    errores.size(), avisos.size()));
            for (int i = 0; i < Math.min(MAX_DETALLE, errores.size()); i++) sb.append("  ERROR ").append(errores.get(i)).append('\n');
            if (errores.size() > MAX_DETALLE) sb.append("  ... y ").append(errores.size() - MAX_DETALLE).append(" errores más\n");
            for (int i = 0; i < Math.min(MAX_DETALLE, avisos.size()); i++) sb.append("  AVISO ").append(avisos.get(i)).append('\n');
            if (avisos.size() > MAX_DETALLE) sb.append("  ... y ").append(avisos.size() - MAX_DETALLE).append(" avisos más\n");
            return sb.toString();
        }
    }

    private ValidadorEntradas() {
    }

    private static boolean enReticula(int x, int y) {
        return x >= 0 && x < MapaVial.ANCHO && y >= 0 && y < MapaVial.ALTO;
    }

    private static boolean esAlmacen(int x, int y) {
        for (Almacen a : new Almacen[]{Compartido.ALMACEN_CENTRAL, Compartido.ALMACEN_NOROESTE, Compartido.ALMACEN_ESTE})
            if (a.x == x && a.y == y) return true;
        return false;
    }

    private static List<String> leerLineas(String archivo) throws IOException {
        List<String> l = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(archivo), StandardCharsets.UTF_8)) {
            String s;
            while ((s = br.readLine()) != null) l.add(s);
        }
        return l;
    }

    static Informe validarVentas(String archivo) throws IOException {
        Informe inf = new Informe(archivo);
        List<String> lineas = leerLineas(archivo);
        int fueraDePlazoOficial = 0;
        for (int i = 0; i < lineas.size(); i++) {
            String s = lineas.get(i).trim();
            if (s.isEmpty()) continue;
            inf.lineas++;
            Matcher m = VENTA.matcher(s);
            if (!m.matches()) { inf.error(i + 1, "formato inválido", s); continue; }
            int h = Integer.parseInt(m.group(2)), min = Integer.parseInt(m.group(3));
            int x = Integer.parseInt(m.group(4)), y = Integer.parseInt(m.group(5));
            int q = Integer.parseInt(m.group(7)), hl = Integer.parseInt(m.group(8));
            if (h > 23 || min > 59) { inf.error(i + 1, "hora inválida", s); continue; }
            if (!enReticula(x, y)) { inf.error(i + 1, "coordenada fuera de la retícula", s); continue; }
            if (q <= 0) { inf.error(i + 1, "cantidad no positiva", s); continue; }
            if (hl <= 0) { inf.error(i + 1, "plazo no positivo", s); continue; }
            if (!PLAZOS_OFICIALES.contains(hl)) fueraDePlazoOficial++;
            if (esAlmacen(x, y)) inf.aviso("línea " + (i + 1) + ": el cliente está en un almacén -> " + s);
            inf.validas++;
        }
        if (fueraDePlazoOficial > 0)
            inf.aviso(fueraDePlazoOficial + " pedidos con plazo distinto de 4, 8, 12, 18 o 36 h");
        return inf;
    }

    static Informe validarBloqueos(String archivo) throws IOException {
        Informe inf = new Informe(archivo);
        List<String> lineas = leerLineas(archivo);
        List<MapaVial.Bloqueo> bloqueos = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            String s = lineas.get(i).trim();
            if (s.isEmpty()) continue;
            inf.lineas++;
            Matcher m = BLOQUEO.matcher(s);
            if (!m.matches()) { inf.error(i + 1, "formato inválido", s); continue; }
            double ini = LectorPedidos.hora(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            double fin = LectorPedidos.hora(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
            int[] c;
            try {
                c = Arrays.stream(m.group(7).split(",")).mapToInt(Integer::parseInt).toArray();
            } catch (NumberFormatException e) { inf.error(i + 1, "coordenadas inválidas", s); continue; }
            if (fin <= ini) { inf.error(i + 1, "el fin no es posterior al inicio", s); continue; }
            if (c.length < 4 || c.length % 2 != 0) { inf.error(i + 1, "la polilínea necesita al menos 2 nodos (pares x,y)", s); continue; }
            boolean ok = true;
            for (int k = 0; k < c.length && ok; k += 2) if (!enReticula(c[k], c[k + 1])) ok = false;
            if (!ok) { inf.error(i + 1, "coordenada fuera de la retícula", s); continue; }
            for (int k = 0; k + 3 < c.length && ok; k += 2) if (c[k] != c[k + 2] && c[k + 1] != c[k + 3]) ok = false;
            if (!ok) { inf.error(i + 1, "tramo diagonal (solo horizontales o verticales)", s); continue; }
            int[] nodos = MapaVial.nodosPolilinea(c);
            for (int n : nodos) {
                if (esAlmacen(MapaVial.x(n), MapaVial.y(n))) {
                    inf.aviso("línea " + (i + 1) + ": el bloqueo incluye un almacén -> " + s);
                    break;
                }
            }
            bloqueos.add(new MapaVial.Bloqueo(ini, fin, nodos));
            inf.validas++;
        }
        // Alcanzabilidad EXACTA: en cada intervalo en que el conjunto de nodos bloqueados es
        // constante (los mismos que usa el simulador), todo nodo libre debe alcanzarse desde el central
        MapaVial mapa = new MapaVial(bloqueos);
        int central = MapaVial.nodo(Compartido.ALMACEN_CENTRAL.x, Compartido.ALMACEN_CENTRAL.y);
        for (int i = 0; i < mapa.numeroIntervalos(); i++) {
            BitSet bloq = mapa.estadoIntervalo(i);
            if (bloq.isEmpty()) continue;
            int[] d = mapa.bfs(central, bloq);
            TreeSet<String> inalcanzables = new TreeSet<>();
            for (int n = 0; n < MapaVial.NODOS && inalcanzables.size() < 5; n++) {
                if (!bloq.get(n) && d[n] < 0) inalcanzables.add("(" + MapaVial.x(n) + "," + MapaVial.y(n) + ")");
            }
            if (!inalcanzables.isEmpty()) {
                double t = mapa.inicioIntervalo(i);
                inf.error(0, String.format("desde el día %d %02d:%02d quedan nodos libres inalcanzables desde el central, p. ej. %s",
                        (int) (t / 24) + 1, (int) (t % 24), (int) Math.round((t * 60) % 60), inalcanzables), null);
            }
        }
        return inf;
    }

    static Informe validarMantenimiento(String archivo) throws IOException {
        Informe inf = new Informe(archivo);
        Set<String> flota = new TreeSet<>();
        for (UnidadTransporte u : Experimento.flotaOficial()) flota.add(u.codigo);
        List<String> lineas = leerLineas(archivo);
        for (int i = 0; i < lineas.size(); i++) {
            String s = lineas.get(i).trim();
            if (s.isEmpty()) continue;
            inf.lineas++;
            Matcher m = MANTENIMIENTO.matcher(s);
            if (!m.matches()) { inf.error(i + 1, "formato inválido (aaaammdd:TTNN)", s); continue; }
            int mes = Integer.parseInt(m.group(2)), dia = Integer.parseInt(m.group(3));
            if (mes < 1 || mes > 12 || dia < 1 || dia > 31) { inf.error(i + 1, "fecha inválida", s); continue; }
            String unidad = m.group(4) + m.group(5);
            if (!flota.contains(unidad)) { inf.error(i + 1, "la unidad no está en la flota oficial", s); continue; }
            inf.validas++;
        }
        return inf;
    }

    public static void main(String[] args) throws IOException {
        List<Informe> informes = new ArrayList<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--ventas": informes.add(validarVentas(args[i + 1])); break;
                case "--bloqueos": informes.add(validarBloqueos(args[i + 1])); break;
                case "--mantenimiento": informes.add(validarMantenimiento(args[i + 1])); break;
                default: throw new IllegalArgumentException("Argumento desconocido: " + args[i]
                        + " (use --ventas, --bloqueos, --mantenimiento)");
            }
        }
        if (informes.isEmpty()) {
            System.out.println("Uso: ValidadorEntradas --ventas f.txt --bloqueos f.bloqueadas --mantenimiento f");
            return;
        }
        int errores = 0;
        for (Informe inf : informes) {
            System.out.print(inf.resumen());
            errores += inf.errores.size();
        }
        System.out.println(errores == 0 ? "RESULTADO: sin errores" : "RESULTADO: " + errores + " errores");
        if (errores > 0) System.exit(1);
    }
}
