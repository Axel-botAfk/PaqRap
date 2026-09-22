package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Intervalo de tiempo durante el cual una unidad de transporte no puede
 * ser programada para una ruta, sin importar el motivo (avería o
 * mantenimiento preventivo).
 *
 * El intervalo es medio abierto: incluye {@code inicio}, excluye {@code fin}.
 */
public record VentanaIndisponibilidad(
        LocalDateTime inicio,
        LocalDateTime fin,
        TipoIndisponibilidad motivo
) {
    public VentanaIndisponibilidad {
        Objects.requireNonNull(inicio, "El inicio de la ventana es obligatorio.");
        Objects.requireNonNull(fin, "El fin de la ventana es obligatorio.");
        Objects.requireNonNull(motivo, "El motivo de la ventana es obligatorio.");
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("El fin de la ventana debe ser posterior al inicio.");
        }
    }

    /** true si el instante dado cae dentro de esta ventana. */
    public boolean incluye(LocalDateTime instante) {
        return !instante.isBefore(inicio) && instante.isBefore(fin);
    }

    /**
     * true si el rango [desde, hasta] se superpone en algún punto con esta ventana.
     * Se usa para rechazar una ruta cuyo tramo de viaje cruza un período de
     * indisponibilidad del vehículo, no solo su instante de llegada.
     */
    public boolean seSuperponeCon(LocalDateTime desde, LocalDateTime hasta) {
        return desde.isBefore(fin) && hasta.isAfter(inicio);
    }
}
