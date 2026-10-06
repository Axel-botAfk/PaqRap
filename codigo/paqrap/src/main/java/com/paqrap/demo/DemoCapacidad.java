package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.GeneradorVentas;
import com.paqrap.modelo.Pedido;
import com.paqrap.planificador.ruteo.DistanciaManhattan;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Sonda de capacidad: cuantos pedidos por dia aguanta la flota.
 *
 * Corre la misma operacion con demanda constante a distintos niveles y mira si la cola crece o
 * se estabiliza. El nivel mas alto en que la cola no crece es el techo sostenible, y es el dato
 * que permite calibrar la curva del escenario de colapso: pedirle a la operacion que dure dos
 * semanas no depende solo del planificador, depende de que la demanda se mantenga por debajo de
 * ese techo durante esas dos semanas.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoCapacidad
 */
public final class DemoCapacidad {
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final int DIAS = 6;
    private static final int[] NIVELES_POR_DEFECTO = {100, 150, 200, 250, 300};

    private DemoCapacidad() {
    }

    public static void main(String[] args) {
        LocalDateTime inicio = PERIODO.atDay(1).atStartOfDay();
        int[] niveles = args.length > 1 ? leerNiveles(args[1]) : NIVELES_POR_DEFECTO;

        System.out.println("=== CAPACIDAD SOSTENIBLE DE LA FLOTA ===");
        System.out.println("Flota: " + DatosCaso.flota().size() + " unidades | "
                + DIAS + " dias por nivel de demanda");
        System.out.println();
        System.out.printf("%8s %10s %10s %10s %9s %10s%n",
                "ped/dia", "recibidos", "entregados", "cola final", "vencidos", "computo");

        for (int nivel : niveles) {
            List<Pedido> ventas = new GeneradorVentas(20260901L)
                    .demandaConstante(PERIODO, 1, DIAS, nivel);

            ResumenSimulacion resumen = simulador()
                    .correr(inicio, Duration.ofDays(DIAS), ventas, DatosCaso.flota());

            System.out.printf("%8d %10d %10d %10d %9d %9.1fs%n",
                    nivel,
                    resumen.pedidosRecibidos(),
                    resumen.pedidosEntregados(),
                    resumen.pedidosPendientes(),
                    resumen.vencidos().size(),
                    resumen.milisegundosDeComputo() / 1000.0);
        }

        System.out.println();
        System.out.println("La cola final crece cuando la demanda supera el techo: ese es el");
        System.out.println("nivel a partir del cual la operacion colapsa si se sostiene.");
    }

    private static int[] leerNiveles(String texto) {
        String[] partes = texto.split(",");
        int[] niveles = new int[partes.length];
        for (int i = 0; i < partes.length; i++) {
            niveles[i] = Integer.parseInt(partes[i].trim());
        }
        return niveles;
    }

    private static Simulador simulador() {
        Parametros parametros = Parametros.constructor(8, 0.30, 20260901L)
                .iteracionesTabu(120)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(40)
                .iteracionesSinMejora(25)
                .construir();

        return new Simulador(
                new BusquedaTabu(new DistanciaManhattan()),
                new DistanciaManhattan(),
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(30),
                false
        );
    }
}
