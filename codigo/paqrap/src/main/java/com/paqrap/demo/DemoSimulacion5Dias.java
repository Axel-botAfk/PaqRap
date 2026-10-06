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
import com.paqrap.simulacion.Ritmo;
import com.paqrap.simulacion.Simulador;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Simulación de cinco días de operación: el escenario 5D del caso, sobre los datos reales.
 *
 * <h2>El requisito de duración</h2>
 *
 * El caso pide que la 5D <b>tarde entre 30 y 60 minutos en ejecutarse</b>, y aclara que eso
 * significa que en ese rato se vea la representación gráfica de los cinco días. O sea que no es
 * un plazo máximo sino una ventana: correrla a fondo y terminar en quince minutos también la
 * incumple, porque no da tiempo a ver nada.
 *
 * Por eso esta corrida va, por defecto, al paso de un reloj real. La duración se elige y de ahí
 * sale todo lo demás:
 *
 * <pre>
 *   K  = cinco días de operación / lo que debe durar en pantalla
 *   Sc = Sa × K
 * </pre>
 *
 * <h2>Duración y fluidez son dos perillas distintas</h2>
 *
 * <b>K decide cuánto dura</b> y nada más. <b>Sa decide cuán fluido se ve</b>: es cada cuánto
 * aparece una imagen nueva. Bajar Sa no acorta la corrida —salen más saltos, cada uno más
 * corto— pero sí la hace más suave, porque el mapa se refresca más seguido.
 *
 * Lo que sí cuesta bajar Sa es <b>cómputo</b>: más saltos son más planificaciones, y cada una
 * sigue costando más o menos lo mismo porque la cola de pedidos no se achica. Con el valor por
 * defecto la operación se resuelve en algo más de un tercio del tiempo en pantalla, y el resto
 * son las pausas; de ahí para abajo el margen se estrecha rápido.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoSimulacion5Dias [carpeta]
 *
 * Propiedades:
 *   -Dpaqrap.duracion=45                         minutos que debe durar en pantalla
 *   -Dpaqrap.sa=5000                             cada cuántos ms se refresca (fluidez)
 *   -Dpaqrap.medicion=true                       sin pausas, a fondo, para medir Ta
 *   -Dpaqrap.periodo=202609                      mes a simular
 *   -Dpaqrap.algoritmo=tabu|grasp|alns|constructivo   cuál planificador corre la operación
 *   -Dpaqrap.bloques=true                        lectura por bloques: cada planificación ve
 *                                                también los pedidos que llegarán durante el
 *                                                tramo que está por ejecutarse
 *   -Dpaqrap.traza=20                            imprime una línea por planificación (una de
 *                                                cada 20) con la cola, la carga y el Ta
 *   -Dpaqrap.tabu.iteraciones=120                esfuerzo de la búsqueda tabú
 *   -Dpaqrap.espera=80                          costo por producto y hora de espera
 *   -Dpaqrap.alns.iteraciones=200               esfuerzo de ALNS
 *   -Dpaqrap.grasp.construcciones=8              esfuerzo de GRASP
 */
public final class DemoSimulacion5Dias {
    private static final YearMonth PERIODO_POR_DEFECTO = YearMonth.of(2026, 9);
    private static final int DIAS = 5;
    private static final long SEMILLA = 20260901L;

    /** Minutos en pantalla por defecto: el centro de la ventana que pide el caso. */
    private static final int DURACION_POR_DEFECTO = 45;

    /** Refresco por defecto. Con 45 minutos da unas 540 imágenes, una cada cinco segundos. */
    private static final long SA_POR_DEFECTO_MS = 5_000L;

    private static final int DURACION_MINIMA = 30;
    private static final int DURACION_MAXIMA = 60;

    private DemoSimulacion5Dias() {
    }

