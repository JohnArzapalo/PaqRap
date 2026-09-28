package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Etapa 23 (SI-23): hora límite efectiva cuando el destino se bloquea antes del plazo y hasta después. */
class LimiteEfectivoTest {

    @TempDir
    Path carpeta;

    @AfterEach
    void limpiar() {
        Contexto.restablecer();
    }

    private MapaVial mapa(String bloqueos) throws IOException {
        Path f = carpeta.resolve("b.bloqueadas");
        Files.writeString(f, bloqueos, StandardCharsets.UTF_8);
        return MapaVial.leer(f.toString());
    }

    /** Nodos (57,44) y (58,44) bloqueados de 02:00 a 10:00 del día 1. */
    private MapaVial bloqueoDe2a10() throws IOException {
        return mapa("01d02h00m-01d10h00m:57,44,58,44\n");
    }

    @Test
    void limiteEfectivoEsElInicioDelBloqueoQueCubreElPlazo() throws IOException {
        MapaVial m = bloqueoDe2a10();
        assertEquals(2.0, m.limiteEfectivo(57, 44, 5.0), 1e-9);    // plazo dentro del bloqueo
        assertEquals(1.0, m.limiteEfectivo(57, 44, 1.0), 1e-9);    // plazo antes del bloqueo
        assertEquals(11.0, m.limiteEfectivo(57, 44, 11.0), 1e-9);  // el bloqueo ya terminó
        assertEquals(10.0, m.limiteEfectivo(57, 44, 10.0), 1e-9);  // [inicio, fin): a las 10:00 ya está libre
        assertEquals(5.0, m.limiteEfectivo(20, 20, 5.0), 1e-9);    // otro nodo, sin bloqueo
    }

    /** Dos bloqueos encadenados (02-06 y 06-12) cuentan como uno solo que empieza a las 02:00. */
    @Test
    void bloqueosEncadenadosSeUnen() throws IOException {
        MapaVial m = mapa("01d02h00m-01d06h00m:57,44,58,44\n01d06h00m-01d12h00m:57,44,58,44\n");
        assertEquals(2.0, m.limiteEfectivo(57, 44, 8.0), 1e-9);
    }

    /** En el Contexto trabaja en horas relativas; con NODO_VECINO no se espera en el destino y rige el plazo. */
    @Test
    void contextoUsaHorasRelativasYRespetaLaRegla() throws IOException {
        Contexto cx = new Contexto();
        cx.mapa = bloqueoDe2a10();
        cx.reglaDestino = Contexto.ReglaDestino.ESPERAR;
        cx.instanteBaseH = 1.0;                                   // se planifica a la 01:00
        Pedido p = new Pedido("A", 57, 44, 1, 4).copiaRelativa(1.0);   // límite 04:00 = 3 h relativas
        assertEquals(1.0, cx.limiteEfectivo(p), 1e-9);           // 02:00 = 1 h relativa
        cx.reglaDestino = Contexto.ReglaDestino.NO_EVALUABLE;
        assertEquals(1.0, cx.limiteEfectivo(p), 1e-9);
        cx.reglaDestino = Contexto.ReglaDestino.NODO_VECINO;
        assertEquals(3.0, cx.limiteEfectivo(p), 1e-9);
        assertEquals(p.horaLimite(), new Contexto().limiteEfectivo(p), 1e-9);   // sin mapa
    }

    /**
     * El auto sale del central (27,14) y recorre 60 km (1.5 h a 40 km/h) hasta (57,44).
     * Plazo 4 h, pero el destino se bloquea de 02:00 a 10:00: llega a tiempo, con 0.5 h de
     * margen real (no 2.5 h). Faltan 0.5 h de la holgura de 1 h: suma a S, nunca a H.
     */
    @Test
    void holguraSeMideHastaLaHoraLimiteEfectiva() throws IOException {
        assumeTrue(TipoUnidad.AUTO.velocidadPromedio == 40.0);
        Contexto cx = new Contexto();
        cx.mapa = bloqueoDe2a10();
        cx.reglaDestino = Contexto.ReglaDestino.NO_EVALUABLE;
        cx.conEstado = true;
        cx.holguraH = 1.0;
        cx.penalidadHolgura = 200;
        Contexto.usar(cx);
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("TA01", TipoUnidad.AUTO);
        r.paradas.add(ParadaAlg.entrega(new Pedido("A", 57, 44, 1, 4)));
        Compartido.EvalRuta e = Compartido.evaluarRuta(r);
        assertEquals(1.5, e.llegada[0], 1e-9);
        assertEquals(0, e.tarde);
        assertEquals(0.5, e.faltaHolguraH, 1e-9);
        assertEquals(e.km * TipoUnidad.AUTO.costoPorKilometro + 0.5 * 200, e.costo, 1e-9);

        // Sin el bloqueo, el margen es 2.5 h y no hay penalidad
        cx.mapa = mapa("");
        assertEquals(0.0, Compartido.evaluarRuta(r).faltaHolguraH, 1e-9);
    }

    /** Llegar dentro de [efectiva, límite] obliga a esperar el desbloqueo: la entrega es tarde (H). */
    @Test
    void llegarDespuesDelInicioDelBloqueoEsTarde() throws IOException {
        assumeTrue(TipoUnidad.AUTO.velocidadPromedio == 40.0);
        Contexto cx = new Contexto();
        cx.mapa = mapa("01d01h00m-01d10h00m:57,44,58,44\n");   // bloqueo desde la 01:00
        cx.reglaDestino = Contexto.ReglaDestino.ESPERAR;
        Contexto.usar(cx);
        RutaAlg r = new RutaAlg();
        r.unidad = new UnidadTransporte("TA01", TipoUnidad.AUTO);
        r.paradas.add(ParadaAlg.entrega(new Pedido("A", 57, 44, 1, 4)));
        Compartido.EvalRuta e = Compartido.evaluarRuta(r);
        assertEquals(10.0, e.llegada[0], 1e-9);   // espera hasta las 10:00
        assertEquals(1, e.tarde);
    }
}
