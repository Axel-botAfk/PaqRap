package com.paqrap.planificador;

import com.paqrap.modelo.Ciudad;
import com.paqrap.modelo.Ubicacion;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Recorrido más corto sobre la retícula sin atravesar nodos cerrados.
 *
 * <h2>Un nodo cerrado es absorbente, no prohibido</h2>
 *
 * Por un nodo bloqueado no se pasa ni se gira: la unidad que llega hasta él tiene que volver por
 * donde vino. Pero <b>llegar sí puede</b>, y por eso puede entregarle a un cliente ubicado sobre
 * el tramo cerrado: entra, deja el paquete y sale dando media vuelta. Es lo que sostiene la
 * garantía del caso de que en una poligonal abierta se llega a todos sus puntos.
 *
 * La búsqueda en anchura lo refleja de la forma más simple posible: a un nodo cerrado se le
 * registra la distancia, de modo que sirve como destino, pero nunca se expande, de modo que no
 * sirve como paso hacia ningún otro lado.
 *
 * Todas las aristas de la ciudad miden lo mismo, de modo que no hace falta A*: un recorrido en
 * anchura desde el origen da de una sola vez la distancia a todos los nodos alcanzables.
 *
 * <h2>Cómo se combina con la hora</h2>
 *
 * Una unidad no puede pasar por una esquina que estará cerrada cuando llegue a ella, aunque
 * estuviera abierta al momento de salir. Resolver eso nodo por nodo en cada consulta sería
 * caro, así que se aprovecha que los bloqueos solo cambian en instantes conocidos:
 *
 * <ul>
 *   <li>Se calcula el recorrido con la ciudad tal como está al salir. Ese resultado se guarda
 *       por origen y se reutiliza mientras no cambien los bloqueos.</li>
 *   <li>Si durante el tramo no empieza ni termina ningún bloqueo, la ciudad no cambió en todo
 *       el trayecto y ese recorrido <b>es</b> el exacto.</li>
 *   <li>Si algo cambia, se recorre el camino encontrado esquina por esquina comprobando que
 *       siga abierta en el momento en que la unidad pasaría por ella. Son unas decenas de
 *       comprobaciones y casi siempre alcanzan, porque lo que cambia suele estar en otra
 *       parte de la ciudad.</li>
 *   <li>Solo si el camino queda efectivamente cortado se resuelve de nuevo, ubicando cada
 *       esquina en el tiempo. Es el caso verdaderamente raro.</li>
 * </ul>
 *
 * El recorrido exacto nunca hace esperar a la unidad a que una calle reabra: avanza sin
 * detenerse. En el peor caso, entonces, informa un camino válido más largo que el óptimo, o
 * declara inalcanzable un destino al que se llegaría esperando frente a la barrera. Nunca al
 * revés: no planifica atravesar una esquina que estará cerrada cuando la unidad pase por ella.
 *
 * <h2>Lo que no modela</h2>
 *
 * La media vuelta obliga a salir del nodo cerrado por la misma calle por la que se entró. Aquí
 * no se arrastra esa dirección de entrada de un tramo al siguiente, así que el tramo que sale de
 * una entrega hecha sobre un nodo cerrado puede quedar hasta 2 km por debajo de lo real. Solo
 * ocurre en las entregas sobre un tramo bloqueado, que son una minoría.
 */
public final class EnrutadorBloqueos implements CalculadorDistancia {
    private static final int NODOS_POR_FILA = Ciudad.ANCHO_KM + 1;
    private static final int TOTAL_NODOS = NODOS_POR_FILA * (Ciudad.ALTO_KM + 1);
    private static final int NO_ALCANZABLE = -1;
    private static final long NANOS_POR_HORA = 3_600_000_000_000L;

    /**
     * Tope de orígenes distintos conservados por ventana; al superarlo se descarta la ventana.
     *
     * Cada recorrido guardado ocupa dos arreglos del tamaño de la ciudad, unos 29 KB, así que
     * el tope acota la memoria del caché: con 128 orígenes por ventana y 16 ventanas queda en
     * unos 59 MB. Los orígenes que de verdad se repiten son los tres almacenes, las posiciones
     * de las unidades y los últimos clientes visitados.
     */
    private static final int MAXIMO_ORIGENES_EN_CACHE = 128;

    /**
     * Cuántas ventanas de bloqueo se conservan a la vez.
     *
     * Guardar una sola no alcanza: al evaluar el programa de una unidad el reloj avanza horas y
     * va cruzando ventanas, de modo que con un único juego de recorridos se descartaría y
     * recalcularía en casi cada tramo. Con los archivos reales, que traen cientos de cierres al
     * mes, eso multiplicaba por cuarenta el tiempo de planificación.
     */
    private static final int VENTANAS_EN_CACHE = 16;

