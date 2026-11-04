package com.paqrap.simulacion;

import com.paqrap.modelo.*;
import com.paqrap.planificador.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AveriasPermanenciaTest {
    @Test void mantieneNodoHastaPermanenciaYReingresaSegunTipo() {
        var inicio = LocalDateTime.of(2026, 9, 1, 8, 0);
        var nodo = new Ubicacion(5, 5);
        var central = Almacen.central("CENTRAL", new Ubicacion(35, 25));
        for (TipoAveria tipo : TipoAveria.values()) {
            var incidencia = new Averia("TA01", inicio.plusHours(1), tipo);
            Map<LocalDateTime, Vehiculo> fotos = new TreeMap<>();
            var simulador = new Simulador((estado, parametros) -> new Solucion(),
                    (a, b, salida, velocidad) -> Math.abs(a.x()-b.x()) + Math.abs(a.y()-b.y()),
                    List.of(central), new Parametros(1, .3, 42), Duration.ofMinutes(30), false)
                .conAverias(new PlanAverias(List.of(incidencia)))
                .observadoPor(new Observador() {
                    public void alPlanificar(LocalDateTime reloj, EstadoOperacion estado,
                            Solucion plan, MedicionDePlanificacion medicion, AvanceDeLaOperacion avance) { }
                    public void alAvanzar(LocalDateTime reloj, List<Vehiculo> unidades) {
                        fotos.put(reloj, unidades.get(0));
                    }
                });
            simulador.correr(inicio, Duration.ofDays(4), List.of(), List.of(
                    new Vehiculo("TA01", TipoVehiculo.AUTO, EstadoVehiculo.DISPONIBLE, nodo)));
            var durante = fotos.get(incidencia.finDeLaPermanencia().minusMinutes(30));
            assertNotNull(durante);
            assertEquals(nodo, durante.getUbicacion(), tipo.name());
            assertEquals(EstadoVehiculo.AVERIADO, durante.getEstado());
            var fin = fotos.get(incidencia.finDeLaPermanencia());
            assertEquals(tipo.regresaAlCentral() ? central.getUbicacion() : nodo, fin.getUbicacion());
            assertEquals(EstadoVehiculo.DISPONIBLE, fotos.get(incidencia.reingreso()).getEstado());
        }
    }
}
