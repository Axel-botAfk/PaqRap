package com.paqrap.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mantenimiento preventivo programado: qué unidad sale de servicio cada día.
 *
 * Una unidad con mantenimiento programado no está disponible para asignarle rutas desde las
 * 00:00 hasta las 23:59 de ese día.
 */
public final class PlanMantenimiento {
    private final Map<LocalDate, Set<String>> unidadesPorDia = new HashMap<>();

    public PlanMantenimiento(Map<LocalDate, Set<String>> unidadesPorDia) {
        Objects.requireNonNull(unidadesPorDia);
        for (Map.Entry<LocalDate, Set<String>> dia : unidadesPorDia.entrySet()) {
            this.unidadesPorDia.put(dia.getKey(), Set.copyOf(dia.getValue()));
        }
    }

    public static PlanMantenimiento vacio() {
        return new PlanMantenimiento(Map.of());
    }

    public boolean enMantenimiento(String unidadId, LocalDateTime instante) {
        return unidadesPorDia
                .getOrDefault(instante.toLocalDate(), Set.of())
                .contains(unidadId);
    }

    public Set<String> unidadesDelDia(LocalDate dia) {
        return unidadesPorDia.getOrDefault(dia, Set.of());
    }

    public int getCantidadDeJornadas() {
        return unidadesPorDia.size();
    }

    /** Une dos planes, por ejemplo los de dos archivos bimensuales consecutivos. */
    public PlanMantenimiento mas(PlanMantenimiento otro) {
        Map<LocalDate, Set<String>> union = new HashMap<>();
        for (Map.Entry<LocalDate, Set<String>> dia : unidadesPorDia.entrySet()) {
            union.put(dia.getKey(), new HashSet<>(dia.getValue()));
        }
        for (Map.Entry<LocalDate, Set<String>> dia : otro.unidadesPorDia.entrySet()) {
            union.computeIfAbsent(dia.getKey(), fecha -> new HashSet<>()).addAll(dia.getValue());
        }
        return new PlanMantenimiento(union);
    }
}
