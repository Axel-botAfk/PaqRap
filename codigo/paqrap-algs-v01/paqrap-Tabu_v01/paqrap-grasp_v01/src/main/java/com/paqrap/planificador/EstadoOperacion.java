package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Vehiculo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class EstadoOperacion {
    private final LocalDateTime reloj;
    private final List<Pedido> pedidos;
    private final List<Almacen> almacenes;
    private final List<Vehiculo> vehiculos;

    public EstadoOperacion(
            LocalDateTime reloj,
            List<Pedido> pedidos,
            List<Almacen> almacenes,
            List<Vehiculo> vehiculos
    ) {
        this.reloj = Objects.requireNonNull(reloj);
        this.pedidos = List.copyOf(pedidos);
        this.almacenes = List.copyOf(almacenes);
        this.vehiculos = List.copyOf(vehiculos);
    }

    public LocalDateTime getReloj() {
        return reloj;
    }

    public List<Pedido> getPedidos() {
        return pedidos;
    }

    public List<Almacen> getAlmacenes() {
        return almacenes;
    }

    public List<Vehiculo> getVehiculos() {
        return vehiculos;
    }
}
