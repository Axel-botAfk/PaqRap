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
import com.paqrap.simulacion.MedicionDePlanificacion;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import com.paqrap.modelo.Pedido;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Los cinco días del caso corridos a fondo, para medir Ta y decidir el ritmo.
 *
 * <h2>Por qué no es la misma clase que la 5D</h2>
 *
 * {@link DemoSimulacion5Dias} tiene que durar entre 30 y 60 minutos, porque en ese rato hay que
 * ver la representación gráfica. Eso la vuelve inútil para medir: el reloj de pared que informa al
 * final es el de las pausas, no el del cómputo, y el noventa por ciento de esos minutos la máquina
 * está esperando.
 *
 * Esta corre la misma operación sin ninguna pausa y sin ventana que cumplir. El número que da es
 * <b>Ta</b>, el tiempo que cuesta una planificación, que es un insumo de la otra: primero se mide
 * acá y después se elige allá.
 *
 * <h2>Qué se responde con esto</h2>
 *
 * Una sola pregunta, y no es «cuánto tarda». Es <b>si la duración que quiero mostrar es posible</b>.
 *
 * <pre>
 *   Sa  tiene que ser mayor que el Ta del peor caso, o una planificación arranca
 *       antes de que termine la anterior y la corrida se atropella
 *   K   = horizonte / duración en pantalla
 *   Sc  = Sa x K  y conviene bastante por debajo del plazo más corto del caso
 * </pre>
 *
 * De ahí sale el resultado que importa: con el Ta observado, <b>cuál es la duración más corta que
 * todavía se sostiene</b>. Si ese número es mayor que 60 minutos, la ventana del caso no se puede
 * cumplir con este algoritmo y este esfuerzo, y hay que bajarle el esfuerzo antes de seguir.
 *
 * <h2>Ta no es un número</h2>
 *
 * Crece con la cola. Planificar con cinco pedidos pendientes no cuesta lo que hacerlo con
 * doscientos, así que el máximo se mide donde la cola es más larga —el final del horizonte— y es
 * ése el que tiene que caber en Sa, no el promedio. El reporte de ritmo lo desglosa por tamaño de
 * cola justamente por eso.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoMedicion5D [dias] [carpeta]
 *
 * Propiedades:
 *   -Dpaqrap.algoritmo=tabu|grasp|alns|constructivo   cuál planificador se mide
 *   -Dpaqrap.inicio=2027-03-15T14:00             fecha y hora de arranque
 *   -Dpaqrap.periodo=202609                      mes a simular
 *   -Dpaqrap.traza=20                            una línea por planificación (una de cada 20)
 *   -Dpaqrap.bloques=true                        lectura por bloques
 *   -Dpaqrap.tabu.iteraciones=120                esfuerzo de la búsqueda tabú
 *   -Dpaqrap.espera=80                          costo por producto y hora de espera
 *   -Dpaqrap.alns.iteraciones=200               esfuerzo de ALNS
 *   -Dpaqrap.grasp.construcciones=8              esfuerzo de GRASP
 *   -Dpaqrap.grasp.alfa=0.30                     apertura de la lista restringida
 */
public final class DemoMedicion5D {
    private static final YearMonth PERIODO_POR_DEFECTO = YearMonth.of(2026, 9);
    private static final int DIAS_POR_DEFECTO = 5;
    private static final long SEMILLA = 20260901L;

    /** Las duraciones de la ventana que pide el caso para la 5D. */
    private static final int[] DURACIONES_CANDIDATAS = {30, 45, 60};

    /** La que usa la demo de presentación por defecto. */
    private static final int DURACION_RECOMENDADA = 45;

    /**
     * Tope de Sc, en minutos. Un cuarto del plazo más corto del caso, que es de cuatro horas: con
     * un Sc mayor, un pedido priorizado puede llegar y vencer sin que nadie lo haya planificado.
     */
    private static final int SC_TOPE_MINUTOS = 60;

