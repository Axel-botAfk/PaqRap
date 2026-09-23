package com.paqrap.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Solucion {
    private final List<Ruta> rutas = new ArrayList<>();
    private final List<Pedido> pedidosNoAsignados = new ArrayList<>();

    /** Secuencia propia de las rutas creadas por la mejora; no colisiona con "R-n" de GRASP. */
    private int secuenciaRutasMejora;

    private String algoritmo = "GRASP";

    public List<Ruta> getRutas() {
        return Collections.unmodifiableList(rutas);
    }

    public Ruta getRuta(int indice) {
        return rutas.get(indice);
    }

    public int getCantidadRutas() {
        return rutas.size();
    }

    public List<Pedido> getPedidosNoAsignados() {
        return Collections.unmodifiableList(pedidosNoAsignados);
    }

    public int getCantidadPedidosNoAsignados() {
        return pedidosNoAsignados.size();
    }

    public String getAlgoritmo() {
        return algoritmo;
    }

    public void setAlgoritmo(String algoritmo) {
        this.algoritmo = Objects.requireNonNull(algoritmo);
    }

    public double getCostoTotal() {
        return rutas.stream().mapToDouble(Ruta::getCostoTotal).sum();
    }

    public double getDistanciaTotalKm() {
        return rutas.stream().mapToDouble(Ruta::getDistanciaTotalKm).sum();
    }

    public void agregarRuta(Ruta ruta) {
        rutas.add(Objects.requireNonNull(ruta));
    }

    public void insertarRuta(int indice, Ruta ruta) {
        rutas.add(indice, Objects.requireNonNull(ruta));
    }

    /** Retira la ruta y devuelve la posición que ocupaba, para poder deshacer el movimiento. */
    public int removerRuta(Ruta ruta) {
        int indice = rutas.indexOf(ruta);
        if (indice >= 0) {
            rutas.remove(indice);
        }
        return indice;
    }

    public Ruta reemplazarRuta(int indice, Ruta ruta) {
        return rutas.set(indice, Objects.requireNonNull(ruta));
    }

    /** Identificador para una ruta creada durante la fase de mejora. */
    public String crearIdRuta() {
        secuenciaRutasMejora++;
        return "R-T" + secuenciaRutasMejora;
    }

    public void agregarPedidoNoAsignado(Pedido pedido) {
        pedidosNoAsignados.add(Objects.requireNonNull(pedido));
    }

    public void agregarPedidoNoAsignado(int indice, Pedido pedido) {
        pedidosNoAsignados.add(indice, Objects.requireNonNull(pedido));
    }

    public void agregarPedidosNoAsignados(List<Pedido> pedidos) {
        pedidosNoAsignados.addAll(pedidos);
    }

    /** Retira el pedido de la bolsa de pendientes y devuelve su posición previa (-1 si no estaba). */
    public int removerPedidoNoAsignado(Pedido pedido) {
        int indice = pedidosNoAsignados.indexOf(pedido);
        if (indice >= 0) {
            pedidosNoAsignados.remove(indice);
        }
        return indice;
    }

    /**
     * Los movimientos de traslado pueden dejar rutas sin entregas. Se eliminan al cerrar la
     * búsqueda para que el plan devuelto no reserve unidades que no salen a operar.
     */
    public void depurarRutasVacias() {
        rutas.removeIf(Ruta::estaVacia);
    }

    public Solucion copiar() {
        Solucion copia = new Solucion();
        for (Ruta ruta : rutas) {
            copia.rutas.add(ruta.copiar());
        }
        copia.pedidosNoAsignados.addAll(pedidosNoAsignados);
        copia.secuenciaRutasMejora = secuenciaRutasMejora;
        copia.algoritmo = algoritmo;
        return copia;
    }
}
