package com.paqrap.modelo;

/**
 * Retícula sobre la que opera PaqRap.
 *
 * El caso define una ciudad rectangular de 70 km en el eje X por 50 km en el eje Y,
 * conceptualizada como nodos (esquinas) separados 1 km. No hay calles diagonales ni curvas
 * y todas son de doble sentido, de modo que la distancia entre dos nodos alcanzables es la
 * distancia Manhattan. El origen de coordenadas (0,0) está en la esquina inferior izquierda.
 *
 * Los extremos se consideran incluidos: un lado de 70 km con nodos cada kilómetro contiene
 * los nodos 0..70.
 */
public final class Ciudad {
    public static final int ANCHO_KM = 70;
    public static final int ALTO_KM = 50;
    public static final double KM_POR_ARISTA = 1.0;

    private Ciudad() {
    }

    public static boolean contiene(int x, int y) {
        return x >= 0 && x <= ANCHO_KM && y >= 0 && y <= ALTO_KM;
    }

    /** Mayor distancia posible entre dos nodos, útil para acotar vecindarios. */
    public static double distanciaMaximaKm() {
        return (ANCHO_KM + ALTO_KM) * KM_POR_ARISTA;
    }
}