    /** Margen sobre el Ta máximo. Apurar Sa hasta el límite deja la corrida sin colchón. */
    private static final double MARGEN = 1.30;

    /** Refrescos candidatos, en milisegundos: los que dan una imagen fluida. */
    private static final long[] SALTOS_CANDIDATOS = {1_000L, 2_000L, 5_000L, 10_000L};

    private DemoMedicion5D() {
    }

    public static void main(String[] args) throws IOException {
        int dias = args.length > 0 ? Integer.parseInt(args[0]) : DIAS_POR_DEFECTO;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : DatosReales.CARPETA_POR_DEFECTO;

        DatosReales datos;
        try {
            datos = DatosReales.cargar(carpeta, java.time.YearMonth.from(inicioElegido(periodo())));
        } catch (IOException falta) {
            System.out.println(falta.getMessage());
            return;
        }

        Duration horizonte = Duration.ofDays(dias);
        LocalDateTime inicio = inicioElegido(periodo());

        // Nada anterior a la fecha de inicio existe para la operacion, como pide el caso. Sin
        // este filtro, los pedidos de los dias previos entran todos de golpe en la primera
        // iteracion -su registro ya paso- y buena parte llega con el plazo vencido de antemano:
        // arrancar el 10 de marzo de 2026 daba 357 vencidos en el mes mas flojo de todos.
        List<Pedido> ventas = new ArrayList<>();
        for (Pedido pedido : datos.ventas()) {
            if (!pedido.getFechaRegistro().isBefore(inicio)) {
                ventas.add(pedido);
            }
        }

        System.out.println("=== MEDICION DE Ta SOBRE " + dias + " DIAS (DATOS REALES) ===");
        System.out.println("Sin ritmo y sin pausas: esta corrida no cumple la ventana del caso");
        System.out.println("  a proposito. Sirve para conocer Ta antes de elegir la duracion.");
        System.out.println();
        datos.imprimirResumen();
        System.out.println("Flota: " + DatosCaso.flota().size() + " unidades");

        Parametros parametros = Parametros
                .constructor(entero("paqrap.grasp.construcciones", 8), alfa(), SEMILLA)
                .penalidadEspera(Double.parseDouble(
                        System.getProperty("paqrap.espera", "80.0")))
                .iteracionesTabu(entero("paqrap.tabu.iteraciones", 120))
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(40)
                .iteracionesSinMejora(25)
                .construir();

        MapaBloqueos mapa = new MapaBloqueos(datos.bloqueos());
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
        Planificador planificador = planificadorElegido(new Evaluador(enrutador));
        System.out.println("Planificador: " + planificador.getClass().getSimpleName()
                + "  | esfuerzo: " + esfuerzoDe(planificador, parametros));

        Simulador simulador = new Simulador(
                planificador,
                enrutador,
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(30),
                false
        ).conMantenimiento(datos.mantenimiento())
                .conEventosDeBloqueo(mapa)
                .conIntervaloMinimo(Duration.ofMinutes(15));

        if (Boolean.getBoolean("paqrap.bloques")) {
            simulador.leyendoPorBloques();
            System.out.println("Lectura por bloques: cada plan ve el tramo que va a ejecutar.");
        }

        // Sin ritmo: el observador no puede informar ocupacion porque no hay salto contra el cual
        // medirla. La columna queda vacia y eso es correcto, no un dato que falte.
        TrazaDePlanificacion traza = TrazaDePlanificacion.configurada(null);
        if (traza != null) {
            simulador.observadoPor(traza);
        }

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, horizonte, ventas, DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
        Reporte.imprimirEvolucionDiaria(resumen);
        imprimirQueRitmoAguanta(resumen, horizonte);
    }

