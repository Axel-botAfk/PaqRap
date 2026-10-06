package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.DatosReales;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.planificador.InsercionPorHolgura;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.alns.BusquedaAlns;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Ritmo;
import com.paqrap.simulacion.Simulador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Corrida sobre los archivos reales entregados por el equipo docente: ventas del mes, bloqueos
 * del mes y plan de mantenimiento preventivo, los tres a la vez.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoDatosReales [dias] [carpeta] [latidoMin] [pisoMin]
 *
 * Por defecto simula 5 dias de setiembre de 2026 leyendo de datos/reales con la busqueda tabu,
 * a fondo y sin esperar: es el modo de medicion.
 *
 * <h2>Los argumentos son del escenario, no del algoritmo</h2>
 *
 * Los cuatro posicionales describen la operacion que se simula y valen igual para los tres
 * planificadores. El esfuerzo de busqueda de cada algoritmo va por propiedad, de modo que
 * corriendo GRASP nunca haya que pasar un numero de la tabu ni al reves.
 *
 * Propiedades del escenario:
 *   -Dpaqrap.periodo=202610                      mes a simular; por defecto setiembre de 2026
 *   -Dpaqrap.algoritmo=tabu|grasp|alns|constructivo   cual planificador corre la operacion
 *   -Dpaqrap.bloques=true                        lectura por bloques: cada planificacion ve
 *                                                tambien los pedidos que llegaran durante el
 *                                                tramo que esta por ejecutarse
 *   -Dpaqrap.traza=20                            imprime una linea por planificacion (una de
 *                                                cada 20) con la cola, la carga y el Ta
 *   -Dpaqrap.seguir=P-00689                      traza un pedido en cada replanificacion
 *   -Dpaqrap.sa=5000                             salto del algoritmo (Sa) en milisegundos
 *   -Dpaqrap.k=14                                proporcionalidad del tiempo (K)
 *
 * Propiedades de cada algoritmo, que solo usa el suyo:
 *   -Dpaqrap.tabu.iteraciones=120                iteraciones de mejora de la busqueda tabu
 *   -Dpaqrap.espera=80                          costo por producto y hora de espera
 *   -Dpaqrap.alns.iteraciones=200               esfuerzo de ALNS
 *   -Dpaqrap.grasp.construcciones=8              soluciones completas que arma GRASP
 *   -Dpaqrap.grasp.alfa=0.30                     apertura de la lista restringida de GRASP
 *
 * Indicando Sa y K la corrida pasa a ir al paso de un reloj real, que es el modo para mostrar
 * el mapa: cada salto dura Sa y consume Sc = Sa x K de operacion. Sin ellos manda el latido.
 */
public final class DemoDatosReales {
    private static final YearMonth PERIODO_POR_DEFECTO = YearMonth.of(2026, 9);
    private static final int DIAS_POR_DEFECTO = 5;

    /** Semilla fija: dos corridas del mismo escenario tienen que dar lo mismo. */
    private static final long SEMILLA = 20260901L;

    private DemoDatosReales() {
    }

