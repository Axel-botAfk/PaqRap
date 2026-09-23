package com.paqrap.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Una unidad que se avería en un instante determinado.
 *
 * El caso no dice cómo se originan —no hay archivo de averías como el de bloqueos ni una
 * probabilidad declarada— así que aquí solo se representa el hecho. Quién las genera queda
 * fuera: pueden venir de un archivo, de un sorteo o de alguien que las dispara desde el
 * visualizador para ver cómo reacciona la operación.
 */
public record Averia(String unidadId, LocalDateTime instante, TipoAveria tipo) {

    public Averia {
        Objects.requireNonNull(unidadId, "La unidad averiada es obligatoria.");
        Objects.requireNonNull(instante, "El instante de la avería es obligatorio.");
        Objects.requireNonNull(tipo, "El tipo de avería es obligatorio.");
    }

    /** Hasta cuándo se queda la unidad donde se averió. */
    public LocalDateTime finDeLaPermanencia() {
        return instante.plus(tipo.permanenciaEnElLugar());
    }

    /** Cuándo vuelve a poder recibir rutas. */
    public LocalDateTime reingreso() {
        return tipo.reingreso(instante);
    }

    @Override
    public String toString() {
        return unidadId + " " + tipo + " a las " + instante + ", reingresa " + reingreso();
    }
}
