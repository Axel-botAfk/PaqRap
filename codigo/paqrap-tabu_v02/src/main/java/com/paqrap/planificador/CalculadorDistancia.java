package com.paqrap.planificador;

import com.paqrap.modelo.Ubicacion;

@FunctionalInterface
public interface CalculadorDistancia {
    double calcularKm(Ubicacion origen, Ubicacion destino);
}