    /**
     * Decide si por el nodo se puede seguir avanzando, sabiendo que la unidad llegaría a él en el
     * paso indicado. Un nodo cerrado responde que no: se llega, pero no se continúa.
     */
    @FunctionalInterface
    private interface Admision {
        boolean dejaSeguir(int nodo, int paso);
    }

    /** Distancias desde un origen y el árbol de caminos que las produce. */
    private record Recorrido(int[] distancia, int[] anterior) {
    }

    private final MapaBloqueos mapa;

    /** Recorridos ya calculados, por ventana de bloqueo y origen; las ventanas viejas se sueltan. */
    private final Map<Integer, Map<Ubicacion, Recorrido>> recorridosPorVentana =
            new LinkedHashMap<>(VENTANAS_EN_CACHE * 2, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Map<Ubicacion, Recorrido>> vieja) {
                    return size() > VENTANAS_EN_CACHE;
                }
            };

    public EnrutadorBloqueos(MapaBloqueos mapa) {
        this.mapa = Objects.requireNonNull(mapa, "El mapa de bloqueos es obligatorio.");
    }

    @Override
    public LocalDateTime finDelAccesoA(
            Ubicacion destino,
            LocalDateTime desde,
            LocalDateTime hasta
    ) {
        return mapa.finDeLaVentanaDeAcceso(destino, desde, hasta);
    }

    @Override
    public double calcularKm(
            Ubicacion origen,
            Ubicacion destino,
            LocalDateTime instanteSalida,
            double velocidadKmH
    ) {
        if (origen.equals(destino)) {
            return 0.0;
        }

        Recorrido recorrido = recorridoDesde(origen, instanteSalida);
        int meta = indice(destino);
        int pasos = recorrido.distancia()[meta];
        if (pasos == NO_ALCANZABLE) {
            return Double.POSITIVE_INFINITY;
        }

        boolean vigente = !cruzaUnCambio(instanteSalida, pasos, velocidadKmH)
                || siguenAbiertasAlPasar(recorrido, meta, instanteSalida, velocidadKmH);
        if (vigente) {
            return pasos * Ciudad.KM_POR_ARISTA;
        }

        int exactos = recorrer(
                indice(origen),
                admisionPorHoraDePaso(instanteSalida, velocidadKmH),
                null
        )[meta];
        return exactos == NO_ALCANZABLE
                ? Double.POSITIVE_INFINITY
                : exactos * Ciudad.KM_POR_ARISTA;
    }

    /**
     * Secuencia de nodos que recorre la unidad, para el monitoreo en el mapa. Siempre se
     * resuelve ubicando cada esquina en el tiempo, porque no está en el camino crítico.
     *
     * @return el camino desde el origen hasta el destino, o una lista vacía si no es alcanzable.
     */
    public List<Ubicacion> camino(
            Ubicacion origen,
            Ubicacion destino,
            LocalDateTime instanteSalida,
            double velocidadKmH
    ) {
        if (origen.equals(destino)) {
            return List.of(origen);
        }

        int inicio = indice(origen);
        int meta = indice(destino);

        int[] anterior = new int[TOTAL_NODOS];
        Arrays.fill(anterior, NO_ALCANZABLE);
        recorrer(inicio, admisionPorHoraDePaso(instanteSalida, velocidadKmH), anterior);

        if (anterior[meta] == NO_ALCANZABLE) {
            return List.of();
        }

        List<Ubicacion> camino = new ArrayList<>();
        for (int nodo = meta; nodo != inicio; nodo = anterior[nodo]) {
            camino.add(ubicacion(nodo));
        }
        camino.add(origen);
        Collections.reverse(camino);
        return List.copyOf(camino);
    }

    /**
     * Recorre el camino encontrado comprobando que cada esquina intermedia siga abierta en el
     * momento en que la unidad pasaría por ella.
     *
     * Quedan exentos los dos extremos: el origen porque la unidad ya está ahí, y el destino
     * porque a un nodo cerrado se le puede entregar entrando y saliendo en media vuelta.
     */
    private boolean siguenAbiertasAlPasar(
            Recorrido recorrido,
            int meta,
            LocalDateTime salida,
            double velocidadKmH
    ) {
        double horasPorArista = Ciudad.KM_POR_ARISTA / velocidadKmH;
        for (int nodo = recorrido.anterior()[meta];
                recorrido.distancia()[nodo] > 0;
                nodo = recorrido.anterior()[nodo]) {
            long nanos = Math.round(recorrido.distancia()[nodo] * horasPorArista * NANOS_POR_HORA);
            if (mapa.estaBloqueado(ubicacion(nodo), salida.plusNanos(nanos))) {
                return false;
            }
        }
        return true;
    }

    /** ¿Empieza o termina algún bloqueo mientras la unidad recorre el tramo? */
    private boolean cruzaUnCambio(LocalDateTime salida, int pasos, double velocidadKmH) {
        double horas = pasos * Ciudad.KM_POR_ARISTA / velocidadKmH;
        return mapa.hayCambioEntre(salida, salida.plusNanos(Math.round(horas * NANOS_POR_HORA)));
    }

    private Admision admisionPorHoraDePaso(LocalDateTime salida, double velocidadKmH) {
        double horasPorArista = Ciudad.KM_POR_ARISTA / velocidadKmH;
        return (nodo, paso) -> !mapa.estaBloqueado(
                ubicacion(nodo),
                salida.plusNanos(Math.round(paso * horasPorArista * NANOS_POR_HORA))
        );
    }

    /** Recorrido desde un origen con la ciudad tal como está al salir. */
    private Recorrido recorridoDesde(Ubicacion origen, LocalDateTime instante) {
        int ventana = mapa.ventanaDe(instante);
        Map<Ubicacion, Recorrido> recorridosPorOrigen =
                recorridosPorVentana.computeIfAbsent(ventana, clave -> new HashMap<>());
        if (recorridosPorOrigen.size() >= MAXIMO_ORIGENES_EN_CACHE) {
            recorridosPorOrigen.clear();
        }

        Recorrido conocido = recorridosPorOrigen.get(origen);
        if (conocido != null) {
            return conocido;
        }

        boolean[] cerrado = nodosCerrados(instante);
        int[] anterior = new int[TOTAL_NODOS];
        Arrays.fill(anterior, NO_ALCANZABLE);
        int[] distancia = recorrer(indice(origen), (nodo, paso) -> !cerrado[nodo], anterior);

        Recorrido calculado = new Recorrido(distancia, anterior);
        recorridosPorOrigen.put(origen, calculado);
        return calculado;
    }

    /**
     * Recorrido en anchura desde un nodo.
     *
     * A un nodo cerrado se le registra la distancia —se puede llegar a entregarle— pero no se lo
     * expande, porque de ahí solo se sale dando media vuelta y no lleva a ninguna parte. El
     * origen siempre se expande: la unidad ya está ahí y tiene que poder salir. Si se entrega
     * {@code anterior}, queda registrado el árbol de caminos.
     */
    private int[] recorrer(int inicio, Admision admision, int[] anterior) {
        int[] distancia = new int[TOTAL_NODOS];
        Arrays.fill(distancia, NO_ALCANZABLE);
        distancia[inicio] = 0;
        if (anterior != null) {
            anterior[inicio] = inicio;
        }

        Deque<Integer> cola = new ArrayDeque<>();
        cola.add(inicio);

        while (!cola.isEmpty()) {
            int actual = cola.poll();
            int siguientePaso = distancia[actual] + 1;
            for (int vecino : vecinos(actual)) {
                if (distancia[vecino] != NO_ALCANZABLE) {
                    continue;
                }
                distancia[vecino] = siguientePaso;
                if (anterior != null) {
                    anterior[vecino] = actual;
                }
                if (admision.dejaSeguir(vecino, siguientePaso)) {
                    cola.add(vecino);
                }
            }
        }

        return distancia;
    }

    private boolean[] nodosCerrados(LocalDateTime instante) {
        boolean[] cerrado = new boolean[TOTAL_NODOS];
        for (Ubicacion nodo : mapa.nodosBloqueadosEn(instante)) {
            cerrado[indice(nodo)] = true;
        }
        return cerrado;
    }

    private int[] vecinos(int nodo) {
        int x = nodo % NODOS_POR_FILA;
        int y = nodo / NODOS_POR_FILA;

        int cantidad = 0;
        int[] resultado = new int[4];
        if (x > 0) {
            resultado[cantidad++] = nodo - 1;
        }
        if (x < Ciudad.ANCHO_KM) {
            resultado[cantidad++] = nodo + 1;
        }
        if (y > 0) {
            resultado[cantidad++] = nodo - NODOS_POR_FILA;
        }
        if (y < Ciudad.ALTO_KM) {
            resultado[cantidad++] = nodo + NODOS_POR_FILA;
        }
        return Arrays.copyOf(resultado, cantidad);
    }

    private static int indice(Ubicacion nodo) {
        return nodo.y() * NODOS_POR_FILA + nodo.x();
    }

    private static Ubicacion ubicacion(int nodo) {
        return new Ubicacion(nodo % NODOS_POR_FILA, nodo / NODOS_POR_FILA);
    }
}
