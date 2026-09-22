package com.paqrap.simulacion;

import com.paqrap.modelo.Solucion;
import com.paqrap.planificador.EstadoOperacion;

import java.time.LocalDateTime;

/**
 * Enganche para mirar la corrida mientras ocurre.
 *
 * Se avisa una vez por planificación, con el plan recién calculado y lo que costó producirlo.
 * Es lo que permite dibujar el mapa sin que el simulador sepa nada de pantallas: quien dibuja
 * se suscribe, y el simulador solo avisa.
 *
 * El plan que llega es el que se va a ejecutar hasta el corte siguiente, no necesariamente
 * entero: una unidad solo recorre lo que alcanza a empezar antes de que el reloj llegue al
 * corte. Lo que se dibuje a partir de acá es la intención del planificador, no el resultado.
 */
@FunctionalInterface
public interface Observador {

    void alPlanificar(
            LocalDateTime reloj,
            EstadoOperacion estado,
            Solucion plan,
            MedicionDePlanificacion medicion
    );
}
