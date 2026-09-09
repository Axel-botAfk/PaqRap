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
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.MatrizDistancias;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compara la solución construida por GRASP con la que devuelve la búsqueda tabú a partir
 * de ella, sobre una misma instancia y con la misma semilla.
 */
public final class DemoTabu {

    private DemoTabu() {
    }

    public static void main(String[] args) {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);

        // Coordenadas solo de prueba, para poder generar una matriz de distancias completa.
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

        MatrizDistancias distancias = registrarDistancias(coordenadas, ubicaciones);

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
                new Pedido("P-001", "C-001", ubicaciones.get("C1"), 3, ahora, TipoEntrega.PRIORITARIA_8H),
                new Pedido("P-002", "C-002", ubicaciones.get("C2"), 2, ahora, TipoEntrega.PRIORITARIA_12H),
                new Pedido("P-003", "C-003", ubicaciones.get("C3"), 4, ahora, TipoEntrega.PRIORITARIA_8H),
                new Pedido("P-004", "C-004", ubicaciones.get("C4"), 2, ahora, TipoEntrega.PRIORITARIA_18H),
                new Pedido("P-005", "C-005", ubicaciones.get("C5"), 5, ahora, TipoEntrega.REGULAR_36H),
                new Pedido("P-006", "C-006", ubicaciones.get("C6"), 3, ahora, TipoEntrega.PRIORITARIA_12H)
        );

        EstadoOperacion estado = new EstadoOperacion(ahora, pedidos, almacenes, vehiculos);
        Parametros parametros = Parametros.constructor(20, 0.30, 20260908L)
                .iteracionesTabu(300)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(60)
                .iteracionesSinMejora(40)
                .limiteMilisegundos(3_000L)
                .construir();

        Solucion solucionGrasp = new Grasp(distancias).planificar(estado, parametros);
        Solucion solucionTabu = new BusquedaTabu(distancias).planificar(estado, parametros);

        imprimir(solucionGrasp);
        imprimir(solucionTabu);

        double mejora = solucionGrasp.getCostoTotal() - solucionTabu.getCostoTotal();
        System.out.printf(
                "%nMejora de la búsqueda tabú: S/ %.2f (%.1f%%)%n",
                mejora,
                solucionGrasp.getCostoTotal() == 0.0 ? 0.0 : 100.0 * mejora / solucionGrasp.getCostoTotal()
        );
    }

    private static void imprimir(Solucion solucion) {
        System.out.println();
        System.out.println("=== " + solucion.getAlgoritmo() + " ===");
        for (Ruta ruta : solucion.getRutas()) {
            System.out.printf(
                    "%-6s | almacen=%-7s | vehiculo=%-8s | pedidos=%-28s | carga=%2d/%2d | km=%6.2f | costo=S/ %8.2f%n",
                    ruta.getId(),
                    ruta.getAlmacen().getId(),
                    ruta.getVehiculo().getId(),
                    ruta.getPedidos().toString(),
                    ruta.getCargaTotal(),
                    ruta.getVehiculo().getCapacidad(),
                    ruta.getDistanciaTotalKm(),
                    ruta.getCostoTotal()
            );
        }
        System.out.println("No asignados: " + solucion.getPedidosNoAsignados());
        System.out.printf("Costo total: S/ %.2f | Distancia total: %.2f km%n",
                solucion.getCostoTotal(), solucion.getDistanciaTotalKm());
    }

    private static MatrizDistancias registrarDistancias(
            Map<String, double[]> coordenadas,
            Map<String, Ubicacion> ubicaciones
    ) {
        MatrizDistancias matriz = new MatrizDistancias();
        List<String> ids = new ArrayList<>(coordenadas.keySet());

        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                double[] a = coordenadas.get(ids.get(i));
                double[] b = coordenadas.get(ids.get(j));
                double km = Math.hypot(a[0] - b[0], a[1] - b[1]);
                matriz.registrar(ubicaciones.get(ids.get(i)), ubicaciones.get(ids.get(j)), km);
            }
        }
        return matriz;
    }
}
