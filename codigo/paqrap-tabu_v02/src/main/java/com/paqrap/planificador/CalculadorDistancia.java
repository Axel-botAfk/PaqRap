package com.paqrap.planificador;

import com.paqrap.modelo.Ubicacion;

import java.time.LocalDateTime;

/**
 * Distancia que recorre una unidad para ir de un nodo a otro.
 *
 * La consulta incluye el instante de salida y la velocidad de la unidad porque los bloqueos
 * de calles tienen vigencia horaria: el mismo par de nodos puede tener recorridos distintos
 * según cuándo se inicie el tramo, y la velocidad determina a qué hora la unidad pasa por
 * cada esquina, que es lo que decide si la encuentra abierta o cerrada.
 */
@FunctionalInterface
public interface CalculadorDistancia {

    /**
     * @param instanteSalida momento en que la unidad inicia el tramo.
     * @param velocidadKmH   velocidad de la unidad, para ubicar en el tiempo cada esquina.
     * @return kilómetros del recorrido más corto, o {@link Double#POSITIVE_INFINITY} si el
     *         destino no es alcanzable sin atravesar un tramo cerrado.
     */
    double calcularKm(
            Ubicacion origen,
            Ubicacion destino,
            LocalDateTime instanteSalida,
            double velocidadKmH
    );

    /**
     * Hasta cuándo se puede llegar al nodo sin encontrarlo cerrado.
     *
     * Es lo que permite priorizar por el tope que de verdad manda. El plazo del cliente no es
     * el único: si la esquina del destino se cierra antes, el pedido se queda sin forma de ser
     * entregado aunque le sobren horas de plazo.
     *
     * @param hasta tope de la búsqueda; más allá no interesa.
     * @return el instante del cierre, o {@code null} si el acceso sigue abierto hasta el tope.
     *         Con la ciudad despejada nunca se cierra.
     */
    default LocalDateTime finDelAccesoA(
            Ubicacion destino,
            LocalDateTime desde,
            LocalDateTime hasta
    ) {
        return null;
    }
}
