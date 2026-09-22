package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.VentanaIndisponibilidad;
import com.paqrap.planificador.DistanciaManhattan;
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.LectorMantenimiento;
import com.paqrap.planificador.Parametros;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Demo de GRASP contra los datos reales del caso (los mismos archivos que entregó el profesor y
 * que ya usa el proyecto de búsqueda tabú del equipo: ventas.202609.txt y
 * mant.preventivo.09.10.txt).
 *
 * Antes de este demo, GRASP solo se probaba con una única planificación estática (un puñado de
 * pedidos, una sola llamada a Grasp.construir). Esto es lo que faltaba para probar los datos
 * reales de verdad: una simulación de varios días que va llegando pedidos, replanifica cada
 * cierto tiempo y reutiliza las mismas 37 unidades entre planificaciones. Ver el comentario de
 * diseño en {@link Simulador} para las decisiones de alcance (no hay reenrutamiento por
 * bloqueos, el reabastecimiento de intermedios se simplifica, etc.).
 *
 * Los bloqueos de calles (bloqueo.*.txt) deliberadamente NO se leen aquí: el README del proyecto
 * ya documenta que el enrutamiento sensible a bloqueos está fuera del alcance de esta
 * implementación de GRASP, y esta simulación respeta esa misma decisión.
 *
 * Ejecutar (desde la raíz del proyecto):
 *   javac -encoding UTF-8 -d build (Get-ChildItem -Recurse -Filter *.java -Path src/main/java).FullName   (PowerShell)
 *   java -cp build com.paqrap.demo.DemoDatosReales [dias] [carpeta] [maxIteracionesGrasp] [alfa] [minutosEntreLatidos]
 *
 * Por defecto simula 3 días de setiembre de 2026 leyendo de datos/reales, con GRASP a 30
 * iteraciones/alfa=0.30 y un latido de replanificación cada 60 minutos.
 */
public final class DemoDatosReales {
    private static final Path CARPETA_POR_DEFECTO = Path.of("datos", "reales");
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final int DIAS_POR_DEFECTO = 3;

    private DemoDatosReales() {
    }

    public static void main(String[] args) throws IOException {
        int dias = args.length > 0 ? Integer.parseInt(args[0]) : DIAS_POR_DEFECTO;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : CARPETA_POR_DEFECTO;
        int maxIteracionesGrasp = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        double alfa = args.length > 3 ? Double.parseDouble(args[3]) : 0.30;
        int minutosEntreLatidos = args.length > 4 ? Integer.parseInt(args[4]) : 60;

        Path archivoVentas = carpeta.resolve("ventas.202609.txt");
        Path archivoMantenimiento = carpeta.resolve("mant.preventivo.09.10.txt");

        if (!Files.exists(archivoVentas)) {
            System.out.println("No se encontraron los archivos reales en " + carpeta.toAbsolutePath());
            System.out.println("Copie ahi ventas.202609.txt y mant.preventivo.09.10.txt "
                    + "(los mismos que entrego el profesor), o pase la carpeta como segundo argumento.");
            return;
        }

        List<Pedido> ventas = LectorVentas.leer(archivoVentas, PERIODO);
        Map<String, List<VentanaIndisponibilidad>> mantenimientos =
                Files.exists(archivoMantenimiento)
                        ? LectorMantenimiento.leer(archivoMantenimiento)
                        : Map.of();

        LocalDateTime inicio = PERIODO.atDay(1).atStartOfDay();

        System.out.println("=== GRASP CONTRA DATOS REALES DEL CASO ===");
        System.out.println("Ventas:        " + archivoVentas + "  (" + ventas.size() + " pedidos en todo el mes)");
        System.out.println("Mantenimiento: " + (Files.exists(archivoMantenimiento)
                ? archivoMantenimiento + "  (" + mantenimientos.size() + " unidades con jornadas programadas)"
                : "no encontrado, se ignora"));
        System.out.println("Bloqueos de calles: fuera de alcance (ver README del proyecto).");
        System.out.println("Horizonte: " + dias + " dias desde " + inicio
                + " | latido de replanificacion cada " + minutosEntreLatidos + " min"
                + " | GRASP: " + maxIteracionesGrasp + " iteraciones, alfa=" + alfa);
        System.out.println("Flota: " + DatosCaso.flota().size() + " unidades "
                + "(10 autos + 15 motos + 12 bicicletas), almacenes: " + DatosCaso.almacenes().size()
                + " (1 central + 2 intermedios).");
        System.out.println();
        System.out.println("Calculando...");

        Grasp grasp = new Grasp(new DistanciaManhattan());
        Parametros parametros = new Parametros(maxIteracionesGrasp, alfa, 20260901L);

        Simulador simulador = new Simulador(
                grasp,
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(minutosEntreLatidos),
                mantenimientos
        );

        ResumenSimulacion resumen = simulador.correr(
                inicio, Duration.ofDays(dias), ventas, DatosCaso.flota());

        imprimirResumen(resumen);
        imprimirEvolucionDiaria(resumen);
    }

    private static void imprimirResumen(ResumenSimulacion resumen) {
        System.out.println();
        System.out.println("=== RESUMEN ===");
        System.out.println("Periodo simulado: " + resumen.inicio() + " a " + resumen.fin());
        System.out.println("Tiempo de computo: " + resumen.milisegundosDeComputo() + " ms"
                + " (" + resumen.cantidadDeLatidosConPlan() + " latidos con planificacion real).");
        System.out.printf("Pedidos recibidos:  %d%n", resumen.pedidosRecibidos());
        System.out.printf("Pedidos entregados: %d (%.1f%%)%n",
                resumen.pedidosEntregados(), resumen.porcentajeEntregado());
        System.out.printf("Pedidos vencidos (nunca se les encontro una insercion a tiempo): %d (%.1f%%)%n",
                resumen.pedidosVencidos(), resumen.porcentajeVencido());
        System.out.printf("Pedidos pendientes al cierre del horizonte: %d%n", resumen.pedidosPendientesAlCierre());
        System.out.printf("Distancia total recorrida: %.2f km%n", resumen.distanciaTotalKm());
        System.out.printf("Costo total: S/ %.2f%n", resumen.costoTotal());
        System.out.printf("Costo promedio por pedido entregado: S/ %.2f%n", resumen.costoPromedioPorPedidoEntregado());
        System.out.printf("Km promedio por pedido entregado: %.2f km%n", resumen.kmPromedioPorPedidoEntregado());
    }

    private static void imprimirEvolucionDiaria(ResumenSimulacion resumen) {
        System.out.println();
        System.out.println("=== EVOLUCION DIARIA (recibidos | entregados | cola al cierre del dia) ===");
        for (var dia : resumen.recibidosPorDia().keySet()) {
            int recibidos = resumen.recibidosPorDia().getOrDefault(dia, 0);
            int entregados = resumen.entregasPorDia().getOrDefault(dia, 0);
            int cola = resumen.colaAlCierreDelDia().getOrDefault(dia, -1);
            System.out.printf("  %s | recibidos=%3d | entregados=%3d | cola al cierre=%s%n",
                    dia, recibidos, entregados, cola >= 0 ? String.valueOf(cola) : "(horizonte no llego a fin de dia)");
        }
    }
}
