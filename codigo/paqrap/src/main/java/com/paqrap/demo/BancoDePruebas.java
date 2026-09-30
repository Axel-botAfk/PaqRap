package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.DatosReales;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.InsercionPorHolgura;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.alns.BusquedaAlns;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Banco de pruebas: corre varios algoritmos sobre los datos reales, a la vez y sin pausas.
 *
 * <h2>Para qué sirve y para qué no</h2>
 *
 * Sirve para <b>probar algoritmos</b>: cambiaste algo y quieres saber si mejoró o empeoró, sin
 * esperar los tres cuartos de hora que dura la presentación del escenario 5D.
 *
 * Dos cosas lo hacen rápido. Primero, va a fondo: no tiene ritmo de reloj, no espera nada.
 * Segundo, lanza cada corrida en su propio hilo, de modo que comparar tres algoritmos cuesta lo
 * que tarda el más lento y no la suma de los tres. En una máquina con núcleos de sobra eso es
 * gratis; el simulador es de un solo hilo, así que cada corrida ocupa uno y no se estorban.
 *
 * <h2>El número que aquí NO sirve</h2>
 *
 * <b>Ta.</b> Con varias corridas compitiendo por el procesador, los tiempos de ejecución salen
 * inflados y además varían según cuántas corran a la vez. Sirven para comparar entre sí —todas
 * sufren lo mismo— pero no para dimensionar el salto del algoritmo.
 *
 * Para elegir Sa hay que medir Ta con una sola corrida, sola en la máquina, que es lo que hace
 * {@code DemoSimulacion5Dias} en modo medición.
 *
 * <h2>Lo que sí es comparable</h2>
 *
 * Entregas, vencidos, kilómetros y costo. Todas las corridas ven el mismo escenario: los mismos
 * pedidos, los mismos bloqueos, el mismo mantenimiento y los mismos instantes de corte. Lo único
 * que cambia entre ellas es quién decide.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.BancoDePruebas [dias] [carpeta]
 *
 * Propiedades:
 *   -Dpaqrap.algoritmos=tabu,grasp,alns,constructivo  cuáles comparar
 *   -Dpaqrap.bloques=true                        lectura por bloques
 *   -Dpaqrap.semillas=1,2,3                      repite cada uno con varias semillas
 *   -Dpaqrap.periodo=202609                      mes a simular
 *   -Dpaqrap.tabu.iteraciones=120                esfuerzo de la búsqueda tabú
 *   -Dpaqrap.espera=80                          costo por producto y hora de espera
 *   -Dpaqrap.alns.iteraciones=200               esfuerzo de ALNS
 *   -Dpaqrap.grasp.construcciones=8              esfuerzo de GRASP
 *   -Dpaqrap.resultadosCsv=salida/resultados.csv  conserva métricas sin redondear
 */
public final class BancoDePruebas {
    private static final YearMonth PERIODO_POR_DEFECTO = YearMonth.of(2026, 9);

    /** Pocos días a propósito: esto es para iterar, no para la corrida de evaluación. */
    private static final int DIAS_POR_DEFECTO = 2;

    private static final long SEMILLA_POR_DEFECTO = 20260901L;

    private BancoDePruebas() {
    }

