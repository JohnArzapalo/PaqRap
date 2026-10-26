# Planificación por bloques con adelanto (SI-30)

**Estado:**
- **Planificador: implementado en la etapa 33** (`Simulador`, pruebas en `BloquesTest`).
- **Visualizador: implementado en la etapa 35** (SI-32): `GET /api/corridas/{ESC}/bloques` y reproducción de las trayectorias con el reloj del navegador (pruebas en `TrayectoriasTest`). Ver §6.

Recoge la indicación del profesor (clase de la semana 8) y cómo la llevamos a PaqRap.

## 1. Indicación del profesor

- **Toda la simulación** se muestra en **bloques de 2 minutos reales**, también la de 5 días.
- No conviene esperar a que termine un bloque para pedirle al planificador el siguiente, porque eso produce un retraso. El planificador **se adelanta**: en su ejemplo, empieza a calcular el bloque 2 (minutos 2 a 4) en el minuto 1, mientras se muestra el bloque 1.
- El visualizador **recibe el bloque siguiente por adelantado** y lo tiene en cola: tiene dos juegos de datos, el bloque en curso y el siguiente. Así el retraso entre recibir y mostrar es mínimo y el planificador trabaja con más calma.
- **El problema es la transición entre bloques:** sus inicios y fines son exactos. Si el bloque 2 no empieza con cada vehículo exactamente donde lo dejó el bloque 1, los vehículos «saltan».

## 2. Antes de la etapa 33

- Se replanificaba cada Sa = 60 min simulados (y ante eventos). En ese instante la simulación **se detenía**, Tabú pensaba durante Ta = 2 s reales y el plan se aplicaba en el mismo instante.
- **Efecto visible:** una pausa de 2 s en cada replanificación y luego un salto, porque el reloj real siguió corriendo mientras el simulado estaba detenido. Es justo lo que el profesor quiere evitar.

## 3. Diseño implementado

**Decisión del equipo (etapa 33):** un bloque es **1 h simulada** y se planifica **30 min simulados antes**. Primero se eligió 2 h (2 min reales a ×60, el ejemplo del profesor), pero con los datos oficiales colapsaba en 11 de 12 ventanas de SIM_5D, frente a 4 de 12 con la replanificación cada 60 min y 3 de 12 con bloques de 1 h (§5). Volver a 2 h es cambiar dos parámetros y reiniciar el servidor.

```
Simulado (h):   0 ──── 0.5 ──── 1 ──── 1.5 ──── 2 ──── 2.5 ──── 3
Se aplica:      [ bloque 1 (0-1 h) ] [ bloque 2 (1-2 h) ] [ bloque 3 ...
Planificador:         [ calcula B2 ]       [ calcula B3 ]
                      ↑ corte de B2: lo llegado hasta aquí entra al plan de B2; lo que llega
                        después se inserta al empezar B2
```

| Parámetro (`config/parametros.properties`) | Por defecto | Significado |
|---|---|---|
| `bloque.min` | 60 | Duración de cada bloque, en minutos **simulados**. 0 = replanificación periódica cada Sa |
| `bloque.adelanto_min` | 30 | Minutos simulados antes del inicio del bloque en que empieza a planificarse (el **corte**) |

Los parámetros están en minutos simulados porque el planificador trabaja en tiempo simulado y porque así el experimento sin visualización (modo acelerado) usa exactamente las mismas reglas. Lo que dura cada bloque en la pantalla depende del ritmo K del escenario:

| Escenario y ritmo | Bloque (1 h simulada) | Adelanto (30 min simulados) |
|---|---|---|
| 1 min real = 1 h simulada (×60, ejemplo del profesor) | 1 min real | 30 s |
| SIM_5D en 60 min (×120) | 30 s | 15 s |
| SIM_5D en 30 min (×240) | 15 s | 7.5 s |
| SIM_5D en 15 min (×480) | 7.5 s | 3.75 s |
| COLAPSO, 1 s = 10 min (×600) | 6 s | 3 s |
| DIA_A_DIA (×1) | 1 h real | 30 min reales |

