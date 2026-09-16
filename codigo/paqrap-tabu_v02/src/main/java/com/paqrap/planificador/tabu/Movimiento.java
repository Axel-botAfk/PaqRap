package com.paqrap.planificador.tabu;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Vehiculo;

/**
 * Descripción de un movimiento sobre la solución vigente.
 *
 * El atributo tabú identifica el cambio que quedará prohibido durante la tenencia, no la
 * solución completa: así se impide deshacer de inmediato la decisión tomada sin bloquear
 * soluciones que solo se parecen.
 */
public final class Movimiento {
    /** Marca de "ruta nueva": el pedido estrena una ruta en lugar de sumarse a una existente. */
    public static final int RUTA_NUEVA = -1;

    private final TipoMovimiento tipo;
    private final int rutaOrigen;
    private final int posicionOrigen;
    private final int rutaDestino;
    private final int posicionDestino;
    private final Pedido pedido;
    private final Vehiculo vehiculoDestino;
    private final Almacen almacenDestino;

    private Movimiento(
            TipoMovimiento tipo,
            int rutaOrigen,
            int posicionOrigen,
            int rutaDestino,
            int posicionDestino,
            Pedido pedido,
            Vehiculo vehiculoDestino,
            Almacen almacenDestino
    ) {
        this.tipo = tipo;
        this.rutaOrigen = rutaOrigen;
        this.posicionOrigen = posicionOrigen;
        this.rutaDestino = rutaDestino;
        this.posicionDestino = posicionDestino;
        this.pedido = pedido;
        this.vehiculoDestino = vehiculoDestino;
        this.almacenDestino = almacenDestino;
    }

    public static Movimiento trasladar(
            int rutaOrigen,
            int posicionOrigen,
            int rutaDestino,
            int posicionDestino,
            Pedido pedido
    ) {
        return new Movimiento(TipoMovimiento.TRASLADAR, rutaOrigen, posicionOrigen,
                rutaDestino, posicionDestino, pedido, null, null);
    }

    public static Movimiento trasladarARutaNueva(
            int rutaOrigen,
            int posicionOrigen,
            Pedido pedido,
            Vehiculo vehiculo,
            Almacen almacen
    ) {
        return new Movimiento(TipoMovimiento.TRASLADAR, rutaOrigen, posicionOrigen,
                RUTA_NUEVA, 0, pedido, vehiculo, almacen);
    }

    public static Movimiento intercambiar(
            int rutaA,
            int posicionA,
            int rutaB,
            int posicionB,
            Pedido pedido
    ) {
        return new Movimiento(TipoMovimiento.INTERCAMBIAR, rutaA, posicionA, rutaB, posicionB,
                pedido, null, null);
    }

    public static Movimiento invertir(int ruta, int desde, int hasta) {
        return new Movimiento(TipoMovimiento.INVERTIR, ruta, desde, ruta, hasta, null, null, null);
    }

    public static Movimiento cambiarVehiculo(int ruta, Vehiculo vehiculo) {
        return new Movimiento(TipoMovimiento.CAMBIAR_VEHICULO, ruta, -1, ruta, -1,
                null, vehiculo, null);
    }

    /**
     * El almacén y el vehículo viajan juntos porque una ruta solo puede salir de un almacén
     * donde su unidad se encuentre; cambiar el almacén sin cambiar la unidad produciría
     * siempre un vecino infactible.
     */
    public static Movimiento cambiarAlmacen(int ruta, Almacen almacen, Vehiculo vehiculo) {
        return new Movimiento(TipoMovimiento.CAMBIAR_ALMACEN, ruta, -1, ruta, -1,
                null, vehiculo, almacen);
    }

    public static Movimiento asignarPendiente(Pedido pedido, int rutaDestino, int posicionDestino) {
        return new Movimiento(TipoMovimiento.ASIGNAR_PENDIENTE, -1, -1, rutaDestino,
                posicionDestino, pedido, null, null);
    }

    public static Movimiento asignarPendienteEnRutaNueva(
            Pedido pedido,
            Vehiculo vehiculo,
            Almacen almacen
    ) {
        return new Movimiento(TipoMovimiento.ASIGNAR_PENDIENTE, -1, -1, RUTA_NUEVA, 0,
                pedido, vehiculo, almacen);
    }

    /** Atributo que se registra en la lista tabú. */
    public String atributo() {
        return switch (tipo) {
            case TRASLADAR, INTERCAMBIAR -> tipo + ":" + pedido.getId() + ":ruta" + rutaOrigen;
            case INVERTIR -> tipo + ":ruta" + rutaOrigen + ":" + posicionOrigen + "-" + posicionDestino;
            case CAMBIAR_VEHICULO -> tipo + ":ruta" + rutaOrigen + ":" + vehiculoDestino.getId();
            case CAMBIAR_ALMACEN -> tipo + ":ruta" + rutaOrigen + ":" + almacenDestino.getId();
            case ASIGNAR_PENDIENTE -> tipo + ":" + pedido.getId();
        };
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public int getRutaOrigen() {
        return rutaOrigen;
    }

    public int getPosicionOrigen() {
        return posicionOrigen;
    }

    public int getRutaDestino() {
        return rutaDestino;
    }

    public int getPosicionDestino() {
        return posicionDestino;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Vehiculo getVehiculoDestino() {
        return vehiculoDestino;
    }

    public Almacen getAlmacenDestino() {
        return almacenDestino;
    }

    @Override
    public String toString() {
        return atributo();
    }
}
