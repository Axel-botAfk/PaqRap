package com.paqrap.datos;

import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.TipoEntrega;
import com.paqrap.planificador.DistanciaManhattan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lector del archivo mensual de ventas reales del caso PaqRap (entregado por el profesor),
 * para usarlo con GRASP.
 *
 * Nombre del archivo: {@code ventas.aaaamm.txt}. Registro: {@code ##d##h##m:posX,posY,cIdCliente,qq,hl}
 *
 * Ejemplo: {@code 11d13h31m:45,43,c9167,12,36}
 *
 * El destino se construye con {@link DistanciaManhattan#deCoordenadas(int, int)} (misma
 * cuadrícula que usa el proyecto de búsqueda tabú del equipo para este mismo archivo). El
 * plazo {@code hl} (en horas) se mapea al {@link TipoEntrega} ya existente en GRASP: los
 * valores reales del caso (4, 8, 12, 18 y 36) coinciden exactamente con las horas de plazo que
 * ya define ese enum, así que no hace falta tocarlo, solo traducir el entero leído del archivo
 * a su constante correspondiente.
 */
public final class LectorVentas {
    private static final Pattern REGISTRO = Pattern.compile(
            "^(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m:(-?\\d+),(-?\\d+),([^,]+),(\\d+),(\\d+)$");
    private static final Pattern PERIODO_EN_NOMBRE = Pattern.compile("(\\d{4})(\\d{2})");

    private static final Map<Integer, TipoEntrega> HORAS_A_TIPO_ENTREGA = Map.of(
            TipoEntrega.PRIORITARIA_4H.getHorasPlazo(), TipoEntrega.PRIORITARIA_4H,
            TipoEntrega.PRIORITARIA_8H.getHorasPlazo(), TipoEntrega.PRIORITARIA_8H,
            TipoEntrega.PRIORITARIA_12H.getHorasPlazo(), TipoEntrega.PRIORITARIA_12H,
            TipoEntrega.PRIORITARIA_18H.getHorasPlazo(), TipoEntrega.PRIORITARIA_18H,
            TipoEntrega.REGULAR_36H.getHorasPlazo(), TipoEntrega.REGULAR_36H
    );

    private LectorVentas() {
    }

    /** Lee el archivo tomando el año y el mes de su nombre (por ejemplo, ventas.202609.txt). */
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
     * de aparición.
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

        LocalDateTime fechaRegistro = periodo.atDay(dia).atTime(
                Integer.parseInt(registro.group(2)),
                Integer.parseInt(registro.group(3))
        );

        int posX = Integer.parseInt(registro.group(4));
        int posY = Integer.parseInt(registro.group(5));
        String clienteId = registro.group(6).trim();
        int cantidad = Integer.parseInt(registro.group(7));
        int horasPlazo = Integer.parseInt(registro.group(8));

        TipoEntrega tipoEntrega = HORAS_A_TIPO_ENTREGA.get(horasPlazo);
        if (tipoEntrega == null) {
            throw new IllegalArgumentException(
                    "El plazo hl=" + horasPlazo + " no coincide con ningún TipoEntrega conocido "
                            + "(se esperaba 4, 8, 12, 18 o 36): " + linea);
        }

        return new Pedido(
                String.format("P-%05d", correlativo),
                clienteId,
                DistanciaManhattan.deCoordenadas(posX, posY),
                cantidad,
                fechaRegistro,
                tipoEntrega
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
