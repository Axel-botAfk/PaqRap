package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoAlmacen;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;

import java.time.Duration;
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
 * Implementación de GRASP para el componente planificador de PaqRap.
 *
 * Comprende:
 * - fase constructiva aleatorizada;
 * - inserciones factibles pedido/almacén/vehículo/ruta;
 * - capacidad, stock, vehículo disponible y plazo;
 * - costo incremental y holgura;
 * - LRC controlada por alfa;
 * - selección aleatoria reproducible por semilla;
 * - búsqueda local por reordenamiento de pedidos dentro de cada ruta;
 * - conservación de la mejor solución entre iteraciones;
 * - registro de pedidos no asignados.
 *
 * La búsqueda local usa un vecindario de reinserción intra-ruta: retira un
 * pedido y prueba todas sus posiciones posibles dentro de la misma ruta.
 * Solo acepta movimientos factibles que reduzcen el costo de la ruta.
 */
public final class Grasp implements Planificador {
    private static final double HORAS_ATENCION_POR_DESTINATARIO = 1.0;
    private static final double EPSILON = 1e-9;

    private final CalculadorDistancia calculadorDistancia;

    public Grasp(CalculadorDistancia calculadorDistancia) {
        this.calculadorDistancia = Objects.requireNonNull(calculadorDistancia);
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

            // Segunda fase de GRASP: mejorar la solución construida antes de
            // compararla con la mejor solución global de la ejecución.
            candidata = busquedaLocal(candidata, estado.getReloj());

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

                    MetricasRuta metricas = calcularMetricas(
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

                    // Para iniciar una ruta desde un almacén, la unidad debe encontrarse allí.
                    // La reubicación de vehículos corresponde a la lógica de operación/
                    // replanificación y no se resuelve dentro de esta construcción GRASP.
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
                    MetricasRuta metricas = calcularMetricas(
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

    private MetricasRuta calcularMetricas(
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
     * Fase de mejora de GRASP.
     *
     * Para cada ruta se explora un vecindario de reinserción intra-ruta:
     * se retira un pedido y se prueba colocarlo en cualquier otra posición.
     * Se aplica la mejor mejora factible encontrada y el proceso se repite
     * hasta alcanzar un óptimo local respecto de este vecindario.
     *
     * Esta operación no modifica almacén, vehículo, carga ni stock; solo el
     * orden de entrega. Cada secuencia candidata vuelve a validarse contra
     * los plazos mediante calcularMetricas(...).
     */
    private Solucion busquedaLocal(
            Solucion solucion,
            LocalDateTime horaPlanificacion
    ) {
        boolean huboMejora;

        do {
            huboMejora = false;

            for (Ruta ruta : solucion.getRutas()) {
                MejoraRuta mejora = buscarMejorReordenamiento(ruta, horaPlanificacion);

                if (mejora == null) {
                    continue;
                }

                ruta.reemplazarPedidos(mejora.secuencia());
                ruta.actualizarMetricas(
                        mejora.metricas().distanciaTotalKm(),
                        mejora.metricas().costoTotal(),
                        mejora.metricas().duracionHoras()
                );
                huboMejora = true;
            }
        } while (huboMejora);

        return solucion;
    }

    /**
     * Devuelve la mejor reinserción factible que reduzca el costo de la ruta.
     * Si ninguna reinserción mejora la ruta, devuelve null.
     */
    private MejoraRuta buscarMejorReordenamiento(
            Ruta ruta,
            LocalDateTime horaPlanificacion
    ) {
        List<Pedido> original = new ArrayList<>(ruta.getPedidos());
        if (original.size() < 2) {
            return null;
        }

        double mejorCosto = ruta.getCostoTotal();
        List<Pedido> mejorSecuencia = null;
        MetricasRuta mejoresMetricas = null;

        for (int origen = 0; origen < original.size(); origen++) {
            List<Pedido> sinPedido = new ArrayList<>(original);
            Pedido movido = sinPedido.remove(origen);

            for (int destino = 0; destino <= sinPedido.size(); destino++) {
                List<Pedido> candidata = new ArrayList<>(sinPedido);
                candidata.add(destino, movido);

                if (candidata.equals(original)) {
                    continue;
                }

                MetricasRuta metricas = calcularMetricas(
                        ruta.getAlmacen(),
                        ruta.getVehiculo(),
                        candidata,
                        horaPlanificacion
                );

                if (!metricas.factible()) {
                    continue;
                }

                if (metricas.costoTotal() < mejorCosto - EPSILON) {
                    mejorCosto = metricas.costoTotal();
                    mejorSecuencia = candidata;
                    mejoresMetricas = metricas;
                }
            }
        }

        if (mejorSecuencia == null) {
            return null;
        }

        return new MejoraRuta(mejorSecuencia, mejoresMetricas);
    }

    private double calcularHolguraHoras(Pedido pedido, LocalDateTime horaEntrega) {
        return horasEntre(horaEntrega, pedido.getFechaLimite());
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

    private static LocalDateTime sumarHoras(LocalDateTime fecha, double horas) {
        long nanos = Math.round(horas * 3_600_000_000_000L);
        return fecha.plusNanos(nanos);
    }

    private static double horasEntre(LocalDateTime inicio, LocalDateTime fin) {
        return Duration.between(inicio, fin).toNanos() / 3_600_000_000_000.0;
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

    private record MetricasRuta(
            boolean factible,
            double distanciaTotalKm,
            double costoTotal,
            double duracionHoras,
            Map<String, LocalDateTime> horasEntrega
    ) {
    }

    private record MejoraRuta(
            List<Pedido> secuencia,
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
