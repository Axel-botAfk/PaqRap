package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Turnos;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.paqrap.planificador.ruteo.CalculadorDistancia;

/**
 * Función objetivo y verificación de restricciones compartidas por GRASP y por la
 * búsqueda tabú.
 *
 * Se extrae de GRASP para que la fase de mejora puntúe exactamente con las mismas reglas
 * con las que se construyó la solución: si ambas fases midieran distinto, la búsqueda
 * podría mejorar un plan que la construcción considera inviable.
 *
 * <h2>Viajes encadenados</h2>
 *
 * Un viaje no se evalúa aislado. Una unidad encadena varios a lo largo del horizonte: sale de
 * donde esté, va al almacén donde carga, entrega, y desde el último cliente se dirige al
 * almacén del viaje siguiente. Los traslados sin carga —hasta el primer almacén y entre el
 * último cliente y el almacén siguiente— cuentan en distancia, tiempo y costo, porque la
 * unidad realmente los recorre.
 */
public final class Evaluador {
    /**
     * Una hora por entrega, sin importar la cantidad de producto que se deja. Ocupa a la
     * unidad pero no cuenta contra el plazo comprometido con el cliente.
     */
    public static final double HORAS_ACONDICIONAMIENTO_POR_ENTREGA = 1.0;

    private final CalculadorDistancia calculadorDistancia;

    public Evaluador(CalculadorDistancia calculadorDistancia) {
        this.calculadorDistancia = Objects.requireNonNull(calculadorDistancia);
    }

    /**
     * Hasta cuándo se le puede entregar de verdad a un pedido.
     *
     * Es el primero de dos topes: el plazo comprometido con el cliente y el instante en que la
     * municipalidad cierra la esquina del destino. Medir la urgencia solo contra el plazo deja
     * pasar un caso que sí ocurre en los datos del caso: un pedido de ocho horas cuyo destino
     * queda cerrado a los cuarenta minutos de llegar. Contra el plazo parece cómodo y se
     * posterga; contra su ventana de acceso es el más apremiante de la cola.
     *
     * El tope por acceso no reemplaza a la verificación de factibilidad, que sigue haciéndola
     * el enrutador al medir cada tramo; solo ordena a quién se atiende primero.
     */
    public LocalDateTime limiteEfectivo(Pedido pedido, LocalDateTime desde) {
        LocalDateTime plazo = pedido.getFechaLimite();
        LocalDateTime cierre = calculadorDistancia.finDelAccesoA(pedido.getDestino(), desde, plazo);
        return cierre == null || cierre.isAfter(plazo) ? plazo : cierre;
    }

    /**
     * Evalúa un viaje: traslado hasta el almacén de carga y luego la secuencia de entregas.
     *
     * El plazo se verifica contra la hora de <b>llegada</b> al cliente; la hora de
     * acondicionamiento se suma después, porque retrasa la siguiente entrega de la unidad pero
     * no forma parte del plazo comprometido. El tiempo de carga en el almacén es despreciable.
     *
     * Cada avance de reloj pasa por {@link Turnos#avanzar}, que intercala el refrigerio del
     * conductor cuando el tramo cruza un cambio de turno. El vehículo no para por el relevo
     * —el conductor entrante lo alcanza donde esté— pero sí la hora que el conductor come.
     *
     * @param posicionInicial dónde está la unidad antes de este viaje.
     * @param horaInicio      desde cuándo está libre para iniciarlo.
     */
    public MetricasRuta calcularMetricas(
            Ubicacion posicionInicial,
            LocalDateTime horaInicio,
            Almacen almacen,
            Vehiculo vehiculo,
            List<Pedido> pedidos
    ) {
        return calcularMetricas(posicionInicial, horaInicio, almacen, vehiculo, pedidos, false);
    }

