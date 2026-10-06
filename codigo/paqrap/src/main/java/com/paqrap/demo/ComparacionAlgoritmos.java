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
import com.paqrap.planificador.alns.BusquedaAlns;
import com.paqrap.planificador.alns.ParametrosAlns;
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
import java.util.Map;
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

    /**
     * Presupuestos de ALNS, en iteraciones de destruir y reparar.
     *
     * El rango es mucho mas bajo que el de la tabu a proposito. Una iteracion de ALNS reinserta
     * una docena larga de pedidos explorando decenas de colocaciones para cada uno, mientras que
     * una de la tabu evalua cuarenta vecinos que son perturbaciones pequenas. Barrer los dos por
     * el mismo rango no compararia nada: solo diria que a ALNS se le pidio mas trabajo.
     */
    private static final int[] ITERACIONES_ALNS = {5, 10, 20, 40, 80, 160};
    private static final int[] ITERACIONES_ALNS_RAPIDO = {20};

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

        List<Medicion> alns = new ArrayList<>();
        for (int iteraciones : RAPIDO ? ITERACIONES_ALNS_RAPIDO : ITERACIONES_ALNS) {
            Medicion medicion = medir(
                    "ALNS x" + iteraciones,
                    () -> new BusquedaAlns(
                            evaluador, ParametrosAlns.porDefecto().conIteraciones(iteraciones)),
                    estado, evaluador,
                    semilla -> base(1, semilla).construir());
            alns.add(medicion);
            imprimir(medicion, constructivo);
        }

        veredicto(constructivo, Map.of("GRASP", grasp, "tabu", tabu, "ALNS", alns));
    }

    /**
     * Quien gana a igualdad de tiempo empleado.
     *
     * Comparar los presupuestos por defecto no dice nada, porque cada algoritmo mide su esfuerzo
     * en una unidad distinta y enfrentarlos asi solo revela cual de los tres ajustes es mas
     * generoso. Lo que decide es otra cosa: fijado un tiempo, quien llega mas lejos.
     *
     * Se toman tramos de tiempo y, dentro de cada uno, se mira el mejor objetivo que cada
     * algoritmo alcanzo sin pasarse. El que no tenga ninguna medicion dentro del tramo se informa
     * como ausente en vez de compararse con una que costo el doble.
     */
    private static void veredicto(Medicion constructivo, Map<String, List<Medicion>> porAlgoritmo) {
        System.out.println();
        System.out.println("  A IGUAL TIEMPO EMPLEADO");
        System.out.printf("  %10s", "hasta ms");
        List<String> nombres = new ArrayList<>(porAlgoritmo.keySet());
        java.util.Collections.sort(nombres);
        for (String nombre : nombres) {
            System.out.printf(" %14s", nombre);
        }
        System.out.println("   mejor");

        for (double tope : topesDeTiempo(porAlgoritmo)) {
            System.out.printf("  %10.0f", tope);
            String mejorNombre = null;
            double mejorObjetivo = Double.POSITIVE_INFINITY;

            for (String nombre : nombres) {
                Medicion dentro = mejorHasta(porAlgoritmo.get(nombre), tope);
                if (dentro == null) {
                    System.out.printf(" %14s", "-");
                    continue;
                }
                System.out.printf(" %14.0f", dentro.objetivo());
                if (dentro.objetivo() < mejorObjetivo) {
                    mejorObjetivo = dentro.objetivo();
                    mejorNombre = nombre;
                }
            }
            System.out.println("   " + (mejorNombre == null ? "-" : mejorNombre));
        }

        System.out.println();
        System.out.printf(
                "  Referencia sin fase de mejora: objetivo %.0f (costo %.0f, %.1f sin atender)"
                        + " en %.0f ms.%n",
                constructivo.objetivo(), constructivo.costo(), constructivo.sinAsignar(),
                constructivo.milisegundos());
        System.out.println("  Un algoritmo solo justifica su costo si baja de esa linea.");
        System.out.println();
        System.out.println("  Ojo al leer el objetivo: lleva la penalidad por lo no atendido, que");
        System.out.println("  domina. Si la columna 'sin asig.' es igual en todas las filas, las");
        System.out.println("  diferencias de objetivo son solo recorrido; mirar la columna de costo.");
    }

    /**
     * Los tramos de tiempo sobre los que se compara.
     *
     * Son los tiempos que de verdad costo alguna medicion, no una escala inventada: asi cada
     * columna corresponde a un presupuesto que alguien puede reproducir.
     */
    private static List<Double> topesDeTiempo(Map<String, List<Medicion>> porAlgoritmo) {
        java.util.TreeSet<Double> topes = new java.util.TreeSet<>();
        for (List<Medicion> mediciones : porAlgoritmo.values()) {
            for (Medicion medicion : mediciones) {
                topes.add(medicion.milisegundos());
            }
        }
        return new ArrayList<>(topes);
    }

    /** El mejor objetivo que este algoritmo alcanzo sin pasar del tiempo indicado. */
    private static Medicion mejorHasta(List<Medicion> mediciones, double tope) {
        Medicion mejor = null;
        for (Medicion medicion : mediciones) {
            if (medicion.milisegundos() <= tope
                    && (mejor == null || medicion.objetivo() < mejor.objetivo())) {
                mejor = medicion;
            }
        }
        return mejor;
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

    /**
     * Una fila del barrido, con la mejora medida donde de verdad se ve.
     *
     * <h2>Por que el porcentaje va sobre el costo y no sobre el objetivo</h2>
     *
     * El objetivo lleva la penalidad por lo que nadie atiende, y esa penalidad domina por varios
     * ordenes de magnitud. Cuando todos los presupuestos dejan los mismos pedidos sin atender
     * -que es lo habitual, porque los que quedan fuera lo estan por plazo o por calle cerrada y
     * ningun algoritmo puede con eso- la penalidad es una constante identica en todas las filas y
     * se come el porcentaje: una mejora real del 25% en recorrido aparecia como -0,0%.
     *
     * Asi que se informan las dos cosas por separado. El costo dice cuanto se ahorra ruteando; los
     * no asignados dicen si alguien ademas atiende a mas clientes, que es lo que de verdad manda
     * y lo que hay que mirar primero cuando cambia.
     */
    private static void imprimir(Medicion medicion, Medicion referencia) {
        String contra = "";
        if (referencia != null) {
            contra = comparacionCon(medicion, referencia);
        }
        System.out.printf("%-34s %8.0f %10.1f %12.0f %9.0f%s%n",
                medicion.nombre(), medicion.milisegundos(), medicion.sinAsignar(),
                medicion.costo(), medicion.objetivo(), contra);
    }

    /**
     * Cuanto mejora esta medicion a la de referencia.
     *
     * Atender a mas clientes se informa primero y aparte, porque no se canjea por dinero: un plan
     * que deja un pedido menos afuera es mejor aunque recorra mas.
     */
    private static String comparacionCon(Medicion medicion, Medicion referencia) {
        StringBuilder contra = new StringBuilder("  ");

        double masAtendidos = referencia.sinAsignar() - medicion.sinAsignar();
        if (Math.abs(masAtendidos) > 1e-9) {
            contra.append(String.format("%+.1f sin atender | ", -masAtendidos));
        }

        if (referencia.costo() == 0) {
            return contra.append("costo de referencia nulo").toString();
        }
        double delta = 100.0 * (medicion.costo() - referencia.costo()) / referencia.costo();
        return contra.append(String.format("%+.1f%% de costo vs constructivo", delta)).toString();
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
