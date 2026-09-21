package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.GeneradorVentas;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Pedido;
import com.paqrap.planificador.DistanciaManhattan;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Simulacion hasta el colapso logistico.
 *
 * La demanda crece cada dia hasta que la flota deja de poder comprometerse con todo lo que
 * llega. Como el planificador nunca acepta una entrega fuera de plazo, el pedido que no alcanza
 * a ser atendido se queda esperando y, cuando vence, la operacion colapso.
 *
 * La rampa esta calibrada contra el techo que mide DemoCapacidad: sube despacio para que la
 * operacion aguante varias semanas y el quiebre se vea venir en la evolucion diaria, en lugar
 * de producirse a los pocos dias por una demanda que ninguna flota podria absorber.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoColapso [ruta/al/ventas202610]
 */
public final class DemoColapso {
    private static final Path ARCHIVO_POR_DEFECTO = Path.of("datos", "ventas202610");
    private static final YearMonth PERIODO = YearMonth.of(2026, 10);
    private static final int PRIMER_DIA = 1;
    private static final int DIAS_MAXIMOS = 31;

    private DemoColapso() {
    }

    public static void main(String[] args) throws IOException {
        LocalDateTime inicio = PERIODO.atDay(PRIMER_DIA).atStartOfDay();
        List<Pedido> ventas = cargarVentas(args);

        Parametros parametros = Parametros.constructor(8, 0.30, 20261001L)
                .iteracionesTabu(120)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(40)
                .iteracionesSinMejora(25)
                .limiteMilisegundos(400L)
                .construir();

        Simulador simulador = new Simulador(
                new BusquedaTabu(new DistanciaManhattan()),
                new DistanciaManhattan(),
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(30),
                true
        );

        System.out.println("=== SIMULACION HASTA EL COLAPSO ===");
        System.out.println("Inicio: " + inicio);
        System.out.println("Pedidos disponibles: " + ventas.size()
                + " repartidos en hasta " + DIAS_MAXIMOS + " dias de demanda creciente");
        System.out.println("Flota: " + DatosCaso.flota().size() + " unidades");
        System.out.println("Calculando...");

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, Duration.ofDays(DIAS_MAXIMOS), ventas, DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
        Reporte.imprimirEvolucionDiaria(resumen);
    }

    private static List<Pedido> cargarVentas(String[] args) throws IOException {
        Path archivo = args.length > 0 ? Path.of(args[0]) : ARCHIVO_POR_DEFECTO;
        if (Files.exists(archivo)) {
            System.out.println("Archivo de ventas: " + archivo);
            return LectorVentas.leer(archivo, PERIODO);
        }

        System.out.println("No se encontro " + archivo + "; se genera demanda creciente.");
        return new GeneradorVentas(20261001L)
                .demandaCreciente(PERIODO, PRIMER_DIA, DIAS_MAXIMOS, 60, 8);
    }
}
