package com.paqrap.planificador;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Resultado de evaluar una secuencia de entregas: si respeta los plazos, cuánto recorre,
 * cuánto cuesta y a qué hora se atiende a cada destinatario.
 */
public record MetricasRuta(
        boolean factible,
        double distanciaTotalKm,
        double costoTotal,
        double duracionHoras,
        Map<String, LocalDateTime> horasEntrega
) {
    public static MetricasRuta infactible() {
        return new MetricasRuta(false, 0.0, 0.0, 0.0, Map.of());
    }
}
