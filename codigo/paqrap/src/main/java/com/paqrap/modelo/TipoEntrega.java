package com.paqrap.modelo;

/**
 * Clasificación del pedido según su plazo.
 *
 * El plazo real llega como un número de horas en el archivo de ventas (campo {@code hl}),
 * de modo que no puede representarse con un conjunto cerrado de valores. Esta clasificación
 * solo sirve para presentar y priorizar: 36 horas es la venta regular y cualquier plazo
 * menor corresponde a una entrega priorizada (4, 8, 12 o 18 horas en el caso).
 */
public enum TipoEntrega {
    REGULAR,
    PRIORITARIA;

    public static final int HORAS_PLAZO_REGULAR = 36;

    public static TipoEntrega de(int horasPlazo) {
        return horasPlazo >= HORAS_PLAZO_REGULAR ? REGULAR : PRIORITARIA;
    }
}