Con el vocabulario del profesor (video TA/SA/CA/K): el **CA** (salto de consumo) es el bloque, 1 h simulada; el **SA** es su duración real, CA / K; el **TA** (2 s) debe ser menor que el adelanto en tiempo real. `CorridaWeb` rechaza un ritmo con el que el adelanto real quede por debajo de Ta; por eso el visualizador ya no ofrece «1 s = 20 min» (×1200, 1.5 s de adelanto) en el escenario hasta el colapso.

**Por qué no bloques de «2 min reales» con cualquier ritmo:** con SIM_5D en 30 min (×240), 2 min reales serían 8 h simuladas. El video del profesor advierte que un salto de 8 h (un turno) da una probabilidad alta de colapso: un pedido de 4 h que llega justo después del inicio de un bloque esperaría 8 h para tener unidad. Con bloques de 1 h, la espera máxima es de 1 h, la misma que con Sa = 60 min.

**Simulador (`Simulador.planificarPorBloques`):**
1. **Corte** (inicio del bloque *k* menos el adelanto): el simulador hace una **copia** de su estado y la avanza hasta el inicio del bloque siguiendo el plan vigente (`avanzarProyeccion`): posiciones, cargas, entregas, mantenimientos y reposición de stock. Es exacto, porque la línea de tiempo comprometida es determinista.
2. Tabú planifica el bloque *k* sobre esa copia, con los pedidos que llegaron hasta el corte, **en otro hilo** mientras la simulación sigue (en el visualizador; en el experimento, en el mismo hilo, con el mismo resultado).
3. **Inicio del bloque:** el simulador arma el estado real de partida y lo compara con el de la copia (`firma`: punto y hora de partida de cada unidad, entregas, stock y plan vigente reparado). Si coinciden, aplica el plan adelantado tal cual. Así cada unidad empieza el bloque donde terminó el anterior: no hay saltos. Si no coinciden (no debería pasar), planifica en el instante y lo anota en la bitácora.
4. Los pedidos que llegan **después del corte** no están en el plan adelantado. Al empezar el bloque se **insertan** en él con la misma inserción que repara el plan vigente (`insertarLlegadasTardias`; milisegundos, sin correr Tabú), y el bloque siguiente los optimiza con el resto. Sin esta inserción esperaban un bloque más el adelanto, y con los datos oficiales eso multiplicaba los colapsos (ver §5).
5. Si el plan no está listo al inicio del bloque, el simulador lo espera y lo cuenta en `bloquesTardios` (es el caso «SA menor que TA» del profesor).

**Vía de excepción (decisión del equipo: cortar el bloque; falta que el profesor lo confirme, pregunta 18):** replanifican en el instante, como antes, y descartan el plan adelantado, que se vuelve a calcular desde el estado nuevo. El equipo acepta la pausa breve que produce (unos 2 s), porque son imprevistos raros: de 0 a 3 por corrida de 5 días con los datos oficiales, más las averías que registre el evaluador.
- un **pedido urgente**: con bloques, la urgencia se mide al inicio del bloque siguiente a su llegada. Si ahí le quedarían menos de `simulacion.urgente_min` (120 min) hasta su hora límite efectiva, se replanifica al llegar (SI-29). Con bloques de 1 h, un pedido de 4 h normal nunca es urgente; sí lo es uno cuyo destino se bloquea poco después;
- en la **operación día a día**, el pedido que registra el operador (a ritmo real, esperar al bloque siguiente sería hasta 1 h);
- una **avería** (de archivo o registrada en el visualizador), el fin de su estadía en el lugar y su fin;
- un **bloqueo encontrado en el camino**.

El fin de un mantenimiento ya no replanifica: la unidad entra en el bloque siguiente.

