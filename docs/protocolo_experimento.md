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

El resto queda como está: Ta = 2000 ms, Sa = 60 min, 5 réplicas, tope de 30 días, penalidad de estabilidad 16.

## 4. Recalcular C_max y los niveles (automático)

Al correr en modo simulación con `--archivo datos/ventas2026MM.txt`, el programa:
1. calcula C_max con ese archivo y lo imprime junto al 1 536 de la hoja;
2. genera, para cada nivel (30/60/90 % de C_max por día), 30 días de pedidos con semilla fija, en `datos/generados/`, conservando la hora del día y la posición del archivo oficial.

Para verlo sin correr todo: `--niveles BAJA --replicas 1 --ta 200 --algoritmos TABU` y leer la cabecera de la salida.

## 5. Ejecutar, repartido por nivel entre 3 PCs

Repartir **por nivel, no por algoritmo**: con parada por tiempo, una PC más rápida favorecería al algoritmo que corra en ella. En cada PC, desde la carpeta del proyecto:

```
set VENTAS=datos\ventas2026MM.txt
set BLOQUEOS=datos\2026MM.bloqueadas
ejecutar_pc1.bat      (en la PC 1: nivel BAJA)
ejecutar_pc2.bat      (en la PC 2: nivel MEDIA)
ejecutar_pc3.bat      (en la PC 3: nivel ALTA)
```

Cada script:
1. compila si hace falta;
2. valida los archivos de entrada y se detiene si hay errores;
3. corre ambos algoritmos × 5 réplicas de su nivel;
4. escribe `resultados_<PC>_pcN_<NIVEL>.csv`, `..._hashes.txt` (SHA-256 de ventas, bloqueos, configuración y archivo generado) y `..._certutil.txt` (hash independiente de Windows).

**Verificación:** los hashes de ventas, bloqueos y configuración deben ser **iguales en las 3 PCs**. Si la configuración difiere solo por fin de línea, revisar `git config core.autocrlf`.

**Tiempo estimado:** ver `docs/capacidad_y_carga.md` §4 y la tabla de réplicas necesarias de `docs/avance_sem08.md` (Etapa 19.3).

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
py analisis/unir_csv.py resultados_*_pc1_BAJA.csv resultados_*_pc2_MEDIA.csv resultados_*_pc3_ALTA.csv --salida resultados_unidos.csv
py analisis/analisis_experimento.py resultados_unidos.csv --salida analisis/salida_final
py analisis/potencia.py resultados_unidos.csv --delta 24
```

## 8. Qué va al IEN

| Sección del IEN | Tabla o gráfico | Archivo |
|---|---|---|
| Datos de prueba | C_max propio frente a la hoja, niveles, pedidos por nivel, hashes de las entradas | Cabecera de la salida de cada PC; `*_hashes.txt` |
| Descriptiva | Media, desviación, mediana, mínimo y máximo de `colapso_h`, `pct_pedidos_en_plazo`, `costo_acumulado`, `planificador_ms_medio` y `cambios_de_unidad` por nivel y algoritmo | `analisis/salida_final/descriptiva.md` |
| Causas | Colapsos por causa, nivel y algoritmo | `causas_colapso.md` |
| Inferencia (sin censuradas) | ANOVA algoritmo × carga (o ART) con la verificación de supuestos | `anova_colapso_h.md` y el texto de supuestos en `resumen.md` |
| Inferencia (con censuradas) | Kaplan-Meier por nivel y log-rank | `kaplan_meier_<NIVEL>.png`, `logrank.md` |
| Robustez | El mismo análisis sin los colapsos por destino bloqueado | `sin_destino_bloqueado/resumen.md` |
| Conclusión | Regla de decisión: tiempo hasta el colapso → % en plazo → costo | `decision.md` |
| Potencia | Réplicas necesarias para detectar 24 h | salida de `potencia.py` |
| Gráficos | Diagramas de caja por variable | `caja_*.png` |
