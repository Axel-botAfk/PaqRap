package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.LectorBloqueos;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Solucion;
import com.paqrap.planificador.InsercionPorHolgura;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.tabu.BusquedaTabu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Comparación de GRASP contra la búsqueda tabú sobre instancias de los datos reales del caso.
 *
 * <h2>Qué se compara y por qué así</h2>
 *
 * No se comparan con sus parámetros por defecto, porque cada uno mide su esfuerzo en una unidad
 * distinta —GRASP en construcciones completas, la tabú en iteraciones de mejora— y enfrentarlos
 * así solo diría cuál de los dos ajustes es más generoso. Lo que se hace es barrer el
 * presupuesto de cada uno y reportar <b>calidad contra tiempo empleado</b>: a igual tiempo
 * gastado, quién llega más lejos.
 *
 * Los dos presupuestos se expresan en unidades de trabajo y no en milisegundos, de modo que cada
 * punto del barrido sea reproducible; el tiempo aparece medido, nunca impuesto.
 *
 * Se incluye además el constructivo por holgura solo, sin mejora, porque es de donde parte la
 * tabú: sin esa referencia no se puede saber cuánto aporta la fase de mejora y cuánto ya venía
 * puesto en la solución inicial.
 *
 * La comparación es a igualdad de todo lo demás: mismo {@link Evaluador}, mismo enrutador con
 * los bloqueos reales, misma instancia y las mismas semillas.
 *
 * <h2>Qué se mide</h2>
 *
 * El valor objetivo, que es costo de operación más 10 000 por pedido sin atender. Se informan
 * los dos sumandos por separado, porque un plan que deja un pedido afuera y otro que lo atiende
 * caro no son comparables por el costo a secas.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.ComparacionAlgoritmos [carpeta]
 *
 * Con -Dpaqrap.rapido=true se corre una sola instancia, una semilla y dos presupuestos: sirve
 * para comprobar de un vistazo que un cambio en los algoritmos no rompió nada ni disparó el
 * tiempo, sin pagar el barrido completo.
 */
