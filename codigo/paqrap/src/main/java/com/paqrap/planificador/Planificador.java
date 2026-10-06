package com.paqrap.planificador;

import com.paqrap.modelo.Solucion;

/**
 * Interfaz alineada con la estructura propuesta en el ISA.
 */
public interface Planificador {
    Solucion planificar(EstadoOperacion estado, Parametros parametros);
}
