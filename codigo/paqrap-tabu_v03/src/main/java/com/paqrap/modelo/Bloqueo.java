package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tramo de calles cerrado por la municipalidad durante un periodo planificado.
 *
 * El bloqueo se declara como una poligonal de nodos: cada par de vértices consecutivos define
 * un tramo, y todos los nodos de ese tramo quedan cerrados. Un nodo bloqueado no se puede
 * atravesar ni permite girar a los lados, de modo que una unidad que llegara hasta él tendría
 * que dar media vuelta; el planificador simplemente no lo usa.
 *
 * La vigencia es semiabierta: el bloqueo rige desde {@code inicio} inclusive hasta {@code fin}
 * exclusive, de modo que dos bloqueos consecutivos sobre el mismo tramo no se superponen.
 */
public record Bloqueo(LocalDateTime inicio, LocalDateTime fin, Set<Ubicacion> nodos) {

    public Bloqueo {
        Objects.requireNonNull(inicio, "El inicio del bloqueo es obligatorio.");
        Objects.requireNonNull(fin, "El fin del bloqueo es obligatorio.");
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("El fin del bloqueo debe ser posterior a su inicio.");
        }
        if (nodos == null || nodos.isEmpty()) {
            throw new IllegalArgumentException("El bloqueo debe cerrar al menos un nodo.");
        }
        nodos = Set.copyOf(nodos);
    }

    /**
     * Construye el bloqueo a partir de los vértices de la poligonal, expandiendo cada tramo
     * a los nodos que lo componen. Los tramos son horizontales o verticales, porque la ciudad
     * no tiene calles diagonales.
     */
    public static Bloqueo dePoligonal(LocalDateTime inicio, LocalDateTime fin, List<Ubicacion> vertices) {
        Objects.requireNonNull(vertices, "La poligonal es obligatoria.");
        if (vertices.isEmpty()) {
            throw new IllegalArgumentException("La poligonal debe tener al menos un vértice.");
        }

        Set<Ubicacion> nodos = new LinkedHashSet<>();
        nodos.add(vertices.get(0));
        for (int i = 1; i < vertices.size(); i++) {
            nodos.addAll(nodosDelTramo(vertices.get(i - 1), vertices.get(i)));
        }
        return new Bloqueo(inicio, fin, nodos);
    }

    public boolean estaVigente(LocalDateTime instante) {
        return !instante.isBefore(inicio) && instante.isBefore(fin);
    }

    public boolean bloquea(Ubicacion nodo, LocalDateTime instante) {
        return estaVigente(instante) && nodos.contains(nodo);
    }

    private static List<Ubicacion> nodosDelTramo(Ubicacion desde, Ubicacion hasta) {
        if (desde.x() != hasta.x() && desde.y() != hasta.y()) {
            throw new IllegalArgumentException(
                    "Un tramo bloqueado debe ser horizontal o vertical: "
                            + desde + " -> " + hasta + ".");
        }

        List<Ubicacion> nodos = new ArrayList<>();
        int pasoX = Integer.signum(hasta.x() - desde.x());
        int pasoY = Integer.signum(hasta.y() - desde.y());

        int x = desde.x();
        int y = desde.y();
        nodos.add(desde);
        while (x != hasta.x() || y != hasta.y()) {
            x += pasoX;
            y += pasoY;
            nodos.add(new Ubicacion(x, y));
        }
        return nodos;
    }

    @Override
    public String toString() {
        return inicio + " a " + fin + " | " + nodos.size() + " nodos";
    }
}
