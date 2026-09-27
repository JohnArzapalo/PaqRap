# Protocolo del experimento para los datos oficiales

Pasos exactos para correr el experimento del IEN cuando lleguen los archivos oficiales del profesor. Mientras no lleguen, todo se puede ensayar con los sintéticos, cuyas salidas llevan la marca "DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME".

## 0. Requisitos (en cada PC)

- JDK 21 o superior; Maven (el `pom.xml` fija versiones y compila sin conexión con `mvn -o` si el repositorio local ya tiene las dependencias; si no, usar `mvn compile` una vez con internet).
- Python 3.12 con `pandas`, `scipy`, `matplotlib`, `statsmodels` y `lifelines` (solo en la PC que analiza).
- El **mismo commit** del repositorio en todas las PCs. Anotar el hash con `git log -1 --format=%h` y confirmar que `git status` no muestra cambios en `src/` ni en `config/`.

## 1. Copiar los archivos oficiales

| Archivo del profesor | Dónde copiarlo | Nombre sugerido |
|---|---|---|
| Pedidos del mes (`ventas2026mm`) | `datos/` | `datos/ventas2026MM.txt` (MM = mes) |
| Bloqueos (`aaaamm.bloqueadas`) | `datos/` | `datos/2026MM.bloqueadas` |
| Mantenimiento (`mant.preventivo`), si se usa | `datos/` | `datos/mant.preventivo` |

**No editar estos archivos.** Son elementos de configuración externos (`docs/control_configuracion.md`, EC-05).

## 2. Verificar el formato

```
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.ValidadorEntradas --ventas datos/ventas2026MM.txt --bloqueos datos/2026MM.bloqueadas --mantenimiento datos/mant.preventivo
```

El validador reporta:
- líneas inválidas;
- coordenadas fuera de 0..70 × 0..50 y plazos distintos de 4/8/12/18/36 (aviso);
- tramos diagonales;
- bloqueos que tocan un almacén;
- intervalos en que algún nodo libre queda inalcanzable desde el central.

Debe terminar con "RESULTADO: sin errores". Si hay errores, **consultarlos con el profesor** antes de seguir; no corregir los archivos por cuenta propia.

## 3. Revisar la configuración

En `config/parametros.properties`, confirmar con el profesor (ver `docs/preguntas_para_el_profesor.md`):
- velocidades;
- `red.destino_bloqueado` (con datos oficiales **no** se usa `--excluir-destinos-bloqueados`);
- `trasvase.minutos`, `mantenimiento.horas.*` y `sim5d.detener_en_colapso`.

El resto queda como está: Ta = 2000 ms, Sa = 60 min, tope de 30 días, penalidad de estabilidad 16.

## Diseño vigente (etapa 25)

- **Variable principal (indicación del profesor):** **% de corridas con colapso** en la simulación de 5 días (SIM_5D). Cada corrida colapsa o no dentro de las 120 h; gana el algoritmo con menor porcentaje.
- **Situaciones por réplica (diseño pareado):** en cada nivel, la réplica r es una muestra de pedidos propia (semilla de carga `semilla_base + 1000·(k+1) + r`), y **TABU y AG corren exactamente esa misma situación**, con los mismos bloqueos, flota, almacenes y parámetros. Así el porcentaje resume muchas situaciones y no la suerte de una sola.
- **Análisis:** McNemar exacta por nivel y global sobre los pares discordantes; regresión logística GEE (pares como grupos); intervalos de Clopper-Pearson. Desempates, en este orden: tiempo hasta el colapso (log-rank), costo por pedido y estabilidad, con pruebas pareadas (t pareada o Wilcoxon).
- **Escenario complementario:** «hasta el colapso» (`ESCENARIO=COLAPSO`) mide cuánto dura cada algoritmo, porque ahí toda corrida termina colapsando.

## 4. Calibrar los niveles de carga (con los datos oficiales)

