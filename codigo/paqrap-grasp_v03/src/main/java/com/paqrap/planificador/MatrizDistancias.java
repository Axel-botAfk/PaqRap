package com.paqrap.planificador;

import com.paqrap.modelo.Ubicacion;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementación de CalculadorDistancia basada en una matriz en memoria.
 * Las calles del caso son de doble sentido, por lo que registra las distancias
 * de forma simétrica.
 *
 * Puede reemplazarse por un adaptador al grafo real sin cambiar la lógica de GRASP.
 */
public final class MatrizDistancias implements CalculadorDistancia {
    private final Map<String, Double> distancias = new HashMap<>();

    public void registrar(Ubicacion a, Ubicacion b, double km) {
        if (km < 0) {
            throw new IllegalArgumentException("La distancia no puede ser negativa.");
        }
        distancias.put(clave(a, b), km);
        distancias.put(clave(b, a), km);
    }

    @Override
    public double calcularKm(Ubicacion origen, Ubicacion destino) {
        if (origen.equals(destino)) {
            return 0.0;
        }

        Double distancia = distancias.get(clave(origen, destino));
        if (distancia == null) {
            throw new IllegalStateException(
                    "No existe distancia registrada entre "
                            + origen.getId() + " y " + destino.getId());
        }
        return distancia;
    }

    private String clave(Ubicacion a, Ubicacion b) {
        return a.getId() + "->" + b.getId();
    }
}
