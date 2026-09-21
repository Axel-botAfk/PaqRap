package com.paqrap.modelo;

import java.util.Objects;

/**
 * Almacén de despacho.
 *
 * El central se considera de inventario infinito porque se abastece permanentemente. Los dos
 * intermedios tienen capacidad máxima de 1000 unidades y se recargan cada 24 horas, a las
 * 23:59:59, de forma instantánea; el stock disponible en cada instante no lo resuelve esta
 * clase sino {@link com.paqrap.planificador.Inventario}, que conoce el reloj de la operación.
 */
public final class Almacen {
    public static final int CAPACIDAD_MAXIMA_INTERMEDIO = 1000;

    private final String id;
    private final TipoAlmacen tipo;
    private final Ubicacion ubicacion;
    private final int stockInicial;

    public Almacen(String id, TipoAlmacen tipo, Ubicacion ubicacion, int stockInicial) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id del almacén es obligatorio.");
        }
        this.tipo = Objects.requireNonNull(tipo, "El tipo de almacén es obligatorio.");
        this.ubicacion = Objects.requireNonNull(ubicacion, "La ubicación del almacén es obligatoria.");

        if (tipo == TipoAlmacen.INTERMEDIO
                && (stockInicial < 0 || stockInicial > CAPACIDAD_MAXIMA_INTERMEDIO)) {
            throw new IllegalArgumentException(
                    "El stock de un almacén intermedio debe estar entre 0 y "
                            + CAPACIDAD_MAXIMA_INTERMEDIO + ".");
        }

        if (tipo == TipoAlmacen.CENTRAL && stockInicial < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }

        this.id = id;
        this.stockInicial = stockInicial;
    }

    public static Almacen central(String id, Ubicacion ubicacion) {
        // El stock numérico no se usa para el central: su inventario es infinito.
        return new Almacen(id, TipoAlmacen.CENTRAL, ubicacion, 0);
    }

    public static Almacen intermedio(String id, Ubicacion ubicacion, int stockInicial) {
        return new Almacen(id, TipoAlmacen.INTERMEDIO, ubicacion, stockInicial);
    }

    /** Intermedio que arranca la operación con su capacidad completa. */
    public static Almacen intermedioLleno(String id, Ubicacion ubicacion) {
        return intermedio(id, ubicacion, CAPACIDAD_MAXIMA_INTERMEDIO);
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

    /** Stock con el que el almacén inicia el escenario, antes de la primera recarga. */
    public int getStockInicial() {
        return stockInicial;
    }

    /** Stock al que vuelve el almacén en cada recarga de 24 horas. */
    public int getCapacidadMaxima() {
        return esInventarioInfinito() ? Integer.MAX_VALUE : CAPACIDAD_MAXIMA_INTERMEDIO;
    }

    public boolean esInventarioInfinito() {
        return tipo == TipoAlmacen.CENTRAL;
    }

    @Override
    public String toString() {
        return id;
    }
}
