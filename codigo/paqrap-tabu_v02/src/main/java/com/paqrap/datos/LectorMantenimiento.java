package com.paqrap.datos;

import com.paqrap.modelo.PlanMantenimiento;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lector del plan de mantenimiento preventivo.
 *
 * Nombre del archivo: {@code mant.preventivo.m1.m2}, donde m1 y m2 son los dos meses que cubre.
 * Registro: {@code aaaammdd:TTNN}
 *
 * Ejemplo: {@code 20260901:TA01}
 *
 * El plan es bimensual y se repite cada dos meses, de modo que para cubrir un periodo más largo
 * se leen varios archivos y se combinan con {@link PlanMantenimiento#mas}.
 */
public final class LectorMantenimiento {
    private static final Pattern REGISTRO =
            Pattern.compile("^(\\d{4})(\\d{2})(\\d{2}):([A-Za-z]{2}\\d{2})$");

    private LectorMantenimiento() {
    }

    public static PlanMantenimiento leer(Path archivo) throws IOException {
        return leerLineas(Files.readAllLines(archivo, StandardCharsets.UTF_8));
    }

    public static PlanMantenimiento leerVarios(List<Path> archivos) throws IOException {
        PlanMantenimiento plan = PlanMantenimiento.vacio();
        for (Path archivo : archivos) {
            plan = plan.mas(leer(archivo));
        }
        return plan;
    }

    public static PlanMantenimiento leerLineas(List<String> lineas) {
        Map<LocalDate, Set<String>> unidadesPorDia = new HashMap<>();

        for (int numero = 1; numero <= lineas.size(); numero++) {
            String linea = lineas.get(numero - 1).trim();
            if (linea.isEmpty() || linea.startsWith("#")) {
                continue;
            }

            Matcher registro = REGISTRO.matcher(linea);
            if (!registro.matches()) {
                throw new IllegalArgumentException(
                        "Línea " + numero + " del plan de mantenimiento: no tiene el formato "
                                + "aaaammdd:TTNN: " + linea);
            }

            LocalDate dia;
            try {
                dia = LocalDate.of(
                        Integer.parseInt(registro.group(1)),
                        Integer.parseInt(registro.group(2)),
                        Integer.parseInt(registro.group(3))
                );
            } catch (RuntimeException fechaInvalida) {
                throw new IllegalArgumentException(
                        "Línea " + numero + " del plan de mantenimiento: fecha inválida en "
                                + linea, fechaInvalida);
            }

            unidadesPorDia
                    .computeIfAbsent(dia, fecha -> new HashSet<>())
                    .add(registro.group(4).toUpperCase());
        }

        return new PlanMantenimiento(unidadesPorDia);
    }
}
