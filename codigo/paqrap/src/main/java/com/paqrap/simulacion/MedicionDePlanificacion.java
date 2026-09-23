package com.paqrap.simulacion;

import java.time.LocalDateTime;

/**
 * Lo que costó una planificación y sobre cuánto trabajo.
 *
 * Se registra una por corrida del planificador para poder responder la pregunta que importa al
 * dimensionar el salto del algoritmo: <b>Ta no es un número, es un rango</b>. Planificar con
 * cinco pedidos en cola no cuesta lo mismo que hacerlo con doscientos, y el Sa tiene que
 * aguantar el peor caso —el de la cola más larga, cerca del colapso—, no el promedio.
 *
 * @param instante       momento de la operación en que se planificó.
 * @param pedidosEnCola  pendientes que entraron a esa planificación.
 * @param milisegundos   Ta observado.
 */
public record MedicionDePlanificacion(
        LocalDateTime instante,
        int pedidosEnCola,
        long milisegundos
) {
}
