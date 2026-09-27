# Propuesta: arquitectura de integración simulador–visualizador

**Estado:** propuesta para que el equipo decida. No elige framework ni implementa el frontend. Lo único implementado es la capa independiente de la tecnología (Etapa 21.2): `ServicioSimulacion`, `Simulador.instantaneaJson`, `inyectarAveria` y `cambiarVelocidad`, con `IntegracionTest`.

## 1. Qué ofrece hoy el núcleo (común a todas las opciones)

| Necesidad | Método ya disponible | Comportamiento |
|---|---|---|
| Correr un escenario | `new ServicioSimulacion(pedidos, flota, planificador, cfg).iniciar()` | Hilo propio. `cfg.escenario` y `cfg.reloj` fijan el ritmo. |
| Ver el estado | `instantanea()` → JSON | Estado **completo** del momento: reloj (simulado y real), semáforo, colapso, almacenes con stock, unidades (estado, posición, carga, paradas, camino), bloqueos activos, pedidos e indicadores. Se puede llamar desde cualquier hilo; se arma con el cerrojo tomado. |
| Registrar una avería | `registrarAveria(unidad, tipo)` | Se aplica en el minuto simulado actual e interrumpe la espera del reloj (SI-18). |
| Cambiar la velocidad | `cambiarVelocidad(tipo, km/h)` | Rige desde la siguiente replanificación (P6, P16). |
| Detener y resultado | `detener()`, `esperar(ms)` | Devuelve `Simulador.Resultado` (colapso, causa, métricas). |

Cómo se conecta cada escenario:
- **SIM_5D:** `Reloj.escalado(f)`, con f = minutos simulados por minuto real (5 días en 30 min → f = 240). La instantánea avanza con el tiempo real.
- **COLAPSO:** hoy corre con `Reloj.sinEspera()` y termina en segundos o minutos, así que no se puede "mirar". Hay dos formas de verlo:
  - (a) correrlo con `Reloj.escalado(f)` alto, con `detenerEnColapso` activado;
  - (b) correrlo sin espera, guardar el registro de eventos (`--eventos`) y reproducirlo en el visualizador.
  (b) no bloquea el servidor, pero exige un reproductor.
- **DIA_A_DIA:** `Reloj.real()`. **Pendiente:** hoy los pedidos se leen del archivo al inicio. Para registrar pedidos en vivo hace falta un `inyectarPedido` análogo a `inyectarAveria` (cola concurrente, admitido en la siguiente replanificación). No se implementó porque el formato del registro en vivo no está definido.

> **Actualización (etapa 24):** la restricción SI-19 ya no rige. Los algoritmos no tienen estado estático y la velocidad cambiada en caliente es por simulación, así que varios escenarios pueden correr en el mismo proceso, cada uno en su hilo. Lo que sigue describe la situación anterior.

Restricción que afecta a todas las opciones (**SI-19**): una simulación por JVM. El `Contexto` es por hilo, pero los algoritmos guardan estado estático y la velocidad es global por tipo de unidad. Para correr dos escenarios a la vez hacen falta dos procesos, o antes una refactorización que quite ese estado estático.

## 2. Opciones

### Opción A: servidor en el mismo proceso, con difusión por canal bidireccional (tipo WebSocket)

```
[navegadores / dispositivos] ⇄ canal bidireccional ⇄ [servidor Java + ServicioSimulacion] (una JVM)
                              ⇆ HTTP (comandos)
```
- El servidor mantiene un `ServicioSimulacion` activo. Un temporizador llama a `instantanea()` cada Δ (p. ej., 1 s real) y **difunde** el mismo JSON a todos los clientes conectados.
- Los comandos llegan por HTTP (o por el mismo canal): iniciar escenario, registrar avería, cambiar velocidad, detener.
- Varios dispositivos a la vez: todos reciben la misma instantánea. Un dispositivo que se conecta tarde recibe el estado completo en el siguiente envío, porque la instantánea no es incremental.

| Pros | Contras | Esfuerzo estimado |
|---|---|---|
| Tiempo real de verdad (empuje) y una sola fuente de verdad. | Hay que manejar conexiones, reconexión y clientes lentos. | **Medio.** Servidor, 3 o 4 endpoints de comando, difusión periódica y el frontend del mapa. |
| Reusa todo lo implementado: el servidor solo llama a `ServicioSimulacion`. | Una simulación por JVM (SI-19): un escenario a la vez por proceso. | |
| La avería en caliente se ve en el siguiente envío (≤ Δ). | Algunas redes (proxy del campus) pueden cortar conexiones persistentes. | |