    /**
     * @param saleYaCargado si la unidad lleva el producto encima: entonces no pasa por el
     *                      almacén y arranca desde donde está, y la capacidad que la limita es
     *                      lo que trae a bordo y no la del vehículo vacío.
     */
    public MetricasRuta calcularMetricas(
            Ubicacion posicionInicial,
            LocalDateTime horaInicio,
            Almacen almacen,
            Vehiculo vehiculo,
            List<Pedido> pedidos,
            boolean saleYaCargado
    ) {
        int carga = 0;
        for (Pedido pedido : pedidos) {
            carga += pedido.getCantidad();
        }
        int tope = saleYaCargado ? vehiculo.getCargaABordo() : vehiculo.getCapacidad();
        if (carga > tope) {
            return MetricasRuta.infactible();
        }
        if (pedidos.isEmpty()) {
            return MetricasRuta.sinEntregas(posicionInicial, horaInicio);
        }

        double velocidad = vehiculo.getVelocidadKmH();
        double distanciaTotal = 0.0;
        LocalDateTime reloj = horaInicio;

        // Traslado sin carga hasta el almacén donde la unidad se abastece. Si ya sale cargada,
        // no hay nada que recoger y el viaje empieza donde esté.
        if (!saleYaCargado && !posicionInicial.equals(almacen.getUbicacion())) {
            double hastaElAlmacen = calculadorDistancia.calcularKm(
                    posicionInicial, almacen.getUbicacion(), reloj, velocidad);
            if (Double.isInfinite(hastaElAlmacen)) {
                return MetricasRuta.infactible();
            }
            distanciaTotal += hastaElAlmacen;
            reloj = Turnos.avanzar(reloj, hastaElAlmacen / velocidad);
        }

        LocalDateTime horaCarga = reloj;
        Ubicacion ubicacionActual = saleYaCargado ? posicionInicial : almacen.getUbicacion();
        Map<String, LocalDateTime> horasLlegada = new HashMap<>();

        for (Pedido pedido : pedidos) {
            double distanciaTramo = calculadorDistancia.calcularKm(
                    ubicacionActual, pedido.getDestino(), reloj, velocidad);
            if (Double.isInfinite(distanciaTramo)) {
                // No hay camino hasta el destino sin atravesar un tramo cerrado.
                return MetricasRuta.infactible();
            }
            distanciaTotal += distanciaTramo;

            LocalDateTime llegada = Turnos.avanzar(reloj, distanciaTramo / velocidad);
            horasLlegada.put(pedido.getId(), llegada);

            if (llegada.isAfter(pedido.getFechaLimite())) {
                return MetricasRuta.infactible();
            }

            reloj = Turnos.avanzar(llegada, HORAS_ACONDICIONAMIENTO_POR_ENTREGA);
            ubicacionActual = pedido.getDestino();
        }

        return new MetricasRuta(
                true,
                distanciaTotal,
                distanciaTotal * vehiculo.getCostoPorKm(),
                horasEntre(horaInicio, reloj),
                horaCarga,
                reloj,
                ubicacionActual,
                horasLlegada
        );
    }

    /**
     * Evalúa en orden todos los viajes de una unidad, arrastrando dónde queda y a qué hora
     * después de cada uno. La lista devuelta viene alineada con la de viajes recibida.
     */
    public List<MetricasRuta> evaluarPrograma(
            Vehiculo vehiculo,
            List<Ruta> viajes,
            LocalDateTime horaInicio
    ) {
        List<MetricasRuta> metricas = new ArrayList<>(viajes.size());

        if (!vehiculo.getEstado().admiteAsignacion()) {
            for (int i = 0; i < viajes.size(); i++) {
                metricas.add(MetricasRuta.infactible());
            }
            return metricas;
        }

        Ubicacion posicion = vehiculo.getUbicacion();
        LocalDateTime reloj = libreDesde(vehiculo, horaInicio);

        for (Ruta viaje : viajes) {
            MetricasRuta medida = calcularMetricas(
                    posicion, reloj, viaje.getAlmacen(), vehiculo, viaje.getPedidos(),
                    viaje.saleYaCargado());
            metricas.add(medida);
            if (!medida.factible()) {
                while (metricas.size() < viajes.size()) {
                    metricas.add(MetricasRuta.infactible());
                }
                return metricas;
            }
            posicion = medida.posicionFinal();
            reloj = medida.horaFin();
        }

        return metricas;
    }

    /** Un solo viaje, partiendo de donde la unidad se encuentra. */
    public MetricasRuta evaluarRuta(Ruta viaje, LocalDateTime horaInicio) {
        Vehiculo vehiculo = viaje.getVehiculo();
        if (!vehiculo.getEstado().admiteAsignacion()) {
            return MetricasRuta.infactible();
        }
        return calcularMetricas(
                vehiculo.getUbicacion(), libreDesde(vehiculo, horaInicio),
                viaje.getAlmacen(), vehiculo, viaje.getPedidos(), viaje.saleYaCargado());
    }

