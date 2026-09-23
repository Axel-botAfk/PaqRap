package com.paqrap.modelo;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Turnos de conducción y refrigerio.
 *
 * <h2>Por qué el cambio de turno no aparece por ningún lado</h2>
 *
 * El relevo no detiene a la unidad: el conductor entrante la alcanza donde esté y el cambio es
 * despreciable frente a los tiempos de recorrido. Así que un turno no obliga a volver al
 * almacén, ni corta una ruta en curso, ni limita cuántas horas seguidas puede operar el
 * vehículo. La unidad trabaja de corrido; lo que rota es quién la conduce.
 *
 * Por eso aquí no hay ningún concepto de conductor ni de asignación de turnos. Sería estado sin
 * efecto sobre ninguna decisión del planificador.
 *
 * <h2>Los turnos del caso</h2>
 *
 * Tres de ocho horas: 07:00-15:00, 15:00-23:00 y 23:00-07:00. No son una convención interna;
 * las averías se definen contra ellos —el tipo 2 vuelve al final del turno siguiente y el tipo 3
 * reingresa en el de las 15:00— así que mover estos límites cambia esas reglas.
 *
 * <h2>Lo que sí detiene a la unidad</h2>
 *
 * El refrigerio. El conductor come una hora por turno y durante esa hora el vehículo no avanza,
 * de modo que <b>cada turno de ocho horas rinde siete de operación</b>.
 *
 * El caso exige que esa hora caiga <b>dentro</b> de la jornada y <b>al menos una hora antes o
 * una hora después de un cambio de turno</b>, así que no puede ir pegada al relevo. Se fija a
 * las cuatro horas de iniciado el turno —11:00, 19:00 y 03:00—, que deja cuatro horas de margen
 * con el cambio anterior y tres con el siguiente.
 *
 * El caso no dice la hora exacta y para el dimensionamiento de la flota da igual: lo que importa
 * es que de cada ocho horas se pierda una. Fijarla al medio del turno es la elección que cumple
 * la restricción con más holgura por los dos lados.
 *
 * <h2>Cómo se usa</h2>
 *
 * Todo avance de reloj que ocupe a la unidad —conducir o acondicionar en casa del cliente— pasa
 * por {@link #avanzar}, que devuelve el instante real de término incluyendo los refrigerios que
 * se hayan cruzado en el medio. Los plazos comprometidos con el cliente se siguen verificando
 * contra ese instante, de modo que el refrigerio puede volver infactible una entrega que sin él
 * llegaba: es justamente el efecto que hay que capturar.
 */
public final class Turnos {
    /** Hora a la que empieza el primer turno del día. */
    public static final int HORA_DE_INICIO = 7;

    /** Turnos del caso: 07:00-15:00, 15:00-23:00 y 23:00-07:00. */
    public static final int HORAS_POR_TURNO = 8;

    /** Lo que el conductor se detiene a comer, una vez por turno. */
    public static final double HORAS_DE_REFRIGERIO = 1.0;

    /**
     * A qué altura del turno se toma el refrigerio: 11:00, 19:00 y 03:00.
     *
     * No puede ser el instante del relevo. El caso pide que la hora de alimentación quede a una
     * hora o más de un cambio de turno, y a la mitad de la jornada queda a cuatro del anterior y
     * a tres del siguiente.
     */
    public static final double HORAS_HASTA_EL_REFRIGERIO = 4.0;

    /** Horas de operación efectiva que rinde cada turno. */
    public static final double HORAS_UTILES_POR_TURNO = HORAS_POR_TURNO - HORAS_DE_REFRIGERIO;

    private static final long SEGUNDOS_POR_TURNO = HORAS_POR_TURNO * 3600L;
    private static final long SEGUNDOS_HASTA_EL_REFRIGERIO =
            Math.round((HORA_DE_INICIO + HORAS_HASTA_EL_REFRIGERIO) * 3600);
    private static final long NANOS_POR_HORA = 3_600_000_000_000L;

    private Turnos() {
    }

    /**
     * Instante en que la unidad termina una ocupación de la duración indicada, sumando el
     * refrigerio de cada turno que se cruce.
     *
     * El cálculo se repite hasta estabilizarse porque el propio refrigerio alarga el intervalo y
     * puede empujarlo dentro del turno siguiente, que trae otro refrigerio. Converge siempre:
     * cada vuelta solo puede añadir cruces, y la cantidad de turnos que caben en un intervalo
     * finito está acotada.
     *
     * @param desde             instante en que arranca la ocupación.
     * @param horasDeOcupacion  tiempo de trabajo puro, sin refrigerios.
     */
    public static LocalDateTime avanzar(LocalDateTime desde, double horasDeOcupacion) {
        if (horasDeOcupacion <= 0) {
            return desde;
        }

        long cruces = 0;
        while (true) {
            LocalDateTime candidato = mas(
                    desde, horasDeOcupacion + cruces * HORAS_DE_REFRIGERIO);
            long observados = refrigeriosCruzados(desde, candidato);
            if (observados == cruces) {
                return candidato;
            }
            cruces = observados;
        }
    }

    /** Cuánto refrigerio se come una ocupación, para informarlo por separado. */
    public static Duration refrigerioEntre(LocalDateTime desde, double horasDeOcupacion) {
        LocalDateTime sinPausa = mas(desde, horasDeOcupacion);
        return Duration.between(sinPausa, avanzar(desde, horasDeOcupacion));
    }

    /** Refrigerios que caen dentro del intervalo (desde, hasta]. */
    private static long refrigeriosCruzados(LocalDateTime desde, LocalDateTime hasta) {
        return indiceDeRefrigerio(hasta) - indiceDeRefrigerio(desde);
    }

    /** Número del refrigerio ya pasado en ese instante, contado desde la época. */
    private static long indiceDeRefrigerio(LocalDateTime instante) {
        return Math.floorDiv(segundosDesdeLaEpoca(instante) - SEGUNDOS_HASTA_EL_REFRIGERIO,
                SEGUNDOS_POR_TURNO);
    }

    /** Instante en que el conductor del turno indicado se detiene a comer. */
    public static LocalDateTime refrigerioDelTurno(long indice) {
        return inicioDelTurno(indice).plusNanos(
                Math.round(HORAS_HASTA_EL_REFRIGERIO * NANOS_POR_HORA));
    }

    /**
     * Número de turno absoluto, contado desde la época. Dos instantes con el mismo número caen
     * en el mismo turno, y la diferencia entre dos números es cuántos cambios hay en medio.
     */
    public static long indiceDeTurno(LocalDateTime instante) {
        return Math.floorDiv(segundosDesdeLaEpoca(instante) - HORA_DE_INICIO * 3600L,
                SEGUNDOS_POR_TURNO);
    }

    private static long segundosDesdeLaEpoca(LocalDateTime instante) {
        return instante.toLocalDate().toEpochDay() * 86_400L
                + instante.toLocalTime().toSecondOfDay();
    }

    /** Instante en que empieza el turno indicado. */
    public static LocalDateTime inicioDelTurno(long indice) {
        long segundos = indice * SEGUNDOS_POR_TURNO + HORA_DE_INICIO * 3600L;
        return LocalDateTime.ofEpochSecond(segundos, 0, java.time.ZoneOffset.UTC);
    }

    /** Instante en que termina el turno al que pertenece el instante dado. */
    public static LocalDateTime finDelTurnoDe(LocalDateTime instante) {
        return inicioDelTurno(indiceDeTurno(instante) + 1);
    }

    /**
     * Fin del turno <b>siguiente</b> al que contiene el instante dado. Es hasta cuándo queda
     * fuera de servicio una unidad con avería de tipo 2.
     */
    public static LocalDateTime finDelTurnoSiguienteA(LocalDateTime instante) {
        return inicioDelTurno(indiceDeTurno(instante) + 2);
    }

    private static LocalDateTime mas(LocalDateTime instante, double horas) {
        return instante.plusNanos(Math.round(horas * NANOS_POR_HORA));
    }
}
