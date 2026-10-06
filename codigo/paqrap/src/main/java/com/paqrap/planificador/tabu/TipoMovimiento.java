package com.paqrap.planificador.tabu;

/**
 * Movimientos que generan la vecindad de una solución.
 *
 * Los tres primeros actúan sobre las rutas (qué se entrega y en qué orden) y los tres
 * últimos sobre las asignaciones (qué unidad y qué almacén atienden cada ruta, y qué
 * pedidos pendientes entran al plan).
 */
public enum TipoMovimiento {
    /** Cambiar un pedido de ruta o de posición dentro de su ruta. */
    TRASLADAR,
    /** Intercambiar dos pedidos entre rutas distintas. */
    INTERCAMBIAR,
    /** Invertir un tramo de la secuencia de entregas de una ruta (2-opt). */
    INVERTIR,
    /** Cambiar la unidad que realiza el viaje. */
    CAMBIAR_VEHICULO,
    /** Cambiar el almacén en el que carga el viaje. */
    CAMBIAR_ALMACEN,
    /** Incorporar al plan un pedido que había quedado sin asignar. */
    ASIGNAR_PENDIENTE
}
