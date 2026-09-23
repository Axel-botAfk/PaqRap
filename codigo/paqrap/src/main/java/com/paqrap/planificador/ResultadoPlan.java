package com.paqrap.planificador;

import java.util.List;

/**
 * Evaluación completa de un plan.
 *
 * {@code metricasPorRuta} viene alineada con la lista de viajes de la solución, de modo que el
 * viaje en la posición i tiene sus métricas en la posición i. Si el plan no es factible, las
 * métricas solo sirven para diagnosticar dónde falló.
 */
public record ResultadoPlan(boolean factible, double costoOperacion, List<MetricasRuta> metricasPorRuta) {

    public static ResultadoPlan infactible(List<MetricasRuta> metricas) {
        return new ResultadoPlan(false, Double.POSITIVE_INFINITY, metricas);
    }
}
