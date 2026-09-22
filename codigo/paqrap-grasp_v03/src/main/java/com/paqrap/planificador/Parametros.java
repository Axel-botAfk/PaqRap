package com.paqrap.planificador;

/**
 * Parámetros mínimos indicados por el pseudocódigo/ISA para GRASP.
 */
public final class Parametros {
    /**
     * Valor por defecto del tiempo de acondicionamiento (pregunta 14 del caso):
     * 1 hora por entrega, sin importar la cantidad de productos.
     */
    public static final double HORAS_ATENCION_POR_DEFECTO = 1.0;

    private final int maxIteraciones;
    private final double alfa;
    private final long semilla;
    private final double horasAtencionPorDestinatario;

    /** Constructor original: usa 1 hora de atención por destinatario (valor del caso). */
    public Parametros(int maxIteraciones, double alfa, long semilla) {
        this(maxIteraciones, alfa, semilla, HORAS_ATENCION_POR_DEFECTO);
    }

    /**
     * Constructor completo. {@code horasAtencionPorDestinatario} es configurable para el
     * diseño de experimentos (IEN), pero el valor del caso es 1 hora (pregunta 14).
     */
    public Parametros(int maxIteraciones, double alfa, long semilla, double horasAtencionPorDestinatario) {
        if (maxIteraciones <= 0) {
            throw new IllegalArgumentException("maxIteraciones debe ser mayor que cero.");
        }
        if (alfa < 0.0 || alfa > 1.0) {
            throw new IllegalArgumentException("alfa debe estar entre 0 y 1.");
        }
        if (horasAtencionPorDestinatario < 0.0) {
            throw new IllegalArgumentException("horasAtencionPorDestinatario no puede ser negativo.");
        }
        this.maxIteraciones = maxIteraciones;
        this.alfa = alfa;
        this.semilla = semilla;
        this.horasAtencionPorDestinatario = horasAtencionPorDestinatario;
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

    public double getHorasAtencionPorDestinatario() {
        return horasAtencionPorDestinatario;
    }
}
