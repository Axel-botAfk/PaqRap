package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Función objetivo y verificación de restricciones compartidas por GRASP y por la
 * búsqueda tabú.
 *
 * Se extrae de GRASP para que la fase de mejora puntúe exactamente con las mismas reglas
 * con las que se construyó la solución: si ambas fases midieran distinto, la búsqueda
 * podría "mejorar" un plan que la construcción considera inviable.
 */
public final class Evaluador {
    public static final double HORAS_ATENCION_POR_DESTINATARIO = 1.0;

    private final CalculadorDistancia calculadorDistancia;

    public Evaluador(CalculadorDistancia calculadorDistancia) {
        this.calculadorDistancia = Objects.requireNonNull(calculadorDistancia);
    }

    /**
     * Recorre la secuencia desde el almacén de salida acumulando viaje y atención.
     * Si una entrega supera su plazo la secuencia se declara infactible en ese punto.
     */
    public MetricasRuta calcularMetricas(
            Almacen almacen,
            Vehiculo vehiculo,
            List<Pedido> pedidos,
            LocalDateTime horaInicio
    ) {
        Ubicacion ubicacionActual = almacen.getUbicacion();
        LocalDateTime reloj = horaInicio;

        double distanciaTotal = 0.0;
        Map<String, LocalDateTime> horasEntrega = new HashMap<>();

        for (Pedido pedido : pedidos) {
            double distanciaTramo = calculadorDistancia.calcularKm(
                    ubicacionActual,
                    pedido.getDestino()
            );
            distanciaTotal += distanciaTramo;

            double horasViaje = distanciaTramo / vehiculo.getVelocidadKmH();
            reloj = sumarHoras(reloj, horasViaje);

            // El caso establece 1 hora de atención por destinatario.
            reloj = sumarHoras(reloj, HORAS_ATENCION_POR_DESTINATARIO);
            horasEntrega.put(pedido.getId(), reloj);

            if (reloj.isAfter(pedido.getFechaLimite())) {
                return new MetricasRuta(
                        false,
                        distanciaTotal,
                        distanciaTotal * vehiculo.getCostoPorKm(),
                        horasEntre(horaInicio, reloj),
                        horasEntrega
                );
            }

            ubicacionActual = pedido.getDestino();
        }

        double costo = distanciaTotal * vehiculo.getCostoPorKm();
        return new MetricasRuta(
                true,
                distanciaTotal,
                costo,
                horasEntre(horaInicio, reloj),
                horasEntrega
        );
    }

    /**
     * Evalúa una ruta completa incluyendo las restricciones de asignación de recursos
     * (capacidad, disponibilidad y ubicación del vehículo).
     *
     * A diferencia de {@link #calcularMetricas}, aquí una distancia no registrada no
     * interrumpe la búsqueda: el vecino simplemente se descarta por infactible.
     */
    public MetricasRuta evaluarRuta(Ruta ruta, LocalDateTime horaInicio) {
        Vehiculo vehiculo = ruta.getVehiculo();

        if (vehiculo.getEstado() != EstadoVehiculo.DISPONIBLE) {
            return MetricasRuta.infactible();
        }
        // Decisión 7 del proyecto: la unidad debe encontrarse en el almacén de salida.
        if (!vehiculo.getUbicacion().equals(ruta.getAlmacen().getUbicacion())) {
            return MetricasRuta.infactible();
        }
        if (ruta.getCargaTotal() > vehiculo.getCapacidad()) {
            return MetricasRuta.infactible();
        }

        try {
            return calcularMetricas(
                    ruta.getAlmacen(),
                    vehiculo,
                    ruta.getPedidos(),
                    horaInicio
            );
        } catch (RuntimeException distanciaDesconocida) {
            return MetricasRuta.infactible();
        }
    }

    /**
     * Restricciones que no se pueden verificar mirando una sola ruta:
     * una unidad no puede cubrir dos rutas y el stock de cada almacén intermedio es finito.
     * Las rutas vacías se ignoran porque se depuran al cerrar la búsqueda.
     */
    public boolean asignacionFactible(Solucion solucion, EstadoOperacion estado) {
        Set<String> vehiculosUsados = new HashSet<>();
        Map<String, Integer> demandaPorAlmacen = new HashMap<>();

        for (Ruta ruta : solucion.getRutas()) {
            if (ruta.estaVacia()) {
                continue;
            }
            if (!vehiculosUsados.add(ruta.getVehiculo().getId())) {
                return false;
            }
            if (!ruta.getAlmacen().esInventarioInfinito()) {
                demandaPorAlmacen.merge(ruta.getAlmacen().getId(), ruta.getCargaTotal(), Integer::sum);
            }
        }

        for (Almacen almacen : estado.getAlmacenes()) {
            int demanda = demandaPorAlmacen.getOrDefault(almacen.getId(), 0);
            if (!almacen.esInventarioInfinito() && demanda > almacen.getStockActual()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Valor a minimizar: costo de operación más una penalidad por cada pedido sin atender.
     * La penalidad es alta a propósito para reproducir la política de GRASP —primero cubrir
     * pedidos, después abaratar—, pero permite que la búsqueda compare planes que difieren
     * en cobertura y costo a la vez. Devuelve infinito si el plan viola alguna restricción.
     */
    public double objetivo(Solucion solucion, EstadoOperacion estado, Parametros parametros) {
        if (!asignacionFactible(solucion, estado)) {
            return Double.POSITIVE_INFINITY;
        }

        double costo = 0.0;
        for (Ruta ruta : solucion.getRutas()) {
            if (ruta.estaVacia()) {
                continue;
            }
            MetricasRuta metricas = evaluarRuta(ruta, estado.getReloj());
            if (!metricas.factible()) {
                return Double.POSITIVE_INFINITY;
            }
            costo += metricas.costoTotal();
        }

        return costo + parametros.getPenalidadNoAsignado() * solucion.getCantidadPedidosNoAsignados();
    }

    /** Vuelca en cada ruta las métricas vigentes tras los movimientos aplicados. */
    public void sincronizarMetricas(Solucion solucion, LocalDateTime horaInicio) {
        for (Ruta ruta : solucion.getRutas()) {
            MetricasRuta metricas = evaluarRuta(ruta, horaInicio);
            ruta.actualizarMetricas(
                    metricas.distanciaTotalKm(),
                    metricas.costoTotal(),
                    metricas.duracionHoras()
            );
        }
    }

    /**
     * Distancia utilizable para acotar vecindarios. Devuelve infinito cuando el par no está
     * registrado, de modo que el filtro de proximidad nunca interrumpa la búsqueda.
     */
    public double distanciaSegura(Ubicacion origen, Ubicacion destino) {
        try {
            return calculadorDistancia.calcularKm(origen, destino);
        } catch (RuntimeException sinDato) {
            return Double.POSITIVE_INFINITY;
        }
    }

    public static LocalDateTime sumarHoras(LocalDateTime fecha, double horas) {
        long nanos = Math.round(horas * 3_600_000_000_000L);
        return fecha.plusNanos(nanos);
    }

    public static double horasEntre(LocalDateTime inicio, LocalDateTime fin) {
        return Duration.between(inicio, fin).toNanos() / 3_600_000_000_000.0;
    }
}
