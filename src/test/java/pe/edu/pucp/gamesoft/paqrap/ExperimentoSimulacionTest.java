package pe.edu.pucp.gamesoft.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private String ventas, bloqueos, salida, cargas, situaciones, carpetaVentas, carpetaBloqueos, meses, mantenimiento;
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
        hilos = ExperimentoSimulacion.HILOS; carpetaVentas = ExperimentoSimulacion.CARPETA_VENTAS;
        carpetaBloqueos = ExperimentoSimulacion.CARPETA_BLOQUEOS; meses = ExperimentoSimulacion.MESES;
        mantenimiento = ExperimentoSimulacion.ARCHIVO_MANTENIMIENTO;
    }

    @AfterEach
    void restaurar() {
        Experimento.ARCHIVO_VENTAS = ventas; Experimento.ARCHIVO_EXPLICITO = explicito;
        Experimento.REPLICAS = replicas; Experimento.MAX_EVALUACIONES = maxEvaluaciones;
        Experimento.SALIDA_CSV = salida; ExperimentoSimulacion.ARCHIVO_BLOQUEOS = bloqueos;
        ExperimentoSimulacion.ESCENARIO = escenario; ExperimentoSimulacion.ACELERADO = acelerado;
        ExperimentoSimulacion.CARGAS = cargas; ExperimentoSimulacion.SITUACIONES = situaciones;
        ExperimentoSimulacion.HILOS = hilos; ExperimentoSimulacion.CARPETA_VENTAS = carpetaVentas;
        ExperimentoSimulacion.CARPETA_BLOQUEOS = carpetaBloqueos; ExperimentoSimulacion.MESES = meses;
        ExperimentoSimulacion.ARCHIVO_MANTENIMIENTO = mantenimiento;
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

    /** Etapa 28: cada corrida recibe su propio MapaVial (las cachés no son seguras entre hilos),
     *  con los mismos bloqueos y las mismas distancias que el mapa base. */
    @Test
    void cadaCorridaTieneSuPropioMapaConLosMismosBloqueos() throws IOException {
        Simulador.Config base = new Simulador.Config();
        base.mapa = MapaVial.leer("datos/202609_SINTETICO.bloqueadas");
        Simulador.Config a = ExperimentoSimulacion.copiar(base);
        Simulador.Config b = ExperimentoSimulacion.copiar(base);
        assertNotSame(base.mapa, a.mapa);
        assertNotSame(a.mapa, b.mapa);
        assertEquals(base.mapa.bloqueos.size(), a.mapa.bloqueos.size());
        for (double t = 0; t < 30 * 24; t += 7.5)
            assertEquals(base.mapa.distancia(27, 14, 12, 38, t), a.mapa.distancia(27, 14, 12, 38, t), 1e-9);
    }

    /** Archivos mensuales con el formato del profesor: ventas.aaaamm.txt (3 pedidos por día) y bloqueo.aamm.txt. */
    private void mesDePrueba(Path ventas, Path bloqueos, int aaaamm, int dias) throws IOException {
        StringBuilder v = new StringBuilder();
        for (int d = 1; d <= dias; d++)
            for (int k = 0; k < 3; k++)
                v.append(String.format("%02dd%02dh00m:%d,%d,c%d,02,36%n", d, 8 + 4 * k, 20 + (d + k) % 15, 10 + k * 5, d * 10 + k));
        Files.writeString(ventas.resolve("ventas." + aaaamm + ".txt"), v.toString(), StandardCharsets.UTF_8);
        Files.writeString(bloqueos.resolve(String.format("bloqueo.%04d.txt", aaaamm % 10000)),
                "01d02h00m-01d06h00m:40,40,45,40\n", StandardCharsets.UTF_8);
    }

    /** Etapa 28, ventanas reales: cada réplica es un tramo real de 5 días de un mes (inicio 1, 6, 11,
     *  16, 21 o 26), el mismo para TABU y AG, y las réplicas son tramos distintos. */
    @Test
    void ventanasRealesSonTramosDeCincoDiasComunesAAmbosAlgoritmos() throws IOException {
        Path v = Files.createDirectories(carpeta.resolve("ventas")), b = Files.createDirectories(carpeta.resolve("bloqueos"));
        mesDePrueba(v, b, 202609, 30);
        mesDePrueba(v, b, 202610, 31);
        Path mant = carpeta.resolve("mant.txt");
        Files.writeString(mant, "20260903:TA01\n20261003:TA02\n", StandardCharsets.UTF_8);
        Experimento.ARCHIVO_EXPLICITO = false;
        ExperimentoSimulacion.ARCHIVO_BLOQUEOS = null;
        ExperimentoSimulacion.CARPETA_VENTAS = v.toString();
        ExperimentoSimulacion.CARPETA_BLOQUEOS = b.toString();
        ExperimentoSimulacion.MESES = "202609-202610";
        ExperimentoSimulacion.ARCHIVO_MANTENIMIENTO = mant.toString();
        ExperimentoSimulacion.SITUACIONES = "ventanas";
        ExperimentoSimulacion.CARGAS = null;
        ExperimentoSimulacion.ESCENARIO = Simulador.Escenario.SIM_5D;
        ExperimentoSimulacion.ACELERADO = true;
        ExperimentoSimulacion.HILOS = 2;
        Experimento.REPLICAS = 3;
        Experimento.MAX_EVALUACIONES = 10;
        Path csv = carpeta.resolve("ventanas.csv");
        Experimento.SALIDA_CSV = csv.toString();
        ExperimentoSimulacion.ejecutar();

        List<String> lineas = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<String> cab = Arrays.asList(lineas.get(0).split(","));
        int alg = cab.indexOf("algoritmo"), rep = cab.indexOf("replica"), sem = cab.indexOf("semilla_carga"),
                arch = cab.indexOf("archivo_carga"), dia = cab.indexOf("dia_inicio"), sit = cab.indexOf("situaciones");
        Map<String, String> semillas = new HashMap<>();
        for (String l : lineas.subList(1, lineas.size())) {
            String[] c = l.split(",", -1);
            String s = c[sem];   // aaaammdd de inicio de la ventana
            assertTrue(s.matches("2026(09|10)(01|06|11|16|21|26)"), s);
            assertEquals("ventas." + s.substring(0, 6) + ".txt", c[arch]);
            assertEquals(Integer.parseInt(s.substring(6)), Integer.parseInt(c[dia]));
            assertEquals("ventanas", c[sit]);
            semillas.put(c[alg] + " " + c[rep], s);
        }
        assertEquals(6, semillas.size());   // 2 algoritmos × 3 réplicas
        for (int r = 1; r <= 3; r++) assertEquals(semillas.get("TABU " + r), semillas.get("AG " + r));
        assertEquals(3, semillas.values().stream().distinct().count());
    }

    /** Etapa 28: las ventanas terminan antes del último pedido del mes (en los datos del profesor los
     *  5 000 pedidos de un mes cubren cada vez menos días) y --meses NOMBRE=rango define un nivel por
     *  grupo de meses. Mes A: 30 días con pedidos -> inicios 1, 6, 11, 16, 21 (26 no cabe, el último
     *  pedido es el día 30 a las 16:00). Mes B: 12 días -> inicios 1 y 6. */
    @Test
    void ventanasSoloDentroDeLosDiasConPedidosYNivelesPorGrupoDeMeses() throws IOException {
        Path v = Files.createDirectories(carpeta.resolve("ventas")), b = Files.createDirectories(carpeta.resolve("bloqueos"));
        mesDePrueba(v, b, 202609, 30);
        mesDePrueba(v, b, 202610, 12);
        Experimento.ARCHIVO_EXPLICITO = false;
        ExperimentoSimulacion.ARCHIVO_BLOQUEOS = null;
        ExperimentoSimulacion.CARPETA_VENTAS = v.toString();
        ExperimentoSimulacion.CARPETA_BLOQUEOS = b.toString();
        ExperimentoSimulacion.MESES = "A=202609-202609,B=202610-202610";
        ExperimentoSimulacion.ARCHIVO_MANTENIMIENTO = null;
        ExperimentoSimulacion.SITUACIONES = "ventanas";
        ExperimentoSimulacion.CARGAS = null;
        ExperimentoSimulacion.ESCENARIO = Simulador.Escenario.SIM_5D;
        ExperimentoSimulacion.ACELERADO = true;
        ExperimentoSimulacion.HILOS = 4;
        Experimento.REPLICAS = 10;   // más que las ventanas disponibles: se usan todas
        Experimento.MAX_EVALUACIONES = 5;
        Path csv = carpeta.resolve("grupos.csv");
        Experimento.SALIDA_CSV = csv.toString();
        ExperimentoSimulacion.ejecutar();

        List<String> lineas = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<String> cab = Arrays.asList(lineas.get(0).split(","));
        int niv = cab.indexOf("nivel"), sem = cab.indexOf("semilla_carga"), lleg = cab.indexOf("pedidos_llegados");
        Map<String, java.util.Set<String>> porNivel = new HashMap<>();
        for (String l : lineas.subList(1, lineas.size())) {
            String[] c = l.split(",", -1);
            porNivel.computeIfAbsent(c[niv], k -> new java.util.TreeSet<>()).add(c[sem]);
            assertTrue(Integer.parseInt(c[lleg]) > 0, "ventana sin pedidos: " + c[sem]);
        }
        assertEquals(java.util.Set.of("20260901", "20260906", "20260911", "20260916", "20260921"), porNivel.get("A"));
        assertEquals(java.util.Set.of("20261001", "20261006"), porNivel.get("B"));
        assertEquals(2 * (5 + 2), lineas.size() - 1);   // ambos algoritmos en cada ventana
    }

    /** Etapa 28: el archivo oficial de mantenimiento trae dos meses; se usan solo los del mes simulado. */
    @Test
    void mantenimientoSoloDelMesSimulado() throws IOException {
        Path mant = carpeta.resolve("mant.preventivo.09.10.txt");
        Files.writeString(mant, "20260903:TA01\n20261003:TA02\n20261015:TM01\n", StandardCharsets.UTF_8);
        List<Mantenimiento> sep = Mantenimiento.leer(mant.toString(), 202609);
        assertEquals(1, sep.size());
        assertEquals("TA01", sep.get(0).unidad);
        assertEquals(2 * 1440.0, sep.get(0).inicioMin, 1e-9);   // día 3 -> minuto 2880
        assertEquals(2, Mantenimiento.leer(mant.toString(), 202610).size());
        assertEquals(3, Mantenimiento.leer(mant.toString()).size());   // sin mes: todos
        assertEquals(202609, Mantenimiento.mesDeArchivo("juego_de_datos/ventas.202609.txt"));
        assertEquals(202609, Mantenimiento.mesDeArchivo("datos/ventas202609_SINTETICO_MES.txt"));
    }

    /** Por nivel (diseño anterior): todas las réplicas usan la misma situación. */
    @Test
    void porNivelTodasLasReplicasUsanLaMismaSituacion() throws IOException {
        Map<String, String> s = correr("por_nivel");
        assertEquals(1, s.values().stream().distinct().count());
    }
}
