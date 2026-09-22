package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.LectorBloqueos;
import com.paqrap.datos.LectorMantenimiento;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.planificador.EnrutadorBloqueos;
import com.paqrap.planificador.MapaBloqueos;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Corrida sobre los archivos reales entregados por el equipo docente: ventas del mes, bloqueos
 * del mes y plan de mantenimiento preventivo, los tres a la vez.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoDatosReales [dias] [carpeta] [iteracionesTabu] [limiteMs] [minutosEntrePlanes]
 *
 * Por defecto simula 5 dias de setiembre de 2026 leyendo de datos/reales.
 */
public final class DemoDatosReales {
    private static final Path CARPETA_POR_DEFECTO = Path.of("datos", "reales");
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final int DIAS_POR_DEFECTO = 5;

    private DemoDatosReales() {
    }

    public static void main(String[] args) throws IOException {
        int dias = args.length > 0 ? Integer.parseInt(args[0]) : DIAS_POR_DEFECTO;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : CARPETA_POR_DEFECTO;
        int iteracionesTabu = args.length > 2 ? Integer.parseInt(args[2]) : 120;
        long limiteMs = args.length > 3 ? Long.parseLong(args[3]) : 400L;
        int minutosEntrePlanes = args.length > 4 ? Integer.parseInt(args[4]) : 30;
        int minutosMinimos = args.length > 5 ? Integer.parseInt(args[5]) : 5;

        Path archivoVentas = carpeta.resolve("ventas.202609.txt");
        Path archivoBloqueos = carpeta.resolve("bloqueo.2609.txt");
        Path archivoMantenimiento = carpeta.resolve("mant.preventivo.09.10.txt");

        if (!Files.exists(archivoVentas)) {
            System.out.println("No se encontraron los archivos reales en " + carpeta.toAbsolutePath());
            System.out.println("Copie ahi ventas.202609.txt, bloqueo.2609.txt y "
                    + "mant.preventivo.09.10.txt, o pase la carpeta como segundo argumento.");
            return;
        }

        List<Pedido> ventas = LectorVentas.leer(archivoVentas, PERIODO);
        List<Bloqueo> bloqueos = LectorBloqueos.leer(archivoBloqueos);
        PlanMantenimiento mantenimiento = LectorMantenimiento.leer(archivoMantenimiento);

        LocalDateTime inicio = PERIODO.atDay(1).atStartOfDay();

        System.out.println("=== DATOS REALES DEL CASO ===");
        System.out.println("Ventas:        " + archivoVentas + "  (" + ventas.size() + " pedidos)");
        System.out.println("Bloqueos:      " + archivoBloqueos + "  (" + bloqueos.size() + " tramos)");
        System.out.println("Mantenimiento: " + archivoMantenimiento
                + "  (" + mantenimiento.getCantidadDeJornadas() + " jornadas programadas)");
        imprimirMantenimientoDelPeriodo(mantenimiento, inicio, dias);

        MapaBloqueos mapa = new MapaBloqueos(bloqueos);
        System.out.println("Nodos cerrados al arrancar: " + mapa.nodosBloqueadosEn(inicio).size());
        System.out.println("Horizonte: " + dias + " dias | replanifica por eventos (latido de "
                + minutosEntrePlanes + " min) | tabu: " + iteracionesTabu + " iteraciones, tope "
                + limiteMs + " ms. Calculando...");

        Parametros parametros = Parametros.constructor(8, 0.30, 20260901L)
                .iteracionesTabu(iteracionesTabu)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(40)
                .iteracionesSinMejora(25)
                .limiteMilisegundos(limiteMs)
                .construir();

        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
        Simulador simulador = new Simulador(
                new BusquedaTabu(enrutador),
                enrutador,
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(minutosEntrePlanes),
                false
        ).conMantenimiento(mantenimiento)
                .conEventosDeBloqueo(mapa)
                .conIntervaloMinimo(Duration.ofMinutes(minutosMinimos));

        String seguido = System.getProperty("paqrap.seguir");
        if (seguido != null) {
            simulador.siguiendoA(seguido);
        }

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, Duration.ofDays(dias), ventas, DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
        Reporte.imprimirEvolucionDiaria(resumen);
    }

    private static void imprimirMantenimientoDelPeriodo(
            PlanMantenimiento plan,
            LocalDateTime inicio,
            int dias
    ) {
        System.out.println("Unidades fuera de servicio en el periodo simulado:");
        for (int i = 0; i < dias; i++) {
            LocalDate dia = inicio.toLocalDate().plusDays(i);
            System.out.println("  " + dia + "  " + plan.unidadesDelDia(dia));
        }
    }
}
