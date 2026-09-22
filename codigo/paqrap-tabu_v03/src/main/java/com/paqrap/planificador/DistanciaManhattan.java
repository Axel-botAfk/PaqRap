package com.paqrap.planificador;

import com.paqrap.modelo.Ubicacion;

import java.time.LocalDateTime;

/**
 * Recorrido más corto sobre la retícula con la ciudad despejada.
 *
 * Con calles de doble sentido, sin diagonales y sin tramos cerrados, el camino más corto entre
 * dos nodos mide exactamente la distancia Manhattan, de modo que no hace falta explorar el
 * grafo. Sirve para los escenarios sin bloqueos y como cota inferior del recorrido real.
 */
public final class DistanciaManhattan implements CalculadorDistancia {

    @Override
    public double calcularKm(
            Ubicacion origen,
            Ubicacion destino,
            LocalDateTime instanteSalida,
            double velocidadKmH
    ) {
        return origen.distanciaManhattanKm(destino);
    }
}
