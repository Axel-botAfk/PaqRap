package com.paqrap.datos;

import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
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
        exigirQueExista(archivoMantenimiento, "de mantenimiento");

        return new DatosReales(
                periodo,
                LectorVentas.leer(archivoVentas, periodo),
                LectorBloqueos.leer(archivoBloqueos),
                LectorMantenimiento.leer(archivoMantenimiento),
                archivoVentas,
                archivoBloqueos,
                archivoMantenimiento
        );
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
