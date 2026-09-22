package com.paqrap.demo;

import com.paqrap.modelo.TipoIndisponibilidad;
import com.paqrap.modelo.VentanaIndisponibilidad;
import com.paqrap.planificador.CalculadorAveria;

import java.time.LocalDateTime;

/**
 * Verifica que CalculadorAveria calcule las ventanas de indisponibilidad
 * exactamente como las describe la pregunta 3 de Preguntas y Respuestas,
 * usando los turnos oficiales del Enunciado de la Situación Auténtica
 * (07:00, 15:00, 23:00).
 */
public final class VerificacionAveria {
    private VerificacionAveria() {
    }

    public static void main(String[] args) {
        pruebaTipo1DuraDosHoras();
        pruebaTipo2DentroDelPrimerTurno();
        pruebaTipo2DentroDelUltimoTurnoDelDia();
        pruebaTipo3AlMenosDosDias();
        pruebaTipo3CuandoYaPasaronLas15h();
        System.out.println("OK - 5 verificaciones del calculo de averias superadas.");
    }

    private static void pruebaTipo1DuraDosHoras() {
        LocalDateTime momento = LocalDateTime.of(2026, 9, 22, 10, 0);
        VentanaIndisponibilidad v = CalculadorAveria.calcular(TipoIndisponibilidad.AVERIA_TIPO_1, momento);

        exigir(v.fin().equals(momento.plusHours(2)),
                "Averia Tipo 1 debe durar exactamente 2 horas.");
    }

    private static void pruebaTipo2DentroDelPrimerTurno() {
        // 10:00 cae dentro del turno 07:00-15:00. El turno siguiente es
        // 15:00-23:00, por lo que la unidad vuelve a las 23:00 del mismo dia.
        LocalDateTime momento = LocalDateTime.of(2026, 9, 22, 10, 0);
        VentanaIndisponibilidad v = CalculadorAveria.calcular(TipoIndisponibilidad.AVERIA_TIPO_2, momento);

        exigir(v.fin().equals(LocalDateTime.of(2026, 9, 22, 23, 0)),
                "Averia Tipo 2 a las 10:00 debe terminar a las 23:00 del mismo dia (fin del turno siguiente).");
    }

    private static void pruebaTipo2DentroDelUltimoTurnoDelDia() {
        // 16:00 cae dentro del turno 15:00-23:00. El turno siguiente es
        // 23:00-07:00, por lo que la unidad vuelve a las 07:00 del dia siguiente.
        LocalDateTime momento = LocalDateTime.of(2026, 9, 22, 16, 0);
        VentanaIndisponibilidad v = CalculadorAveria.calcular(TipoIndisponibilidad.AVERIA_TIPO_2, momento);

        exigir(v.fin().equals(LocalDateTime.of(2026, 9, 23, 7, 0)),
                "Averia Tipo 2 a las 16:00 debe terminar a las 07:00 del dia siguiente.");
    }

    private static void pruebaTipo3AlMenosDosDias() {
        // 22-sept 10:00 + 2 dias = 24-sept 10:00, que es antes de las 15:00
        // de ese mismo dia, asi que el retorno es 24-sept a las 15:00.
        LocalDateTime momento = LocalDateTime.of(2026, 9, 22, 10, 0);
        VentanaIndisponibilidad v = CalculadorAveria.calcular(TipoIndisponibilidad.AVERIA_TIPO_3, momento);

        exigir(v.fin().equals(LocalDateTime.of(2026, 9, 24, 15, 0)),
                "Averia Tipo 3 a las 10:00 del 22-sept debe volver el 24-sept a las 15:00.");
        exigir(!v.fin().isBefore(momento.plusDays(2)),
                "Averia Tipo 3 debe respetar el minimo de 2 dias.");
    }

    private static void pruebaTipo3CuandoYaPasaronLas15h() {
        // 22-sept 16:00 + 2 dias = 24-sept 16:00, que ya paso las 15:00 de
        // ese dia, asi que el retorno se corre al 25-sept a las 15:00.
        LocalDateTime momento = LocalDateTime.of(2026, 9, 22, 16, 0);
        VentanaIndisponibilidad v = CalculadorAveria.calcular(TipoIndisponibilidad.AVERIA_TIPO_3, momento);

        exigir(v.fin().equals(LocalDateTime.of(2026, 9, 25, 15, 0)),
                "Averia Tipo 3 a las 16:00 del 22-sept debe volver el 25-sept a las 15:00.");
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
