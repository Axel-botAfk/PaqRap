package com.paqrap.planificador;

import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Vehiculo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Coloca por partes los pedidos que no caben enteros en ninguna unidad.
 *
 * <h2>Por qué el planificador y no el simulador</h2>
 *
 * Partir un pedido era hasta ahora un remiendo posterior a la planificación, y solo se aplicaba
 * cuando el pedido superaba la capacidad del vehículo más grande. O sea que un pedido de quince
 * unidades, que cabe de sobra en un auto vacío, lo rechazaban los tres algoritmos si en ese
 * momento no quedaba ningún auto con quince libres, aunque hubiera dos con ocho y siete.
 *
 * Con el producto como variable principal eso no se sostiene: lo que se entrega son productos, y
 * si hay sitio para los quince repartidos entre dos unidades, hay que usarlo. Cerca del colapso es
 * la diferencia entre atender y no atender, porque es justo cuando la capacidad queda fragmentada.
 *
 * <h2>Cuándo actúa</h2>
 *
 * Como pasada final, después de que el algoritmo hizo lo suyo, y solo sobre lo que quedó sin
 * asignar. No se mete en la búsqueda: partir dentro de cada vecindario multiplicaría el espacio de
 * exploración por la cantidad de formas de trocear cada pedido, y el costo no lo justifica cuando
 * el caso que importa —capacidad fragmentada— se detecta igual de bien al final.
 *
 * Solo intenta partir lo que de verdad lo necesita. Si el pedido cabía entero en algún sitio, el
 * algoritmo ya lo habría colocado; si no cupo por plazo o por camino cerrado, partirlo tampoco
 * ayuda, y la prueba de factibilidad lo descarta sola.
 *
 * <h2>Se atiende primero al más urgente</h2>
 *
 * El orden es por urgencia y no por tamaño, igual que en el resto del planificador: la capacidad
 * suelta que queda es escasa, y gastarla en un pedido holgado para después no poder atender a uno
 * que vence es exactamente lo que acorta la operación.
 */
public final class RepartoParcial {

    /** Cuántos viajes se prueban por pedido. Los de más sitio libre primero. */
    private static final int VIAJES_A_PROBAR = 8;

    private final Evaluador evaluador;

    public RepartoParcial(Evaluador evaluador) {
        this.evaluador = Objects.requireNonNull(evaluador);
    }

    /**
     * Intenta colocar por partes lo que quedó sin asignar, modificando la solución en el sitio.
     *
     * @return cuántos pedidos se llegaron a partir.
     */
    public int repartir(Solucion solucion, EstadoOperacion estado) {
        List<Pedido> candidatos = new ArrayList<>(solucion.getPedidosNoAsignados());
        if (candidatos.isEmpty()) {
            return 0;
        }

        // Una unidad interrumpida a mitad de viaje puede haber quedado sin ningun viaje en el
        // plan, porque el algoritmo no le encontro nada que darle y los viajes vacios se depuran.
        // Su carga a bordo sigue siendo capacidad utilizable, y es justo la que hace falta cuando
        // lo que queda esta fragmentado, asi que se le devuelve el viaje antes de repartir.
        sembrarViajesConCargaABordo(solucion, estado);
        if (solucion.getCantidadRutas() == 0) {
            return 0;
        }

        LocalDateTime reloj = estado.getReloj();
        candidatos.sort(Comparator.comparingDouble(
                (Pedido pedido) -> evaluador.urgencia(pedido, reloj)).reversed());

        int partidos = 0;
        for (Pedido pedido : candidatos) {
            if (repartirUno(solucion, estado, pedido)) {
                partidos++;
            }
        }
        return partidos;
    }