    public static void main(String[] args) throws IOException {
        int dias = args.length > 0 ? Integer.parseInt(args[0]) : DIAS_POR_DEFECTO;
        Path carpeta = args.length > 1 ? Path.of(args[1]) : DatosReales.CARPETA_POR_DEFECTO;
        int minutosEntrePlanes = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        int minutosMinimos = args.length > 3 ? Integer.parseInt(args[3]) : 15;

        DatosReales datos;
        try {
            datos = DatosReales.cargar(carpeta, periodo());
        } catch (IOException falta) {
            System.out.println(falta.getMessage());
            System.out.println("Copie ahi los archivos del mes o indique otro con -Dpaqrap.periodo.");
            return;
        }

        List<Pedido> ventas = datos.ventas();
        List<Bloqueo> bloqueos = datos.bloqueos();
        PlanMantenimiento mantenimiento = datos.mantenimiento();

        LocalDateTime inicio = periodo().atDay(1).atStartOfDay();

        System.out.println("=== DATOS REALES DEL CASO ===");
        datos.imprimirResumen();
        imprimirMantenimientoDelPeriodo(mantenimiento, inicio, dias);

        MapaBloqueos mapa = new MapaBloqueos(bloqueos);
        System.out.println("Nodos cerrados al arrancar: " + mapa.nodosBloqueadosEn(inicio).size());
        System.out.println("Horizonte: " + dias + " dias | replanifica por eventos (latido de "
                + minutosEntrePlanes + " min, piso de " + minutosMinimos + " min)");

        Parametros parametros = Parametros
                .constructor(entero("paqrap.grasp.construcciones", 8), alfa(), SEMILLA)
                .penalidadEspera(Double.parseDouble(
                        System.getProperty("paqrap.espera", "80.0")))
                .iteracionesTabu(entero("paqrap.tabu.iteraciones", 120))
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(entero("paqrap.tabu.muestra", 40))
                .iteracionesSinMejora(25)
                .construir();

        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
        Planificador planificador = planificadorElegido(new Evaluador(enrutador));
        System.out.println("Planificador: " + planificador.getClass().getSimpleName()
                + "  | esfuerzo: " + esfuerzoDe(planificador, parametros));

        Ritmo ritmo = ritmoElegido();
        if (ritmo != null) {
            System.out.println("Ritmo: " + ritmo + "  (la corrida va al paso de un reloj real)");
            System.out.printf("  Duracion estimada en pantalla: %.1f min para %d dias%n",
                    dias * 24.0 * 60 / ritmo.proporcionalidad(), dias);
        }

        Simulador simulador = new Simulador(
                planificador,
                enrutador,
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(minutosEntrePlanes),
                false
        ).conMantenimiento(mantenimiento)
                .conEventosDeBloqueo(mapa)
                .conIntervaloMinimo(Duration.ofMinutes(minutosMinimos));

        if (Boolean.getBoolean("paqrap.bloques")) {
            simulador.leyendoPorBloques();
            System.out.println("Lectura por bloques: cada plan ve el tramo que va a ejecutar.");
        }

        TrazaDePlanificacion traza = TrazaDePlanificacion.configurada(ritmo);
        if (traza != null) {
            simulador.observadoPor(traza);
        }

        if (ritmo != null) {
            simulador.conRitmo(ritmo);
        }

        String seguido = System.getProperty("paqrap.seguir");
        if (seguido != null) {
            simulador.siguiendoA(seguido);
        }

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, Duration.ofDays(dias), ventas, DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
        Reporte.imprimirEvolucionDiaria(resumen);
    }

    /**
     * Cual de los tres planificadores corre la simulacion. La comparacion por plan mide una
     * planificacion aislada; esta corre la operacion entera, que es donde se ve si una ventaja
     * por plan se sostiene cuando cada decision condiciona a la siguiente.
     */
    /** Mes a simular, en formato aaaamm. */
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
        if (valor.length() != 6) {
            throw new IllegalArgumentException(
                    "paqrap.periodo debe tener la forma aaaamm, y fue: " + valor);
        }
        return YearMonth.of(
                Integer.parseInt(valor.substring(0, 4)), Integer.parseInt(valor.substring(4)));
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

    /** El esfuerzo de búsqueda que de verdad va a usar el planificador elegido. */
    private static String esfuerzoDe(Planificador planificador, Parametros parametros) {
        if (planificador instanceof BusquedaTabu) {
            return parametros.getIteracionesTabu() + " iteraciones de mejora";
        }
        if (planificador instanceof Grasp) {
            return parametros.getMaxIteraciones() + " construcciones, alfa "
                    + parametros.getAlfa();
        }
        return "determinista, sin parametros de busqueda";
    }

    private static int entero(String propiedad, int porDefecto) {
        String valor = System.getProperty(propiedad);
        return valor == null ? porDefecto : Integer.parseInt(valor);
    }

    private static double alfa() {
        String valor = System.getProperty("paqrap.grasp.alfa");
        return valor == null ? 0.30 : Double.parseDouble(valor);
    }

    /**
     * Ritmo de reloj, si se pidieron Sa y K.
     *
     * Son dos propiedades y no argumentos posicionales porque la mayoria de las corridas son de
     * medicion y no quieren ritmo: agregarlas al final de la lista obligaria a repetir los seis
     * argumentos anteriores cada vez.
     */
    private static Ritmo ritmoElegido() {
        String sa = System.getProperty("paqrap.sa");
        String k = System.getProperty("paqrap.k");
        if (sa == null && k == null) {
            return null;
        }
        if (sa == null || k == null) {
            throw new IllegalArgumentException(
                    "Para fijar el ritmo hacen falta las dos: -Dpaqrap.sa y -Dpaqrap.k.");
        }
        return new Ritmo(Duration.ofMillis(Long.parseLong(sa)), Double.parseDouble(k));
    }

    private static void imprimirMantenimientoDelPeriodo(
            PlanMantenimiento plan,
            LocalDateTime inicio,
            int dias
    ) {
        System.out.println("Unidades fuera de servicio en el periodo simulado:");
        for (int i = 0; i < dias; i++) {
            LocalDate dia = inicio.toLocalDate().plusDays(i);
            System.out.println("  " + dia + "  " + plan.unidadesDelDia(dia));
        }
    }
}
