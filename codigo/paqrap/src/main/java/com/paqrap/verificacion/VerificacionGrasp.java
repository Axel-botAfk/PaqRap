package com.paqrap.verificacion;

import com.paqrap.datos.DatosCaso;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Turnos;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.Inventario;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.ResultadoPlan;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.paqrap.escenarios.Escenario;

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
        pruebaRefrigerioPorTurno();
        System.out.println("OK - 9 verificaciones de GRASP superadas.");
    }

    /** El plan construido respeta plazos, capacidad por viaje y stock por periodo. */
    private static void pruebaConstruccionValida() {
        Escenario escenario = Escenario.pequeno(AHORA);
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

        Escenario escenario = Escenario.pequeno(AHORA).conPedidos(List.of(
                Escenario.pedido("P-GRANDE", 29, 16, mayorCapacidad + 1, AHORA, 36)
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
        Escenario escenario = Escenario.pequeno(AHORA)
                .conAlmacenes(List.of(
                        Almacen.central(DatosCaso.ID_CENTRAL, DatosCaso.UBICACION_CENTRAL),
                        Almacen.intermedio(DatosCaso.ID_NOR_OESTE, DatosCaso.UBICACION_NOR_OESTE, 0),
                        Almacen.intermedio(DatosCaso.ID_ESTE, DatosCaso.UBICACION_ESTE, 0)
                ))
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(Escenario.pedido("P-001", 29, 16, 3, AHORA, 12)));

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
        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(Escenario.pedido("P-LEJOS", 57, 32, 2, AHORA, 1)));

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
        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(Escenario.pedido("P-JUSTO", 27, 20, 2, AHORA, 1)));

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

        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(
                        Escenario.pedido("P-001", 29, 16, 8, AHORA, 36),
                        Escenario.pedido("P-002", 25, 12, 8, AHORA, 36),
                        Escenario.pedido("P-003", 30, 18, 8, AHORA, 36),
                        Escenario.pedido("P-004", 24, 17, 8, AHORA, 36)
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
        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(
                        Escenario.pedido("P-001", 57, 29, 8, AHORA, 36),
                        Escenario.pedido("P-002", 58, 26, 8, AHORA, 36),
                        Escenario.pedido("P-003", 59, 28, 8, AHORA, 36),
                        Escenario.pedido("P-004", 56, 25, 8, AHORA, 36)
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

    /**
     * El conductor come una hora por turno y durante esa hora la unidad no avanza, de modo que
     * cada turno de ocho horas rinde siete de operación. El relevo, en cambio, no cuesta nada:
     * el conductor entrante alcanza al vehículo donde esté.
     */
    private static void pruebaRefrigerioPorTurno() {
        // Dentro del turno y sin pasar por las 11:00: no hay pausa.
        LocalDateTime tarde = LocalDateTime.of(2026, 9, 8, 12, 0);
        exigir(
                Turnos.avanzar(tarde, 2.0).equals(tarde.plusHours(2)),
                "Un tramo que no cruza la hora de alimentación no debería pagarla; dio "
                        + Turnos.avanzar(tarde, 2.0) + "."
        );

        // Sale 10:30 y conduce una hora: cruza las 11:00, así que come en el camino.
        LocalDateTime antesDelRefrigerio = LocalDateTime.of(2026, 9, 8, 10, 30);
        exigir(
                Turnos.avanzar(antesDelRefrigerio, 1.0)
                        .equals(LocalDateTime.of(2026, 9, 8, 12, 30)),
                "Cruzar la hora de alimentación debería sumarla; dio "
                        + Turnos.avanzar(antesDelRefrigerio, 1.0) + "."
        );

        // Nueve horas desde las 10:30 cruzan el refrigerio de las 11:00 y el de las 19:00.
        exigir(
                Turnos.avanzar(antesDelRefrigerio, 9.0)
                        .equals(LocalDateTime.of(2026, 9, 8, 21, 30)),
                "Nueve horas desde las 10:30 deberían terminar 21:30 con dos refrigerios; dio "
                        + Turnos.avanzar(antesDelRefrigerio, 9.0) + "."
        );
        exigir(
                Turnos.refrigerioEntre(antesDelRefrigerio, 9.0).toHours() == 2,
                "El refrigerio informado no coincide con el que se cobró."
        );

        // La exigencia del caso: la hora de alimentación va dentro de la jornada y a una hora o
        // más de un cambio de turno. Con el refrigerio a las 11:00 del turno 07:00-15:00, quedan
        // cuatro horas con el relevo anterior y tres con el siguiente.
        long turno = Turnos.indiceDeTurno(LocalDateTime.of(2026, 9, 8, 9, 0));
        LocalDateTime inicioDelTurno = Turnos.inicioDelTurno(turno);
        LocalDateTime comida = Turnos.refrigerioDelTurno(turno);
        LocalDateTime finDelTurno = Turnos.inicioDelTurno(turno + 1);

        exigir(
                comida.equals(LocalDateTime.of(2026, 9, 8, 11, 0)),
                "El refrigerio del turno 07:00-15:00 debería ser a las 11:00 y es a las "
                        + comida + "."
        );
        exigir(
                !comida.isBefore(inicioDelTurno.plusHours(1)),
                "El refrigerio debe empezar al menos una hora después del cambio de turno."
        );
        exigir(
                !comida.plusHours(1).isAfter(finDelTurno.minusHours(1)),
                "El refrigerio debe terminar al menos una hora antes del cambio de turno."
        );

        exigir(
                Turnos.HORAS_UTILES_POR_TURNO == 7.0,
                "Un turno de ocho horas debería rendir siete de operación."
        );
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
