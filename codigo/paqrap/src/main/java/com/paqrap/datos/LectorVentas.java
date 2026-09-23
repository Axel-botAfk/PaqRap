package com.paqrap.datos;

import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ubicacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lector del archivo mensual de ventas.
 *
 * Nombre del archivo: {@code ventasaaaamm}.
 * Registro: {@code ##d##h##m:posX,posY,cIdCliente,qq,hl}
 *
 * Ejemplo: {@code 11d13h31m:45,43,c9167,12,36}
 *
 * El día, la hora y el minuto en que llega el pedido son relativos al mes del archivo. El
 * campo {@code hl} es el número de horas de plazo, que puede ser 36 (regular) o uno de los
 * plazos priorizados.
 */
public final class LectorVentas {
    private static final Pattern REGISTRO = Pattern.compile(
            "^(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m:(-?\\d+),(-?\\d+),([^,]+),(\\d+),(\\d+)$");
    private static final Pattern PERIODO_EN_NOMBRE = Pattern.compile("(\\d{4})(\\d{2})");

    private LectorVentas() {
    }

    /** Lee el archivo tomando el año y el mes de su nombre. */
    public static List<Pedido> leer(Path archivo) throws IOException {
        return leer(archivo, periodoDelNombre(archivo.getFileName().toString()));
    }

    public static List<Pedido> leer(Path archivo, YearMonth periodo) throws IOException {
        return leerLineas(Files.readAllLines(archivo, StandardCharsets.UTF_8), periodo);
    }

    public static List<Pedido> leerLineas(List<String> lineas, YearMonth periodo) {
        List<Pedido> pedidos = new ArrayList<>();
        int correlativo = 0;

        for (int numero = 1; numero <= lineas.size(); numero++) {
            String linea = lineas.get(numero - 1).trim();
            if (linea.isEmpty() || linea.startsWith("#")) {
                continue;
            }
            correlativo++;
            try {
                pedidos.add(parsear(linea, periodo, correlativo));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(
                        "Línea " + numero + " del archivo de ventas: " + error.getMessage(), error);
            }
        }
        return List.copyOf(pedidos);
    }

    /**
     * El archivo no trae identificador de pedido, así que se asigna uno correlativo por orden
     * de aparición. Es lo que permite seguir un mismo pedido a lo largo de la simulación.
     */
    public static Pedido parsear(String linea, YearMonth periodo, int correlativo) {
        Matcher registro = REGISTRO.matcher(linea.trim());
        if (!registro.matches()) {
            throw new IllegalArgumentException(
                    "No tiene el formato ##d##h##m:posX,posY,cliente,qq,hl: " + linea);
        }

        int dia = Integer.parseInt(registro.group(1));
        if (dia < 1 || dia > periodo.lengthOfMonth()) {
            throw new IllegalArgumentException("El día " + dia + " no existe en " + periodo + ".");
        }

        LocalDateTime llegada = periodo.atDay(dia).atTime(
                Integer.parseInt(registro.group(2)),
                Integer.parseInt(registro.group(3))
        );

        return new Pedido(
                String.format("P-%05d", correlativo),
                registro.group(6).trim(),
                new Ubicacion(Integer.parseInt(registro.group(4)), Integer.parseInt(registro.group(5))),
                Integer.parseInt(registro.group(7)),
                llegada,
                Integer.parseInt(registro.group(8))
        );
    }

    /** Línea en el formato del caso, para escribir archivos de prueba. */
    public static String aRegistro(Pedido pedido) {
        return String.format(
                "%02dd%02dh%02dm:%d,%d,%s,%d,%d",
                pedido.getFechaRegistro().getDayOfMonth(),
                pedido.getFechaRegistro().getHour(),
                pedido.getFechaRegistro().getMinute(),
                pedido.getDestino().x(),
                pedido.getDestino().y(),
                pedido.getClienteId(),
                pedido.getCantidad(),
                pedido.getHorasPlazo()
        );
    }

    public static YearMonth periodoDelNombre(String nombreArchivo) {
        Matcher encontrado = PERIODO_EN_NOMBRE.matcher(nombreArchivo);
        if (!encontrado.find()) {
            throw new IllegalArgumentException(
                    "El nombre del archivo de ventas debe contener aaaamm: " + nombreArchivo);
        }
        return YearMonth.of(
                Integer.parseInt(encontrado.group(1)),
                Integer.parseInt(encontrado.group(2))
        );
    }
}
