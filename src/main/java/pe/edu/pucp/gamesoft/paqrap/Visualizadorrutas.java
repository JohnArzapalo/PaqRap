package pe.edu.pucp.gamesoft.paqrap;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Visualizador de una Solucion del planificador PaqRap.
 * Dibuja la retícula de 70x50 km, los almacenes, las rutas (recorrido Manhattan)
 * de cada unidad y verifica el plazo de cada pedido con la velocidad del vehículo.
 *
 * Uso:
 *   Visualizadorrutas.imprimirReporte(solucion, "AG");
 *   Visualizadorrutas.guardarPNG(solucion, "AG", "03_genetico.png");
 *   Visualizadorrutas.mostrar(solucion, "AG");
 *
 * @author JL
 */
public class Visualizadorrutas extends JPanel {

    // ===== Parámetros del caso =====
    static final int ANCHO_KM = 70, ALTO_KM = 50;
    static final Almacen ALM_NOROESTE = new Almacen("AL-NO", 12, 38);
    static final Almacen ALM_ESTE = new Almacen("AL-ES", 57, 27);

    // Semáforo configurable (RNF d): % de pedidos entregados en plazo.
    // Se leen de config/parametros.properties (semaforo.umbral_verde / _ambar).
    static double UMBRAL_VERDE = Parametros.decimal("semaforo.umbral_verde", 100.0);
    static double UMBRAL_AMBAR = Parametros.decimal("semaforo.umbral_ambar", 95.0);

    // ===== Parámetros de dibujo =====
    private static final int ESC = 13, MARGEN = 40, ANCHO_LEYENDA = 320;
    private static final int ANCHO_PX = MARGEN * 2 + ANCHO_KM * ESC + ANCHO_LEYENDA;
    private static final int ALTO_PX = MARGEN * 2 + ALTO_KM * ESC;
    private static final Color ROJO = new Color(0xd62728);
    private static final Color[] PALETA = {
        new Color(0x1f77b4), new Color(0x2ca02c), new Color(0x9467bd), new Color(0xff7f0e),
        new Color(0x17becf), new Color(0x8c564b), new Color(0xe377c2), new Color(0x7f7f7f)
    };

    private final Solucion sol;
    private final String titulo;

    public Visualizadorrutas(Solucion sol, String titulo) {
        this.sol = sol;
        this.titulo = titulo;
        setPreferredSize(new Dimension(ANCHO_PX, ALTO_PX));
    }

    // ===== Cálculo de tiempos: se usa el MISMO cálculo que el planificador (Compartido) =====
    static double[] horasLlegada(RutaAlg r) {
        return Compartido.horasLlegada(r);
    }

    static boolean esTarde(ParadaAlg p, double llegada) {
        return Compartido.llegaTarde(p, llegada);
    }

    static int pedidosTarde(RutaAlg r) {
        return Compartido.pedidosTarde(r);
    }

    /** Rutas con al menos una parada: las unidades libres no se dibujan ni se cuentan. */
    static List<RutaAlg> rutasUsadas(Solucion s) {
        List<RutaAlg> usadas = new ArrayList<>();
        for (RutaAlg r : s.rutas) if (!r.estaVacia()) usadas.add(r);
        return usadas;
    }

