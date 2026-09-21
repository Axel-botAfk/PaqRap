package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Construcción por vecino más cercano, con un filtro de urgencia sobre los k candidatos.
 *
 * Es la solución de partida de la búsqueda tabú. Existe para que los dos algoritmos que se
 * comparan sean independientes: si la tabú arrancara del resultado de GRASP, la comparación no
 * mediría dos algoritmos sino uno y su fase de mejora, y la segunda ganaría por construcción.
 *
 * <h2>Cómo construye</h2>
 *
 * Recorre las unidades una por una —las más baratas por kilómetro primero, porque el objetivo
 * es el costo— y con cada una arma viajes hasta que no pueda más:
 *
 * <ol>
 *   <li>la unidad carga en el almacén con producto más cercano a donde quedó;</li>
 *   <li>desde el punto en que está, mira los {@code k} pedidos pendientes más cercanos que
 *       quepan en lo que le resta de capacidad, y de esos elige el de plazo más apretado;</li>
 *   <li>lo agrega si el programa completo sigue siendo factible, y repite;</li>
 *   <li>cuando ningún candidato entra, cierra el viaje y empieza otro.</li>
 * </ol>
 *
 * El filtro por cercanía es lo que la hace barata; el desempate por urgencia es lo que evita
 * que un vecino más cercano puro deje vencer los pedidos priorizados. Con {@code k=1} queda el
 * vecino más cercano clásico.
 *
 * Es determinista: no usa la semilla. La aleatoriedad de la exploración la aporta después la
 * búsqueda tabú.
 */
public final class ConstructorVecinoMasCercano implements Planificador {
    public static final String NOMBRE = "Vecino más cercano";
    public static final int VECINOS_POR_DEFECTO = 5;

    private final Evaluador evaluador;
    private final int vecinos;
    private final boolean priorizarCapacidad;

    public ConstructorVecinoMasCercano(CalculadorDistancia calculadorDistancia) {
        this(new Evaluador(calculadorDistancia), VECINOS_POR_DEFECTO);
    }

    public ConstructorVecinoMasCercano(Evaluador evaluador) {
        this(evaluador, VECINOS_POR_DEFECTO);
    }

    public ConstructorVecinoMasCercano(Evaluador evaluador, int vecinos) {
        this(evaluador, vecinos, false);
    }

    /**
     * @param priorizarCapacidad {@code false} —el valor por defecto— llena primero las unidades
     *                           más baratas por kilómetro; {@code true}, las de mayor capacidad.
     *                           Medido sobre la operación completa, lo primero rinde más: al
     *                           llenar antes las unidades chicas el trabajo se reparte entre toda
     *                           la flota, mientras que empezar por los autos concentra las
     *                           entregas en diez unidades y deja ociosas a las otras veintisiete.
     */
    public ConstructorVecinoMasCercano(Evaluador evaluador, int vecinos, boolean priorizarCapacidad) {
        if (vecinos <= 0) {
            throw new IllegalArgumentException("La cantidad de vecinos debe ser mayor que cero.");
        }
        this.evaluador = Objects.requireNonNull(evaluador);
        this.vecinos = vecinos;
        this.priorizarCapacidad = priorizarCapacidad;
    }

    @Override
    public Solucion planificar(EstadoOperacion estado, Parametros parametros) {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(parametros);

        Solucion solucion = new Solucion();
        solucion.setAlgoritmo(NOMBRE);

        List<Pedido> pendientes = new ArrayList<>();
        for (Pedido pedido : estado.getPedidos()) {
            if (!pedido.getFechaRegistro().isAfter(estado.getReloj())) {
                pendientes.add(pedido);
            }
        }

        Inventario inventario = new Inventario(estado.getAlmacenes(), estado.getReloj());

        for (Vehiculo unidad : unidadesPorCosto(estado)) {
            if (pendientes.isEmpty()) {
                break;
            }
            List<Ruta> programa = new ArrayList<>();
            while (armarUnViaje(unidad, programa, pendientes, estado, inventario)) {
                // Sigue mientras la unidad pueda encadenar otro viaje.
            }
            for (Ruta viaje : programa) {
                solucion.agregarRuta(viaje);
            }
        }

        solucion.agregarPedidosNoAsignados(pendientes);
        evaluador.sincronizarMetricas(solucion, estado);
        return solucion;
    }

