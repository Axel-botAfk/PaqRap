package com.paqrap.modelo;

/**
 * Valores operativos de un tipo de unidad de transporte.
 *
 * Se separan del enum {@link TipoVehiculo} porque el caso exige poder cambiarlos por
 * parámetros mientras el software está en ejecución.
 */
public record EspecificacionVehiculo(int capacidadPaquetes, double velocidadKmH, double costoPorKm) {

    public EspecificacionVehiculo {
        if (capacidadPaquetes <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser mayor que cero.");
        }
        if (velocidadKmH <= 0.0) {
            throw new IllegalArgumentException("La velocidad debe ser mayor que cero.");
        }
        if (costoPorKm <= 0.0) {
            throw new IllegalArgumentException("El costo por kilómetro debe ser mayor que cero.");
        }
    }

    public EspecificacionVehiculo conVelocidad(double nuevaVelocidadKmH) {
        return new EspecificacionVehiculo(capacidadPaquetes, nuevaVelocidadKmH, costoPorKm);
    }

    public EspecificacionVehiculo conCosto(double nuevoCostoPorKm) {
        return new EspecificacionVehiculo(capacidadPaquetes, velocidadKmH, nuevoCostoPorKm);
    }

    public EspecificacionVehiculo conCapacidad(int nuevaCapacidad) {
        return new EspecificacionVehiculo(nuevaCapacidad, velocidadKmH, costoPorKm);
    }
}
