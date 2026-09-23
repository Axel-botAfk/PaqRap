package com.paqrap.planificador.ruteo;

import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Ciudad;
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
 * Lo que se cierra son nodos: por un nodo bloqueado no se pasa ni se gira, de modo que una
 * unidad que llega hasta él tiene que volver por donde vino. Eso no lo vuelve inalcanzable: se
 * puede entrar a entregar y salir dando media vuelta. Queda sin acceso solo el nodo que tiene
 * todos sus vecinos cerrados, porque entonces no hay por dónde entrar.
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

    /**
     * Primer instante, a partir del indicado, en que empieza o termina algún bloqueo.
     *
     * Es lo que permite replanificar justo cuando la ciudad cambia, en lugar de esperar al
     * siguiente punto de una grilla fija.
     *
     * @return el instante, o {@code null} si ya no queda ningún cambio por delante.
     */
    public LocalDateTime proximoCambioDesde(LocalDateTime instante) {
        int posicion = Collections.binarySearch(instantesDeCambio, instante);
        int siguiente = posicion >= 0 ? posicion : -posicion - 1;
        return siguiente < instantesDeCambio.size() ? instantesDeCambio.get(siguiente) : null;
    }

    /**
     * Hasta cuándo se puede llegar a un nodo sin encontrarlo incomunicado.
     *
     * Un nodo queda sin acceso solo cuando <b>todos</b> sus vecinos están cerrados a la vez:
     * mientras le quede uno abierto, la unidad llega hasta ahí y entra a entregar dando después
     * media vuelta. Que el propio nodo esté bloqueado no le quita el acceso. Se busca la primera
     * ventana de acceso a partir de {@code desde} —si ya está incomunicado, la que empieza
     * cuando se libere alguno de sus vecinos— y se devuelve el instante en que esa ventana
     * termina.
     *
     * Es una condición local, no una comprobación de que exista camino desde el almacén; de eso
     * se encarga el enrutador al medir cada tramo. Aquí solo interesa ordenar la cola.
     *
     * @param hasta tope de la búsqueda; más allá de la fecha límite del pedido no interesa.
     * @return el instante del cierre, o {@code null} si el acceso sigue abierto hasta el tope.
     */
    public LocalDateTime finDeLaVentanaDeAcceso(
            Ubicacion nodo,
            LocalDateTime desde,
            LocalDateTime hasta
    ) {
        if (bloqueos.isEmpty() || hasta.isBefore(desde)) {
            return null;
        }

        int posicion = Collections.binarySearch(instantesDeCambio, desde);
        int siguiente = posicion >= 0 ? posicion + 1 : -posicion - 1;

        boolean abierto = !estaAislado(nodo, desde);
        for (int i = siguiente; i < instantesDeCambio.size(); i++) {
            LocalDateTime cambio = instantesDeCambio.get(i);
            if (cambio.isAfter(hasta)) {
                break;
            }
            boolean cerradoAhora = sinAcceso(nodo, nodosDeVentana(i, cambio));
            if (abierto && cerradoAhora) {
                return cambio;
            }
            abierto = !cerradoAhora;
        }

        // Nunca se abrió dentro del tope: no hay ventana de acceso que defender.
        return abierto ? null : hasta;
    }

    public Set<Ubicacion> nodosBloqueadosEn(LocalDateTime instante) {
        return nodosDeVentana(ventanaDe(instante), instante);
    }

    public boolean estaBloqueado(Ubicacion nodo, LocalDateTime instante) {
        return nodosBloqueadosEn(instante).contains(nodo);
    }

    /**
     * ¿Están cerrados todos los vecinos de este nodo?
     *
     * Es la única situación en que no se le puede entregar: sin un vecino abierto no hay desde
     * dónde entrar. Que el nodo mismo esté bloqueado no basta, porque se entra y se sale dando
     * media vuelta.
     */
    public boolean estaAislado(Ubicacion nodo, LocalDateTime instante) {
        return sinAcceso(nodo, nodosBloqueadosEn(instante));
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

    private static boolean sinAcceso(Ubicacion nodo, Set<Ubicacion> cerrados) {
        if (cerrados.isEmpty()) {
            return false;
        }
        for (Ubicacion vecino : vecinos(nodo)) {
            if (!cerrados.contains(vecino)) {
                return false;
            }
        }
        return true;
    }

    private static List<Ubicacion> vecinos(Ubicacion nodo) {
        List<Ubicacion> vecinos = new ArrayList<>(4);
        if (nodo.x() > 0) {
            vecinos.add(new Ubicacion(nodo.x() - 1, nodo.y()));
        }
        if (nodo.x() < Ciudad.ANCHO_KM) {
            vecinos.add(new Ubicacion(nodo.x() + 1, nodo.y()));
        }
        if (nodo.y() > 0) {
            vecinos.add(new Ubicacion(nodo.x(), nodo.y() - 1));
        }
        if (nodo.y() < Ciudad.ALTO_KM) {
            vecinos.add(new Ubicacion(nodo.x(), nodo.y() + 1));
        }
        return vecinos;
    }
}
