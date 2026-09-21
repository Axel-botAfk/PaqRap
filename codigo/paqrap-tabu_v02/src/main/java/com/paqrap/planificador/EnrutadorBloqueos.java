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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Recorrido más corto sobre la retícula sin atravesar tramos cerrados.
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
 */
public final class EnrutadorBloqueos implements CalculadorDistancia {
    private static final int NODOS_POR_FILA = Ciudad.ANCHO_KM + 1;
    private static final int TOTAL_NODOS = NODOS_POR_FILA * (Ciudad.ALTO_KM + 1);
    private static final int NO_ALCANZABLE = -1;
    private static final long NANOS_POR_HORA = 3_600_000_000_000L;

    /** Tope de orígenes distintos conservados; al superarlo se descarta lo acumulado. */
    private static final int MAXIMO_ORIGENES_EN_CACHE = 512;

    /** Decide si la unidad puede entrar a un nodo al que llegaría en el paso indicado. */
    @FunctionalInterface
    private interface Admision {
        boolean puedeEntrar(int nodo, int paso);
    }

    /** Distancias desde un origen y el árbol de caminos que las produce. */
    private record Recorrido(int[] distancia, int[] anterior) {
    }

    private final MapaBloqueos mapa;
    private final Map<Ubicacion, Recorrido> recorridosPorOrigen = new HashMap<>();
    private int ventanaEnCache = Integer.MIN_VALUE;

    public EnrutadorBloqueos(MapaBloqueos mapa) {
        this.mapa = Objects.requireNonNull(mapa, "El mapa de bloqueos es obligatorio.");
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
     * Recorre el camino encontrado comprobando que cada esquina siga abierta en el momento en
     * que la unidad pasaría por ella. El origen queda exento: la unidad ya está ahí.
     */
    private boolean siguenAbiertasAlPasar(
            Recorrido recorrido,
            int meta,
            LocalDateTime salida,
            double velocidadKmH
    ) {
        double horasPorArista = Ciudad.KM_POR_ARISTA / velocidadKmH;
        for (int nodo = meta; recorrido.distancia()[nodo] > 0; nodo = recorrido.anterior()[nodo]) {
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
        if (ventana != ventanaEnCache) {
            recorridosPorOrigen.clear();
            ventanaEnCache = ventana;
        }
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
     * Recorrido en anchura desde un nodo. El origen se admite aunque esté cerrado: si la unidad
     * ya se encuentra ahí, tiene que poder salir; lo que no se permite es entrar a un nodo
     * cerrado. Si se entrega {@code anterior}, queda registrado el árbol de caminos.
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
                if (distancia[vecino] != NO_ALCANZABLE || !admision.puedeEntrar(vecino, siguientePaso)) {
                    continue;
                }
                distancia[vecino] = siguientePaso;
                if (anterior != null) {
                    anterior[vecino] = actual;
                }
                cola.add(vecino);
            }
        }

        return distancia;
    }

    private boolean[] nodosCerrados(LocalDateTime instante) {
        boolean[] cerrado = new boolean[TOTAL_NODOS];
        Set<Ubicacion> bloqueados = mapa.nodosBloqueadosEn(instante);
        for (Ubicacion nodo : bloqueados) {
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
