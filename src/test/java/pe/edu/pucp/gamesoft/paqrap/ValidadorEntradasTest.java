package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Etapa 19.5: validador de archivos de entrada. */
class ValidadorEntradasTest {

    @TempDir
    Path carpeta;

    private String archivo(String nombre, String contenido) throws IOException {
        Path f = carpeta.resolve(nombre);
        Files.writeString(f, contenido, StandardCharsets.UTF_8);
        return f.toString();
    }

    @Test
    void ventasDetectaFormatoYCoordenadas() throws IOException {
        ValidadorEntradas.Informe inf = ValidadorEntradas.validarVentas(archivo("v.txt",
                "01d08h00m:29,29,c8232,10,18\n"      // válida
              + "01d08h00m:71,29,c1,1,36\n"          // x fuera de la retícula
              + "01d25h00m:1,1,c1,1,36\n"            // hora inválida
              + "no es un pedido\n"                  // formato
              + "01d09h00m:1,1,c1,0,36\n"            // cantidad 0
              + "01d09h00m:2,2,c2,3,5\n"));          // válida, pero plazo no oficial -> aviso
        assertEquals(6, inf.lineas);
        assertEquals(2, inf.validas);
        assertEquals(4, inf.errores.size());
        assertEquals(1, inf.avisos.size());
    }

    @Test
    void bloqueosDetectaDiagonalesYEncierros() throws IOException {
        ValidadorEntradas.Informe inf = ValidadorEntradas.validarBloqueos(archivo("b.bloqueadas",
                "01d00h00m-01d05h00m:1,1,3,3\n"                      // tramo diagonal
              + "01d00h00m-01d05h00m:4,4,6,4,6,6,4,6,4,4\n"         // anillo cerrado: (5,5) inalcanzable
              + "01d06h00m-01d05h00m:1,1,1,3\n"));                  // fin antes del inicio
        assertTrue(inf.errores.stream().anyMatch(e -> e.contains("diagonal")));
        assertTrue(inf.errores.stream().anyMatch(e -> e.contains("inalcanzables") && e.contains("(5,5)")));
        assertTrue(inf.errores.stream().anyMatch(e -> e.contains("fin no es posterior")));
    }

    @Test
    void mantenimientoDetectaUnidadesInexistentes() throws IOException {
        ValidadorEntradas.Informe inf = ValidadorEntradas.validarMantenimiento(archivo("mant.preventivo",
                "20260905:TA03\n20260905:TA11\n2026095:TM01\n"));
        assertEquals(1, inf.validas);
        assertEquals(2, inf.errores.size());
    }

    @Test
    void losDatosSinteticosDelMesSonValidos() throws IOException {
        assertEquals(0, ValidadorEntradas.validarVentas("datos/ventas202609_SINTETICO_MES.txt").errores.size());
        assertEquals(0, ValidadorEntradas.validarBloqueos("datos/202609_SINTETICO.bloqueadas").errores.size());
    }
}
