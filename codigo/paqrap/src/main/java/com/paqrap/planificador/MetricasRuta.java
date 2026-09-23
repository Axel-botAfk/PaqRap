package com.paqrap.planificador;

import com.paqrap.modelo.Ubicacion;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Resultado de evaluar un viaje: si respeta los plazos, cuánto recorre, cuánto cuesta, a qué
 * hora <b>llega</b> a cada destinatario y en qué situación deja a la unidad.
 *
 * Se registra la hora de llegada y no la de fin de la entrega porque el plazo comprometido con
 * el cliente se mide contra la llegada: la hora de acondicionamiento dentro de las instalaciones
 * del cliente queda fuera del plazo, aunque sí ocupe a la unidad.
 *
 * {@link #horaCarga()}, {@link #horaFin()} y {@link #posicionFinal()} son lo que permite
 * encadenar el siguiente viaje de la misma unidad. Cuando el viaje no es factible, solo
 * {@link #factible()} tiene significado.
 */
public record MetricasRuta(
        boolean factible,
        double distanciaTotalKm,
        double costoTotal,
        double duracionHoras,
        LocalDateTime horaCarga,
        LocalDateTime horaFin,
        Ubicacion posicionFinal,
        Map<String, LocalDateTime> horasLlegada
) {
    public static MetricasRuta infactible() {
        return new MetricasRuta(false, 0.0, 0.0, 0.0, null, null, null, Map.of());
    }

    /** Viaje sin entregas: la unidad no se mueve ni gasta. */
    public static MetricasRuta sinEntregas(Ubicacion posicion, LocalDateTime instante) {
        return new MetricasRuta(true, 0.0, 0.0, 0.0, instante, instante, posicion, Map.of());
    }
}
