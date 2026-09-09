package com.paqrap.modelo;

import java.util.Objects;

public final class Vehiculo {
    private final String id;
    private final TipoVehiculo tipo;
    private final EstadoVehiculo estado;
    private final Ubicacion ubicacion;

    public Vehiculo(
            String id,
            TipoVehiculo tipo,
            EstadoVehiculo estado,
            Ubicacion ubicacion
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id del vehículo es obligatorio.");
        }
        this.id = id;
        this.tipo = Objects.requireNonNull(tipo, "El tipo de vehículo es obligatorio.");
        this.estado = Objects.requireNonNull(estado, "El estado del vehículo es obligatorio.");
        this.ubicacion = Objects.requireNonNull(ubicacion, "La ubicación del vehículo es obligatoria.");
    }

    public String getId() {
        return id;
    }

    public TipoVehiculo getTipo() {
        return tipo;
    }

    public EstadoVehiculo getEstado() {
        return estado;
    }

    public Ubicacion getUbicacion() {
        return ubicacion;
    }

    public int getCapacidad() {
        return tipo.getCapacidadPaquetes();
    }

    public double getVelocidadKmH() {
        return tipo.getVelocidadKmH();
    }

    public double getCostoPorKm() {
        return tipo.getCostoPorKm();
    }

    @Override
    public String toString() {
        return id + "(" + tipo + ")";
    }
}
