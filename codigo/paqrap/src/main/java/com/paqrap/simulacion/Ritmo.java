package com.paqrap.simulacion;

import java.time.Duration;
import java.util.Objects;

/**
 * Ritmo de la planificación programada: cada cuánto se lanza el planificador en tiempo real y
 * cuánto tiempo de la operación se consume en cada lanzamiento.
 *
 * <h2>Los tres tiempos</h2>
 *
 * <ul>
 *   <li><b>Ta</b>, tiempo de ejecución del algoritmo: lo que demora una planificación. No se
 *       fija, se mide, y depende de cuántos pedidos haya en cola.</li>
 *   <li><b>Sa</b>, salto del algoritmo: el tiempo <b>real</b> que transcurre entre el inicio de
 *       una planificación y el de la siguiente. Es lo que marca el pulso de la pantalla.</li>
 *   <li><b>Sc</b>, salto del consumo: el tiempo <b>de la operación</b> que avanza en cada salto.
 *       Es {@code Sa × K}.</li>
 * </ul>
 *
 * <h2>Qué es K</h2>
 *
 * La proporción entre el reloj de la operación y el reloj de la pared. Con K = 1 la simulación
 * va al ritmo del día a día: un minuto de pantalla es un minuto de operación. Con K = 14, cada
 * minuto de pantalla consume catorce minutos de operación.
 *
 * La aceleración <b>no</b> sale de hacer más rápido el algoritmo ni de optimizar el código:
 * sale de consumir más datos por vez. Subir K no cambia una sola decisión del planificador,
 * solo agranda el trozo de operación que se resuelve en cada corrida.
 *
 * <h2>Por qué Sa tiene que ser mayor que Ta</h2>
 *
 * Si el planificador tarda más de lo que dura el salto, la corrida siguiente arranca sobre una
 * que no terminó. La operación se atropella a sí misma y la solución se cae. Como Ta crece con
 * la cola de pedidos, un Sa que alcanza con demanda normal puede quedarse corto cerca del
 * colapso logístico, que es justamente cuando más importa.
 *
 * Por eso {@link #ocupacion(long)} informa qué fracción del salto ocupó cada planificación, y el
 * resumen de la corrida cuenta cuántas veces se rebasó.
 *
 * <h2>Y por qué Sc no puede ser cualquiera</h2>
 *
 * Sc es lo que de verdad decide la calidad logística: es cuánto tiempo de operación pasa sin
 * que nadie replanifique. Con un Sc de ocho o veinticuatro horas, un pedido con plazo de seis
 * horas puede llegar y vencer sin haber sido planificado nunca. Con los plazos del caso —entre
 * cuatro y diez horas— conviene mantener Sc bastante por debajo del plazo más corto.
 */
public record Ritmo(Duration saltoDelAlgoritmo, double proporcionalidad) {

    public Ritmo {
        Objects.requireNonNull(saltoDelAlgoritmo, "El salto del algoritmo (Sa) es obligatorio.");
        if (saltoDelAlgoritmo.isZero() || saltoDelAlgoritmo.isNegative()) {
            throw new IllegalArgumentException("El salto del algoritmo (Sa) debe ser positivo.");
        }
        if (proporcionalidad <= 0) {
            throw new IllegalArgumentException("La proporcionalidad (K) debe ser mayor que cero.");
        }
    }

    /** El día a día: el reloj de la operación avanza al mismo ritmo que el de la pared. */
    public static Ritmo diaADia(Duration saltoDelAlgoritmo) {
        return new Ritmo(saltoDelAlgoritmo, 1.0);
    }

    /**
     * Ritmo que comprime un horizonte dado en el tiempo de pantalla que se le quiera dedicar.
     *
     * Es la forma cómoda de plantearlo cuando lo que se sabe es "quiero mostrar cinco días en
     * veinte minutos": de ahí sale K, y con el Sa elegido queda determinado Sc.
     */
    public static Ritmo para(Duration horizonte, Duration duracionEnPantalla, Duration saltoDelAlgoritmo) {
        Objects.requireNonNull(horizonte, "El horizonte es obligatorio.");
        Objects.requireNonNull(duracionEnPantalla, "La duración en pantalla es obligatoria.");
        if (duracionEnPantalla.isZero() || duracionEnPantalla.isNegative()) {
            throw new IllegalArgumentException("La duración en pantalla debe ser positiva.");
        }
        double k = (double) horizonte.toNanos() / duracionEnPantalla.toNanos();
        return new Ritmo(saltoDelAlgoritmo, k);
    }

    /** Sc = Sa × K: el tiempo de operación que avanza en cada salto. */
    public Duration saltoDelConsumo() {
        return Duration.ofNanos(Math.round(saltoDelAlgoritmo.toNanos() * proporcionalidad));
    }

    /**
     * Qué fracción del salto ocupa una planificación que tardó lo indicado. Por debajo de 1 hay
     * margen; en 1 o más, la corrida siguiente arrancaría antes de que ésta termine.
     */
    public double ocupacion(long milisegundosDeEjecucion) {
        return milisegundosDeEjecucion / (double) saltoDelAlgoritmo.toMillis();
    }

    /** Milisegundos de Sa, para reportar y para esperar. */
    public long milisegundosDelSalto() {
        return saltoDelAlgoritmo.toMillis();
    }

    @Override
    public String toString() {
        return String.format(
                "Sa=%d ms | K=%.1f | Sc=%d min",
                milisegundosDelSalto(), proporcionalidad, saltoDelConsumo().toMinutes());
    }
}
