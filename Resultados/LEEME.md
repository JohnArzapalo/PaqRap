# Resultados del experimento numérico (datos oficiales)

Todo lo de esta carpeta se hizo con los **datos oficiales del profesor** (`juego_de_datos/`), en la simulación de 5 días (SIM_5D), con ventanas reales (SI-26) y Ta = 2 s. No hay datos sintéticos.

| Carpeta | Qué contiene |
|---|---|
| `calibracion_202609/` | Calibración de carga con septiembre de 2026 (etapa 28): niveles C090 a C115 y ARCHIVO, 4 réplicas. Sirvió para elegir los meses de cada nivel. |
| `corrida1_semilla1000/` | Experimento completo, corrida 1: semillas de los algoritmos 1001-1040. |
| `corrida2_semilla2000/` | Corrida 2: mismos tramos, semillas 2001-2040. |
| `corrida3_semilla3000/` | Corrida 3: mismos tramos, semillas 3001-3040. |
| `combinado/` | Las tres corridas unidas (618 filas, 309 pares) y su análisis. |

En cada corrida:
- `experimento_oficial_SIM_5D.csv`: una fila por corrida (algoritmo × tramo).
- `..._hashes.txt`: SHA-256 de cada archivo de entrada, para comprobar que todas las PCs usaron los mismos datos.
- `consola.log`: salida del programa.
- `analisis/`: tablas (`.md` y `.csv`), gráficos y `resumen.md`.
- Solo en la corrida 1: `resumen_corrida1.html`, resumen de una página.

## Veredicto (etapa 29)

| Corrida (semilla) | % colapso Tabú | % colapso AG | p |
|---|---|---|---|
| 1 (1000) | 46.6 | 49.5 | 0.65 |
| 2 (2000) | 49.5 | 44.7 | 0.40 |
| 3 (3000) | 42.7 | 50.5 | 0.15 |
| **Combinadas** | **46.3** | **48.2** | **0.46** (GEE por tramo) |

- En el % de colapsos, los dos algoritmos son **equivalentes**. Ninguna corrida muestra diferencia y la dirección cambia de una corrida a otra.
- **Desempates, iguales en las tres corridas:** el AG es de 1.4 a 4.5 % más barato por pedido en BAJA y MEDIA, y la Búsqueda Tabú hace de 4 a 10 veces menos cambios de unidad al replanificar.
- **Algoritmo elegido: Búsqueda Tabú** (etapa 30, SI-27). Como empatan en colapsos, el equipo puso la estabilidad antes que el costo en el desempate; con esa regla, Tabú gana en los tres niveles y en las tres corridas (`combinado/analisis/decision.md`).

## Por qué hay tres corridas

- Los tramos (situaciones) son los mismos en las tres corridas: los fija `carga.semilla_base`. Solo cambia el azar interno de los algoritmos (`--semilla-base`).
- En cerca del 18 % de los tramos colapsa uno de los dos algoritmos y el otro no, así que ese azar pesa.
- Tres corridas muestran si el veredicto se repite y permiten detectar diferencias de unos 10 puntos en el % de colapsos, frente a los 15 de una sola.

**Cómo combinarlas:** unir los CSV con `analisis/unir_csv.py` y analizar el archivo unido. La tabla «0b» muestra cada corrida por separado. La prueba combinada válida es la **GEE agrupada por tramo**, porque la McNemar global cuenta el mismo tramo varias veces.

```
py analisis/unir_csv.py Resultados/corrida*_semilla*/experimento_oficial_SIM_5D.csv --salida Resultados/combinado/experimento_oficial_combinado.csv
py analisis/analisis_experimento.py Resultados/combinado/experimento_oficial_combinado.csv --salida Resultados/combinado/analisis
```

## Cómo reproducir una corrida

```
java -Xmx10g -cp target/classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --escenario SIM_5D --acelerado si --situaciones ventanas --carpeta-ventas juego_de_datos/ventas.v20260909-20260928T011657Z-1-001/ventas.v20260909 --carpeta-bloqueos juego_de_datos/bloqueos.v20260909-20260928T011612Z-1-001/bloqueos.v20260909 --mantenimiento juego_de_datos/mant.preventivo.09.10.txt --meses BAJA=202609-202610,MEDIA=202611-202612,ALTA=202701-202702 --paso-ventana 1 --replicas 40 --hilos 7 --semilla-base 3000 --salida Resultados/corrida3_semilla3000/experimento_oficial_SIM_5D.csv
```

Detalle de cada etapa en `docs/avance_sem07.md`.
