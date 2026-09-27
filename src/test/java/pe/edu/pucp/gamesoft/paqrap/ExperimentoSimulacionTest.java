package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Etapa 25: situaciones por réplica (diseño pareado) en el experimento de simulación. */
class ExperimentoSimulacionTest {

    @TempDir
    Path carpeta;

    // Configuración estática del experimento (línea de comandos): se guarda y se restaura
    private String ventas, bloqueos, salida, cargas, situaciones;
    private boolean explicito, acelerado;
    private int replicas, hilos;
    private long maxEvaluaciones;
    private Simulador.Escenario escenario;

    @BeforeEach
    void guardar() {
        ventas = Experimento.ARCHIVO_VENTAS; explicito = Experimento.ARCHIVO_EXPLICITO;
        replicas = Experimento.REPLICAS; maxEvaluaciones = Experimento.MAX_EVALUACIONES;
        salida = Experimento.SALIDA_CSV; bloqueos = ExperimentoSimulacion.ARCHIVO_BLOQUEOS;
        escenario = ExperimentoSimulacion.ESCENARIO; acelerado = ExperimentoSimulacion.ACELERADO;
        cargas = ExperimentoSimulacion.CARGAS; situaciones = ExperimentoSimulacion.SITUACIONES;
        hilos = ExperimentoSimulacion.HILOS;
    }

    @AfterEach
    void restaurar() {
        Experimento.ARCHIVO_VENTAS = ventas; Experimento.ARCHIVO_EXPLICITO = explicito;
        Experimento.REPLICAS = replicas; Experimento.MAX_EVALUACIONES = maxEvaluaciones;
        Experimento.SALIDA_CSV = salida; ExperimentoSimulacion.ARCHIVO_BLOQUEOS = bloqueos;
        ExperimentoSimulacion.ESCENARIO = escenario; ExperimentoSimulacion.ACELERADO = acelerado;
        ExperimentoSimulacion.CARGAS = cargas; ExperimentoSimulacion.SITUACIONES = situaciones;
        ExperimentoSimulacion.HILOS = hilos;
        Contexto.restablecer();
    }

    /** Corre SIM_5D con una carga pequeña (10 % de C_max) y 2 réplicas; devuelve
     *  "ALGORITMO réplica" -> semilla de carga, leída del CSV. */
    private Map<String, String> correr(String modoSituaciones) throws IOException {
        Experimento.ARCHIVO_VENTAS = "datos/ventas202609_SINTETICO_MES.txt";
        Experimento.ARCHIVO_EXPLICITO = true;
        ExperimentoSimulacion.ARCHIVO_BLOQUEOS = "datos/202609_SINTETICO.bloqueadas";
        ExperimentoSimulacion.ESCENARIO = Simulador.Escenario.SIM_5D;
        ExperimentoSimulacion.ACELERADO = true;
        ExperimentoSimulacion.CARGAS = "T10=0.1";
        ExperimentoSimulacion.SITUACIONES = modoSituaciones;
        ExperimentoSimulacion.HILOS = 2;
        Experimento.REPLICAS = 2;
        Experimento.MAX_EVALUACIONES = 20;
        Path csv = carpeta.resolve("prueba_" + modoSituaciones + "_SINTETICO.csv");
        Experimento.SALIDA_CSV = csv.toString();
        ExperimentoSimulacion.ejecutar();

        List<String> lineas = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<String> cab = Arrays.asList(lineas.get(0).split(","));
        int alg = cab.indexOf("algoritmo"), rep = cab.indexOf("replica"), sem = cab.indexOf("semilla_carga");
        Map<String, String> semillas = new HashMap<>();
        for (String l : lineas.subList(1, lineas.size())) {
            String[] c = l.split(",", -1);
            semillas.put(c[alg] + " " + c[rep], c[sem]);
        }
        assertEquals(4, semillas.size());   // 2 algoritmos × 2 réplicas
        return semillas;
    }

    /** Por réplica: TABU y AG corren la MISMA situación en cada réplica, y cada réplica es distinta. */
    @Test
    void porReplicaCadaReplicaEsUnaSituacionPropiaYComun() throws IOException {
        Map<String, String> s = correr("por_replica");
        assertEquals(s.get("TABU 1"), s.get("AG 1"));
        assertEquals(s.get("TABU 2"), s.get("AG 2"));
        assertNotEquals(s.get("TABU 1"), s.get("TABU 2"));
    }

    /** Por nivel (diseño anterior): todas las réplicas usan la misma situación. */
    @Test
    void porNivelTodasLasReplicasUsanLaMismaSituacion() throws IOException {
        Map<String, String> s = correr("por_nivel");
        assertEquals(1, s.values().stream().distinct().count());
    }
}
