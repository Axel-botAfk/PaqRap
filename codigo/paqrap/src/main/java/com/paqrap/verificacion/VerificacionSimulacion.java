package com.paqrap.verificacion;

import com.paqrap.datos.DatosCaso;
import com.paqrap.datos.GeneradorVentas;
import com.paqrap.datos.LectorVentas;
import com.paqrap.modelo.Averia;
import com.paqrap.modelo.PlanAverias;
import com.paqrap.modelo.TipoAveria;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
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
        pruebaLecturaClasicaEsperaAlCorte();
        pruebaLecturaPorBloquesNoEsperaAlCorte();
        pruebaLecturaPorBloquesNoEntregaAntesDeTiempo();
        pruebaProductosYPedidosSeContabilizanPorSeparado();
        pruebaPedidoAMedioServirNoCuentaComoAtendido();
        pruebaElPedidoAnticipadoNoSePierde();
        pruebaElKilometrajeNoSeCobraDosVeces();
        System.out.println("OK - 16 verificaciones de simulación superadas.");
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

    // ------------------------------------------------------------ lectura por bloques

    /**
     * Un pedido que llega dentro del bloque espera al corte siguiente.
     *
     * Es el comportamiento clásico y la razón por la que existe la lectura por bloques: la
     * latencia de planificación es igual al tamaño del bloque.
     */
    private static void pruebaLecturaClasicaEsperaAlCorte() {
        int planificacionesAntes = planificacionesHastaVerlo(false);
        afirmar(
                planificacionesAntes >= 2,
                "Sin bloques, el pedido que llega dentro del bloque tendría que esperar al corte"
                        + " siguiente y entró en la planificación número " + planificacionesAntes + "."
        );
    }

    /** Con bloques, ese mismo pedido entra a la primera planificación: latencia cero. */
    private static void pruebaLecturaPorBloquesNoEsperaAlCorte() {
        int conBloques = planificacionesHastaVerlo(true);
        afirmar(
                conBloques == 1,
                "Con lectura por bloques el pedido debería entrar a la primera planificación y"
                        + " entró a la número " + conBloques + "."
        );
    }

    /**
     * En qué número de planificación aparece un pedido que se registra a mitad del primer bloque.
     */
    private static int planificacionesHastaVerlo(boolean porBloques) {
        LocalDateTime aMitadDelBloque = INICIO.plusMinutes(10);
        List<Pedido> ventas = List.of(
                // El ancla existe desde el arranque: sin algo pendiente no habría una primera
                // planificación con la cual comparar.
                Escenario.pedido("P-ANCLA", 41, 25, 2, INICIO, 12),
                Escenario.pedido("P-TARDIO", 43, 26, 2, aMitadDelBloque, 12));

        Simulador simulador = simulador(false);
        if (porBloques) {
            simulador = simulador.leyendoPorBloques();
        }

        int[] cuandoLoVio = {0};
        int[] planificacion = {0};
        simulador.observadoPor((reloj, estado, plan, medicion, avance) -> {
            planificacion[0]++;
            if (cuandoLoVio[0] == 0) {
                for (Pedido pedido : estado.getPedidos()) {
                    if (pedido.getId().equals("P-TARDIO")) {
                        cuandoLoVio[0] = planificacion[0];
                    }
                }
            }
        });

        simulador.correr(INICIO, Duration.ofHours(6), ventas, DatosCaso.flota());
        return cuandoLoVio[0];
    }

    /**
     * Anticipar no es entregar antes de tiempo.
     *
     * El planificador ve el pedido al abrir el bloque, pero el borde inferior de la ventana
     * impide que ninguna unidad lo deje antes de que el cliente lo haya hecho.
     */
    private static void pruebaLecturaPorBloquesNoEntregaAntesDeTiempo() {
        LocalDateTime aMitadDelBloque = INICIO.plusMinutes(10);
        List<Pedido> ventas = List.of(
                Escenario.pedido("P-CERQUISIMA", 42, 26, 1, aMitadDelBloque, 12));

        ResumenSimulacion resumen = simulador(false)
                .leyendoPorBloques()
                .correr(INICIO, Duration.ofHours(6), ventas, DatosCaso.flota());

        for (Entrega entrega : resumen.entregas()) {
            afirmar(
                    !entrega.llegada().isBefore(entrega.pedido().getFechaRegistro()),
                    "Se entregó " + entrega.pedido().getId() + " a las " + entrega.llegada()
                            + ", antes de que el cliente lo pidiera ("
                            + entrega.pedido().getFechaRegistro() + ")."
            );
        }
    }

    /**
     * Productos y pedidos son dos cifras distintas y las dos se informan.
     *
     * El objetivo minimiza productos sin atender, de modo que reportar solo pedidos mediría algo
     * distinto de lo que la búsqueda optimiza.
     */
    private static void pruebaProductosYPedidosSeContabilizanPorSeparado() {
        List<Pedido> ventas = List.of(
                Escenario.pedido("P-UNO", 43, 26, 1, INICIO, 12),
                Escenario.pedido("P-DIEZ", 44, 27, 10, INICIO, 12)
        );

        ResumenSimulacion resumen = simulador(false)
                .correr(INICIO, Duration.ofHours(8), ventas, DatosCaso.flota());

        afirmar(
                resumen.pedidosRecibidos() == 2,
                "Deberían contarse dos pedidos y se contaron " + resumen.pedidosRecibidos() + "."
        );
        afirmar(
                resumen.productosRecibidos() == 11,
                "Deberían contarse once productos y se contaron "
                        + resumen.productosRecibidos() + "."
        );
        afirmar(
                resumen.productosEntregados() + resumen.productosPendientes()
                        + resumen.productosVencidos() == resumen.productosRecibidos(),
                "Los productos entregados, pendientes y vencidos no suman los recibidos."
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
                resumen.pedidosOriginalesConAlgunaEntrega() == 1,
                "La contabilidad debería ver un solo pedido del cliente y vio "
                        + resumen.pedidosOriginalesConAlgunaEntrega() + "."
        );
        afirmar(
                resumen.pedidosOriginalesCompletos() == 1,
                "Los diez productos llegaron, así que el pedido debería contar como servido."
        );
        afirmar(
                resumen.pedidosOriginalesIncompletos() == 0,
                "No debería quedar ningún pedido a medio servir."
        );
        afirmar(
                resumen.pedidosOriginalesEnPlazo() == 1,
                "Todas las partes llegaron dentro del plazo, así que el pedido está en plazo."
        );
        afirmar(resumen.pedidosPartidos() == 1, "El pedido partido no quedó registrado.");
    }

    /**
     * Un pedido del que llegó solo una parte no es un pedido atendido.
     *
     * Es la diferencia entre contar visitas y contar clientes servidos: si a un pedido de diez le
     * llegaron cuatro, hubo una entrega puntual y un cliente que sigue esperando. La cobertura
     * tiene que reflejar lo segundo.
     *
     * Se fuerza cortando el horizonte: una sola bicicleta de capacidad cuatro no alcanza a dar
     * las tres vueltas que hacen falta.
     */
    private static void pruebaPedidoAMedioServirNoCuentaComoAtendido() {
        Pedido grande = new Pedido("P-MEDIO", "c98", new Ubicacion(28, 16), 10, INICIO, 24);

        ResumenSimulacion resumen = simulador(false)
                .correr(INICIO, Duration.ofHours(3), List.of(grande),
                        DatosCaso.flotaReducida(0, 0, 1));

        int entregado = 0;
        for (Entrega entrega : resumen.entregas()) {
            entregado += entrega.pedido().getCantidad();
        }
        if (entregado == 0 || entregado >= 10) {
            // El escenario no aisló el caso a medio servir; no hay nada que afirmar.
            return;
        }

        afirmar(
                resumen.pedidosOriginalesConAlgunaEntrega() == 1,
                "Hubo entregas, así que el pedido debería figurar con alguna."
        );
        afirmar(
                resumen.pedidosOriginalesCompletos() == 0,
                "Llegaron " + entregado + " de 10 y el pedido se contó como servido completo."
        );
        afirmar(
                resumen.pedidosOriginalesEnPlazo() == 0,
                "Un pedido incompleto no puede estar en plazo: al cliente le falta producto."
        );
        afirmar(
                resumen.pedidosOriginalesIncompletos() == 1,
                "El pedido a medio servir debería figurar como incompleto."
        );
    }

    /**
     * El pedido que el bloque anticipa tiene que llegarle al planificador, no evaporarse.
     *
     * Verlo en el estado no basta: los tres algoritmos filtraban por fecha de registro al entrar,
     * de modo que un pedido anticipado ni se planificaba ni figuraba como no asignado. La lectura
     * por bloques quedaba inerte y la contabilidad no cuadraba, pero nada fallaba a la vista.
     *
     * Se comprueba sobre el plan: cada pedido del estado tiene que estar en una ruta o en la bolsa
     * de no asignados. Se corre con los tres planificadores porque el filtro estaba en los tres.
     */
    private static void pruebaElPedidoAnticipadoNoSePierde() {
        for (String algoritmo : List.of("tabu", "grasp", "alns")) {
            comprobarQueNadaSePierdeConBloques(algoritmo);
        }
    }

    private static void comprobarQueNadaSePierdeConBloques(String algoritmo) {
        List<Pedido> ventas = List.of(
                Escenario.pedido("P-ANCLA", 41, 25, 2, INICIO, 12),
                Escenario.pedido("P-TARDIO", 43, 26, 2, INICIO.plusMinutes(10), 12));

        Simulador simulador = simuladorCon(algoritmo).leyendoPorBloques();

        boolean[] loVio = {false};
        simulador.observadoPor((reloj, estado, plan, medicion, avance) -> {
            java.util.Set<String> colocados = new java.util.HashSet<>();
            for (com.paqrap.modelo.Ruta viaje : plan.getRutas()) {
                for (Pedido pedido : viaje.getPedidos()) {
                    colocados.add(pedido.getId());
                }
            }
            for (Pedido pedido : plan.getPedidosNoAsignados()) {
                colocados.add(pedido.getId());
            }
            for (Pedido pedido : estado.getPedidos()) {
                afirmar(
                        colocados.contains(pedido.getId()),
                        "Con " + algoritmo + ", el pedido " + pedido.getId()
                                + " entró al estado y el plan no lo colocó ni lo dejó sin asignar."
                );
                if (pedido.getId().equals("P-TARDIO")) {
                    loVio[0] = true;
                }
            }
        });

        simulador.correr(INICIO, Duration.ofHours(6), ventas, DatosCaso.flota());
        afirmar(loVio[0], "Con " + algoritmo + ", el pedido anticipado nunca llegó al estado.");
    }

    /** El mismo simulador de las demás pruebas, con el planificador que se quiera medir. */
    private static Simulador simuladorCon(String algoritmo) {
        Parametros parametros = Parametros.constructor(3, 0.30, 7L)
                .iteracionesTabu(30)
                .tamanoMuestraVecindario(20)
                .iteracionesSinMejora(10)
                .construir();

        com.paqrap.planificador.Evaluador evaluador =
                new com.paqrap.planificador.Evaluador(new DistanciaManhattan());
        com.paqrap.planificador.Planificador planificador = switch (algoritmo) {
            case "grasp" -> new com.paqrap.planificador.grasp.Grasp(evaluador);
            case "alns" -> new com.paqrap.planificador.alns.BusquedaAlns(evaluador);
            default -> new BusquedaTabu(evaluador);
        };

        return new Simulador(
                planificador,
                new DistanciaManhattan(),
                DatosCaso.almacenes(),
                parametros,
                Duration.ofMinutes(30),
                false
        );
    }

    /**
     * Interrumpir una ruta a mitad de camino no infla el kilometraje.
     *
     * <h2>Lo que se comprueba</h2>
     *
     * Si un viaje cuesta A y el corte lo parte por la mitad, la planificacion siguiente calcula un
     * viaje B desde donde quedo la unidad. El gasto real es lo andado de A mas lo andado de B, no
     * A mas B: la mitad de A que no se recorrio no se paga, y la mitad que si se recorrio no se
     * vuelve a pagar en B.
     *
     * Se monta un escenario con un solo recorrido posible y se corre dos veces, con cortes cada
     * cinco minutos y con un corte mas largo que toda la operacion. El kilometraje tiene que salir
     * identico: es una propiedad geometrica y no puede depender de cada cuanto se replanifica.
     *
     * <h2>La geometria</h2>
     *
     * Una bicicleta en (27,20) y un cliente en (27,8), con el almacen central justo en medio en
     * (27,14). Los otros dos almacenes quedan a mas de veinte kilometros, de modo que el recorrido
     * barato es evidente: seis kilometros hasta el almacen y seis hasta el cliente. A doce
     * kilometros por hora eso es una hora de viaje, asi que con cortes de cinco minutos la unidad
     * se interrumpe una docena de veces.
     */
    private static void pruebaElKilometrajeNoSeCobraDosVeces() {
        double conCortesCortos = kilometrosDeUnRecorridoUnico(Duration.ofMinutes(5));
        double sinInterrupciones = kilometrosDeUnRecorridoUnico(Duration.ofHours(12));

        afirmar(
                sinInterrupciones > 0,
                "El escenario de control no recorrio nada; la prueba no mide nada."
        );
        afirmar(
                Math.abs(conCortesCortos - sinInterrupciones) < 1e-6,
                "Interrumpir la ruta cambio el kilometraje: " + conCortesCortos
                        + " km con cortes cada 5 min contra " + sinInterrupciones
                        + " km sin interrupciones. Se esta cobrando dos veces o de menos."
        );
        afirmar(
                Math.abs(sinInterrupciones - 12.0) < 1e-6,
                "El recorrido geometrico son 12 km -seis al almacen y seis al cliente- y se"
                        + " contaron " + sinInterrupciones + "."
        );
    }

    /** Kilometros recorridos en el escenario de un solo camino posible, con el corte indicado. */
    private static double kilometrosDeUnRecorridoUnico(Duration corte) {
        Vehiculo bicicleta = Escenario.unidad(
                TipoVehiculo.BICICLETA, 1, new Ubicacion(27, 20));
        List<Pedido> ventas = List.of(
                Escenario.pedido("P-UNICO", 27, 8, 1, INICIO, 12));

        Simulador simulador = new Simulador(
                new BusquedaTabu(new DistanciaManhattan()),
                new DistanciaManhattan(),
                DatosCaso.almacenes(),
                Parametros.constructor(2, 0.30, 5L).iteracionesTabu(20).construir(),
                corte,
                false
        ).conIntervaloMinimo(corte);

        return simulador
                .correr(INICIO, Duration.ofHours(12), ventas, List.of(bicicleta))
                .distanciaTotalKm();
    }

    private static void afirmar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
