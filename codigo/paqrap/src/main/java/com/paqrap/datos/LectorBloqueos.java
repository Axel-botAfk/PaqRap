package com.paqrap.datos;

import com.paqrap.modelo.Bloqueo;
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
 * Lector del archivo mensual de calles bloqueadas.
 *
 * Nombre del archivo: {@code aaaamm.bloqueadas}.
 * Registro: {@code ##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn}
 *
 * Ejemplo: {@code 01d06h00m-01d15h00m:31,21,34,21}
 *
 * El día, la hora y el minuto son relativos al mes del archivo, de modo que el año y el mes se
 * toman del nombre. Los pares {@code xi,yi} son los vértices de la poligonal bloqueada; el
 * bloqueo se expande a todos los nodos de cada tramo.
 */
public final class LectorBloqueos {
    public static final String EXTENSION = ".bloqueadas";

    private static final Pattern REGISTRO = Pattern.compile(
            "^(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m-(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m:(.+)$");
    private static final Pattern PERIODO_LARGO = Pattern.compile("(\\d{4})(\\d{2})");
    private static final Pattern PERIODO_CORTO = Pattern.compile("(\\d{2})(\\d{2})");

    private LectorBloqueos() {
    }

    /** Lee el archivo tomando el año y el mes de su nombre. */
    public static List<Bloqueo> leer(Path archivo) throws IOException {
        return leer(archivo, periodoDelNombre(archivo.getFileName().toString()));
    }

    public static List<Bloqueo> leer(Path archivo, YearMonth periodo) throws IOException {
        return leerLineas(Files.readAllLines(archivo, StandardCharsets.UTF_8), periodo);
    }

    /** Lee varios archivos mensuales y los combina en un solo listado. */
    public static List<Bloqueo> leerVarios(List<Path> archivos) throws IOException {
        List<Bloqueo> bloqueos = new ArrayList<>();
        for (Path archivo : archivos) {
            bloqueos.addAll(leer(archivo));
        }
        return List.copyOf(bloqueos);
    }

    public static List<Bloqueo> leerLineas(List<String> lineas, YearMonth periodo) {
        List<Bloqueo> bloqueos = new ArrayList<>();
        for (int numero = 1; numero <= lineas.size(); numero++) {
            String linea = lineas.get(numero - 1).trim();
            if (linea.isEmpty() || linea.startsWith("#")) {
                continue;
            }
            try {
                bloqueos.add(parsear(linea, periodo));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(
                        "Línea " + numero + " del archivo de bloqueos: " + error.getMessage(), error);
            }
        }
        return List.copyOf(bloqueos);
    }

    public static Bloqueo parsear(String linea, YearMonth periodo) {
        Matcher registro = REGISTRO.matcher(linea.trim());
        if (!registro.matches()) {
            throw new IllegalArgumentException(
                    "No tiene el formato ##d##h##m-##d##h##m:x1,y1,...: " + linea);
        }

        LocalDateTime inicio = instante(
                periodo,
                Integer.parseInt(registro.group(1)),
                Integer.parseInt(registro.group(2)),
                Integer.parseInt(registro.group(3))
        );
        LocalDateTime fin = instante(
                periodo,
                Integer.parseInt(registro.group(4)),
                Integer.parseInt(registro.group(5)),
                Integer.parseInt(registro.group(6))
        );

        return Bloqueo.dePoligonal(inicio, fin, vertices(registro.group(7)));
    }

    /**
     * Año y mes del archivo.
     *
     * Se aceptan las dos formas que aparecen en la práctica: {@code aaaamm}, como describe el
     * enunciado ({@code 202609.bloqueadas}), y {@code aamm}, como vienen nombrados los archivos
     * entregados ({@code bloqueo.2609.txt}).
     */
    public static YearMonth periodoDelNombre(String nombreArchivo) {
        Matcher largo = PERIODO_LARGO.matcher(nombreArchivo);
        if (largo.find()) {
            return YearMonth.of(
                    Integer.parseInt(largo.group(1)),
                    Integer.parseInt(largo.group(2))
            );
        }

        Matcher corto = PERIODO_CORTO.matcher(nombreArchivo);
        if (corto.find()) {
            return YearMonth.of(
                    2000 + Integer.parseInt(corto.group(1)),
                    Integer.parseInt(corto.group(2))
            );
        }

        throw new IllegalArgumentException(
                "El nombre del archivo de bloqueos debe contener aaaamm o aamm: " + nombreArchivo);
    }

    private static List<Ubicacion> vertices(String coordenadas) {
        String[] valores = coordenadas.split(",");
        if (valores.length < 2 || valores.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Las coordenadas deben venir en pares x,y: " + coordenadas);
        }

        List<Ubicacion> vertices = new ArrayList<>();
        for (int i = 0; i < valores.length; i += 2) {
            vertices.add(new Ubicacion(
                    Integer.parseInt(valores[i].trim()),
                    Integer.parseInt(valores[i + 1].trim())
            ));
        }
        return vertices;
    }

    private static LocalDateTime instante(YearMonth periodo, int dia, int hora, int minuto) {
        if (dia < 1 || dia > periodo.lengthOfMonth()) {
            throw new IllegalArgumentException(
                    "El día " + dia + " no existe en " + periodo + ".");
        }
        // Un cierre que termina a medianoche puede venir escrito como 24h00m.
        if (hora == 24 && minuto == 0) {
            return periodo.atDay(dia).atStartOfDay().plusDays(1);
        }
        return periodo.atDay(dia).atTime(hora, minuto);
    }
}
