package com.paqrap.simulacion;

import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.EstadoOperacion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enganche para mirar la corrida mientras ocurre.
 *
 * Se avisa una vez por planificación, con el plan recién calculado y lo que costó producirlo.
 * Es lo que permite dibujar el mapa sin que el simulador sepa nada de pantallas: quien dibuja
 * se suscribe, y el simulador solo avisa.
 *
 * Junto al plan llega el {@link AvanceDeLaOperacion}, que dice lo que ya pasó: cuánto se entregó,
 * cuánto se perdió por plazo y cuánto producto viaja ahora mismo encima de las unidades. El plan
 * solo habla del futuro, y sin el pasado no se puede seguir una corrida.
 *
 * El plan que llega es el que se va a ejecutar hasta el corte siguiente, no necesariamente
 * entero: una unidad solo recorre lo que alcanza a empezar antes de que el reloj llegue al
 * corte. Lo que se dibuje a partir de acá es la intención del planificador, no el resultado.
 */
@FunctionalInterface
public interface Observador {

    /** El motor está por calcular un plan nuevo; el anterior ya no debe presentarse como vigente. */
    default void alIniciarPlanificacion(LocalDateTime reloj) { }

    void alPlanificar(
            LocalDateTime reloj,
            EstadoOperacion estado,
            Solucion plan,
            MedicionDePlanificacion medicion,
            AvanceDeLaOperacion avance
    );

    /** Posiciones ya ejecutadas al cierre de cada salto; no son rutas futuras. */
    default void alAvanzar(LocalDateTime reloj, List<Vehiculo> vehiculos) { }

    /** Inventario disponible después del salto, no el stock inicial del mapa. */
    default void alActualizarInventario(LocalDateTime reloj, List<Almacen> almacenes) { }
}
