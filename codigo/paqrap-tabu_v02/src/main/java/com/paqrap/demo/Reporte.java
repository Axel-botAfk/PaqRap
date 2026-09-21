package com.paqrap.demo;

import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.simulacion.ResumenSimulacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Impresión por consola de un plan, compartida por las demos.
 */
public final class Reporte {

    private Reporte() {
    }

    public static void imprimir(Solucion solucion) {
        imprimir(solucion.getAlgoritmo(), solucion);
    }

    public static void imprimir(String titulo, Solucion solucion) {
        System.out.println();
        System.out.println("=== " + titulo + " ===");
        for (Ruta ruta : solucion.getRutas()) {
            System.out.printf(
                    "%-5s | almacen=%-14s | unidad=%-5s | carga=%2d/%2d | km=%6.2f | costo=S/ %8.2f | %s%n",
                    ruta.getId(),
                    ruta.getAlmacen().getId(),
                    ruta.getVehiculo().getId(),
                    ruta.getCargaTotal(),
                    ruta.getVehiculo().getCapacidad(),
                    ruta.getDistanciaTotalKm(),
                    ruta.getCostoTotal(),
                    ruta.getPedidos()
            );
        }
        imprimirProgramasConVariosViajes(solucion);

        System.out.println("No asignados: " + solucion.getPedidosNoAsignados());
        System.out.printf(
                "Costo total: S/ %.2f | Distancia total: %.2f km | Viajes: %d%n",
                solucion.getCostoTotal(),
                solucion.getDistanciaTotalKm(),
                solucion.getCantidadRutas()
        );
    }

    /**
     * Unidades que hacen más de un viaje, con la secuencia de almacenes en que recargan. Es la
     * parte del plan que no se ve viaje por viaje.
     */
    private static void imprimirProgramasConVariosViajes(Solucion solucion) {
        Map<String, List<Ruta>> porUnidad = new LinkedHashMap<>();
        for (Ruta viaje : solucion.getRutas()) {
            porUnidad.computeIfAbsent(viaje.getVehiculo().getId(), id -> new ArrayList<>()).add(viaje);
        }

        boolean hayEncadenados = porUnidad.values().stream().anyMatch(viajes -> viajes.size() > 1);
        if (!hayEncadenados) {
            return;
        }

        System.out.println("Unidades con varios viajes:");
        for (Map.Entry<String, List<Ruta>> programa : porUnidad.entrySet()) {
            if (programa.getValue().size() < 2) {
                continue;
            }
            StringBuilder secuencia = new StringBuilder();
            for (Ruta viaje : programa.getValue()) {
                if (secuencia.length() > 0) {
                    secuencia.append(" -> ");
                }
                secuencia.append(viaje.getAlmacen().getId())
                        .append(' ').append(viaje.getPedidos());
            }
            System.out.printf("  %-5s %d viajes | %s%n",
                    programa.getKey(), programa.getValue().size(), secuencia);
        }
    }

    /** Resultado de una corrida simulada. */
    public static void imprimirResumen(ResumenSimulacion resumen, long milisegundosDeReloj) {
        System.out.println();
        System.out.println("=== RESULTADO ===");
        System.out.println("Periodo simulado: " + resumen.inicio() + " a " + resumen.fin());
        System.out.printf(
                "Pedidos: %d recibidos | %d entregados (%.1f%%) | %d pendientes | %d vencidos%n",
                resumen.pedidosRecibidos(),
                resumen.pedidosEntregados(),
                resumen.porcentajeAtendido(),
                resumen.pedidosPendientes(),
                resumen.vencidos().size()
        );
        System.out.printf(
                "Entregas en plazo: %d de %d%n",
                resumen.entregasEnPlazo(), resumen.pedidosEntregados());
        System.out.printf(
                "Operacion: %.2f km | S/ %.2f%n",
                resumen.distanciaTotalKm(), resumen.costoTotal());
        System.out.printf(
                "Planificacion: %d iteraciones | %.0f ms por iteracion | %.1f s de computo%n",
                resumen.iteracionesDePlanificacion(),
                resumen.milisegundosPorIteracion(),
                resumen.milisegundosDeComputo() / 1000.0
        );
        System.out.printf("Tiempo de reloj de la corrida: %.1f s%n", milisegundosDeReloj / 1000.0);

        if (resumen.huboColapso()) {
            System.out.println();
            System.out.println("COLAPSO LOGISTICO");
            System.out.println("  Primer pedido vencido: " + resumen.pedidoQueColapso().getId()
                    + " para el cliente " + resumen.pedidoQueColapso().getClienteId());
            System.out.println("  Destino: " + resumen.pedidoQueColapso().getDestino()
                    + " | " + resumen.pedidoQueColapso().getCantidad() + " unidades"
                    + " | plazo de " + resumen.pedidoQueColapso().getHorasPlazo() + " h");
            System.out.println("  Llego: " + resumen.pedidoQueColapso().getFechaRegistro()
                    + " | vencio: " + resumen.instanteDelColapso());
        } else {
            System.out.println();
            System.out.println("Sin colapso: todos los pedidos vencidos: 0");
        }
    }

