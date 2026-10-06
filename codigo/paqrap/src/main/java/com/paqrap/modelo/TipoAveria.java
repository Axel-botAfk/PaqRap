package com.paqrap.modelo;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Los tres tipos de avería del caso.
 *
 * Una avería es la no disponibilidad de una unidad durante un periodo. Cada tipo define dos
 * tiempos distintos que conviene no confundir:
 *
 * <ul>
 *   <li><b>Permanencia en el lugar</b>: cuánto se queda la unidad donde se averió. Es la ventana
 *       durante la cual otra unidad puede acercarse a trasvasarle la carga.</li>
 *   <li><b>Reingreso</b>: cuándo vuelve a estar disponible para recibir rutas. Siempre es igual
 *       o posterior al fin de la permanencia.</li>
 * </ul>
 *
 * En los tipos 2 y 3 la unidad se traslada al almacén central al terminar su permanencia, y con
 * ella los paquetes que no alcanzaron a trasvasarse. El traslado es instantáneo por convención
 * del curso: no consume tiempo ni kilómetros.
 */
public enum TipoAveria {

    /**
     * Menor. Se resuelve en el sitio en dos horas —una llanta que se desinfla— y la unidad
     * retoma su ruta desde donde estaba, sin pasar por ningún almacén.
     */
    TIPO_1(Duration.ofHours(2), false),

    /**
     * Intermedia. La unidad permanece hasta cuatro horas en el lugar y vuelve a estar disponible
     * al terminar el turno siguiente a aquel en que se averió. Ejemplo del caso: se rompe la
     * transmisión.
     */
    TIPO_2(Duration.ofHours(4), true),

    /**
     * Mayor. La unidad permanece cuatro horas en el lugar y queda fuera al menos dos días por
     * mantenimiento, reingresando en el turno de las 15:00.
     */
    TIPO_3(Duration.ofHours(4), true);

    /** Días mínimos fuera de servicio de una avería mayor. */
    private static final int DIAS_DE_TALLER_TIPO_3 = 2;

    /** Turno en que reingresa una unidad con avería mayor. */
    private static final int HORA_DE_REINGRESO_TIPO_3 = 15;

    private final Duration permanenciaEnElLugar;
    private final boolean regresaAlCentral;

    TipoAveria(Duration permanenciaEnElLugar, boolean regresaAlCentral) {
        this.permanenciaEnElLugar = permanenciaEnElLugar;
        this.regresaAlCentral = regresaAlCentral;
    }

    /** Cuánto se queda la unidad donde se averió; es la ventana para trasvasarle la carga. */
    public Duration permanenciaEnElLugar() {
        return permanenciaEnElLugar;
    }

    /**
     * ¿La unidad termina en el almacén central?
     *
     * Solo los tipos 2 y 3. El tipo 1 se arregla donde está y sigue desde ahí, de modo que su
     * posición no cambia.
     */
    public boolean regresaAlCentral() {
        return regresaAlCentral;
    }

    /** Instante en que la unidad vuelve a poder recibir rutas. */
    public LocalDateTime reingreso(LocalDateTime instanteDeLaAveria) {
        return switch (this) {
            case TIPO_1 -> instanteDeLaAveria.plus(permanenciaEnElLugar);
            case TIPO_2 -> Turnos.finDelTurnoSiguienteA(instanteDeLaAveria);
            case TIPO_3 -> reingresoTrasElTaller(instanteDeLaAveria);
        };
    }

    /**
     * Dos días como mínimo, y además hay que esperar al turno de las 15:00: si al cumplirse los
     * dos días ya pasó esa hora, el reingreso se corre al día siguiente.
     */
    private static LocalDateTime reingresoTrasElTaller(LocalDateTime instanteDeLaAveria) {
        LocalDateTime minimo = instanteDeLaAveria.plusDays(DIAS_DE_TALLER_TIPO_3);
        LocalDateTime reingreso = minimo.toLocalDate().atTime(HORA_DE_REINGRESO_TIPO_3, 0);
        return reingreso.isBefore(minimo) ? reingreso.plusDays(1) : reingreso;
    }
}
