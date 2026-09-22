package com.paqrap.demo;

import com.paqrap.modelo.Solucion;
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;

import java.time.LocalDateTime;

/**
 * Dataset mediano (13 pedidos, flota completa) para comparar GRASP contra la búsqueda tabú.
 *
 * Ejecutar con:
 *   mvn package
 *   java -cp target/classes com.paqrap.demo.DemoTabuDataset
 */
public final class DemoTabuDataset {

    private DemoTabuDataset() {
    }

    public static void main(String[] args) {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 15, 8, 0);
        EscenarioDemo escenario = EscenarioDemo.mediano(ahora);

        Parametros parametros = Parametros.constructor(50, 0.30, 20260915L)
                .iteracionesTabu(400)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(80)
                .iteracionesSinMejora(60)
                .limiteMilisegundos(5_000L)
                .construir();

        BusquedaTabu tabuSearch = new BusquedaTabu(escenario.distancias());

        Solucion grasp = new Grasp(escenario.distancias()).planificar(escenario.estado(), parametros);
        Solucion inicial = tabuSearch.construirSolucionInicial(escenario.estado(), parametros);
        Solucion tabu = tabuSearch.planificar(escenario.estado(), parametros);

        System.out.println("=== DATASET MEDIANO: GRASP vs BUSQUEDA TABU ===");
        System.out.println("Pedidos de entrada: " + escenario.pedidos().size());
        System.out.println("Almacenes: " + escenario.almacenes().size());
        System.out.println("Unidades: " + escenario.vehiculos().size());

        Reporte.imprimir(grasp);
        Reporte.imprimir(inicial);
        Reporte.imprimir(tabu);
        Reporte.compararAlgoritmos(grasp, inicial, tabu);

        System.out.println();
        System.out.println("Nota: P-013 pide 30 unidades y ninguna unidad de la flota las lleva "
                + "en un solo viaje; quedara sin asignar hasta que existan entregas parciales.");
    }
}
