package com.paqrap.planificador;

import com.paqrap.modelo.TipoIndisponibilidad;
import com.paqrap.modelo.VentanaIndisponibilidad;

import java.time.LocalDateTime;

/**
 * Calcula la ventana de indisponibilidad de un vehículo según el tipo de
 * avería (pregunta 3 de Preguntas y Respuestas) y los turnos oficiales del
 * caso (Enunciado de la Situación Auténtica: cambios de turno cada 8 horas,
 * a las 07:00, 15:00 y 23:00).
 *
 * Reglas aplicadas:
 * - Avería Tipo 1 (menor): no disponible exactamente 2 horas.
 * - Avería Tipo 2 (intermedia): no disponible hasta el final del turno
 *   siguiente al que ocurrió la avería.
 * - Avería Tipo 3 (mayor): no disponible por al menos 2 días, y retorna a
 *   operación en el turno de 15:00 a 23:00 (es decir, exactamente a las
 *   15:00 del primer día en que ya se cumplieron los 2 días).
 *
 * Fuera de alcance por ahora (no afecta la duración de la ventana, pero sí
 * está pendiente si se quiere modelar con más detalle): el traslado
 * "instantáneo" al almacén central en averías Tipo 2 y 3, el trasvase de
 * paquetes no entregados (30 min, según Preguntas y Respuestas) y la
 * permanencia de hasta 4 horas en el lugar de la avería antes de ese
 * traslado. El modelo actual de GRASP no lleva un seguimiento de la
 * ubicación del vehículo en tiempo real dentro de la ruta, así que estos
 * detalles no cambian si el vehículo se excluye o no de la planificación
 * mientras dure su ventana de indisponibilidad, que es lo único que GRASP
 * necesita para respetar la regla.
 */
public final class CalculadorAveria {

    private CalculadorAveria() {
    }

    /**
     * @param tipo    AVERIA_TIPO_1, AVERIA_TIPO_2 o AVERIA_TIPO_3.
     * @param momento instante en el que ocurre la avería.
     * @return la ventana de indisponibilidad resultante, lista para agregarse
     * a un {@code Vehiculo}.
     */
    public static VentanaIndisponibilidad calcular(TipoIndisponibilidad tipo, LocalDateTime momento) {
        LocalDateTime fin = switch (tipo) {
            case AVERIA_TIPO_1 -> momento.plusHours(2);
            case AVERIA_TIPO_2 -> Turno.finDelSiguienteTurno(momento);
            case AVERIA_TIPO_3 -> finAveriaTipo3(momento);
            case MANTENIMIENTO_PREVENTIVO -> throw new IllegalArgumentException(
                    "El mantenimiento preventivo se calcula con LectorMantenimiento, no con CalculadorAveria.");
        };
        return new VentanaIndisponibilidad(momento, fin, tipo);
    }

    private static LocalDateTime finAveriaTipo3(LocalDateTime momento) {
        LocalDateTime minimoFin = momento.plusDays(2);
        LocalDateTime candidato = minimoFin.toLocalDate().atTime(15, 0);
        if (candidato.isBefore(minimoFin)) {
            candidato = candidato.plusDays(1);
        }
        return candidato;
    }
}
