# Diagrama de clases (a partir del código real)

Clases del paquete `pe.edu.pucp.gamesoft.paqrap` a fin de la semana 08. Solo aparecen los atributos y métodos necesarios para entender la estructura; los nombres son los del código. Las clases anidadas usan la notación `Externa_Interna` porque Mermaid no admite el punto.

## 1. Modelo y evaluación

```mermaid
classDiagram
    direction LR
    class TipoUnidad {
        <<enum>>
        AUTO
        MOTO
        BICICLETA
        int capacidadMaxima
        double costoPorKilometro
        double velocidadPromedio
    }
    class UnidadTransporte {
        String codigo
        TipoUnidad tipo
    }
    class Almacen {
        String codigo
        int x
        int y
    }
    class Pedido {
        String id
        String idOriginal
        int x
        int y
        int cantidad
        double horaRegistro
        int plazoMaximoHoras
        String aBordoDe
        String enAveriada
        horaLimite() double
        copiaRelativa(double) Pedido
    }
    class TipoParada {
        <<enum>>
        ENTREGA
        RECARGA
        TRASVASE
    }
    class ParadaAlg {
        TipoParada tipo
        Pedido pedido
        Almacen almacen
        String unidadAveriada
        int cantidad
    }
    class RutaAlg {
        UnidadTransporte unidad
        List~ParadaAlg~ paradas
        double distanciaKm
        double costo
    }
    class Solucion {
        List~RutaAlg~ rutas
        List~Pedido~ pedidosSinAsignar
        int H
        double S
        int rutasInfactibles
        int excesoStock
    }
    class Contexto {
        <<ThreadLocal>>
        double instanteBaseH
        MapaVial mapa
        List~AlmacenPlan~ almacenes
        Map inicios
        Map averiadas
        Solucion planBase
        ReglaDestino reglaDestino
        Map asignacionVigente
        double penalidadCambio
        actual()$ Contexto
        usar(Contexto)$
        restablecer()$
        inicioDe(UnidadTransporte) Inicio
        distanciaTramo(...) double
        puntoDeEntrega(...) int[]
        esperaDestino(...) double
        almacenMasCercano(int, int) Almacen
    }
    class Contexto_Inicio {
        int x
        int y
        double inicioH
        double noDisponibleDesdeH
    }
    class Contexto_AlmacenPlan {
        Almacen almacen
        double stock
    }
    class Contexto_Averiada {
        String codigo
        int x
        int y
        double hastaH
    }
    class Compartido {
        <<utilidad>>
        evaluarRuta(RutaAlg, List~Hito~)$ EvalRuta
        evaluarRuta(RutaAlg)$ EvalRuta
        evaluarSolucion(Solucion)$
        esFactible(Solucion)$ boolean
        mejorQue(Solucion, Solucion)$ boolean
    }
    class Compartido_EvalRuta {
        double km
        double costo
        double finH
        int tarde
        int cambiosDeUnidad
        boolean factible
        String motivo
    }
    class Hito {
        Tipo tipo
        double inicioH
        double finH
        ParadaAlg parada
    }
    class MapaVial {
        int ANCHO$
        int ALTO$
        leer(String)$ MapaVial
        bloqueado(int, int, double) boolean
        bloqueadoDurante(int, int, double, double) boolean
        distanciaTramo(...) int
        caminoTramo(...) List
        finBloqueo(int, int, double) double
    }
    UnidadTransporte --> TipoUnidad
    RutaAlg --> UnidadTransporte
    RutaAlg *-- ParadaAlg
    ParadaAlg --> TipoParada
    ParadaAlg --> Pedido
    ParadaAlg --> Almacen
    Solucion *-- RutaAlg
    Solucion o-- Pedido : sin asignar
    Contexto *-- Contexto_Inicio
    Contexto *-- Contexto_AlmacenPlan
    Contexto *-- Contexto_Averiada
    Contexto --> MapaVial
    Contexto --> Solucion : planBase
    Compartido ..> Contexto : actual()
    Compartido ..> Compartido_EvalRuta
    Compartido ..> Hito : produce
```

## 2. Algoritmos y planificador

