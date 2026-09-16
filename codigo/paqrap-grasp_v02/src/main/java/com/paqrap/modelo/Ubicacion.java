package com.paqrap.modelo;

import java.util.Objects;

/**
 * Identificador lógico de una ubicación.
 *
 * Decisión de diseño para la primera iteración:
 * todavía no se acopla el planificador a coordenadas ni a un grafo real.
 * El identificador podrá mapearse posteriormente a un nodo del componente de rutas.
 */
public final class Ubicacion {
    private final String id;

    public Ubicacion(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id de la ubicación es obligatorio.");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ubicacion that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
