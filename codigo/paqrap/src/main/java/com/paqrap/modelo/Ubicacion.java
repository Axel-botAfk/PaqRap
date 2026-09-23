package com.paqrap.modelo;

/**
 * Nodo de la retícula: una esquina identificada por sus coordenadas (x, y) en kilómetros.
 *
 * Los clientes, los almacenes y las unidades de transporte se refieren siempre a un nodo.
 * Sustituye al identificador lógico de la primera iteración, que impedía calcular distancias
 * sin una tabla cargada a mano.
 */
public record Ubicacion(int x, int y) {

    public Ubicacion {
        if (!Ciudad.contiene(x, y)) {
            throw new IllegalArgumentException(
                    "La ubicación (" + x + "," + y + ") está fuera de la ciudad "
                            + Ciudad.ANCHO_KM + "x" + Ciudad.ALTO_KM + ".");
        }
    }

    /**
     * Distancia sobre la retícula sin considerar bloqueos. Con calles de doble sentido y sin
     * diagonales, el recorrido más corto entre dos nodos libres es la suma de los catetos.
     */
    public double distanciaManhattanKm(Ubicacion otra) {
        return (Math.abs(x - otra.x) + Math.abs(y - otra.y)) * Ciudad.KM_POR_ARISTA;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