    /** Llegadas, entregas y cola al cierre de cada dia: donde se quiebra la operacion. */
    public static void imprimirEvolucionDiaria(ResumenSimulacion resumen) {
        System.out.println();
        System.out.println("Evolucion diaria:");
        System.out.println("  dia          llegan  entregan  cola");
        List<LocalDate> dias = new ArrayList<>(resumen.recibidosPorDia().keySet());
        dias.sort(LocalDate::compareTo);
        for (LocalDate dia : dias) {
            System.out.printf("  %s    %4d      %4d  %4d%n",
                    dia,
                    resumen.recibidosPorDia().getOrDefault(dia, 0),
                    resumen.entregasPorDia().getOrDefault(dia, 0),
                    resumen.colaAlCierreDelDia().getOrDefault(dia, 0));
        }
        System.out.printf("  Entregas diarias promedio: %.0f%n", resumen.entregasDiariasPromedio());
    }

    /** Cuantos pedidos llegan cada dia, para ver donde se quiebra la operacion. */
    public static void imprimirDemandaPorDia(List<Pedido> pedidos) {
        Map<LocalDate, Integer> porDia = new LinkedHashMap<>();
        for (Pedido pedido : pedidos) {
            porDia.merge(pedido.getFechaRegistro().toLocalDate(), 1, Integer::sum);
        }

        System.out.println();
        System.out.println("Demanda por dia:");
        for (Map.Entry<LocalDate, Integer> dia : porDia.entrySet()) {
            System.out.printf("  %s  %3d pedidos%n", dia.getKey(), dia.getValue());
        }
    }

    /**
     * Compara los dos algoritmos que pide el caso, mostrando ademas de cuanto parte la tabu.
     *
     * Son independientes: la tabu no arranca del resultado de GRASP sino de su propio
     * constructivo, asi que las dos columnas se pueden comparar entre si.
     */
    public static void compararAlgoritmos(Solucion grasp, Solucion inicial, Solucion tabu) {
        System.out.println();
        System.out.println("=== COMPARACION ===");
        linea("GRASP", grasp);
        linea("Vecino mas cercano (partida de la tabu)", inicial);
        linea("Busqueda Tabu", tabu);

        System.out.printf(
                "%nLa tabu mejora su propia partida en %s%n",
                variacion(inicial.getCostoTotal(), tabu.getCostoTotal()));
        System.out.printf(
                "Busqueda Tabu frente a GRASP: %s%n",
                variacion(grasp.getCostoTotal(), tabu.getCostoTotal()));
    }

    private static void linea(String nombre, Solucion solucion) {
        System.out.printf(
                "  %-42s S/ %9.2f | %7.2f km | %2d viajes | %d sin asignar%n",
                nombre,
                solucion.getCostoTotal(),
                solucion.getDistanciaTotalKm(),
                solucion.getCantidadRutas(),
                solucion.getCantidadPedidosNoAsignados()
        );
    }

    private static String variacion(double desde, double hasta) {
        double diferencia = desde - hasta;
        double porcentaje = desde == 0.0 ? 0.0 : 100.0 * diferencia / desde;
        return String.format("S/ %.2f (%.1f%%)", diferencia, porcentaje);
    }
}