**Tiempo del planificador (decisión del equipo):** el plan adelantado no detiene el reloj, así que Tabú puede usar más que Ta = 2 s: la mitad del adelanto en tiempo real (`bloque.fraccion_ta = 0.5`), al menos Ta y como mucho 10 s (`bloque.ta_max_ms`). Con 5 días en 60 min (el ritmo por defecto) el adelanto dura 15 s y Tabú usa 7.5 s; con 30 min, 3.75 s. La otra mitad es margen: proyectar el estado, una PC más lenta y más pedidos cerca del colapso. Las replanificaciones por excepción, que sí detienen el reloj, siguen con 2 s. Para la exposición: **TA** = 7.5 s por bloque (2 s en las excepciones), **SA** = 30 s reales (1 h simulada a ×120) y **CA** (Sc) = 1 h simulada.

**Ritmo de SIM_5D (decisión del equipo):** 60 min por defecto (×120); también se ofrece 30 min (×240). Se quitó «5 días en 15 min», que estaba fuera del rango de 30 a 60 min que fijó el profesor.

**Concurrencia:** el planificador y el simulador usan el mismo `MapaVial`, cuyas cachés de distancias ahora están sincronizadas. `Contexto` ya era por hilo (etapa 21) y cada llamada a Tabú crea su propio objeto (etapa 24).

## 4. Pruebas JUnit (`BloquesTest`)

- Todos los bloques, menos el primero, usan el plan calculado con adelanto: el estado proyectado coincidió con el real.
- Las unidades siempre avanzan a un nodo vecino (`saltos = 0`), con bloques y sin ellos.
- Un pedido que llega después del corte se planifica en el bloque siguiente.
- Un pedido urgente rompe el bloque y replanifica al llegar.
- En otro hilo el resultado es el mismo que en el hilo de la simulación.
- Con reloj real, el plan de cada bloque está listo antes de su inicio (`bloquesTardios = 0`).
- El adelanto no puede superar al bloque.

## 5. Experimento

- `ExperimentoSimulacion` acepta `--bloque 120 --adelanto 60`. Sin esas opciones replanifica cada Sa, como en las etapas 28 a 31. Así los resultados del experimento siguen siendo reproducibles.
- Las corridas del visualizador (`CorridaWeb`) usan bloques siempre.
- **Verificación con los datos oficiales** (MEDIA, 12 ventanas, Tabú, Ta = 500 ms; tabla completa en `docs/avance_sem07.md`, etapa 33):
  - periódico, 4/12 colapsos;
  - bloques de 2 h con inserción, 11/12;
  - bloques de 1 h con 30 min de adelanto e inserción, 3/12.
  - El tamaño del bloque en tiempo simulado decide cuánto espera un pedido nuevo, y eso decide el % de colapsos. **El equipo eligió 1 h.**

**Decisiones del equipo sobre esta etapa:**
- **No se repite el experimento con bloques:** Tabú se eligió comparando ambos algoritmos en las mismas condiciones, y los bloques cambian cuándo se calcula el plan, no el algoritmo.
- **No se mostrarán los pedidos «por planificar»** (los que llegaron durante el bloque y aún no tienen unidad): esperan como mucho 1 h simulada (30 s reales a ×120) y no le aportan al usuario del visualizador.
- **La operación día a día queda en espera:** por ahora, el pedido que registra el operador se planifica al registrarse; se revisará cuando se trabaje ese escenario.

## 6. Visualizador (hecho en la etapa 35)

Lo que sigue era el pendiente; se hizo tal cual. Además, el reloj se pausa mientras la simulación espera al planificador (SI-32), y el navegador sostiene a cada unidad en su lugar hasta tener las trayectorias del plan nuevo, así que tampoco salta con una avería.


- `GET /api/corridas/{ESC}/bloques`: la línea de tiempo (cada unidad, nodo a nodo, con su hora) del bloque en curso y, cuando esté listo, la del siguiente.
- El visualizador **reproduce** esas trayectorias según el reloj, con dos bloques en cola y una transición exacta, en vez de interpolar a ojo entre fotos. La foto por segundo se mantiene para los indicadores.
- El bloque siguiente que el visualizador tiene en cola puede cambiar al empezar, por la inserción de los pedidos llegados después del corte. Las posiciones no saltan (el cambio empieza donde está cada unidad), pero el visualizador debe reemplazar la cola con la línea de tiempo definitiva.
