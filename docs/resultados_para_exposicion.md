# Resultados para la exposición (versión semana 07, etapas 5 a 7)

> **DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME.** No está el archivo oficial de ventas; todas las corridas usan `ventas202609_SINTETICO_08a12h.txt` y la carga generada a partir de él. Los resultados de `Main` usan instancias de juguete escritas en el código, no el archivo.

## 1. Demostración `Main` (2 s por algoritmo; I1 e I2 de 6 pedidos, 1 auto y 1 moto)

| Instancia | Algoritmo | Expuesto sem. 06 | Etapas 0-3 (sem. 07) | **Esta versión** | ¿Qué cambió? |
|---|---|---|---|---|---|
| I1 | Clarke & Wright | S/ 852, H=0 | S/ 708, H=0 | **S/ 708, H=0** | Etapa 1.5: fusión por extremos y orden por hora límite |
| I1 | Búsqueda Tabú | S/ 708, H=0 | S/ 672, H=0 | **S/ 672, H=0** | Etapa 1.2: lista de candidatos + Intercambio |
| I1 | AG + Split | S/ 672, H=0 | S/ 672, H=0 | **S/ 672, H=0** | — |
| I2 | Clarke & Wright | S/ 900, **H=2** | S/ 840, **H=2** | **S/ 816, H=0** | Etapa 6.3: la inserción elige ruta y posición (tardanzas → costo) |
| I2 | Búsqueda Tabú | S/ 820, **H=1** (P3 tarde) | S/ 816, H=0 | **S/ 816, H=0** | Etapa 1.3: Intercambio P3 ↔ P5 |
| I2 | AG + Split | S/ 816, H=0 | S/ 816, H=0 | **S/ 816, H=0** | — |

- En esta versión el AG incluye la búsqueda local memética. En I1 hizo unas 137 000 aplicaciones, con unas 13 000 mejoras. El resultado final es el mismo; en estas instancias pequeñas ya se llegaba al óptimo.
- Imágenes generadas: `01_inicial`, `02_tabu`, **`03_ag_generacion0`** (mejor de la generación 0 del AG en I1: S/ 708, H=0), `04_ag_final` (S/ 672), `05_ag_convergencia` (paneles H y S, I2), `06` a `08` (I2). Están en `verificacion_etapas5a7/`.
- **Para la exposición:** si se muestran los números de la semana 06, aclarar que C&W y Tabú mejoraron por las correcciones R1-R4 y la Etapa 6.3.

## 2. Experimento estático (1 réplica, Ta = 1000 ms)

| Instancia (ventana) | Algoritmo | H | % pedidos en plazo | S | Vehículos |
|---|---|---|---|---|---|
| I1 (08:00-09:00) | TABU | 0 | 100 | 5 540 | 10 |
| I1 | AG | 0 | 100 | 5 540 | 10 |
| I2 (09:30-10:30) | TABU | 0 | 100 | 4 380 | 8 |
| I2 | AG | 0 | 100 | 4 460 | 8 |
| I3 (11:00-12:00) | TABU | 0 | 100 | 2 504 | 5 |
| I3 | AG | 0 | 100 | 2 504 | 5 |

Con 1 réplica no se hacen pruebas estadísticas (el análisis lo indica: "réplicas insuficientes").

## 3. Efecto de la búsqueda local memética (AG, 5 réplicas, Ta = 1000 ms)

| Instancia | Variante | H medio | S medio (desv.) | Generaciones medias |
|---|---|---|---|---|
| I1 (1 h) | sin búsqueda local | 0.0 | 5 685.6 (27.9) | 2 453 |
| I1 (1 h) | **con búsqueda local (versión final)** | 0.0 | **5 615.2** (72.4) | 1 790 |
| I2 (1 h) | sin búsqueda local | 0.0 | 4 424.4 (41.0) | 3 124 |
| I2 (1 h) | con búsqueda local | 0.0 | 4 438.0 (53.3) | 1 678 |
| I3 (1 h) | sin / con | 0.0 / 0.0 | 2 504 / 2 504 | 5 428 / 2 350 |
| 08:00-12:00 (4 h) | sin búsqueda local | 9.6 | 12 274.0 (723.3) | 787 |
| 08:00-12:00 (4 h) | solo Reubicación + Intercambio | **11.4** (peor) | 10 185.2 (742.6) | 498 |
| 08:00-12:00 (4 h) | **+ Inserción (versión final)** | **9.2** | **11 632.8** (466.0) | 460 |