    /**
     * De Ta a la duración: qué ritmos sostiene lo que se acaba de medir.
     *
     * Hay dos restricciones y tiran en sentidos opuestos.
     *
     * <pre>
     *   Sa &gt;= Ta maximo x margen     o una planificacion arranca antes de que termine la anterior
     *   Sc = Sa x K &lt;= tope          o pasa demasiada operacion sin que nadie replanifique
     * </pre>
     *
     * Como K queda fijado por la duración que se quiera mostrar ({@code K = horizonte / duracion}),
     * las dos se convierten en un <b>rango válido de Sa</b> para cada duración. Si el rango está
     * vacío, esa duración no se sostiene con este algoritmo y este esfuerzo.
     *
     * El Ta que se usa es el máximo y no el promedio: el salto tiene que aguantar la cola más
     * larga, que es la del final del horizonte.
     */
    private static void imprimirQueRitmoAguanta(ResumenSimulacion resumen, Duration horizonte) {
        if (resumen.iteracionesDePlanificacion() == 0) {
            return;
        }

        long taMaximo = resumen.taMaximoMs();
        double saMinimoMs = taMaximo * MARGEN;

        System.out.println();
        System.out.println("--- Que ritmo aguanta este Ta ---");
        System.out.printf(
                "Ta maximo observado: %d ms, con una cola de hasta %d pedidos.%n",
                taMaximo, resumen.colaMaximaPlanificada());
        System.out.printf(
                "Sa minimo: %.0f ms (Ta maximo x %.2f de margen).%n", saMinimoMs, MARGEN);
        System.out.printf(
                "Sc tope: %d min, un cuarto del plazo mas corto del caso.%n", SC_TOPE_MINUTOS);
        System.out.println();
        System.out.printf("  %9s %7s %22s %11s  %s%n",
                "duracion", "K", "Sa valido", "imagenes", "veredicto");

        boolean alguna = false;
        for (int duracion : DURACIONES_CANDIDATAS) {
            double k = horizonte.toMinutes() / (double) duracion;
            // Sc = Sa x K, de modo que el tope de Sc impone un tope a Sa.
            double saMaximoMs = SC_TOPE_MINUTOS * 60_000.0 / k;

            if (saMinimoMs > saMaximoMs) {
                System.out.printf("  %7d min %7.0f %22s %11s  %s%n",
                        duracion, k, "-", "-",
                        "no se sostiene: Ta no cabe en un Sc tolerable");
                continue;
            }
            alguna = true;
            System.out.printf("  %7d min %7.0f %9.1f s - %6.1f s %11s  %s%n",
                    duracion, k,
                    saMinimoMs / 1000.0, saMaximoMs / 1000.0,
                    imagenes(duracion, saMinimoMs, saMaximoMs),
                    duracion == DURACION_RECOMENDADA ? "<-- el de la presentacion" : "");
        }

        System.out.println();
        System.out.println("  Dentro del rango, un Sa chico da mas imagenes y un Sc menor, o sea");
        System.out.println("  mas fluidez y mejor calidad logistica. El limite de abajo es Ta.");

        recomendar(alguna, saMinimoMs, horizonte);
    }

    /** Cuántas imágenes salen en los extremos del rango de Sa. */
    private static String imagenes(int duracionMin, double saMinimoMs, double saMaximoMs) {
        long conSaMaximo = Math.round(duracionMin * 60_000.0 / saMaximoMs);
        long conSaMinimo = Math.round(duracionMin * 60_000.0 / saMinimoMs);
        return conSaMaximo + "-" + conSaMinimo;
    }