    // ===== Dibujo =====
    private static int px(int x) { return MARGEN + x * ESC; }
    private static int py(int y) { return MARGEN + (ALTO_KM - y) * ESC; }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ANCHO_PX, ALTO_PX);
        dibujarReticula(g);
        List<RutaAlg> usadas = rutasUsadas(sol);
        for (int i = 0; i < usadas.size(); i++) dibujarRuta(g, usadas.get(i), i);
        dibujarSinAsignar(g);
        dibujarAlmacen(g, Compartido.ALMACEN_CENTRAL, "Central", new Color(0x0b3d91), 16);
        dibujarAlmacen(g, ALM_NOROESTE, "Nor-Oeste", new Color(0xe07b00), 13);
        dibujarAlmacen(g, ALM_ESTE, "Este", new Color(0xe07b00), 13);
        dibujarLeyenda(g);
    }

    private void dibujarReticula(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int x = 0; x <= ANCHO_KM; x++) {
            g.setColor(x % 5 == 0 ? new Color(215, 215, 215) : new Color(242, 242, 242));
            g.drawLine(px(x), py(0), px(x), py(ALTO_KM));
            if (x % 10 == 0) { g.setColor(Color.GRAY); g.drawString(String.valueOf(x), px(x) - 5, py(0) + 15); }
        }
        for (int y = 0; y <= ALTO_KM; y++) {
            g.setColor(y % 5 == 0 ? new Color(215, 215, 215) : new Color(242, 242, 242));
            g.drawLine(px(0), py(y), px(ANCHO_KM), py(y));
            if (y % 10 == 0) { g.setColor(Color.GRAY); g.drawString(String.valueOf(y), px(0) - 22, py(y) + 4); }
        }
        g.setColor(Color.GRAY);
        g.drawString("X (km)", px(ANCHO_KM) - 30, py(0) + 30);
        g.drawString("Y (km)", 4, py(ALTO_KM) - 10);
    }

    private static Stroke trazo(TipoUnidad t) {
        switch (t) {
            case AUTO: return new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            case MOTO: return new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{9f, 5f}, 0f);
            default:   return new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{2f, 4f}, 0f);
        }
    }

    private void dibujarRuta(Graphics2D g, RutaAlg r, int idx) {
        Color c = PALETA[idx % PALETA.length];
        double off = ((idx % 5) - 2) * 1.5;   // desfase para que rutas en la misma calle no se tapen
        Almacen al = Compartido.ALMACEN_CENTRAL;

        Path2D camino = new Path2D.Double();
        camino.moveTo(px(al.x) + off, py(al.y) + off);
        int ax = al.x, ay = al.y;
        for (ParadaAlg p : r.paradas) {
            camino.lineTo(px(p.x()) + off, py(ay) + off);    // primero en X
            camino.lineTo(px(p.x()) + off, py(p.y()) + off); // luego en Y
            ax = p.x(); ay = p.y();
        }
        camino.lineTo(px(al.x) + off, py(ay) + off);          // retorno al almacén
        camino.lineTo(px(al.x) + off, py(al.y) + off);
        g.setColor(c);
        g.setStroke(trazo(r.unidad.tipo));
        g.draw(camino);

        double[] t = horasLlegada(r);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < r.paradas.size(); i++) {
            ParadaAlg p = r.paradas.get(i);
            if (p.tipo != TipoParada.ENTREGA) continue;
            boolean tarde = esTarde(p, t[i]);
            g.setColor(c);
            g.fillOval(px(p.x()) - 5, py(p.y()) - 5, 10, 10);
            g.setColor(tarde ? ROJO : Color.DARK_GRAY);
            g.setStroke(new BasicStroke(tarde ? 2.5f : 1f));
            g.drawOval(px(p.x()) - 5, py(p.y()) - 5, 10, 10);
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(tarde ? ROJO : Color.BLACK);
            g.drawString((i + 1) + "." + p.pedido.id + " (" + p.cantidad + ")", px(p.x()) + 7, py(p.y()) - 5);
        }
    }

    private void dibujarSinAsignar(Graphics2D g) {
        g.setColor(ROJO);
        g.setStroke(new BasicStroke(2.5f));
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        for (Pedido p : sol.pedidosSinAsignar) {
            int x = px(p.x), y = py(p.y);
            g.drawLine(x - 5, y - 5, x + 5, y + 5);
            g.drawLine(x - 5, y + 5, x + 5, y - 5);
            g.drawString(p.id + " sin asignar", x + 7, y - 5);
        }
    }

    private void dibujarAlmacen(Graphics2D g, Almacen a, String nombre, Color c, int tam) {
        g.setColor(c);
        g.fillRect(px(a.x) - tam / 2, py(a.y) - tam / 2, tam, tam);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(px(a.x) - tam / 2, py(a.y) - tam / 2, tam, tam);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString(nombre + " (" + a.x + "," + a.y + ")", px(a.x) + tam / 2 + 3, py(a.y) + tam / 2 + 11);
    }

    private void dibujarLeyenda(Graphics2D g) {
        int lx = MARGEN + ANCHO_KM * ESC + 30, ly = MARGEN;
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString(titulo, lx, ly);
        ly += 22;

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (TipoUnidad t : TipoUnidad.values()) {
            g.setColor(Color.DARK_GRAY);
            g.setStroke(trazo(t));
            g.drawLine(lx, ly - 4, lx + 30, ly - 4);
            g.setStroke(new BasicStroke(1f));
            g.drawString(String.format("%-9s cap %2d  %2.0f km/h  S/ %.2f/km", t.name(), t.capacidadMaxima,
                    t.velocidadPromedio, t.costoPorKilometro), lx + 38, ly);
            ly += 16;
        }
        ly += 10;

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(Color.BLACK);
        g.drawString("Rutas", lx, ly);
        ly += 16;
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));

        double km = 0;
        List<RutaAlg> usadas = rutasUsadas(sol);
        int maxFilas = (ALTO_PX - ly - 150) / 15;
        for (int i = 0; i < usadas.size(); i++) {
            RutaAlg r = usadas.get(i);
            int tr = pedidosTarde(r);
            km += r.distanciaKm;
            if (i < maxFilas) {
                g.setColor(PALETA[i % PALETA.length]);
                g.fillRect(lx, ly - 9, 10, 10);
                g.setColor(tr > 0 ? ROJO : Color.BLACK);
                g.drawString(String.format("%-3s %-4s %2d/%2d %3.0fkm S/%7.2f%s", r.unidad.codigo,
                        r.unidad.tipo.name().substring(0, 4), r.cargaTotal(), r.unidad.tipo.capacidadMaxima,
                        r.distanciaKm, r.costo, tr > 0 ? " T:" + tr : ""), lx + 14, ly);
                ly += 15;
            } else if (i == maxFilas) {
                g.setColor(Color.GRAY);
                g.drawString("... y " + (usadas.size() - maxFilas) + " rutas más", lx + 14, ly);
                ly += 15;
            }
        }
        // % de pedidos ORIGINALES en plazo: todas sus entregas asignadas y a tiempo
        int[] enPlazoTotal = Compartido.pedidosOriginalesEnPlazo(sol);
        int enPlazo = enPlazoTotal[0], totalPed = enPlazoTotal[1];
        double pct = totalPed == 0 ? 100.0 : 100.0 * enPlazo / totalPed;

        ly = ALTO_PX - 125;
        g.setColor(Color.BLACK);
        g.drawLine(lx, ly - 14, lx + ANCHO_LEYENDA - 40, ly - 14);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("Unidades usadas: " + sol.vehiculosUsados(), lx, ly); ly += 17;
        g.drawString(String.format("Km totales: %.0f", km), lx, ly); ly += 17;
        g.drawString(String.format("Función objetivo: H = %d   S = S/ %.2f", sol.H, sol.S), lx, ly); ly += 17;
        g.setColor(sol.pedidosSinAsignar.isEmpty() ? Color.BLACK : ROJO);
        g.drawString("Pedidos sin asignar: " + sol.pedidosSinAsignar.size(), lx, ly); ly += 17;
        Color sem = pct >= UMBRAL_VERDE ? new Color(0x2e9e3e) : pct >= UMBRAL_AMBAR ? new Color(0xf0a800) : ROJO;
        g.setColor(sem);
        g.fillOval(lx, ly - 11, 13, 13);
        g.setColor(Color.BLACK);
        g.drawString(String.format("Pedidos en plazo: %.1f%% (%d/%d)", pct, enPlazo, totalPed), lx + 19, ly);
    }

    // ===== API pública =====
    public static void mostrar(Solucion s, String titulo) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("PaqRap - " + titulo);
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.add(new JScrollPane(new Visualizadorrutas(s, titulo)));
            f.pack();
            f.setLocationByPlatform(true);
            f.setVisible(true);
        });
    }

    public static void guardarPNG(Solucion s, String titulo, String archivo) {
        try {
            Visualizadorrutas v = new Visualizadorrutas(s, titulo);
            v.setSize(ANCHO_PX, ALTO_PX);
            BufferedImage img = new BufferedImage(ANCHO_PX, ALTO_PX, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            v.paint(g);
            g.dispose();
            ImageIO.write(img, "png", new File(archivo));
            System.out.println("  Imagen guardada: " + new File(archivo).getAbsolutePath());
        } catch (Exception e) {
            System.err.println("  No se pudo guardar la imagen: " + e.getMessage());
        }
    }

    /** Tabla en consola con la hora de llegada de cada pedido frente a su plazo. */
    public static void imprimirReporte(Solucion s, String titulo) {
        System.out.println("--- Detalle de plazos: " + titulo + " ---");
        for (RutaAlg r : rutasUsadas(s)) {
            double[] t = horasLlegada(r);
            System.out.printf("  %s (%s, %.0f km/h):%n", r.unidad.codigo, r.unidad.tipo, r.unidad.tipo.velocidadPromedio);
            for (int i = 0; i < r.paradas.size(); i++) {
                ParadaAlg p = r.paradas.get(i);
                if (p.tipo != TipoParada.ENTREGA) continue;
                System.out.printf("     %-3s en (%2d,%2d)  llega %5.2f h  límite %5.2f h  %s%n", p.pedido.id,
                        p.x(), p.y(), t[i], p.pedido.horaLimite(), esTarde(p, t[i]) ? "TARDE" : "OK");
            }
        }
        if (!s.pedidosSinAsignar.isEmpty()) System.out.println("  Sin asignar: " + s.pedidosSinAsignar);
    }

    /** Curva de convergencia del mejor individuo por generación (R11), en dos
     *  paneles: arriba H (entregas sin asignar + tarde) y abajo S (costo).
     *  Como el criterio es jerárquico (primero H, luego S), S puede SUBIR en
     *  una generación en la que baja H; por eso se grafican ambos. */
    public static void guardarConvergenciaPNG(List<double[]> historialCompleto, String titulo, String archivo) {
        // Se recorta la vista hasta un poco después de la última mejora, para que la caída se vea
        int ultimaMejora = 0;
        for (int i = 1; i < historialCompleto.size(); i++) {
            double[] a = historialCompleto.get(i), b = historialCompleto.get(i - 1);
            if (a[0] != b[0] || a[1] != b[1]) ultimaMejora = i;
        }
        int corte = Math.min(historialCompleto.size(), Math.max(20, ultimaMejora * 2 + 1));
        List<double[]> historial = historialCompleto.subList(0, corte);
        int w = 800, h = 620, m = 70;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString(titulo, m, 28);
        if (historial.size() > 1) {
            // Panel superior: H; panel inferior: S
            dibujarPanelConvergencia(g, historial, 0, "H", "%.0f", new Color(0xd62728), m, 50, w - 30, 250);
            dibujarPanelConvergencia(g, historial, 1, "S", "S/ %.0f", new Color(0x1f77b4), m, 300, w - 30, h - 60);
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString((historial.size() - 1) + " generaciones mostradas", w - 190, h - 45);
            double[] ini = historialCompleto.get(0), fin = historialCompleto.get(historialCompleto.size() - 1);
            g.drawString(String.format("Última mejora en la generación %d de %d ejecutadas "
                    + "(H %.0f -> %.0f, S/ %.2f -> S/ %.2f)", ultimaMejora, historialCompleto.size() - 1,
                    ini[0], fin[0], ini[1], fin[1]), m, h - 18);
        }
        g.dispose();
        try {
            ImageIO.write(img, "png", new File(archivo));
            System.out.println("  Imagen guardada: " + new File(archivo).getAbsolutePath());
        } catch (Exception e) {
            System.err.println("  No se pudo guardar la curva: " + e.getMessage());
        }
    }

    /** Dibuja la serie historial[i][indice] en el rectángulo (x0, yArriba)-(x1, yAbajo). */
    private static void dibujarPanelConvergencia(Graphics2D g, List<double[]> historial, int indice, String nombre,
                                                 String formato, Color color, int x0, int yArriba, int x1, int yAbajo) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double[] v : historial) { min = Math.min(min, v[indice]); max = Math.max(max, v[indice]); }
        if (max == min) max = min + 1;
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(x0, yAbajo, x1, yAbajo);
        g.drawLine(x0, yAbajo, x0, yArriba);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(nombre, x0 - 20, (yArriba + yAbajo) / 2);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(String.format(formato, max), 5, yArriba + 5);
        g.drawString(String.format(formato, min), 5, yAbajo);
        g.drawString("0", x0, yAbajo + 14);
        g.setColor(color);
        g.setStroke(new BasicStroke(2.2f));
        int n = historial.size();
        for (int i = 1; i < n; i++) {
            int xa = x0 + (int) ((double) (i - 1) / (n - 1) * (x1 - x0));
            int xb = x0 + (int) ((double) i / (n - 1) * (x1 - x0));
            int ya = yAbajo - (int) ((historial.get(i - 1)[indice] - min) / (max - min) * (yAbajo - yArriba));
            int yb = yAbajo - (int) ((historial.get(i)[indice] - min) / (max - min) * (yAbajo - yArriba));
            g.drawLine(xa, ya, xb, yb);
        }
    }
}