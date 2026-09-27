package com.paqrap.verificacion;

import com.paqrap.datos.DatosCaso;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
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
        pruebaUnidadQueYaLlevaCarga();
        pruebaElObjetivoCuentaProductos();
        pruebaNoSeEntregaAntesDelRegistro();
        pruebaElObjetivoPrefiereSalvarAlUrgente();
        pruebaPartirCuandoLaCapacidadQuedoFragmentada();
        System.out.println("OK - 14 verificaciones de GRASP superadas.");
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

    /**
     * Una unidad interrumpida a mitad de viaje llega a la planificación siguiente con producto
     * encima, y tiene que poder repartirlo desde donde está, sin volver a ningún almacén.
     *
     * Este caso solo aparece en corridas largas, cuando el reloj corta a una unidad después de
     * cargar. Se dejó pasar a producción y reventó con un índice fuera de rango sobre los datos
     * reales: la solución nacía con el viaje ya sembrado pero el contexto de GRASP no lo sabía.
     */
    private static void pruebaUnidadQueYaLlevaCarga() {
        Vehiculo conCarga = Escenario
                .unidad(TipoVehiculo.AUTO, 1, new Ubicacion(40, 30))
                .conCarga(6);

        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(conCarga))
                .conPedidos(List.of(
                        Escenario.pedido("P-CERCA", 41, 30, 4, AHORA, 12),
                        Escenario.pedido("P-LEJOS", 42, 31, 2, AHORA, 12)
                ));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(4, 0.30, 3L).construir());

        exigir(
                solucion.getPedidosNoAsignados().isEmpty(),
                "Con seis productos a bordo deberia repartir los seis y quedaron "
                        + solucion.getPedidosNoAsignados().size() + " sin asignar."
        );

        for (Ruta viaje : solucion.getRutas()) {
            exigir(
                    viaje.saleYaCargado(),
                    "Deberia repartir lo que ya lleva, sin estrenar un viaje desde un almacen."
            );
            exigir(
                    viaje.getCargaTotal() <= conCarga.getCargaABordo(),
                    "No puede repartir mas producto del que lleva encima: "
                            + viaje.getCargaTotal() + " de " + conCarga.getCargaABordo() + "."
            );
        }
    }

    /**
     * La penalidad se cobra por producto, no por pedido.
     *
     * Es lo que impide que al planificador le convenga sacrificar siempre el pedido grande: si
     * dejar afuera diez unidades costara lo mismo que dejar afuera una, sacrificaría la de diez
     * porque le libera diez veces más capacidad al mismo precio.
     */
    private static void pruebaElObjetivoCuentaProductos() {
        Escenario escenario = Escenario.pequeno(AHORA);
        Evaluador evaluador = new Evaluador(escenario.distancias());
        Parametros parametros = Parametros.constructor(1, 0.30, 3L).construir();
        double penalidad = parametros.getPenalidadNoAsignado();

        // Plazos holgados a proposito: con mas de un dia de margen la urgencia vale 1 y queda a
        // la vista la proporcionalidad con la cantidad, que es lo que esta prueba mide.
        Solucion dejaUno = new Solucion();
        dejaUno.agregarPedidoNoAsignado(Escenario.pedido("P-CHICO", 43, 26, 1, AHORA, 36));

        Solucion dejaDiez = new Solucion();
        dejaDiez.agregarPedidoNoAsignado(Escenario.pedido("P-GRANDE", 43, 26, 10, AHORA, 36));

        double conUno = evaluador.objetivo(dejaUno, escenario.estado(), parametros);
        double conDiez = evaluador.objetivo(dejaDiez, escenario.estado(), parametros);

        exigir(
                Math.abs(conUno - penalidad) < 1e-6,
                "Dejar un producto sin atender deberia costar " + penalidad + " y costo " + conUno + "."
        );
        exigir(
                Math.abs(conDiez - 10 * penalidad) < 1e-6,
                "Dejar diez productos sin atender deberia costar " + (10 * penalidad)
                        + " y costo " + conDiez + "."
        );
        exigir(
                conDiez > conUno,
                "Dejar afuera el pedido grande tiene que salir mas caro que dejar afuera el chico."
        );
    }

    /**
     * No se entrega un pedido antes de que el cliente lo haya hecho.
     *
     * Es el borde inferior de la ventana de tiempo. Con la lectura clásica nunca se activa,
     * porque la planificación arranca después del registro de todo lo que ve; con la lectura por
     * bloques es lo único que impide que anticipar se convierta en entregar en el pasado.
     */
    private static void pruebaNoSeEntregaAntesDelRegistro() {
        Escenario escenario = Escenario.pequeno(AHORA);
        Almacen central = escenario.almacenes().get(0);
        Vehiculo unidad = Escenario.unidad(TipoVehiculo.AUTO, 1, central.getUbicacion());

        // Un destino a un par de esquinas del almacen: la unidad llegaria en minutos.
        Ubicacion aLaVuelta = new Ubicacion(
                central.getUbicacion().x() + 2, central.getUbicacion().y());
        LocalDateTime dentroDeTresHoras = AHORA.plusHours(3);

        MetricasRuta medida = new Evaluador(escenario.distancias()).calcularMetricas(
                unidad.getUbicacion(),
                AHORA,
                central,
                unidad,
                List.of(Escenario.pedido(
                        "P-FUTURO", aLaVuelta.x(), aLaVuelta.y(), 1, dentroDeTresHoras, 12))
        );

        exigir(medida.factible(), "El viaje al pedido futuro deberia ser factible esperando.");
        LocalDateTime llegada = medida.horasLlegada().get("P-FUTURO");
        exigir(
                llegada.equals(dentroDeTresHoras),
                "La entrega deberia registrarse al momento del pedido (" + dentroDeTresHoras
                        + ") y quedo en " + llegada + "."
        );
        exigir(
                medida.duracionHoras() > 3.0,
                "La unidad queda ocupada la espera completa y la duracion fue de solo "
                        + medida.duracionHoras() + " h."
        );
    }

    /**
     * Entre salvar a un urgente chico y a un holgado grande, el objetivo elige al urgente.
     *
     * Es lo que hace que la operación dure. Dentro de una planificación todo lo asignado llega a
     * tiempo, porque el plazo es restricción dura; lo único que se decide es qué se deja afuera, y
     * hay que dejar afuera lo que todavía se puede servir después.
     *
     * Sin la ponderación por urgencia el objetivo elegía por tamaño, y un movimiento que sacaba
     * al urgente para meter al grande aparecía como una mejora.
     */
    private static void pruebaElObjetivoPrefiereSalvarAlUrgente() {
        Escenario escenario = Escenario.pequeno(AHORA);
        Evaluador evaluador = new Evaluador(escenario.distancias());
        Parametros parametros = Parametros.constructor(1, 0.30, 3L).construir();

        // Dos horas de plazo contra treinta y seis: uno no admite espera y el otro sí.
        Solucion dejaAlUrgente = new Solucion();
        dejaAlUrgente.agregarPedidoNoAsignado(
                Escenario.pedido("P-URGENTE", 43, 26, 1, AHORA, 2));

        Solucion dejaAlHolgado = new Solucion();
        dejaAlHolgado.agregarPedidoNoAsignado(
                Escenario.pedido("P-HOLGADO", 43, 26, 10, AHORA, 36));

        double costoDejarAlUrgente = evaluador.objetivo(
                dejaAlUrgente, escenario.estado(), parametros);
        double costoDejarAlHolgado = evaluador.objetivo(
                dejaAlHolgado, escenario.estado(), parametros);

        exigir(
                costoDejarAlUrgente > costoDejarAlHolgado,
                "Dejar afuera un producto urgente deberia salir mas caro que dejar diez holgados: "
                        + costoDejarAlUrgente + " contra " + costoDejarAlHolgado + "."
        );

        // Y la ponderacion tiene que ser continua: sin holgura pesa el maximo, con holgura de
        // sobra pesa uno, y en medio algo intermedio.
        // Registrado cinco horas atras con plazo de cuatro: su limite ya paso.
        double sinMargen = evaluador.urgencia(
                Escenario.pedido("P-A", 43, 26, 1, AHORA.minusHours(5), 4), AHORA);
        double aMedias = evaluador.urgencia(
                Escenario.pedido("P-B", 43, 26, 1, AHORA, 12), AHORA);
        double deSobra = evaluador.urgencia(
                Escenario.pedido("P-C", 43, 26, 1, AHORA, 36), AHORA);

        exigir(
                Math.abs(sinMargen - Evaluador.URGENCIA_MAXIMA) < 1e-9,
                "Sin margen la urgencia deberia ser la maxima y fue " + sinMargen + "."
        );
        exigir(
                Math.abs(deSobra - 1.0) < 1e-9,
                "Con holgura de sobra la urgencia deberia ser 1 y fue " + deSobra + "."
        );
        exigir(
                aMedias > 1.0 && aMedias < Evaluador.URGENCIA_MAXIMA,
                "Con holgura intermedia la urgencia deberia quedar en medio y fue " + aMedias + "."
        );
    }

    /**
     * Un pedido que no cabe entero en ninguna unidad pero si repartido entre dos.
     *
     * Es el caso que antes se rechazaba. El remiendo del simulador solo partia lo que superaba la
     * capacidad del vehiculo mas grande, de modo que un pedido de seis, que cabe de sobra en un
     * auto vacio, se quedaba sin atender si en ese momento no habia ningun auto con seis libres.
     *
     * Se monta con dos unidades interrumpidas a mitad de viaje, una con cuatro productos encima y
     * otra con dos: entre las dos suman los seis, pero ninguna los tiene. El plazo es corto a
     * proposito para que ir a un almacen a por un viaje nuevo no sea alternativa.
     *
     * Se comprueba con los tres planificadores porque el reparto es compartido.
     */
    private static void pruebaPartirCuandoLaCapacidadQuedoFragmentada() {
        for (String algoritmo : List.of("holgura", "grasp", "alns")) {
            exigirQueReparta(algoritmo);
        }
    }

    private static void exigirQueReparta(String algoritmo) {
        // Motos en la esquina mas lejana de los tres almacenes: el mas cercano esta a 34 km, o
        // sea casi tres horas de ida y vuelta a 25 km/h. Con plazo de una hora, ir a cargar un
        // viaje nuevo no es alternativa y la unica salida es repartir lo que ya llevan encima.
        Vehiculo conCuatro = Escenario
                .unidad(TipoVehiculo.MOTO, 1, new Ubicacion(69, 49)).conCarga(4);
        Vehiculo conDos = Escenario
                .unidad(TipoVehiculo.MOTO, 2, new Ubicacion(68, 48)).conCarga(2);

        // Seis cabe de sobra en una moto vacia -llevan ocho-, asi que el viejo remiendo del
        // simulador no lo habria partido: solo partia lo que superaba la capacidad mas grande.
        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(conCuatro, conDos))
                .conPedidos(List.of(Escenario.pedido("P-SEIS", 68, 49, 6, AHORA, 1)));

        Evaluador evaluador = new Evaluador(escenario.distancias());
        Parametros parametros = Parametros.constructor(4, 0.30, 5L)
                .iteracionesTabu(40)
                .construir();

        com.paqrap.planificador.Planificador planificador = switch (algoritmo) {
            case "grasp" -> new Grasp(evaluador);
            case "alns" -> new com.paqrap.planificador.alns.BusquedaAlns(evaluador);
            default -> new com.paqrap.planificador.InsercionPorHolgura(evaluador);
        };

        Solucion solucion = planificador.planificar(escenario.estado(), parametros);

        int repartido = 0;
        int partes = 0;
        for (Ruta viaje : solucion.getRutas()) {
            for (com.paqrap.modelo.Pedido entrega : viaje.getPedidos()) {
                exigir(
                        entrega.getIdOriginal().equals("P-SEIS"),
                        "Con " + algoritmo + " aparecio una entrega ajena: " + entrega.getId()
                );
                repartido += entrega.getCantidad();
                partes++;
            }
        }

        exigir(
                repartido == 6,
                "Con " + algoritmo + " deberia repartir los seis productos entre las dos unidades"
                        + " y repartio " + repartido + "."
        );
        exigir(
                partes >= 2,
                "Con " + algoritmo + " los seis tendrian que ir en al menos dos partes y fueron "
                        + partes + "."
        );
        exigir(
                solucion.getCantidadProductosNoAsignados() == 0,
                "Con " + algoritmo + " quedaron "
                        + solucion.getCantidadProductosNoAsignados() + " productos sin atender."
        );
        exigir(
                evaluador.asignacionFactible(solucion, escenario.estado()),
                "Con " + algoritmo + " el plan repartido no es factible."
        );
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
