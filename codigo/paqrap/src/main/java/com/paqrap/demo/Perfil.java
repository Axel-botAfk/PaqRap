package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.DatosReales;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Solucion;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.InsercionPorHolgura;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.alns.BusquedaAlns;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.tabu.BusquedaTabu;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Una sola planificación por algoritmo, sobre el mismo estado y con los contadores a la vista.
 *
 * Existe para responder por qué un algoritmo tarda más que otro con una atribución y no con una
 * sospecha: cuántas veces evalúa el plan completo, cuántas mide un viaje y cuántas le pregunta al
 * enrutador. El reloj solo dice que algo va lento; estos números dicen qué.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.Perfil [horasDeCola] [carpeta]
 */
public final class Perfil {
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);

    /**
     * Cuanto tiempo de operacion avanza el simulador antes de tirar el plan y rehacerlo.
     *
     * Es el latido por defecto. Todo lo que el plan prometa despues de este instante no llega a
     * ocurrir: la unidad se queda donde la sorprendio el corte y la planificacion siguiente decide
     * de nuevo. Por eso un plan barato en total puede rendir menos que uno que entrega temprano.
     */
    private static final int CORTE_MINUTOS = 30;

    private Perfil() {
    }

    public static void main(String[] args) throws IOException {
        int horas = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : DatosReales.CARPETA_POR_DEFECTO;

        DatosReales datos = DatosReales.cargar(carpeta, PERIODO);
        LocalDateTime reloj = PERIODO.atDay(1).atStartOfDay().plusHours(horas);

        List<Pedido> enCola = new ArrayList<>();
        for (Pedido pedido : datos.ventas()) {
            if (!pedido.getFechaRegistro().isAfter(reloj)) {
                enCola.add(pedido);
            }
        }

        System.out.println("=== PERFIL DE UNA PLANIFICACION ===");
        System.out.println("Instante: " + reloj + " | cola: " + enCola.size() + " pedidos");
        System.out.println();
        System.out.println("  Corte: " + CORTE_MINUTOS + " min, que es lo unico que el"
                + " simulador ejecuta antes de replanificar.");
        System.out.println();
        System.out.printf("  %-14s %9s %8s %9s %9s %9s %9s%n",
                "algoritmo", "ms", "viajes", "prod.plan", "prod.<corte", "km plan", "km <corte");
        int aBordoTotal = 0;
        for (com.paqrap.modelo.Vehiculo u : DatosCaso.flota()) { aBordoTotal += u.getCargaABordo(); }
        System.out.println("  (producto a bordo al planificar: " + aBordoTotal + ")");

        for (String algoritmo : List.of("constructivo", "tabu", "grasp", "alns")) {
            MapaBloqueos mapa = new MapaBloqueos(datos.bloqueos());
            EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
            Evaluador evaluador = new Evaluador(enrutador);

            EstadoOperacion estado = new EstadoOperacion(
                    reloj, enCola, DatosCaso.almacenes(), DatosCaso.flota());
            Parametros parametros = Parametros.constructor(8, 0.30, 20260901L)
                    .penalidadEspera(Double.parseDouble(
                            System.getProperty("paqrap.espera", "80.0")))
                    .iteracionesTabu(120)
                    .tenenciaTabu(8)
                    .tamanoMuestraVecindario(40)
                    .iteracionesSinMejora(25)
                    .construir();

            Evaluador.PLANES = 0;
            Evaluador.METRICAS = 0;
            EnrutadorBloqueos.CONSULTAS = 0;
            EnrutadorBloqueos.BUSQUEDAS = 0;

            long antes = System.currentTimeMillis();
            Solucion plan = planificador(algoritmo, evaluador).planificar(estado, parametros);
            long ms = System.currentTimeMillis() - antes;

            LocalDateTime corte = reloj.plusMinutes(CORTE_MINUTOS);
            int productosAntesDelCorte = 0;
            double kmAntesDelCorte = 0.0;
            double kmDelPlan = 0.0;

            List<MetricasRuta> metricas =
                    evaluador.evaluarPlan(plan, estado).metricasPorRuta();
            for (int i = 0; i < plan.getCantidadRutas(); i++) {
                MetricasRuta medida = metricas.get(i);
                if (medida == null || !medida.factible()) {
                    continue;
                }
                kmDelPlan += medida.distanciaTotalKm();
                boolean alcanzaAlguna = false;
                for (Pedido pedido : plan.getRuta(i).getPedidos()) {
                    LocalDateTime llegada = medida.horasLlegada().get(pedido.getId());
                    if (llegada != null && !llegada.isAfter(corte)) {
                        productosAntesDelCorte += pedido.getCantidad();
                        alcanzaAlguna = true;
                    }
                }
                if (alcanzaAlguna) {
                    kmAntesDelCorte += medida.distanciaTotalKm();
                }
            }

            int viajesCargados = 0;
            int productoEnCargados = 0;
            for (com.paqrap.modelo.Ruta viaje : plan.getRutas()) {
                if (viaje.saleYaCargado()) {
                    viajesCargados++;
                    productoEnCargados += viaje.getCargaTotal();
                }
            }
            // Clientes por viaje separados por tipo de unidad: dice si los viajes cortos son
            // decision del objetivo o tope fisico de motos y bicis.
            java.util.Map<String, int[]> porTipo = new java.util.TreeMap<>();
            for (com.paqrap.modelo.Ruta viaje : plan.getRutas()) {
                if (viaje.estaVacia()) {
                    continue;
                }
                int[] cuenta = porTipo.computeIfAbsent(
                        viaje.getVehiculo().getTipo().name(), t -> new int[3]);
                cuenta[0]++;
                cuenta[1] += viaje.getPedidos().size();
                cuenta[2] += viaje.getCargaTotal();
            }
            StringBuilder desglose = new StringBuilder();
            for (java.util.Map.Entry<String, int[]> e : porTipo.entrySet()) {
                int[] c = e.getValue();
                desglose.append(String.format("  %s %d viajes %.1f cli/viaje %.1f prod/viaje",
                        e.getKey().substring(0, 4), c[0], c[1] / (double) c[0],
                        c[2] / (double) c[0]));
            }

            System.out.printf("  %-14s %9d %8d %9d %9d %9.0f %9.0f  %s%n",
                    algoritmo, ms, plan.getCantidadRutas(),
                    plan.getCantidadProductosAsignados(), productosAntesDelCorte,
                    kmDelPlan, kmAntesDelCorte, desglose.toString());
        }
    }

    /** Esfuerzo de ALNS, en iteraciones de destruir y reparar. */
    private static com.paqrap.planificador.alns.ParametrosAlns alnsConEsfuerzo() {
        String valor = System.getProperty("paqrap.alns.iteraciones");
        com.paqrap.planificador.alns.ParametrosAlns base =
                com.paqrap.planificador.alns.ParametrosAlns.porDefecto();
        return valor == null ? base : base.conIteraciones(Integer.parseInt(valor));
    }

    private static Planificador planificador(String algoritmo, Evaluador evaluador) {
        return switch (algoritmo) {
            case "grasp" -> new Grasp(evaluador);
            case "alns" -> new BusquedaAlns(evaluador, alnsConEsfuerzo());
            case "tabu" -> new BusquedaTabu(evaluador);
            default -> new InsercionPorHolgura(evaluador);
        };
    }
}
