# Resultados para la exposición (semana 07, etapas 8 a 15)

> **DATOS SINTÉTICOS - NO VÁLIDOS PARA EL INFORME.** No están los archivos oficiales. La simulación usa `ventas202609_SINTETICO_MES.txt` (pedidos del mes) y `202609_SINTETICO.bloqueadas` (bloqueos), generados con semilla (ver `docs/datos_sinteticos.md`). `Main` usa instancias de juguete escritas en el código.

## 1. Demostración `Main` (versión de referencia congelada)

Valores estables para el equipo: carpeta `DP1/PaqRap_version_referencia_sem07` (`LEEME.md`). 2 s por algoritmo; 6 pedidos, 1 auto y 1 moto.

| Instancia | Algoritmo | Expuesto sem. 06 | **Referencia sem. 07 (= versión actual)** | ¿Qué cambió desde la semana 06? |
|---|---|---|---|---|
| I1 | Clarke & Wright | S/ 852, H=0 | **S/ 708, H=0** | Fusión por extremos y orden por hora límite |
| I1 | Búsqueda Tabú | S/ 708, H=0 | **S/ 672, H=0** | Lista de candidatos + Intercambio |
| I1 | AG + Split | S/ 672, H=0 | **S/ 672, H=0** | — |
| I2 | Clarke & Wright | S/ 900, H=2 | **S/ 816, H=0** | Inserción por ruta y posición |
| I2 | Búsqueda Tabú | S/ 820, H=1 | **S/ 816, H=0** | Intercambio P3 ↔ P5 |
| I2 | AG + Split | S/ 816, H=0 | **S/ 816, H=0** | — |

**Las etapas 8 a 15 no cambiaron `Main`.** El evaluador general reproduce exactamente el modelo anterior cuando el contexto es el de por defecto: salida del central en la hora 0, Manhattan, sin almacenes intermedios. La única diferencia que se ve de una corrida a otra es la mejor solución de la **generación 0 del AG en I2**. No es un cambio de código: la semilla del AG se fija una vez en `Main`, y la corrida de I1, que depende del tiempo, consume una cantidad variable de números aleatorios. La imagen `03_ag_generacion0.png` es de I1 y da S/ 708.

## 2. Experimento estático (preliminar; 1 réplica, Ta = 1000 ms)

| Instancia | TABU: H / S | AG: H / S |
|---|---|---|
| I1 (08:00-09:00) | 0 / 5 540 | 0 / 5 648 |
| I2 (09:30-10:30) | 0 / 4 380 | 0 / 4 460 |
| I3 (11:00-12:00) | 0 / 2 504 | 0 / 2 504 |

## 3. Simulación: experimento de la Etapa 14.2

Configuración: 1 réplica por combinación, Ta = 1000 ms, Sa = 60 min, escenario EXPERIMENTO. Bloqueos del mes, almacenes intermedios con stock, replanificación con estado, entregas parciales `urgentes`, alimentación; sin averías. C_max = 1 536 paquetes/día.

| Nivel (paquetes/día) | Algoritmo | Colapso | Causa | Costo acumulado (S/) | % pedidos en plazo | Replanificaciones | Cambios de unidad |
|---|---|---|---|---|---|---|---|
| BAJA (461) | TABU | 189.4 h (día 8, 21:25) | llegada tardía (a bordo) | 69 684 | 99.76 | 186 | 124 |
| BAJA (461) | AG | 219.8 h (día 10, 03:50) | **destino bloqueado** | 73 634 | 99.80 | 216 | 854 |
| MEDIA (922) | TABU | 150.3 h (día 7, 06:20) | **destino bloqueado** | 104 485 | 99.82 | 150 | 34 |
| MEDIA (922) | AG | 219.9 h (día 10, 03:56) | **destino bloqueado** | 154 666 | 99.89 | 219 | 2 865 |
| ALTA (1 382) | TABU | 104.2 h (día 5, 08:11) | llegada tardía (planificada) | 107 277 | 99.83 | 104 | 147 |
| ALTA (1 382) | AG | 100.8 h (día 5, 04:49) | llegada tardía (planificada) | 108 688 | 99.82 | 100 | 1 096 |

**Lectura (sin valor estadístico: 1 réplica, datos sintéticos):**
- El tiempo hasta el colapso **baja con la carga**, como se esperaba. No hubo que recalibrar: no todas colapsan el día 1 ni ninguna queda sin colapsar.
- **3 de 6 colapsos son por "destino bloqueado":** el cliente está en un nodo bloqueado desde antes de su pedido y hasta después de su plazo. Con la regla vigente en la semana 07 (`esperar` el desbloqueo), **ningún algoritmo puede evitarlo**. *(Desde la etapa 22 el valor por defecto es `no_evaluable`, que excluye esos pedidos del colapso mientras el profesor responde la pregunta 11.)* Esos colapsos los deciden los datos. Hay que aclarar la regla con el profesor (pregunta 11) o evitar que el generador ubique pedidos así.
- **Variabilidad alta:** la corrida anterior, con el mismo código salvo la etiqueta de causa, dio para AG en MEDIA 453.6 h en vez de 219.9 h, y para TABU en BAJA 219.8 h en vez de 189.4 h. Es el efecto de la parada por tiempo real y de colapsos que dependen de un solo pedido. Hacen falta las 5 réplicas, o el modo por evaluaciones, para comparar.
- **Estabilidad:** el AG cambia muchas más entregas de unidad entre ciclos que Tabú (854 a 2 865 frente a 34 a 147). Tabú parte del plan vigente; el AG lo siembra, pero su decodificador reasigna.
- No hubo bloqueos encontrados en ruta: el planificador los anticipa con la ventana del tramo.

## 4. Entregas parciales flexibles (10.3; Tabú, 1 réplica, Ta = 300 ms)

| Nivel | Colapso sin división (`ninguna`) | Colapso con división (`urgentes`) |
|---|---|---|
| BAJA | 219.8 h | 219.8 h (mismo colapso: destino bloqueado) |
| MEDIA | 123.2 h | **150.3 h** (+27 h) |
| ALTA | 100.5 h | **104.3 h** (+3.8 h) |

Dividir las urgentes grandes retrasó el colapso en MEDIA y ALTA. Con 1 réplica es solo indicativo. En una prueba controlada (`SimuladorTest`), un pedido urgente de 20 paquetes sin autos colapsa por capacidad sin división y se entrega a tiempo con ella.

## 5. Escenario SIM_5D (acelerado; MEDIA, Tabú, Ta = 2000 ms)

**No colapsó en los 5 días** (censurada en 120 h); 100 % de pedidos en plazo, 119 replanificaciones, 4 min reales. Registro de eventos: `verificacion_etapas8a15/eventos_sim5d_MEDIA_TABU_r1_SINTETICO.csv` (2 280 líneas: 499 entregas, 379 cargas, 119 replanificaciones…). En modo no acelerado, los 5 días se muestran en ~30 min (`sim5d.minutos_reales`).

## 6. Tiempo estimado de las 30 corridas del IEN (Ta = 2000 ms)

≈ 3 h en una PC, según lo medido; hasta ~12 h en el peor caso, si nada colapsa antes de los 30 días. Por nivel en 3 PCs: BAJA ≈ 72 min, MEDIA ≈ 109 min, ALTA ≈ 29 min (detalle en `docs/capacidad_y_carga.md`).