    /**
     * Evalúa el plan completo: encadena los viajes de cada unidad y descuenta del inventario
     * lo que cada uno carga, en el periodo de reposición que le corresponde.
     *
     * Una unidad no puede cargar en un almacén que no tiene producto suficiente en ese momento,
     * que es la regla de "no regresar a un almacén sin stock".
     */
    public ResultadoPlan evaluarPlan(Solucion solucion, EstadoOperacion estado) {
        List<Ruta> viajes = solucion.getRutas();
        List<MetricasRuta> metricas = new ArrayList<>(Collections.nCopies(viajes.size(), null));
        Inventario inventario = new Inventario(estado.getAlmacenes(), estado.getReloj());

        boolean factible = true;
        double costo = 0.0;

        for (Map.Entry<String, List<Integer>> programa : indicesPorVehiculo(viajes).entrySet()) {
            List<Integer> indices = programa.getValue();
            Vehiculo vehiculo = viajes.get(indices.get(0)).getVehiculo();

            List<Ruta> susViajes = new ArrayList<>(indices.size());
            for (int indice : indices) {
                susViajes.add(viajes.get(indice));
            }

            List<MetricasRuta> medidas = evaluarPrograma(vehiculo, susViajes, estado.getReloj());
            for (int i = 0; i < indices.size(); i++) {
                MetricasRuta medida = medidas.get(i);
                metricas.set(indices.get(i), medida);

                if (!medida.factible()) {
                    factible = false;
                    continue;
                }
                costo += medida.costoTotal();

                Ruta viaje = susViajes.get(i);
                if (viaje.estaVacia() || viaje.saleYaCargado()) {
                    // Lo que la unidad ya lleva encima salió del almacén en una iteración
                    // anterior y ya se descontó entonces.
                    continue;
                }
                if (!inventario.tieneStock(viaje.getAlmacen(), medida.horaCarga(), viaje.getCargaTotal())) {
                    factible = false;
                    continue;
                }
                inventario.consumir(viaje.getAlmacen(), medida.horaCarga(), viaje.getCargaTotal());
            }
        }

        return factible
                ? new ResultadoPlan(true, costo, List.copyOf(metricas))
                : ResultadoPlan.infactible(metricas);
    }

    /**
     * Agrupa las posiciones de los viajes por unidad, conservando el orden de la solución:
     * ese orden es el que la unidad sigue durante el horizonte.
     */
    public static Map<String, List<Integer>> indicesPorVehiculo(List<Ruta> viajes) {
        Map<String, List<Integer>> porVehiculo = new LinkedHashMap<>();
        for (int i = 0; i < viajes.size(); i++) {
            porVehiculo
                    .computeIfAbsent(viajes.get(i).getVehiculo().getId(), id -> new ArrayList<>())
                    .add(i);
        }
        return porVehiculo;
    }

    public boolean asignacionFactible(Solucion solucion, EstadoOperacion estado) {
        return evaluarPlan(solucion, estado).factible();
    }

    /**
     * Valor a minimizar: entregar lo máximo posible al menor costo posible.
     *
     * <pre>
     *   objetivo = costo de operación + penalidadNoAsignado x pedidos que nadie atiende
     * </pre>
     *
     * El orden entre los dos criterios no es negociable: primero cubrir, después abaratar. Entre
     * dos planes que atienden a los mismos clientes gana siempre el más barato, y ningún ahorro
     * compra dejar un pedido afuera.
     *
     * La penalidad funciona como orden lexicográfico y no como un canje real porque domina por
     * un orden de magnitud lo que se juega en el margen: sumar una entrega más a un plan cuesta
     * unos cientos de soles —el desvío hasta el cliente y lo que retrasa al resto del viaje—,
     * contra los 10 000 de dejarla sin atender. Lo que importa es esa comparación marginal, no
     * el costo total del plan.
     *
     * Devuelve infinito si el plan viola alguna restricción, de modo que la búsqueda nunca puede
     * elegir un plan inviable por barato que parezca.
     */
    public double objetivo(Solucion solucion, EstadoOperacion estado, Parametros parametros) {
        ResultadoPlan resultado = evaluarPlan(solucion, estado);
        if (!resultado.factible()) {
            return Double.POSITIVE_INFINITY;
        }
        return resultado.costoOperacion()
                + parametros.getPenalidadNoAsignado() * solucion.getCantidadPedidosNoAsignados();
    }

    /** Vuelca en cada viaje las métricas vigentes tras los movimientos aplicados. */
    public void sincronizarMetricas(Solucion solucion, EstadoOperacion estado) {
        List<MetricasRuta> metricas = evaluarPlan(solucion, estado).metricasPorRuta();
        List<Ruta> viajes = solucion.getRutas();
        for (int i = 0; i < viajes.size(); i++) {
            MetricasRuta medida = metricas.get(i);
            viajes.get(i).actualizarMetricas(
                    medida.distanciaTotalKm(),
                    medida.costoTotal(),
                    medida.duracionHoras()
            );
        }
    }

    /**
     * Una unidad que venía ocupada no arranca en el instante de planificación sino cuando
     * termina lo que ya tenía comprometido.
     */
    public static LocalDateTime libreDesde(Vehiculo vehiculo, LocalDateTime horaInicio) {
        LocalDateTime disponible = vehiculo.getDisponibleDesde();
        return disponible == null || disponible.isBefore(horaInicio) ? horaInicio : disponible;
    }

    public static LocalDateTime sumarHoras(LocalDateTime fecha, double horas) {
        long nanos = Math.round(horas * 3_600_000_000_000L);
        return fecha.plusNanos(nanos);
    }

    public static double horasEntre(LocalDateTime inicio, LocalDateTime fin) {
        return Duration.between(inicio, fin).toNanos() / 3_600_000_000_000.0;
    }
}
