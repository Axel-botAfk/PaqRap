package com.paqrap.modelo;

/**
 * Situación de una unidad de transporte frente al planificador.
 *
 * Las averías y el mantenimiento preventivo dejan a la unidad fuera de servicio por un
 * periodo; por ahora el planificador solo distingue si puede o no comprometerla en una ruta.
 * El instante de reingreso se incorporará junto con la replanificación.
 */
public enum EstadoVehiculo {
    /** En un almacén, sin ruta asignada. */
    DISPONIBLE,
    /** Con una ruta en curso. */
    EN_RUTA,
    /** Fuera de servicio por avería (tipo 1, 2 o 3). */
    AVERIADO,
    /** Fuera de servicio por mantenimiento preventivo programado. */
    EN_MANTENIMIENTO;

    /** Solo una unidad disponible puede recibir una ruta nueva en esta iteración. */
    public boolean admiteAsignacion() {
        return this == DISPONIBLE;
    }
}
