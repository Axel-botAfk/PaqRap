package com.paqrap.planificador;

import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Ubicacion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bloqueos vigentes en cada instante.
 *
 * El conjunto de nodos cerrados no cambia de manera continua: solo se modifica cuando empieza
 * o termina un bloqueo. El mapa aprovecha eso indexando los instantes de cambio, de modo que
 * responder por un instante cualquiera cuesta una búsqueda binaria y el conjunto de nodos de
 * cada ventana se calcula una sola vez. El enrutador usa ese número de ventana para saber
 * cuándo puede reutilizar un recorrido ya calculado.
 */
public final class MapaBloqueos {
    /** Ventana anterior al primer bloqueo del archivo: no hay ningún nodo cerrado. */
    public static final int VENTANA_SIN_BLOQUEOS = -1;

    private final List<Bloqueo> bloqueos;
    private final List<LocalDateTime> instantesDeCambio;
    private final Map<Integer, Set<Ubicacion>> nodosPorVentana = new HashMap<>();

    public MapaBloqueos(List<Bloqueo> bloqueos) {
        this.bloqueos = List.copyOf(bloqueos);

        TreeSet<LocalDateTime> cambios = new TreeSet<>();
        for (Bloqueo bloqueo : this.bloqueos) {
            cambios.add(bloqueo.inicio());
            cambios.add(bloqueo.fin());
        }
        this.instantesDeCambio = List.copyOf(new ArrayList<>(cambios));
    }

    public static MapaBloqueos vacio() {
        return new MapaBloqueos(List.of());
    }

    public List<Bloqueo> getBloqueos() {
        return bloqueos;
    }

    public boolean estaVacio() {
        return bloqueos.isEmpty();
    }

    /**
     * Identifica el intervalo de tiempo durante el cual el conjunto de nodos cerrados no
     * cambia. Dos instantes con la misma ventana ven exactamente la misma ciudad.
     */
    public int ventanaDe(LocalDateTime instante) {
        int posicion = Collections.binarySearch(instantesDeCambio, instante);
        if (posicion >= 0) {
            return posicion;
        }
        return -posicion - 2;
    }

    /**
     * Indica si entre dos instantes empieza o termina algún bloqueo. Si no ocurre ningún
     * cambio, la ciudad que ve una unidad al salir es la misma que encuentra al llegar, y
     * basta con resolver el recorrido una sola vez.
     *
     * El intervalo se evalúa como (desde, hasta]: un cambio justo en el instante de llegada
     * sí cuenta, porque podría cerrar el nodo de destino.
     */
    public boolean hayCambioEntre(LocalDateTime desde, LocalDateTime hasta) {
        if (bloqueos.isEmpty() || !hasta.isAfter(desde)) {
            return false;
        }
        int posicion = Collections.binarySearch(instantesDeCambio, desde);
        int siguiente = posicion >= 0 ? posicion + 1 : -posicion - 1;
        return siguiente < instantesDeCambio.size()
                && !instantesDeCambio.get(siguiente).isAfter(hasta);
    }

    public Set<Ubicacion> nodosBloqueadosEn(LocalDateTime instante) {
        return nodosDeVentana(ventanaDe(instante), instante);
    }

    public boolean estaBloqueado(Ubicacion nodo, LocalDateTime instante) {
        return nodosBloqueadosEn(instante).contains(nodo);
    }

    /**
     * Nodos cerrados durante una ventana. Se calcula al primer pedido y se conserva, porque
     * la búsqueda consulta la misma ventana muchas veces seguidas.
     */
    public Set<Ubicacion> nodosDeVentana(int ventana, LocalDateTime instanteDeReferencia) {
        if (ventana == VENTANA_SIN_BLOQUEOS || bloqueos.isEmpty()) {
            return Set.of();
        }
        return nodosPorVentana.computeIfAbsent(ventana, clave -> {
            Set<Ubicacion> nodos = new HashSet<>();
            for (Bloqueo bloqueo : bloqueos) {
                if (bloqueo.estaVigente(instanteDeReferencia)) {
                    nodos.addAll(bloqueo.nodos());
                }
            }
            return Set.copyOf(nodos);
        });
    }
}
