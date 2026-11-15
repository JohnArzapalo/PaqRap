package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Etapa 38 (indicación del profesor, 14-nov-2026): la pantalla de inicio solo pide la fecha y la hora; la
 * carga de archivos (SI-37) y los parámetros del sistema (SI-36) son opciones aparte. Se prueban con
 * carpetas temporales: juego_de_datos/ y config/ no se tocan.
 */
class CargaYParametrosTest {

    private static final YearMonth SEP = YearMonth.of(2026, 9);

    @TempDir
    Path tmp;
    private Path carpetaAntes, archivoAntes;

    @BeforeEach
    void usarCarpetasTemporales() {
        carpetaAntes = ArchivosCargados.carpeta;
        archivoAntes = ParametrosSistema.archivo;
        ArchivosCargados.carpeta = tmp.resolve("datos_cargados");
        ParametrosSistema.archivo = tmp.resolve("sistema.properties");
        ParametrosSistema.recargar();
    }

    @AfterEach
    void restaurar() {
        ArchivosCargados.carpeta = carpetaAntes;
        ParametrosSistema.archivo = archivoAntes;
        ParametrosSistema.recargar();
        Contexto.restablecer();
    }

    // ============================ Carga de archivos ============================

    /** Unas ventas válidas de septiembre se guardan aparte y las corridas de ese mes las leen en lugar de las
     *  oficiales; al quitarlas vuelve a regir el archivo del profesor. */
    @Test
    void lasVentasCargadasReemplazanALasOficialesDelMes() throws IOException {
        Path oficial = DatosOficiales.archivoVentas(SEP);
        assertTrue(Files.exists(oficial), "hay datos oficiales de 2026-09");
        ValidadorEntradas.Informe inf = ArchivosCargados.cargar(ArchivosCargados.Tipo.VENTAS, SEP,
                "01d00h05m:10,20,c001,5,8\n01d02h00m:40,30,c002,3,36\n02d10h30m:65,45,c003,30,12\n");
        assertTrue(inf.errores.isEmpty(), "sin errores: " + inf.errores);
        assertEquals(3, inf.validas);
        assertEquals(ArchivosCargados.ventas(SEP), DatosOficiales.archivoVentas(SEP), "el mes usa el archivo cargado");
        assertTrue(Files.exists(oficial), "el oficial sigue intacto");

        List<Pedido> leidos = new LectorVentasIncremental(SEP, 0, 30 * 1440).leerHasta(Double.POSITIVE_INFINITY);
        assertEquals(List.of("c001", "c002", "c003"), leidos.stream().map(p -> p.idOriginal.split("-")[0]).distinct().toList(),
                "los pedidos son los del archivo cargado (el de 30 paquetes se parte)");
        assertEquals(38, leidos.stream().mapToInt(p -> p.cantidad).sum());

        assertTrue(ArchivosCargados.eliminar(ArchivosCargados.Tipo.VENTAS, SEP));
        assertEquals(oficial, DatosOficiales.archivoVentas(SEP), "sin el cargado vuelve el oficial");
    }

    /** Un archivo con errores no se guarda: el informe dice qué líneas fallan (formato, fuera del mapa, día que
     *  no existe en el mes) y el archivo cargado antes queda como estaba. */
    @Test
    void unArchivoConErroresNoSeGuarda() throws IOException {
        ArchivosCargados.cargar(ArchivosCargados.Tipo.VENTAS, SEP, "01d00h05m:10,20,c001,5,8\n");
        String antes = Files.readString(ArchivosCargados.ventas(SEP));
        ValidadorEntradas.Informe inf = ArchivosCargados.cargar(ArchivosCargados.Tipo.VENTAS, SEP,
                "01d00h05m:10,20,c001,5,8\nesto no es una venta\n02d01h00m:90,20,c002,4,8\n31d01h00m:10,10,c003,2,8\n");
        assertEquals(3, inf.errores.size(), "formato, coordenada y día 31 en septiembre: " + inf.errores);
        assertTrue(inf.errores.get(2).contains("día 31"), inf.errores.get(2));
        assertEquals(antes, Files.readString(ArchivosCargados.ventas(SEP)), "el archivo anterior no cambió");
        assertEquals(1, Files.list(ArchivosCargados.ventas(SEP).getParent()).count(), "no quedan temporales");
        assertTrue(ArchivosCargados.informeJson(inf, ArchivosCargados.Tipo.VENTAS, SEP).contains("\"guardado\":false"));
        assertThrows(IllegalArgumentException.class, () -> ArchivosCargados.cargar(ArchivosCargados.Tipo.VENTAS, SEP, "  "));
        assertThrows(IllegalArgumentException.class, () -> ArchivosCargados.cargar(ArchivosCargados.Tipo.BLOQUEOS, null, "x"),
                "las ventas y los bloqueos necesitan el mes");
    }

