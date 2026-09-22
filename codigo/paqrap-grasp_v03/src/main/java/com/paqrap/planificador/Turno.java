package com.paqrap.planificador;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Turnos de trabajo del caso PaqRap.
 *
 * Fuente: Enunciado de la Situación Auténtica (agosto 2026): "Los cambios de
 * turno se realizan cada 8 horas (07:00, 15:00, 23:00)". Es decir, hay 3
 * turnos diarios de 8 horas cada uno:
 *   Turno 1: 07:00 - 15:00
 *   Turno 2: 15:00 - 23:00
 *   Turno 3: 23:00 - 07:00 (del día siguiente)
 *
 * Se usa para calcular la duración de las averías Tipo 2 y Tipo 3 (pregunta
 * 3 de Preguntas y Respuestas), que dependen del turno en el que ocurre la
 * avería. También es la base para, más adelante, ubicar la hora de
 * refrigerio de los conductores (1 hora, dentro de la jornada, al menos una
 * hora antes o después de un cambio de turno), aunque esa parte todavía no
 * está implementada aquí.
 */
public final class Turno {

    private static final LocalTime[] LIMITES = {
            LocalTime.of(7, 0), LocalTime.of(15, 0), LocalTime.of(23, 0)
    };

    private Turno() {
    }

    /** Instante en que termina el turno vigente en el momento dado. */
    public static LocalDateTime finDelTurnoActual(LocalDateTime instante) {
        for (LocalTime limite : LIMITES) {
            LocalDateTime candidato = instante.toLocalDate().atTime(limite);
            if (instante.isBefore(candidato)) {
                return candidato;
            }
        }
        // Ya se pasaron las 23:00: el turno vigente (23:00-07:00) termina al
        // dia siguiente a las 07:00.
        return instante.toLocalDate().plusDays(1).atTime(LIMITES[0]);
    }

    /**
     * Instante en que termina el turno siguiente al vigente. Como los 3
     * turnos duran exactamente 8 horas cada uno, basta con sumar 8 horas al
     * fin del turno actual.
     */
    public static LocalDateTime finDelSiguienteTurno(LocalDateTime instante) {
        return finDelTurnoActual(instante).plusHours(8);
    }
}
