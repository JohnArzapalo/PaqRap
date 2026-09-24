# Avance semana 08: etapas 16 a 21

Cada etapa se cierra con: compilación, todas las pruebas JUnit, un resumen aquí y un commit que cita la etapa.

## Etapa 16: control de versiones

- Repositorio git en `PaqRap` con `.gitignore`: compilación, `__pycache__`, `verificacion_*`, `analisis/salida*`, `datos/generados`, PNG y CSV de la raíz, IDE y `obsoleto_sem06`.
- **Historial reconstruido** en la rama `historial`, a partir de las copias en disco:
  - `v-sem06-expuesto` (desde `PaqRap_sem06_expuesto`);
  - `v-sem07-referencia` (desde `PaqRap_version_referencia_sem07`).
- `main` parte de ahí. Primer commit: "Estado etapas 8-15 (sem07)".
- `docs/control_configuracion.md`: elementos de configuración, convención de etiquetas y dónde está cada copia. Es insumo para el plan IEEE 828.
- Nota: en `v-sem07-referencia`, `BusquedaTabu` quedó en `Busquedatabu.java` (Windows no distingue mayúsculas); en `main` se corrigió.
