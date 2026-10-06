package com.paqrap.planificador.grasp;

import com.paqrap.datos.DatosCaso;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Inventario;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.escenarios.Escenario;

class GraspTest {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 8, 8, 0);
    private static final int CAPACIDAD_AUTO =
            TipoVehiculo.AUTO.getEspecificacionDelCaso().capacidadPaquetes();

    /** Cuatro pedidos de 8 unidades: no caben en un solo viaje de un auto. */
    private static Escenario conUnSoloAuto(List<com.paqrap.modelo.Pedido> pedidos) {
        return Escenario.pequeno(AHORA)
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.AUTO, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(pedidos);
    }

    @Test
    void debeConstruirViajesConAlmacenUnidadYPedidos() {
        Escenario escenario = Escenario.pequeno(AHORA);
        Evaluador evaluador = new Evaluador(escenario.distancias());

        Solucion solucion = new Grasp(evaluador)
                .planificar(escenario.estado(), Parametros.constructor(30, 0.30, 1L).construir());

        assertFalse(solucion.getRutas().isEmpty(), "GRASP no generó ningún viaje.");
        assertTrue(evaluador.evaluarPlan(solucion, escenario.estado()).factible());

        for (Ruta viaje : solucion.getRutas()) {
            assertFalse(viaje.estaVacia());
            assertTrue(viaje.getCargaTotal() <= viaje.getVehiculo().getCapacidad());
        }
    }

    @Test
    void noDebeAsignarPedidoQueExcedeCapacidadDeTodaLaFlota() {
        Escenario escenario = Escenario.pequeno(AHORA).conPedidos(List.of(
                Escenario.pedido("P-GRANDE", 29, 16, CAPACIDAD_AUTO + 1, AHORA, 36)
        ));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(10, 0.30, 2L).construir());

        assertEquals(1, solucion.getCantidadPedidosNoAsignados());
        assertEquals(0, solucion.getCantidadRutas());
    }

    @Test
    void debeUsarCentralComoRespaldoSiIntermedioNoTieneStock() {
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

        assertEquals(1, solucion.getCantidadRutas());
        assertEquals(DatosCaso.ID_CENTRAL, solucion.getRuta(0).getAlmacen().getId());
    }

    @Test
    void debeRechazarViajeQueIncumplePlazo() {
        // 48 km sobre la retícula para una bicicleta de 12 km/h: cuatro horas de viaje.
        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(Escenario.pedido("P-LEJOS", 57, 32, 2, AHORA, 1)));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(10, 0.30, 4L).construir());

        assertEquals(1, solucion.getCantidadPedidosNoAsignados());
    }

    /**
     * El acondicionamiento en el cliente dura una hora pero queda fuera del plazo comprometido:
     * una llegada dentro de la fecha límite debe aceptarse aunque la entrega termine después.
     */
    @Test
    void debeAceptarEntregaQueLlegaEnPlazoAunqueElAcondicionamientoLoExceda() {
        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(
                        Escenario.unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_CENTRAL)
                ))
                .conPedidos(List.of(Escenario.pedido("P-JUSTO", 27, 20, 2, AHORA, 1)));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(10, 0.0, 5L).construir());

        assertEquals(0, solucion.getCantidadPedidosNoAsignados());

        MetricasRuta metricas = new Evaluador(escenario.distancias())
                .evaluarRuta(solucion.getRuta(0), AHORA);
        assertTrue(metricas.factible());
        assertTrue(metricas.duracionHoras() > 1.0,
                "La duración debe incluir la hora de acondicionamiento.");
    }

    @Test
    void debeRecargarElAlmacenIntermedioCada24Horas() {
        Almacen intermedio = Almacen.intermedio(
                DatosCaso.ID_ESTE, DatosCaso.UBICACION_ESTE, 10);
        Inventario inventario = new Inventario(List.of(intermedio), AHORA);

        assertEquals(10, inventario.disponible(intermedio, AHORA));

        inventario.consumir(intermedio, AHORA, 10);
        assertEquals(0, inventario.disponible(intermedio, AHORA));
        assertEquals(
                Almacen.CAPACIDAD_MAXIMA_INTERMEDIO,
                inventario.disponible(intermedio, AHORA.plusDays(1))
        );
    }

    /** Con una sola unidad, atender 32 unidades de producto exige encadenar viajes. */
    @Test
    void debeEncadenarViajesCuandoLaCargaNoEntraEnUnoSolo() {
        Escenario escenario = conUnSoloAuto(List.of(
                Escenario.pedido("P-001", 29, 16, 8, AHORA, 36),
                Escenario.pedido("P-002", 25, 12, 8, AHORA, 36),
                Escenario.pedido("P-003", 30, 18, 8, AHORA, 36),
                Escenario.pedido("P-004", 24, 17, 8, AHORA, 36)
        ));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(20, 0.30, 6L).construir());

        assertEquals(0, solucion.getCantidadPedidosNoAsignados());
        assertTrue(solucion.getCantidadRutas() >= 2,
                "32 unidades no caben en un viaje de " + CAPACIDAD_AUTO);
        for (Ruta viaje : solucion.getRutas()) {
            assertEquals("TA01", viaje.getVehiculo().getId());
            assertTrue(viaje.getCargaTotal() <= CAPACIDAD_AUTO);
        }
        assertTrue(new Evaluador(escenario.distancias())
                .evaluarPlan(solucion, escenario.estado()).factible());
    }

    /**
     * Si todas las entregas están junto al almacén Este, la unidad debe recargar ahí en lugar
     * de volver al central a 43 km.
     */
    @Test
    void debeRecargarEnElAlmacenMasCercanoALaZonaDeEntrega() {
        Escenario escenario = conUnSoloAuto(List.of(
                Escenario.pedido("P-001", 57, 29, 8, AHORA, 36),
                Escenario.pedido("P-002", 58, 26, 8, AHORA, 36),
                Escenario.pedido("P-003", 59, 28, 8, AHORA, 36),
                Escenario.pedido("P-004", 56, 25, 8, AHORA, 36)
        ));

        Solucion solucion = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), Parametros.constructor(20, 0.0, 7L).construir());

        assertEquals(0, solucion.getCantidadPedidosNoAsignados());
        assertTrue(
                solucion.getRutas().stream()
                        .anyMatch(viaje -> viaje.getAlmacen().getId().equals(DatosCaso.ID_ESTE)),
                "Ningún viaje cargó en el almacén Este."
        );
    }
}
