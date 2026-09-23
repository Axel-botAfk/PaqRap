package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Pedido {
    private final String id;
    private final String clienteId;
    private final Ubicacion destino;
    private final int cantidad;
    private final LocalDateTime fechaRegistro;
    private final TipoEntrega tipoEntrega;
    private final LocalDateTime fechaLimite;

    public Pedido(
            String id,
            String clienteId,
            Ubicacion destino,
            int cantidad,
            LocalDateTime fechaRegistro,
            TipoEntrega tipoEntrega
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id del pedido es obligatorio.");
        }
        if (clienteId == null || clienteId.isBlank()) {
            throw new IllegalArgumentException("El cliente del pedido es obligatorio.");
        }
        this.destino = Objects.requireNonNull(destino, "El destino es obligatorio.");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad del pedido debe ser mayor que cero.");
        }
        this.fechaRegistro = Objects.requireNonNull(fechaRegistro, "La fecha de registro es obligatoria.");
        this.tipoEntrega = Objects.requireNonNull(tipoEntrega, "El tipo de entrega es obligatorio.");

        this.id = id;
        this.clienteId = clienteId;
        this.cantidad = cantidad;
        this.fechaLimite = fechaRegistro.plusHours(tipoEntrega.getHorasPlazo());
    }

    public String getId() {
        return id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public Ubicacion getDestino() {
        return destino;
    }

    public int getCantidad() {
        return cantidad;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public TipoEntrega getTipoEntrega() {
        return tipoEntrega;
    }

    public LocalDateTime getFechaLimite() {
        return fechaLimite;
    }

    @Override
    public String toString() {
        return id;
    }
}