    public static void main(String[] args) throws Exception {
        int dias = args.length > 0 ? Integer.parseInt(args[0]) : DIAS_POR_DEFECTO;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : DatosReales.CARPETA_POR_DEFECTO;

        DatosReales datos;
        try {
            datos = DatosReales.cargar(carpeta, periodo());
        } catch (IOException falta) {
            System.out.println(falta.getMessage());
            return;
        }

        List<String> algoritmos = List.of(
                System.getProperty("paqrap.algoritmos", "tabu,grasp,constructivo").split(","));
        List<Long> semillas = semillas();

        List<Corrida> corridas = new ArrayList<>();
        for (String algoritmo : algoritmos) {
            for (long semilla : semillas) {
                corridas.add(new Corrida(algoritmo.trim(), semilla, dias, datos));
            }
        }

        System.out.println("=== BANCO DE PRUEBAS (DATOS REALES, EN PARALELO) ===");
        datos.imprimirResumen();
        System.out.println("Horizonte: " + dias + " dias | " + corridas.size()
                + " corridas en paralelo | nucleos disponibles: "
                + Runtime.getRuntime().availableProcessors());
        System.out.println("Sin ritmo: va a fondo. Los Ta salen inflados por la competencia");
        System.out.println("  entre corridas y no sirven para dimensionar Sa.");
        System.out.println("Calculando...");

        ExecutorService hilos = Executors.newFixedThreadPool(
                Math.min(corridas.size(), Runtime.getRuntime().availableProcessors()));

        long antes = System.currentTimeMillis();
        List<Future<Resultado>> pendientes = new ArrayList<>();
        for (Corrida corrida : corridas) {
            pendientes.add(hilos.submit(corrida::ejecutar));
        }

        List<Resultado> resultados = new ArrayList<>();
        for (Future<Resultado> futuro : pendientes) {
            resultados.add(futuro.get());
        }
        hilos.shutdown();
        long transcurrido = System.currentTimeMillis() - antes;

        String rutaCsv = System.getProperty("paqrap.resultadosCsv");
        if (rutaCsv != null && !rutaCsv.isBlank()) {
            escribirCsv(Path.of(rutaCsv), resultados, dias);
        }
        imprimir(resultados, transcurrido, semillas.size());
    }

