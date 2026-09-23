package com.paqrap.verificacion;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.GeneradorVentas;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Averia;
import com.paqrap.modelo.PlanAverias;
import com.paqrap.modelo.TipoAveria;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.planificador.ruteo.DistanciaManhattan;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.Entrega;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import com.paqrap.escenarios.Escenario;

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
        pruebaTiemposDeCadaTipoDeAveria();
        pruebaUnidadAveriadaNoEntrega();
        pruebaPartirUnPedido();
        pruebaEntregaParcialCuandoNoCabeEntero();
        System.out.println("OK - 9 verificaciones de simulación superadas.");
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
        Pedido alInicio = Escenario.pedido("P-INICIO", 29, 16, 3, INICIO, 36);
        Pedido masTarde = Escenario.pedido("P-TARDIO", 25, 12, 3, INICIO.plusHours(50), 36);

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
                Escenario.pedido("P-001", 27, 40, 2, INICIO, 4),
                Escenario.pedido("P-002", 27, 47, 2, INICIO, 4),
                Escenario.pedido("P-003", 8, 14, 2, INICIO, 4),
                Escenario.pedido("P-004", 50, 14, 2, INICIO, 4)
        );

        ResumenSimulacion resumen = simulador(true)
                .correr(INICIO, Duration.ofDays(1), imposibles,
                        List.of(Escenario.unidad(TipoVehiculo.BICICLETA, 1, DatosCaso.UBICACION_CENTRAL)));

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

    /**
     * Los tres tipos contra el enunciado, con los turnos del caso (07:00, 15:00 y 23:00).
     */
    private static void pruebaTiemposDeCadaTipoDeAveria() {
        // Tipo 1: dos horas y sigue desde donde estaba.
        LocalDateTime manana = LocalDateTime.of(2026, 9, 8, 9, 0);
        afirmar(
                TipoAveria.TIPO_1.reingreso(manana).equals(manana.plusHours(2)),
                "Una avería menor debería durar dos horas y duró hasta "
                        + TipoAveria.TIPO_1.reingreso(manana) + "."
        );
        afirmar(
                !TipoAveria.TIPO_1.regresaAlCentral(),
                "Una avería menor se resuelve en el sitio; la unidad no vuelve al central."
        );

        // Tipo 2: se avería en el turno 07-15, así que vuelve al final del turno 15-23.
        afirmar(
                TipoAveria.TIPO_2.reingreso(manana)
                        .equals(LocalDateTime.of(2026, 9, 8, 23, 0)),
                "Una avería intermedia de las 09:00 debería reingresar a las 23:00 y dio "
                        + TipoAveria.TIPO_2.reingreso(manana) + "."
        );
        afirmar(
                TipoAveria.TIPO_2.permanenciaEnElLugar().toHours() == 4
                        && TipoAveria.TIPO_3.permanenciaEnElLugar().toHours() == 4,
                "Las averías 2 y 3 permanecen cuatro horas en el lugar."
        );

        // Tipo 3: dos días de taller y reingreso en el turno de las 15:00.
        afirmar(
                TipoAveria.TIPO_3.reingreso(manana)
                        .equals(LocalDateTime.of(2026, 9, 10, 15, 0)),
                "Una avería mayor de las 09:00 debería reingresar el día 10 a las 15:00 y dio "
                        + TipoAveria.TIPO_3.reingreso(manana) + "."
        );

        // Si al cumplirse los dos días ya pasaron las 15:00, se corre al día siguiente.
        LocalDateTime tarde = LocalDateTime.of(2026, 9, 8, 18, 0);
        afirmar(
                TipoAveria.TIPO_3.reingreso(tarde)
                        .equals(LocalDateTime.of(2026, 9, 11, 15, 0)),
                "Una avería mayor de las 18:00 debería esperar al turno siguiente y dio "
                        + TipoAveria.TIPO_3.reingreso(tarde) + "."
        );
    }

    /**
     * Una unidad averiada no puede entregar mientras está fuera de servicio, y sus pedidos no se
     * pierden: los reparte el resto de la flota en la planificación siguiente.
     */
    private static void pruebaUnidadAveriadaNoEntrega() {
        List<Pedido> ventas = new GeneradorVentas(23L)
                .demandaConstante(PERIODO, 1, 1, 30);

        String averiada = DatosCaso.flota().get(0).getId();
        Averia averia = new Averia(averiada, INICIO.plusMinutes(30), TipoAveria.TIPO_3);

        ResumenSimulacion resumen = simulador(false)
                .conAverias(new PlanAverias(List.of(averia)))
                .correr(INICIO, Duration.ofDays(1), ventas, DatosCaso.flota());

        for (Entrega entrega : resumen.entregas()) {
            afirmar(
                    !entrega.unidad().getId().equals(averiada)
                            || !entrega.llegada().isAfter(averia.reingreso()),
                    "La unidad " + averiada + " entregó a las " + entrega.llegada()
                            + " estando averiada hasta " + averia.reingreso() + "."
            );
        }

        afirmar(
                resumen.averiasOcurridas().size() == 1,
                "La avería programada no quedó registrada en el resumen."
        );
        afirmar(
                !resumen.huboColapso(),
                "Perder una unidad de treinta y siete no debería colapsar la operación."
        );
    }

    /** Partir conserva cliente, destino, plazo y cantidad total, y da ids deterministas. */
    private static void pruebaPartirUnPedido() {
        Pedido entero = new Pedido("P-007", "c1", new Ubicacion(30, 20), 10, INICIO, 8);
        List<Pedido> partes = entero.partirEn(4);

        afirmar(partes.size() == 2, "Partir debería dar exactamente dos entregas.");
        afirmar(
                partes.get(0).getCantidad() + partes.get(1).getCantidad() == entero.getCantidad(),
                "Las partes no suman la cantidad del pedido original."
        );
        for (Pedido parte : partes) {
            afirmar(
                    parte.getIdOriginal().equals("P-007") && parte.esParteDeOtro(),
                    "Cada parte debería recordar de qué pedido salió."
            );
            afirmar(
                    parte.getDestino().equals(entero.getDestino())
                            && parte.getFechaLimite().equals(entero.getFechaLimite()),
                    "Partir no debería cambiar el destino ni la fecha límite."
            );
        }
        afirmar(
                partes.get(0).getId().equals("P-007a") && partes.get(1).getId().equals("P-007b"),
                "Los ids de las partes deberían ser deterministas y dieron "
                        + partes.get(0).getId() + " y " + partes.get(1).getId() + "."
        );
    }

    /**
     * Un pedido que no cabe entero en ninguna unidad se parte y se entrega en varias visitas.
     * Con flota de solo bicicletas —capacidad 4— un pedido de diez necesita tres.
     */
    private static void pruebaEntregaParcialCuandoNoCabeEntero() {
        Pedido grande = new Pedido("P-GRANDE", "c99", new Ubicacion(28, 16), 10, INICIO, 24);

        ResumenSimulacion resumen = simulador(false)
                .correr(INICIO, Duration.ofDays(1), List.of(grande),
                        DatosCaso.flotaReducida(0, 0, 4));

        int entregado = 0;
        for (Entrega entrega : resumen.entregas()) {
            afirmar(
                    entrega.pedido().getIdOriginal().equals("P-GRANDE"),
                    "Apareció una entrega de un pedido que no se pidió."
            );
            entregado += entrega.pedido().getCantidad();
        }

        afirmar(
                entregado == 10,
                "Las entregas parciales deberían sumar los diez productos y sumaron "
                        + entregado + "."
        );
        afirmar(
                resumen.entregas().size() >= 3,
                "Diez productos en bicicletas de cuatro necesitan al menos tres visitas y hubo "
                        + resumen.entregas().size() + "."
        );
        afirmar(
                resumen.pedidosOriginalesEntregados() == 1,
                "La contabilidad debería ver un solo pedido del cliente y vio "
                        + resumen.pedidosOriginalesEntregados() + "."
        );
        afirmar(resumen.pedidosPartidos() == 1, "El pedido partido no quedó registrado.");
    }

    private static void afirmar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