### Opción B: servidor en el mismo proceso, con HTTP simple (consulta periódica o eventos del servidor)

```
[dispositivos] --GET /instantanea cada Δ--> [servidor Java + ServicioSimulacion]
               --POST /averia, /velocidad, /escenario-->
```
- Cada cliente pide la instantánea cada Δ (consulta periódica), o se suscribe a un flujo unidireccional de eventos del servidor.
- Los comandos van por POST.

| Pros | Contras | Esfuerzo estimado |
|---|---|---|
| Lo más simple de construir y de probar (cualquier navegador o `curl`). | Con consulta periódica, cada cliente genera su propia carga (N clientes = N instantáneas por Δ). Se mitiga guardando la última instantánea en caché del servidor. | **Bajo.** 1 GET y 3 o 4 POST. |
| Funciona detrás de proxies y firewalls. | Latencia de hasta Δ; no es empuje puro (salvo con eventos del servidor). | |
| Fácil de migrar a A después, porque los endpoints de comando son los mismos. | La misma restricción SI-19. | |

### Opción C: simulación en procesos separados más un coordinador

```
[dispositivos] ⇄ [coordinador (HTTP / canal)] ⇄ [proceso SIM_5D] [proceso COLAPSO] [proceso DIA_A_DIA]
```
- Cada escenario corre en su propia JVM con `ServicioSimulacion`, y cumple SI-19 sin refactorizar.
- Un coordinador lanza y detiene procesos, reenvía comandos y difunde las instantáneas.
- Los procesos se comunican con el coordinador por HTTP local, entrada y salida estándar u otro medio.

| Pros | Contras | Esfuerzo estimado |
|---|---|---|
| Varios escenarios **simultáneos** (p. ej., una demo de SIM_5D mientras corre COLAPSO). | Más piezas: ciclo de vida de los procesos, puertos y errores parciales. | **Alto.** Coordinador, protocolo interno y lanzamiento de procesos, además de lo de A o B. |
| Aísla fallos: si un escenario se cae, los demás siguen. | Más memoria: una JVM y una caché BFS por escenario. | |
| Escala a varias máquinas si hiciera falta. | Excesivo si solo se muestra un escenario a la vez. | |

## 3. Comparación resumida

| Criterio | A | B | C |
|---|---|---|---|
| Tiempo real para varios dispositivos | Empuje, bueno | Consulta cada Δ, aceptable | Según A o B dentro del coordinador |
| Avería desde el visualizador | Sí | Sí | Sí (reenviada) |
| Cambio de velocidad en caliente | Sí, desde la siguiente replanificación | Igual | Igual (por proceso) |
| Escenarios simultáneos | No (SI-19) | No (SI-19) | **Sí** |
| Esfuerzo | Medio | Bajo | Alto |
| Riesgo de red en la exposición | Medio | Bajo | Medio |

**Criterios de decisión sugeridos, sin elegir por el equipo:**
1. ¿El profesor exige ver dos escenarios a la vez? Si sí, solo C lo resuelve sin refactorizar.
2. ¿Qué latencia aceptan para "tiempo real"? Con Δ = 1 s, B suele ser suficiente.
3. ¿Qué experiencia tiene el equipo con conexiones persistentes? Si es poca, B primero y migrar a A después (los comandos no cambian).

## 4. Puntos abiertos (dependen del profesor o del equipo)

- Formato del registro de averías y de pedidos en vivo (DIA_A_DIA): hoy es provisional.
- Si el cambio de velocidad debe regir de inmediato, a mitad de un tramo, en lugar de desde la siguiente replanificación (hoy es lo segundo; replanificar a mitad de un tramo rompería la línea de tiempo comprometida).
- Frecuencia de envío Δ y tamaño de la instantánea con la carga real: medir con los datos oficiales. Si fuera grande, se puede enviar el `camino` de cada unidad solo cuando cambia.
- Autenticación: quién puede registrar averías (hoy, cualquier cliente que llame al método).
- Quitar el estado estático de los algoritmos (semilla, contadores) y la velocidad global, si se quieren varios escenarios en una JVM.
