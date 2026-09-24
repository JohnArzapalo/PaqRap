# Avance semana 08: etapas 16 a 21

Cada etapa se cierra con: compilación, todas las pruebas JUnit, un resumen aquí y un commit que cita la etapa.

## Etapa 16: control de versiones

- Repositorio git en `PaqRap` con `.gitignore`: compilación, `__pycache__`, `verificacion_*`, `analisis/salida*`, `datos/generados`, PNG y CSV de la raíz, IDE y `obsoleto_sem06`.
- **Historial reconstruido** en la rama `historial`, a partir de las copias en disco:
  - `v-sem06-expuesto` (desde `PaqRap_sem06_expuesto`);
  - `v-sem07-referencia` (desde `PaqRap_version_referencia_sem07`).
- `main` parte de ahí. Primer commit: "Estado etapas 8-15 (sem07)".
- `docs/control_configuracion.md`: elementos de configuración, convención de etiquetas y dónde está cada copia. Es insumo para el plan IEEE 828.
- Nota: en `v-sem07-referencia`, `BusquedaTabu` quedó en `Busquedatabu.java` (Windows no distingue mayúsculas); en `main` se corrigió.

## Etapa 17: destino bloqueado (AV-10, pregunta 11)

- **17.1** Parámetro `red.destino_bloqueado = esperar | nodo_vecino | no_evaluable` (por defecto `esperar`, el comportamiento anterior); argumento `--destino-bloqueado`.
  - `nodo_vecino` (**SI-14**): si el destino está bloqueado al salir o a la llegada estimada, la entrega se hace desde el nodo adyacente no bloqueado más cercano por la red, sin esperar (`Contexto.puntoDeEntrega`).
  - `no_evaluable` (**SI-15**): al llegar el pedido, si su destino está bloqueado durante toda la ventana [registro, hora límite] (`MapaVial.bloqueadoDurante`), queda "inentregable por bloqueo". No se planifica, se excluye del colapso y se cuenta en la columna `pedidos_inentregables_bloqueo`.
- **17.2** `--excluir-destinos-bloqueados si`: `GeneradorCarga` descarta los pedidos sorteados cuyo destino queda bloqueado durante toda su ventana. Solo se aplica si el archivo de ventas es SINTÉTICO; con datos oficiales se avisa y no se filtra. El archivo generado lleva `_sinDestBloq` en el nombre.
- **17.3** El análisis mantiene la tabla de causas de colapso y agrega el análisis completo repetido sin las corridas con causa "destino bloqueado" (subcarpeta `sin_destino_bloqueado/`); se reportan ambos. Con la corrida de la Etapa 14.2 se excluyen 3 de 6 corridas.
- **17.4** `DestinoBloqueadoTest` (4 pruebas: esperar, nodo_vecino, no_evaluable frente a esperar en el simulador, y exclusión en el generador). **Total: 51 pruebas, todas pasan.**

<!-- ETAPA18 -->

<!-- ETAPA19 -->

## Etapa 20: documentación para defender el código

- **20.1** `docs/guia_del_codigo.md`:
  - mapa de clases por módulo;
  - flujo de una corrida del simulador y de `replanificar` paso a paso, con los nombres de los métodos;
  - flujo de una llamada a Tabú y al AG con estado;
  - evaluación de una ruta (`Compartido.evaluarRuta` + `Contexto`);
  - traza a mano del ejemplo del profesor (`AveriaTest`): avería tipo 1 de TM01 en el minuto 36; TA01 sale del central, trasvasa en (42,14) y entrega a los **111 min** (límite 180). Sin el auto y con avería tipo 2, el colapso ocurre exactamente en el minuto 180 con la causa "a bordo de unidad averiada".
- **20.2** Diagramas en Mermaid a partir del código real:
  - `docs/diseno/diagrama_clases.md`: tres vistas (modelo y evaluación; algoritmos; simulador e integración); los atributos y métodos se verificaron contra las fuentes;
  - `docs/diseno/diagrama_secuencia_replanificacion.md`: un ciclo de `replanificar`, incluida la avería externa y la lectura de la instantánea.
- **20.3** `docs/modulos_por_integrante.md` (**propuesta**): 6 módulos con un responsable principal y un respaldo. Tabú: Arzapalo + Alcca. AG: Alvarado + Torres. Simulador, mapa, averías y experimento, repartidos entre los cuatro. Cada módulo tiene 5 preguntas del JP con respuestas que citan la clase y el método.
- **20.4** Cabeceras: todas las clases nuevas desde `v-sem07-referencia` (`Averia`, `Contexto`, `EscritorJson`, `GeneradorDatosSinteticos`, `Hito`, `Mantenimiento`, `MapaVial`, `Reloj`, `ServicioSimulacion`, `ValidadorEntradas`), más `Simulador` y `Compartido`, ya tenían comentario en español con su propósito. Ahora llevan además una línea "Supuestos: SI-xx".
  - Los supuestos nuevos **SI-14 a SI-19** se agregaron a la tabla de `docs/propuesta_cambios_IEN.md`: nodo vecino, no evaluable, penalidad de estabilidad, instancia de calibración, avería externa y una simulación por JVM.
- Sin cambios de comportamiento: **60 pruebas, todas pasan.**

## Etapa 21: integración con el visualizador (sin frontend)

- **21.1** `docs/propuesta_arquitectura_integracion.md`: tres opciones, con pros, contras y esfuerzo.
  - A: el mismo proceso difunde la instantánea por un canal bidireccional.
  - B: el mismo proceso sirve HTTP simple (consulta periódica o eventos del servidor).
  - C: un proceso por escenario y un coordinador.
  - Cubre cómo se conectan SIM_5D, COLAPSO y DIA_A_DIA, la vista en varios dispositivos (difusión de la instantánea completa), las averías registradas desde el visualizador y el cambio de velocidad en caliente (P6, P16).
  - No elige framework. Deja como criterios de decisión: escenarios simultáneos, latencia aceptable y experiencia del equipo.
- **21.2** Capa independiente de la tecnología web (commit `8926be9`):
  - `Contexto` pasa a ser por hilo (`ThreadLocal`);
  - `Reloj.esperarHasta` es interrumpible y se agrega `ahora()`;
  - `Simulador` suma `crear`/`ejecutar`, un cerrojo, `inyectarAveria`, `cambiarVelocidad` (desde la siguiente replanificación), `detener` e `instantaneaJson`;
  - nuevas clases `EscritorJson` y `ServicioSimulacion`.
  - `IntegracionTest` tiene 4 pruebas: JSON completo y bien formado, avería externa, cambio de velocidad y servicio en otro hilo con avería en caliente.
- **Pendiente:** registrar pedidos en vivo (`inyectarPedido`) para DIA_A_DIA, porque el formato no está definido. Varios escenarios en una JVM requieren quitar el estado estático de los algoritmos (SI-19).
