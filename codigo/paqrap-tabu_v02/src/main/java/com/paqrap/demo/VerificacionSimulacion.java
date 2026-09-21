package com.paqrap.demo;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.GeneradorVentas;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.planificador.DistanciaManhattan;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Verificación ejecutable sin JUnit de la operación simulada.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.VerificacionSimulacion
 */
public final class VerificacionSimulacion {
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final LocalDateTime INICIO = PERIODO.atDay(1).atStartOfDay();

    private VerificacionSimulacion() {
    }

    public static void main(String[] args) {
        pruebaDemandaSostenible();
        pruebaPedidoQueLlegaDuranteLaSimulacion();
        pruebaColapsoConFlotaInsuficiente();
        pruebaSinPedidosDuplicados();
        pruebaFormatoDelArchivoDeVentas();
        System.out.println("OK - 5 verificaciones de simulación superadas.");
    }

    /** Con una demanda que la flota puede atender, no vence ningún pedido. */
    private static void pruebaDemandaSostenible() {
        List<Pedido> ventas = new GeneradorVentas(11L)
                .demandaConstante(PERIODO, 1, 2, 40);

        ResumenSimulacion resumen = simulador(false)
                .correr(INICIO, Duration.ofDays(3), ventas, DatosCaso.flota());

        afirmar(!resumen.huboColapso(), "Venció un pedido con una demanda que la flota soporta.");
        afirmar(
                resumen.pedidosEntregados() == resumen.pedidosRecibidos(),
                "Quedaron pedidos sin entregar: " + resumen.pedidosPendientes() + " pendientes."
        );
        afirmar(
                resumen.entregasEnPlazo() == resumen.pedidosEntregados(),
                "Hay entregas fuera de plazo, que el planificador no debería aceptar."
        );
    }

    /** Un pedido que llega con la simulación en marcha se incorpora y se atiende. */
    private static void pruebaPedidoQueLlegaDuranteLaSimulacion() {
        Pedido alInicio = EscenarioDemo.pedido("P-INICIO", 29, 16, 3, INICIO, 36);
        Pedido masTarde = EscenarioDemo.pedido("P-TARDIO", 25, 12, 3, INICIO.plusHours(50), 36);

        ResumenSimulacion resumen = simulador(false)
                .correr(INICIO, Duration.ofDays(4), List.of(alInicio, masTarde),
                        DatosCaso.flotaReducida(1, 0, 0));

        afirmar(resumen.pedidosRecibidos() == 2, "No se incorporó el pedido que llegó después.");
        afirmar(
                resumen.pedidosEntregados() == 2,
                "No se entregaron ambos pedidos: " + resumen.pedidosEntregados() + " de 2."
        );
        afirmar(!resumen.huboColapso(), "Se declaró colapso sin motivo.");
    }

    /**
     * Una sola bicicleta contra cuatro pedidos priorizados en extremos opuestos de la ciudad:
     * por lejos que optimice, no llega, y los plazos vencen.
     */
    private static void pruebaColapsoConFlotaInsuficiente() {
        List<Pedido> imposibles = List.of(
                EscenarioDemo.pedido("P-001", 27, 40, 2, INICIO, 4),
                EscenarioDemo.pedido("P-002", 27, 47, 2, INICIO, 4),
                EscenarioDemo.pedido("P-003", 8, 14, 2, INICIO, 4),
                EscenarioDemo.pedido("P-004", 50, 14, 2, INICIO, 4)
        );

        ResumenSimulacion resumen = simulador(true)
                .correr(INICIO, Duration.ofDays(1), imposibles,
                        List.of(EscenarioDemo.unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_CENTRAL)));

        afirmar(resumen.huboColapso(), "No se detectó el colapso pese a que los plazos vencieron.");
        afirmar(
                resumen.pedidoQueColapso() != null,
                "El resumen no identifica el pedido que provocó el colapso."
        );
        afirmar(
                resumen.instanteDelColapso().equals(resumen.pedidoQueColapso().getFechaLimite()),
                "El instante del colapso no coincide con la fecha límite del pedido vencido."
        );
        afirmar(
                resumen.entregasEnPlazo() == resumen.pedidosEntregados(),
                "Lo que sí se entregó debería haber salido dentro de plazo."
        );
    }

    /** Ningún pedido puede entregarse dos veces ni perderse entre replanificaciones. */
    private static void pruebaSinPedidosDuplicados() {
        List<Pedido> ventas = new GeneradorVentas(23L)
                .demandaConstante(PERIODO, 1, 2, 30);

        ResumenSimulacion resumen = simulador(false)
                .correr(INICIO, Duration.ofDays(3), ventas, DatosCaso.flota());

        afirmar(
                resumen.pedidosEntregados() + resumen.pedidosPendientes()
                        + resumen.vencidos().size() == resumen.pedidosRecibidos(),
                "Los pedidos entregados, pendientes y vencidos no suman los recibidos."
        );
    }

    /** Formato del caso: ##d##h##m:posX,posY,cIdCliente,qq,hl */
    private static void pruebaFormatoDelArchivoDeVentas() {
        List<Pedido> pedidos = LectorVentas.leerLineas(
                List.of("# comentario", "", "11d13h31m:45,43,c9167,12,36"), PERIODO);

        afirmar(pedidos.size() == 1, "El lector no devolvió el registro del ejemplo.");
        Pedido pedido = pedidos.get(0);
        afirmar(
                pedido.getFechaRegistro().equals(PERIODO.atDay(11).atTime(13, 31)),
                "La fecha de llegada no se interpretó como 11 de setiembre a las 13:31."
        );
        afirmar(pedido.getDestino().x() == 45 && pedido.getDestino().y() == 43,
                "El destino no se leyó como (45,43).");
        afirmar(pedido.getClienteId().equals("c9167"), "El cliente no se leyó.");
        afirmar(pedido.getCantidad() == 12, "La cantidad no se leyó.");
        afirmar(pedido.getHorasPlazo() == 36, "El plazo no se leyó.");

        afirmar(
                LectorVentas.aRegistro(pedido).equals("11d13h31m:45,43,c9167,12,36"),
                "Escribir el pedido no reproduce el registro original: "
                        + LectorVentas.aRegistro(pedido)
        );
    }

    private static Simulador simulador(boolean detenerAlColapsar) {
        Parametros parametros = Parametros.constructor(5, 0.30, 7L)
                .iteracionesTabu(60)
                .tamanoMuestraVecindario(30)
                .iteracionesSinMejora(20)
                .limiteMilisegundos(200L)
                .construir();

        return new Simulador(
                new BusquedaTabu(new DistanciaManhattan()),
                new DistanciaManhattan(),
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(30),
                detenerAlColapsar
        );
    }

    private static void afirmar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