    public static void main(String[] args) throws IOException {
        Path carpeta = args.length > 0 ? Path.of(args[0]) : DatosReales.CARPETA_POR_DEFECTO;

        DatosReales datos;
        try {
            datos = DatosReales.cargar(carpeta, periodo());
        } catch (IOException falta) {
            System.out.println(falta.getMessage());
            return;
        }

        Duration horizonte = Duration.ofDays(DIAS);
        LocalDateTime inicio = periodo().atDay(1).atStartOfDay();

        System.out.println("=== SIMULACION 5D (DATOS REALES) ===");
        datos.imprimirResumen();
        System.out.println("Flota: " + DatosCaso.flota().size() + " unidades");

        Ritmo ritmo = ritmoElegido(horizonte);
        anunciarRitmo(ritmo, horizonte);

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
        System.out.println("Planificador: " + planificador.getClass().getSimpleName());

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

        TrazaDePlanificacion traza = TrazaDePlanificacion.configurada(ritmo);
        if (traza != null) {
            simulador.observadoPor(traza);
        }

        if (ritmo != null) {
            simulador.conRitmo(ritmo);
        }

        long antes = System.currentTimeMillis();
        ResumenSimulacion resumen = simulador.correr(
                inicio, horizonte, datos.ventas(), DatosCaso.flota());
        long transcurrido = System.currentTimeMillis() - antes;

        Reporte.imprimirResumen(resumen, transcurrido);
        Reporte.imprimirEvolucionDiaria(resumen);
        comprobarLaVentana(transcurrido, ritmo);
    }

    /**
     * Ritmo con el que se mostrará la corrida, salvo que se pida el modo de medición.
     *
     * Se elige la duración y de ahí sale K; Sa se elige aparte porque gobierna otra cosa, la
     * fluidez. Es deliberado que el parámetro sea la duración y no K: K es un número sin
     * significado para quien prepara la presentación, y la duración es justo lo que el caso pide.
     */
    private static Ritmo ritmoElegido(Duration horizonte) {
        if (Boolean.getBoolean("paqrap.medicion")) {
            return null;
        }
        int minutos = entero("paqrap.duracion", DURACION_POR_DEFECTO);
        if (minutos < DURACION_MINIMA || minutos > DURACION_MAXIMA) {
            System.out.printf(
                    "AVISO: el caso pide entre %d y %d minutos y se pidieron %d.%n",
                    DURACION_MINIMA, DURACION_MAXIMA, minutos);
        }
        long sa = Long.parseLong(System.getProperty("paqrap.sa", String.valueOf(SA_POR_DEFECTO_MS)));
        return Ritmo.para(horizonte, Duration.ofMinutes(minutos), Duration.ofMillis(sa));
    }

    private static void anunciarRitmo(Ritmo ritmo, Duration horizonte) {
        if (ritmo == null) {
            System.out.println("Modo medicion: sin pausas, a fondo. No cumple la ventana del caso,");
            System.out.println("  pero es lo que sirve para conocer el Ta antes de elegir el ritmo.");
            return;
        }
        long imagenes = horizonte.toMinutes() / Math.max(1, ritmo.saltoDelConsumo().toMinutes());
        System.out.println("Ritmo: " + ritmo);
        System.out.printf("  Duracion en pantalla: %.0f min | una imagen cada %.1f s | %d imagenes%n",
                horizonte.toMinutes() / ritmo.proporcionalidad(),
                ritmo.milisegundosDelSalto() / 1000.0,
                imagenes);
    }

    /** Lo que el caso evalúa: que la corrida haya durado lo que tenía que durar. */
    private static void comprobarLaVentana(long milisegundos, Ritmo ritmo) {
        if (ritmo == null) {
            return;
        }
        double minutos = milisegundos / 60_000.0;
        System.out.println();
        if (minutos < DURACION_MINIMA || minutos > DURACION_MAXIMA) {
            System.out.printf(
                    "FUERA DE LA VENTANA: duro %.1f min y el caso pide entre %d y %d.%n",
                    minutos, DURACION_MINIMA, DURACION_MAXIMA);
            System.out.println("  Si duro de menos, algun salto no alcanzo a esperar porque el");
            System.out.println("  planificador se paso de Sa; mira el aviso del ritmo mas arriba.");
        } else {
            System.out.printf("Duracion en la ventana del caso: %.1f min (entre %d y %d).%n",
                    minutos, DURACION_MINIMA, DURACION_MAXIMA);
        }
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
