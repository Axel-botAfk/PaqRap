package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.planificador.ConstructorVecinoMasCercano;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.AplicadorMovimiento;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.planificador.tabu.Movimiento;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verificación ejecutable sin JUnit de la búsqueda tabú, en la misma línea que
 * {@link VerificacionGrasp}.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.VerificacionTabu
 */
public final class VerificacionTabu {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 8, 8, 0);

    private VerificacionTabu() {
    }

    public static void main(String[] args) {
        pruebaNoEmpeoraSuSolucionInicial();
        pruebaEsIndependienteDeGrasp();
        pruebaRepartoEntreUnidades();
        pruebaPlanResultanteFactible();
        pruebaEncadenaViajes();
        pruebaCoberturaDePedidos();
        pruebaReproducibilidadPorSemilla();
        pruebaAsignacionDePedidoPendiente();
        pruebaMovimientosReversibles();
        System.out.println("OK - 9 verificaciones de búsqueda tabú superadas.");
    }

    /**
     * La mejora conserva la mejor solución encontrada, así que nunca puede devolver algo peor
     * que su propio punto de partida. Se compara con la función objetivo y no solo con el
     * costo, porque incorporar un pedido pendiente puede encarecer el plan y aun así mejorarlo.
     */
    private static void pruebaNoEmpeoraSuSolucionInicial() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA);
        Parametros parametros = parametros(20260908L);
        Evaluador evaluador = new Evaluador(escenario.distancias());
        BusquedaTabu tabuSearch = new BusquedaTabu(evaluador);

        Solucion inicial = tabuSearch.construirSolucionInicial(escenario.estado(), parametros);
        Solucion mejorada = tabuSearch.planificar(escenario.estado(), parametros);

        double valorInicial = evaluador.objetivo(inicial, escenario.estado(), parametros);
        double valorMejorado = evaluador.objetivo(mejorada, escenario.estado(), parametros);

        afirmar(
                Double.isFinite(valorInicial),
                "El constructivo de partida entregó un plan infactible."
        );
        afirmar(
                valorMejorado <= valorInicial + 1e-6,
                "La búsqueda tabú devolvió un plan peor que su solución de partida."
        );
    }

    /**
     * Los dos algoritmos que se comparan tienen que ser independientes: la tabú no debe apoyarse
     * en GRASP, porque entonces la comparación mediría un algoritmo y su fase de mejora.
     */
    private static void pruebaEsIndependienteDeGrasp() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA);
        Parametros parametros = parametros(20260908L);

        Solucion partida = new BusquedaTabu(escenario.distancias())
                .construirSolucionInicial(escenario.estado(), parametros);

        afirmar(
                partida.getAlgoritmo().equals(ConstructorVecinoMasCercano.NOMBRE),
                "La búsqueda tabú sigue partiendo de " + partida.getAlgoritmo() + "."
        );

        Solucion grasp = new Grasp(escenario.distancias()).planificar(escenario.estado(), parametros);
        afirmar(
                grasp.getAlgoritmo().equals(Grasp.NOMBRE),
                "GRASP dejó de identificarse como tal."
        );
    }

    /** Plazos, capacidad por viaje y stock por periodo de reposición. */
    private static void pruebaPlanResultanteFactible() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA);
        Evaluador evaluador = new Evaluador(escenario.distancias());
        Solucion tabu = new BusquedaTabu(evaluador).planificar(escenario.estado(), parametros(7L));

        afirmar(
                evaluador.evaluarPlan(tabu, escenario.estado()).factible(),
                "El plan viola plazos, capacidad o el stock de algún almacén."
        );

        for (Ruta viaje : tabu.getRutas()) {
            afirmar(!viaje.estaVacia(), "El plan conserva viajes sin entregas.");
            afirmar(
                    viaje.getCargaTotal() <= viaje.getVehiculo().getCapacidad(),
                    "El viaje " + viaje.getId() + " excede la capacidad de la unidad."
            );
        }
    }

    /**
     * La mejora tiene que poder repartir el trabajo en varios viajes de la misma unidad: con
     * una sola unidad disponible, la única forma de atender más producto del que carga de una
     * vez es volver a un almacén y salir de nuevo.
     */
    private static void pruebaEncadenaViajes() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA)
                .conVehiculos(List.of(
                        EscenarioDemo.unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(
                        EscenarioDemo.pedido("P-001", 29, 16, 8, AHORA, 36),
                        EscenarioDemo.pedido("P-002", 25, 12, 8, AHORA, 36),
                        EscenarioDemo.pedido("P-003", 30, 18, 8, AHORA, 36),
                        EscenarioDemo.pedido("P-004", 24, 17, 8, AHORA, 36)
                ));

        Evaluador evaluador = new Evaluador(escenario.distancias());
        Solucion tabu = new BusquedaTabu(evaluador).planificar(escenario.estado(), parametros(11L));

        afirmar(
                tabu.getCantidadPedidosNoAsignados() == 0,
                "Quedaron pedidos sin atender pese a que la unidad podía encadenar otro viaje."
        );
        afirmar(tabu.getCantidadRutas() >= 2, "El plan no encadenó varios viajes.");
        afirmar(
                evaluador.evaluarPlan(tabu, escenario.estado()).factible(),
                "El plan con viajes encadenados resultó infactible."
        );
    }

    /**
     * El constructivo tiene que repartir el trabajo entre toda la flota, no agotar unas pocas
     * unidades.
     *
     * La primera versión llenaba las unidades una por una, de la más barata a la más cara: con
     * una cola de unas decenas de pedidos, bicicletas y motos se llevaban todo y los autos
     * quedaban parados. Medido sobre los datos reales del caso, la flota operaba al 43% mientras
     * se vencían plazos. Esta prueba fija la corrección: con más pedidos que unidades, tienen
     * que trabajar los tres tipos y buena parte de la flota.
     */
    private static void pruebaRepartoEntreUnidades() {
        List<Pedido> muchos = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            // Repartidos por toda la ciudad y con cantidades que van de 2 a 13 unidades.
            muchos.add(EscenarioDemo.pedido(
                    String.format("P-%03d", i + 1),
                    3 + (i * 7) % 65,
                    2 + (i * 11) % 46,
                    2 + i % 12,
                    AHORA,
                    36));
        }

        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA)
                .conVehiculos(DatosCaso.flota())
                .conPedidos(muchos);

        Solucion inicial = new BusquedaTabu(escenario.distancias())
                .construirSolucionInicial(escenario.estado(), parametros(3L));

        Set<String> unidadesUsadas = new HashSet<>();
        Set<TipoVehiculo> tiposUsados = new HashSet<>();
        for (Ruta viaje : inicial.getRutas()) {
            unidadesUsadas.add(viaje.getVehiculo().getId());
            tiposUsados.add(viaje.getVehiculo().getTipo());
        }

        afirmar(
                tiposUsados.size() == TipoVehiculo.values().length,
                "El plan no usa los tres tipos de unidad, solo " + tiposUsados + "."
        );
        afirmar(
                unidadesUsadas.size() >= 20,
                "Con 60 pedidos y 37 unidades el trabajo quedó concentrado en solo "
                        + unidadesUsadas.size() + " unidades."
        );
    }

    /** Ningún pedido puede perderse ni duplicarse durante los movimientos. */
    private static void pruebaCoberturaDePedidos() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA);
        Solucion tabu = new BusquedaTabu(escenario.distancias())
                .planificar(escenario.estado(), parametros(99L));

        List<String> vistos = new ArrayList<>();
        for (Ruta ruta : tabu.getRutas()) {
            for (Pedido pedido : ruta.getPedidos()) {
                vistos.add(pedido.getId());
            }
        }
        for (Pedido pedido : tabu.getPedidosNoAsignados()) {
            vistos.add(pedido.getId());
        }

        Set<String> unicos = new HashSet<>(vistos);
        afirmar(unicos.size() == vistos.size(), "Hay pedidos duplicados en el plan.");
        afirmar(
                unicos.size() == escenario.pedidos().size(),
                "El plan no cubre todos los pedidos de la instancia."
        );
    }

    /** Misma semilla, misma instancia, mismo resultado. */
    private static void pruebaReproducibilidadPorSemilla() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA);
        Parametros parametros = parametros(1234L);

        Solucion primera = new BusquedaTabu(escenario.distancias())
                .planificar(escenario.estado(), parametros);
        Solucion segunda = new BusquedaTabu(escenario.distancias())
                .planificar(escenario.estado(), parametros);

        afirmar(
                firma(primera).equals(firma(segunda)),
                "Dos ejecuciones con la misma semilla produjeron planes distintos."
        );
    }

    /**
     * Movimiento de asignación: se parte de un plan con un pedido en la bolsa de pendientes
     * y una unidad libre, y la búsqueda debe incorporarlo.
     */
    private static void pruebaAsignacionDePedidoPendiente() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA);
        Solucion inicial = escenario.solucionDePrueba();

        Solucion mejorada = new BusquedaTabu(escenario.distancias())
                .mejorar(inicial, escenario.estado(), parametros(5L));

        afirmar(
                mejorada.getCantidadPedidosNoAsignados() == 0,
                "La búsqueda tabú no incorporó el pedido pendiente."
        );
        afirmar(
                inicial.getCantidadPedidosNoAsignados() == 1,
                "La búsqueda tabú modificó la solución recibida."
        );
    }

    /**
     * Los vecinos se evalúan aplicando y deshaciendo el movimiento sobre la misma solución.
     * Si deshacer no restituyera el estado exacto, la búsqueda avanzaría sobre datos corruptos.
     */
    private static void pruebaMovimientosReversibles() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA);

        List<Movimiento> movimientos = List.of(
                Movimiento.trasladar(0, 0, 1, 1, escenario.pedido("P-001")),
                Movimiento.trasladar(0, 1, 0, 0, escenario.pedido("P-002")),
                Movimiento.trasladarARutaNueva(0, 0, escenario.pedido("P-001"),
                        escenario.vehiculo("TB01"), escenario.almacen(DatosCaso.ID_NOR_OESTE)),
                Movimiento.intercambiar(0, 0, 1, 0, escenario.pedido("P-001")),
                Movimiento.invertir(0, 0, 1),
                Movimiento.cambiarVehiculo(0, escenario.vehiculo("TB01")),
                Movimiento.cambiarAlmacen(0, escenario.almacen(DatosCaso.ID_ESTE)),
                Movimiento.asignarPendiente(escenario.pedido("P-005"), 1, 1),
                Movimiento.asignarPendienteEnRutaNueva(escenario.pedido("P-005"),
                        escenario.vehiculo("TB02"), escenario.almacen(DatosCaso.ID_ESTE))
        );

        for (Movimiento movimiento : movimientos) {
            Solucion solucion = escenario.solucionDePrueba();
            String antes = firma(solucion);

            AplicadorMovimiento.Deshacer deshacer = AplicadorMovimiento.aplicar(solucion, movimiento);
            afirmar(
                    !firma(solucion).equals(antes),
                    "El movimiento " + movimiento + " no modificó la solución."
            );

            deshacer.ejecutar();
            afirmar(
                    firma(solucion).equals(antes),
                    "El movimiento " + movimiento + " no se pudo deshacer."
            );
        }
    }

    private static Parametros parametros(long semilla) {
        return Parametros.constructor(20, 0.30, semilla)
                .iteracionesTabu(200)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(50)
                .iteracionesSinMejora(30)
                .limiteMilisegundos(2_000L)
                .construir();
    }

    /** Representación textual del plan, usada para comparar estados. */
    private static String firma(Solucion solucion) {
        StringBuilder texto = new StringBuilder();
        for (Ruta ruta : solucion.getRutas()) {
            texto.append(ruta.getId())
                    .append('|').append(ruta.getAlmacen().getId())
                    .append('|').append(ruta.getVehiculo().getId())
                    .append('|').append(ruta.getPedidos())
                    .append('\n');
        }
        texto.append("pendientes=").append(solucion.getPedidosNoAsignados());
        return texto.toString();
    }

    private static void afirmar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