    /** El comando concreto con el que correr la presentación, o por qué todavía no se puede. */
    private static void recomendar(boolean alguna, double saMinimoMs, Duration horizonte) {
        System.out.println();
        if (!alguna) {
            System.out.println("NINGUNA DURACION DE LA VENTANA SE SOSTIENE con este esfuerzo.");
            System.out.println("  Bajale el esfuerzo al algoritmo y vuelve a medir:");
            System.out.println("  -Dpaqrap.tabu.iteraciones=60  o  -Dpaqrap.grasp.construcciones=4");
            return;
        }

        double k = horizonte.toMinutes() / (double) DURACION_RECOMENDADA;
        double saMaximoMs = SC_TOPE_MINUTOS * 60_000.0 / k;
        if (saMinimoMs > saMaximoMs) {
            System.out.printf(
                    "La duracion de %d min no se sostiene; elige una de las que si aparecen"
                            + " arriba.%n", DURACION_RECOMENDADA);
            return;
        }

        // Se sugiere el refresco comodo mas chico que entre en el rango: mas imagenes, mejor Sc.
        long saSugerido = (long) Math.ceil(saMinimoMs / 1000.0) * 1000L;
        for (long candidato : SALTOS_CANDIDATOS) {
            if (candidato >= saMinimoMs && candidato <= saMaximoMs) {
                saSugerido = candidato;
                break;
            }
        }

        System.out.println("Para la corrida de presentacion:");
        System.out.printf("  java \"-Dpaqrap.duracion=%d\" \"-Dpaqrap.sa=%d\" -cp target/classes"
                        + " com.paqrap.demo.DemoSimulacion5Dias%n",
                DURACION_RECOMENDADA, saSugerido);
        System.out.printf("  Eso da K = %.0f, Sc = %.1f min y unas %d imagenes.%n",
                k, saSugerido / 1000.0 * k / 60.0,
                Math.round(DURACION_RECOMENDADA * 60_000.0 / saSugerido));
    }

    private static Planificador planificadorElegido(Evaluador evaluador) {
        String elegido = System.getProperty("paqrap.algoritmo", "tabu").toLowerCase();
        return switch (elegido) {
            case "grasp" -> new Grasp(evaluador);
            case "constructivo" -> new InsercionPorHolgura(evaluador);
            case "alns" -> new BusquedaAlns(evaluador, alnsConEsfuerzo());
            case "tabu" -> new BusquedaTabu(evaluador);
            default -> throw new IllegalArgumentException(
                    "paqrap.algoritmo debe ser tabu, grasp, alns o constructivo, y fue: " + elegido);
        };
    }

    private static String esfuerzoDe(Planificador planificador, Parametros parametros) {
        if (planificador instanceof BusquedaTabu) {
            return parametros.getIteracionesTabu() + " iteraciones de mejora";
        }
        if (planificador instanceof Grasp) {
            return parametros.getMaxIteraciones() + " construcciones, alfa " + parametros.getAlfa();
        }
        if (planificador instanceof BusquedaAlns) {
            return "destruir y reparar por defecto";
        }
        return "una sola pasada constructiva";
    }

    /** Esfuerzo de ALNS, en iteraciones de destruir y reparar. */
    private static com.paqrap.planificador.alns.ParametrosAlns alnsConEsfuerzo() {
        String valor = System.getProperty("paqrap.alns.iteraciones");
        com.paqrap.planificador.alns.ParametrosAlns base =
                com.paqrap.planificador.alns.ParametrosAlns.porDefecto();
        return valor == null ? base : base.conIteraciones(Integer.parseInt(valor));
    }

    /**
     * Instante en que arranca la operacion, o nulo para el primer dia del mes.
     *
     * El caso lo pide asi: se coloca fecha y hora, se agarra el dia en esa ubicacion y se avanzan
     * los dias del escenario. Nada anterior a ese instante existe para la operacion.
     *
     * Elegir bien la fecha importa mas de lo que parece. Los archivos del curso concentran sus
     * 5000 pedidos en cada vez menos dias -31 en enero de 2026, 6 en diciembre de 2028- asi que la
     * densidad diaria cambia por completo segun donde se arranque, y con ella la dificultad. Y las
     * primeras horas son siempre irreales porque la flota empieza vacia.
     *
     * Formato ISO: {@code 2027-03-15T14:00}. Tambien vale solo la fecha.
     */
    private static LocalDateTime inicioElegido(java.time.YearMonth porDefecto) {
        String valor = System.getProperty("paqrap.inicio");
        if (valor == null || valor.isBlank()) {
            return porDefecto.atDay(1).atStartOfDay();
        }
        return valor.contains("T")
                ? LocalDateTime.parse(valor)
                : java.time.LocalDate.parse(valor).atStartOfDay();
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
