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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dataset de prueba mediano para GRASP de PaqRap.
 *
 * Copiar este archivo en:
 * src/main/java/com/paqrap/demo/DemoGraspDataset.java
 *
 * Ejecutar con:
 * java -cp target/classes com.paqrap.demo.DemoGraspDataset
 *
 * El dataset incluye:
 * - 3 almacenes (1 central + 2 intermedios)
 * - 8 vehículos
 * - 13 pedidos
 * - 1 pedido deliberadamente no asignable (30 paquetes)
 * - matriz completa y simétrica de distancias calculada desde coordenadas de prueba
 */
public final class DemoGraspDataset {

    private DemoGraspDataset() {
    }

    private record Punto(double x, double y) {
    }

    public static void main(String[] args) {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 15, 8, 0);

        // -------------------------
        // 1. Ubicaciones
        // -------------------------
        Ubicacion centralU = new Ubicacion("CENTRAL");
        Ubicacion int1U = new Ubicacion("INT-1");
        Ubicacion int2U = new Ubicacion("INT-2");

        Ubicacion c1 = new Ubicacion("CLIENTE-01");
        Ubicacion c2 = new Ubicacion("CLIENTE-02");
        Ubicacion c3 = new Ubicacion("CLIENTE-03");
        Ubicacion c4 = new Ubicacion("CLIENTE-04");
        Ubicacion c5 = new Ubicacion("CLIENTE-05");
        Ubicacion c6 = new Ubicacion("CLIENTE-06");
        Ubicacion c7 = new Ubicacion("CLIENTE-07");
        Ubicacion c8 = new Ubicacion("CLIENTE-08");
        Ubicacion c9 = new Ubicacion("CLIENTE-09");
        Ubicacion c10 = new Ubicacion("CLIENTE-10");
        Ubicacion c11 = new Ubicacion("CLIENTE-11");
        Ubicacion c12 = new Ubicacion("CLIENTE-12");
        Ubicacion c13 = new Ubicacion("CLIENTE-13");

        // Coordenadas de prueba, usadas únicamente para construir la matriz de distancias.
        Map<Ubicacion, Punto> puntos = new LinkedHashMap<>();
        puntos.put(centralU, new Punto(0, 0));
        puntos.put(int1U, new Punto(5, 6));
        puntos.put(int2U, new Punto(10, 3));

        puntos.put(c1, new Punto(5.5, 6.5));
        puntos.put(c2, new Punto(6.5, 6.0));
        puntos.put(c3, new Punto(7.0, 7.5));
        puntos.put(c4, new Punto(9.5, 3.5));
        puntos.put(c5, new Punto(11.0, 2.5));
        puntos.put(c6, new Punto(2.0, 2.5));
        puntos.put(c7, new Punto(3.0, 4.0));
        puntos.put(c8, new Punto(4.0, 8.0));
        puntos.put(c9, new Punto(8.0, 8.5));
        puntos.put(c10, new Punto(12.0, 6.5));
        puntos.put(c11, new Punto(1.5, 7.5));
        puntos.put(c12, new Punto(7.5, 1.0));
        puntos.put(c13, new Punto(13.0, 1.0));

        // -------------------------
        // 2. Almacenes
        // -------------------------
        Almacen central = Almacen.central("ALM-C", centralU);
        Almacen int1 = Almacen.intermedio("ALM-I1", int1U, 35);
        Almacen int2 = Almacen.intermedio("ALM-I2", int2U, 30);

        List<Almacen> almacenes = List.of(central, int1, int2);