    /**
     * Arma un viaje más para la unidad.
     *
     * @return {@code true} si logró cargar al menos una entrega; {@code false} cuando la unidad
     *         ya no puede hacer nada más y hay que pasar a la siguiente.
     */
    private boolean armarUnViaje(
            Vehiculo unidad,
            List<Ruta> programa,
            List<Pedido> pendientes,
            EstadoOperacion estado,
            Inventario inventario
    ) {
        if (pendientes.isEmpty()) {
            return false;
        }

        Situacion situacion = situacionTras(unidad, programa, estado);
        if (situacion == null) {
            return false;
        }

        Almacen almacen = almacenMasCercanoConStock(
                estado.getAlmacenes(), situacion.posicion(), situacion.instante(), inventario);
        if (almacen == null) {
            return false;
        }

        Ruta viaje = new Ruta("R-" + (programa.size() + 1) + "-" + unidad.getId(), almacen, unidad);
        programa.add(viaje);

        Ubicacion desde = almacen.getUbicacion();
        List<Pedido> cargados = new ArrayList<>();

        while (true) {
            Pedido elegido = siguienteEntrega(desde, unidad, viaje, pendientes);
            if (elegido == null) {
                break;
            }

            viaje.insertarPedido(viaje.getPedidos().size(), elegido);
            LocalDateTime horaCarga = horaDeCargaSiEsFactible(unidad, programa, estado);

            boolean cabe = horaCarga != null
                    && inventario.tieneStock(almacen, horaCarga, viaje.getCargaTotal());
            if (!cabe) {
                viaje.retirarPedido(viaje.getPedidos().size() - 1);
                break;
            }

            pendientes.remove(elegido);
            cargados.add(elegido);
            desde = elegido.getDestino();
        }

        if (cargados.isEmpty()) {
            programa.remove(programa.size() - 1);
            return false;
        }

        LocalDateTime horaCarga = horaDeCargaSiEsFactible(unidad, programa, estado);
        inventario.consumir(almacen, horaCarga, viaje.getCargaTotal());
        return true;
    }

    /**
     * De los pedidos pendientes que quepan en lo que resta de capacidad, los {@code k} más
     * cercanos al punto actual; de esos, el de plazo más apretado.
     */
    private Pedido siguienteEntrega(
            Ubicacion desde,
            Vehiculo unidad,
            Ruta viaje,
            List<Pedido> pendientes
    ) {
        int capacidadRestante = unidad.getCapacidad() - viaje.getCargaTotal();
        if (capacidadRestante <= 0) {
            return null;
        }

        List<Pedido> caben = new ArrayList<>();
        for (Pedido pedido : pendientes) {
            if (pedido.getCantidad() <= capacidadRestante) {
                caben.add(pedido);
            }
        }
        if (caben.isEmpty()) {
            return null;
        }

        caben.sort(Comparator.comparingDouble(
                pedido -> desde.distanciaManhattanKm(pedido.getDestino())));

        List<Pedido> cercanos = caben.subList(0, Math.min(vecinos, caben.size()));
        return cercanos.stream()
                .min(Comparator.comparing(Pedido::getFechaLimite))
                .orElse(null);
    }

    /** Hora en que la unidad cargaría el último viaje, o {@code null} si el programa no cierra. */
    private LocalDateTime horaDeCargaSiEsFactible(
            Vehiculo unidad,
            List<Ruta> programa,
            EstadoOperacion estado
    ) {
        List<MetricasRuta> metricas =
                evaluador.evaluarPrograma(unidad, programa, estado.getReloj());
        for (MetricasRuta medida : metricas) {
            if (!medida.factible()) {
                return null;
            }
        }
        return metricas.get(metricas.size() - 1).horaCarga();
    }

    /** Dónde queda la unidad y desde cuándo está libre después de los viajes ya armados. */
    private Situacion situacionTras(Vehiculo unidad, List<Ruta> programa, EstadoOperacion estado) {
        if (programa.isEmpty()) {
            return new Situacion(
                    unidad.getUbicacion(), Evaluador.libreDesde(unidad, estado.getReloj()));
        }

        List<MetricasRuta> metricas =
                evaluador.evaluarPrograma(unidad, programa, estado.getReloj());
        MetricasRuta ultima = metricas.get(metricas.size() - 1);
        return ultima.factible() ? new Situacion(ultima.posicionFinal(), ultima.horaFin()) : null;
    }

    private Almacen almacenMasCercanoConStock(
            List<Almacen> almacenes,
            Ubicacion desde,
            LocalDateTime instante,
            Inventario inventario
    ) {
        Almacen mejor = null;
        double menorDistancia = Double.POSITIVE_INFINITY;

        for (Almacen almacen : almacenes) {
            if (inventario.disponible(almacen, instante) <= 0) {
                continue;
            }
            double distancia = desde.distanciaManhattanKm(almacen.getUbicacion());
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                mejor = almacen;
            }
        }
        return mejor;
    }

    /**
     * Orden en que se van llenando las unidades.
     *
     * Por defecto se llenan primero las unidades más baratas por kilómetro. La alternativa
     * —empezar por las de mayor capacidad, que agrupan más entregas por viaje— se probó y rinde
     * peor sobre la operación completa: como cada unidad se llena hasta que no puede más, partir
     * por los autos concentra el trabajo en diez unidades y deja ociosas a las otras veintisiete,
     * y lo que escasea cuando la demanda aprieta son unidades trabajando en paralelo.
     */
    private List<Vehiculo> unidadesPorCosto(EstadoOperacion estado) {
        List<Vehiculo> disponibles = new ArrayList<>();
        for (Vehiculo unidad : estado.getVehiculos()) {
            if (unidad.getEstado().admiteAsignacion()) {
                disponibles.add(unidad);
            }
        }

        Comparator<Vehiculo> orden = priorizarCapacidad
                ? Comparator.comparingInt(Vehiculo::getCapacidad).reversed()
                        .thenComparingDouble(Vehiculo::getCostoPorKm)
                : Comparator.comparingDouble(Vehiculo::getCostoPorKm)
                        .thenComparing(Comparator.comparingInt(Vehiculo::getCapacidad).reversed());
        disponibles.sort(orden);
        return disponibles;
    }

    private record Situacion(Ubicacion posicion, LocalDateTime instante) {
    }
}
