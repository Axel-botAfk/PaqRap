package com.paqrap.datos;

import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Los tres archivos que entrega el equipo docente, para un mes cualquiera.
 *
 * Existe para que ninguna demo vuelva a tener los nombres de un mes escritos a mano. Los del
 * curso no siguen una sola convención —las ventas llevan el año de cuatro cifras y los bloqueos
 * de dos, y el mantenimiento es bimensual y cubre dos meses a la vez— así que armarlos en un
 * único sitio evita que cada escenario invente su propia forma de equivocarse.
 *
 * <pre>
 *   ventas.202610.txt            ventas del mes
 *   bloqueo.2610.txt             calles cerradas del mes
 *   mant.preventivo.09.10.txt    mantenimiento preventivo del bimestre
 * </pre>
 */
public record DatosReales(
        YearMonth periodo,
        List<Pedido> ventas,
        List<Bloqueo> bloqueos,
        PlanMantenimiento mantenimiento,
        Path archivoVentas,
        Path archivoBloqueos,
        Path archivoMantenimiento
) {
    /** Dónde viven por defecto los archivos del curso dentro del proyecto. */
    public static final Path CARPETA_POR_DEFECTO = Path.of("datos", "reales");

    /**
     * Carga los tres archivos del periodo indicado.
     *
     * @throws IOException si falta alguno de ellos; se nombra cuál, porque el error más común es
     *                     pedir un mes para el que todavía no se copiaron los datos.
     */
    public static DatosReales cargar(Path carpeta, YearMonth periodo) throws IOException {
        Path archivoVentas = carpeta.resolve(nombreDeVentas(periodo));
        Path archivoBloqueos = carpeta.resolve(nombreDeBloqueos(periodo));
        Path archivoMantenimiento = carpeta.resolve(nombreDeMantenimiento(periodo));

        exigirQueExista(archivoVentas, "de ventas");
        exigirQueExista(archivoBloqueos, "de bloqueos");

        // El mantenimiento es opcional. El equipo docente entrega un solo archivo bimensual, con
        // fechas absolutas, mientras que las ventas y los bloqueos llegan de todos los meses. Un
        // mes sin archivo es un mes sin mantenimiento programado, y eso se informa; inventar un
        // plan proyectando el bimestre que si existe seria dar por dato algo que nadie dio.
        PlanMantenimiento mantenimiento = Files.exists(archivoMantenimiento)
                ? LectorMantenimiento.leer(archivoMantenimiento)
                : PlanMantenimiento.vacio();

        return new DatosReales(
                periodo,
                LectorVentas.leer(archivoVentas, periodo),
                LectorBloqueos.leer(archivoBloqueos),
                mantenimiento,
                archivoVentas,
                archivoBloqueos,
                archivoMantenimiento
        );
    }

    /**
     * Los meses que hacen falta para simular desde un instante durante tantos dias.
     *
     * Existe porque las corridas ya no se definen por mes sino por fecha de inicio y duracion,
     * como pide el caso: se coloca fecha y hora, y de ahi se avanza. Cargar de mas seria leer
     * decenas de miles de pedidos que nunca entran en el horizonte.
     */
    public static List<YearMonth> mesesQueCubren(LocalDateTime inicio, int dias) {
        YearMonth primero = YearMonth.from(inicio);
        YearMonth ultimo = YearMonth.from(inicio.plusDays(dias));
        List<YearMonth> meses = new ArrayList<>();
        for (YearMonth mes = primero; !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            meses.add(mes);
        }
        return meses;
    }

    /**
     * Los meses que de verdad estan en la carpeta, en orden.
     *
     * Sirve para recorrer todo lo disponible sin tener que enumerarlo a mano ni saber de antemano
     * hasta donde llega lo que entrego el equipo docente.
     */
    public static List<YearMonth> mesesDisponibles(Path carpeta) throws IOException {
        List<YearMonth> meses = new ArrayList<>();
        try (java.util.stream.Stream<Path> archivos = Files.list(carpeta)) {
            for (Path archivo : archivos.toList()) {
                String nombre = archivo.getFileName().toString();
                if (!nombre.startsWith("ventas.") || !nombre.endsWith(".txt")) {
                    continue;
                }
                String aaaamm = nombre.substring("ventas.".length(), nombre.length() - 4);
                if (aaaamm.length() != 6 || !aaaamm.chars().allMatch(Character::isDigit)) {
                    continue;
                }
                meses.add(YearMonth.of(
                        Integer.parseInt(aaaamm.substring(0, 4)),
                        Integer.parseInt(aaaamm.substring(4))));
            }
        }
        java.util.Collections.sort(meses);
        return meses;
    }

    /** {@code ventas.aaaamm.txt} */
    public static String nombreDeVentas(YearMonth periodo) {
        return String.format("ventas.%04d%02d.txt", periodo.getYear(), periodo.getMonthValue());
    }

    /** {@code bloqueo.aamm.txt}, con el año de dos cifras. */
    public static String nombreDeBloqueos(YearMonth periodo) {
        return String.format("bloqueo.%02d%02d.txt",
                periodo.getYear() % 100, periodo.getMonthValue());
    }

    /**
     * {@code mant.preventivo.m1.m2.txt}. El plan es bimensual y empieza en el mes impar del par,
     * de modo que octubre se sirve del mismo archivo que setiembre.
     */
    public static String nombreDeMantenimiento(YearMonth periodo) {
        int mes = periodo.getMonthValue();
        int primero = mes % 2 == 1 ? mes : mes - 1;
        return String.format("mant.preventivo.%02d.%02d.txt", primero, primero + 1);
    }

    private static void exigirQueExista(Path archivo, String cual) throws IOException {
        if (!Files.exists(archivo)) {
            throw new IOException("No se encontró el archivo " + cual + ": "
                    + archivo.toAbsolutePath());
        }
    }

    public void imprimirResumen() {
        System.out.println("Periodo:       " + periodo);
        System.out.println("Ventas:        " + archivoVentas + "  (" + ventas.size() + " pedidos)");
        System.out.println("Bloqueos:      " + archivoBloqueos
                + "  (" + bloqueos.size() + " tramos)");
        System.out.println("Mantenimiento: " + archivoMantenimiento
                + "  (" + mantenimiento.getCantidadDeJornadas() + " jornadas programadas)");
    }
}
