package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class Vehiculo {
    private final String id;
    private final TipoVehiculo tipo;
    private final EstadoVehiculo estado;
    private final Ubicacion ubicacion;
    private final List<VentanaIndisponibilidad> indisponibilidades;

    /** Constructor original: sin averías ni mantenimientos programados. */
    public Vehiculo(
            String id,
            TipoVehiculo tipo,
            EstadoVehiculo estado,
            Ubicacion ubicacion
    ) {
        this(id, tipo, estado, ubicacion, List.of());
    }

    /**
     * Constructor completo, con las ventanas de indisponibilidad por avería
     * (pregunta 3) o mantenimiento preventivo (pregunta 19) ya calculadas
     * para esta unidad.
     */
    public Vehiculo(
            String id,
            TipoVehiculo tipo,
            EstadoVehiculo estado,
            Ubicacion ubicacion,
            List<VentanaIndisponibilidad> indisponibilidades
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id del vehículo es obligatorio.");
        }
        this.id = id;
        this.tipo = Objects.requireNonNull(tipo, "El tipo de vehículo es obligatorio.");
        this.estado = Objects.requireNonNull(estado, "El estado del vehículo es obligatorio.");
        this.ubicacion = Objects.requireNonNull(ubicacion, "La ubicación del vehículo es obligatoria.");
        this.indisponibilidades = List.copyOf(
                Objects.requireNonNull(indisponibilidades, "Las indisponibilidades no pueden ser null."));
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

    public List<VentanaIndisponibilidad> getIndisponibilidades() {
        return indisponibilidades;
    }

    /** true si, en el instante dado, ninguna avería ni mantenimiento programado cubre al vehículo. */
    public boolean estaDisponibleEn(LocalDateTime instante) {
        for (VentanaIndisponibilidad ventana : indisponibilidades) {
            if (ventana.incluye(instante)) {
                return false;
            }
        }
        return true;
    }

    /** true si el rango [desde, hasta] no cruza ninguna avería ni mantenimiento programado. */
    public boolean estaDisponibleEnRango(LocalDateTime desde, LocalDateTime hasta) {
        for (VentanaIndisponibilidad ventana : indisponibilidades) {
            if (ventana.seSuperponeCon(desde, hasta)) {
                return false;
            }
        }
        return true;
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