- **Primera versión (solo Reubicación e Intercambio):** en la instancia de 4 h, H empeoró de 9.6 a 11.4. Ahí H viene de entregas sin asignar (hay más carga que capacidad de un viaje), esos operadores no pueden insertarlas y la búsqueda local consumía generaciones solo para bajar el costo.
- **Versión final:** agrega Inserción cuando hay entregas sin asignar (parámetro `ag.insercion_en_busqueda_local`). H no empeora en ninguna instancia; S baja en 2, queda igual en 1 y sube 0.3 % en I2.
- **Significancia:** ninguna diferencia es significativa con n = 5 (Mann-Whitney: I1 S p = 0.20, I2 S p = 0.40; 4 h H p = 0.27, S p = 0.15).
- Se puede desactivar con `ag.busqueda_local=no` o `--busqueda-local no`.
- CSV: `verificacion_etapas5a7/busqueda_local/`.

## 4. Capacidad teórica diaria (C_max)

| | Autos | Motos | Bicicletas | Total |
|---|---|---|---|---|
| Propio (datos sintéticos, 40/25/12 km/h) | 960 | 480 | 96 | **1 536** |
| Hoja auxiliar del profesor (REVISAR; 20/40/14 km/h) | 720 | 720 | 96 | **1 536** |

El total coincide por casualidad; la composición no. Detalle en `docs/capacidad_y_carga.md`.

## 5. Corrida corta del modo simulación (1 réplica por combinación, Ta = 1000 ms, Sa = 60 min, horizonte 120 h)

| Nivel (paquetes/día) | Algoritmo | Colapso | Censurada | Costo acumulado (S/) | % pedidos en plazo | Replanificaciones | Viajes (máx. por unidad) | Pedido que colapsa |
|---|---|---|---|---|---|---|---|---|
| BAJA (461) | TABU | 12.53 h (día 1, 12:32) | no | 15 198 | 96.7 | 5 | 31 (2) | c6113-L15 |
| BAJA (461) | AG | 63.73 h (día 3, 15:44) | no | 49 364 | 99.5 | 22 | 107 (7) | c1249-L186 |
| MEDIA (922) | TABU | 63.73 h (día 3, 15:44) | no | 77 828 | 99.7 | 34 | 191 (11) | c1249-L363 |
| MEDIA (922) | AG | 15.73 h (día 1, 15:44) | no | 11 178 | 98.5 | 8 | 28 (2) | c1249-L122 |
| ALTA (1 382) | TABU | 15.73 h (día 1, 15:44) | no | 23 964 | 99.0 | 8 | 62 (3) | c1249-L177 |
| ALTA (1 382) | AG | 15.73 h (día 1, 15:44) | no | 7 326 | 95.1 | 8 | 22 (1) | c1249-L176 |

**Lectura (sin valor estadístico: 1 réplica y datos sintéticos):**
- Todas las corridas colapsan entre el día 1 y el día 3. El costo acumulado y los viajes crecen con el tiempo simulado, así que solo son comparables entre corridas que duran lo mismo.
- **Todos los colapsos vienen de pedidos con plazo de 4 h.** En el archivo base hay 3: c6113 (08:32, 12 paquetes en (2,20)), c1249 (11:44, 15 paquetes en (61,42)) y c7315 (11:55, 2 paquetes en (7,32)). Los que colapsaron son los copiados de c6113 y c1249: están lejos del central y tienen más de 8 paquetes, así que solo un auto puede llevarlos. Si en ese momento los 10 autos están en ruta, el pedido vence.
- El archivo sintético concentra la carga de cada día entre 08:00 y 12:00; por eso hasta BAJA satura la mañana.
- Con 1 réplica, el análisis no puede estimar el error del ANOVA, y la regla de decisión da "sin diferencia significativa" en los tres niveles.

Archivos: `verificacion_etapas5a7/simulacion_1rep_SINTETICO.csv`, `analisis/salida_simulacion_1rep/`, `datos/generados/`.

## 6. Tiempo estimado para las 30 corridas del IEN (Ta = 2000 ms, Sa = 60 min)

- **Peor caso:** 121 replanificaciones × 2 s ≈ 4 min por corrida, es decir **≈ 2 h** en 1 PC.
- **Con datos sintéticos** (colapso temprano): ≈ 14 min en 1 PC.
- **Reparto recomendado:** por nivel (`--niveles BAJA` / `MEDIA` / `ALTA`), ≈ 40 min por PC en el peor caso con 3 PCs. **No** repartir por algoritmo: con parada por tiempo se confundiría el algoritmo con la máquina.
