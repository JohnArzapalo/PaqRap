# PaqRap: planificador y simulador de rutas de reparto

Proyecto del curso **1INF54 (PUCP)**, equipo **Eq5E**: Alcca, Alvarado, Torres y Arzapalo.

PaqRap planifica y simula en tiempo real el reparto de paquetes de una flota de 10 autos, 15 motos y 12 bicicletas, con tres almacenes, sobre una ciudad en retícula de 71 × 51 km con calles bloqueadas. Compara dos metaheurísticas:

- **Búsqueda Tabú** (`BusquedaTabu`)
- **Algoritmo Genético** (`AlgoritmoGenetico`)

Ambas replanifican cada 60 min, y además ante un evento (una avería o un bloqueo), partiendo del plan vigente y del estado real de cada unidad: posición, carga a bordo, averías y mantenimiento.

> ⚠️ **Los datos incluidos son SINTÉTICOS** (`datos/*SINTETICO*`), generados mientras no estén los oficiales. Toda salida producida con ellos lleva la marca «SINTETICO» y **no es válida para el informe**.

---

## Resultado principal: 5 días sin colapso

Con la configuración actual, **ninguno de los dos algoritmos colapsa en la simulación de 5 días**: 42 de 42 corridas, en 4 niveles de carga y 4 tramos del mes, con 100 % de pedidos en plazo.

| | Búsqueda Tabú | Algoritmo Genético |
|---|---|---|
| Cumple 5 días | 21/21 | 21/21 |
| Costo por pedido entregado | S/ 171.7 | **S/ 162.4** (5.4 % más barato) |
| km por pedido | 28.3 | 25.9 |
| Cambios de unidad al replanificar (5 días) | **~36** (muy estable) | ~343 |

Qué se configuró y por qué: **[docs/configuracion_5_dias.md](docs/configuracion_5_dias.md)**.

---

## Requisitos

- Java 21 y Maven 3.9 (se puede compilar sin conexión con `mvn -o`).
- Opcional, para el análisis estadístico: Python 3.12 con pandas, scipy, matplotlib, statsmodels y lifelines.

## Cómo usarlo

Todos los comandos se ejecutan en la raíz del proyecto.

**Compilar y correr las pruebas** (64 pruebas JUnit):

```bash
mvn test
```

**Demostración con instancias pequeñas** (genera imágenes PNG de las rutas):

```bash
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Main
```

**Simulación de 5 días** (acelerada, ambos algoritmos, 3 réplicas):

```bash
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --escenario SIM_5D --acelerado si --archivo datos/ventas202609_SINTETICO_MES.txt --bloqueos datos/202609_SINTETICO.bloqueadas --niveles ARCHIVO --replicas 3 --salida cinco_dias_SINTETICO.csv
```

Opciones de esta simulación:
- `--niveles BAJA,MEDIA,ALTA,ARCHIVO`: 30 %, 60 % y 90 % de la capacidad de la flota, o el archivo de ventas tal cual.
- `--dia-inicio N`: empezar la simulación de 5 días el día N del mes.
- Sin `--acelerado si`, los 5 días se muestran en unos 30 min reales.

**Experimento** (en 3 PC, una por nivel de carga): `scripts\ejecutar_pc1.bat`, `scripts\ejecutar_pc2.bat` y `scripts\ejecutar_pc3.bat`. Los CSV quedan en `salidas/`. El paso a paso está en [docs/protocolo_experimento.md](docs/protocolo_experimento.md).

**Validar archivos de entrada** antes de correr:

```bash
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.ValidadorEntradas --ventas datos/ventas202609_SINTETICO_MES.txt --bloqueos datos/202609_SINTETICO.bloqueadas
```

Todos los parámetros (velocidades, Sa, Ta, holgura, reglas de bloqueo, etc.) están comentados en [config/parametros.properties](config/parametros.properties).

---

## Guía de lectura

| Si quieres entender… | Lee |
|---|---|
| Cómo se cumple el requisito de 5 días y cuánto cuesta cada algoritmo | [docs/configuracion_5_dias.md](docs/configuracion_5_dias.md) |
| El código: clases, flujo del simulador, cómo planifican Tabú y el AG, cómo se evalúa una ruta, ejemplo trazado a mano | [docs/guia_del_codigo.md](docs/guia_del_codigo.md) |
| Diagramas de clases y de secuencia (Mermaid, se ven en GitHub) | [docs/diseno/](docs/diseno/) |
| Qué módulo defiende cada integrante y preguntas probables del JP | [docs/modulos_por_integrante.md](docs/modulos_por_integrante.md) |
| El diseño del simulador (eventos, colapso, escenarios) | [docs/diseno_simulador.md](docs/diseno_simulador.md) |
| Cómo correr el experimento en 3 PC | [docs/protocolo_experimento.md](docs/protocolo_experimento.md) |
| Capacidad de la flota (C_max) y niveles de carga | [docs/capacidad_y_carga.md](docs/capacidad_y_carga.md) |
| Cómo se generaron los datos sintéticos | [docs/datos_sinteticos.md](docs/datos_sinteticos.md) |
| Supuestos (SI-01 a SI-22) y cambios propuestos al IEN | [docs/propuesta_cambios_IEN.md](docs/propuesta_cambios_IEN.md) |
| Preguntas pendientes para el profesor | [docs/preguntas_para_el_profesor.md](docs/preguntas_para_el_profesor.md) |
| Propuesta de integración con el visualizador (sin elegir framework) | [docs/propuesta_arquitectura_integracion.md](docs/propuesta_arquitectura_integracion.md) |
| Historial de avances | [docs/avance_sem07.md](docs/avance_sem07.md), [docs/avance_sem08.md](docs/avance_sem08.md) |
| Versiones y etiquetas del repositorio | [docs/control_configuracion.md](docs/control_configuracion.md) |

## Estructura

```
src/main/java/pe/edu/pucp/gamesoft/paqrap/   código fuente (Java 21)
src/test/java/...                            pruebas JUnit 5
config/parametros.properties                 parámetros del modelo
datos/                                       ventas y bloqueos (SINTÉTICOS)
juego_de_datos/                              ventas, bloqueos y mantenimiento OFICIALES del profesor
analisis/                                    análisis estadístico y de potencia (Python)
docs/                                        documentación
Resultados/                                  resultados oficiales del experimento (ver LEEME.md)
scripts/                                     experimento por PC (ejecutar_pc1/2/3.bat)
baseline/                                    salida de referencia congelada de Main
salidas/                                     salidas locales: figuras de Main, CSV de scripts/ (no se versiona)
exposicion/                                  diapositivas de la exposición (no se versiona)
```

## Versiones

- `main`: versión actual.
- Rama `historial` con las etiquetas `v-sem06-expuesto` (versión expuesta en la semana 06) y `v-sem07-referencia` (referencia de la semana 07).
