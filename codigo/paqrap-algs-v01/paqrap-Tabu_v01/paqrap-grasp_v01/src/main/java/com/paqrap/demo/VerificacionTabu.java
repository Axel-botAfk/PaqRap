package com.paqrap.demo;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoEntrega;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.MatrizDistancias;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.AplicadorMovimiento;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.planificador.tabu.Movimiento;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verificación ejecutable sin JUnit de la búsqueda tabú, en la misma línea que
 * {@link VerificacionGrasp}.
 */
public final class VerificacionTabu {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 8, 8, 0);

    private VerificacionTabu() {
    }

    public static void main(String[] args) {
        pruebaNoEmpeoraAGrasp();
        pruebaPlanResultanteFactible();
        pruebaCoberturaDePedidos();
        pruebaReproducibilidadPorSemilla();
        pruebaAsignacionDePedidoPendiente();
        pruebaMovimientosReversibles();
        System.out.println("OK - 6 verificaciones de búsqueda tabú superadas.");
    }

    /** La fase de mejora parte de GRASP, de modo que nunca debe devolver un plan peor. */
    private static void pruebaNoEmpeoraAGrasp() {
        Escenario escenario = Escenario.completo();
        Parametros parametros = parametros(20260908L);

        Solucion grasp = new Grasp(escenario.distancias).planificar(escenario.estado, parametros);
        Solucion tabu = new BusquedaTabu(escenario.distancias).planificar(escenario.estado, parametros);

        afirmar(
                tabu.getCantidadPedidosNoAsignados() <= grasp.getCantidadPedidosNoAsignados(),
                "La búsqueda tabú dejó más pedidos sin asignar que GRASP."
        );
        afirmar(
                tabu.getCostoTotal() <= grasp.getCostoTotal() + 1e-6,
                "La búsqueda tabú devolvió un costo mayor que GRASP."
        );
    }

    /** Capacidad, plazos, unidad en su almacén de salida, unidad única por ruta y stock. */
    private static void pruebaPlanResultanteFactible() {
        Escenario escenario = Escenario.completo();
        Evaluador evaluador = new Evaluador(escenario.distancias);
        Solucion tabu = new BusquedaTabu(evaluador).planificar(escenario.estado, parametros(7L));

        afirmar(
                evaluador.asignacionFactible(tabu, escenario.estado),
                "El plan viola la unicidad de vehículo o el stock de algún almacén."
        );

        for (Ruta ruta : tabu.getRutas()) {
            afirmar(!ruta.estaVacia(), "El plan conserva rutas sin entregas.");
            afirmar(
                    ruta.getCargaTotal() <= ruta.getVehiculo().getCapacidad(),
                    "La ruta " + ruta.getId() + " excede la capacidad del vehículo."
            );
            afirmar(
                    ruta.getVehiculo().getUbicacion().equals(ruta.getAlmacen().getUbicacion()),
                    "La ruta " + ruta.getId() + " sale de un almacén donde su unidad no se encuentra."
            );

            MetricasRuta metricas = evaluador.evaluarRuta(ruta, escenario.estado.getReloj());
            afirmar(metricas.factible(), "La ruta " + ruta.getId() + " incumple algún plazo.");
        }
    }

    /** Ningún pedido puede perderse ni duplicarse durante los movimientos. */
    private static void pruebaCoberturaDePedidos() {
        Escenario escenario = Escenario.completo();
        Solucion tabu = new BusquedaTabu(escenario.distancias)
                .planificar(escenario.estado, parametros(99L));

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
                unicos.size() == escenario.pedidos.size(),
                "El plan no cubre todos los pedidos de la instancia."
        );
    }

    /** Misma semilla, misma instancia, mismo resultado. */
    private static void pruebaReproducibilidadPorSemilla() {
        Escenario escenario = Escenario.completo();
        Parametros parametros = parametros(1234L);

        Solucion primera = new BusquedaTabu(escenario.distancias)
                .planificar(escenario.estado, parametros);
        Solucion segunda = new BusquedaTabu(escenario.distancias)
                .planificar(escenario.estado, parametros);

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
        Escenario escenario = Escenario.completo();

        Pedido asignado = escenario.pedido("P-001");
        Pedido pendiente = escenario.pedido("P-003");

        Solucion inicial = new Solucion();
        Ruta ruta = new Ruta("R-1", escenario.almacen("ALM-I1"), escenario.vehiculo("MOTO-01"));
        ruta.insertarPedido(0, asignado);
        inicial.agregarRuta(ruta);
        inicial.agregarPedidoNoAsignado(pendiente);

        Solucion mejorada = new BusquedaTabu(escenario.distancias)
                .mejorar(inicial, escenario.estado, parametros(5L));

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
        Escenario escenario = Escenario.completo();

        List<Movimiento> movimientos = List.of(
                Movimiento.trasladar(0, 0, 1, 1, escenario.pedido("P-001")),
                Movimiento.trasladar(0, 1, 0, 0, escenario.pedido("P-002")),
                Movimiento.trasladarARutaNueva(0, 0, escenario.pedido("P-001"),
                        escenario.vehiculo("BICI-01"), escenario.almacen("ALM-I1")),
                Movimiento.intercambiar(0, 0, 1, 0, escenario.pedido("P-001")),
                Movimiento.invertir(0, 0, 1),
                Movimiento.cambiarVehiculo(0, escenario.vehiculo("BICI-01")),
                Movimiento.cambiarAlmacen(0, escenario.almacen("ALM-I2"), escenario.vehiculo("MOTO-02")),
                Movimiento.asignarPendiente(escenario.pedido("P-005"), 1, 1),
                Movimiento.asignarPendienteEnRutaNueva(escenario.pedido("P-005"),
                        escenario.vehiculo("BICI-02"), escenario.almacen("ALM-I2"))
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

    /** Instancia de prueba compartida por las verificaciones. */
    private static final class Escenario {
        private final MatrizDistancias distancias;
        private final List<Almacen> almacenes;
        private final List<Vehiculo> vehiculos;
        private final List<Pedido> pedidos;
        private final EstadoOperacion estado;

        private Escenario(
                MatrizDistancias distancias,
                List<Almacen> almacenes,
                List<Vehiculo> vehiculos,
                List<Pedido> pedidos
        ) {
            this.distancias = distancias;
            this.almacenes = almacenes;
            this.vehiculos = vehiculos;
            this.pedidos = pedidos;
            this.estado = new EstadoOperacion(AHORA, pedidos, almacenes, vehiculos);
        }

        private static Escenario completo() {
            Map<String, double[]> coordenadas = new LinkedHashMap<>();
            coordenadas.put("CENTRAL", new double[]{0, 0});
            coordenadas.put("INT-1", new double[]{10, 0});
            coordenadas.put("INT-2", new double[]{0, 10});
            coordenadas.put("C1", new double[]{11, 2});
            coordenadas.put("C2", new double[]{12, 1});
            coordenadas.put("C3", new double[]{2, 11});
            coordenadas.put("C4", new double[]{1, 12});
            coordenadas.put("C5", new double[]{6, 6});
            coordenadas.put("C6", new double[]{9, 4});

            Map<String, Ubicacion> ubicaciones = new LinkedHashMap<>();
            for (String id : coordenadas.keySet()) {
                ubicaciones.put(id, new Ubicacion(id));
            }

            MatrizDistancias matriz = new MatrizDistancias();
            List<String> ids = new ArrayList<>(coordenadas.keySet());
            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    double[] a = coordenadas.get(ids.get(i));
                    double[] b = coordenadas.get(ids.get(j));
                    matriz.registrar(
                            ubicaciones.get(ids.get(i)),
                            ubicaciones.get(ids.get(j)),
                            Math.hypot(a[0] - b[0], a[1] - b[1])
                    );
                }
            }

            List<Almacen> almacenes = List.of(
                    Almacen.central("ALM-C", ubicaciones.get("CENTRAL")),
                    Almacen.intermedio("ALM-I1", ubicaciones.get("INT-1"), 30),
                    Almacen.intermedio("ALM-I2", ubicaciones.get("INT-2"), 30)
            );

            List<Vehiculo> vehiculos = List.of(
                    new Vehiculo("AUTO-01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, ubicaciones.get("CENTRAL")),
                    new Vehiculo("MOTO-01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, ubicaciones.get("INT-1")),
                    new Vehiculo("MOTO-02", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, ubicaciones.get("INT-2")),
                    new Vehiculo("BICI-01", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, ubicaciones.get("INT-1")),
                    new Vehiculo("BICI-02", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, ubicaciones.get("INT-2"))
            );

            List<Pedido> pedidos = List.of(
                    new Pedido("P-001", "C-001", ubicaciones.get("C1"), 3, AHORA, TipoEntrega.PRIORITARIA_8H),
                    new Pedido("P-002", "C-002", ubicaciones.get("C2"), 2, AHORA, TipoEntrega.PRIORITARIA_12H),
                    new Pedido("P-003", "C-003", ubicaciones.get("C3"), 4, AHORA, TipoEntrega.PRIORITARIA_8H),
                    new Pedido("P-004", "C-004", ubicaciones.get("C4"), 2, AHORA, TipoEntrega.PRIORITARIA_18H),
                    new Pedido("P-005", "C-005", ubicaciones.get("C5"), 3, AHORA, TipoEntrega.REGULAR_36H),
                    new Pedido("P-006", "C-006", ubicaciones.get("C6"), 3, AHORA, TipoEntrega.PRIORITARIA_12H)
            );

            return new Escenario(matriz, almacenes, vehiculos, pedidos);
        }

        /** Plan armado a mano: dos rutas con dos entregas cada una y un pedido pendiente. */
        private Solucion solucionDePrueba() {
            Solucion solucion = new Solucion();

            Ruta primera = new Ruta("R-1", almacen("ALM-I1"), vehiculo("MOTO-01"));
            primera.insertarPedido(0, pedido("P-001"));
            primera.insertarPedido(1, pedido("P-002"));
            solucion.agregarRuta(primera);

            Ruta segunda = new Ruta("R-2", almacen("ALM-I2"), vehiculo("MOTO-02"));
            segunda.insertarPedido(0, pedido("P-003"));
            segunda.insertarPedido(1, pedido("P-004"));
            solucion.agregarRuta(segunda);

            solucion.agregarPedidoNoAsignado(pedido("P-005"));
            return solucion;
        }

        private Pedido pedido(String id) {
            return pedidos.stream()
                    .filter(p -> p.getId().equals(id))
                    .findFirst()
                    .orElseThrow();
        }

        private Vehiculo vehiculo(String id) {
            return vehiculos.stream()
                    .filter(v -> v.getId().equals(id))
                    .findFirst()
                    .orElseThrow();
        }

        private Almacen almacen(String id) {
            return almacenes.stream()
                    .filter(a -> a.getId().equals(id))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
