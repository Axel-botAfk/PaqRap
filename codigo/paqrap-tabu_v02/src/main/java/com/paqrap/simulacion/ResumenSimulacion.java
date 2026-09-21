package com.paqrap.simulacion;

import com.paqrap.modelo.Pedido;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Resultado de una corrida: qué tanto se atendió, cuánto costó y si la operación colapsó.
 *
 * El colapso logístico se declara cuando vence el plazo de un pedido sin que se haya
 * entregado. Con los planes que produce el planificador —que solo acepta entregas dentro del
 * plazo— eso ocurre cuando la flota ya no alcanza a comprometerse con un pedido y su fecha
 * límite pasa mientras sigue esperando.
 */
public record ResumenSimulacion(
        LocalDateTime inicio,
        LocalDateTime fin,
        int pedidosRecibidos,
        int pedidosEntregados,
        int pedidosPendientes,
        int entregasEnPlazo,
        double distanciaTotalKm,
        double costoTotal,
        int iteracionesDePlanificacion,
        long milisegundosDeComputo,
        Pedido pedidoQueColapso,
        LocalDateTime instanteDelColapso,
        List<Pedido> vencidos,
        Map<LocalDate, Integer> recibidosPorDia,
        Map<LocalDate, Integer> entregasPorDia,
        Map<LocalDate, Integer> colaAlCierreDelDia
) {
    public boolean huboColapso() {
        return pedidoQueColapso != null;
    }

    public double porcentajeAtendido() {
        return pedidosRecibidos == 0 ? 100.0 : 100.0 * pedidosEntregados / pedidosRecibidos;
    }

    /** Entregas diarias sostenidas: el techo real de la flota con este planificador. */
    public double entregasDiariasPromedio() {
        if (entregasPorDia().isEmpty()) {
            return 0.0;
        }
        int total = 0;
        for (int entregas : entregasPorDia().values()) {
            total += entregas;
        }
        return (double) total / entregasPorDia().size();
    }

    public double milisegundosPorIteracion() {
        return iteracionesDePlanificacion == 0
                ? 0.0
                : (double) milisegundosDeComputo / iteracionesDePlanificacion;
    }
}
