package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Etapa 9.5: red vial con bloqueos. */
class MapaVialTest {

    @TempDir
    Path carpeta;

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    /** Muro vertical en x = 10, de y = 0 a y = 9, activo de 00:00 a 10:00 del día 1. */
    private MapaVial muro() throws IOException {
        Path f = carpeta.resolve("prueba.bloqueadas");
        Files.writeString(f, "01d00h00m-01d10h00m:10,0,10,9\n", StandardCharsets.UTF_8);
        return MapaVial.leer(f.toString());
    }

    @Test
    void elCaminoRodeaElBloqueoYEsMasLargoQueManhattan() throws IOException {
        MapaVial m = muro();
        // de (5,5) a (15,5): Manhattan 10; hay que subir a y = 10 para cruzar x = 10 -> 5 + 10 + 5 = 20
        assertEquals(20.0, m.distancia(5, 5, 15, 5, 1.0), 1e-9);
        assertTrue(m.distancia(5, 5, 15, 5, 1.0) > Compartido.distancia(5, 5, 15, 5));
    }

    @Test
    void unNodoBloqueadoNoSeAtraviesa() throws IOException {
        MapaVial m = muro();
        List<Integer> camino = m.camino(5, 5, 15, 5, 1.0);
        assertEquals(21, camino.size());   // 20 aristas
        for (int n : camino) assertFalse(MapaVial.x(n) == 10 && MapaVial.y(n) <= 9, "atraviesa el muro");
        for (int i = 1; i < camino.size(); i++) {   // pasos de 1 km sin diagonales
            int a = camino.get(i - 1), b = camino.get(i);
            assertEquals(1, Math.abs(MapaVial.x(a) - MapaVial.x(b)) + Math.abs(MapaVial.y(a) - MapaVial.y(b)));
        }
    }

    @Test
    void laCacheDevuelveLoMismoQueUnBfsNuevo() throws IOException {
        MapaVial m = MapaVial.leer("datos/202609_SINTETICO.bloqueadas");
        java.util.Random r = new java.util.Random(3);
        for (int k = 0; k < 200; k++) {
            int x1 = r.nextInt(71), y1 = r.nextInt(51), x2 = r.nextInt(71), y2 = r.nextInt(51);
            double t = r.nextDouble() * 720;
            double enCache = m.distancia(x1, y1, x2, y2, t);
            m.distancia(x1, y1, x2, y2, t);   // segunda consulta: desde la caché
            int[] fresco = m.bfs(MapaVial.nodo(x1, y1), m.bloqueadosEn(t));
            int d = fresco[MapaVial.nodo(x2, y2)];
            assertEquals(d < 0 ? Compartido.distancia(x1, y1, x2, y2) : d, enCache, 1e-9);
        }
    }

    @Test
    void unBloqueoQueTerminaRestableceManhattan() throws IOException {
        MapaVial m = muro();
        assertEquals(20.0, m.distancia(5, 5, 15, 5, 9.99), 1e-9);
        assertEquals(10.0, m.distancia(5, 5, 15, 5, 10.0), 1e-9);   // el bloqueo es [inicio, fin)
    }

    @Test
    void polilineaIncluyeTodosLosNodosDeCadaTramo() {
        int[] nodos = MapaVial.nodosPolilinea(new int[]{1, 1, 3, 1, 3, 2});
        assertArrayEquals(new int[]{MapaVial.nodo(1, 1), MapaVial.nodo(2, 1), MapaVial.nodo(3, 1), MapaVial.nodo(3, 2)},
                nodos);
    }

    /** 9.2: si el destino está bloqueado al llegar, la entrega espera a que se desbloquee. */
    @Test
    void entregaEnDestinoBloqueadoEsperaAlDesbloqueo() throws IOException {
        MapaVial m = muro();
        Contexto cx = new Contexto();
        cx.mapa = m;
        Contexto.usar(cx);
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("TA01", TipoUnidad.AUTO);
        r.paradas.add(ParadaAlg.entrega(new Pedido("A", 10, 5, 1, 36)));   // (10,5) está en el muro hasta las 10:00
        double llegada = Compartido.evaluarRuta(r).llegada[0];
        assertEquals(10.0, llegada, 1e-9);
    }

    /** 9.3: con red.bloqueos, las horas de llegada usan el camino real. */
    @Test
    void horasDeLlegadaUsanElMapa() throws IOException {
        Contexto cx = new Contexto();
        cx.mapa = muro();
        cx.instanteBaseH = 0;
        Contexto.usar(cx);
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("TA01", TipoUnidad.AUTO);
        // del central (27,14) a (5,5) no cruza el muro; de (5,5) a (15,5) sí: rodeo de 20 km
        r.paradas.add(ParadaAlg.entrega(new Pedido("A", 5, 5, 1, 36)));
        r.paradas.add(ParadaAlg.entrega(new Pedido("B", 15, 5, 1, 36)));
        double[] llegada = Compartido.evaluarRuta(r).llegada;
        double tramo1 = cx.distancia(27, 14, 5, 5, 0) / 40.0;
        assertEquals(tramo1 + 1 + 20 / 40.0, llegada[1], 1e-9);
    }
}
