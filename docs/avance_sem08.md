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
