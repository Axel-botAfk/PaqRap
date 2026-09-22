package com.paqrap.planificador.tabu;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.CalculadorDistancia;
import com.paqrap.planificador.ConstructorVecinoMasCercano;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Búsqueda tabú para el componente planificador de PaqRap.
 *
 * Es uno de los dos algoritmos que el caso pide comparar, y es independiente del otro: su
 * solución de partida la arma {@link ConstructorVecinoMasCercano}, no GRASP. Si arrancara del
 * resultado de GRASP, la comparación no mediría dos algoritmos sino uno y su fase de mejora.
 * El constructivo inicial se puede sustituir por cualquier otro {@link Planificador}, lo que
 * permite además correr la variante GRASP + tabú como tercer punto de comparación.
 *
 * Sobre esa solución inicial aplica movimientos que cubren los dos niveles de decisión del
 * caso:
 *
 * - viajes: trasladar un pedido de posición, intercambiar pedidos e invertir un tramo de la
 *   secuencia de entregas;
 * - asignaciones: cambiar la unidad que hace el viaje, cambiar el almacén donde carga, y sumar
 *   al plan pedidos que habían quedado sin asignar.
 *
 * Una unidad puede encadenar varios viajes, de modo que crear un viaje nuevo no exige una
 * unidad ociosa: se agrega al final del programa de la que convenga.
 *
 * En cada iteración se toma una muestra del vecindario, se descartan los movimientos cuyo
 * atributo está en la lista tabú —salvo que superen a la mejor solución conocida, criterio
 * de aspiración— y se acepta el mejor candidato aunque empeore el valor actual, lo que
 * permite salir de óptimos locales.
 */
public final class BusquedaTabu implements Planificador {
    public static final String NOMBRE = "Búsqueda Tabú";

    private static final double EPSILON = 1e-9;
    private static final int RUTAS_DESTINO_POR_PENDIENTE = 3;

    private final Evaluador evaluador;
    private final Planificador constructor;

    public BusquedaTabu(CalculadorDistancia calculadorDistancia) {
        this(new Evaluador(calculadorDistancia));
    }

    public BusquedaTabu(Evaluador evaluador) {
        this(evaluador, new ConstructorVecinoMasCercano(evaluador));
    }

    /** Variante con otro constructivo inicial, para experimentar con el punto de partida. */
    public BusquedaTabu(Evaluador evaluador, Planificador constructor) {
        this.evaluador = Objects.requireNonNull(evaluador);
        this.constructor = Objects.requireNonNull(constructor);
    }

    /** Solución de partida, útil para medir cuánto aporta la fase de mejora. */
    public Solucion construirSolucionInicial(EstadoOperacion estado, Parametros parametros) {
        return constructor.planificar(estado, parametros);
    }

    @Override
    public Solucion planificar(EstadoOperacion estado, Parametros parametros) {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(parametros);

        Solucion inicial = constructor.planificar(estado, parametros);
        return mejorar(inicial, estado, parametros);
    }

    /** Método de conveniencia para trabajar directamente con las colecciones del caso. */
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

    /**
     * Núcleo iterativo. Recibe un plan factible y devuelve el mejor encontrado; la solución
     * recibida no se modifica, de modo que la de partida sigue disponible para comparar.
     */
    public Solucion mejorar(Solucion inicial, EstadoOperacion estado, Parametros parametros) {
        Random aleatorio = new Random(parametros.getSemilla());
        ListaTabu listaTabu = new ListaTabu(parametros.getTenenciaTabu());

        Solucion actual = inicial.copiar();
        double valorActual = evaluador.objetivo(actual, estado, parametros);
        Solucion mejor = actual.copiar();
        double valorMejor = valorActual;

        long inicioMs = System.currentTimeMillis();
        int sinMejora = 0;
        int reinicios = 0;

        for (int iteracion = 1; iteracion <= parametros.getIteracionesTabu(); iteracion++) {
            if (System.currentTimeMillis() - inicioMs > parametros.getLimiteMilisegundos()) {
                break;
            }

            List<Movimiento> vecindario = generarVecindario(actual, estado, parametros, aleatorio);
            Movimiento elegido = null;
            double valorElegido = Double.POSITIVE_INFINITY;

            for (Movimiento movimiento : vecindario) {
                AplicadorMovimiento.Deshacer deshacer = AplicadorMovimiento.aplicar(actual, movimiento);
                double valorVecino = evaluador.objetivo(actual, estado, parametros);
                deshacer.ejecutar();

                if (Double.isInfinite(valorVecino)) {
                    continue;
                }

                boolean prohibido = listaTabu.esTabu(movimiento.atributo(), iteracion);
                boolean aspira = valorVecino < valorMejor - EPSILON;
                if (prohibido && !aspira) {
                    continue;
                }

                if (valorVecino < valorElegido) {
                    valorElegido = valorVecino;
                    elegido = movimiento;
                }
            }

            if (elegido == null) {
                break;
            }

            AplicadorMovimiento.aplicar(actual, elegido);
            listaTabu.registrar(elegido.atributo(), iteracion);
            valorActual = valorElegido;

            if (valorActual < valorMejor - EPSILON) {
                valorMejor = valorActual;
                mejor = actual.copiar();
                sinMejora = 0;
            } else if (++sinMejora >= parametros.getIteracionesSinMejora()) {
                // Intensificación: al estancarse se regresa a la mejor solución conocida y se
                // libera la memoria de corto plazo, de modo que la búsqueda explore otro camino
                // en lugar de detenerse con presupuesto de tiempo todavía disponible.
                if (++reinicios > parametros.getReiniciosTabu()) {
                    break;
                }
                actual = mejor.copiar();
                valorActual = valorMejor;
                listaTabu = new ListaTabu(parametros.getTenenciaTabu());
                sinMejora = 0;
            }
        }

        mejor.depurarRutasVacias();
        evaluador.sincronizarMetricas(mejor, estado);
        mejor.setAlgoritmo(NOMBRE);
        return mejor;
    }

