package com.paqrap.simulacion;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Vehiculo;

import java.time.LocalDateTime;

/**
 * Entrega efectivamente realizada durante la simulación: qué pedido, qué unidad, desde qué
 * almacén salió el producto y a qué hora llegó al cliente.
 */
public record Entrega(
        Pedido pedido,
        Vehiculo unidad,
        Almacen almacen,
        LocalDateTime llegada,
        double kilometros,
        double costo
) {
    public boolean enPlazo() {
        return !llegada.isAfter(pedido.getFechaLimite());
    }
}
