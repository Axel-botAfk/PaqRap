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
}
