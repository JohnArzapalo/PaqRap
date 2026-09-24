package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LectorPedidosTest {

    @TempDir
    Path carpeta;

    private List<Pedido> leer(String contenido) throws IOException {
        Path archivo = carpeta.resolve("ventas_prueba.txt");
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        // Ventana: día 1, 08:00-09:00 -> se planifica a las 09:00 (hora 0)
        return LectorPedidos.leerVentana(archivo.toString(), LectorPedidos.hora(1, 8, 0), 1);
    }

    @Test
    void leeUnaLinea() throws IOException {
        List<Pedido> p = leer("01d08h00m:29,29,c8232,10,18\n");
        assertEquals(1, p.size());
        Pedido a = p.get(0);
        assertEquals("c8232-L1", a.id);
        assertEquals("c8232-L1", a.idOriginal);
        assertEquals(29, a.x);
        assertEquals(29, a.y);
        assertEquals(10, a.cantidad);
        assertEquals(18, a.plazoMaximoHoras);
        assertEquals(-1.0, a.horaRegistro, 1e-9);   // llegó 1 h antes de planificar
        assertEquals(17.0, a.horaLimite(), 1e-9);
    }

    @Test
    void filtraPorVentanaYOmiteLineasInvalidas() throws IOException {
        List<Pedido> p = leer("01d07h59m:1,1,c1,1,36\n"      // antes de la ventana
                            + "01d08h30m:2,2,c2,1,36\n"      // dentro
                            + "01d09h00m:3,3,c3,1,36\n"      // fin de ventana: fuera (intervalo semiabierto)
                            + "esto no es un registro\n"
                            + "\n");
        assertEquals(1, p.size());
        assertEquals("c2-L2", p.get(0).id);
    }

    @Test
    void divideUnPedidoDe30PaquetesEn24Mas6() throws IOException {
        List<Pedido> p = leer("01d08h30m:10,10,c1111,30,4\n");
        assertEquals(2, p.size());
        assertEquals(24, p.get(0).cantidad);
        assertEquals(6, p.get(1).cantidad);
        assertEquals("c1111-L1-1", p.get(0).id);
        assertEquals("c1111-L1-2", p.get(1).id);
        assertEquals("c1111-L1", p.get(0).idOriginal);
        assertEquals("c1111-L1", p.get(1).idOriginal);
        assertEquals(3.5, p.get(0).horaLimite(), 1e-9);
    }

    @Test
    void ventanasSuperpuestasSeRechazan() {
        assertThrows(IllegalArgumentException.class,
                () -> Experimento.Ventana.leerLista("1d08h00m/2,1d09h00m/1"));
        assertEquals(3, Experimento.Ventana.leerLista("1d08h00m/1,1d09h30m/1,1d11h00m/1").size());
    }
}
