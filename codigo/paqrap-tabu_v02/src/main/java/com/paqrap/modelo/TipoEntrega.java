package com.paqrap.modelo;

/**
 * Plazos definidos por el caso PaqRap.
 */
public enum TipoEntrega {
    REGULAR_36H(36),
    PRIORITARIA_4H(4),
    PRIORITARIA_8H(8),
    PRIORITARIA_12H(12),
    PRIORITARIA_18H(18);

    private final int horasPlazo;

    TipoEntrega(int horasPlazo) {
        this.horasPlazo = horasPlazo;
    }

    public int getHorasPlazo() {
        return horasPlazo;
    }
}