    /** Los bloqueos y el mantenimiento cargados son los que usa una corrida del mes; el listado dice de dónde
     *  sale cada archivo. */
    @Test
    void bloqueosYMantenimientoCargadosLosUsaLaCorrida() throws IOException {
        assertTrue(ArchivosCargados.cargar(ArchivosCargados.Tipo.BLOQUEOS, SEP, "01d00h00m-01d05h00m:10,10,10,15\n").errores.isEmpty());
        assertTrue(ArchivosCargados.cargar(ArchivosCargados.Tipo.MANTENIMIENTO, null, "20260905:TA01\n20261003:TM02\n").errores.isEmpty());
        DatosOficiales d = DatosOficiales.cargar(SEP, 0, 5 * 1440, false);
        assertEquals(1, d.bloqueos.size(), "solo el bloqueo cargado");
        assertEquals(6, d.bloqueos.get(0).nodos.length, "de (10,10) a (10,15)");
        assertEquals(1, d.mantenimientos.size(), "solo el mantenimiento de septiembre");
        assertEquals("TA01", d.mantenimientos.get(0).unidad);

        String l = ArchivosCargados.listadoJson();
        assertTrue(l.contains("\"mantenimiento\":\"cargado\""), l);
        assertTrue(l.contains("{\"mes\":\"2026-09\",\"ventas\":\"oficial\",\"bloqueos\":\"cargado\"}"), l);
        assertTrue(l.contains("{\"mes\":\"2026-10\",\"ventas\":\"oficial\",\"bloqueos\":\"oficial\"}"), l);
    }

    // ============================ Parámetros del sistema ============================

    /** Sin archivo rigen los valores de siempre; un cambio válido se guarda y lo leen las corridas siguientes
     *  (aunque se reinicie el servidor). */
    @Test
    void losParametrosSeGuardanYSobrevivenAlReinicio() throws IOException {
        ParametrosSistema p = ParametrosSistema.vigentes();
        assertEquals(10, p.autos);
        assertEquals(40, p.llenadoBajo);
        assertEquals(120, p.ritmo(Simulador.Escenario.SIM_5D), 1e-9, "5 días en 60 min");
        assertEquals(1, p.ritmo(Simulador.Escenario.DIA_A_DIA), 1e-9);

        ParametrosSistema.actualizar(Map.of("autos", "8", "llenado_bajo", "30", "llenado_alto", "80", "ritmo_SIM_5D", "240"));
        ParametrosSistema.recargar();   // como si se reiniciara el servidor
        p = ParametrosSistema.vigentes();
        assertEquals(8, p.autos);
        assertEquals(15, p.motos, "lo que no se cambió se mantiene");
        assertEquals(30, p.llenadoBajo);
        assertEquals(80, p.llenadoAlto);
        assertEquals(240, p.ritmo(Simulador.Escenario.SIM_5D), 1e-9);
        assertTrue(p.json().contains("\"llenado_bajo\":30") && p.json().contains("\"SIM_5D\":240.000"), p.json());
    }

    /** Un cambio inválido se rechaza con su motivo y no se guarda nada. */
    @Test
    void unCambioInvalidoSeRechazaSinGuardar() throws IOException {
        ParametrosSistema.actualizar(Map.of("motos", "12"));
        String guardado = Files.readString(ParametrosSistema.archivo);
        for (Map<String, String> malo : List.of(Map.of("llenado_bajo", "80", "llenado_alto", "70"),
                Map.of("autos", "0", "motos", "0", "bicicletas", "0"), Map.of("autos", "31"),
                Map.of("holgura_verde", "30", "holgura_ambar", "60"), Map.of("ritmo_COLAPSO", "900"), Map.of("autos", "diez")))
            assertThrows(IllegalArgumentException.class, () -> ParametrosSistema.actualizar(malo), malo.toString());
        assertEquals(guardado, Files.readString(ParametrosSistema.archivo), "el archivo no cambió");
        assertEquals(12, ParametrosSistema.vigentes().motos);
        assertFalse(ParametrosSistema.vigentes().autos == 31);
    }
}
