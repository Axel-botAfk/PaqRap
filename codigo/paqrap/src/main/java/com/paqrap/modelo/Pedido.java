package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Pedido tal como llega en el archivo de ventas: {@code ##d##h##m:posX,posY,cIdCliente,qq,hl}.
 *
 * El plazo se guarda como número de horas ({@code hl}) y no como un conjunto cerrado de
 * valores, porque el archivo puede traer cualquier entero. La fecha límite se calcula desde
 * la fecha de registro y no incluye la hora de acondicionamiento en el cliente, que según el
 * caso queda fuera del plazo comprometido.
 *
 * <h2>Entregas parciales</h2>
 *
 * Un pedido se puede {@link #partirEn partir} en varias entregas que viajan por separado, incluso
 * en unidades distintas. Cada parte es un pedido completo por derecho propio —mismo cliente,
 * mismo destino, misma fecha límite— con su porción de la cantidad, y todas recuerdan de cuál
 * salieron en {@link #getIdOriginal()}.
 *
 * Que cada parte sea un pedido normal es lo que hace que el resto del sistema no se entere:
 * capacidad, plazos, inserción y ruteo funcionan igual. Lo único que tiene que mirar el id
 * original es la contabilidad, para no contar cuatro pedidos donde el cliente hizo uno.
 *
 * Y encaja con la regla del caso de que el acondicionamiento es de una hora <b>por entrega</b>,
 * sin importar cuántos productos se dejen: como cada parte es una parada, cada una paga su hora.
 * Por eso partir nunca sale gratis, y el planificador solo debería hacerlo cuando el pedido
 * entero no cabe en ninguna unidad disponible.
 */
public final class Pedido {
    private final String id;
    private final String idOriginal;
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
        this(id, id, clienteId, destino, cantidad, fechaRegistro, horasPlazo);
    }

    private Pedido(
            String id,
            String idOriginal,
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
        this.idOriginal = idOriginal;
        this.clienteId = clienteId;
        this.cantidad = cantidad;
        this.horasPlazo = horasPlazo;
        this.fechaLimite = fechaRegistro.plusHours(horasPlazo);
    }

    public String getId() {
        return id;
    }

    /**
     * Pedido del que salió esta entrega. En un pedido sin partir coincide con su propio id, de
     * modo que agrupar por este campo siempre cuenta pedidos del cliente y no entregas.
     */
    public String getIdOriginal() {
        return idOriginal;
    }

    public boolean esParteDeOtro() {
        return !id.equals(idOriginal);
    }

    /**
     * Parte el pedido en dos entregas: una de {@code cantidadDeLaPrimera} productos y otra con
     * el resto. Las dos conservan cliente, destino y fecha límite, y apuntan al mismo original.
     *
     * El cliente recibe dos visitas de una hora cada una en lugar de una, así que solo vale la
     * pena cuando el pedido entero no cabe en ninguna unidad que pueda llegar a tiempo.
     */
    public List<Pedido> partirEn(int cantidadDeLaPrimera) {
        if (cantidadDeLaPrimera <= 0 || cantidadDeLaPrimera >= cantidad) {
            throw new IllegalArgumentException(
                    "La primera parte debe llevar entre 1 y " + (cantidad - 1)
                            + " productos, y se pidió " + cantidadDeLaPrimera + ".");
        }
        return List.of(
                parte("a", cantidadDeLaPrimera),
                parte("b", cantidad - cantidadDeLaPrimera));
    }

    /**
     * Las partes se nombran colgando una letra del id de quien las produjo: P-007 da P-007a y
     * P-007b, y volver a partir P-007a da P-007aa y P-007ab. Es determinista a propósito, porque
     * un id que dependiera del momento o de la memoria haría que dos corridas con la misma
     * semilla dejaran de ser comparables.
     */
    private Pedido parte(String letra, int cuantos) {
        return new Pedido(
                id + letra, idOriginal, clienteId, destino, cuantos, fechaRegistro, horasPlazo);
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
