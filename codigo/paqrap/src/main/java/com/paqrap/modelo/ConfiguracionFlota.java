package com.paqrap.modelo;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Valores de capacidad, velocidad y costo vigentes para cada tipo de unidad.
 *
 * El caso pide que estos parámetros puedan cambiarse con el software en funcionamiento y
 * aclara que el cambio es por tipo de unidad, no por unidad individual, y que aplica desde
 * la siguiente iteración de planificación. Por eso existe una configuración compartida
 * ({@link #porDefecto()}) que las unidades consultan en cada evaluación, en lugar de copiar
 * los valores al construirse.
 */
public final class ConfiguracionFlota {
    private static final ConfiguracionFlota COMPARTIDA = new ConfiguracionFlota();

    private final Map<TipoVehiculo, EspecificacionVehiculo> especificaciones =
            new EnumMap<>(TipoVehiculo.class);

    private ConfiguracionFlota() {
        restaurarValoresDelCaso();
    }

    /**
     * Configuración que usa toda la operación. Es mutable a propósito: un cambio en caliente
     * debe alcanzar a las unidades ya creadas.
     */
    public static ConfiguracionFlota porDefecto() {
        return COMPARTIDA;
    }

    /** Configuración independiente, para experimentos que no deben alterar la operación. */
    public static ConfiguracionFlota nueva() {
        return new ConfiguracionFlota();
    }

    public void restaurarValoresDelCaso() {
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            especificaciones.put(tipo, tipo.getEspecificacionDelCaso());
        }
    }

    public EspecificacionVehiculo de(TipoVehiculo tipo) {
        return especificaciones.get(Objects.requireNonNull(tipo));
    }

    public void cambiarVelocidad(TipoVehiculo tipo, double velocidadKmH) {
        especificaciones.put(tipo, de(tipo).conVelocidad(velocidadKmH));
    }

    public void cambiarCosto(TipoVehiculo tipo, double costoPorKm) {
        especificaciones.put(tipo, de(tipo).conCosto(costoPorKm));
    }

    public void cambiarCapacidad(TipoVehiculo tipo, int capacidadPaquetes) {
        especificaciones.put(tipo, de(tipo).conCapacidad(capacidadPaquetes));
    }
}
