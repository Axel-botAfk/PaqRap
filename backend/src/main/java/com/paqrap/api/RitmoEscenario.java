package com.paqrap.api;

import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.simulacion.Ritmo;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Set;

/**
 * Ritmo de cada escenario: cuánto tiempo real dura una ventana (Sa) y cuánta operación
 * se publica en ella (Sc = Sa × K).
 *
 * Es solo presentación: el simulador replanifica con su propia cadencia y ninguna decisión
 * depende de Sa ni de Sc.
 *
 * <ul>
 *   <li>Cinco días: se fija la duración objetivo (30 a 60 min, 40 por defecto) y con Sa de
 *       20 s sale Sc. Con 40 min son 120 ventanas de una hora simulada.</li>
 *   <li>Día a día: se elige la velocidad (1×, 10×, 60× o 240×); 1× es tiempo real.</li>
 *   <li>Colapso: Sa de 10 s y dos horas por ventana, de modo que 30 días se recorren como
 *       máximo en una hora y la corrida se corta en el primer incumplimiento.</li>
 * </ul>
 *
 * Sc se mantiene en una o dos horas: el servidor calcula una ventana por delante de lo que se
 * ve, así que una avería registrada desde la pantalla entra al simulador al final de la ventana
 * visible. Con ventanas de varias horas ese desfase se notaría.
 */
record RitmoEscenario(Ritmo ritmo, Integer duracionObjetivoMin, Integer velocidad) {
    static final int DURACION_5_DIAS_POR_DEFECTO_MIN = 40;
    static final int DURACION_5_DIAS_MINIMA_MIN = 30;
    static final int DURACION_5_DIAS_MAXIMA_MIN = 60;
    static final Duration SA_5_DIAS = Duration.ofSeconds(20);
    static final Duration SA_DIA_A_DIA = Duration.ofSeconds(20);
    static final int VELOCIDAD_POR_DEFECTO = 60;
    static final Set<Integer> VELOCIDADES = Set.of(1, 10, 60, 240);
    static final Duration SA_COLAPSO = Duration.ofSeconds(10);
    static final Duration SC_COLAPSO = Duration.ofMinutes(120);

    static RitmoEscenario para(CrearEjecucionRequest solicitud, int dias) {
        Duration horizonte = Duration.ofDays(dias);
        Integer minutos = solicitud.minutosPorVentana();
        Integer segundos = solicitud.segundosPorVentana();
        if (minutos != null && (minutos < 1 || minutos > 24 * 60)) {
            throw invalido("Los minutos simulados por ventana deben estar entre 1 y 1440.");
        }
        if (segundos != null && (segundos < 1 || segundos > 600)) {
            throw invalido("Los segundos reales por ventana deben estar entre 1 y 600.");
        }

        return switch (solicitud.escenario()) {
            case PERIODO_5_DIAS -> {
                if (minutos != null || segundos != null) {
                    Duration sa = segundos == null ? SA_5_DIAS : Duration.ofSeconds(segundos);
                    Duration sc = minutos == null ? Duration.ofHours(1) : Duration.ofMinutes(minutos);
                    Ritmo ritmo = new Ritmo(sa, (double) sc.toNanos() / sa.toNanos());
                    long ventanas = ventanas(horizonte, ritmo);
                    long totalMin = ventanas * sa.toSeconds() / 60;
                    if (totalMin < DURACION_5_DIAS_MINIMA_MIN || totalMin > DURACION_5_DIAS_MAXIMA_MIN) {
                        throw invalido("Con ese ritmo los 5 días durarían " + totalMin
                                + " min; deben durar entre 30 y 60 min.");
                    }
                    yield new RitmoEscenario(ritmo, (int) totalMin, null);
                }
                int objetivo = solicitud.duracionObjetivoMin() == null
                        ? DURACION_5_DIAS_POR_DEFECTO_MIN : solicitud.duracionObjetivoMin();
                if (objetivo < DURACION_5_DIAS_MINIMA_MIN || objetivo > DURACION_5_DIAS_MAXIMA_MIN) {
                    throw invalido("La duración objetivo de 5 días debe estar entre 30 y 60 min.");
                }
                yield new RitmoEscenario(
                        Ritmo.para(horizonte, Duration.ofMinutes(objetivo), SA_5_DIAS), objetivo, null);
            }
            case OPERACION_DIARIA -> {
                int velocidad = solicitud.velocidad() == null
                        ? VELOCIDAD_POR_DEFECTO : solicitud.velocidad();
                if (!VELOCIDADES.contains(velocidad)) {
                    throw invalido("La velocidad del día a día debe ser 1, 10, 60 o 240.");
                }
                Duration sa = segundos == null ? SA_DIA_A_DIA : Duration.ofSeconds(segundos);
                yield new RitmoEscenario(new Ritmo(sa, velocidad), null, velocidad);
            }
            case COLAPSO -> {
                Duration sa = segundos == null ? SA_COLAPSO : Duration.ofSeconds(segundos);
                Duration sc = minutos == null ? SC_COLAPSO : Duration.ofMinutes(minutos);
                yield new RitmoEscenario(new Ritmo(sa, (double) sc.toNanos() / sa.toNanos()),
                        null, null);
            }
        };
    }

    /** Sc en segundos enteros: los cortes del reloj de la operación no llevan fracciones. */
    Duration saltoDelConsumo() {
        return Duration.ofSeconds(Math.max(1, Math.round(ritmo.saltoDelConsumo().toMillis() / 1000.0)));
    }

    long saMs() { return ritmo.milisegundosDelSalto(); }

    static long ventanas(Duration horizonte, Ritmo ritmo) {
        long sc = ritmo.saltoDelConsumo().toNanos();
        return (horizonte.toNanos() + sc - 1) / sc;
    }

    long ventanas(Duration horizonte) {
        long sc = saltoDelConsumo().toNanos();
        return (horizonte.toNanos() + sc - 1) / sc;
    }

    /** Duración real prevista si cada cálculo cabe dentro de su salto. */
    Duration duracionEstimada(Duration horizonte) {
        return Duration.ofMillis(ventanas(horizonte) * saMs());
    }

    private static ApiException invalido(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "RITMO_INVALIDO", mensaje);
    }
}
