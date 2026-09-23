package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoEntrega;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraspTest {

    @Test
    void debeConstruirRutasConAlmacenVehiculoYPedidos() {
        Escenario e = escenarioBase();

        Solucion solucion = e.grasp.construir(
                e.ahora,
                e.pedidos,
                e.almacenes,
                e.vehiculos,
                new Parametros(20, 0.30, 12345L)
        );

        assertFalse(solucion.getRutas().isEmpty());
        assertTrue(solucion.getPedidosNoAsignados().isEmpty());

        int pedidosAsignados = solucion.getRutas().stream()
                .mapToInt(r -> r.getPedidos().size())
                .sum();

        assertEquals(e.pedidos.size(), pedidosAsignados);

        for (Ruta ruta : solucion.getRutas()) {
            assertNotNull(ruta.getAlmacen());
            assertNotNull(ruta.getVehiculo());
            assertFalse(ruta.getPedidos().isEmpty());
            assertTrue(ruta.getCargaTotal() <= ruta.getVehiculo().getCapacidad());
            assertTrue(ruta.getCostoTotal() >= 0);
        }
    }

    @Test
    void noDebeAsignarPedidoQueExcedeCapacidadDeTodaLaFlota() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);
        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion cliente = new Ubicacion("CLIENTE-X");

        Almacen central = Almacen.central("ALM-C", centralU);
        Vehiculo auto = new Vehiculo(
                "AUTO-01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, centralU);

        Pedido imposible = new Pedido(
                "P-X",
                "C-X",
                cliente,
                25,
                ahora,
                TipoEntrega.REGULAR_36H
        );

        MatrizDistancias matriz = new MatrizDistancias();
        matriz.registrar(centralU, cliente, 1);

        Grasp grasp = new Grasp(matriz);
        Solucion solucion = grasp.construir(
                ahora,
                List.of(imposible),
                List.of(central),
                List.of(auto),
                new Parametros(1, 0, 1L)
        );

        assertTrue(solucion.getRutas().isEmpty());
        assertEquals(List.of(imposible), solucion.getPedidosNoAsignados());
    }

    @Test
    void debeUsarCentralComoRespaldoSiIntermedioNoTieneStock() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);

        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion intermedioU = new Ubicacion("INTERMEDIO");
        Ubicacion clienteU = new Ubicacion("CLIENTE");

        Almacen central = Almacen.central("ALM-C", centralU);
        Almacen intermedio = Almacen.intermedio("ALM-I", intermedioU, 1);

        Vehiculo moto = new Vehiculo(
                "MOTO-01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, centralU);

        Pedido pedido = new Pedido(
                "P-1",
                "C-1",
                clienteU,
                4,
                ahora,
                TipoEntrega.PRIORITARIA_8H
        );

        MatrizDistancias matriz = new MatrizDistancias();
        matriz.registrar(centralU, clienteU, 5);
        matriz.registrar(intermedioU, clienteU, 1);

        Grasp grasp = new Grasp(matriz);
        Solucion solucion = grasp.construir(
                ahora,
                List.of(pedido),
                List.of(intermedio, central),
                List.of(moto),
                new Parametros(1, 0, 8L)
        );

        assertEquals(1, solucion.getRutas().size());
        assertEquals("ALM-C", solucion.getRutas().get(0).getAlmacen().getId());
        assertTrue(solucion.getPedidosNoAsignados().isEmpty());
    }

    @Test
    void debeRechazarRutaQueIncumplePlazo() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);

        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion clienteU = new Ubicacion("CLIENTE-LEJANO");

        Almacen central = Almacen.central("ALM-C", centralU);
        Vehiculo bicicleta = new Vehiculo(
                "BICI-01",
                TipoVehiculo.BICICLETA,
                EstadoVehiculo.DISPONIBLE,
                centralU
        );

        Pedido urgente = new Pedido(
                "P-U",
                "C-U",
                clienteU,
                1,
                ahora,
                TipoEntrega.PRIORITARIA_4H
        );

        MatrizDistancias matriz = new MatrizDistancias();
        // 48 km / 12 km/h = 4 h de viaje + 1 h de atención => 5 h, incumple.
        matriz.registrar(centralU, clienteU, 48);

        Grasp grasp = new Grasp(matriz);
        Solucion solucion = grasp.construir(
                ahora,
                List.of(urgente),
                List.of(central),
                List.of(bicicleta),
                new Parametros(1, 0, 99L)
        );

        assertTrue(solucion.getRutas().isEmpty());
        assertEquals(1, solucion.getPedidosNoAsignados().size());
    }

    @Test
    void busquedaLocalDebeMejorarElOrdenDeUnaRuta() {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);

        Ubicacion almacenU = new Ubicacion("A");
        Ubicacion b = new Ubicacion("B");
        Ubicacion c = new Ubicacion("C");
        Ubicacion d = new Ubicacion("D");

        Almacen central = Almacen.central("ALM-C", almacenU);
        Vehiculo auto = new Vehiculo(
                "AUTO-1", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, almacenU);

        Pedido p1 = new Pedido("P1", "C1", b, 1, ahora, TipoEntrega.REGULAR_36H);
        Pedido p2 = new Pedido("P2", "C2", c, 1, ahora, TipoEntrega.REGULAR_36H);
        Pedido p3 = new Pedido("P3", "C3", d, 1, ahora, TipoEntrega.REGULAR_36H);

        MatrizDistancias m = new MatrizDistancias();
        m.registrar(almacenU, b, 10);
        m.registrar(almacenU, c, 9);
        m.registrar(almacenU, d, 8);
        m.registrar(b, c, 50);
        m.registrar(c, d, 50);
        m.registrar(b, d, 1);

        Solucion solucion = new Grasp(m).construir(
                ahora,
                List.of(p1, p2, p3),
                List.of(central),
                List.of(auto),
                new Parametros(1, 0.0, 7L)
        );

        assertEquals(1, solucion.getRutas().size());
        Ruta ruta = solucion.getRutas().get(0);

        // La fase constructiva genera B -> C -> D (110 km).
        // La búsqueda local encuentra D -> B -> C (59 km).
        assertEquals(List.of(p3, p1, p2), ruta.getPedidos());
        assertEquals(59.0, ruta.getDistanciaTotalKm(), 1e-9);
        assertEquals(472.0, ruta.getCostoTotal(), 1e-9);
        assertTrue(solucion.getPedidosNoAsignados().isEmpty());
    }

    private Escenario escenarioBase() {
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

        Vehiculo auto = new Vehiculo(
                "AUTO-1", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, centralU);
        Vehiculo moto = new Vehiculo(
                "MOTO-1", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, i1U);
        Vehiculo bici = new Vehiculo(
                "BICI-1", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, i2U);

        Pedido p1 = new Pedido(
                "P1", "C1", c1, 4, ahora, TipoEntrega.PRIORITARIA_4H);
        Pedido p2 = new Pedido(
                "P2", "C2", c2, 3, ahora, TipoEntrega.PRIORITARIA_8H);
        Pedido p3 = new Pedido(
                "P3", "C3", c3, 6, ahora, TipoEntrega.PRIORITARIA_12H);

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

        Grasp grasp = new Grasp(m);

        return new Escenario(
                ahora,
                List.of(p1, p2, p3),
                List.of(central, i1, i2),
                List.of(auto, moto, bici),
                grasp
        );
    }

    private record Escenario(
            LocalDateTime ahora,
            List<Pedido> pedidos,
            List<Almacen> almacenes,
            List<Vehiculo> vehiculos,
            Grasp grasp
    ) {
    }
}
