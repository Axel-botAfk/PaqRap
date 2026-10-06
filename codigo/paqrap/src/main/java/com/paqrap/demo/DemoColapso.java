package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.DatosReales;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.Pedido;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.alns.BusquedaAlns;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Ritmo;
import com.paqrap.simulacion.Simulador;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulación hasta el colapso logístico, sobre los datos reales del caso.
 *
 * Corre con las ventas, los bloqueos y el mantenimiento del mes que entrega el equipo docente, y
 * amplifica la demanda día a día hasta que la flota deja de poder comprometerse con todo lo que
 * llega. Como el planificador nunca acepta una entrega fuera de plazo, el pedido que no alcanza a
 * ser atendido se queda esperando y, cuando vence, la operación colapsó.
 *
 * <h2>Por qué hay que amplificar</h2>
 *
 * La demanda real no colapsa esta flota: sobre setiembre entrega el 97% con la cola estable. Para
 * encontrar el punto de quiebre hay que subirla, y la forma que menos distorsiona el escenario es
 * <b>repetir los pedidos reales</b> en lugar de inventar otros: se conserva dónde caen los
 * clientes, a qué hora llegan y qué plazos traen, que es lo que hace que el colapso se parezca al
 * de la operación y no al de una demanda uniforme.
 *
 * El factor arranca en 1 y crece {@code incremento} por día. Con el valor por defecto, el día 10
 * llega el triple de demanda que el día 1.
 *
 * <h2>Qué mirar</h2>
 *
 * El día del primer vencimiento y, sobre todo, la evolución diaria de la cola: el quiebre se ve
 * venir varios días antes, cuando la cola deja de bajar entre jornadas.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoColapso [dias] [carpeta]
 *
 * Propiedades:
 *   -Dpaqrap.inicio=2027-03-15T14:00             fecha y hora de arranque; por defecto, el
 *                                                primer mes con datos en la carpeta
 *   -Dpaqrap.algoritmo=tabu|grasp|alns|constructivo   cuál planificador corre la operación
 *   -Dpaqrap.tabu.iteraciones=120                esfuerzo de la búsqueda tabú
 *   -Dpaqrap.espera=80                          costo por producto y hora de espera
 *   -Dpaqrap.alns.iteraciones=200               esfuerzo de ALNS
 *   -Dpaqrap.grasp.construcciones=8              esfuerzo de GRASP
 *   -Dpaqrap.bloques=false                       lectura clásica; por bloques es lo de serie
 *   -Dpaqrap.sa=5000                             refresco de pantalla, en milisegundos
 *   -Dpaqrap.sc=15                               minutos de operación por imagen (K sale solo)
 *   -Dpaqrap.medicion=true                       sin pausas, a fondo, para medir
 *   -Dpaqrap.traza=0                             apaga la traza, que aquí va encendida
 *   -Dpaqrap.traza=20                            imprime una línea por planificación (una de
 *                                                cada 20) con la cola, la carga y el Ta
 *
 * <h2>El bloque es fijo y chico, a propósito</h2>
 *
 * Aquí la corrida no tiene duración objetivo: corta en el primer vencido, de modo que cuánto dura
 * es un resultado y no algo que se pueda despejar. Eso deja el tamaño del bloque libre de la
 * presentación, así que se fija por criterio logístico: {@value #BLOQUE_MINUTOS} minutos, que es
 * bastante menos que el plazo más corto del caso.
 *
 * Importa porque la fecha de colapso depende del bloque en las dos formas de leer, y en
 * direcciones opuestas. Sin anticipación, un bloque grande hace esperar a los pedidos y adelanta
 * el colapso; con anticipación, un bloque grande le regala futuro al planificador y lo retrasa.
 * Con el bloque chico las dos variantes casi coinciden, que es la única situación en que la fecha
 * de colapso mide la flota y no la forma de leer los datos.
 *
 * Correr las dos y comparar la fecha es un resultado por sí mismo: dice cuánto del punto de
 * quiebre era flota y cuánto era información.
 */
public final class DemoColapso {
    private static final YearMonth PERIODO_POR_DEFECTO = YearMonth.of(2026, 10);
    /** Sin argumento se recorre todo lo que haya: el recorte al maximo real se hace despues. */
    private static final int DIAS_POR_DEFECTO = 9_999;


    /** Tamaño del bloque de planificación, en minutos. Elegido por logística, no por pantalla. */
    private static final int BLOQUE_MINUTOS = 30;


    /** Refresco por defecto, en milisegundos: cada cuanto avanza la pantalla. */
    private static final long SA_POR_DEFECTO_MS = 20_000L;

    private static final long SEMILLA = 20261001L;

    private DemoColapso() {
    }

    public static void main(String[] args) throws IOException {
        int dias = args.length > 0 ? Integer.parseInt(args[0]) : DIAS_POR_DEFECTO;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : DatosReales.CARPETA_POR_DEFECTO;

        LocalDateTime inicio = inicioElegido();

        List<YearMonth> disponibles;
        try {
            disponibles = DatosReales.mesesDisponibles(carpeta);
        } catch (IOException falta) {
            System.out.println("No se pudo leer la carpeta de datos: " + falta.getMessage());
            return;
        }
        if (disponibles.isEmpty()) {
            System.out.println("No hay archivos de ventas en " + carpeta.toAbsolutePath());
            return;
        }

        if (inicio == null) {
            inicio = disponibles.get(0).atDay(1).atStartOfDay();
        }

        // Los meses que se cargan salen de la fecha de inicio y de los dias pedidos, no de una
        // lista fija: es lo que describe el caso -se coloca fecha y hora y de ahi se avanza- y
        // evita leer decenas de miles de pedidos que nunca entran en el horizonte.
        int diasHastaElFinDeLosDatos = (int) java.time.temporal.ChronoUnit.DAYS.between(
                inicio, disponibles.get(disponibles.size() - 1).atEndOfMonth().atTime(23, 59));
        if (diasHastaElFinDeLosDatos <= 0) {
            System.out.println("La fecha de inicio " + inicio
                    + " queda despues del ultimo mes con datos ("
                    + disponibles.get(disponibles.size() - 1) + ").");
            return;
        }
        if (dias > diasHastaElFinDeLosDatos) {
            System.out.printf(
                    "Se pidieron %d dias y desde %s solo hay datos para %d; se recorta.%n",
                    dias, inicio.toLocalDate(), diasHastaElFinDeLosDatos);
            dias = diasHastaElFinDeLosDatos;
        }

        List<YearMonth> periodos = DatosReales.mesesQueCubren(inicio, dias);
        List<DatosReales> meses = new ArrayList<>();
        try {
            for (YearMonth mes : periodos) {
                meses.add(DatosReales.cargar(carpeta, mes));
            }
        } catch (IOException falta) {
            System.out.println(falta.getMessage());
            return;
        }

        // Nada anterior a la fecha de inicio existe para la operacion, como pide el caso.
        List<Pedido> reales = new ArrayList<>();
        List<Bloqueo> bloqueosDeTodos = new ArrayList<>();
        PlanMantenimiento mantenimiento = PlanMantenimiento.vacio();
        for (DatosReales mes : meses) {
            for (Pedido pedido : mes.ventas()) {
                if (!pedido.getFechaRegistro().isBefore(inicio)) {
                    reales.add(pedido);
                }
            }
            bloqueosDeTodos.addAll(mes.bloqueos());
            mantenimiento = mantenimiento.mas(mes.mantenimiento());
        }

        System.out.println("=== SIMULACION HASTA EL COLAPSO (DATOS REALES) ===");
        System.out.println("Inicio: " + inicio + "  (nada anterior existe para la operacion)");
        for (DatosReales mes : meses) {
            mes.imprimirResumen();
        }
        System.out.printf("Meses con datos en la carpeta: %d (%s a %s)%n",
                disponibles.size(), disponibles.get(0),
                disponibles.get(disponibles.size() - 1));
        System.out.println("Demanda: la real de los archivos, sin amplificar ni recortar.");
        if (reales.isEmpty()) {
            System.out.println("  Ningun pedido cae en el horizonte pedido.");
            System.out.println("  Los archivos de un mes no siempre cubren el mes entero: los");
            System.out.println("  ultimos meses concentran sus 5000 pedidos en 6 o 7 dias, de modo");
            System.out.println("  que arrancar pasada esa fecha deja la operacion sin demanda.");
            System.out.println("  Usa una fecha de inicio mas temprana dentro del mes.");
            return;
        }
        int ultimoDia = 1;
        for (Pedido pedido : reales) {
            ultimoDia = Math.max(ultimoDia, (int) java.time.temporal.ChronoUnit.DAYS.between(
                    inicio.toLocalDate(), pedido.getFechaRegistro().toLocalDate()) + 1);
        }
        int productos = 0;
        for (Pedido pedido : reales) {
            productos += pedido.getCantidad();
        }
        System.out.printf("  %d pedidos y %d productos, repartidos en %d dias%n",
                reales.size(), productos, ultimoDia);
        System.out.printf("  Densidad: %d pedidos y %d productos por dia%n",
                reales.size() / ultimoDia, productos / ultimoDia);
        System.out.println("Flota: " + DatosCaso.flota().size() + " unidades");
        System.out.println("Horizonte maximo: " + dias + " dias. Calculando...");

        Parametros parametros = Parametros
                .constructor(entero("paqrap.grasp.construcciones", 8), alfa(), SEMILLA)
                .penalidadEspera(Double.parseDouble(
                        System.getProperty("paqrap.espera", "80.0")))
                .iteracionesTabu(entero("paqrap.tabu.iteraciones", 120))
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(40)
                .iteracionesSinMejora(25)
                .construir();

        MapaBloqueos mapa = new MapaBloqueos(bloqueosDeTodos);
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
        Planificador planificador = planificadorElegido(new Evaluador(enrutador));
        System.out.println("Planificador: " + planificador.getClass().getSimpleName());

        Simulador simulador = new Simulador(
                planificador,
                enrutador,
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(BLOQUE_MINUTOS),
                true
        ).conMantenimiento(mantenimiento)
                .conEventosDeBloqueo(mapa)
                .conIntervaloMinimo(Duration.ofMinutes(BLOQUE_MINUTOS));

        // Por bloques de serie: es la dinamica que describe el caso -el planificador jala un
        // paquete, lo resuelve y lo deja al visualizador- y sin ella un pedido esperaria un bloque
        // entero antes de que alguien lo mire.
        boolean porBloques = !"false".equalsIgnoreCase(
                System.getProperty("paqrap.bloques", "true"));
        if (porBloques) {
            simulador.leyendoPorBloques();
        }

        Ritmo ritmo = ritmoElegido();
        if (ritmo != null) {
            simulador.conRitmo(ritmo);
            System.out.println("Ritmo: " + ritmo);
            System.out.printf(
                    "  Una imagen cada %.1f s | %.0f min de operacion por imagen | K = %.0f%n",
                    ritmo.milisegundosDelSalto() / 1000.0,
                    (double) ritmo.saltoDelConsumo().toMinutes(),
                    ritmo.proporcionalidad());
            System.out.printf(
                    "  A este ritmo, cada dia simulado son %.1f min de pantalla.%n",
                    24 * 60 / ritmo.proporcionalidad());
        } else {
            System.out.println("Modo medicion: sin pausas, a fondo.");
        }

        // En el colapso la traza va encendida por defecto: es la unica forma de ver venir el
        // quiebre, que se anuncia varias horas antes en que la cola deja de bajar.
        TrazaDePlanificacion traza = TrazaDePlanificacion.configurada(ritmo, "1");
        if (traza != null) {
            simulador.observadoPor(traza);
        }
        System.out.printf(
                "Bloque de planificacion: %d min | lectura %s%n",
                BLOQUE_MINUTOS,
                porBloques
                        ? "por bloques (ve el tramo que va a ejecutar)"
                        : "clasica (solo lo ya llegado)");

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, Duration.ofDays(dias), reales, DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
        Reporte.imprimirEvolucionDiaria(resumen);
    }

    /**
     * Ritmo con el que se muestra la corrida.
     *
     * <h2>Aqui manda Sc, no la duracion</h2>
     *
     * En la 5D se elige cuanto debe durar en pantalla y de ahi sale todo. Aqui no se puede: la
     * corrida termina con el primer pedido vencido, asi que cuanto dura es un <b>resultado</b> y
     * no algo que se pueda despejar.
     *
     * Eso deja Sc libre de la presentacion, y entonces se fija por criterio logistico: el bloque
     * de {@value #BLOQUE_MINUTOS} minutos, bastante por debajo del plazo mas corto del caso. Lo
     * que se ajusta a conveniencia es Sa -la velocidad de la pantalla- y K sale de la division.
     *
     * Asi la fecha de colapso no depende de lo rapido que se quiera ver: subir o bajar Sa cambia
     * cuanto tarda el video y no cambia ni una decision del planificador.
     */
    private static Ritmo ritmoElegido() {
        if (Boolean.getBoolean("paqrap.medicion")) {
            return null;
        }
        long sa = Long.parseLong(
                System.getProperty("paqrap.sa", String.valueOf(SA_POR_DEFECTO_MS)));
        int scMinutos = entero("paqrap.sc", BLOQUE_MINUTOS);
        double k = scMinutos * 60_000.0 / sa;
        return new Ritmo(Duration.ofMillis(sa), k);
    }


    private static Planificador planificadorElegido(Evaluador evaluador) {
        String elegido = System.getProperty("paqrap.algoritmo", "tabu").toLowerCase();
        return switch (elegido) {
            case "grasp" -> new Grasp(evaluador);
            case "constructivo" -> new com.paqrap.planificador.InsercionPorHolgura(evaluador);
            case "alns" -> new BusquedaAlns(evaluador, alnsConEsfuerzo());
            case "tabu" -> new BusquedaTabu(evaluador);
            default -> throw new IllegalArgumentException(
                    "paqrap.algoritmo debe ser tabu, grasp, alns o constructivo, y fue: " + elegido);
        };
    }

    /** Esfuerzo de ALNS, en iteraciones de destruir y reparar. */
    private static com.paqrap.planificador.alns.ParametrosAlns alnsConEsfuerzo() {
        String valor = System.getProperty("paqrap.alns.iteraciones");
        com.paqrap.planificador.alns.ParametrosAlns base =
                com.paqrap.planificador.alns.ParametrosAlns.porDefecto();
        return valor == null ? base : base.conIteraciones(Integer.parseInt(valor));
    }

    /**
     * Instante en que arranca la operacion, o nulo para empezar en el primer mes con datos.
     *
     * El caso lo pide explicitamente: se coloca fecha y hora, se agarra el dia en esa ubicacion y
     * se avanza. Nada anterior a ese instante existe para la operacion, ni como pedido pendiente
     * ni como carga en las unidades, de modo que las primeras horas son siempre irreales -la flota
     * arranca vacia- y conviene no leer nada de ellas.
     *
     * Formato ISO: {@code 2027-03-15T14:00}. Tambien vale solo la fecha.
     */
    private static LocalDateTime inicioElegido() {
        String valor = System.getProperty("paqrap.inicio");
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.contains("T")
                ? LocalDateTime.parse(valor)
                : java.time.LocalDate.parse(valor).atStartOfDay();
    }

    /**
     * Los meses que se recorren, en orden.
     *
     * <h2>Por que varios</h2>
     *
     * Un solo mes limita la corrida a 31 dias, y con una curva de demanda suave el quiebre puede
     * caer despues. Encadenar los meses que haya deja que la operacion llegue hasta donde de
     * verdad aguanta en lugar de hasta donde se acaba el archivo.
     *
     * Con {@code -Dpaqrap.periodos=202609,202610} se indican a mano. Sin nada, se usan los dos que
     * entrega el equipo docente.
     */
    private static List<YearMonth> periodos() {
        String valor = System.getProperty("paqrap.periodos");
        if (valor == null) {
            String uno = System.getProperty("paqrap.periodo");
            if (uno != null) {
                return List.of(mes(uno));
            }
            return List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        }
        List<YearMonth> meses = new ArrayList<>();
        for (String parte : valor.split(",")) {
            meses.add(mes(parte.trim()));
        }
        return meses;
    }

    private static YearMonth mes(String aaaamm) {
        return YearMonth.of(
                Integer.parseInt(aaaamm.substring(0, 4)), Integer.parseInt(aaaamm.substring(4)));
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
