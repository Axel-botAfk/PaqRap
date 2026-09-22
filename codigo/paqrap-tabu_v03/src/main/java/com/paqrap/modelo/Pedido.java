package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pedido tal como llega en el archivo de ventas: {@code ##d##h##m:posX,posY,cIdCliente,qq,hl}.
 *
 * El plazo se guarda como número de horas ({@code hl}) y no como un conjunto cerrado de
 * valores, porque el archivo puede traer cualquier entero. La fecha límite se calcula desde
 * la fecha de registro y no incluye la hora de acondicionamiento en el cliente, que según el
 * caso queda fuera del plazo comprometido.
 */
public final class Pedido {
    private final String id;
    private final String clienteId;
    private final Ubicacion destino;
    private final int cantidad;
    private final LocalDateTime fechaRegistro;
    private final int horasPlazo;
    private final LocalDateTime fechaLimite;

    public Pedido(
            String id,
            String clienteId,
            Ubicacion destino,
            int cantidad,
            LocalDateTime fechaRegistro,
            int horasPlazo
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
        if (horasPlazo <= 0) {
            throw new IllegalArgumentException("El plazo del pedido debe ser mayor que cero.");
        }
        this.fechaRegistro = Objects.requireNonNull(fechaRegistro, "La fecha de registro es obligatoria.");

        this.id = id;
        this.clienteId = clienteId;
        this.cantidad = cantidad;
        this.horasPlazo = horasPlazo;
        this.fechaLimite = fechaRegistro.plusHours(horasPlazo);
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

    public int getHorasPlazo() {
        return horasPlazo;
    }

    public TipoEntrega getTipoEntrega() {
        return TipoEntrega.de(horasPlazo);
    }

    /** Instante máximo de <b>llegada</b> al cliente. */
    public LocalDateTime getFechaLimite() {
        return fechaLimite;
    }

    @Override
    public String toString() {
        return id;
    }
}
