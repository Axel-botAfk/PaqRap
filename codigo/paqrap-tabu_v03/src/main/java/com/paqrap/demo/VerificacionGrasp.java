package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.Inventario;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.ResultadoPlan;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verificación ejecutable sin JUnit de la fase constructiva.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.VerificacionGrasp
 */
public final class VerificacionGrasp {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 8, 8, 0);

    private VerificacionGrasp() {
    }

    public static void main(String[] args) {
        pruebaConstruccionValida();
        pruebaCapacidad();
        pruebaCentralComoRespaldo();
        pruebaPlazo();
        pruebaAcondicionamientoFueraDelPlazo();
        pruebaRecargaDeAlmacenIntermedio();
        pruebaViajesEncadenados();
        pruebaRecargaEnElAlmacenMasCercano();
        System.out.println("OK - 8 verificaciones de GRASP superadas.");
    }

    /** El plan construido respeta plazos, capacidad por viaje y stock por periodo. */
    private static void pruebaConstruccionValida() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA);
        Evaluador evaluador = new Evaluador(escenario.distancias());

        Solucion solucion = new Grasp(evaluador)
                .planificar(escenario.estado(), Parametros.constructor(30, 0.30, 1L).construir());

        ResultadoPlan resultado = evaluador.evaluarPlan(solucion, escenario.estado());
        exigir(
                resultado.factible(),
                "El plan viola plazos, capacidad o el stock de algún almacén."
        );

        List<String> vistos = new ArrayList<>();
        for (Ruta viaje : solucion.getRutas()) {
            exigir(!viaje.estaVacia(), "El plan conserva viajes sin entregas.");
            exigir(
                    viaje.getCargaTotal() <= viaje.getVehiculo().getCapacidad(),
                    "El viaje " + viaje.getId() + " excede la capacidad de la unidad."
            );
            viaje.getPedidos().forEach(p -> vistos.add(p.getId()));
        }
        solucion.getPedidosNoAsignados().forEach(p -> vistos.add(p.getId()));

        Set<String> unicos = new HashSet<>(vistos);
        exigir(unicos.size() == vistos.size(), "Hay pedidos duplicados en el plan.");
        exigir(
                unicos.size() == escenario.pedidos().size(),
                "El plan no cubre todos los pedidos de la instancia."
        );
    }

    /** Un pedido mayor que la unidad más grande no puede asignarse sin entregas parciales. */
    private static void pruebaCapacidad() {
        int mayorCapacidad = TipoVehiculo.AUTO.getEspecificacionDelCaso().capacidadPaquetes();

        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA).conPedidos(List.of(
                EscenarioDemo.pedido("P-GRANDE", 29, 16, mayorCapacidad + 1, AHORA, 36)
        ));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(10, 0.30, 2L).construir());

        exigir(
                solucion.getCantidadPedidosNoAsignados() == 1,
                "Se asignó un pedido que excede la capacidad de toda la flota."
        );
    }

    /** Si los intermedios se quedaron sin stock, el pedido debe cargarse en el central. */
    private static void pruebaCentralComoRespaldo() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA)
                .conAlmacenes(List.of(
                        Almacen.central(DatosCaso.ID_CENTRAL, DatosCaso.UBICACION_CENTRAL),
                        Almacen.intermedio(DatosCaso.ID_NOR_OESTE, DatosCaso.UBICACION_NOR_OESTE, 0),
                        Almacen.intermedio(DatosCaso.ID_ESTE, DatosCaso.UBICACION_ESTE, 0)
                ))
                .conVehiculos(List.of(
                        EscenarioDemo.unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(EscenarioDemo.pedido("P-001", 29, 16, 3, AHORA, 12)));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(10, 0.0, 3L).construir());

        exigir(solucion.getCantidadRutas() == 1, "No se generó el viaje de respaldo.");
        exigir(
                solucion.getRuta(0).getAlmacen().getId().equals(DatosCaso.ID_CENTRAL),
                "El pedido no se cargó en el almacén central."
        );
    }

    /** Un plazo imposible de cumplir deja el pedido sin asignar. */
    private static void pruebaPlazo() {
        // 48 km sobre la retícula para una bicicleta de 12 km/h: cuatro horas de viaje.
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA)
                .conVehiculos(List.of(
                        EscenarioDemo.unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(EscenarioDemo.pedido("P-LEJOS", 57, 32, 2, AHORA, 1)));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(10, 0.30, 4L).construir());

        exigir(
                solucion.getCantidadPedidosNoAsignados() == 1,
                "Se aceptó un viaje que incumple el plazo."
        );
    }

    /**
     * La hora de acondicionamiento en el cliente queda fuera del plazo comprometido: un pedido
     * cuya llegada entra justo dentro del plazo debe aceptarse aunque la entrega termine después.
     */
    private static void pruebaAcondicionamientoFueraDelPlazo() {
        // 6 km desde el central a 12 km/h: llega a la media hora, con plazo de 1 hora.
        // Sumando el acondicionamiento la entrega cierra a la hora y media.
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA)
                .conVehiculos(List.of(
                        EscenarioDemo.unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(EscenarioDemo.pedido("P-JUSTO", 27, 20, 2, AHORA, 1)));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(10, 0.0, 5L).construir());

        exigir(
                solucion.getCantidadPedidosNoAsignados() == 0,
                "Se rechazó un pedido que llega dentro del plazo: el acondicionamiento "
                        + "no debe contarse contra la fecha límite."
        );

        MetricasRuta metricas = new Evaluador(escenario.distancias())
                .evaluarRuta(solucion.getRuta(0), AHORA);
        exigir(metricas.factible(), "El viaje resultó infactible al reevaluarlo.");
        exigir(
                metricas.duracionHoras() > 1.0,
                "La duración debe incluir la hora de acondicionamiento aunque el plazo no la cuente."
        );
    }

    /** Los almacenes intermedios vuelven a su capacidad máxima en la recarga de las 23:59:59. */
    private static void pruebaRecargaDeAlmacenIntermedio() {
        Almacen intermedio = Almacen.intermedio(DatosCaso.ID_ESTE, DatosCaso.UBICACION_ESTE, 10);
        Inventario inventario = new Inventario(List.of(intermedio), AHORA);

        exigir(inventario.disponible(intermedio, AHORA) == 10, "El stock inicial no se respetó.");

        inventario.consumir(intermedio, AHORA, 10);
        exigir(inventario.disponible(intermedio, AHORA) == 0, "El consumo no descontó el stock.");

        LocalDateTime maniana = AHORA.plusDays(1);
        exigir(
                inventario.disponible(intermedio, maniana) == Almacen.CAPACIDAD_MAXIMA_INTERMEDIO,
                "El almacén intermedio no se recargó al pasar las 23:59:59."
        );
    }

    /**
     * Con una sola unidad y más producto del que carga de una vez, la unidad debe volver a
     * cargar y encadenar otro viaje en lugar de dejar pedidos sin atender.
     */
    private static void pruebaViajesEncadenados() {
        int capacidad = TipoVehiculo.AUTO.getEspecificacionDelCaso().capacidadPaquetes();

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

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(20, 0.30, 6L).construir());

        exigir(
                solucion.getCantidadPedidosNoAsignados() == 0,
                "Quedaron pedidos sin atender pese a que la unidad podía hacer otro viaje."
        );
        exigir(
                solucion.getCantidadRutas() >= 2,
                "32 unidades de producto no caben en un viaje de " + capacidad
                        + ": debieron encadenarse varios."
        );
        for (Ruta viaje : solucion.getRutas()) {
            exigir(
                    viaje.getVehiculo().getId().equals("TA01"),
                    "Se usó una unidad que no estaba en la instancia."
            );
            exigir(
                    viaje.getCargaTotal() <= capacidad,
                    "El viaje " + viaje.getId() + " supera la capacidad de la unidad."
            );
        }
        exigir(
                new Evaluador(escenario.distancias())
                        .evaluarPlan(solucion, escenario.estado()).factible(),
                "El encadenamiento de viajes resultó infactible."
        );
    }

    /**
     * Una unidad que terminó de entregar lejos del central debe recargar en el almacén
     * intermedio que le queda cerca, en vez de cruzar la ciudad de vuelta.
     */
    private static void pruebaRecargaEnElAlmacenMasCercano() {
        EscenarioDemo escenario = EscenarioDemo.pequeno(AHORA)
                .conVehiculos(List.of(
                        EscenarioDemo.unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(
                        EscenarioDemo.pedido("P-001", 57, 29, 8, AHORA, 36),
                        EscenarioDemo.pedido("P-002", 58, 26, 8, AHORA, 36),
                        EscenarioDemo.pedido("P-003", 59, 28, 8, AHORA, 36),
                        EscenarioDemo.pedido("P-004", 56, 25, 8, AHORA, 36)
                ));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(20, 0.0, 7L).construir());

        exigir(solucion.getCantidadPedidosNoAsignados() == 0, "Quedaron pedidos sin atender.");

        boolean recargaEnElEste = solucion.getRutas().stream()
                .anyMatch(viaje -> viaje.getAlmacen().getId().equals(DatosCaso.ID_ESTE));
        exigir(
                recargaEnElEste,
                "Ningún viaje cargó en el almacén Este pese a que todas las entregas están ahí."
        );
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