        // -------------------------
        // 3. Vehículos
        // -------------------------
        List<Vehiculo> vehiculos = List.of(
                new Vehiculo("AUTO-C-01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, centralU),
                new Vehiculo("MOTO-C-01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, centralU),

                new Vehiculo("AUTO-I1-01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, int1U),
                new Vehiculo("MOTO-I1-01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, int1U),
                new Vehiculo("BICI-I1-01", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, int1U),

                new Vehiculo("AUTO-I2-01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, int2U),
                new Vehiculo("MOTO-I2-01", TipoVehiculo.MOTO, EstadoVehiculo.DISPONIBLE, int2U),
                new Vehiculo("BICI-I2-01", TipoVehiculo.BICICLETA, EstadoVehiculo.DISPONIBLE, int2U)
        );

        // -------------------------
        // 4. Pedidos
        // -------------------------
        List<Pedido> pedidos = new ArrayList<>();

        pedidos.add(new Pedido("P-001", "C-001", c1, 2, ahora, TipoEntrega.PRIORITARIA_4H));
        pedidos.add(new Pedido("P-002", "C-002", c2, 3, ahora, TipoEntrega.PRIORITARIA_8H));
        pedidos.add(new Pedido("P-003", "C-003", c3, 4, ahora, TipoEntrega.PRIORITARIA_12H));
        pedidos.add(new Pedido("P-004", "C-004", c4, 5, ahora, TipoEntrega.PRIORITARIA_8H));
        pedidos.add(new Pedido("P-005", "C-005", c5, 3, ahora, TipoEntrega.PRIORITARIA_18H));
        pedidos.add(new Pedido("P-006", "C-006", c6, 7, ahora, TipoEntrega.PRIORITARIA_8H));
        pedidos.add(new Pedido("P-007", "C-007", c7, 5, ahora, TipoEntrega.PRIORITARIA_12H));
        pedidos.add(new Pedido("P-008", "C-008", c8, 2, ahora, TipoEntrega.PRIORITARIA_18H));
        pedidos.add(new Pedido("P-009", "C-009", c9, 4, ahora, TipoEntrega.REGULAR_36H));
        pedidos.add(new Pedido("P-010", "C-010", c10, 6, ahora, TipoEntrega.REGULAR_36H));
        pedidos.add(new Pedido("P-011", "C-011", c11, 1, ahora, TipoEntrega.PRIORITARIA_4H));
        pedidos.add(new Pedido("P-012", "C-012", c12, 3, ahora, TipoEntrega.PRIORITARIA_12H));

        // Pedido deliberadamente imposible con el modelo actual:
        // el vehículo de mayor capacidad (AUTO) admite 24 paquetes y el pedido no se divide.
        pedidos.add(new Pedido("P-013", "C-013", c13, 30, ahora, TipoEntrega.REGULAR_36H));

        // -------------------------
        // 5. Distancias
        // -------------------------
        MatrizDistancias distancias = new MatrizDistancias();
        registrarMatrizCompleta(distancias, puntos);

        // -------------------------
        // 6. Parámetros GRASP
        // -------------------------
        Parametros parametros = new Parametros(
                50,        // maxIteraciones
                0.30,      // alfa
                20260915L  // semilla reproducible
        );

        // -------------------------
        // 7. Ejecución
        // -------------------------
        Grasp grasp = new Grasp(distancias);

        Solucion solucion = grasp.construir(
                ahora,
                pedidos,
                almacenes,
                vehiculos,
                parametros
        );

        // -------------------------
        // 8. Resultado
        // -------------------------
        System.out.println("=== DATASET DE PRUEBA GRASP ===");
        System.out.println("Pedidos de entrada: " + pedidos.size());
        System.out.println("Almacenes: " + almacenes.size());
        System.out.println("Vehiculos: " + vehiculos.size());
        System.out.println();

        int asignados = 0;
        for (Ruta ruta : solucion.getRutas()) {
            asignados += ruta.getPedidos().size();
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

        System.out.println();
        System.out.println("Pedidos asignados: " + asignados);
        System.out.println("Pedidos no asignados: " + solucion.getPedidosNoAsignados());
        System.out.printf("Costo total: S/ %.2f%n", solucion.getCostoTotal());
    }

    /**
     * Registra todas las distancias entre pares de ubicaciones.
     * Se usa distancia euclidiana y se escala por 2.5 para obtener valores
     * de kilómetros útiles para una prueba de planificación.
     */
    private static void registrarMatrizCompleta(
            MatrizDistancias matriz,
            Map<Ubicacion, Punto> puntos
    ) {
        List<Ubicacion> ubicaciones = new ArrayList<>(puntos.keySet());

        for (int i = 0; i < ubicaciones.size(); i++) {
            for (int j = i + 1; j < ubicaciones.size(); j++) {
                Ubicacion a = ubicaciones.get(i);
                Ubicacion b = ubicaciones.get(j);

                Punto pa = puntos.get(a);
                Punto pb = puntos.get(b);

                double dx = pa.x() - pb.x();
                double dy = pa.y() - pb.y();
                double km = Math.hypot(dx, dy) * 2.5;

                matriz.registrar(a, b, redondear(km));
            }
        }
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
