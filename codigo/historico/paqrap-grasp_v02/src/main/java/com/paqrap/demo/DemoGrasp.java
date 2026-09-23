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

public final class DemoGrasp {

    private DemoGrasp() {
    }

    public static void main(String[] args) {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);

        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion int1U = new Ubicacion("INT-1");
        Ubicacion int2U = new Ubicacion("INT-2");
        Ubicacion c1 = new Ubicacion("CLIENTE-1");
        Ubicacion c2 = new Ubicacion("CLIENTE-2");
        Ubicacion c3 = new Ubicacion("CLIENTE-3");

        Almacen central = Almacen.central("ALM-C", centralU);
        Almacen int1 = Almacen.intermedio("ALM-I1", int1U, 10);
        Almacen int2 = Almacen.intermedio("ALM-I2", int2U, 10);

        Vehiculo auto = new Vehiculo(
                "AUTO-01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, centralU);
        Vehiculo moto = new Vehiculo(
                "MOTO-01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, int1U);
        Vehiculo bici = new Vehiculo(
                "BICI-01", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, int2U);

        Pedido p1 = new Pedido(
                "P-001", "C-001", c1, 4, ahora, TipoEntrega.PRIORITARIA_4H);
        Pedido p2 = new Pedido(
                "P-002", "C-002", c2, 3, ahora, TipoEntrega.PRIORITARIA_8H);
        Pedido p3 = new Pedido(
                "P-003", "C-003", c3, 6, ahora, TipoEntrega.PRIORITARIA_12H);

        MatrizDistancias distancias = new MatrizDistancias();
        registrarDistancias(distancias, centralU, int1U, int2U, c1, c2, c3);

        Grasp grasp = new Grasp(distancias);
        Parametros parametros = new Parametros(20, 0.30, 20260908L);

        Solucion solucion = grasp.construir(
                ahora,
                List.of(p1, p2, p3),
                List.of(central, int1, int2),
                List.of(auto, moto, bici),
                parametros
        );

        System.out.println("=== SOLUCION GRASP ===");
        for (Ruta ruta : solucion.getRutas()) {
            System.out.printf(
                    "%s | almacen=%s | vehiculo=%s | pedidos=%s | carga=%d/%d | km=%.2f | costo=S/ %.2f%n",
                    ruta.getId(),
                    ruta.getAlmacen().getId(),
                    ruta.getVehiculo().getId(),
                    ruta.getPedidos(),
                    ruta.getCargaTotal(),
                    ruta.getVehiculo().getCapacidad(),
                    ruta.getDistanciaTotalKm(),
                    ruta.getCostoTotal()
            );
        }

        System.out.println("No asignados: " + solucion.getPedidosNoAsignados());
        System.out.printf("Costo total: S/ %.2f%n", solucion.getCostoTotal());
    }

    private static void registrarDistancias(
            MatrizDistancias m,
            Ubicacion central,
            Ubicacion i1,
            Ubicacion i2,
            Ubicacion c1,
            Ubicacion c2,
            Ubicacion c3
    ) {
        // Datos exclusivamente de prueba.
        m.registrar(central, c1, 12);
        m.registrar(central, c2, 14);
        m.registrar(central, c3, 16);

        m.registrar(i1, c1, 2);
        m.registrar(i1, c2, 6);
        m.registrar(i1, c3, 9);

        m.registrar(i2, c1, 8);
        m.registrar(i2, c2, 3);
        m.registrar(i2, c3, 5);

        m.registrar(c1, c2, 4);
        m.registrar(c1, c3, 7);
        m.registrar(c2, c3, 3);
    }
}
