package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stock disponible en cada almacén a lo largo del tiempo.
 *
 * Los almacenes intermedios se recargan cada 24 horas a las 23:59:59 —de forma instantánea
 * para efectos del curso— hasta su capacidad máxima, de modo que el stock no es un número
 * fijo sino uno por periodo de reposición. El central tiene inventario infinito.
 *
 * Cada construcción de GRASP y cada evaluación de la búsqueda tabú trabajan sobre su propia
 * instancia, para no alterar el estado de la operación mientras se exploran alternativas.
 */
public final class Inventario {
    public static final LocalTime HORA_RECARGA = LocalTime.of(23, 59, 59);

    private final Map<String, Almacen> almacenes = new HashMap<>();
    private final Map<String, Integer> consumo = new HashMap<>();
    private final LocalDate periodoInicial;

    public Inventario(Iterable<Almacen> almacenes, LocalDateTime inicioOperacion) {
        Objects.requireNonNull(almacenes);
        this.periodoInicial = periodoDe(Objects.requireNonNull(inicioOperacion));
        for (Almacen almacen : almacenes) {
            this.almacenes.put(almacen.getId(), almacen);
        }
    }

    private Inventario(Inventario origen) {
        this.almacenes.putAll(origen.almacenes);
        this.consumo.putAll(origen.consumo);
        this.periodoInicial = origen.periodoInicial;
    }

    /**
     * Periodo de reposición al que pertenece un instante. La recarga ocurre a las 23:59:59,
     * así que lo despachado a partir de ese segundo ya se sirve con el stock del día siguiente.
     */
    public static LocalDate periodoDe(LocalDateTime instante) {
        return instante.toLocalTime().isBefore(HORA_RECARGA)
                ? instante.toLocalDate()
                : instante.toLocalDate().plusDays(1);
    }

    public Inventario copiar() {
        return new Inventario(this);
    }

    public int disponible(Almacen almacen, LocalDateTime instante) {
        if (almacen.esInventarioInfinito()) {
            return Integer.MAX_VALUE;
        }
        LocalDate periodo = periodoDe(instante);
        return stockAlIniciarElPeriodo(almacen, periodo) - consumo.getOrDefault(clave(almacen, periodo), 0);
    }

    public boolean tieneStock(Almacen almacen, LocalDateTime instante, int cantidad) {
        return disponible(almacen, instante) >= cantidad;
    }

    public void consumir(Almacen almacen, LocalDateTime instante, int cantidad) {
        if (almacen.esInventarioInfinito()) {
            return;
        }
        if (!tieneStock(almacen, instante, cantidad)) {
            throw new IllegalStateException(
                    "Stock insuficiente en " + almacen.getId() + " el " + periodoDe(instante) + ".");
        }
        consumo.merge(clave(almacen, periodoDe(instante)), cantidad, Integer::sum);
    }

    /**
     * Devuelve producto al almacén. Sirve para rehacer el cálculo de una unidad sin arrastrar
     * lo que había reservado con su programa anterior.
     */
    public void liberar(Almacen almacen, LocalDateTime instante, int cantidad) {
        if (almacen.esInventarioInfinito()) {
            return;
        }
        String clave = clave(almacen, periodoDe(instante));
        int actual = consumo.getOrDefault(clave, 0);
        if (cantidad > actual) {
            throw new IllegalStateException(
                    "Se intenta devolver más producto del que se retiró de " + almacen.getId() + ".");
        }
        consumo.put(clave, actual - cantidad);
    }

    /**
     * Antes de la primera recarga el almacén conserva el stock con el que empezó el escenario;
     * de ahí en adelante cada periodo arranca con la capacidad completa.
     */
    private int stockAlIniciarElPeriodo(Almacen almacen, LocalDate periodo) {
        return periodo.isAfter(periodoInicial)
                ? almacen.getCapacidadMaxima()
                : almacen.getStockInicial();
    }

    private String clave(Almacen almacen, LocalDate periodo) {
        return almacen.getId() + "@" + periodo;
    }
}
