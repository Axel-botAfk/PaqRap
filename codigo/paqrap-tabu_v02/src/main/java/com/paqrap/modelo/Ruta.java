package com.paqrap.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Ruta {
    private final String id;
    private Almacen almacen;
    private Vehiculo vehiculo;
    private final List<Pedido> pedidos = new ArrayList<>();

    private double distanciaTotalKm;
    private double costoTotal;
    private double duracionHoras;

    public Ruta(String id, Almacen almacen, Vehiculo vehiculo) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id de la ruta es obligatorio.");
        }
        this.id = id;
        this.almacen = Objects.requireNonNull(almacen, "El almacén es obligatorio.");
        this.vehiculo = Objects.requireNonNull(vehiculo, "El vehículo es obligatorio.");
    }

    public String getId() {
        return id;
    }

    public Almacen getAlmacen() {
        return almacen;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public List<Pedido> getPedidos() {
        return Collections.unmodifiableList(pedidos);
    }

    public int getCargaTotal() {
        return pedidos.stream().mapToInt(Pedido::getCantidad).sum();
    }

    public int getCapacidadDisponible() {
        return vehiculo.getCapacidad() - getCargaTotal();
    }

    public double getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    public double getCostoTotal() {
        return costoTotal;
    }

    public double getDuracionHoras() {
        return duracionHoras;
    }

    public boolean estaVacia() {
        return pedidos.isEmpty();
    }

    public boolean contiene(String idPedido) {
        return pedidos.stream().anyMatch(p -> p.getId().equals(idPedido));
    }

    public void insertarPedido(int posicion, Pedido pedido) {
        if (posicion < 0 || posicion > pedidos.size()) {
            throw new IllegalArgumentException("Posición de inserción inválida.");
        }
        pedidos.add(posicion, Objects.requireNonNull(pedido));
    }

    /**
     * Operaciones de modificación requeridas por la búsqueda local y la búsqueda tabú.
     * Los movimientos se aplican, se miden y se deshacen sobre la misma ruta, de modo que
     * la ruta debe poder retirar, reemplazar y reordenar pedidos sin reconstruirse.
     */
    public Pedido retirarPedido(int posicion) {
        if (posicion < 0 || posicion >= pedidos.size()) {
            throw new IllegalArgumentException("Posición de retiro inválida.");
        }
        return pedidos.remove(posicion);
    }

    public Pedido reemplazarPedido(int posicion, Pedido pedido) {
        if (posicion < 0 || posicion >= pedidos.size()) {
            throw new IllegalArgumentException("Posición de reemplazo inválida.");
        }
        return pedidos.set(posicion, Objects.requireNonNull(pedido));
    }

    /** Invierte el tramo [desde, hasta] de la secuencia de entregas (movimiento 2-opt). */
    public void invertirSegmento(int desde, int hasta) {
        if (desde < 0 || hasta >= pedidos.size() || desde >= hasta) {
            throw new IllegalArgumentException("Segmento a invertir inválido.");
        }
        int i = desde;
        int j = hasta;
        while (i < j) {
            Pedido temporal = pedidos.get(i);
            pedidos.set(i, pedidos.get(j));
            pedidos.set(j, temporal);
            i++;
            j--;
        }
    }

    /**
     * Reasignación de recursos: la ruta conserva sus entregas pero cambia el vehículo
     * y/o el almacén de salida. Es la parte de "asignación" del vecindario.
     */
    public void reasignarVehiculo(Vehiculo nuevoVehiculo) {
        this.vehiculo = Objects.requireNonNull(nuevoVehiculo, "El vehículo es obligatorio.");
    }

    public void reasignarAlmacen(Almacen nuevoAlmacen) {
        this.almacen = Objects.requireNonNull(nuevoAlmacen, "El almacén es obligatorio.");
    }

    public void actualizarMetricas(double distanciaTotalKm, double costoTotal, double duracionHoras) {
        this.distanciaTotalKm = distanciaTotalKm;
        this.costoTotal = costoTotal;
        this.duracionHoras = duracionHoras;
    }

    /** Copia independiente: los pedidos, el almacén y el vehículo son inmutables y se comparten. */
    public Ruta copiar() {
        Ruta copia = new Ruta(id, almacen, vehiculo);
        copia.pedidos.addAll(pedidos);
        copia.actualizarMetricas(distanciaTotalKm, costoTotal, duracionHoras);
        return copia;
    }

    @Override
    public String toString() {
        return id + " " + almacen.getId() + " " + vehiculo.getId() + " " + pedidos;
    }
}
