package com.paqrap.planificador;

import com.paqrap.modelo.TipoIndisponibilidad;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.VentanaIndisponibilidad;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee el archivo de mantenimiento preventivo del caso PaqRap
 * (pregunta 19 de Preguntas y Respuestas: archivo "mant.preventivo.m1.m2").
 *
 * Cada registro tiene el formato {@code aaaammdd:TTNN}; por ejemplo
 * {@code 20260901:TA01} significa que la unidad TA01 (Auto 01) inicia su
 * mantenimiento preventivo el 1 de septiembre de 2026.
 *
 * <p><b>Dato pendiente de confirmar por el profesor</b>: la duración del
 * mantenimiento por tipo de unidad todavía no está cerrada en las Preguntas
 * y Respuestas ("FALTA poner duración de los mantenimientos..."). Se usan
 * aquí los valores provisionales que esa misma respuesta menciona como
 * referencia (bicicleta = 1 turno, moto = 1 día, auto = 2 días), pero en
 * todos los casos se respeta como mínimo la Nota 1 de esa pregunta: la
 * unidad no se programa desde las 00:00 hasta las 23:59 del día registrado.
 * Por eso la duración efectiva es {@code max(duración del tipo, 24h)}.
 * Actualizar {@link #DURACION_HORAS_POR_TIPO} en cuanto el profesor
 * confirme el valor definitivo.
 */
public final class LectorMantenimiento {

    private static final Pattern PATRON_REGISTRO = Pattern.compile("(\\d{8}):([A-Z]{2})(\\d{2})");

    /** Duración provisional de un mantenimiento preventivo, por tipo de vehículo. */
    private static final Map<TipoVehiculo, Long> DURACION_HORAS_POR_TIPO = Map.of(
            TipoVehiculo.BICICLETA, 8L,   // "un turno" (provisional)
            TipoVehiculo.MOTO, 24L,       // "1 día" (provisional)
            TipoVehiculo.AUTO, 48L        // "2 días" (provisional)
    );

    private static final Map<String, TipoVehiculo> PREFIJO_A_TIPO = Map.of(
            "TA", TipoVehiculo.AUTO,
            "TM", TipoVehiculo.MOTO,
            "TB", TipoVehiculo.BICICLETA
    );

    private LectorMantenimiento() {
    }

    /**
     * @return un mapa idVehiculo (formato TTNN, p.e. "TA01") a sus ventanas de
     * mantenimiento preventivo programadas en el archivo.
     */
    public static Map<String, List<VentanaIndisponibilidad>> leer(Path archivo) {
        try {
            return leer(Files.readAllLines(archivo));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de mantenimiento: " + archivo, e);
        }
    }

    public static Map<String, List<VentanaIndisponibilidad>> leer(List<String> lineas) {
        Map<String, List<VentanaIndisponibilidad>> resultado = new HashMap<>();

        for (String linea : lineas) {
            String limpia = linea.strip();
            if (limpia.isEmpty()) {
                continue;
            }

            Matcher m = PATRON_REGISTRO.matcher(limpia);
            if (!m.matches()) {
                throw new IllegalArgumentException("Registro de mantenimiento invalido: " + limpia);
            }

            String fecha = m.group(1);
            String prefijoTipo = m.group(2);
            String numero = m.group(3);
            String idVehiculo = prefijoTipo + numero;

            TipoVehiculo tipo = PREFIJO_A_TIPO.get(prefijoTipo);
            if (tipo == null) {
                throw new IllegalArgumentException("Tipo de vehiculo no reconocido en: " + limpia);
            }

            LocalDate dia = LocalDate.parse(fecha, DateTimeFormatter.BASIC_ISO_DATE);
            LocalDateTime inicio = dia.atStartOfDay();

            // Nota 1 de la pregunta 19: como minimo, el dia completo (00:00-23:59).
            long horasTipo = DURACION_HORAS_POR_TIPO.get(tipo);
            long horasEfectivas = Math.max(horasTipo, 24L);
            LocalDateTime fin = inicio.plusHours(horasEfectivas);

            VentanaIndisponibilidad ventana = new VentanaIndisponibilidad(
                    inicio, fin, TipoIndisponibilidad.MANTENIMIENTO_PREVENTIVO);

            resultado.computeIfAbsent(idVehiculo, k -> new ArrayList<>()).add(ventana);
        }

        return resultado;
    }
}
