# Control de configuración de PaqRap (insumo para el Plan de Gestión de la Configuración, IEEE 828)

> Este documento es un **insumo**, no el plan. Registra qué se controla, dónde está y cómo se versiona, para que el equipo redacte su Plan de Gestión de la Configuración (IEEE 828).

## 1. Repositorio

- Repositorio git local en `DP1/PaqRap` (creado en la Etapa 16). Aún **no tiene remoto**; el equipo decide dónde publicarlo (GitHub, GitLab de la PUCP, etc.).
- Ramas:
  - `main`: línea de desarrollo; un commit por etapa, con la etapa citada en el mensaje.
  - `historial`: reconstrucción de las versiones anteriores al repositorio, a partir de las copias en disco (ver sección 4).

## 2. Elementos de configuración (EC)

| ID | Elemento | Ubicación | Tipo | Quién lo cambia | Cómo se identifica la versión |
|---|---|---|---|---|---|
| EC-01 | Código fuente del planificador y del simulador | `src/main/java/pe/edu/pucp/gamesoft/paqrap/` | Código | Integrantes, por módulo (ver `docs/modulos_por_integrante.md`) | Commit y etiqueta git |
| EC-02 | Pruebas unitarias (JUnit 5) | `src/test/java/...` | Código | Autor del módulo probado | Commit git |
| EC-03 | Construcción | `pom.xml` (versiones de plugins fijadas; compila sin conexión con `mvn -o`) | Configuración | Responsable de configuración | Commit git |
| EC-04 | Parámetros del planificador y del simulador | `config/parametros.properties` | Configuración | Acordado en equipo: cambia los resultados | Commit git. Cada corrida del experimento registra en su CSV los parámetros clave (Ta, Sa, parada, parciales, etc.) |
| EC-05 | Datos de entrada **oficiales** (`ventas2026mm`, `aaaamm.bloqueadas`, `mant.preventivo`) | `datos/` (aún no llegan) | Datos externos | Nadie: se reciben del profesor y no se editan | Hash SHA-256 registrado en cada ejecución (Etapa 19) |
| EC-06 | Datos de entrada **sintéticos** | `datos/*SINTETICO*` | Datos generados | Solo el generador (`GeneradorDatosSinteticos`, con semilla fija) | Commit git + semilla del generador |
| EC-07 | Conjuntos generados por nivel de carga | `datos/generados/` (**no versionado**: se regenera igual con la misma semilla) | Datos derivados | `GeneradorCarga` | Semilla y archivo base en el nombre; hash en la ejecución |
| EC-08 | Scripts de análisis | `analisis/*.py` | Código | Responsable del experimento | Commit git |
| EC-09 | Documentación técnica y propuestas | `docs/` | Documentos | Integrantes | Commit git |
| EC-10 | Documentos oficiales del equipo (IEN, ISA, DP, DS 22-29, estándares 61-69) | **Fuera de este repositorio** | Documentos | Equipo, con su control de cambios | Según el plan del equipo. Aquí solo hay propuestas (`docs/propuesta_*.md`) |

**No son elementos de configuración** (se regeneran, están en `.gitignore`): `target/`, `verificacion_*/`, `analisis/salida*/`, `datos/generados/`, imágenes PNG en la raíz, CSV de resultados en la raíz.

## 3. Convención de versiones y etiquetas

| Etiqueta | Significado |
|---|---|
| `v-semNN-<hito>` | Versión asociada a un hito del cronograma. Ejemplos: `v-sem06-expuesto`, `v-sem07-referencia`. |
| `v-semNN-etapaMM` (opcional) | Versión al cierre de una etapa de trabajo. |

- Una etiqueta se crea **anotada** (`git tag -a`) y no se mueve.
- **Mensajes de commit:** `Etapa NN: <resumen>`, con la etapa citada; un commit por etapa.
- **Resultados de un experimento:** para que se puedan reproducir, se anotan juntos el commit del código, el hash de los datos de entrada y los parámetros (en el CSV).

## 4. Versiones anteriores al repositorio (dónde está cada copia)

| Versión | Copia en disco (no modificar) | En git |
|---|---|---|
| Expuesta en la semana 06 | `DP1/PaqRap_sem06_expuesto/` | Etiqueta `v-sem06-expuesto` (rama `historial`, commit `63af5f8`) |
| Referencia de la semana 07 (Main 708/672/672 y 816/816/816) | `DP1/PaqRap_version_referencia_sem07/` (con `LEEME.md`) | Etiqueta `v-sem07-referencia` (rama `historial`, commit `8ac8d14`) |
| Archivos de clases antiguas (Modelo, Estructuracompartida, Busquedatabu) | `PaqRap/obsoleto_sem06/` | En `v-sem06-expuesto` |

**Notas:**
- En `v-sem07-referencia`, la clase `BusquedaTabu` quedó guardada en el archivo `Busquedatabu.java`. Windows no distingue mayúsculas y git conservó el nombre de la versión anterior. La clase no es pública, así que compila igual. En `main` ya se llama `BusquedaTabu.java`.
- Las copias históricas excluyen lo mismo que el `.gitignore`: compilación, salidas de verificación y copias obsoletas.

## 5. Cómo reproducir una versión

```
git checkout v-sem07-referencia
"C:\Program Files\Maven\apache-maven-3.9.16\bin\mvn" -o test
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Main
git checkout main
```
