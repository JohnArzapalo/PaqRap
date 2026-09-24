# Diagrama de secuencia: un ciclo de replanificación

Sale del código de `Simulador.correr()` y `Simulador.replanificar(t)` (semana 08). El ciclo se dispara por el periodo Sa (60 min) o por un evento: una avería, del archivo o externa; un bloqueo encontrado en el camino; el fin de una avería o de un mantenimiento.

```mermaid
sequenceDiagram
    autonumber
    participant V as Visualizador (futuro)
    participant SV as ServicioSimulacion
    participant S as Simulador
    participant R as Reloj
    participant CX as Contexto (hilo de simulación)
    participant P as Planificador (Tabú o AG)
    participant C as Compartido
    participant M as MapaVial

    Note over S: correr(): el cerrojo se toma al inicio de cada vuelta
    S->>S: próximo instante T = min(Sa, eventos, bloqueos, averías, mantenimiento, siguientePlazo, reposición)
    S->>R: esperarHasta(T) [se suelta el cerrojo]
    opt avería registrada desde el visualizador
        V->>SV: registrarAveria("TA01", 1)
        SV->>S: inyectarAveria() → cola + interrupt()
        R-->>S: false (interrumpido)
        S->>R: ahora(T) → T' (minuto simulado real)
    end
    S->>S: aplicarEventosHasta(T): NODO, RECARGA, TRASVASE, ENTREGA, FIN
    S->>S: verificarColapso(T)
    alt una entrega superó su hora límite
        S-->>SV: fin: colapso (pedido, unidad, causa)
    else sin colapso
        S->>S: aplicarAveria(...) / fin de avería / mantenimiento / bloqueo encontrado
        S->>S: replanificar(T)
        activate S
        S->>S: velocidades pendientes (P16)
        S->>M: bloqueadoDurante(destino, registro, límite) [si la regla es no_evaluable]
        S->>S: admite los pedidos nuevos → estado P
        S->>S: dividirUrgentes(T, operativas)
        S->>S: new Contexto(): instante, mapa, stock, Inicio por unidad, averiadas
        S->>S: aPlanificar = copiaRelativa(): P, U:cod → aBordoDe, A:cod → enAveriada
        S->>CX: Contexto.usar(cx)
        S->>S: cx.asignacionVigente = asignación anterior (estabilidad)
        S->>S: cx.planBase = planVigenteReparado() → armarReparado + mejorInsercion
        S->>P: planificar(EstadoPlanificacion(cx, aPlanificar, flota, Ta, tope, semilla)) [se suelta el cerrojo]
        activate P
        P->>CX: Contexto.usar(e.contexto)
        loop hasta agotar Ta o el tope de evaluaciones
            P->>C: evaluarSolucion / evaluarRuta (H, S, factibilidad)
            C->>CX: inicioDe, distanciaTramo, puntoDeEntrega, esperaDestino
            CX->>M: distanciaTramo(x1, y1, x2, y2, t, v) (BFS con la ventana del tramo)
        end
        P-->>S: Plan(rutas, sinAsignar, iteraciones, evaluaciones)
        deactivate P
        S->>S: vacía las colas de eventos (conserva el nodo comprometido)
        S->>S: comprometer(T, cx, plan, operativas)
        loop por cada ruta del plan
            S->>C: evaluarRuta(r, hitos) → línea de tiempo
            S->>M: caminoTramo(...) → eventos NODO km a km
            S->>S: encuentroMin (primer bloqueo en el camino), cambios de unidad
        end
        S->>CX: Contexto.restablecer()
        deactivate S
    end
    V->>SV: instantanea() (en cualquier momento)
    SV->>S: instantaneaJson() [cerrojo]
    S-->>V: JSON: reloj, semáforo, unidades, rutas, bloqueos, pedidos
```

**Puntos que conviene poder explicar**
1. El planificador **nunca** ve el estado absoluto: recibe copias relativas al instante T (`copiaRelativa`) y un `Contexto` con el punto y la hora desde los que cada unidad puede empezar.
2. Una unidad en viaje queda **comprometida** hasta el siguiente nodo: su `Inicio` es ese nodo, y el evento NODO se vuelve a encolar después de planificar.
3. Ambos algoritmos parten del plan vigente reparado (`cx.planBase`). El AG además garantiza no devolver algo peor que ese plan.
4. El cerrojo se suelta durante la espera del reloj y durante la llamada al planificador. Por eso la instantánea puede leerse mientras se planifica.
5. El cambio de velocidad solo se aplica al inicio de `replanificar`. Así no cambia a mitad de una línea de tiempo ya comprometida (P16).
