package com.paqrap.modelo;

/**
 * Valores tomados directamente del caso PaqRap.
 */
public enum TipoVehiculo {
    AUTO(24, 40.0, 8.0),
    MOTO(8, 25.0, 6.0),
    BICICLETA(4, 12.0, 3.0);

    private final int capacidadPaquetes;
    private final double velocidadKmH;
    private final double costoPorKm;

    TipoVehiculo(int capacidadPaquetes, double velocidadKmH, double costoPorKm) {
        this.capacidadPaquetes = capacidadPaquetes;
        this.velocidadKmH = velocidadKmH;
        this.costoPorKm = costoPorKm;
    }

    public int getCapacidadPaquetes() {
        return capacidadPaquetes;
    }

    public double getVelocidadKmH() {
        return velocidadKmH;
    }

    public double getCostoPorKm() {
        return costoPorKm;
    }
}
