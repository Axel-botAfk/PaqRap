package com.paqrap.verificacion;

import com.paqrap.datos.DatosCaso;
import com.paqrap.escenarios.Escenario;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.alns.BusquedaAlns;
import com.paqrap.planificador.alns.ParametrosAlns;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.ruteo.MapaBloqueos;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Verificación ejecutable sin JUnit de la búsqueda adaptativa de vecindarios grandes.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.verificacion.VerificacionAlns
 */
public final class VerificacionAlns {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 8, 8, 0);
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);

    private VerificacionAlns() {
    }

    public static void main(String[] args) {
        pruebaPlanValido();
        pruebaRespetaCapacidadYPlazo();
        pruebaReproducibilidadPorSemilla();
        pruebaMasIteracionesNoEmpeoran();
        pruebaRespetaBloqueos();
        pruebaUnidadQueYaLlevaCarga();
        pruebaEsIndependienteDeGrasp();
        System.out.println("OK - 7 verificaciones de ALNS superadas.");
    }

    /** Lo mínimo: el plan cubre pedidos, no inventa entregas y no repite ninguna. */
    private static void pruebaPlanValido() {
        Escenario escenario = Escenario.mediano(AHORA);
        Solucion solucion = planificar(escenario, 80, 5L);

        for (Ruta viaje : solucion.getRutas()) {
            afirmar(!viaje.estaVacia(), "Quedó un viaje sin entregas en el plan.");
        }

        // La cuenta se lleva en producto y no en número de pedidos: el planificador puede partir
        // uno que no quepa entero, y entonces los pedidos del plan son más que los del escenario
        // aunque el producto sea exactamente el mismo.
        int delEscenario = 0;
        for (Pedido pedido : escenario.estado().getPedidos()) {
            delEscenario += pedido.getCantidad();
        }
        afirmar(
                solucion.getCantidadProductosAsignados()
                        + solucion.getCantidadProductosNoAsignados() == delEscenario,
                "Lo asignado y lo no asignado suman "
                        + (solucion.getCantidadProductosAsignados()
                           + solucion.getCantidadProductosNoAsignados())
                        + " productos y el escenario pedia " + delEscenario + "."
        );

        List<String> vistos = new java.util.ArrayList<>();
        for (Ruta viaje : solucion.getRutas()) {
            for (Pedido pedido : viaje.getPedidos()) {
                afirmar(!vistos.contains(pedido.getId()),
                        "El pedido " + pedido.getId() + " aparece en dos viajes.");
                vistos.add(pedido.getId());
            }
        }
    }

    /** El plan tiene que ser factible con las mismas reglas que miden los otros algoritmos. */
    private static void pruebaRespetaCapacidadYPlazo() {
        Escenario escenario = Escenario.mediano(AHORA);
        Solucion solucion = planificar(escenario, 80, 11L);

        for (Ruta viaje : solucion.getRutas()) {
            afirmar(
                    viaje.getCargaTotal() <= viaje.getVehiculo().getCapacidad(),
                    "El viaje " + viaje.getId() + " se pasa de la capacidad de su unidad."
            );
        }

        Evaluador evaluador = new Evaluador(escenario.distancias());
        afirmar(
                evaluador.asignacionFactible(solucion, escenario.estado()),
                "El plan de ALNS no pasa la verificación de factibilidad del evaluador."
        );
    }

    /** Dos corridas con la misma semilla tienen que dar exactamente lo mismo. */
    private static void pruebaReproducibilidadPorSemilla() {
        Escenario escenario = Escenario.mediano(AHORA);

        Solucion una = planificar(escenario, 60, 7L);
        Solucion otra = planificar(escenario, 60, 7L);
        Solucion distinta = planificar(escenario, 60, 99L);

        afirmar(
                Math.abs(una.getCostoTotal() - otra.getCostoTotal()) < 1e-9,
                "Dos corridas con la misma semilla dieron costos distintos: "
                        + una.getCostoTotal() + " y " + otra.getCostoTotal() + "."
        );
        afirmar(
                una.getCantidadRutas() == otra.getCantidadRutas(),
                "Dos corridas con la misma semilla dieron distinta cantidad de viajes."
        );
        afirmar(
                distinta.getCostoTotal() > 0,
                "La corrida con otra semilla no produjo ningún plan."
        );
    }

    /**
     * Más presupuesto no puede empeorar el resultado.
     *
     * Es la propiedad que distingue a una búsqueda que conserva lo mejor encontrado de una que
     * solo reconstruye: si ALNS devolviera a veces algo peor con más iteraciones, significaría
     * que perdió el récord por el camino.
     */
    private static void pruebaMasIteracionesNoEmpeoran() {
        Escenario escenario = Escenario.mediano(AHORA);
        Parametros parametros = Parametros.constructor(1, 0.30, 3L).construir();
        Evaluador evaluador = new Evaluador(escenario.distancias());

        double conPocas = evaluador.objetivo(
                planificar(escenario, 20, 3L), escenario.estado(), parametros);
        double conMuchas = evaluador.objetivo(
                planificar(escenario, 200, 3L), escenario.estado(), parametros);

        afirmar(
                conMuchas <= conPocas + 1e-9,
                "Con más iteraciones el objetivo empeoró: " + conPocas + " -> " + conMuchas + "."
        );
    }

    /** Con una calle cerrada en medio, el plan sigue siendo factible y no la atraviesa. */
    private static void pruebaRespetaBloqueos() {
        Bloqueo muro = Bloqueo.dePoligonal(
                PERIODO.atDay(8).atTime(6, 0),
                PERIODO.atDay(8).atTime(20, 0),
                List.of(new Ubicacion(25, 17), new Ubicacion(29, 17))
        );
        MapaBloqueos mapa = new MapaBloqueos(List.of(muro));
        Escenario escenario = Escenario.mediano(AHORA)
                .conDistancias(new EnrutadorBloqueos(mapa));

        Solucion solucion = planificar(escenario, 60, 13L);
        Evaluador evaluador = new Evaluador(escenario.distancias());

        afirmar(
                evaluador.asignacionFactible(solucion, escenario.estado()),
                "Con bloqueos, el plan de ALNS dejó de ser factible."
        );
    }

    /**
     * Una unidad interrumpida a mitad de viaje llega con producto encima y ALNS tiene que
     * repartirlo desde donde está, sin mandarla de vuelta a un almacén.
     */
    private static void pruebaUnidadQueYaLlevaCarga() {
        Vehiculo conCarga = Escenario
                .unidad(TipoVehiculo.AUTO, 1, new Ubicacion(40, 30))
                .conCarga(6);

        Escenario escenario = Escenario.pequeno(AHORA)
                .conVehiculos(List.of(conCarga))
                .conPedidos(List.of(
                        Escenario.pedido("P-CERCA", 41, 30, 4, AHORA, 12),
                        Escenario.pedido("P-LEJOS", 42, 31, 2, AHORA, 12)
                ));

        Solucion solucion = planificar(escenario, 40, 17L);

        afirmar(
                solucion.getPedidosNoAsignados().isEmpty(),
                "Con seis productos a bordo deberia repartir los seis y quedaron "
                        + solucion.getPedidosNoAsignados().size() + " sin asignar."
        );
        for (Ruta viaje : solucion.getRutas()) {
            afirmar(
                    viaje.saleYaCargado(),
                    "Deberia repartir lo que ya lleva, sin estrenar un viaje desde un almacen."
            );
        }
    }

    /**
     * ALNS no puede heredar la solución de GRASP.
     *
     * El caso pide comparar metaheurísticas, y si una arrancara del resultado de otra la
     * comparación mediría un algoritmo y su fase de mejora. Se comprueba de la única forma
     * observable: los dos planes tienen que diferir.
     */
    private static void pruebaEsIndependienteDeGrasp() {
        Escenario escenario = Escenario.mediano(AHORA);
        Parametros parametros = Parametros.constructor(8, 0.30, 23L).construir();

        Solucion deGrasp = new Grasp(escenario.distancias())
                .planificar(escenario.estado(), parametros);
        Solucion deAlns = planificar(escenario, 150, 23L);

        afirmar(
                !deGrasp.getAlgoritmo().equals(deAlns.getAlgoritmo()),
                "Los dos planes se identifican con el mismo algoritmo."
        );
        afirmar(
                Math.abs(deGrasp.getCostoTotal() - deAlns.getCostoTotal()) > 1e-9
                        || deGrasp.getCantidadRutas() != deAlns.getCantidadRutas(),
                "ALNS devolvió un plan idéntico al de GRASP; puede estar partiendo de él."
        );
    }

    private static Solucion planificar(Escenario escenario, int iteraciones, long semilla) {
        BusquedaAlns alns = new BusquedaAlns(
                new Evaluador(escenario.distancias()),
                ParametrosAlns.porDefecto().conIteraciones(iteraciones));
        return alns.planificar(
                escenario.estado(), Parametros.constructor(1, 0.30, semilla).construir());
    }

    private static void afirmar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
