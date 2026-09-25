package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Etapa 22: cambios para cumplir los 5 días sin colapso (tolerancia en el límite y holgura de seguridad). */
class CincoDiasTest {

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    /** Una entrega que llega en el mismo instante que la hora límite (salvo redondeo) no es colapso. */
    @Test
    void entregaEnElLimiteExactoNoEsColapso() {
        double limite = (10 + 25 / 60.0 + 4) * 60;   // pedido de las 10:25 con hl = 4 h
        assertFalse(Simulador.vencido(limite, limite));
        assertFalse(Simulador.vencido(limite, limite + 1e-9));   // error de redondeo
        assertTrue(Simulador.vencido(limite, limite + 0.01));    // 0.6 s tarde sí es colapso
    }

    /** La ventana de un tramo incluye la parada de alimentación: si el viaje cruza las 03:00, la
     *  unidad llega 1 h después, y un bloqueo que empieza en esa hora ya debe evitarse al elegir
     *  el camino (antes se ignoraba y la unidad quedaba detenida hasta el fin del bloqueo). */
    @Test
    void laVentanaDelTramoIncluyeLaAlimentacion(@org.junit.jupiter.api.io.TempDir java.nio.file.Path carpeta)
            throws java.io.IOException {
        java.nio.file.Path f = carpeta.resolve("prueba.bloqueadas");
        // Muro en x = 10 (y = 0..9) de 03:30 a 10:00 del día 1
        java.nio.file.Files.writeString(f, "01d03h30m-01d10h00m:10,0,10,9\n");
        MapaVial m = MapaVial.leer(f.toString());
        double salida = 2 + 50 / 60.0;   // 02:50; 10 km a 40 km/h = 15 min
        assertEquals(10.0, m.distanciaTramo(5, 5, 15, 5, salida, 40, false), 1e-9);   // llega 03:05: sin muro
        assertEquals(20.0, m.distanciaTramo(5, 5, 15, 5, salida, 40, true), 1e-9);    // come 03-04, llega 04:05: rodea
    }

    private static RutaAlg rutaCon(Pedido p, TipoUnidad tipo) {
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("U", tipo);
        r.paradas.add(ParadaAlg.entrega(p));
        return r;
    }

    /** La holgura va a S (costo) y nunca a H: una entrega a tiempo con poco margen paga
     *  penalidad_holgura por hora faltante, y no cuenta como tarde. Solo rige con estado. */
    @Test
    void holguraSumaASyNoAH() {
        // Auto desde el central (27,14) a (47,14): 20 km a 40 km/h = 0.5 h; hl = 1 h -> margen 0.5 h
        Pedido p = new Pedido("H", 47, 14, 5, 1);
        RutaAlg r = rutaCon(p, TipoUnidad.AUTO);
        double costoSinHolgura = Compartido.evaluarRuta(r).costo;   // contexto por defecto: sin estado

        Contexto cx = new Contexto();
        cx.conEstado = true;
        cx.holguraH = 1.0;
        cx.penalidadHolgura = 200;
        cx.alimentacion = false;
        Contexto.usar(cx);
        Compartido.EvalRuta e = Compartido.evaluarRuta(r);
        assertEquals(0, e.tarde);
        assertEquals(0.5, e.faltaHolguraH, 1e-9);                 // faltan 30 min de margen
        assertEquals(costoSinHolgura + 100, e.costo, 1e-9);      // 0.5 h x 200 soles

        cx.holguraH = 0;   // desactivada
        assertEquals(costoSinHolgura, Compartido.evaluarRuta(r).costo, 1e-9);
    }

    /** Con la holgura, entre dos planes sin tardanzas se prefiere el que deja margen:
     *  la bicicleta llega casi al límite y el auto con margen, aunque cueste más por km. */
    @Test
    void holguraPrefiereUnidadConMargen() {
        // (39,14): 12 km. Bicicleta: 1 h (hl = 1 h -> margen 0). Auto: 0.3 h (margen 0.7 h).
        Pedido p = new Pedido("B", 39, 14, 3, 1);
        Contexto cx = new Contexto();
        cx.conEstado = true;
        cx.alimentacion = false;
        cx.holguraH = 1.0;
        cx.penalidadHolgura = 200;
        Contexto.usar(cx);
        Compartido.EvalRuta bici = Compartido.evaluarRuta(rutaCon(p, TipoUnidad.BICICLETA));
        Compartido.EvalRuta auto = Compartido.evaluarRuta(rutaCon(p, TipoUnidad.AUTO));
        assertEquals(0, bici.tarde);
        assertEquals(0, auto.tarde);
        assertTrue(auto.costo < bici.costo, "con holgura el auto debe ser preferible: auto=" + auto.costo
                + " bici=" + bici.costo);

        cx.holguraH = 0;   // sin holgura gana la bicicleta (más barata por km)
        assertTrue(Compartido.evaluarRuta(rutaCon(p, TipoUnidad.BICICLETA)).costo
                < Compartido.evaluarRuta(rutaCon(p, TipoUnidad.AUTO)).costo);
    }
}
