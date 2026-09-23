package com.paqrap.modelo;

/**
 * Tipos de unidad de transporte del caso, con el prefijo con el que se codifican sus
 * identificadores (TTNN: TA01, TM03, TB10).
 *
 * Los valores de capacidad, velocidad y costo son los del enunciado y solo sirven como punto
 * de partida: los vigentes durante la operación los administra {@link ConfiguracionFlota},
 * porque pueden cambiarse en caliente.
 */
public enum TipoVehiculo {
    AUTO("TA", new EspecificacionVehiculo(24, 40.0, 8.0)),
    MOTO("TM", new EspecificacionVehiculo(8, 25.0, 6.0)),
    BICICLETA("TB", new EspecificacionVehiculo(4, 12.0, 3.0));

    private final String prefijo;
    private final EspecificacionVehiculo especificacionDelCaso;

    TipoVehiculo(String prefijo, EspecificacionVehiculo especificacionDelCaso) {
        this.prefijo = prefijo;
        this.especificacionDelCaso = especificacionDelCaso;
    }

    public String getPrefijo() {
        return prefijo;
    }

    public EspecificacionVehiculo getEspecificacionDelCaso() {
        return especificacionDelCaso;
    }

    /** Identificador TTNN de la unidad número {@code numero} de este tipo. */
    public String codigoUnidad(int numero) {
        if (numero <= 0 || numero > 99) {
            throw new IllegalArgumentException("El correlativo de la unidad debe estar entre 1 y 99.");
        }
        return String.format("%s%02d", prefijo, numero);
    }

    public static TipoVehiculo porPrefijo(String prefijo) {
        for (TipoVehiculo tipo : values()) {
            if (tipo.prefijo.equalsIgnoreCase(prefijo)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Prefijo de tipo de unidad desconocido: " + prefijo);
    }
}