Para que el porcentaje distinga algo, los niveles deben producir colapsos en **una parte** de las corridas: ni 0 % ni 100 % en ambos algoritmos. Con los datos sintéticos, 30/60/90 % de C_max dan 0 % de colapsos en 5 días y 120 % o más da 100 % (`docs/avance_sem07.md`, etapa 25). Con los oficiales hay que repetir el barrido:

```
java -Xmx8g -cp target/classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --escenario SIM_5D --acelerado si --archivo datos/ventas2026MM.txt --bloqueos datos/2026MM.bloqueadas --cargas C090=0.9,C100=1.0,C105=1.05,C110=1.1,C115=1.15,C120=1.2 --replicas 4 --hilos 6 --salida calibracion_carga.csv
py analisis/analisis_experimento.py calibracion_carga.csv --salida analisis/salida_calibracion
```

- `--cargas NOMBRE=fracción,...` define niveles a medida (fracción de C_max por día).
- Elegir tres fracciones cuyo % de colapsos (sumando ambos algoritmos) quede cerca de 20 %, 50 % y 80 %. Fijarlas en `config/parametros.properties` (`carga.nivel.BAJA`, `carga.nivel.MEDIA`, `carga.nivel.ALTA`) y **anotarlas como supuesto** (SI-nn). Consultar al profesor si los niveles del IEN (30/60/90 %) deben mantenerse.
- `--hilos` puede llegar al número de núcleos físicos de la PC (en la del equipo, 8).

Al correr en modo simulación con `--archivo datos/ventas2026MM.txt`, el programa:
1. calcula C_max con ese archivo y lo imprime junto al 1 536 de la hoja;
2. genera, para cada nivel y cada réplica, 30 días de pedidos con su semilla, en `datos/generados/`, conservando la hora del día y la posición del archivo oficial.

## 4b. Número de réplicas

```
py analisis/potencia.py calibracion_carga.csv --delta-pp 15 --hilos 6
py analisis/potencia.py --tabla
```

`potencia.py` calcula los **pares** (réplicas por nivel) que necesita la prueba de McNemar exacta. Parte de la fórmula de Connor y verifica con la potencia exacta. Depende de dos cosas:
- la diferencia de % de colapsos que se quiere detectar (delta);
- el % de pares en que colapsa solo uno de los dos (psi).

Por ejemplo, con psi = 0.30, detectar 20 puntos requiere 61 pares por nivel, y detectar 15 puntos requiere 113.

**Qué se quiere detectar decide cuántas réplicas hacen falta:**
- **Decisión global** (qué algoritmo colapsa menos, sumando los niveles): la McNemar global usa todos los pares, así que el número de pares de `potencia.py` es el **total**. Con 3 niveles, cada nivel necesita un tercio.
- **Diferencia en cada nivel por separado:** ese número de pares hace falta **en cada nivel**.

Con los sintéticos (etapa 25), la discordancia combinada fue psi = 0.56. Detectar 20 puntos requiere entonces 118 pares: **40 réplicas por nivel** para la decisión global (valor por defecto de `ejecutar_nivel.bat`), o 118 por nivel para decidir en cada uno. Con los datos oficiales, recalcular con el CSV de la calibración y fijar `REPLICAS`.

## 5. Ejecutar, repartido por nivel entre 3 PCs

Repartir **por nivel, no por algoritmo**: con parada por tiempo, una PC más rápida favorecería al algoritmo que corra en ella. En cada PC, desde la carpeta del proyecto:

```
set VENTAS=datos\ventas2026MM.txt
set BLOQUEOS=datos\2026MM.bloqueadas
set REPLICAS=40
set HILOS=6
ejecutar_pc1.bat      (en la PC 1: nivel BAJA)
ejecutar_pc2.bat      (en la PC 2: nivel MEDIA)
ejecutar_pc3.bat      (en la PC 3: nivel ALTA)
```

