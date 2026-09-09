package com.paqrap.planificador.tabu;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Vehiculo;

/**
 * Aplica un movimiento sobre la solución y devuelve la acción que lo revierte.
 *
 * La búsqueda evalúa muchos vecinos por iteración, de modo que copiar la solución completa
 * en cada prueba sería costoso. En su lugar el movimiento se aplica, se mide y se deshace,
 * dejando la solución exactamente como estaba.
 */
public final class AplicadorMovimiento {

    /** Acción que devuelve la solución a su estado previo. */
    @FunctionalInterface
    public interface Deshacer {
        void ejecutar();
    }

    private AplicadorMovimiento() {
    }

    public static Deshacer aplicar(Solucion solucion, Movimiento movimiento) {
        return switch (movimiento.getTipo()) {
            case TRASLADAR -> trasladar(solucion, movimiento);
            case INTERCAMBIAR -> intercambiar(solucion, movimiento);
            case INVERTIR -> invertir(solucion, movimiento);
            case CAMBIAR_VEHICULO -> cambiarVehiculo(solucion, movimiento);
            case CAMBIAR_ALMACEN -> cambiarAlmacen(solucion, movimiento);
            case ASIGNAR_PENDIENTE -> asignarPendiente(solucion, movimiento);
        };
    }

    private static Deshacer trasladar(Solucion solucion, Movimiento m) {
        Ruta origen = solucion.getRuta(m.getRutaOrigen());
        Pedido movido = origen.retirarPedido(m.getPosicionOrigen());

        if (m.getRutaDestino() == Movimiento.RUTA_NUEVA) {
            Ruta nueva = crearRuta(solucion, m.getAlmacenDestino(), m.getVehiculoDestino(), movido);
            return () -> {
                solucion.removerRuta(nueva);
                origen.insertarPedido(m.getPosicionOrigen(), movido);
            };
        }

        Ruta destino = solucion.getRuta(m.getRutaDestino());
        // Tras retirar el pedido, las posiciones posteriores de la misma ruta se desplazan.
        int posicion = m.getRutaOrigen() == m.getRutaDestino() && m.getPosicionDestino() > m.getPosicionOrigen()
                ? m.getPosicionDestino() - 1
                : m.getPosicionDestino();
        posicion = Math.min(Math.max(posicion, 0), destino.getPedidos().size());
        destino.insertarPedido(posicion, movido);

        final int posicionFinal = posicion;
        return () -> {
            destino.retirarPedido(posicionFinal);
            origen.insertarPedido(m.getPosicionOrigen(), movido);
        };
    }

    private static Deshacer intercambiar(Solucion solucion, Movimiento m) {
        Ruta a = solucion.getRuta(m.getRutaOrigen());
        Ruta b = solucion.getRuta(m.getRutaDestino());
        Pedido pedidoA = a.getPedidos().get(m.getPosicionOrigen());
        Pedido pedidoB = b.getPedidos().get(m.getPosicionDestino());

        a.reemplazarPedido(m.getPosicionOrigen(), pedidoB);
        b.reemplazarPedido(m.getPosicionDestino(), pedidoA);

        return () -> {
            a.reemplazarPedido(m.getPosicionOrigen(), pedidoA);
            b.reemplazarPedido(m.getPosicionDestino(), pedidoB);
        };
    }

    private static Deshacer invertir(Solucion solucion, Movimiento m) {
        Ruta ruta = solucion.getRuta(m.getRutaOrigen());
        ruta.invertirSegmento(m.getPosicionOrigen(), m.getPosicionDestino());
        return () -> ruta.invertirSegmento(m.getPosicionOrigen(), m.getPosicionDestino());
    }

    private static Deshacer cambiarVehiculo(Solucion solucion, Movimiento m) {
        Ruta ruta = solucion.getRuta(m.getRutaOrigen());
        Vehiculo anterior = ruta.getVehiculo();
        ruta.reasignarVehiculo(m.getVehiculoDestino());
        return () -> ruta.reasignarVehiculo(anterior);
    }

    private static Deshacer cambiarAlmacen(Solucion solucion, Movimiento m) {
        Ruta ruta = solucion.getRuta(m.getRutaOrigen());
        Almacen almacenAnterior = ruta.getAlmacen();
        Vehiculo vehiculoAnterior = ruta.getVehiculo();
        ruta.reasignarAlmacen(m.getAlmacenDestino());
        ruta.reasignarVehiculo(m.getVehiculoDestino());
        return () -> {
            ruta.reasignarAlmacen(almacenAnterior);
            ruta.reasignarVehiculo(vehiculoAnterior);
        };
    }

    private static Deshacer asignarPendiente(Solucion solucion, Movimiento m) {
        Pedido pedido = m.getPedido();
        int indicePendiente = solucion.removerPedidoNoAsignado(pedido);
        if (indicePendiente < 0) {
            throw new IllegalStateException(
                    "El pedido " + pedido.getId() + " no está en la bolsa de pendientes.");
        }

        if (m.getRutaDestino() == Movimiento.RUTA_NUEVA) {
            Ruta nueva = crearRuta(solucion, m.getAlmacenDestino(), m.getVehiculoDestino(), pedido);
            return () -> {
                solucion.removerRuta(nueva);
                solucion.agregarPedidoNoAsignado(indicePendiente, pedido);
            };
        }

        Ruta destino = solucion.getRuta(m.getRutaDestino());
        int posicion = Math.min(Math.max(m.getPosicionDestino(), 0), destino.getPedidos().size());
        destino.insertarPedido(posicion, pedido);

        return () -> {
            destino.retirarPedido(posicion);
            solucion.agregarPedidoNoAsignado(indicePendiente, pedido);
        };
    }

    private static Ruta crearRuta(Solucion solucion, Almacen almacen, Vehiculo vehiculo, Pedido pedido) {
        Ruta nueva = new Ruta(solucion.crearIdRuta(), almacen, vehiculo);
        nueva.insertarPedido(0, pedido);
        solucion.agregarRuta(nueva);
        return nueva;
    }
}
