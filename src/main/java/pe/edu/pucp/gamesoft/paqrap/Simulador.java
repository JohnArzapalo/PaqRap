package pe.edu.pucp.gamesoft.paqrap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulador mínimo (Etapa 5; diseño en docs/diseno_simulador.md).
 *
 * SUPUESTOS DE ESTA VERSIÓN MÍNIMA:
 *  - Solo el almacén central (27,14); sin bloqueos (distancia Manhattan),
 *    sin turnos ni alimentación, sin averías ni mantenimiento.
 *  - Reloj simulado en minutos desde el día 1 a las 00:00. Un pedido entra a
 *    pendientes cuando el reloj alcanza su hora de registro.
 *  - Cada Sa minutos se replanifican los pedidos pendientes NO despachados
 *    con las unidades que están en el central en ese instante. Las rutas
 *    devueltas se despachan de inmediato y no se modifican: la unidad
 *    termina su ruta, vuelve al central y queda disponible desde su hora de
 *    regreso. Los varios viajes por unidad surgen de ahí (sin el tope de
 *    408 paquetes del experimento estático).
 *    MEJORA PENDIENTE (P16 del profesor): replanificar también las unidades
 *    en ruta (sus paradas aún no visitadas) en cada iteración.
 *  - Base de tiempo del planificador: el instante de replanificación es la
 *    hora 0 y cada pedido llega con su hora de registro relativa a él (ver
 *    Planificador). Horas de llegada y regreso: Compartido.horasLlegada y
 *    Compartido.duracionRuta, las mismas funciones que usan los algoritmos.
 *
 * COLAPSO (criterio operativo): primer instante simulado en que la hora
 * límite de un pedido ORIGINAL pasa sin que esté completamente entregado,
 * esté o no asignado. Se detecta de dos formas:
 *  1) una entrega pendiente cuya hora límite ya pasó en el ciclo actual;
 *  2) una entrega despachada que llegará después de su hora límite (se
 *     conoce al despachar, porque las rutas no cambian).
 * Se registra la hora límite exacta, no la del ciclo en que se detecta.
 * Si no hay colapso hasta el horizonte, la corrida es CENSURADA.
 */
class Simulador {

    static class Config {
        double saMin = Parametros.decimal("simulacion.sa_min", 60);
        double horizonteMin = Parametros.decimal("simulacion.horizonte_min", 7200);
        long taMs = Parametros.entero("simulacion.ta_ms", 2000);
        long maxEvaluaciones = 0;
        long semilla = 1;
    }

    /** Una ruta despachada, en minutos absolutos. */
    static class Despacho {
        UnidadTransporte unidad;
        double salidaMin, regresoMin, costo, km;
        final List<String> entregas = new ArrayList<>();
        final List<Double> llegadasMin = new ArrayList<>();
    }

    static class Resultado {
        boolean censurada;
        double colapsoMin;          // = horizonte si es censurada
        String pedidoColapso;       // pedido original que provocó el colapso (null si censurada)
        double costoAcumulado, kmAcumulados;
        int pedidosOriginalesTotal, pedidosLlegados, pedidosEntregados, pedidosEvaluables, pedidosEnPlazo;
        int replanificaciones;
        double planificadorMsMedio, planificadorMsMax;
        long iteracionesTotales, evaluacionesTotales;
        /** Σ entregas que un plan dejó sin asignar y volvieron a la cola (indicador de estabilidad). */
        long aplazamientos;
        int viajesTotales, viajesMax;
        double viajesMedio;
        final List<Despacho> despachos = new ArrayList<>();

        double pctPedidosEnPlazo() {
            return pedidosEvaluables == 0 ? 100.0 : 100.0 * pedidosEnPlazo / pedidosEvaluables;
        }
    }

    /** Estado de un pedido original (todas sus entregas parciales). */
    private static class Original {
        double registroMin, limiteMin;
        int partes, partesDespachadas;
        double ultimaLlegadaMin;

        boolean entregadoHasta(double t) {
            return partesDespachadas == partes && ultimaLlegadaMin <= t;
        }
    }