    /**
     * Muestra aleatoria del vecindario. Enumerar todos los vecinos de una solución con decenas
     * de rutas es inviable por iteración, de modo que se explora una muestra de tamaño
     * configurable que combina los tipos de movimiento.
     *
     * Los movimientos que incorporan pedidos pendientes se generan aparte y siempre: reducir
     * la cantidad de pedidos sin atender domina la función objetivo, así que conviene que
     * estén presentes en cada iteración y no solo cuando el sorteo los favorezca.
     */
    private List<Movimiento> generarVecindario(
            Solucion solucion,
            EstadoOperacion estado,
            Parametros parametros,
            Random aleatorio
    ) {
        List<Movimiento> vecindario = new ArrayList<>();
        List<Vehiculo> unidades = unidadesDisponibles(estado);
        List<Almacen> almacenes = estado.getAlmacenes();

        agregarAsignacionesPendientes(vecindario, solucion, almacenes, unidades, aleatorio);

        List<int[]> ubicaciones = new ArrayList<>();
        List<Ruta> rutas = solucion.getRutas();
        for (int i = 0; i < rutas.size(); i++) {
            for (int p = 0; p < rutas.get(i).getPedidos().size(); p++) {
                ubicaciones.add(new int[]{i, p});
            }
        }
        if (ubicaciones.isEmpty()) {
            return vecindario;
        }

        double radio = parametros.getRadioVecindarioKm();

        for (int intento = 0; intento < parametros.getTamanoMuestraVecindario(); intento++) {
            int[] origen = ubicaciones.get(aleatorio.nextInt(ubicaciones.size()));
            Ruta rutaOrigen = rutas.get(origen[0]);
            Pedido pedidoOrigen = rutaOrigen.getPedidos().get(origen[1]);
            Ubicacion destinoOrigen = pedidoOrigen.getDestino();
            int sorteo = aleatorio.nextInt(10);

            if (sorteo < 4) {
                // Traslado: cambia el viaje o la posición de una entrega.
                if (aleatorio.nextInt(5) == 0 && !unidades.isEmpty()) {
                    if (rutaOrigen.getPedidos().size() < 2) {
                        continue;
                    }
                    Vehiculo vehiculo = unidades.get(aleatorio.nextInt(unidades.size()));
                    Almacen almacen = almacenes.get(aleatorio.nextInt(almacenes.size()));
                    vecindario.add(Movimiento.trasladarARutaNueva(
                            origen[0], origen[1], pedidoOrigen, vehiculo, almacen));
                } else {
                    int destino = aleatorio.nextInt(rutas.size());
                    if (destino != origen[0] && !rutaProxima(rutas.get(destino), destinoOrigen, radio)) {
                        continue;
                    }
                    int posicion = aleatorio.nextInt(rutas.get(destino).getPedidos().size() + 1);
                    if (destino == origen[0] && (posicion == origen[1] || posicion == origen[1] + 1)) {
                        continue;
                    }
                    vecindario.add(Movimiento.trasladar(
                            origen[0], origen[1], destino, posicion, pedidoOrigen));
                }
            } else if (sorteo < 7) {
                // Intercambio entre dos rutas distintas.
                int[] otro = ubicaciones.get(aleatorio.nextInt(ubicaciones.size()));
                if (otro[0] == origen[0]) {
                    continue;
                }
                Ubicacion destinoOtro = rutas.get(otro[0]).getPedidos().get(otro[1]).getDestino();
                if (!sonProximos(destinoOrigen, destinoOtro, radio)) {
                    continue;
                }
                vecindario.add(Movimiento.intercambiar(
                        origen[0], origen[1], otro[0], otro[1], pedidoOrigen));
            } else if (sorteo < 8) {
                // Reordenamiento interno (2-opt).
                int cantidad = rutaOrigen.getPedidos().size();
                if (cantidad < 2) {
                    continue;
                }
                int desde = aleatorio.nextInt(cantidad - 1);
                int hasta = desde + 1 + aleatorio.nextInt(cantidad - desde - 1);
                vecindario.add(Movimiento.invertir(origen[0], desde, hasta));
            } else if (sorteo < 9) {
                // Otra unidad se hace cargo del viaje.
                if (unidades.isEmpty()) {
                    continue;
                }
                Vehiculo candidato = unidades.get(aleatorio.nextInt(unidades.size()));
                if (candidato.getId().equals(rutaOrigen.getVehiculo().getId())) {
                    continue;
                }
                vecindario.add(Movimiento.cambiarVehiculo(origen[0], candidato));
            } else {
                // El viaje pasa a cargar en otro almacén; la unidad se traslada hasta allí.
                Almacen candidato = almacenes.get(aleatorio.nextInt(almacenes.size()));
                if (candidato.getId().equals(rutaOrigen.getAlmacen().getId())) {
                    continue;
                }
                vecindario.add(Movimiento.cambiarAlmacen(origen[0], candidato));
            }
        }

        return vecindario;
    }

