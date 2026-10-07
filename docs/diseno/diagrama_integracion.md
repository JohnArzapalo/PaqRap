# Interacción entre el Planificador y el Visualizador (etapa 32)

La solución integrada tiene **dos componentes** que se comunican **solo por una API HTTP**:

- **Planificador** (Java, `ServidorWeb`): carga los datos oficiales, simula y planifica con Búsqueda Tabú. Corre una simulación por escenario, en su propio hilo, y guarda el estado en memoria.
- **Visualizador** (HTML + JavaScript, `web/index.html`): dibuja el estado y envía comandos. **No simula nada**: todo lo que muestra viene del planificador.

Cualquier dispositivo con un navegador puede abrir el visualizador y ver cualquiera de los tres escenarios. Todos los dispositivos ven **la misma corrida**, porque el estado vive en el planificador.

## 1. Componentes y despliegue

```mermaid
flowchart LR
    subgraph Dispositivos["Dispositivos (navegador)"]
        PC["PC del operador<br/>rol: control"]
        Cel["Celular / tablet<br/>rol: observador"]
        Proy["Proyector del aula<br/>rol: observador"]
    end

    subgraph AWS["Servidor (instancia AWS EC2, Java 21)"]
        subgraph SW["ServidorWeb (com.sun.net.httpserver)"]
            EST["Archivos del visualizador<br/>web/index.html, vendor/leaflet"]
            API["API REST<br/>/api/corridas/{ESC}/..."]
        end
        subgraph Corridas["Una CorridaWeb por escenario (hilos independientes)"]
            C1["DIA_A_DIA<br/>reloj real"]
            C2["SIM_5D<br/>5 días en 30 min"]
            C3["COLAPSO<br/>hasta el primer pedido tarde"]
        end
        SIM["ServicioSimulacion → Simulador<br/>(eventos discretos, colapso)"]
        TABU["Planificador: Búsqueda Tabú<br/>(Compartido, Contexto, reparto por productos)"]
        DATOS[("juego_de_datos/<br/>ventas, bloqueos, mantenimiento")]
    end

    PC & Cel & Proy -- "GET / (HTML, JS)" --> EST
    PC & Cel & Proy -- "GET estado cada 1 s<br/>GET eventos" --> API
    PC -- "POST iniciar, finalizar,<br/>averías, velocidades, pedidos" --> API
    API --> C1 & C2 & C3
    C1 & C2 & C3 --> SIM
    SIM -- "cada Sa = 60 min y ante eventos" --> TABU
    DATOS -- "DatosOficiales.cargar" --> C1 & C2 & C3
```

## 2. Secuencia de una corrida

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operador (rol control)
    participant V as Visualizador (navegador)
    participant API as ServidorWeb (API REST)
    participant C as CorridaWeb (escenario)
    participant S as Simulador (hilo propio)
    participant T as Búsqueda Tabú
    actor Obs as Otro dispositivo (observador)

    Op->>V: Elige escenario, fecha de inicio, ritmo y flota
    V->>API: POST /api/corridas/SIM_5D (inicio, factor, flota)
    API->>C: CorridaWeb.iniciar(...)
    C->>C: DatosOficiales.cargar(mes, inicio, horizonte)
    C->>S: ServicioSimulacion.iniciar() (nuevo hilo)
    API-->>V: 200 {corrida, estado}

    loop Cada Sa (60 min simulados) y ante eventos
        S->>T: planificar(estado real, plan vigente, Ta = 2 s)
        T-->>S: rutas por unidad (productos repartidos)
        S->>S: compromete el plan y avanza el reloj (Reloj.escalado)
    end

    par Cada segundo, en cada dispositivo
        V->>API: GET /api/corridas/SIM_5D/estado
        API->>S: instantaneaJson() (con cerrojo)
        S-->>V: reloj, unidades, rutas, pedidos, bloqueos, indicadores
        V->>API: GET /api/corridas/SIM_5D/eventos?desde=n
        API-->>V: eventos nuevos (bitácora)
    and
        Obs->>API: GET /api/corridas/SIM_5D/estado
        API-->>Obs: la misma instantánea
    end

    opt Evento externo (rol control)
        Op->>V: Registra avería / cambia velocidad / registra pedido (día a día)
        V->>API: POST /api/corridas/{ESC}/averias | velocidades | pedidos
        API->>S: inyectarAveria / cambiarVelocidad / inyectarPedido
        S->>T: replanificación inmediata (evento)
    end

    alt Un producto supera su hora límite
        S-->>API: estado COLAPSO (la corrida termina)
    else Se cumple el horizonte (5 días) o se finaliza
        S-->>API: estado FINALIZADA
    end
    V->>API: GET /api/corridas/{ESC}/reporte
    API-->>V: resumen por tipo de unidad y plan al cierre
```

## 3. Contrato de la API

| Método y ruta | Datos | Respuesta |
|---|---|---|
| `GET /api/datos` | — | meses con datos oficiales (`desde`, `hasta`) |
| `GET /api/corridas` | — | estado de las tres corridas (para las pestañas) |
| `POST /api/corridas/{ESC}` | `inicio=aaaa-mm-ddThh:mm`, `inicio_ms`, `factor`, `autos`, `motos`, `bicicletas` | `{corrida, estado}`; 409 si ya hay una en ejecución |
| `GET /api/corridas/{ESC}/estado` | — | `{corrida, estado}`: la instantánea completa |
| `GET /api/corridas/{ESC}/eventos?desde=n` | — | `{total, eventos[]}` desde el evento n |
| `GET /api/corridas/{ESC}/reporte` | — | resumen por tipo de unidad y plan vigente |
| `POST /api/corridas/{ESC}/finalizacion` | — | detiene la corrida |
| `POST /api/corridas/{ESC}/averias` | `unidad`, `tipo` (1, 2 o 3) | avería en el instante actual |
| `POST /api/corridas/{ESC}/velocidades` | `tipo`, `kmh` | rige desde la siguiente replanificación |
| `POST /api/corridas/DIA_A_DIA/pedidos` | `cliente`, `x`, `y`, `cantidad`, `plazo` | `{id, registro_min, limite_min}` |

`{ESC}` es `SIM_5D`, `COLAPSO` o `DIA_A_DIA`. Los datos de entrada van como formulario (`application/x-www-form-urlencoded`) y las respuestas son JSON. La API permite CORS, así que el visualizador también puede alojarse en otro dominio (`index.html?api=https://servidor`).

## 4. Decisiones de diseño

- **Consulta periódica (1 s) en lugar de WebSocket** (opción B de `docs/propuesta_arquitectura_integracion.md`): es lo más simple de construir y explicar, funciona detrás de cualquier proxy y no necesita librerías. La instantánea es completa, no incremental, así que un dispositivo que se conecta tarde ve todo en el siguiente segundo.
- **El visualizador interpola** el movimiento de las unidades a lo largo de su camino entre dos instantáneas, para que se vea fluido.
- **Un hilo por escenario:** desde la etapa 24 los algoritmos no tienen estado estático, así que las tres corridas pueden ir a la vez en el mismo proceso.
- **Rol de control u observador:** cualquier dispositivo puede mirar; solo el que tiene el rol de control muestra los botones de comandos. Para esta entrega el rol se elige en el navegador, sin autenticación.
