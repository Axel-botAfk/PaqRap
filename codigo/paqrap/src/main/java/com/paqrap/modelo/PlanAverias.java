package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Averías programadas para una corrida, ordenadas por instante.
 *
 * Se consulta por intervalos —"qué se averió entre el corte anterior y éste"— porque así avanza
 * el simulador: a saltos, no instante a instante. Una avería que cae dentro de un salto se
 * aplica al llegar al corte, que es cuando la operación se entera.
 */
public final class PlanAverias {
    private final List<Averia> averias;

    public PlanAverias(List<Averia> averias) {
        List<Averia> ordenadas = new ArrayList<>(Objects.requireNonNull(averias));
        ordenadas.sort(Comparator.comparing(Averia::instante));
        this.averias = List.copyOf(ordenadas);
    }

    public static PlanAverias vacio() {
        return new PlanAverias(List.of());
    }

    public List<Averia> getAverias() {
        return averias;
    }

    public boolean estaVacio() {
        return averias.isEmpty();
    }

    public int getCantidad() {
        return averias.size();
    }

    /** Averías ocurridas en el intervalo (desde, hasta]. */
    public List<Averia> entre(LocalDateTime desde, LocalDateTime hasta) {
        List<Averia> ocurridas = new ArrayList<>();
        for (Averia averia : averias) {
            if (averia.instante().isAfter(desde) && !averia.instante().isAfter(hasta)) {
                ocurridas.add(averia);
            }
        }
        return ocurridas;
    }

    /** Primer instante con una avería posterior al indicado, para cortar el reloj justo ahí. */
    public LocalDateTime proximaDespuesDe(LocalDateTime instante) {
        for (Averia averia : averias) {
            if (averia.instante().isAfter(instante)) {
                return averia.instante();
            }
        }
        return null;
    }
}
