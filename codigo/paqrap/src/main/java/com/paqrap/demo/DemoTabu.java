package com.paqrap.demo;

import com.paqrap.modelo.Solucion;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;

import java.time.LocalDateTime;
import com.paqrap.escenarios.Escenario;

/**
 * Compara los dos algoritmos del caso sobre una misma instancia y con la misma semilla.
 *
 * Son independientes: GRASP construye con su lista restringida aleatorizada, y la búsqueda
 * tabú parte de su propio constructivo de vecino más cercano. Se imprime también esa solución
 * de partida, para separar cuánto aporta la fase de mejora de cuánto aporta el punto inicial.
 *
 * Ejecutar con:
 *   mvn package
 *   java -cp target/classes com.paqrap.demo.DemoTabu
 */
public final class DemoTabu {

    private DemoTabu() {
    }

    public static void main(String[] args) {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);
        Escenario escenario = Escenario.pequeno(ahora);

        Parametros parametros = Parametros.constructor(50, 0.30, 20260908L)
                .iteracionesTabu(300)
                .tenenciaTabu(8)
                .tamanoMuestraVecindario(60)
                .iteracionesSinMejora(40)
                .construir();

        BusquedaTabu tabuSearch = new BusquedaTabu(escenario.distancias());

        Solucion grasp = new Grasp(escenario.distancias()).planificar(escenario.estado(), parametros);
        Solucion inicial = tabuSearch.construirSolucionInicial(escenario.estado(), parametros);
        Solucion tabu = tabuSearch.planificar(escenario.estado(), parametros);

        System.out.println("=== GRASP vs BUSQUEDA TABU ===");
        System.out.println("Pedidos: " + escenario.pedidos().size()
                + " | Unidades: " + escenario.vehiculos().size());

        Reporte.imprimir(grasp);
        Reporte.imprimir(inicial);
        Reporte.imprimir(tabu);
        Reporte.compararAlgoritmos(grasp, inicial, tabu);
    }
}
