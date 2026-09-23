package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.DatosReales;
import com.paqrap.modelo.Pedido;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
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
 *   java -cp target/classes com.paqrap.demo.DemoColapso [dias] [carpeta] [incrementoPorDia]
 *
 * Propiedades:
 *   -Dpaqrap.periodo=202610                      mes a usar; por defecto octubre de 2026
 *   -Dpaqrap.algoritmo=tabu|grasp|constructivo   cuál planificador corre la operación
 *   -Dpaqrap.tabu.iteraciones=120                esfuerzo de la búsqueda tabú
 *   -Dpaqrap.grasp.construcciones=8              esfuerzo de GRASP
 */
public final class DemoColapso {
    private static final YearMonth PERIODO_POR_DEFECTO = YearMonth.of(2026, 10);
    private static final int DIAS_POR_DEFECTO = 31;

    /** Cuánto crece el factor de demanda por día. */
    private static final double INCREMENTO_POR_DEFECTO = 0.22;

    private static final long SEMILLA = 20261001L;

    private DemoColapso() {
    }

    public static void main(String[] args) throws IOException {
        int dias = args.length > 0 ? Integer.parseInt(args[0]) : DIAS_POR_DEFECTO;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : DatosReales.CARPETA_POR_DEFECTO;
        double incremento = args.length > 2
                ? Double.parseDouble(args[2])
                : INCREMENTO_POR_DEFECTO;

        DatosReales datos;
        try {
            datos = DatosReales.cargar(carpeta, periodo());
        } catch (IOException falta) {
            System.out.println(falta.getMessage());
            return;
        }

        LocalDateTime inicio = periodo().atDay(1).atStartOfDay();
        List<Pedido> ventas = amplificar(datos.ventas(), inicio, incremento);

        System.out.println("=== SIMULACION HASTA EL COLAPSO (DATOS REALES) ===");
        datos.imprimirResumen();
        System.out.printf("Demanda amplificada: factor 1,00 el dia 1 y +%.2f por dia%n", incremento);
        System.out.println("  " + datos.ventas().size() + " pedidos reales -> "
                + ventas.size() + " tras amplificar");
        System.out.println("Flota: " + DatosCaso.flota().size() + " unidades");
        System.out.println("Horizonte maximo: " + dias + " dias. Calculando...");

        Parametros parametros = Parametros
                .constructor(entero("paqrap.grasp.construcciones", 8), alfa(), SEMILLA)
                .iteracionesTabu(entero("paqrap.tabu.iteraciones", 120))
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(40)
                .iteracionesSinMejora(25)
                .construir();

        MapaBloqueos mapa = new MapaBloqueos(datos.bloqueos());
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
        Planificador planificador = planificadorElegido(new Evaluador(enrutador));
        System.out.println("Planificador: " + planificador.getClass().getSimpleName());

        Simulador simulador = new Simulador(
                planificador,
                enrutador,
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(30),
                true
        ).conMantenimiento(datos.mantenimiento())
                .conEventosDeBloqueo(mapa)
                .conIntervaloMinimo(Duration.ofMinutes(15));

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, Duration.ofDays(dias), ventas, DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
        Reporte.imprimirEvolucionDiaria(resumen);
    }

    /**
     * Repite cada pedido tantas veces como indique el factor del día en que llega.
     *
     * La parte entera del factor da copias seguras y la fracción decide una copia más con esa
     * probabilidad, de modo que un factor de 2,3 multiplique la demanda por 2,3 y no por 2 ni
     * por 3. El sorteo va con semilla fija: dos corridas del mismo escenario tienen que traer
     * exactamente los mismos pedidos.
     *
     * Las copias conservan cliente, destino, cantidad, hora de llegada y plazo; solo cambia el
     * identificador. Es demanda del mismo perfil, no demanda distinta.
     */
    private static List<Pedido> amplificar(
            List<Pedido> reales,
            LocalDateTime inicio,
            double incremento
    ) {
        java.util.Random sorteo = new java.util.Random(SEMILLA);
        List<Pedido> amplificadas = new ArrayList<>(reales.size());

        for (Pedido pedido : reales) {
            long dia = Duration.between(inicio, pedido.getFechaRegistro()).toDays();
            double factor = 1.0 + Math.max(0, dia) * incremento;

            int copias = (int) Math.floor(factor);
            if (sorteo.nextDouble() < factor - copias) {
                copias++;
            }

            amplificadas.add(pedido);
            for (int i = 2; i <= copias; i++) {
                amplificadas.add(new Pedido(
                        pedido.getId() + "#" + i,
                        pedido.getClienteId(),
                        pedido.getDestino(),
                        pedido.getCantidad(),
                        pedido.getFechaRegistro(),
                        pedido.getHorasPlazo()
                ));
            }
        }
        return amplificadas;
    }

    private static Planificador planificadorElegido(Evaluador evaluador) {
        String elegido = System.getProperty("paqrap.algoritmo", "tabu").toLowerCase();
        return switch (elegido) {
            case "grasp" -> new Grasp(evaluador);
            case "constructivo" -> new com.paqrap.planificador.InsercionPorHolgura(evaluador);
            case "tabu" -> new BusquedaTabu(evaluador);
            default -> throw new IllegalArgumentException(
                    "paqrap.algoritmo debe ser tabu, grasp o constructivo, y fue: " + elegido);
        };
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
