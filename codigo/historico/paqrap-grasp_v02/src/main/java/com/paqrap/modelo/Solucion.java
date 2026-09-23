package com.paqrap.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Solucion {
    private final List<Ruta> rutas = new ArrayList<>();
    private final List<Pedido> pedidosNoAsignados = new ArrayList<>();

    public List<Ruta> getRutas() {
        return Collections.unmodifiableList(rutas);
    }

    public List<Pedido> getPedidosNoAsignados() {
        return Collections.unmodifiableList(pedidosNoAsignados);
    }

    public double getCostoTotal() {
        return rutas.stream().mapToDouble(Ruta::getCostoTotal).sum();
    }

    public double getDistanciaTotalKm() {
        return rutas.stream().mapToDouble(Ruta::getDistanciaTotalKm).sum();
    }

    public void agregarRuta(Ruta ruta) {
        rutas.add(ruta);
    }

    public void agregarPedidoNoAsignado(Pedido pedido) {
        pedidosNoAsignados.add(pedido);
    }

    public void agregarPedidosNoAsignados(List<Pedido> pedidos) {
        pedidosNoAsignados.addAll(pedidos);
    }
}
