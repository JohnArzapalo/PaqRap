package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Etapa 12.6, con el ejemplo del profesor. E: 8 paquetes en (57,14), a 30 km
 * al este del central, plazo 3 h (180 min). Al inicio solo la moto TM01 está
 * disponible (el auto TA01 está en mantenimiento hasta el minuto 30), así que
 * la moto lleva E: tardaría 72 min. En el minuto 36 la moto se avería en
 * (42,14), a 15 km del central y 15 km del cliente.
 */
class AveriaTest {

    @TempDir
    Path carpeta;

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private static final List<Pedido> PEDIDO = List.of(new Pedido("E", "E", 57, 14, 8, 3, 0.0));

    private static Simulador.Config config(int tipoAveria, boolean conAuto) {
        Simulador.Config c = SimuladorTest.config(60);
        c.horizonteMin = 600;
        c.almacenesIntermedios = false;
        c.averias = List.of(new Averia(36, "TM01", tipoAveria));
        if (conAuto) c.mantenimientos = List.of(new Mantenimiento("TA01", 0, 30));
        return c;
    }

    /** Avería tipo 1 (2 h en el lugar): si la moto esperara, llegaría en el minuto 156 + 36 = 192 > 180.
     *  El auto sale del central (libre desde el minuto 30), trasvasa en (42,14) y entrega a tiempo:
     *  36 + 22.5 (viaje) + 30 (trasvase) + 22.5 (viaje) = 111 min. */
    @Test
    void otraUnidadTrasvasaYEntregaATiempo() {
        assumeTrue(TipoUnidad.AUTO.velocidadPromedio == 40.0 && TipoUnidad.MOTO.velocidadPromedio == 25.0);
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TM01", TipoUnidad.MOTO),
                                               new UnidadTransporte("TA01", TipoUnidad.AUTO));
        Simulador.Resultado r = Simulador.simular(PEDIDO, flota, Planificador.tabu(8), config(1, true));
        assertTrue(r.censurada, "no debe colapsar: " + r.causaColapso);
        assertEquals(1, r.averiasAplicadas);
        assertEquals(1, r.trasvases);
        assertEquals(1, r.pedidosEnPlazo);
        double entrega = -1;
        for (Simulador.Evt e : r.registro) if (e.tipo == Simulador.Evt.T.ENTREGA) { entrega = e.min; assertEquals("TA01", e.unidad); }
        assertEquals(111.0, entrega, 1e-6);
    }

    /** Si nadie llega a tiempo (sin el auto; avería tipo 2: la moto queda hasta 4 h en el
     *  lugar), el colapso ocurre en la hora límite exacta, con la causa correcta. */
    @Test
    void siNadieLlegaElColapsoEsEnLaHoraLimiteExacta() {
        List<UnidadTransporte> flota = List.of(new UnidadTransporte("TM01", TipoUnidad.MOTO));
        Simulador.Resultado r = Simulador.simular(PEDIDO, flota, Planificador.genetico(10), config(2, false));
        assertFalse(r.censurada);
        assertEquals(180.0, r.colapsoMin, 1e-9);
        assertEquals("a bordo de unidad averiada", r.causaColapso);
        assertEquals("TM01", r.unidadColapso);
    }

    @Test
    void reglasDeTiempoPorTipo() {
        Averia t1 = new Averia(600, "TA01", 1);     // 10:00 del día 1
        assertEquals(720, t1.enLugarHastaMin(), 1e-9);
        assertEquals(720, t1.disponibleDesdeMin(), 1e-9);
        Averia t2 = new Averia(600, "TA01", 2);     // turno 07-15 -> fin del turno siguiente: 23:00
        assertEquals(1380, t2.disponibleDesdeMin(), 1e-9);
        assertEquals(840, t2.enLugarHastaMin(), 1e-9);   // 4 h en el lugar
        Averia t3 = new Averia(600, "TA01", 3);     // al menos 2 días -> día 3 a las 15:00
        assertEquals(2 * 1440 + 900, t3.disponibleDesdeMin(), 1e-9);
    }

    @Test
    void lectoresDeAveriasYMantenimiento() throws IOException {
        Path a = carpeta.resolve("averias.txt");
        Files.writeString(a, "03d10h15m:TA04:1\nlinea mala\n01d00h05m:TM02:3\n", StandardCharsets.UTF_8);
        List<Averia> av = Averia.leer(a.toString());
        assertEquals(2, av.size());
        assertEquals("TM02", av.get(0).unidad);   // ordenadas por tiempo
        assertEquals(2 * 1440 + 615, av.get(1).tiempoMin, 1e-9);

        Path m = carpeta.resolve("mant.preventivo");
        Files.writeString(m, "20260905:TA03\n", StandardCharsets.UTF_8);
        List<Mantenimiento> mt = Mantenimiento.leer(m.toString());
        assertEquals(4 * 1440, mt.get(0).inicioMin, 1e-9);
        assertEquals("TA03", mt.get(0).unidad);
    }
}