    /**
     * Reparte un pedido entre los huecos disponibles.
     *
     * Se avanza de a un trozo: se busca el viaje con más sitio libre, se le mete lo que quepa y se
     * sigue con el resto. Si no entra ni un trozo, el pedido se queda entero donde estaba.
     *
     * Lo que sobra al final no se descarta: queda como un pedido más en la bolsa de no asignados,
     * con su {@code idOriginal} intacto, de modo que la contabilidad del cliente sigue cuadrando y
     * la planificación siguiente puede terminar de servirlo.
     */
    private boolean repartirUno(Solucion solucion, EstadoOperacion estado, Pedido pedido) {
        if (cabriaEntero(solucion, pedido)) {
            // Si cabía entero en algún sitio y el algoritmo no lo puso, fue por plazo o por
            // camino, no por capacidad. Partirlo no arregla ninguna de las dos.
            return false;
        }

        Pedido resto = pedido;
        boolean seColocoAlgo = false;

        while (resto != null && resto.getCantidad() > 0) {
            Colocacion mejor = mejorTrozo(solucion, estado, resto);
            if (mejor == null) {
                break;
            }

            if (mejor.cantidad() == resto.getCantidad()) {
                aplicar(solucion, mejor, resto);
                resto = null;
            } else {
                List<Pedido> partes = resto.partirEn(mejor.cantidad());
                aplicar(solucion, mejor, partes.get(0));
                resto = partes.get(1);
            }
            seColocoAlgo = true;
        }

        if (!seColocoAlgo) {
            return false;
        }

        solucion.removerPedidoNoAsignado(pedido);
        if (resto != null) {
            solucion.agregarPedidoNoAsignado(resto);
        }
        return true;
    }

    /**
     * Devuelve un viaje ya cargado a cada unidad que lleva producto encima y no tiene ninguno.
     *
     * Solo se siembran unidades con carga: darle un viaje desde almacen a una unidad vacia seria
     * planificar de verdad, y eso le toca al algoritmo, no a esta pasada.
     */
    private void sembrarViajesConCargaABordo(Solucion solucion, EstadoOperacion estado) {
        Set<String> conViaje = new HashSet<>();
        for (Ruta viaje : solucion.getRutas()) {
            conViaje.add(viaje.getVehiculo().getId());
        }
        for (Vehiculo unidad : estado.getVehiculos()) {
            if (unidad.getEstado().admiteAsignacion()
                    && unidad.getCargaABordo() > 0
                    && !conViaje.contains(unidad.getId())) {
                solucion.agregarRuta(new Ruta(
                        "C-" + unidad.getId(), estado.getAlmacenes().get(0), unidad, true));
            }
        }
    }

    /** ¿Hay algún viaje con sitio para el pedido entero? */
    private boolean cabriaEntero(Solucion solucion, Pedido pedido) {
        for (Ruta viaje : solucion.getRutas()) {
            if (viaje.getCapacidadDisponible() >= pedido.getCantidad()) {
                return true;
            }
        }
        return false;
    }

    /**
     * El mejor trozo colocable: qué viaje, en qué posición y de qué tamaño.
     *
     * Se prueban los viajes con más sitio libre, y dentro de cada uno todas las posiciones. Se
     * queda con el que menos encarezca el plan, que es la misma regla con la que insertan los tres
     * algoritmos.
     */
    private Colocacion mejorTrozo(Solucion solucion, EstadoOperacion estado, Pedido resto) {
        List<Integer> porSitio = new ArrayList<>();
        for (int i = 0; i < solucion.getCantidadRutas(); i++) {
            if (solucion.getRuta(i).getCapacidadDisponible() > 0) {
                porSitio.add(i);
            }
        }
        porSitio.sort(Comparator.comparingInt(
                (Integer i) -> solucion.getRuta(i).getCapacidadDisponible()).reversed());

        Colocacion mejor = null;
        double mejorCosto = Double.POSITIVE_INFINITY;
        int probados = 0;

        for (int indice : porSitio) {
            if (probados++ >= VIAJES_A_PROBAR) {
                break;
            }
            Ruta viaje = solucion.getRuta(indice);
            int cabe = Math.min(viaje.getCapacidadDisponible(), resto.getCantidad());
            Pedido trozo = cabe == resto.getCantidad()
                    ? resto
                    : resto.partirEn(cabe).get(0);

            for (int posicion = 0; posicion <= viaje.getPedidos().size(); posicion++) {
                viaje.insertarPedido(posicion, trozo);
                ResultadoPlan resultado = evaluador.evaluarPlan(solucion, estado);
                viaje.retirarPedido(posicion);

                if (resultado.factible() && resultado.costoOperacion() < mejorCosto) {
                    mejorCosto = resultado.costoOperacion();
                    mejor = new Colocacion(indice, posicion, cabe);
                }
            }
        }
        return mejor;
    }

    private void aplicar(Solucion solucion, Colocacion donde, Pedido trozo) {
        solucion.getRuta(donde.indiceRuta()).insertarPedido(donde.posicion(), trozo);
    }

    /** Dónde y cuánto: viaje, posición dentro de él y unidades de producto. */
    private record Colocacion(int indiceRuta, int posicion, int cantidad) {
    }
}
