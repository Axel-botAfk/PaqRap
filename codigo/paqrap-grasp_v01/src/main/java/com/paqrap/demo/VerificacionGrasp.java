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
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.MatrizDistancias;
import com.paqrap.planificador.Parametros;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Verificación ejecutable sin JUnit, útil como evidencia cuando solo se dispone de JDK.
 */
public final class VerificacionGrasp {
    private VerificacionGrasp() {
    }

    public static void main(String[] args) {
        pruebaConstruccionValida();
        pruebaCapacidad();
        pruebaCentralComoRespaldo();
        pruebaPlazo();
        System.out.println("OK - 4 verificaciones de GRASP superadas.");
    }

    private static void pruebaConstruccionValida() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);

        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion i1U = new Ubicacion("I1");
        Ubicacion i2U = new Ubicacion("I2");
        Ubicacion c1 = new Ubicacion("C1");
        Ubicacion c2 = new Ubicacion("C2");
        Ubicacion c3 = new Ubicacion("C3");

        Almacen central = Almacen.central("ALM-C", centralU);
        Almacen i1 = Almacen.intermedio("ALM-I1", i1U, 20);
        Almacen i2 = Almacen.intermedio("ALM-I2", i2U, 20);

        Vehiculo auto = new Vehiculo("AUTO-1", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, centralU);
        Vehiculo moto = new Vehiculo("MOTO-1", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, i1U);
        Vehiculo bici = new Vehiculo("BICI-1", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, i2U);

        Pedido p1 = new Pedido("P1", "C1", c1, 4, ahora, TipoEntrega.PRIORITARIA_4H);
        Pedido p2 = new Pedido("P2", "C2", c2, 3, ahora, TipoEntrega.PRIORITARIA_8H);
        Pedido p3 = new Pedido("P3", "C3", c3, 6, ahora, TipoEntrega.PRIORITARIA_12H);

        MatrizDistancias m = new MatrizDistancias();
        m.registrar(centralU, c1, 10);
        m.registrar(centralU, c2, 12);
        m.registrar(centralU, c3, 14);
        m.registrar(i1U, c1, 2);
        m.registrar(i1U, c2, 5);
        m.registrar(i1U, c3, 8);
        m.registrar(i2U, c1, 7);
        m.registrar(i2U, c2, 3);
        m.registrar(i2U, c3, 4);
        m.registrar(c1, c2, 3);
        m.registrar(c1, c3, 6);
        m.registrar(c2, c3, 2);

        Solucion s = new Grasp(m).construir(
                ahora,
                List.of(p1, p2, p3),
                List.of(central, i1, i2),
                List.of(auto, moto, bici),
                new Parametros(20, 0.30, 12345L)
        );

        exigir(s.getPedidosNoAsignados().isEmpty(), "Deberían asignarse todos los pedidos.");
        exigir(!s.getRutas().isEmpty(), "Debe generarse al menos una ruta.");

        int asignados = 0;
        for (Ruta r : s.getRutas()) {
            exigir(r.getAlmacen() != null, "Cada ruta debe tener almacén.");
            exigir(r.getVehiculo() != null, "Cada ruta debe tener vehículo.");
            exigir(!r.getPedidos().isEmpty(), "Cada ruta debe tener pedidos.");
            exigir(r.getVehiculo().getUbicacion().equals(r.getAlmacen().getUbicacion()),
                    "El vehículo debe iniciar en el almacén asignado.");
            exigir(r.getCargaTotal() <= r.getVehiculo().getCapacidad(),
                    "La ruta no debe superar capacidad.");
            asignados += r.getPedidos().size();
        }
        exigir(asignados == 3, "Deben quedar asignados los 3 pedidos.");
    }

    private static void pruebaCapacidad() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);
        Ubicacion a = new Ubicacion("A");
        Ubicacion c = new Ubicacion("C");

        Pedido pedido = new Pedido("PX", "CX", c, 25, ahora, TipoEntrega.REGULAR_36H);
        Vehiculo auto = new Vehiculo("AUTO", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, a);
        Almacen central = Almacen.central("CENTRAL", a);

        MatrizDistancias m = new MatrizDistancias();
        m.registrar(a, c, 1);

        Solucion s = new Grasp(m).construir(
                ahora, List.of(pedido), List.of(central), List.of(auto),
                new Parametros(1, 0, 1L));

        exigir(s.getRutas().isEmpty(), "Un pedido de 25 no cabe en un auto de 24.");
        exigir(s.getPedidosNoAsignados().size() == 1, "El pedido debe quedar no asignado.");
    }

    private static void pruebaCentralComoRespaldo() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);
        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion intermedioU = new Ubicacion("I");
        Ubicacion clienteU = new Ubicacion("C");

        Almacen central = Almacen.central("ALM-C", centralU);
        Almacen intermedio = Almacen.intermedio("ALM-I", intermedioU, 1);
        Vehiculo moto = new Vehiculo("MOTO", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, centralU);
        Pedido pedido = new Pedido("P", "C", clienteU, 4, ahora, TipoEntrega.PRIORITARIA_8H);

        MatrizDistancias m = new MatrizDistancias();
        m.registrar(centralU, clienteU, 5);
        m.registrar(intermedioU, clienteU, 1);

        Solucion s = new Grasp(m).construir(
                ahora, List.of(pedido), List.of(intermedio, central), List.of(moto),
                new Parametros(1, 0, 2L));

        exigir(s.getRutas().size() == 1, "Debe existir una ruta.");
        exigir("ALM-C".equals(s.getRutas().get(0).getAlmacen().getId()),
                "Debe usarse el almacén central por falta de stock intermedio.");
    }

    private static void pruebaPlazo() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);
        Ubicacion a = new Ubicacion("A");
        Ubicacion c = new Ubicacion("C");

        Almacen central = Almacen.central("CENTRAL", a);
        Vehiculo bici = new Vehiculo("BICI", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, a);
        Pedido pedido = new Pedido("P", "C", c, 1, ahora, TipoEntrega.PRIORITARIA_4H);

        MatrizDistancias m = new MatrizDistancias();
        m.registrar(a, c, 48); // 4 h de viaje + 1 h atención > plazo 4 h

        Solucion s = new Grasp(m).construir(
                ahora, List.of(pedido), List.of(central), List.of(bici),
                new Parametros(1, 0, 3L));

        exigir(s.getRutas().isEmpty(), "No debe crear una ruta que incumpla el plazo.");
        exigir(s.getPedidosNoAsignados().size() == 1, "El pedido debe quedar no asignado.");
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
