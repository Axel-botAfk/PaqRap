package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.GeneradorVentas;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Pedido;
import com.paqrap.planificador.ruteo.DistanciaManhattan;
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
 * Simulacion de cinco dias de operacion: el escenario 5D del caso.
 *
 * Toma el archivo de ventas del mes, deja que el reloj avance de media hora en media hora y
 * replanifica en cada salto con los pedidos que ya llegaron y las unidades donde hayan quedado.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoSimulacion5Dias [ruta/al/ventas202609]
 */
public final class DemoSimulacion5Dias {
    private static final Path ARCHIVO_POR_DEFECTO = Path.of("datos", "ventas202609");
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final int PRIMER_DIA = 1;
    private static final int DIAS = 5;

    private DemoSimulacion5Dias() {
    }

    public static void main(String[] args) throws IOException {
        LocalDateTime inicio = PERIODO.atDay(PRIMER_DIA).atStartOfDay();
        List<Pedido> ventas = cargarVentas(args);

        Parametros parametros = Parametros.constructor(8, 0.30, 20260901L)
                .iteracionesTabu(120)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(40)
                .iteracionesSinMejora(25)
                .construir();

        Simulador simulador = new Simulador(
                new BusquedaTabu(new DistanciaManhattan()),
                new DistanciaManhattan(),
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(30),
                false
        );

        System.out.println("=== SIMULACION DE 5 DIAS ===");
        System.out.println("Inicio: " + inicio);
        System.out.println("Pedidos del periodo: " + ventas.size());
        System.out.println("Flota: " + DatosCaso.flota().size() + " unidades");
        System.out.println("Replanificacion cada 30 minutos. Calculando...");

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, Duration.ofDays(DIAS), ventas, DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
    }

    private static List<Pedido> cargarVentas(String[] args) throws IOException {
        Path archivo = args.length > 0 ? Path.of(args[0]) : ARCHIVO_POR_DEFECTO;
        if (Files.exists(archivo)) {
            System.out.println("Archivo de ventas: " + archivo);
            return LectorVentas.leer(archivo, PERIODO);
        }

        System.out.println("No se encontro " + archivo + "; se generan ventas equivalentes.");
        return new GeneradorVentas(20260901L)
                .demandaConstante(PERIODO, PRIMER_DIA, DIAS, 120);
    }
}
