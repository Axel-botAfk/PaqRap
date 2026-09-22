package com.paqrap.demo;

import com.paqrap.modelo.Solucion;
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.Parametros;

import java.time.LocalDateTime;

/**
 * Construcción GRASP sobre el escenario pequeño de la ciudad del caso.
 *
 * Ejecutar con:
 *   mvn package
 *   java -cp target/classes com.paqrap.demo.DemoGrasp
 */
public final class DemoGrasp {

    private DemoGrasp() {
    }

    public static void main(String[] args) {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);
        EscenarioDemo escenario = EscenarioDemo.pequeno(ahora);

        Parametros parametros = Parametros.constructor(50, 0.30, 20260908L).construir();
        Solucion solucion = new Grasp(escenario.distancias()).planificar(escenario.estado(), parametros);

        System.out.println("=== GRASP: construccion ===");
        System.out.println("Hora de planificacion: " + ahora);
        System.out.println("Pedidos: " + escenario.pedidos().size()
                + " | Almacenes: " + escenario.almacenes().size()
                + " | Unidades: " + escenario.vehiculos().size());

        Reporte.imprimir(solucion);
    }
}