    /** Exporta los valores crudos para análisis estadístico sin transcribir la tabla redondeada. */
    private static void escribirCsv(Path destino, List<Resultado> resultados, int dias)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "periodo,dias,algoritmo,semilla,productos_entregados,productos_pendientes,"
                + "productos_vencidos,porcentaje_productos,distancia_km,costo_soles,"
                + "tiempo_ms\n");
        for (Resultado resultado : resultados) {
            ResumenSimulacion s = resultado.resumen();
            csv.append(periodo()).append(',')
                    .append(dias).append(',')
                    .append(resultado.algoritmo()).append(',')
                    .append(resultado.semilla()).append(',')
                    .append(s.productosEntregados()).append(',')
                    .append(s.productosPendientes()).append(',')
                    .append(s.productosVencidos()).append(',')
                    .append(s.porcentajeAtendidoEnProductos()).append(',')
                    .append(s.distanciaTotalKm()).append(',')
                    .append(s.costoTotal()).append(',')
                    .append(resultado.milisegundos()).append('\n');
        }
        Path padre = destino.toAbsolutePath().getParent();
        if (padre != null) {
            Files.createDirectories(padre);
        }
        Files.writeString(destino, csv, StandardCharsets.UTF_8);
        System.out.println("CSV de resultados: " + destino.toAbsolutePath());
    }

    private static void imprimir(List<Resultado> resultados, long transcurrido, int cuantasSemillas) {
        System.out.println();
        System.out.printf("%-14s %8s %11s %7s %9s %9s %10s %12s %9s %9s%n",
                "algoritmo", "semilla", "prod.entr.", "%prod", "pend.", "vencidos",
                "km", "S/", "S/ x prod", "reloj s");

        for (Resultado r : resultados) {
            ResumenSimulacion s = r.resumen();
            // Por producto y no por pedido: es la unidad en la que mide la función objetivo.
            double porProducto = s.productosEntregados() == 0
                    ? 0.0
                    : s.costoTotal() / s.productosEntregados();
            System.out.printf("%-14s %8d %11d %7.1f %9d %9d %10.0f %12.0f %9.2f %9.1f%n",
                    r.algoritmo(), r.semilla(), s.productosEntregados(),
                    s.porcentajeAtendidoEnProductos(), s.productosPendientes(),
                    s.productosVencidos(), s.distanciaTotalKm(), s.costoTotal(), porProducto,
                    r.milisegundos() / 1000.0);
        }

        System.out.println();
        System.out.printf("Reloj total: %.1f s en paralelo | suma de las corridas: %.1f s%n",
                transcurrido / 1000.0,
                resultados.stream().mapToLong(Resultado::milisegundos).sum() / 1000.0);

        if (cuantasSemillas > 1) {
            System.out.println("Con varias semillas, compara el promedio por algoritmo y no una fila suelta.");
        }
    }

    /**
     * Una corrida aislada.
     *
     * Cada una arma su propio mapa de bloqueos, su enrutador, su evaluador y su flota: los dos
     * primeros guardan recorridos ya calculados en tablas que no son seguras entre hilos, y
     * compartirlas daría resultados corruptos o distintos en cada ejecución. Lo único que se
     * comparte son los pedidos, que son inmutables.
     */
    private record Corrida(String algoritmo, long semilla, int dias, DatosReales datos) {

        private Resultado ejecutar() {
            MapaBloqueos mapa = new MapaBloqueos(datos.bloqueos());
            EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
            Evaluador evaluador = new Evaluador(enrutador);

            Parametros parametros = Parametros
                    .constructor(entero("paqrap.grasp.construcciones", 8), alfa(), semilla)
                    .penalidadEspera(Double.parseDouble(
                        System.getProperty("paqrap.espera", "80.0")))
                .iteracionesTabu(entero("paqrap.tabu.iteraciones", 120))
                    .tenenciaTabu(8)
                    .tamanoMuestraVecindario(40)
                    .iteracionesSinMejora(25)
                    .construir();

            Simulador simulador = new Simulador(
                    planificador(algoritmo, evaluador),
                    enrutador,
                    DatosCaso.almacenes(),
                    parametros,
                    Duration.ofMinutes(15),
                    false
            ).conMantenimiento(datos.mantenimiento())
                    .conEventosDeBloqueo(mapa)
                    .conIntervaloMinimo(Duration.ofMinutes(15));

            if (Boolean.getBoolean("paqrap.bloques")) {
                simulador.leyendoPorBloques();
            }

            LocalDateTime inicio = datos.periodo().atDay(1).atStartOfDay();
            long antes = System.currentTimeMillis();
            ResumenSimulacion resumen = simulador.correr(
                    inicio, Duration.ofDays(dias), datos.ventas(), DatosCaso.flota());
            return new Resultado(
                    algoritmo, semilla, resumen, System.currentTimeMillis() - antes);
        }
    }

    private record Resultado(
            String algoritmo,
            long semilla,
            ResumenSimulacion resumen,
            long milisegundos
    ) {
    }

    private static Planificador planificador(String algoritmo, Evaluador evaluador) {
        return switch (algoritmo.toLowerCase()) {
            case "grasp" -> new Grasp(evaluador);
            case "constructivo" -> new InsercionPorHolgura(evaluador);
            case "alns" -> new BusquedaAlns(evaluador, alnsConEsfuerzo());
            case "tabu" -> new BusquedaTabu(evaluador);
            default -> throw new IllegalArgumentException(
                    "Algoritmo desconocido: " + algoritmo + ". Use tabu, grasp, alns o constructivo.");
        };
    }

    private static List<Long> semillas() {
        String valor = System.getProperty("paqrap.semillas");
        if (valor == null) {
            return List.of(SEMILLA_POR_DEFECTO);
        }
        List<Long> semillas = new ArrayList<>();
        for (String parte : valor.split(",")) {
            semillas.add(Long.parseLong(parte.trim()));
        }
        return semillas;
    }

    /** Esfuerzo de ALNS, en iteraciones de destruir y reparar. */
    private static com.paqrap.planificador.alns.ParametrosAlns alnsConEsfuerzo() {
        String valor = System.getProperty("paqrap.alns.iteraciones");
        com.paqrap.planificador.alns.ParametrosAlns base =
                com.paqrap.planificador.alns.ParametrosAlns.porDefecto();
        return valor == null ? base : base.conIteraciones(Integer.parseInt(valor));
    }

    private static YearMonth periodo() {
        String valor = System.getProperty("paqrap.periodo");
        if (valor == null) {
            return PERIODO_POR_DEFECTO;
        }
        return YearMonth.of(
                Integer.parseInt(valor.substring(0, 4)), Integer.parseInt(valor.substring(4)));
    }

    private static int entero(String propiedad, int porDefecto) {
        String valor = System.getProperty(propiedad);
        return valor == null ? porDefecto : Integer.parseInt(valor);
    }

    private static double alfa() {
        String valor = System.getProperty("paqrap.grasp.alfa");
        return valor == null ? 0.30 : Double.parseDouble(valor);
    }
}
