package com.paqrap.demo;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoEntrega;
import com.paqrap.modelo.TipoIndisponibilidad;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.modelo.VentanaIndisponibilidad;
import com.paqrap.planificador.CalculadorAveria;
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.LectorMantenimiento;
import com.paqrap.planificador.MatrizDistancias;
import com.paqrap.planificador.Parametros;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dataset de demostracion para los parametros pedidos por la jp (22-sept-2026):
 * averias, mantenimiento preventivo (archivo oficial "mant.preventivo.m1.m2") y
 * la correccion del plazo de entrega (pregunta 11 de Preguntas y Respuestas: la
 * hora de atencion/acondicionamiento ya no cuenta contra el plazo).
 *
 * A diferencia de VerificacionGrasp/VerificacionMantenimiento (que son checks
 * silenciosos), este archivo imprime cada escenario paso a paso para poder
 * mostrarlo en la exposicion. Si algo no se cumple, el programa se detiene con
 * un AssertionError explicando que fallo.
 *
 * Compilar y correr (desde la raiz del proyecto):
 *   javac -encoding UTF-8 -d build $(find src/main/java -name "*.java")
 *   java -cp build com.paqrap.demo.DemoDatasetJP
 */
public final class DemoDatasetJP {

    private DemoDatasetJP() {
    }

    public static void main(String[] args) {
        System.out.println("=== DEMO: parametros pedidos por la jp (averias, mantenimiento, plazo) ===");
        System.out.println();

        demoAveriaExcluyeVehiculo();
        System.out.println();
        demoAveriaTipo3ExcluyeVariosDias();
        System.out.println();
        demoMantenimientoExcluyeVehiculo();
        System.out.println();
        demoPlazoNoIncluyeHoraAtencion();
        System.out.println();
        demoDatasetCombinado();

        System.out.println();
        System.out.println("=== TODO OK: los 5 escenarios se comportaron como se esperaba ===");
    }

