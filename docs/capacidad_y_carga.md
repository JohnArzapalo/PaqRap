# Capacidad teórica de la flota, niveles de carga y tiempo de ejecución

> **Datos:** el cálculo usa el archivo **sintético** `ventas202609_SINTETICO_08a12h.txt` porque no está el archivo oficial en `datos/`. **DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME.** Al recibir el archivo oficial, se repite con `Experimento --modo simulacion --archivo <oficial>` y los valores se recalculan solos.

## 1. C_max propio (cálculo del IEN)

Implementado en `CapacidadFlota.calcular`:

C_max = Σ_k n_k · q_k · ⌊T / t_k⌋, con t_k = d̄ / v_k + 1 h × e_k

| Símbolo | Valor usado | Cómo se obtiene |
|---|---|---|
| T | 21 h | 24 h − 3 turnos × 1 h de alimentación (`carga.horas_efectivas`) |
| d̄ | 62.93 km | Media de la distancia Manhattan de ida y vuelta entre el central (27,14) y cada entrega del archivo |
| v_k | 40 / 25 / 12 km/h | Velocidades configuradas (las del enunciado) |
| e_k | max(1, q_k / q̄_k) | Entregas promedio por viaje. q̄_k es el tamaño medio de las entregas del archivo que **caben** en el tipo k (un viaje lleno lleva q_k paquetes en entregas de tamaño q̄_k) |

| Tipo | n | q | v (km/h) | q̄_k | e_k | t_k (h) | ⌊21 / t_k⌋ | paquetes/día |
|---|---|---|---|---|---|---|---|---|
| AUTO | 10 | 24 | 40 | 7.42 | 3.24 | 4.81 | 4 | 960 |
| MOTO | 15 | 8 | 25 | 3.31 | 2.42 | 4.93 | 4 | 480 |
| BICICLETA | 12 | 4 | 12 | 2.13 | 1.88 | 7.12 | 2 | 96 |
| **Total** | | | | | | | | **1 536** |

**Aproximaciones (optimistas):** se cuenta un solo trayecto de ida y vuelta por viaje, sin sumar la distancia entre clientes de un mismo viaje, y se asume que cada viaje sale lleno.

## 2. Comparación con la hoja auxiliar del profesor

| | Autos | Motos | Bicicletas | Total | Velocidades |
|---|---|---|---|---|---|
| Hoja "Aux-Flota-SIN-VALOR" (REVISAR, no oficial) | 10·24·3 = 720 | 15·8·6 = 720 | 12·4·2 = 96 | **1 536** | 20 / 40 / 14 |
| Cálculo propio (datos sintéticos) | 10·24·4 = 960 | 15·8·4 = 480 | 12·4·2 = 96 | **1 536** | 40 / 25 / 12 |

**El total coincide por casualidad; la composición no.** La hoja da más viajes a las motos (es la unidad más rápida con sus velocidades) y el cálculo propio más a los autos. Con el archivo oficial, o si el profesor confirma otras velocidades, el C_max propio cambiará.

Por defecto se usa el propio (`carga.cmax_fuente=propio`); con `carga.cmax_fuente=hoja` se usa 1 536 fijo.

## 3. Niveles de carga y generación de pedidos

| Nivel | Fracción de C_max (configurable) | Paquetes/día | Pedidos en 5 días | Entregas | Paquetes en 5 días | Semilla | Archivo |
|---|---|---|---|---|---|---|---|
| BAJA | 30 % | 461 | 311 | 318 | 2 364 | 2027 | `datos/generados/carga_BAJA_30pct_semilla2027_de_ventas202609_SINTETICO_08a12h.txt` |
| MEDIA | 60 % | 922 | 604 | 621 | 4 632 | 2028 | `datos/generados/carga_MEDIA_60pct_semilla2028_de_ventas202609_SINTETICO_08a12h.txt` |
| ALTA | 90 % | 1 382 | 896 | 923 | 6 942 | 2029 | `datos/generados/carga_ALTA_90pct_semilla2029_de_ventas202609_SINTETICO_08a12h.txt` |

Método (`GeneradorCarga`): para cada día se sortean pedidos completos del archivo base con reposición (bootstrap) hasta alcanzar el objetivo de paquetes del día. Cada pedido conserva posición, cliente, cantidad, plazo y **hora del día**, así que se mantienen la distribución espacial y temporal del archivo y su correlación. La semilla es fija por nivel, de modo que ambos algoritmos y todas las réplicas reciben los mismos pedidos.

**⚠ Efecto del archivo sintético.** El archivo base solo tiene pedidos entre las **08:00 y las 12:00**, así que toda la carga de cada día llega en 4 horas. Con BAJA (461 paquetes/día) llegan ~115 paquetes por hora, cuando la flota procesa unos 64 por hora (1 536 / 24). En la práctica, BAJA ya satura la mañana. Con el archivo oficial, repartido en el día, los niveles tendrán el significado esperado.

## 4. Justificación de Ta = 2000 ms y tiempo total

Parámetros por defecto: Sa = 60 min simulados, horizonte = 7 200 min, así que hay como máximo 121 replanificaciones por corrida. El planificador solo se llama si hay pedidos pendientes y unidades en el central.

| Escenario | Tiempo por corrida | 30 corridas (IEN) |
|---|---|---|
| Peor caso (llamada al planificador en los 121 ciclos, sin colapso) | 121 × 2 s ≈ 4.0 min | ≈ **2.0 h** en 1 PC |
| Observado con datos sintéticos (Ta = 1000 ms, 6 corridas: 85 s en total; colapso el día 1-3) | ≈ 14 s (x2 con Ta = 2000) | ≈ **14 min** en 1 PC |

¿Por qué 2000 ms? En el experimento estático, con Ta = 1000 ms, la mediana de `tiempo_mejor_ms` estuvo entre 300 y 700 ms en instancias de ~20 entregas. En la simulación, cada replanificación maneja del orden de decenas de pendientes, así que 2 s da margen a ambos algoritmos sin pasar de ~2 h en el peor caso para las 30 corridas.

## 5. Cómo repartir las 30 corridas en varias PCs

**Recomendación: repartir por NIVEL, no por algoritmo.** Con parada por tiempo, una PC más rápida hace más iteraciones. Si TABU corre en una PC y AG en otra, el efecto del algoritmo se confunde con el de la máquina. Si cada PC corre ambos algoritmos de su nivel, la comparación dentro de cada nivel es justa.

| PCs | PC 1 | PC 2 | PC 3 | Peor caso por PC |
|---|---|---|---|---|
| 3 | `--niveles BAJA` | `--niveles MEDIA` | `--niveles ALTA` | 10 corridas ≈ 40 min |
| 2 | `--niveles BAJA,MEDIA` | `--niveles ALTA` | — | 20 corridas ≈ 80 min |

Ejemplo (PC 1):
```
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --archivo datos/<oficial>.txt --niveles BAJA --salida sim_pc1.csv
```
Todas las PCs generan el mismo archivo de carga para cada nivel (misma semilla, mismo archivo base y misma configuración); conviene verificarlo comparando los archivos de `datos/generados/`. Después:
```
py analisis/unir_csv.py sim_pc1.csv sim_pc2.csv sim_pc3.csv --salida sim_unidos.csv
py analisis/analisis_experimento.py sim_unidos.csv --salida analisis/salida_simulacion
```
Si se necesita reproducibilidad exacta entre PCs, use `--max-evaluaciones N` (el tope es por llamada al planificador).
