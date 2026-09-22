package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.LectorBloqueos;
import com.paqrap.datos.LectorMantenimiento;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.EnrutadorBloqueos;
import com.paqrap.planificador.Evaluador;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Igual que {@link DemoDatosReales} (misma fuente de datos, mismo planificador por defecto),
 * pero con el reporte más detallado para exponer: usa el enganche {@link
 * com.paqrap.simulacion.Observador} del {@link Simulador} para mostrar, además del resumen
 * agregado, un plan completo real (ruta por ruta) y la utilización de la flota a lo largo de
 * toda la corrida.
 *
 * No cambia ninguna regla de negocio ni el mecanismo de simulación: solo mira lo que el
 * simulador ya publica en cada planificación.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoDatosRealesDetallado [dias] [carpeta] [iteracionesTabu] [limiteMs] [minutosEntrePlanes]
 *
 * Por defecto simula 5 días de setiembre de 2026 leyendo de datos/reales con la búsqueda tabú.
 */
public final class DemoDatosRealesDetallado {
    private static final Path CARPETA_POR_DEFECTO = Path.of("datos", "reales");
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final int DIAS_POR_DEFECTO = 5;

    private DemoDatosRealesDetallado() {
    }

    public static void main(String[] args) throws IOException {
        int dias = args.length > 0 ? Integer.parseInt(args[0]) : DIAS_POR_DEFECTO;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : CARPETA_POR_DEFECTO;
        int iteracionesTabu = args.length > 2 ? Integer.parseInt(args[2]) : 120;
        long limiteMs = args.length > 3 ? Long.parseLong(args[3]) : 400L;
        int minutosEntrePlanes = args.length > 4 ? Integer.parseInt(args[4]) : 30;

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

        System.out.println("=== DATOS REALES DEL CASO (version detallada) ===");
        System.out.println("Ventas:        " + archivoVentas + "  (" + ventas.size() + " pedidos)");
        System.out.println("Bloqueos:      " + archivoBloqueos + "  (" + bloqueos.size() + " tramos)");
        System.out.println("Mantenimiento: " + archivoMantenimiento
                + "  (" + mantenimiento.getCantidadDeJornadas() + " jornadas programadas)");
        System.out.println("Horizonte: " + dias + " dias | latido de " + minutosEntrePlanes
                + " min | tabu: " + iteracionesTabu + " iteraciones, tope " + limiteMs + " ms.");
        System.out.println("Calculando (esto imprime, ademas del resumen, un plan completo real"
                + " y la utilizacion de flota)...");

        Parametros parametros = Parametros.constructor(8, 0.30, 20260901L)
                .iteracionesTabu(iteracionesTabu)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(40)
                .iteracionesSinMejora(25)
                .limiteMilisegundos(limiteMs)
                .construir();

        MapaBloqueos mapa = new MapaBloqueos(bloqueos);
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
        BusquedaTabu tabu = new BusquedaTabu(new Evaluador(enrutador));

        // --- Lo que agrega esta version: mirar cada planificacion mientras ocurre. ---
        Set<String> unidadesUsadasAlgunaVez = new TreeSet<>();
        Set<LocalDate> diasYaMostrados = new HashSet<>();
        int[] cantidadDePlanificaciones = {0};
        List<String> ejemplosDePlan = new ArrayList<>();

        Simulador simulador = new Simulador(
                tabu,
                enrutador,
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(minutosEntrePlanes),
                false
        ).conMantenimiento(mantenimiento)
                .conEventosDeBloqueo(mapa)
                .observadoPor((reloj, estado, plan, medicion) -> {
                    cantidadDePlanificaciones[0]++;
                    for (Ruta ruta : plan.getRutas()) {
                        unidadesUsadasAlgunaVez.add(ruta.getVehiculo().getId());
                    }
                    // Un plan completo de ejemplo por dia (el primero que se calcule ese dia),
                    // para ver como se ve una planificacion real y no solo los totales.
                    LocalDate dia = reloj.toLocalDate();
                    if (diasYaMostrados.add(dia) && diasYaMostrados.size() <= 2) {
                        ejemplosDePlan.add(capturarPlan(reloj, estado, plan, medicion));
                    }
                });

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, Duration.ofDays(dias), ventas, DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
        Reporte.imprimirEvolucionDiaria(resumen);

        System.out.println();
        System.out.println("=== EJEMPLOS DE PLAN REAL (primera planificacion de cada dia mostrado) ===");
        for (String ejemplo : ejemplosDePlan) {
            System.out.println(ejemplo);
        }

        imprimirUtilizacionDeFlota(DatosCaso.flota(), unidadesUsadasAlgunaVez, cantidadDePlanificaciones[0]);
    }

