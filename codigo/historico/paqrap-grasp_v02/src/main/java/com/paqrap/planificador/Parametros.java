package com.paqrap.planificador;

/**
 * Parámetros mínimos indicados por el pseudocódigo/ISA para GRASP.
 */
public final class Parametros {
    private final int maxIteraciones;
    private final double alfa;
    private final long semilla;

    public Parametros(int maxIteraciones, double alfa, long semilla) {
        if (maxIteraciones <= 0) {
            throw new IllegalArgumentException("maxIteraciones debe ser mayor que cero.");
        }
        if (alfa < 0.0 || alfa > 1.0) {
            throw new IllegalArgumentException("alfa debe estar entre 0 y 1.");
        }
        this.maxIteraciones = maxIteraciones;
        this.alfa = alfa;
        this.semilla = semilla;
    }

    public int getMaxIteraciones() {
        return maxIteraciones;
    }

    public double getAlfa() {
        return alfa;
    }

    public long getSemilla() {
        return semilla;
    }
}
