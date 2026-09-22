package com.paqrap.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Ruta {
    private final String id;
    private final Almacen almacen;
    private final Vehiculo vehiculo;
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

    public void insertarPedido(int posicion, Pedido pedido) {
        if (posicion < 0 || posicion > pedidos.size()) {
            throw new IllegalArgumentException("Posición de inserción inválida.");
        }
        pedidos.add(posicion, Objects.requireNonNull(pedido));
    }

    /**
     * Reemplaza el orden de entregas de la ruta. Se utiliza durante la fase
     * de búsqueda local de GRASP después de validar la secuencia candidata.
     */
    public void reemplazarPedidos(List<Pedido> nuevaSecuencia) {
        Objects.requireNonNull(nuevaSecuencia, "La secuencia de pedidos es obligatoria.");
        if (nuevaSecuencia.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("La secuencia no puede contener pedidos nulos.");
        }

        int nuevaCarga = nuevaSecuencia.stream().mapToInt(Pedido::getCantidad).sum();
        if (nuevaCarga > vehiculo.getCapacidad()) {
            throw new IllegalArgumentException("La nueva secuencia supera la capacidad del vehículo.");
        }

        pedidos.clear();
        pedidos.addAll(nuevaSecuencia);
    }

    public void actualizarMetricas(double distanciaTotalKm, double costoTotal, double duracionHoras) {
        this.distanciaTotalKm = distanciaTotalKm;
        this.costoTotal = costoTotal;
        this.duracionHoras = duracionHoras;
    }

    @Override
    public String toString() {
        return id + " " + almacen.getId() + " " + vehiculo.getId() + " " + pedidos;
    }
}