public final class ComparacionAlgoritmos {
    private static final Path CARPETA_POR_DEFECTO = Path.of("datos", "reales");
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);

    /** Semillas con las que se repite cada medición; se informa el promedio. */
    private static final long[] SEMILLAS = {20260901L, 7L, 4242L};
    private static final long[] SEMILLAS_RAPIDO = {20260901L};

    /** Presupuestos de GRASP, en construcciones completas. */
    private static final int[] CONSTRUCCIONES_GRASP = {1, 2, 4, 8, 16, 32};
    private static final int[] CONSTRUCCIONES_RAPIDO = {1, 8};

    /**
     * Presupuestos de la tabú, en iteraciones de mejora.
     *
     * Se barre por iteraciones y no por milisegundos para que la medición sea reproducible: un
     * corte por reloj daría un resultado distinto en cada máquina y no se podría afirmar nada
     * sobre cuál algoritmo es mejor. El tiempo que cuesta cada presupuesto se informa medido.
     */
    private static final int[] ITERACIONES_TABU = {10, 25, 50, 100, 200, 400};
    private static final int[] ITERACIONES_RAPIDO = {120};

    private static final boolean RAPIDO = Boolean.getBoolean("paqrap.rapido");

    private static final double ALFA = 0.30;

    private ComparacionAlgoritmos() {
    }

    public static void main(String[] args) throws IOException {
        Path carpeta = args.length > 0 ? Path.of(args[0]) : CARPETA_POR_DEFECTO;
        Path archivoVentas = carpeta.resolve("ventas.202609.txt");
        Path archivoBloqueos = carpeta.resolve("bloqueo.2609.txt");

        if (!Files.exists(archivoVentas)) {
            System.out.println("No se encontraron los archivos reales en " + carpeta.toAbsolutePath());
            return;
        }

        List<Pedido> ventas = LectorVentas.leer(archivoVentas, PERIODO);
        List<Bloqueo> bloqueos = LectorBloqueos.leer(archivoBloqueos);
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(new MapaBloqueos(bloqueos));
        Evaluador evaluador = new Evaluador(enrutador);

        System.out.println("=== GRASP CONTRA BUSQUEDA TABU ===");
        System.out.println("Ventas:   " + archivoVentas + "  (" + ventas.size() + " pedidos)");
        System.out.println("Bloqueos: " + archivoBloqueos + "  (" + bloqueos.size() + " tramos)");
        System.out.println("Semillas: " + semillas().length + " por medicion, se informa el promedio");
        System.out.println("Objetivo: costo de operacion + 10000 por pedido sin atender");

        for (Instancia instancia : instancias(ventas)) {
            compararEn(instancia, evaluador, enrutador);
        }
    }

    /**
     * Tres instantes del mes con ventanas de acumulación crecientes, para ver si el orden entre
     * los algoritmos se sostiene cuando la cola crece.
     */
    private static long[] semillas() {
        return RAPIDO ? SEMILLAS_RAPIDO : SEMILLAS;
    }

    private static List<Instancia> instancias(List<Pedido> ventas) {
        if (RAPIDO) {
            return List.of(new Instancia("cola media", PERIODO.atDay(6).atTime(14, 0), 4, ventas));
        }
        return List.of(
                new Instancia("cola corta", PERIODO.atDay(3).atTime(9, 0), 2, ventas),
                new Instancia("cola media", PERIODO.atDay(6).atTime(14, 0), 4, ventas),
                new Instancia("cola larga", PERIODO.atDay(9).atTime(11, 0), 8, ventas)
        );
    }

    private static void compararEn(Instancia instancia, Evaluador evaluador, EnrutadorBloqueos enrutador) {
        EstadoOperacion estado = instancia.estado();

        System.out.println();
        System.out.println("--- " + instancia.nombre() + " | " + instancia.instante()
                + " | " + estado.getPedidos().size() + " pedidos en cola, "
                + estado.getVehiculos().size() + " unidades ---");

        // El enrutador guarda recorridos ya calculados: sin esta pasada en vacio la primera
        // medicion pagaria el llenado del cache y el orden de ejecucion decidiria el resultado.
        calentar(estado, evaluador);

        System.out.printf("%-34s %8s %10s %12s %9s%n",
                "algoritmo", "ms", "sin asig.", "costo S/", "objetivo");

        Medicion constructivo = medir(
                "Constructivo por holgura (solo)",
                () -> new InsercionPorHolgura(evaluador),
                estado, evaluador, semilla -> base(1, semilla).construir());
        imprimir(constructivo, null);

        List<Medicion> grasp = new ArrayList<>();
        for (int construcciones : RAPIDO ? CONSTRUCCIONES_RAPIDO : CONSTRUCCIONES_GRASP) {
            Medicion medicion = medir(
                    "GRASP x" + construcciones,
                    () -> new Grasp(evaluador),
                    estado, evaluador,
                    semilla -> base(construcciones, semilla).construir());
            grasp.add(medicion);
            imprimir(medicion, constructivo);
        }

        List<Medicion> tabu = new ArrayList<>();
        for (int iteraciones : RAPIDO ? ITERACIONES_RAPIDO : ITERACIONES_TABU) {
            Medicion medicion = medir(
                    "Tabu x" + iteraciones,
                    () -> new BusquedaTabu(evaluador),
                    estado, evaluador,
                    semilla -> base(1, semilla)
                            .iteracionesTabu(iteraciones)
                            .tenenciaTabu(8)
                            .tamanoMuestraVecindario(40)
                            .iteracionesSinMejora(25)
                            .construir());
            tabu.add(medicion);
            imprimir(medicion, constructivo);
        }

        veredicto(grasp, tabu, constructivo);
    }

    /**
     * Quién gana a igualdad de tiempo: por cada presupuesto de GRASP se busca la medición de la
     * tabú que gastó un tiempo parecido o menor, y se comparan los objetivos.
     */
    private static void veredicto(List<Medicion> grasp, List<Medicion> tabu, Medicion constructivo) {
        System.out.println("  A igual tiempo empleado:");
        for (Medicion unGrasp : grasp) {
            Medicion rival = null;
            for (Medicion unaTabu : tabu) {
                if (unaTabu.milisegundos() <= unGrasp.milisegundos()
                        && (rival == null || unaTabu.milisegundos() > rival.milisegundos())) {
                    rival = unaTabu;
                }
            }
            if (rival == null) {
                continue;
            }
            String gana = rival.objetivo() < unGrasp.objetivo() ? "tabu"
                    : rival.objetivo() > unGrasp.objetivo() ? "GRASP" : "empate";
            System.out.printf("    %-16s (%.0f ms, obj %.0f)  vs  %-14s (%.0f ms, obj %.0f)  -> %s%n",
                    unGrasp.nombre(), unGrasp.milisegundos(), unGrasp.objetivo(),
                    rival.nombre(), rival.milisegundos(), rival.objetivo(), gana);
        }

        Medicion mejorTabu = tabu.get(tabu.size() - 1);
        double aporte = constructivo.objetivo() - mejorTabu.objetivo();
        System.out.printf("  La fase de mejora de la tabu le saca %.0f al constructivo del que parte "
                        + "(%.1f%%).%n",
                aporte, constructivo.objetivo() == 0 ? 0 : 100.0 * aporte / constructivo.objetivo());
    }

    /** Promedio sobre las semillas. El constructivo es determinista y sale igual en todas. */
    private static Medicion medir(
            String nombre,
            Supplier<Planificador> fabrica,
            EstadoOperacion estado,
            Evaluador evaluador,
            LongFunction<Parametros> ajuste
    ) {
        double sumaMs = 0.0;
        double sumaCosto = 0.0;
        double sumaObjetivo = 0.0;
        double sumaSinAsignar = 0.0;

        for (long semilla : semillas()) {
            Parametros parametros = ajuste.apply(semilla);
            Planificador planificador = fabrica.get();

            long antes = System.nanoTime();
            Solucion solucion = planificador.planificar(estado, parametros);
            sumaMs += (System.nanoTime() - antes) / 1_000_000.0;

            sumaCosto += solucion.getCostoTotal();
            sumaSinAsignar += solucion.getCantidadPedidosNoAsignados();
            sumaObjetivo += evaluador.objetivo(solucion, estado, parametros);
        }

        int n = semillas().length;
        return new Medicion(nombre, sumaMs / n, sumaSinAsignar / n, sumaCosto / n, sumaObjetivo / n);
    }

    private static void imprimir(Medicion medicion, Medicion referencia) {
        String contra = "";
        if (referencia != null && referencia.objetivo() != 0) {
            double delta = 100.0 * (medicion.objetivo() - referencia.objetivo()) / referencia.objetivo();
            contra = String.format("  %+.1f%% vs constructivo", delta);
        }
        System.out.printf("%-34s %8.0f %10.1f %12.0f %9.0f%s%n",
                medicion.nombre(), medicion.milisegundos(), medicion.sinAsignar(),
                medicion.costo(), medicion.objetivo(), contra);
    }

    private static void calentar(EstadoOperacion estado, Evaluador evaluador) {
        Parametros parametros = base(1, 1L).iteracionesTabu(20).construir();
        new Grasp(evaluador).planificar(estado, parametros);
        new BusquedaTabu(evaluador).planificar(estado, parametros);
    }

    private static Parametros.Constructor base(int construcciones, long semilla) {
        return Parametros.constructor(construcciones, ALFA, semilla);
    }

    /** Una foto de la operación: los pedidos llegados en las últimas horas y la flota completa. */
    private record Instancia(String nombre, LocalDateTime instante, int horasDeCola, List<Pedido> ventas) {

        private EstadoOperacion estado() {
            LocalDateTime desde = instante.minusHours(horasDeCola);
            List<Pedido> cola = new ArrayList<>();
            for (Pedido pedido : ventas) {
                LocalDateTime llegada = pedido.getFechaRegistro();
                if (!llegada.isBefore(desde) && !llegada.isAfter(instante)) {
                    cola.add(pedido);
                }
            }
            return new EstadoOperacion(
                    instante, cola, DatosCaso.almacenes(), DatosCaso.flota());
        }
    }

    private record Medicion(
            String nombre,
            double milisegundos,
            double sinAsignar,
            double costo,
            double objetivo
    ) {
    }
}
