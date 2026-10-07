package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Las imágenes de Main van a salidas/figuras/: la carpeta se crea si no existe. */
class VisualizadorrutasTest {

    @Test
    void guardarCreaLaCarpetaDeSalida(@TempDir Path tmp) {
        File destino = tmp.resolve("salidas").resolve("figuras").resolve("convergencia.png").toFile();
        List<double[]> historial = List.of(new double[]{2, 500}, new double[]{1, 450}, new double[]{0, 470});
        Visualizadorrutas.guardarConvergenciaPNG(historial, "prueba", destino.getPath());
        assertTrue(destino.isFile(), "la imagen debe quedar en la carpeta creada");
    }
}
