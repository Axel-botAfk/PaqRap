package com.paqrap.planificador;

import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Fase de mejora de GRASP.
 *
 * El método publicado por Feo y Resende alterna dos fases en cada iteración: una construcción
 * golosa aleatorizada y una <b>búsqueda local</b> que lleva esa solución a un óptimo local. Sin
 * la segunda, aumentar el presupuesto solo significa volver a muestrear la misma distribución
 * golosa, que es lo que se medía antes: cuadruplicar las construcciones mejoraba el objetivo
 * menos de medio punto porcentual.
 *
 * <h2>Independiente de la búsqueda tabú</h2>
 *
 * No reutiliza nada del paquete {@code tabu}. Es deliberado: los dos algoritmos que el caso pide
 * comparar tienen que poder explicarse y medirse por separado, y compartir la maquinaria de
 * movimientos haría que un cambio en uno moviera al otro sin que se note.
 *
 * <h2>Qué mueve</h2>
 *
 * Tres vecindarios, aplicados en ese orden sobre cada pedido:
 *
 * <ol>
 *   <li><b>Incorporar</b> un pedido que quedó sin asignar. Va primero porque cubrir pedidos es
 *       lo que más pesa en el objetivo, y la construcción pudo haber descartado uno por el orden
 *       en que fue llenando las unidades, no porque fuera imposible.</li>
 *   <li><b>Reubicar</b> una entrega en otro viaje, o en otra posición del suyo.</li>
 *   <li><b>Intercambiar</b> dos entregas de viajes distintos.</li>
 * </ol>
 *
 * <h2>Cómo decide</h2>
 *
 * Primera mejora, no la mejor: en cuanto un movimiento baja el objetivo se acepta y se pasa al
 * pedido siguiente, sin terminar de explorar su vecindario. Con vecindarios de este tamaño,
 * buscar la mejor mejora cuesta mucho más y rinde parecido.
 *
 * Cada candidato se aplica sobre la solución, se mide y se deshace si no mejoró; copiar el plan
 * entero para probar cada vecino sería mucho más caro. Como {@link Evaluador#objetivo} devuelve
 * infinito ante cualquier violación, un movimiento que rompe capacidad, plazo, stock o camino
 * nunca puede ganar, y no hace falta verificarlo aparte.
 *
 * <h2>Qué tan lejos busca</h2>
 *
 * El vecindario está acotado por dos parámetros, porque explorarlo entero por cada pedido de una
 * cola de cincuenta multiplicaría por diez el costo de cada construcción: solo se consideran los
 * {@code viajesCandidatosBusquedaLocal} viajes más cercanos al destino, y se dan como mucho
 * {@code pasadasBusquedaLocal} pasadas, cortando antes si una pasada no encuentra nada.
 *
 * Es determinista: no usa la semilla. La aleatoriedad de GRASP vive en la construcción.
 */
public final class BusquedaLocal {
    private static final double EPSILON = 1e-9;

    private final Evaluador evaluador;

    public BusquedaLocal(Evaluador evaluador) {
        this.evaluador = Objects.requireNonNull(evaluador);
    }

    /**
     * Mejora la solución en el lugar hasta el óptimo local del vecindario acotado.
     *
     * @return el valor objetivo alcanzado.
     */
    public double mejorar(Solucion solucion, EstadoOperacion estado, Parametros parametros) {
        double valor = evaluador.objetivo(solucion, estado, parametros);
        if (Double.isInfinite(valor)) {
            // La construcción entregó algo inviable; moverlo no lo arregla y medir cada vecino
            // contra infinito no distingue ninguno.
            return valor;
        }

        for (int pasada = 0; pasada < parametros.getPasadasBusquedaLocal(); pasada++) {
            double alInicio = valor;
            valor = unaPasada(solucion, estado, parametros, valor);
            if (valor >= alInicio - EPSILON) {
                break;
            }
        }
        return valor;
    }

    private double unaPasada(
            Solucion solucion,
            EstadoOperacion estado,
            Parametros parametros,
            double valorInicial
    ) {
        double valor = valorInicial;
        int candidatos = parametros.getViajesCandidatosBusquedaLocal();

        for (Pedido pendiente : List.copyOf(solucion.getPedidosNoAsignados())) {
            valor = intentarIncorporar(solucion, estado, parametros, valor, pendiente, candidatos);
        }

        for (int ruta = 0; ruta < solucion.getCantidadRutas(); ruta++) {
            // La cantidad de entregas del viaje cambia cuando un movimiento se acepta, así que
            // se relee en cada vuelta en lugar de fijarla al entrar.
            for (int posicion = 0; posicion < solucion.getRuta(ruta).getPedidos().size(); posicion++) {
                double tras = intentarReubicar(
                        solucion, estado, parametros, valor, ruta, posicion, candidatos);
                if (tras < valor - EPSILON) {
                    valor = tras;
                    continue;
                }
                valor = intentarIntercambiar(
                        solucion, estado, parametros, valor, ruta, posicion, candidatos);
            }
        }
        return valor;
    }

    /** Mete un pedido sin asignar en alguno de los viajes cercanos a su destino. */
    private double intentarIncorporar(
            Solucion solucion,
            EstadoOperacion estado,
            Parametros parametros,
            double valor,
            Pedido pedido,
            int candidatos
    ) {
        int indiceEnLaLista = solucion.removerPedidoNoAsignado(pedido);
        if (indiceEnLaLista < 0) {
            return valor;
        }

        for (int destino : viajesCercanos(solucion, pedido, candidatos)) {
            Ruta viaje = solucion.getRuta(destino);
            if (viaje.getCapacidadDisponible() < pedido.getCantidad()) {
                continue;
            }
            for (int posicion = 0; posicion <= viaje.getPedidos().size(); posicion++) {
                viaje.insertarPedido(posicion, pedido);
                double candidato = evaluador.objetivo(solucion, estado, parametros);
                if (candidato < valor - EPSILON) {
                    return candidato;
                }
                viaje.retirarPedido(posicion);
            }
        }

        solucion.agregarPedidoNoAsignado(indiceEnLaLista, pedido);
        return valor;
    }

    /** Saca la entrega de su viaje y la prueba en otro, o en otra posición del mismo. */
    private double intentarReubicar(
            Solucion solucion,
            EstadoOperacion estado,
            Parametros parametros,
            double valor,
            int rutaOrigen,
            int posicionOrigen,
            int candidatos
    ) {
        Ruta origen = solucion.getRuta(rutaOrigen);
        Pedido pedido = origen.retirarPedido(posicionOrigen);

        for (int destino : viajesCercanos(solucion, pedido, candidatos)) {
            Ruta viaje = solucion.getRuta(destino);
            if (destino != rutaOrigen && viaje.getCapacidadDisponible() < pedido.getCantidad()) {
                continue;
            }
            for (int posicion = 0; posicion <= viaje.getPedidos().size(); posicion++) {
                if (destino == rutaOrigen && posicion == posicionOrigen) {
                    continue;
                }
                viaje.insertarPedido(posicion, pedido);
                double candidato = evaluador.objetivo(solucion, estado, parametros);
                if (candidato < valor - EPSILON) {
                    return candidato;
                }
                viaje.retirarPedido(posicion);
            }
        }

        origen.insertarPedido(posicionOrigen, pedido);
        return valor;
    }

    /** Permuta la entrega con otra de un viaje cercano. */
    private double intentarIntercambiar(
            Solucion solucion,
            EstadoOperacion estado,
            Parametros parametros,
            double valor,
            int rutaOrigen,
            int posicionOrigen,
            int candidatos
    ) {
        Ruta origen = solucion.getRuta(rutaOrigen);
        Pedido pedido = origen.getPedidos().get(posicionOrigen);

        for (int destino : viajesCercanos(solucion, pedido, candidatos)) {
            if (destino == rutaOrigen) {
                continue;
            }
            Ruta viaje = solucion.getRuta(destino);
            for (int posicion = 0; posicion < viaje.getPedidos().size(); posicion++) {
                Pedido otro = viaje.getPedidos().get(posicion);
                if (!caben(origen, pedido, otro) || !caben(viaje, otro, pedido)) {
                    continue;
                }

                origen.reemplazarPedido(posicionOrigen, otro);
                viaje.reemplazarPedido(posicion, pedido);
                double candidato = evaluador.objetivo(solucion, estado, parametros);
                if (candidato < valor - EPSILON) {
                    return candidato;
                }
                origen.reemplazarPedido(posicionOrigen, pedido);
                viaje.reemplazarPedido(posicion, otro);
            }
        }
        return valor;
    }

    /** ¿El viaje admite cambiar una entrega por otra sin pasarse de capacidad? */
    private boolean caben(Ruta viaje, Pedido sale, Pedido entra) {
        int carga = viaje.getCargaTotal() - sale.getCantidad() + entra.getCantidad();
        return carga <= viaje.getVehiculo().getCapacidad();
    }

    /**
     * Índices de los viajes más cercanos al destino del pedido, medidos en línea recta sobre la
     * retícula contra el almacén del viaje y contra sus entregas. Es una cota inferior del
     * recorrido real, suficiente para descartar los viajes que operan al otro lado de la ciudad.
     */
    private List<Integer> viajesCercanos(Solucion solucion, Pedido pedido, int cuantos) {
        int total = solucion.getCantidadRutas();
        List<Integer> indices = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            indices.add(i);
        }
        if (total <= cuantos) {
            return indices;
        }

        double[] cercania = new double[total];
        for (int i = 0; i < total; i++) {
            cercania[i] = cercaniaDe(solucion.getRuta(i), pedido);
        }
        indices.sort(Comparator.comparingDouble(i -> cercania[i]));
        return List.copyOf(indices.subList(0, cuantos));
    }

    private double cercaniaDe(Ruta viaje, Pedido pedido) {
        double menor = pedido.getDestino().distanciaManhattanKm(viaje.getAlmacen().getUbicacion());
        for (Pedido entrega : viaje.getPedidos()) {
            menor = Math.min(menor, pedido.getDestino().distanciaManhattanKm(entrega.getDestino()));
        }
        return menor;
    }
}
