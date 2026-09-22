package com.paqrap.demo;

import com.paqrap.modelo.TipoIndisponibilidad;
import com.paqrap.modelo.VentanaIndisponibilidad;
import com.paqrap.planificador.LectorMantenimiento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Verifica el parser del archivo de mantenimiento preventivo (pregunta 19 de
 * Preguntas y Respuestas) contra el ejemplo publicado por el profesor para
 * septiembre-octubre 2026 (archivo mant.preventivo.09.10).
 */
public final class VerificacionMantenimiento {
    private VerificacionMantenimiento() {
    }

    public static void main(String[] args) {
        pruebaFormatoDelCaso();
        pruebaDuracionMinimaDeUnDiaCompleto();
        System.out.println("OK - 2 verificaciones del lector de mantenimiento superadas.");
    }

    private static void pruebaFormatoDelCaso() {
        // Primeras lineas del ejemplo publicado por el profesor para 09/2026.
        List<String> lineas = List.of(
                "20260901:TA01",
                "20260902:TB01",
                "20260903:TM07",
                "20260906:TB11"
        );

        Map<String, List<VentanaIndisponibilidad>> plan = LectorMantenimiento.leer(lineas);

        exigir(plan.containsKey("TA01"), "Debe registrar el mantenimiento de TA01.");
        exigir(plan.containsKey("TB01"), "Debe registrar el mantenimiento de TB01.");
        exigir(plan.containsKey("TM07"), "Debe registrar el mantenimiento de TM07.");
        exigir(plan.containsKey("TB11"), "Debe registrar el mantenimiento de TB11.");

        VentanaIndisponibilidad ventanaTA01 = plan.get("TA01").get(0);
        exigir(ventanaTA01.motivo() == TipoIndisponibilidad.MANTENIMIENTO_PREVENTIVO,
                "El motivo debe ser MANTENIMIENTO_PREVENTIVO.");
        exigir(ventanaTA01.inicio().equals(LocalDate.of(2026, 9, 1).atStartOfDay()),
                "TA01 debe iniciar mantenimiento el 2026-09-01 00:00.");
    }

    private static void pruebaDuracionMinimaDeUnDiaCompleto() {
        // Nota 1 de la pregunta 19: ninguna unidad se programa entre las 00:00 y
        // las 23:59 del dia registrado, sin importar el tipo.
        Map<String, List<VentanaIndisponibilidad>> plan = LectorMantenimiento.leer(
                List.of("20260902:TB01"));

        VentanaIndisponibilidad ventana = plan.get("TB01").get(0);
        LocalDateTime finDelDia = LocalDate.of(2026, 9, 2).atTime(23, 59);

        exigir(ventana.incluye(finDelDia),
                "La ventana debe cubrir, como minimo, hasta las 23:59 del dia registrado.");
        exigir(!ventana.incluye(LocalDate.of(2026, 9, 1).atTime(23, 0)),
                "La ventana no debe cubrir el dia anterior al registrado.");
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
