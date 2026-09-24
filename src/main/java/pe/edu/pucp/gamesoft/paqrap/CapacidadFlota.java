package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Capacidad teórica diaria de la flota (C_max), base de los niveles de carga
 * del IEN (30 / 60 / 90 % de C_max por día).
 *
 * Cálculo propio (por defecto):
 *   C_max = Σ_k n_k · q_k · ⌊T / t_k⌋
 *   - n_k: unidades del tipo k en la flota; q_k: capacidad (paquetes).
 *   - T: horas efectivas por día = 21 (24 h menos 3 turnos × 1 h de
 *     alimentación), parámetro carga.horas_efectivas.
 *   - t_k: tiempo medio de un viaje completo del tipo k =
 *       d̄ / v_k  +  HORAS_ENTREGA × e_k
 *     d̄  = distancia media de ida y vuelta (Manhattan) desde el central a
 *          las entregas del archivo de pedidos;
 *     v_k = velocidad configurada del tipo k;
 *     e_k = entregas promedio por viaje = max(1, q_k / q̄_k), donde q̄_k es el
 *          tamaño medio de las entregas del archivo que caben en el tipo k
 *          (un viaje lleno lleva q_k paquetes repartidos en entregas de
 *          tamaño medio q̄_k).
 *   APROXIMACIÓN: se cuenta un solo trayecto de ida y vuelta por viaje; no se
 *   suma la distancia entre clientes de un mismo viaje (optimista) y se asume
 *   que cada viaje sale lleno (optimista).
 *
 * Referencia (NO oficial): 1 536 paquetes/día de la hoja auxiliar
 * "Aux-Flota-SIN-VALOR" del Excel de preguntas y respuestas del profesor,
 * marcada "REVISAR": 10·24·3 + 15·8·6 + 12·4·2 con velocidades 20/40/14 km/h
 * y número de viajes tomado como mínimo. Se elige con carga.cmax_fuente.
 */
final class CapacidadFlota {

    static final double HORAS_EFECTIVAS = Parametros.decimal("carga.horas_efectivas", 21);
    static final double CMAX_HOJA = Parametros.decimal("carga.cmax_hoja", 1536);
    /** "propio" (por defecto) u "hoja". */
    static final String FUENTE = Parametros.texto("carga.cmax_fuente", "propio");

    private CapacidadFlota() {
    }

    static class DetalleTipo {
        TipoUnidad tipo;
        int unidades;
        double tamanoMedioEntrega;   // q̄_k
        double entregasPorViaje;     // e_k
        double horasViaje;           // t_k
        int viajesPorDia;            // ⌊T / t_k⌋
        long paquetesPorDia;         // n_k · q_k · ⌊T / t_k⌋
    }

    static class Resultado {
        double distanciaIdaVueltaKm;   // d̄
        final List<DetalleTipo> tipos = new ArrayList<>();
        long cmaxPropio;
        double cmaxHoja = CMAX_HOJA;

        /** C_max que se usa para los niveles, según carga.cmax_fuente. */
        double cmaxElegido() {
            return "hoja".equalsIgnoreCase(FUENTE) ? cmaxHoja : cmaxPropio;
        }

        String fuenteElegida() {
            return "hoja".equalsIgnoreCase(FUENTE) ? "hoja (1 536, REVISAR)" : "propio";
        }

        String reporte() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "  d̄ (ida y vuelta) = %.2f km; T = %.0f h; horas de entrega = %.1f%n",
                    distanciaIdaVueltaKm, HORAS_EFECTIVAS, Compartido.HORAS_ENTREGA));
            sb.append("  Tipo       n   q  v(km/h)  q̄_k   e_k   t_k(h)  viajes/día  paquetes/día\n");
            for (DetalleTipo d : tipos) {
                sb.append(String.format(Locale.US, "  %-9s %2d  %2d  %6.1f  %5.2f  %4.2f  %6.2f  %10d  %12d%n",
                        d.tipo, d.unidades, d.tipo.capacidadMaxima, d.tipo.velocidadPromedio, d.tamanoMedioEntrega,
                        d.entregasPorViaje, d.horasViaje, d.viajesPorDia, d.paquetesPorDia));
            }
            sb.append(String.format(Locale.US, "  C_max propio = %d paquetes/día | hoja auxiliar = %.0f | se usa: %s%n",
                    cmaxPropio, cmaxHoja, fuenteElegida()));
            return sb.toString();
        }
    }

    /** @param entregas entregas del archivo de pedidos (ya divididas en partes de hasta 24) */
    static Resultado calcular(List<Pedido> entregas, List<UnidadTransporte> flota) {
        Resultado res = new Resultado();
        Almacen al = Compartido.ALMACEN_CENTRAL;
        double suma = 0;
        for (Pedido p : entregas) suma += 2 * Compartido.distancia(al.x, al.y, p.x, p.y);
        res.distanciaIdaVueltaKm = entregas.isEmpty() ? 0 : suma / entregas.size();

        for (TipoUnidad t : TipoUnidad.values()) {
            int n = 0;
            for (UnidadTransporte u : flota) if (u.tipo == t) n++;
            if (n == 0) continue;
            DetalleTipo d = new DetalleTipo();
            d.tipo = t;
            d.unidades = n;
            double sumaQ = 0;
            int caben = 0;
            for (Pedido p : entregas) {
                if (p.cantidad <= t.capacidadMaxima) { sumaQ += p.cantidad; caben++; }
            }
            if (caben == 0) {   // ninguna entrega cabe: el tipo no aporta capacidad útil
                res.tipos.add(d);
                continue;
            }
            d.tamanoMedioEntrega = sumaQ / caben;
            d.entregasPorViaje = Math.max(1.0, t.capacidadMaxima / d.tamanoMedioEntrega);
            d.horasViaje = res.distanciaIdaVueltaKm / t.velocidadPromedio + Compartido.HORAS_ENTREGA * d.entregasPorViaje;
            d.viajesPorDia = (int) Math.floor(HORAS_EFECTIVAS / d.horasViaje);
            d.paquetesPorDia = (long) n * t.capacidadMaxima * d.viajesPorDia;
            res.cmaxPropio += d.paquetesPorDia;
            res.tipos.add(d);
        }
        return res;
    }
}
