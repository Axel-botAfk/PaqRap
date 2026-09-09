package com.paqrap.planificador.tabu;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.CalculadorDistancia;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Grasp;
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
 * Parte de la solución construida por GRASP y la modifica mediante movimientos que cubren
 * los dos niveles de decisión del caso:
 *
 * - rutas: trasladar un pedido de posición, intercambiar pedidos e invertir un tramo de la
 *   secuencia de entregas;
 * - asignaciones: cambiar la unidad de una ruta, reasignar la ruta a otro almacén con una
 *   unidad disponible allí, y sumar al plan pedidos que habían quedado sin asignar.
 *
 * En cada iteración se toma una muestra del vecindario, se descartan los movimientos cuyo
 * atributo está en la lista tabú —salvo que superen a la mejor solución conocida, criterio
 * de aspiración— y se acepta el mejor candidato aunque empeore el valor actual, lo que
 * permite salir de óptimos locales.
 */
public final class BusquedaTabu implements Planificador {
    public static final String NOMBRE = "GRASP + Búsqueda Tabú";

    private static final double EPSILON = 1e-9;
    private static final int RUTAS_DESTINO_POR_PENDIENTE = 3;

    private final Evaluador evaluador;
    private final Grasp constructor;

    public BusquedaTabu(CalculadorDistancia calculadorDistancia) {
        this(new Evaluador(calculadorDistancia));
    }

    public BusquedaTabu(Evaluador evaluador) {
        this.evaluador = Objects.requireNonNull(evaluador);
        this.constructor = new Grasp(evaluador);
    }

    @Override
    public Solucion planificar(EstadoOperacion estado, Parametros parametros) {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(parametros);

        Solucion inicial = constructor.planificar(estado, parametros);
        return mejorar(inicial, estado, parametros);
    }

    /**
     * Método de conveniencia equivalente al de GRASP, para trabajar directamente con las
     * colecciones del caso.
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

    /**
     * Núcleo iterativo. Recibe un plan factible y devuelve el mejor encontrado; la solución
     * recibida no se modifica, de modo que el resultado de GRASP sigue disponible para comparar.
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
        evaluador.sincronizarMetricas(mejor, estado.getReloj());
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
        List<Vehiculo> vehiculosLibres = vehiculosLibres(solucion, estado);
        List<Almacen> almacenes = estado.getAlmacenes();

        agregarAsignacionesPendientes(vecindario, solucion, almacenes, vehiculosLibres, aleatorio);

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
                // Traslado: cambia la ruta o la posición de una entrega.
                if (aleatorio.nextInt(5) == 0 && !vehiculosLibres.isEmpty()) {
                    Vehiculo vehiculo = vehiculosLibres.get(aleatorio.nextInt(vehiculosLibres.size()));
                    Almacen almacen = almacenDe(vehiculo, almacenes);
                    if (almacen == null || rutaOrigen.getPedidos().size() < 2) {
                        continue;
                    }
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
                // Reasignación de unidad conservando el almacén de salida.
                Vehiculo candidato = elegirVehiculoEn(
                        vehiculosLibres, rutaOrigen.getAlmacen().getUbicacion(), aleatorio);
                if (candidato == null || candidato.getId().equals(rutaOrigen.getVehiculo().getId())) {
                    continue;
                }
                vecindario.add(Movimiento.cambiarVehiculo(origen[0], candidato));
            } else {
                // Reasignación de almacén: la ruta pasa a salir de otro almacén con una unidad
                // disponible allí.
                if (vehiculosLibres.isEmpty()) {
                    continue;
                }
                Vehiculo candidato = vehiculosLibres.get(aleatorio.nextInt(vehiculosLibres.size()));
                if (candidato.getUbicacion().equals(rutaOrigen.getAlmacen().getUbicacion())) {
                    continue;
                }
                Almacen almacen = almacenDe(candidato, almacenes);
                if (almacen == null) {
                    continue;
                }
                vecindario.add(Movimiento.cambiarAlmacen(origen[0], almacen, candidato));
            }
        }

        return vecindario;
    }

    /** Intentos de incorporar al plan cada pedido que GRASP no logró asignar. */
    private void agregarAsignacionesPendientes(
            List<Movimiento> vecindario,
            Solucion solucion,
            List<Almacen> almacenes,
            List<Vehiculo> vehiculosLibres,
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

            for (Vehiculo vehiculo : vehiculosLibres) {
                if (pedido.getCantidad() > vehiculo.getCapacidad()) {
                    continue;
                }
                Almacen almacen = almacenDe(vehiculo, almacenes);
                if (almacen == null) {
                    continue;
                }
                vecindario.add(Movimiento.asignarPendienteEnRutaNueva(pedido, vehiculo, almacen));
            }
        }
    }

    /** Unidades disponibles que ninguna ruta con entregas está usando. */
    private List<Vehiculo> vehiculosLibres(Solucion solucion, EstadoOperacion estado) {
        Set<String> ocupados = new HashSet<>();
        for (Ruta ruta : solucion.getRutas()) {
            if (!ruta.estaVacia()) {
                ocupados.add(ruta.getVehiculo().getId());
            }
        }

        List<Vehiculo> libres = new ArrayList<>();
        for (Vehiculo vehiculo : estado.getVehiculos()) {
            if (vehiculo.getEstado() == EstadoVehiculo.DISPONIBLE
                    && !ocupados.contains(vehiculo.getId())) {
                libres.add(vehiculo);
            }
        }
        return libres;
    }

    private Vehiculo elegirVehiculoEn(List<Vehiculo> candidatos, Ubicacion ubicacion, Random aleatorio) {
        List<Vehiculo> enUbicacion = new ArrayList<>();
        for (Vehiculo vehiculo : candidatos) {
            if (vehiculo.getUbicacion().equals(ubicacion)) {
                enUbicacion.add(vehiculo);
            }
        }
        if (enUbicacion.isEmpty()) {
            return null;
        }
        return enUbicacion.get(aleatorio.nextInt(enUbicacion.size()));
    }

    /** Almacén desde el cual la unidad puede iniciar una ruta (decisión 7 del proyecto). */
    private Almacen almacenDe(Vehiculo vehiculo, List<Almacen> almacenes) {
        for (Almacen almacen : almacenes) {
            if (almacen.getUbicacion().equals(vehiculo.getUbicacion())) {
                return almacen;
            }
        }
        return null;
    }

    /**
     * Vecindario granular: mover una entrega a una ruta que opera al otro extremo de la ciudad
     * casi nunca mejora el costo. Con el radio por defecto (infinito) no se filtra nada, porque
     * la matriz de distancias del modelo actual puede estar registrada de forma parcial.
     */
    private boolean sonProximos(Ubicacion a, Ubicacion b, double radio) {
        if (Double.isInfinite(radio)) {
            return true;
        }
        return evaluador.distanciaSegura(a, b) <= radio;
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