```mermaid
classDiagram
    direction LR
    class Planificador {
        <<interface>>
        planificar(EstadoPlanificacion) Plan
        tabu(int)$ Planificador
        genetico(int)$ Planificador
    }
    class Planificador_EstadoPlanificacion {
        Contexto contexto
        List~Pedido~ pedidos
        List~UnidadTransporte~ flota
        long presupuestoMs
        long maxEvaluaciones
        long semilla
    }
    class Planificador_Plan {
        List~RutaAlg~ rutas
        List~Pedido~ sinAsignar
        long iteraciones
        long evaluaciones
        de(Solucion, long, long)$ Plan
    }
    class BusquedaTabu {
        ejecutarDesdeCero(...)$ Solucion
        ejecutar(Solucion, List, ...)$ Solucion
        -buscar(...)$ Solucion
        -generarVecino(Solucion, boolean)$ Movimiento
    }
    class ListaTabu
    class ParTabu
    class Movimiento {
        Solucion solucion
        List~ParTabu~ atributos
        List~ParTabu~ prohibir
    }
    class Heuristicaconstructiva {
        construirSolucionInicial(...)$ Solucion
    }
    class OperadoresVecindario {
        reubicacion(...)$
        intercambio(...)$
        dosOpt(...)$
        crossExchange(...)$
        recarga(...)$
        insercion(...)$
        mejorInsercion(Solucion, List, Pedido)$ PosicionInsercion
        conUnidadesLibres(...)$
    }
    class AlgoritmoGenetico {
        ejecutar(...)$ Solucion
        inicializarPoblacion(...)$
        cruceOX(...)$
        mutar(...)$
        busquedaLocal(...)$
        splitConEstado(Cromosoma, List)$ Solucion
        cortar(...)$
        asignarTramo(...)$
    }
    class Cromosoma {
        List~Pedido~ permutacion
        Map tipoAsignado
        double aptH
        double aptS
    }
    class Poblacion
    Planificador ..> Planificador_EstadoPlanificacion
    Planificador ..> Planificador_Plan
    Planificador ..> BusquedaTabu : tabu()
    Planificador ..> AlgoritmoGenetico : genetico()
    BusquedaTabu --> ListaTabu
    ListaTabu *-- ParTabu
    BusquedaTabu ..> Movimiento
    Movimiento o-- ParTabu
    BusquedaTabu ..> Heuristicaconstructiva : primer ciclo
    BusquedaTabu ..> OperadoresVecindario
    AlgoritmoGenetico --> Poblacion
    Poblacion *-- Cromosoma
    AlgoritmoGenetico ..> OperadoresVecindario : búsqueda local
    BusquedaTabu ..> Compartido
    AlgoritmoGenetico ..> Compartido
```

## 3. Simulador, experimento e integración

```mermaid
classDiagram
    direction LR
    class Simulador {
        Config cfg
        ReentrantLock cerrojo
        crear(...)$ Simulador
        simular(...)$ Resultado
        ejecutar() Resultado
        -correr()
        -aplicarEventosHasta(double)
        -verificarColapso(double) boolean
        -aplicarAveria(Averia, double) boolean
        -replanificar(double)
        -dividirUrgentes(double, List)
        -planVigenteReparado(...) Solucion
        -comprometer(...)
        inyectarAveria(String, int)
        cambiarVelocidad(TipoUnidad, double)
        detener()
        instantaneaJson() String
    }
    class Simulador_Config {
        Escenario escenario
        double saMin
        long taMs
        MapaVial mapa
        List~Averia~ averias
        List~Mantenimiento~ mantenimientos
        Reloj reloj
        ReglaDestino reglaDestino
        double penalidadEstabilidad
    }
    class Simulador_Escenario {
        <<enum>>
        EXPERIMENTO
        SIM_5D
        COLAPSO
        DIA_A_DIA
    }
    class Simulador_Resultado {
        boolean censurada
        double colapsoMin
        String causaColapso
        List~Evt~ registro
        int cambiosDeUnidad
        int pedidosInentregablesBloqueo
    }
    class Simulador_Unidad {
        EstadoUnidad estado
        int x
        int y
        ArrayDeque~Evt~ eventos
        Set~String~ aBordo
    }
    class Simulador_Evt {
        T tipo
        double min
    }
    class Reloj {
        <<interface>>
        esperarHasta(double) boolean
        ahora(double) double
        sinEspera()$
        escalado(double)$
        real()$
    }
    class Averia {
        double tiempoMin
        String unidad
        int tipo
        enLugarHastaMin() double
        disponibleDesdeMin() double
    }
    class Mantenimiento {
        String unidad
        double inicioMin
        double finMin
    }
    class ServicioSimulacion {
        iniciar()
        instantanea() String
        registrarAveria(String, int)
        cambiarVelocidad(TipoUnidad, double)
        detener()
        esperar(long) Resultado
    }
    class EscritorJson
    class ExperimentoSimulacion {
        ejecutar(...)$
        calibrar(...)$ Map
        sha256(Path)$ String
    }
    class Experimento {
        main(String[])$
    }
    class CapacidadFlota
    class GeneradorCarga
    class LectorPedidos
    class ValidadorEntradas
    Simulador *-- Simulador_Config
    Simulador *-- Simulador_Unidad
    Simulador ..> Simulador_Resultado
    Simulador_Resultado *-- Simulador_Evt
    Simulador_Config --> Simulador_Escenario
    Simulador_Config --> Reloj
    Simulador_Config --> MapaVial
    Simulador_Config o-- Averia
    Simulador_Config o-- Mantenimiento
    Simulador --> Planificador
    Simulador ..> Contexto : arma en cada ciclo
    Simulador ..> Compartido : evaluarRuta con hitos
    Simulador ..> EscritorJson
    ServicioSimulacion --> Simulador
    Experimento ..> ExperimentoSimulacion : --modo simulacion
    ExperimentoSimulacion ..> Simulador
    ExperimentoSimulacion ..> CapacidadFlota
    ExperimentoSimulacion ..> GeneradorCarga
    ExperimentoSimulacion ..> LectorPedidos
    ExperimentoSimulacion ..> ValidadorEntradas
```

**Notas**
- `Contexto` guarda el contexto activo por hilo (`ThreadLocal`). Sin contexto explícito, el contexto por defecto reproduce el modelo simple que usan `Main` y el modo estático.
- `Compartido` es la **única** función objetivo: la usan ambos algoritmos y el simulador. Así la comparación Tabú vs AG es justa.
- `ServicioSimulacion` y `EscritorJson` son la capa de integración, independiente de la tecnología web (Etapa 21).
