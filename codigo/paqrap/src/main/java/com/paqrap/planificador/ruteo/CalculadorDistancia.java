package com.paqrap.planificador.ruteo;

import com.paqrap.modelo.Ubicacion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
     * Esquinas que recorre la unidad, en orden, desde el origen hasta el destino.
     *
     * Hace falta para saber <b>dónde queda</b> una unidad que todavía va en camino cuando llega
     * el corte del simulador: sin el camino solo se sabe cuánto mide el tramo, y la unidad
     * tendría que aparecer entera en un extremo o en el otro.
     *
     * Con la ciudad despejada el recorrido más corto es cualquier escalera entre los dos nodos;
     * se devuelve la que avanza primero en X y después en Y.
     *
     * @return el camino incluyendo los dos extremos, o una lista vacía si no es alcanzable.
     */
    default List<Ubicacion> camino(
            Ubicacion origen,
            Ubicacion destino,
            LocalDateTime instanteSalida,
            double velocidadKmH
    ) {
        List<Ubicacion> camino = new ArrayList<>();
        camino.add(origen);

        int x = origen.x();
        int y = origen.y();
        while (x != destino.x()) {
            x += Integer.signum(destino.x() - x);
            camino.add(new Ubicacion(x, y));
        }
        while (y != destino.y()) {
            y += Integer.signum(destino.y() - y);
            camino.add(new Ubicacion(x, y));
        }
        return camino;
    }

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
