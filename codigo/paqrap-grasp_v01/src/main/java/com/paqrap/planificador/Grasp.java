package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoAlmacen;
import com.paqrap.modelo.Vehiculo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Primera iteración funcional de GRASP para PaqRap.
 *
 * Alcance:
 * - fase constructiva;
 * - inserciones factibles pedido/almacén/vehículo/ruta;
 * - capacidad, stock, vehículo disponible y plazo;
 * - costo incremental;
 * - holgura;
 * - LRC controlada por alfa;
 * - selección aleatoria reproducible por semilla;
 * - pedidos no asignados.
 *
 * La fase de mejora vive en {@link com.paqrap.planificador.tabu.BusquedaTabu}, que parte
 * de la solución construida aquí. Aún NO se implementan bloqueos, fallas durante una ruta,
 * alimentación ni replanificación.
 */
public final class Grasp implements Planificador {
    public static final String NOMBRE = "GRASP";

    private final Evaluador evaluador;

    public Grasp(CalculadorDistancia calculadorDistancia) {
        this(new Evaluador(calculadorDistancia));
    }

    public Grasp(Evaluador evaluador) {
        this.evaluador = Objects.requireNonNull(evaluador);
    }

    public Evaluador getEvaluador() {
        return evaluador;
    }

    @Override
    public Solucion planificar(EstadoOperacion estado, Parametros parametros) {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(parametros);

        Random random = new Random(parametros.getSemilla());
        Solucion mejor = null;

        for (int iteracion = 0; iteracion < parametros.getMaxIteraciones(); iteracion++) {
            Solucion candidata = construirUnaSolucion(
                    estado.getReloj(),
                    estado.getPedidos(),
                    estado.getAlmacenes(),
                    estado.getVehiculos(),
                    parametros.getAlfa(),
                    random
            );

            if (mejor == null || esMejor(candidata, mejor)) {
                mejor = candidata;
            }
        }

        return mejor;
    }

    /**
     * Método de conveniencia para trabajar directamente con las colecciones.
     * Se mantiene la hora de planificación explícita porque los plazos dependen de ella.
     */
    public Solucion construir(
            LocalDateTime horaPlanificacion,
            List<Pedido> pedidos,
            List<Almacen> almacenes,
            List<Vehiculo> vehiculos,
            Parametros parametros
    ) {
        return planificar(
                new EstadoOperacion(horaPlanificacion, pedidos, almacenes, vehiculos),
                parametros
        );
    }

    private Solucion construirUnaSolucion(
            LocalDateTime horaPlanificacion,
            List<Pedido> pedidos,
            List<Almacen> almacenes,
            List<Vehiculo> vehiculos,
            double alfa,
            Random random
    ) {
        Solucion solucion = new Solucion();
        solucion.setAlgoritmo(NOMBRE);

        List<Pedido> pendientes = pedidos.stream()
                .filter(p -> !p.getFechaRegistro().isAfter(horaPlanificacion))
                .sorted(Comparator.comparing(Pedido::getFechaLimite))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        ContextoConstruccion contexto = new ContextoConstruccion(almacenes);

        while (!pendientes.isEmpty()) {
            List<Insercion> inserciones = generarInsercionesFactibles(
                    solucion,
                    pendientes,
                    almacenes,
                    vehiculos,
                    horaPlanificacion,
                    contexto
            );

            if (inserciones.isEmpty()) {
                solucion.agregarPedidosNoAsignados(pendientes);
                break;
            }

            inserciones.sort(
                    Comparator.comparingDouble(Insercion::holguraHoras)
                            .thenComparingDouble(Insercion::costoIncremental)
            );

            List<Insercion> lrc = construirListaRestringida(inserciones, alfa);
            Insercion elegida = lrc.get(random.nextInt(lrc.size()));

            aplicarInsercion(solucion, elegida, contexto);
            pendientes.remove(elegida.pedido());
        }

        return solucion;
    }

    private List<Insercion> generarInsercionesFactibles(
            Solucion solucion,
            List<Pedido> pendientes,
            List<Almacen> almacenes,
            List<Vehiculo> vehiculos,
            LocalDateTime horaPlanificacion,
            ContextoConstruccion contexto
    ) {
        List<Insercion> resultado = new ArrayList<>();

        for (Pedido pedido : pendientes) {
            // 1) Intentar insertar el pedido en rutas ya creadas.
            for (Ruta ruta : solucion.getRutas()) {
                if (!contexto.tieneStock(ruta.getAlmacen(), pedido.getCantidad())) {
                    continue;
                }
                if (ruta.getCargaTotal() + pedido.getCantidad() > ruta.getVehiculo().getCapacidad()) {
                    continue;
                }

                for (int posicion = 0; posicion <= ruta.getPedidos().size(); posicion++) {
                    List<Pedido> secuencia = new ArrayList<>(ruta.getPedidos());
                    secuencia.add(posicion, pedido);

                    MetricasRuta metricas = evaluador.calcularMetricas(
                            ruta.getAlmacen(),
                            ruta.getVehiculo(),
                            secuencia,
                            horaPlanificacion
                    );

                    if (!metricas.factible()) {
                        continue;
                    }

                    double costoIncremental = metricas.costoTotal() - ruta.getCostoTotal();
                    double holgura = calcularHolguraHoras(
                            pedido,
                            metricas.horasEntrega().get(pedido.getId())
                    );

                    resultado.add(new Insercion(
                            pedido,
                            ruta.getAlmacen(),
                            ruta.getVehiculo(),
                            ruta,
                            posicion,
                            costoIncremental,
                            holgura,
                            metricas
                    ));
                }
            }

            // 2) Crear una ruta nueva con un vehículo todavía no utilizado.
            for (Almacen almacen : almacenes) {
                if (!contexto.tieneStock(almacen, pedido.getCantidad())) {
                    continue;
                }

                for (Vehiculo vehiculo : vehiculos) {
                    if (vehiculo.getEstado() != EstadoVehiculo.DISPONIBLE) {
                        continue;
                    }

                    // Primera iteración: no se permite "teletransportar" una unidad.
                    // Para iniciar una ruta desde un almacén, la unidad debe encontrarse allí.
                    // La reubicación de vehículos podrá modelarse posteriormente como parte
                    // de la operación/replanificación.
                    if (!vehiculo.getUbicacion().equals(almacen.getUbicacion())) {
                        continue;
                    }

                    if (contexto.vehiculoYaAsignado(vehiculo)) {
                        continue;
                    }
                    if (pedido.getCantidad() > vehiculo.getCapacidad()) {
                        continue;
                    }

                    List<Pedido> secuencia = List.of(pedido);
                    MetricasRuta metricas = evaluador.calcularMetricas(
                            almacen,
                            vehiculo,
                            secuencia,
                            horaPlanificacion
                    );

                    if (!metricas.factible()) {
                        continue;
                    }

                    double holgura = calcularHolguraHoras(
                            pedido,
                            metricas.horasEntrega().get(pedido.getId())
                    );

                    resultado.add(new Insercion(
                            pedido,
                            almacen,
                            vehiculo,
                            null,
                            0,
                            metricas.costoTotal(),
                            holgura,
                            metricas
                    ));
                }
            }
        }

        return resultado;
    }

