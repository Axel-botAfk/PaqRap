package com.paqrap.modelo;

import java.util.Objects;

public final class Almacen {
    public static final int CAPACIDAD_MAXIMA_INTERMEDIO = 1000;

    private final String id;
    private final TipoAlmacen tipo;
    private final Ubicacion ubicacion;
    private final int stockActual;

    public Almacen(String id, TipoAlmacen tipo, Ubicacion ubicacion, int stockActual) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id del almacén es obligatorio.");
        }
        this.tipo = Objects.requireNonNull(tipo, "El tipo de almacén es obligatorio.");
        this.ubicacion = Objects.requireNonNull(ubicacion, "La ubicación del almacén es obligatoria.");

        if (tipo == TipoAlmacen.INTERMEDIO
                && (stockActual < 0 || stockActual > CAPACIDAD_MAXIMA_INTERMEDIO)) {
            throw new IllegalArgumentException(
                    "El stock de un almacén intermedio debe estar entre 0 y 1000.");
        }

        if (tipo == TipoAlmacen.CENTRAL && stockActual < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }

        this.id = id;
        this.stockActual = stockActual;
    }

    public static Almacen central(String id, Ubicacion ubicacion) {
        // El stock numérico no se usa para el central: su inventario es infinito.
        return new Almacen(id, TipoAlmacen.CENTRAL, ubicacion, 0);
    }

    public static Almacen intermedio(String id, Ubicacion ubicacion, int stockActual) {
        return new Almacen(id, TipoAlmacen.INTERMEDIO, ubicacion, stockActual);
    }

    public String getId() {
        return id;
    }

    public TipoAlmacen getTipo() {
        return tipo;
    }

    public Ubicacion getUbicacion() {
        return ubicacion;
    }

    public int getStockActual() {
        return stockActual;
    }

    public boolean esInventarioInfinito() {
        return tipo == TipoAlmacen.CENTRAL;
    }

    @Override
    public String toString() {
        return id;
    }
}
