package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Unidad de transporte. Su identificador sigue el formato TTNN del caso (TA01, TM03, TB10).
 *
 * Los valores operativos no se guardan aquí: se consultan a la {@link ConfiguracionFlota}
 * en cada uso, de modo que un cambio de velocidad en caliente alcance también a las unidades
 * ya creadas.
 *
 * Durante la operación la unidad se va moviendo, así que además de dónde está se registra
 * desde cuándo vuelve a estar libre: una unidad que quedó en camino no puede iniciar un viaje
 * nuevo antes de terminar lo que ya tenía comprometido.
 */
public final class Vehiculo {
    private final String id;
    private final TipoVehiculo tipo;
    private final EstadoVehiculo estado;
    private final Ubicacion ubicacion;
    private final LocalDateTime disponibleDesde;
    private final ConfiguracionFlota configuracion;

    public Vehiculo(
            String id,
            TipoVehiculo tipo,
            EstadoVehiculo estado,
            Ubicacion ubicacion
    ) {
        this(id, tipo, estado, ubicacion, null, ConfiguracionFlota.porDefecto());
    }

    public Vehiculo(
            String id,
            TipoVehiculo tipo,
            EstadoVehiculo estado,
            Ubicacion ubicacion,
            LocalDateTime disponibleDesde,
            ConfiguracionFlota configuracion
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id del vehículo es obligatorio.");
        }
        this.id = id;
        this.tipo = Objects.requireNonNull(tipo, "El tipo de vehículo es obligatorio.");
        this.estado = Objects.requireNonNull(estado, "El estado del vehículo es obligatorio.");
        this.ubicacion = Objects.requireNonNull(ubicacion, "La ubicación del vehículo es obligatoria.");
        this.disponibleDesde = disponibleDesde;
        this.configuracion = Objects.requireNonNull(configuracion, "La configuración es obligatoria.");
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

    /**
     * Instante a partir del cual la unidad puede iniciar un viaje nuevo. Es {@code null} cuando
     * está libre desde el arranque de la planificación.
     */
    public LocalDateTime getDisponibleDesde() {
        return disponibleDesde;
    }

    public int getCapacidad() {
        return configuracion.de(tipo).capacidadPaquetes();
    }

    public double getVelocidadKmH() {
        return configuracion.de(tipo).velocidadKmH();
    }

    public double getCostoPorKm() {
        return configuracion.de(tipo).costoPorKm();
    }

    /** Copia de la unidad situada en otro nodo. */
    public Vehiculo en(Ubicacion nuevaUbicacion) {
        return new Vehiculo(id, tipo, estado, nuevaUbicacion, disponibleDesde, configuracion);
    }

    public Vehiculo con(EstadoVehiculo nuevoEstado) {
        return new Vehiculo(id, tipo, nuevoEstado, ubicacion, disponibleDesde, configuracion);
    }

    /** Dónde queda la unidad y desde cuándo vuelve a estar libre, tras avanzar la operación. */
    public Vehiculo tras(Ubicacion nuevaUbicacion, LocalDateTime instanteLibre) {
        return new Vehiculo(id, tipo, estado, nuevaUbicacion, instanteLibre, configuracion);
    }

    @Override
    public String toString() {
        return id;
    }
}