    // ------------------------------------------------------------------
    // 1) Averia: un vehiculo con una averia vigente no debe recibir rutas.
    // ------------------------------------------------------------------
    private static void demoAveriaExcluyeVehiculo() {
        System.out.println("--- Escenario 1: averia vigente excluye al vehiculo ---");

        LocalDateTime ahora = LocalDateTime.of(2026, 9, 22, 8, 0);
        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion clienteU = new Ubicacion("CLIENTE-AVERIA");

        Almacen central = Almacen.central("ALM-C", centralU);

        // Averia tipo 1: la unidad no esta disponible por 2 horas exactas
        // (pregunta 3 de Preguntas y Respuestas). Ocurre 1h antes de "ahora",
        // asi que la ventana (07:00-09:00) cubre el instante de planificacion.
        VentanaIndisponibilidad averia = CalculadorAveria.calcular(
                TipoIndisponibilidad.AVERIA_TIPO_1, ahora.minusHours(1));

        Vehiculo motoConAveria = new Vehiculo(
                "TM01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, centralU, List.of(averia));

        Pedido pedido = new Pedido("P-AVERIA", "C-AVERIA", clienteU, 2, ahora, TipoEntrega.PRIORITARIA_8H);

        MatrizDistancias matriz = new MatrizDistancias();
        matriz.registrar(centralU, clienteU, 5);

        Grasp grasp = new Grasp(matriz);
        Solucion solucion = grasp.construir(
                ahora, List.of(pedido), List.of(central), List.of(motoConAveria),
                new Parametros(1, 0, 1L));

        System.out.println("Vehiculo TM01 (MOTO) con averia tipo 1 (dura 2h) de " + averia.inicio() + " a " + averia.fin());
        System.out.println("Pedido P-AVERIA, unico vehiculo disponible es TM01.");
        System.out.println("Rutas generadas: " + solucion.getRutas().size());
        System.out.println("Pedidos no asignados: " + solucion.getPedidosNoAsignados());

        exigir(solucion.getRutas().isEmpty(), "TM01 no debio recibir ninguna ruta estando en averia.");
        exigir(solucion.getPedidosNoAsignados().equals(List.of(pedido)),
                "P-AVERIA debio quedar sin asignar porque el unico vehiculo esta en averia.");

        System.out.println("RESULTADO: OK, el vehiculo en averia no fue usado.");
    }

    // ------------------------------------------------------------------
    // 1b) Averia Tipo 3 (mayor): al menos 2 dias, retorna en el turno 15:00-23:00.
    // ------------------------------------------------------------------
    private static void demoAveriaTipo3ExcluyeVariosDias() {
        System.out.println("--- Escenario 1b: averia tipo 3 excluye al vehiculo por varios dias ---");

        LocalDateTime ahora = LocalDateTime.of(2026, 9, 22, 8, 0);
        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion clienteU = new Ubicacion("CLIENTE-AVERIA-3");

        Almacen central = Almacen.central("ALM-C", centralU);

        // Averia tipo 3 ocurrida ayer a las 10:00: al menos 2 dias y retorna
        // exactamente en el turno 15:00-23:00 (pregunta 3 de Preguntas y
        // Respuestas + turnos del Enunciado de la Situacion Autentica).
        LocalDateTime momentoAveria = ahora.minusDays(1).withHour(10).withMinute(0);
        VentanaIndisponibilidad averia = CalculadorAveria.calcular(
                TipoIndisponibilidad.AVERIA_TIPO_3, momentoAveria);

        Vehiculo autoConAveria = new Vehiculo(
                "TA02", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, centralU, List.of(averia));

        Pedido pedido = new Pedido("P-AVERIA3", "C-AVERIA3", clienteU, 2, ahora, TipoEntrega.REGULAR_36H);

        MatrizDistancias matriz = new MatrizDistancias();
        matriz.registrar(centralU, clienteU, 5);

        Grasp grasp = new Grasp(matriz);
        Solucion solucion = grasp.construir(
                ahora, List.of(pedido), List.of(central), List.of(autoConAveria),
                new Parametros(1, 0, 1L));

        System.out.println("Averia tipo 3 ocurrida el " + momentoAveria + " (al menos 2 dias, retorna a las 15:00).");
        System.out.println("Vehiculo TA02 (AUTO) no disponible de " + averia.inicio() + " a " + averia.fin());
        System.out.println("Rutas generadas: " + solucion.getRutas().size());
        System.out.println("Pedidos no asignados: " + solucion.getPedidosNoAsignados());

        exigir(solucion.getRutas().isEmpty(), "TA02 no debio recibir ninguna ruta estando en averia tipo 3.");
        exigir(solucion.getPedidosNoAsignados().equals(List.of(pedido)),
                "P-AVERIA3 debio quedar sin asignar porque el unico vehiculo esta en averia tipo 3.");

        System.out.println("RESULTADO: OK, el vehiculo en averia tipo 3 no fue usado (sigue bloqueado 2+ dias despues).");
    }

    // ------------------------------------------------------------------
    // 2) Mantenimiento preventivo (archivo mant.preventivo.m1.m2, pregunta 19).
    // ------------------------------------------------------------------
    private static void demoMantenimientoExcluyeVehiculo() {
        System.out.println("--- Escenario 2: mantenimiento preventivo (archivo oficial) excluye al vehiculo ---");

        LocalDateTime ahora = LocalDateTime.of(2026, 9, 22, 8, 0);
        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion clienteU = new Ubicacion("CLIENTE-MANT");

        Almacen central = Almacen.central("ALM-C", centralU);

        // Simula una linea del archivo oficial: TA01 entra a mantenimiento hoy.
        Map<String, List<VentanaIndisponibilidad>> mantenimientos =
                LectorMantenimiento.leer(List.of("20260922:TA01"));

        Vehiculo autoEnMantenimiento = new Vehiculo(
                "TA01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, centralU,
                mantenimientos.get("TA01"));

        Pedido pedido = new Pedido("P-MANT", "C-MANT", clienteU, 3, ahora, TipoEntrega.REGULAR_36H);

        MatrizDistancias matriz = new MatrizDistancias();
        matriz.registrar(centralU, clienteU, 8);

        Grasp grasp = new Grasp(matriz);
        Solucion solucion = grasp.construir(
                ahora, List.of(pedido), List.of(central), List.of(autoEnMantenimiento),
                new Parametros(1, 0, 1L));

        VentanaIndisponibilidad ventana = mantenimientos.get("TA01").get(0);
        System.out.println("Archivo simulado: 20260922:TA01");
        System.out.println("Ventana de mantenimiento leida: " + ventana.inicio() + " a " + ventana.fin());
        System.out.println("Rutas generadas: " + solucion.getRutas().size());
        System.out.println("Pedidos no asignados: " + solucion.getPedidosNoAsignados());

        exigir(solucion.getRutas().isEmpty(), "TA01 no debio recibir ninguna ruta estando en mantenimiento.");
        exigir(solucion.getPedidosNoAsignados().equals(List.of(pedido)),
                "P-MANT debio quedar sin asignar porque el unico vehiculo esta en mantenimiento.");

        System.out.println("RESULTADO: OK, el vehiculo en mantenimiento no fue usado.");
    }

    // ------------------------------------------------------------------
    // 3) El plazo de entrega ya no incluye la hora de atencion (pregunta 11).
    // ------------------------------------------------------------------
    private static void demoPlazoNoIncluyeHoraAtencion() {
        System.out.println("--- Escenario 3: la hora de atencion (1h) ya no cuenta contra el plazo ---");

        LocalDateTime ahora = LocalDateTime.of(2026, 9, 22, 8, 0);
        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion clienteU = new Ubicacion("CLIENTE-LIMITE");

        Almacen central = Almacen.central("ALM-C", centralU);
        Vehiculo moto = new Vehiculo("MOTO-01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, centralU);

        // MOTO va a 25 km/h. 87.5 km de viaje = 3.5 h.
        // Plazo PRIORITARIA_4H = 4 h desde "ahora".
        // Con la regla antigua (incorrecta): 3.5h viaje + 1h atencion = 4.5h > 4h -> se rechazaba.
        // Con la regla oficial (pregunta 11): la hora de atencion no cuenta -> 3.5h <= 4h -> se acepta.
        Pedido pedido = new Pedido("P-LIMITE", "C-LIMITE", clienteU, 2, ahora, TipoEntrega.PRIORITARIA_4H);

        MatrizDistancias matriz = new MatrizDistancias();
        matriz.registrar(centralU, clienteU, 87.5);

        Grasp grasp = new Grasp(matriz);
        Solucion solucion = grasp.construir(
                ahora, List.of(pedido), List.of(central), List.of(moto),
                new Parametros(1, 0, 1L));

        System.out.println("Distancia: 87.5 km, velocidad MOTO: 25 km/h -> 3.5 h de viaje.");
        System.out.println("Plazo del pedido: 4 h (PRIORITARIA_4H).");
        System.out.println("Rutas generadas: " + solucion.getRutas().size());
        System.out.println("Pedidos no asignados: " + solucion.getPedidosNoAsignados());

        exigir(!solucion.getRutas().isEmpty(), "El pedido debio asignarse: el viaje (3.5h) no supera el plazo (4h).");
        exigir(solucion.getPedidosNoAsignados().isEmpty(),
                "No debio quedar ningun pedido sin asignar en este escenario.");

        System.out.println("RESULTADO: OK, el pedido se asigno porque la hora de atencion no cuenta contra el plazo.");
    }

    // ------------------------------------------------------------------
    // 4) Dataset combinado: varios pedidos y vehiculos, algunos bloqueados
    //    por averia/mantenimiento, para ver un caso mas realista completo.
    // ------------------------------------------------------------------
    private static void demoDatasetCombinado() {
        System.out.println("--- Escenario 4: dataset combinado (varios pedidos y vehiculos) ---");

        LocalDateTime ahora = LocalDateTime.of(2026, 9, 22, 8, 0);

        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion int1U = new Ubicacion("INT-1");
        Ubicacion c1 = new Ubicacion("CLIENTE-01");
        Ubicacion c2 = new Ubicacion("CLIENTE-02");
        Ubicacion c3 = new Ubicacion("CLIENTE-03");
        Ubicacion c4 = new Ubicacion("CLIENTE-04");
        Ubicacion c5 = new Ubicacion("CLIENTE-05");

        Almacen central = Almacen.central("ALM-C", centralU);
        Almacen int1 = Almacen.intermedio("ALM-I1", int1U, 20);

        // TA01: bloqueado por mantenimiento (archivo oficial).
        Map<String, List<VentanaIndisponibilidad>> mantenimientos =
                LectorMantenimiento.leer(List.of("20260922:TA01"));
        Vehiculo ta01 = new Vehiculo(
                "TA01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, centralU, mantenimientos.get("TA01"));

        // TM01: bloqueado por averia tipo 2 ocurrida a las 07:30 (30 min antes
        // de "ahora"). Esa hora cae en el turno 07:00-15:00, asi que el
        // turno SIGUIENTE es 15:00-23:00 y la unidad vuelve recien a las
        // 23:00 del mismo dia (pregunta 3 + turnos del Enunciado de la
        // Situacion Autentica: 07:00, 15:00, 23:00).
        VentanaIndisponibilidad averia = CalculadorAveria.calcular(
                TipoIndisponibilidad.AVERIA_TIPO_2, ahora.minusMinutes(30));
        Vehiculo tm01 = new Vehiculo(
                "TM01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, centralU, List.of(averia));

        // Vehiculos disponibles normalmente.
        Vehiculo bici01 = new Vehiculo("TB01", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, centralU);
        Vehiculo autoI1 = new Vehiculo("AUTO-I1-01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, int1U);
        Vehiculo motoI1 = new Vehiculo("MOTO-I1-01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, int1U);

        List<Vehiculo> vehiculos = List.of(ta01, tm01, bici01, autoI1, motoI1);
        List<Almacen> almacenes = List.of(central, int1);

        List<Pedido> pedidos = List.of(
                new Pedido("P-01", "C-01", c1, 3, ahora, TipoEntrega.PRIORITARIA_8H),
                new Pedido("P-02", "C-02", c2, 4, ahora, TipoEntrega.PRIORITARIA_12H),
                new Pedido("P-03", "C-03", c3, 2, ahora, TipoEntrega.REGULAR_36H),
                new Pedido("P-04", "C-04", c4, 5, ahora, TipoEntrega.PRIORITARIA_18H),
                new Pedido("P-05", "C-05", c5, 3, ahora, TipoEntrega.REGULAR_36H)
        );

        MatrizDistancias matriz = new MatrizDistancias();
        matriz.registrar(centralU, c1, 4);
        matriz.registrar(centralU, c2, 6);
        matriz.registrar(centralU, c3, 5);
        matriz.registrar(centralU, c4, 7);
        matriz.registrar(centralU, c5, 9);
        matriz.registrar(int1U, c1, 6);
        matriz.registrar(int1U, c2, 3);
        matriz.registrar(int1U, c3, 4);
        matriz.registrar(int1U, c4, 5);
        matriz.registrar(int1U, c5, 6);
        matriz.registrar(centralU, int1U, 8);
        matriz.registrar(c1, c2, 2);
        matriz.registrar(c2, c3, 3);
        matriz.registrar(c3, c4, 2);
        matriz.registrar(c4, c5, 3);
        matriz.registrar(c1, c3, 4);
        matriz.registrar(c1, c4, 5);
        matriz.registrar(c1, c5, 6);
        matriz.registrar(c2, c4, 4);
        matriz.registrar(c2, c5, 5);
        matriz.registrar(c3, c5, 3);

        Grasp grasp = new Grasp(matriz);
        Solucion solucion = grasp.construir(
                ahora, pedidos, almacenes, vehiculos, new Parametros(50, 0.30, 20260922L));

        System.out.println("Vehiculos bloqueados:");
        System.out.println("  TA01 (mantenimiento) de " + mantenimientos.get("TA01").get(0).inicio()
                + " a " + mantenimientos.get("TA01").get(0).fin());
        System.out.println("  TM01 (averia tipo 2) de " + averia.inicio() + " a " + averia.fin());
        System.out.println("Vehiculos disponibles: TB01, AUTO-I1-01, MOTO-I1-01.");
        System.out.println();

        for (Ruta ruta : solucion.getRutas()) {
            System.out.printf(
                    "%s | almacen=%s | vehiculo=%s | pedidos=%s | carga=%d/%d | km=%.2f | costo=S/ %.2f%n",
                    ruta.getId(), ruta.getAlmacen().getId(), ruta.getVehiculo().getId(),
                    ruta.getPedidos(), ruta.getCargaTotal(), ruta.getVehiculo().getCapacidad(),
                    ruta.getDistanciaTotalKm(), ruta.getCostoTotal());

            exigir(!ruta.getVehiculo().getId().equals("TA01") && !ruta.getVehiculo().getId().equals("TM01"),
                    "Ninguna ruta debio usar TA01 ni TM01 (estan bloqueados).");
        }

        System.out.println();
        System.out.println("Pedidos no asignados: " + solucion.getPedidosNoAsignados());
        System.out.printf("Costo total: S/ %.2f%n", solucion.getCostoTotal());
        System.out.println("RESULTADO: OK, ninguna ruta uso los vehiculos bloqueados.");
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
