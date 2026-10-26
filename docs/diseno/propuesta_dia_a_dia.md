# Operación día a día completa (etapa 35, Tarea 6): plan

**Estado:** plan, a la espera del visto bueno del equipo. No se ha tocado el simulador.

Ítems del formulario: A11 (avance del planificador), B13 (carga de datos), C12 a C16 (relojes), G09 (reporte al cerrar las operaciones).

## 1. Qué hay hoy

| Tema | Hoy |
|---|---|
| Inicio | La fecha y hora son las actuales (campo de solo lectura). Ritmo ×1 (tiempo real) o ×10 (demostración). Llegan los pedidos del archivo oficial desde ese instante. |
| Planificación | Por bloques de 1 h con 30 min de adelanto, como los otros escenarios (`CorridaWeb`). |
| Registro individual | `POST /api/corridas/DIA_A_DIA/pedidos`: el pedido llega **en el instante actual** y **siempre** replanifica en el instante (excepción en `Simulador.correr`). |
| Carga masiva | La valida el navegador y registra cada línea como un pedido individual **en el instante actual**: la fecha y hora de la línea se ignoran. |
| Relojes | Resuelto en la Tarea 1: con ritmo ×1 se rotulan como «hora de la operación (= hora real)». |
| Cierre | Botón «Finalizar», igual que en los otros escenarios. El reporte de cierre muestra indicadores, pero del último plan solo cuenta rutas y paradas. |

## 2. Qué falta y cómo lo haría

### 2.1 Registro individual con su fecha y hora
- El formulario muestra la **fecha y hora de registro** (la de la operación, a nivel de minuto) y la hora límite resultante, antes y después de registrar. La respuesta del servidor ya trae `registro_min` y `limite_min`.
- La tabla de pedidos registrados muestra la fecha y hora completas (hoy, solo con el formato corto).
- No se permite registrar en el pasado: el pedido llega en la hora actual de la operación.

### 2.2 Carga masiva que respeta la fecha y hora de cada línea
- Endpoint nuevo `POST /api/corridas/DIA_A_DIA/pedidos/lote` (cuerpo: el texto con el formato del profesor, `##d##h##m:x,y,cliente,cantidad,plazo`). El servidor lo valida con el mismo lector que los archivos oficiales (`LectorPedidos`), así que el formato es exactamente el de los datos del profesor.
- `##d##h##m` es el día del mes y la hora: se interpreta **en el mes en curso de la operación**.
  - Si la hora es **igual o posterior** a la actual, el pedido queda programado: llega en su hora, como los del archivo oficial (`Simulador.programarPedido`, que lo pone en `porLlegar` en su lugar).
  - Si la hora **ya pasó**, la línea se rechaza con el motivo «hora anterior a la actual de la operación» (registrar en el pasado cambiaría lo que ya ocurrió y podría vencer el plazo al instante).
- La respuesta lista las líneas aceptadas (con su id, registro y hora límite) y las rechazadas con su motivo; el navegador las muestra en la tabla actual.

### 2.3 Planificación por bloques también para los pedidos registrados
- Hoy un pedido registrado por el operador **siempre** replanifica en el instante. Propuesta: tratarlo como cualquier pedido del archivo:
  - si llega antes del corte del bloque siguiente, entra en el plan adelantado;
  - si llega después del corte, se **inserta** al empezar el bloque (`insertarLlegadasTardias`);
  - si es **urgente** (SI-29: le quedarían menos de 120 min hasta su hora límite efectiva al empezar el bloque siguiente), replanifica en el instante, como en SIM_5D.
- Consecuencia que hay que aceptar: a ritmo ×1 un bloque dura 1 h real, así que un pedido normal (plazo de 8 h o más) puede esperar hasta 1 h para tener unidad. Es la misma regla de SIM_5D (allí 1 h simulada).
- Es un cambio de una línea en `Simulador.correr` (la condición `cfg.escenario == Escenario.DIA_A_DIA`), con su prueba en `BloquesTest` (el pedido registrado espera al bloque; uno urgente replanifica al llegar).

### 2.4 Cierre de operaciones con el reporte de la última planificación estable (G09)
- En DIA_A_DIA el botón se llama **«Cerrar operaciones»** (confirmación: «se detiene la operación para todos los dispositivos y se muestra el reporte»).
- `reporteJson` pasa a traer **el plan vigente completo** (la «última planificación estable»): para cada unidad, sus paradas con tipo, pedido o almacén, cantidad y hora estimada de llegada, y el instante en que se aplicó ese plan. Además, los pedidos que quedan sin entregar.
- El modal del reporte muestra esa tabla (y el CSV la exporta). Sirve igual para G08 (fin de la SIM_5D) y G10 (colapso); se verifica en los tres escenarios (Tarea 7).

### 2.5 Relojes
- Ya hechos en la Tarea 1. Con ×10 se aclara que es un ritmo de demostración.

## 3. Pruebas
- JUnit: lote con líneas futuras (llegan en su hora), pasadas (rechazadas) y con formato inválido; pedido registrado que entra por bloque y uno urgente que replanifica; `reporteJson` con el plan vigente y sus horas.
- Navegador: registro individual y masivo, cierre de operaciones y reporte.

## 4. Archivos que cambiarían
`Simulador` (programar pedidos con su hora, condición de excepción, reporte con el plan), `CorridaWeb` y `ServidorWeb` (endpoint de lote), `LectorPedidos` (leer una línea), `web/index.html` (registro, carga masiva, botón de cierre y reporte), pruebas y documentación.