    /** Intentos de incorporar al plan cada pedido que GRASP no logró asignar. */
    private void agregarAsignacionesPendientes(
            List<Movimiento> vecindario,
            Solucion solucion,
            List<Almacen> almacenes,
            List<Vehiculo> unidades,
            Random aleatorio
    ) {
        List<Pedido> pendientes = solucion.getPedidosNoAsignados();
        if (pendientes.isEmpty()) {
            return;
        }

        int cantidadRutas = solucion.getCantidadRutas();

        for (Pedido pedido : pendientes) {
            for (int intento = 0; intento < RUTAS_DESTINO_POR_PENDIENTE && cantidadRutas > 0; intento++) {
                int indiceRuta = aleatorio.nextInt(cantidadRutas);
                Ruta ruta = solucion.getRuta(indiceRuta);
                if (ruta.getCapacidadDisponible() < pedido.getCantidad()) {
                    continue;
                }
                int posicion = aleatorio.nextInt(ruta.getPedidos().size() + 1);
                vecindario.add(Movimiento.asignarPendiente(pedido, indiceRuta, posicion));
            }

            for (Vehiculo vehiculo : unidades) {
                if (pedido.getCantidad() > vehiculo.getCapacidad()) {
                    continue;
                }
                Almacen almacen = almacenes.get(aleatorio.nextInt(almacenes.size()));
                vecindario.add(Movimiento.asignarPendienteEnRutaNueva(pedido, vehiculo, almacen));
            }
        }
    }

    /**
     * Unidades que pueden recibir trabajo. Ya no se exige que estén ociosas: una unidad con
     * viajes asignados puede encadenar otro al final de su programa.
     */
    private List<Vehiculo> unidadesDisponibles(EstadoOperacion estado) {
        List<Vehiculo> disponibles = new ArrayList<>();
        for (Vehiculo vehiculo : estado.getVehiculos()) {
            if (vehiculo.getEstado().admiteAsignacion()) {
                disponibles.add(vehiculo);
            }
        }
        return disponibles;
    }

    /**
     * Vecindario granular: mover una entrega a una ruta que opera al otro extremo de la ciudad
     * casi nunca mejora el costo. El radio se mide en línea recta sobre la retícula, sin mirar
     * los bloqueos: es un filtro de cercanía para decidir qué vecinos vale la pena evaluar, y
     * la distancia sin bloqueos es una cota inferior de la real, de modo que nunca descarta un
     * vecino que sí estaba cerca. Con el radio por defecto (infinito) no se filtra nada.
     */
    private boolean sonProximos(Ubicacion a, Ubicacion b, double radio) {
        if (Double.isInfinite(radio)) {
            return true;
        }
        return a.distanciaManhattanKm(b) <= radio;
    }

    private boolean rutaProxima(Ruta ruta, Ubicacion ubicacion, double radio) {
        if (Double.isInfinite(radio)) {
            return true;
        }
        if (sonProximos(ruta.getAlmacen().getUbicacion(), ubicacion, radio)) {
            return true;
        }
        for (Pedido pedido : ruta.getPedidos()) {
            if (sonProximos(pedido.getDestino(), ubicacion, radio)) {
                return true;
            }
        }
        return false;
    }
}