    private static String capturarPlan(
            LocalDateTime reloj,
            com.paqrap.planificador.EstadoOperacion estado,
            com.paqrap.modelo.Solucion plan,
            com.paqrap.simulacion.MedicionDePlanificacion medicion
    ) {
        StringBuilder texto = new StringBuilder();
        texto.append(String.format(
                "%n--- Planificacion en %s | %d pedidos en cola | Ta=%d ms ---%n",
                reloj, medicion.pedidosEnCola(), medicion.milisegundos()));
        for (Ruta ruta : plan.getRutas()) {
            texto.append(String.format(
                    "%-5s | almacen=%-14s | unidad=%-5s | carga=%2d/%2d | km=%6.2f | costo=S/ %8.2f | %s%n",
                    ruta.getId(),
                    ruta.getAlmacen().getId(),
                    ruta.getVehiculo().getId(),
                    ruta.getCargaTotal(),
                    ruta.getVehiculo().getCapacidad(),
                    ruta.getDistanciaTotalKm(),
                    ruta.getCostoTotal(),
                    ruta.getPedidos()
            ));
        }
        texto.append("No asignados en este plan: ").append(plan.getPedidosNoAsignados()).append(System.lineSeparator());
        texto.append(String.format(
                "Plan: S/ %.2f | %.2f km | %d viajes%n",
                plan.getCostoTotal(), plan.getDistanciaTotalKm(), plan.getCantidadRutas()));
        return texto.toString();
    }

    /**
     * De las 37 unidades de la flota, cuantas llegaron a recibir al menos una ruta en todo el
     * periodo simulado y cuales nunca se usaron. Es la pregunta que la evolucion diaria (llegan/
     * entregan/cola) no responde: si el cuello de botella es de cola o de unidades ociosas.
     */
    private static void imprimirUtilizacionDeFlota(
            List<Vehiculo> flotaCompleta,
            Set<String> unidadesUsadas,
            int cantidadDePlanificaciones
    ) {
        System.out.println();
        System.out.println("=== UTILIZACION DE FLOTA ===");
        System.out.println("Planificaciones totales en la corrida: " + cantidadDePlanificaciones);
        System.out.printf("Unidades que recibieron al menos una ruta: %d de %d (%.1f%%)%n",
                unidadesUsadas.size(), flotaCompleta.size(),
                100.0 * unidadesUsadas.size() / flotaCompleta.size());

        Set<String> sinUsar = new TreeSet<>();
        for (Vehiculo vehiculo : flotaCompleta) {
            if (!unidadesUsadas.contains(vehiculo.getId())) {
                sinUsar.add(vehiculo.getId());
            }
        }
        if (sinUsar.isEmpty()) {
            System.out.println("Todas las unidades de la flota se usaron al menos una vez.");
        } else {
            System.out.println("Unidades que nunca recibieron una ruta: " + sinUsar);
        }

        var porTipo = new TreeMap<String, int[]>(); // [usadas, total]
        for (Vehiculo vehiculo : flotaCompleta) {
            String tipo = vehiculo.getTipo().toString();
            int[] contador = porTipo.computeIfAbsent(tipo, t -> new int[2]);
            contador[1]++;
            if (unidadesUsadas.contains(vehiculo.getId())) {
                contador[0]++;
            }
        }
        System.out.println("Por tipo de unidad:");
        for (var entrada : porTipo.entrySet()) {
            System.out.printf("  %-10s %d de %d usadas%n",
                    entrada.getKey(), entrada.getValue()[0], entrada.getValue()[1]);
        }
    }
}
