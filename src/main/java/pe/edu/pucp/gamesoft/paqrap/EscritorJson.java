package pe.edu.pucp.gamesoft.paqrap;

import java.util.Locale;

/**
 * Escritor JSON mínimo, sin librerías externas (Etapa 21). Arma el texto en
 * orden: abrir objeto o arreglo, escribir pares clave-valor y cerrar. Pone las
 * comas y escapa las cadenas. Lo usa Simulador.instantaneaJson para la capa
 * de integración con el visualizador, independiente de la tecnología web.
 */
final class EscritorJson {
    private final StringBuilder sb = new StringBuilder();
    private boolean primero = true;

    private void separar() {
        if (!primero) sb.append(',');
        primero = false;
    }

    EscritorJson abrirObjeto(String clave) {
        separar();
        if (clave != null) sb.append(cadena(clave)).append(':');
        sb.append('{');
        primero = true;
        return this;
    }

    EscritorJson abrirArreglo(String clave) {
        separar();
        if (clave != null) sb.append(cadena(clave)).append(':');
        sb.append('[');
        primero = true;
        return this;
    }

    EscritorJson cerrarObjeto() {
        sb.append('}');
        primero = false;
        return this;
    }

    EscritorJson cerrarArreglo() {
        sb.append(']');
        primero = false;
        return this;
    }

    EscritorJson valor(String clave, String v) {
        separar();
        if (clave != null) sb.append(cadena(clave)).append(':');
        sb.append(v == null ? "null" : cadena(v));
        return this;
    }

    EscritorJson valor(String clave, double v) {
        separar();
        if (clave != null) sb.append(cadena(clave)).append(':');
        sb.append(Double.isFinite(v) ? String.format(Locale.US, "%.3f", v) : "null");
        return this;
    }

    EscritorJson valor(String clave, long v) {
        separar();
        if (clave != null) sb.append(cadena(clave)).append(':');
        sb.append(v);
        return this;
    }

    EscritorJson valor(String clave, boolean v) {
        separar();
        if (clave != null) sb.append(cadena(clave)).append(':');
        sb.append(v);
        return this;
    }

    /** Punto [x, y] dentro de un arreglo. */
    EscritorJson punto(int x, int y) {
        separar();
        sb.append('[').append(x).append(',').append(y).append(']');
        return this;
    }

    private static String cadena(String s) {
        StringBuilder r = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': r.append("\\\""); break;
                case '\\': r.append("\\\\"); break;
                case '\n': r.append("\\n"); break;
                case '\r': r.append("\\r"); break;
                case '\t': r.append("\\t"); break;
                default:
                    if (c < 0x20) r.append(String.format("\\u%04x", (int) c));
                    else r.append(c);
            }
        }
        return r.append('"').toString();
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
