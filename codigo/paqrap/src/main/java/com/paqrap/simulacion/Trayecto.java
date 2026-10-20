package com.paqrap.simulacion;

import com.paqrap.modelo.Ubicacion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que una unidad recorrió durante un salto del reloj: los puntos por los que pasó y a qué
 * hora.
 *
 * Solo se anotan los hitos —la salida, la llegada al almacén, cada entrega, el fin del
 * acondicionamiento y la esquina en la que la encontró el corte—, no cada cuadra. Entre dos
 * pasos la unidad viaja por la retícula; quien dibuja puede interpolar sabiendo que llega al
 * siguiente punto a la hora indicada.
 *
 * Es información de presentación: el simulador la produce al ejecutar el plan y no la vuelve a
 * leer, así que no cambia ninguna decisión del planificador.
 */
public record Trayecto(String unidadId, List<Paso> pasos) {

    public Trayecto {
        pasos = List.copyOf(pasos);
    }

    /** Qué ocurre en cada punto del recorrido. */
    public enum Hito { SALIDA, ALMACEN, ENTREGA, FIN_ENTREGA, CORTE }

    /**
     * La unidad está en {@code ubicacion} a la hora {@code instante}.
     *
     * @param pedidoId pedido entregado en ese punto; nulo si no es una entrega.
     */
    public record Paso(Ubicacion ubicacion, LocalDateTime instante, Hito hito, String pedidoId) { }
}
