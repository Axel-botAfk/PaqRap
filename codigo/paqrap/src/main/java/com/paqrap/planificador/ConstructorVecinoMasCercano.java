package com.paqrap.planificador;

import com.paqrap.planificador.ruteo.CalculadorDistancia;

/**
 * Constructor determinista de la solución inicial de Búsqueda Tabú.
 * En cada paso incorpora el pedido factible más próximo al extremo del programa
 * de una unidad candidata. Si debe abrir un viaje, incluye el trayecto hasta el
 * almacén de carga. La cercanía se mide en la retícula de Manhattan; el evaluador
 * valida después bloqueos, capacidad, inventario, turnos y plazos.
 * No invoca GRASP ni utiliza una solución producida por GRASP.
 */
public final class ConstructorVecinoMasCercano extends InsercionPorHolgura {
    public static final String NOMBRE = "Vecino más cercano";

    public ConstructorVecinoMasCercano(CalculadorDistancia distancias) {
        this(new Evaluador(distancias));
    }

    public ConstructorVecinoMasCercano(Evaluador evaluador) {
        super(evaluador, UNIDADES_CANDIDATAS_POR_DEFECTO,
                CriterioSeleccion.VECINO_MAS_CERCANO);
    }
}