    private List<Insercion> construirListaRestringida(List<Insercion> ordenadas, double alfa) {
        if (ordenadas.isEmpty()) {
            return List.of();
        }

        // Decisión de diseño para esta primera iteración:
        // alfa=0 => comportamiento totalmente voraz (solo el mejor candidato).
        // alfa=1 => todos los candidatos pueden ser elegidos.
        int cantidad = 1 + (int) Math.floor(alfa * (ordenadas.size() - 1));
        return new ArrayList<>(ordenadas.subList(0, cantidad));
    }

    private void aplicarInsercion(
            Solucion solucion,
            Insercion insercion,
            ContextoConstruccion contexto
    ) {
        Ruta ruta;

        if (insercion.rutaExistente() == null) {
            ruta = new Ruta(
                    "R-" + (solucion.getRutas().size() + 1),
                    insercion.almacen(),
                    insercion.vehiculo()
            );
            solucion.agregarRuta(ruta);
            contexto.marcarVehiculoAsignado(insercion.vehiculo());
        } else {
            ruta = insercion.rutaExistente();
        }

        ruta.insertarPedido(insercion.posicion(), insercion.pedido());
        ruta.actualizarMetricas(
                insercion.metricas().distanciaTotalKm(),
                insercion.metricas().costoTotal(),
                insercion.metricas().duracionHoras()
        );

        contexto.consumirStock(insercion.almacen(), insercion.pedido().getCantidad());
    }

    private double calcularHolguraHoras(Pedido pedido, LocalDateTime horaEntrega) {
        return Evaluador.horasEntre(horaEntrega, pedido.getFechaLimite());
    }

    private boolean esMejor(Solucion candidata, Solucion actual) {
        // Política alineada con el caso: primero cumplir pedidos/plazos,
        // después minimizar costo.
        int noAsignadosCandidata = candidata.getPedidosNoAsignados().size();
        int noAsignadosActual = actual.getPedidosNoAsignados().size();

        if (noAsignadosCandidata != noAsignadosActual) {
            return noAsignadosCandidata < noAsignadosActual;
        }

        return candidata.getCostoTotal() < actual.getCostoTotal();
    }

    private record Insercion(
            Pedido pedido,
            Almacen almacen,
            Vehiculo vehiculo,
            Ruta rutaExistente,
            int posicion,
            double costoIncremental,
            double holguraHoras,
            MetricasRuta metricas
    ) {
    }

    /**
     * Estado interno de UNA construcción GRASP.
     * Evita alterar el stock original o los objetos de entrada cuando se realizan
     * varias iteraciones con la misma información.
     */
    private static final class ContextoConstruccion {
        private final Map<String, Integer> stockRestante = new HashMap<>();
        private final Set<String> vehiculosAsignados = new HashSet<>();

        private ContextoConstruccion(List<Almacen> almacenes) {
            for (Almacen almacen : almacenes) {
                if (almacen.getTipo() == TipoAlmacen.INTERMEDIO) {
                    stockRestante.put(almacen.getId(), almacen.getStockActual());
                }
            }
        }

        private boolean tieneStock(Almacen almacen, int cantidad) {
            if (almacen.esInventarioInfinito()) {
                return true;
            }
            return stockRestante.getOrDefault(almacen.getId(), 0) >= cantidad;
        }

        private void consumirStock(Almacen almacen, int cantidad) {
            if (almacen.esInventarioInfinito()) {
                return;
            }
            int actual = stockRestante.getOrDefault(almacen.getId(), 0);
            if (actual < cantidad) {
                throw new IllegalStateException("Stock insuficiente al aplicar inserción.");
            }
            stockRestante.put(almacen.getId(), actual - cantidad);
        }

        private boolean vehiculoYaAsignado(Vehiculo vehiculo) {
            return vehiculosAsignados.contains(vehiculo.getId());
        }

        private void marcarVehiculoAsignado(Vehiculo vehiculo) {
            vehiculosAsignados.add(vehiculo.getId());
        }
    }
}