    /**
     * @param pedidos entregas con horaRegistro ABSOLUTA en horas (LectorPedidos.leerAbsoluto)
     */
    static Resultado simular(List<Pedido> pedidos, List<UnidadTransporte> flota, Planificador planificador,
                             Config cfg) {
        List<Pedido> porLlegar = new ArrayList<>(pedidos);
        porLlegar.sort(Comparator.comparingDouble(p -> p.horaRegistro));
        Map<String, Original> originales = new LinkedHashMap<>();
        for (Pedido p : porLlegar) {
            Original o = originales.computeIfAbsent(p.idOriginal, k -> new Original());
            o.registroMin = p.horaRegistro * 60;
            o.limiteMin = p.horaLimite() * 60;
            o.partes++;
        }
        Map<String, Pedido> porId = new HashMap<>();
        for (Pedido p : porLlegar) porId.put(p.id, p);

        Map<String, Double> disponibleDesde = new HashMap<>();
        Map<String, Integer> viajes = new HashMap<>();
        for (UnidadTransporte u : flota) { disponibleDesde.put(u.codigo, 0.0); viajes.put(u.codigo, 0); }

        Resultado res = new Resultado();
        res.pedidosOriginalesTotal = originales.size();
        List<Pedido> pendientes = new ArrayList<>();
        int siguiente = 0;
        double colapsoPrevisto = Double.POSITIVE_INFINITY;
        String pedidoColapsoPrevisto = null;
        double sumaMs = 0;
        int ciclo = 0;
        double t = 0;

        while (true) {
            // 1. Llegan los pedidos registrados hasta t
            while (siguiente < porLlegar.size() && porLlegar.get(siguiente).horaRegistro * 60 <= t) {
                pendientes.add(porLlegar.get(siguiente++));
            }
            // 2. ¿Colapso? (pendiente vencida o despacho tardío ya previsto)
            double colapso = colapsoPrevisto;
            String causa = pedidoColapsoPrevisto;
            for (Pedido p : pendientes) {
                double limite = p.horaLimite() * 60;
                if (limite <= t && limite < colapso) { colapso = limite; causa = p.idOriginal; }
            }
            if (colapso <= t) {
                res.colapsoMin = colapso;
                res.pedidoColapso = causa;
                break;
            }
            if (t >= cfg.horizonteMin) {
                res.censurada = true;
                res.colapsoMin = cfg.horizonteMin;
                break;
            }
            // 3. Replanificación con las unidades que están en el central
            List<UnidadTransporte> disponibles = new ArrayList<>();
            for (UnidadTransporte u : flota) if (disponibleDesde.get(u.codigo) <= t) disponibles.add(u);
            if (!pendientes.isEmpty() && !disponibles.isEmpty()) {
                List<Pedido> relativos = new ArrayList<>();
                for (Pedido p : pendientes) {
                    relativos.add(new Pedido(p.id, p.idOriginal, p.x, p.y, p.cantidad, p.plazoMaximoHoras,
                            p.horaRegistro - t / 60.0));
                }
                long semillaCiclo = cfg.semilla * 1_000_003L + ciclo;
                long t0 = System.nanoTime();
                Planificador.Plan plan = planificador.planificar(relativos, disponibles, cfg.taMs,
                        cfg.maxEvaluaciones, semillaCiclo);
                double ms = (System.nanoTime() - t0) / 1e6;
                res.replanificaciones++;
                sumaMs += ms;
                res.planificadorMsMax = Math.max(res.planificadorMsMax, ms);
                res.iteracionesTotales += plan.iteraciones;
                res.evaluacionesTotales += plan.evaluaciones;
                res.aplazamientos += plan.sinAsignar.size();

                // 4. Despacho de las rutas (horas relativas -> minutos absolutos)
                for (RutaAlg r : plan.rutas) {
                    Despacho d = new Despacho();
                    d.unidad = r.unidad;
                    d.salidaMin = t;
                    d.regresoMin = t + 60 * Compartido.duracionRuta(r);
                    d.costo = r.costo;
                    d.km = r.distanciaKm;
                    double[] llegada = Compartido.horasLlegada(r);
                    for (int i = 0; i < r.paradas.size(); i++) {
                        Pedido p = porId.get(r.paradas.get(i).pedido.id);
                        double llegadaMin = t + 60 * llegada[i];
                        d.entregas.add(p.id);
                        d.llegadasMin.add(llegadaMin);
                        pendientes.remove(p);
                        Original o = originales.get(p.idOriginal);
                        o.partesDespachadas++;
                        o.ultimaLlegadaMin = Math.max(o.ultimaLlegadaMin, llegadaMin);
                        if (llegadaMin > o.limiteMin && o.limiteMin < colapsoPrevisto) {
                            colapsoPrevisto = o.limiteMin;
                            pedidoColapsoPrevisto = p.idOriginal;
                        }
                    }
                    disponibleDesde.put(r.unidad.codigo, d.regresoMin);
                    viajes.merge(r.unidad.codigo, 1, Integer::sum);
                    res.despachos.add(d);
                }
            }
            ciclo++;
            t = Math.min(t + cfg.saMin, cfg.horizonteMin);
        }

        // 5. Métricas hasta el colapso o el horizonte
        double fin = res.colapsoMin;
        for (Despacho d : res.despachos) {
            if (d.salidaMin <= fin) { res.costoAcumulado += d.costo; res.kmAcumulados += d.km; }
        }
        for (Original o : originales.values()) {
            if (o.registroMin > fin) continue;
            res.pedidosLlegados++;
            boolean entregado = o.entregadoHasta(fin);
            if (entregado) res.pedidosEntregados++;
            // Evaluable: su resultado ya se conoce en "fin" (entregado o vencido)
            if (entregado || o.limiteMin <= fin) {
                res.pedidosEvaluables++;
                if (entregado && o.ultimaLlegadaMin <= o.limiteMin) res.pedidosEnPlazo++;
            }
        }
        res.planificadorMsMedio = res.replanificaciones == 0 ? 0 : sumaMs / res.replanificaciones;
        for (int v : viajes.values()) { res.viajesTotales += v; res.viajesMax = Math.max(res.viajesMax, v); }
        res.viajesMedio = flota.isEmpty() ? 0 : (double) res.viajesTotales / flota.size();
        return res;
    }
}
