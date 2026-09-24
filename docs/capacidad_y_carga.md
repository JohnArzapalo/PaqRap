# Capacidad teórica de la flota, niveles de carga y tiempo de ejecución

> **Datos:** el cálculo usa el archivo **sintético del mes**, `ventas202609_SINTETICO_MES.txt`, porque no está el oficial. **DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME.** Con el archivo oficial se repite con `--archivo <oficial>` y todo se recalcula solo.

## 1. C_max propio (cálculo del IEN)

C_max = Σ_k n_k · q_k · ⌊T / t_k⌋, con t_k = d̄ / v_k + 1 h × e_k (clase `CapacidadFlota`).

| Símbolo | Valor | Cómo se obtiene |
|---|---|---|
| T | 21 h | 24 h − 3 turnos × 1 h de alimentación |
| d̄ | 66.21 km | Media de la distancia de ida y vuelta (Manhattan) entre el central y las entregas del archivo mensual |
| v_k | 40 / 25 / 12 km/h | Velocidades configuradas (las del enunciado) |
| e_k | max(1, q_k / q̄_k) | Entregas promedio por viaje; q̄_k es el tamaño medio de las entregas que caben en el tipo k |

| Tipo | n | q | q̄_k | e_k | t_k (h) | ⌊21/t_k⌋ | paquetes/día |
|---|---|---|---|---|---|---|---|
| AUTO | 10 | 24 | 8.11 | 2.96 | 4.61 | 4 | 960 |
| MOTO | 15 | 8 | 3.71 | 2.15 | 4.80 | 4 | 480 |
| BICICLETA | 12 | 4 | 2.46 | 1.62 | 7.14 | 2 | 96 |
| **Total** | | | | | | | **1 536** |

**Aproximaciones (optimistas):** un solo trayecto de ida y vuelta por viaje y viajes llenos.

## 2. Comparación con la hoja auxiliar del profesor

| | Autos | Motos | Bicicletas | Total | Velocidades |
|---|---|---|---|---|---|
| Hoja "Aux-Flota-SIN-VALOR" (REVISAR, no oficial) | 10·24·3 = 720 | 15·8·6 = 720 | 12·4·2 = 96 | **1 536** | 20 / 40 / 14 |
| Cálculo propio (archivo sintético del mes) | 10·24·4 = 960 | 15·8·4 = 480 | 12·4·2 = 96 | **1 536** | 40 / 25 / 12 |

El total coincide por casualidad; la composición no. Pasó lo mismo con el archivo sintético de 65 líneas, porque ⌊21/t_k⌋ es poco sensible a d̄ en ese rango. Por defecto se usa el propio (`carga.cmax_fuente=propio`).

## 3. Niveles de carga

| Nivel | Fracción | Paquetes/día | Pedidos en 30 días | Paquetes en 30 días | Semilla | Archivo en `datos/generados/` |
|---|---|---|---|---|---|---|
| BAJA | 30 % | 461 | 1 640 | 14 160 | 2027 | `carga_BAJA_30pct_semilla2027_de_ventas202609_SINTETICO_MES.txt` |
| MEDIA | 60 % | 922 | 3 151 | 27 971 | 2028 | `carga_MEDIA_60pct_semilla2028_de_ventas202609_SINTETICO_MES.txt` |
| ALTA | 90 % | 1 382 | 4 968 | 41 743 | 2029 | `carga_ALTA_90pct_semilla2029_de_ventas202609_SINTETICO_MES.txt` |

Bootstrap de pedidos completos del archivo mensual, conservando hora del día y posición; ahora la carga se reparte en las 24 h. Los bloqueos son los del archivo del mes (copia en `datos/generados/`), iguales para todas las corridas.

**Calibración (14.2):** en la corrida corta los colapsos van del día 3 al día 19; no todas colapsan el día 1 ni ninguna queda sin colapsar, así que **no hizo falta recalibrar**.

## 4. Tiempo real de las 30 corridas (Ta = 2000 ms, Sa = 60 min)

Cada replanificación consume casi exactamente Ta (el resto del simulador pesa poco: con Ta = 300 ms, 150 replanificaciones tomaron 47 s). El tiempo de una corrida ≈ (número de replanificaciones) × Ta, y el número de replanificaciones ≈ horas simuladas hasta el colapso.

| Estimación | Base | 30 corridas en 1 PC |
|---|---|---|
| Medida (1 réplica, Ta = 1000 ms, 6 corridas): 1 062 s en total | × 2 por Ta = 2000; × 5 réplicas | **≈ 3 h** |
| Peor caso: ninguna colapsa antes del tope de 30 días (≈ 720 replanificaciones × 2 s = 24 min por corrida) | | ≈ 12 h |

**Reparto en 3 PCs, por nivel** (no por algoritmo: con parada por tiempo se confundiría el algoritmo con la máquina). Tiempos estimados con la corrida de 1 réplica × 2 (Ta) × 5 (réplicas):

| PC | Filtro | Estimado |
|---|---|---|
| PC 1 | `--niveles BAJA` | ≈ 72 min (≈ 216 replanificaciones por corrida) |
| PC 2 | `--niveles MEDIA` | ≈ 109 min (el AG llegó a 453 replanificaciones) |
| PC 3 | `--niveles ALTA` | ≈ 29 min |

Con 1 réplica la varianza es alta; conviene balancear moviendo réplicas (`--replicas`) cuando se conozcan los tiempos reales. Todas las PCs generan exactamente los mismos archivos de carga: misma semilla, mismo archivo base y misma configuración.

```
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Experimento --modo simulacion --niveles BAJA --salida sim_pc1.csv
py analisis/unir_csv.py sim_pc1.csv sim_pc2.csv sim_pc3.csv --salida sim_unidos.csv
py analisis/analisis_experimento.py sim_unidos_SINTETICO.csv --salida analisis/salida_simulacion
```