Cada script:
1. compila si hace falta;
2. valida los archivos de entrada y se detiene si hay errores;
3. corre SIM_5D (`--acelerado si`, `--situaciones por_replica`) con ambos algoritmos × `REPLICAS` situaciones de su nivel, `HILOS` a la vez;
4. escribe `resultados_<PC>_pcN_SIM_5D_<NIVEL>.csv`, `..._hashes.txt` (SHA-256 de ventas, bloqueos, configuración y **cada** archivo generado) y `..._certutil.txt` (hash independiente de Windows).

Para el escenario complementario, repetir con `set ESCENARIO=COLAPSO`.

**Verificación:**
- Los hashes de ventas, bloqueos, configuración y cargas generadas deben ser **iguales en las 3 PCs**. Si la configuración difiere solo por fin de línea, revisar `git config core.autocrlf`.
- **Con parada por tiempo, `HILOS` no debe superar los núcleos físicos.** Si no, cada planificador hace menos trabajo en su Ta. El orden aleatorio mezcla ambos algoritmos, así que ninguno queda en desventaja frente al otro.

**Tiempo estimado:** una corrida de SIM_5D tarda de 1 a 5 min con Ta = 2 s (menos si colapsa pronto). `potencia.py` estima el total con la duración observada.

## 6. (Opcional) Verificación reproducible

Para comprobar que un resultado no depende de la velocidad de la PC, repetir un nivel en modo reproducible:
```
set EXTRA=--calibrar-evaluaciones si
ejecutar_pc1.bat
```
Esto mide cuántas evaluaciones hace cada algoritmo en Ta en esa PC (`..._calibracion.txt`) y corre con esos topes: con la misma semilla, el resultado es idéntico en cualquier PC. **El experimento del IEN se reporta con el modo por tiempo**; este modo solo sirve para verificar.

## 7. Unir y analizar

En la PC que analiza, copiar los CSV de las 3 PCs y ejecutar:
```
py analisis/unir_csv.py resultados_*_pc1_SIM_5D_BAJA.csv resultados_*_pc2_SIM_5D_MEDIA.csv resultados_*_pc3_SIM_5D_ALTA.csv --salida resultados_unidos.csv
py analisis/analisis_experimento.py resultados_unidos.csv --salida analisis/salida_final
py analisis/potencia.py resultados_unidos.csv
```

## 8. Qué va al IEN

| Sección del IEN | Tabla o gráfico | Archivo |
|---|---|---|
| Datos de prueba | C_max propio frente a la hoja, niveles calibrados, situaciones por nivel, hashes de las entradas | Cabecera de la salida de cada PC; `*_hashes.txt` |
| **Variable principal** | % de corridas con colapso por nivel y algoritmo con IC exacto; pares discordantes; McNemar exacta por nivel y global; GEE | `colapsos.md`, `pct_colapso.png` y el texto global en `resumen.md` |
| Descriptiva | Media, desviación, mediana, mínimo y máximo de `colapso_h`, `pct_pedidos_en_plazo`, `costo_por_pedido`, `km_por_pedido`, `planificador_ms_medio` y `cambios_de_unidad` | `descriptiva.md` |
| Causas | Colapsos por causa, nivel y algoritmo | `causas_colapso.md` |
| Desempate: tiempo hasta el colapso | Kaplan-Meier por nivel y log-rank | `kaplan_meier_<NIVEL>.png`, `logrank.md` |
| Desempate: costo y estabilidad | Pruebas pareadas por nivel (t pareada o Wilcoxon) | `comparaciones.md` |
| Robustez | El mismo análisis sin los colapsos por destino bloqueado | `sin_destino_bloqueado/resumen.md` |
| Conclusión | Regla: % de colapsos → tiempo hasta el colapso → costo por pedido → estabilidad | `decision.md` |
| Potencia | Pares necesarios para McNemar exacta | salida de `potencia.py` |
| Gráficos | Diagramas de caja por variable | `caja_*.png` |
