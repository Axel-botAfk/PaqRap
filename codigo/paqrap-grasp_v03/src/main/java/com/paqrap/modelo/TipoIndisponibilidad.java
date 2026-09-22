package com.paqrap.modelo;

/**
 * Motivo por el cual una unidad de transporte no puede ser programada
 * durante un intervalo de tiempo (Preguntas y Respuestas del caso, preguntas 3 y 19).
 */
public enum TipoIndisponibilidad {
    /** No disponible por 2 horas (p.e. una llanta se desinfla). */
    AVERIA_TIPO_1,
    /** No disponible hasta el final del siguiente turno al que ocurrió la avería. */
    AVERIA_TIPO_2,
    /** No disponible por al menos 2 días; retorna a operación en el turno 15:00-23:00. */
    AVERIA_TIPO_3,
    /** Mantenimiento preventivo programado (archivo mant.preventivo.m1.m2). */
    MANTENIMIENTO_PREVENTIVO
}
