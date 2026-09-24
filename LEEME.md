# PaqRap: versión de referencia, semana 07

**Copia congelada. No modificar.** Es el estado del código al cerrar las Etapas 5 a 7 (simulador mínimo, búsqueda local memética, operadores 2-opt y cross-exchange, inserción de C&W por posición). El desarrollo continúa en la carpeta `PaqRap`.

## Tabla de Main vigente (valores de referencia estables para el equipo)

Instancias de juguete de `Main` (6 pedidos, 1 auto y 1 moto); 2 s por algoritmo. Salida completa en `verificacion_etapas5a7/main.txt`.

| Instancia | Clarke & Wright | Búsqueda Tabú | AG + Split (con búsqueda local) | Mejor de la generación 0 del AG |
|---|---|---|---|---|
| I1 (plazos holgados) | S/ 708, H=0 | S/ 672, H=0 | S/ 672, H=0 | S/ 708, H=0 |
| I2 (P2 y P3 en 1 h, P4 en 3 h) | S/ 816, H=0 | S/ 816, H=0 | S/ 816, H=0 | S/ 816, H=0 |

H = entregas sin asignar + entregas fuera de plazo; S = costo en soles.

### Historial
| Versión | I1 C&W / Tabú / AG | I2 C&W / Tabú / AG |
|---|---|---|
| Expuesta en la semana 06 | 852 / 708 / 672 | 900 (H=2) / 820 (H=1) / 816 |
| Semana 07, etapas 0-3 | 708 / 672 / 672 | 840 (H=2) / 816 / 816 |
| **Semana 07, referencia (esta copia)** | **708 / 672 / 672** | **816 / 816 / 816** (todas H=0) |

## Cómo reproducir
```
"C:\Program Files\Maven\apache-maven-3.9.16\bin\mvn" -o test
java -cp target/classes pe.edu.pucp.gamesoft.paqrap.Main
```
Estado de las pruebas en esta copia: 28 pruebas JUnit, todas pasan.
