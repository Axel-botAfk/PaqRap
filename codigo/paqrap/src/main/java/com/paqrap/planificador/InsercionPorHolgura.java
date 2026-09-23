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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.paqrap.planificador.ruteo.CalculadorDistancia;

/**
 * Construcción por inserción, atendiendo en cada paso al pedido que está más cerca de quedarse
 * sin forma de ser entregado.
 *
 * Es la solución de partida de la búsqueda tabú. Existe para que los dos algoritmos que se
 * comparan sean independientes: si la tabú arrancara del resultado de GRASP, la comparación no
 * mediría dos algoritmos sino uno y su fase de mejora, y la segunda ganaría por construcción.
 *
 * <h2>Qué se atiende primero</h2>
 *
 * No el pedido de plazo más corto, sino aquel cuya <b>mejor entrega posible llegaría más justa
 * a su límite efectivo</b>. Es la priorización por holgura del caso, medida sobre la mejor forma
 * real de atenderlo y contra el tope que de verdad manda.
 *
 * Ese tope no siempre es el plazo del cliente. Si la municipalidad cierra la esquina del destino
 * antes de que el plazo venza, el pedido se queda sin acceso mucho antes de quedarse sin tiempo,
 * y es entonces cuando hay que salir. Sobre los datos reales del caso se perdía exactamente así
 * una entrega: un pedido de ocho horas de plazo cuyo destino cerraba a los treinta y nueve
 * minutos de haber llegado. Contra el plazo era el menos urgente de la cola y se postergó; contra
 * su ventana de acceso era el primero. Lo calcula {@link Evaluador#limiteEfectivo}.
 *
 * La holgura además se recalcula a medida que se reparte: cada asignación ocupa unidades, las
 * alternativas que quedan llegan más tarde, y el pedido va subiendo en la fila hasta que le
 * toca.
 *
 * A igual holgura decide el <b>arrepentimiento</b>: cuánto se encarece atender el pedido si su
 * mejor unidad se la lleva otro. Un pedido que solo puede ir en una unidad pierde todo si la
 * cede, así que va antes que uno con varias alternativas parecidas.
 *
 * <h2>Cómo elige</h2>
 *
 * Por cada pedido y cada una de las {@code k} unidades candidatas —las más cercanas con
 * capacidad suficiente, tomando al menos una cuota de cada tipo— se evalúan dos alternativas
 * sobre el programa completo de la unidad: sumar la entrega al final de su último viaje, o
 * estrenar uno cargando en el almacén con producto más cercano. De ahí salen el mejor destino y
 * el mejor destino en otra unidad.
 *
 * Es determinista: no usa la semilla. La aleatoriedad de la exploración la aporta después la
 * búsqueda tabú.
 */
public final class InsercionPorHolgura implements Planificador {
    public static final String NOMBRE = "Inserción por holgura";

    /** Unidades que compiten por cada pedido. */
    public static final int UNIDADES_CANDIDATAS_POR_DEFECTO = 18;

    private final Evaluador evaluador;
    private final int unidadesCandidatas;

    public InsercionPorHolgura(CalculadorDistancia calculadorDistancia) {
        this(new Evaluador(calculadorDistancia), UNIDADES_CANDIDATAS_POR_DEFECTO);
    }

    public InsercionPorHolgura(Evaluador evaluador) {
        this(evaluador, UNIDADES_CANDIDATAS_POR_DEFECTO);
    }

    public InsercionPorHolgura(Evaluador evaluador, int unidadesCandidatas) {
        if (unidadesCandidatas <= 0) {
            throw new IllegalArgumentException("Las unidades candidatas deben ser más de cero.");
        }
        this.evaluador = Objects.requireNonNull(evaluador);
        this.unidadesCandidatas = unidadesCandidatas;
    }

    @Override
    public Solucion planificar(EstadoOperacion estado, Parametros parametros) {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(parametros);

        List<Pedido> pendientes = new ArrayList<>();
        for (Pedido pedido : estado.getPedidos()) {
            if (!pedido.getFechaRegistro().isAfter(estado.getReloj())) {
                pendientes.add(pedido);
            }
        }
        Reparto reparto = new Reparto(estado, evaluador, unidadesCandidatas);
        List<Pedido> sinAsignar = reparto.repartir(pendientes);

        Solucion solucion = new Solucion();
        solucion.setAlgoritmo(NOMBRE);
        reparto.volcarEn(solucion);
        solucion.agregarPedidosNoAsignados(sinAsignar);
        evaluador.sincronizarMetricas(solucion, estado);
        return solucion;
    }

    /** Programas en construcción, uno por unidad, con su costo y lo que retiran de cada almacén. */
    private static final class Reparto {
        private final EstadoOperacion estado;
        private final Evaluador evaluador;
        private final Inventario inventario;
        private final int unidadesCandidatas;

        private final Map<String, List<Ruta>> viajesPorUnidad = new LinkedHashMap<>();
        private final Map<String, Double> costoPorUnidad = new HashMap<>();
        private final Map<String, List<CargaEnAlmacen>> cargasPorUnidad = new HashMap<>();
        private final Map<String, Situacion> situacionPorUnidad = new HashMap<>();
        private final List<Vehiculo> disponibles = new ArrayList<>();

        /**
         * Límite efectivo de cada pedido, calculado una sola vez: depende del destino y del
         * reloj de la planificación, ninguno de los dos cambia mientras se reparte.
         */
        private final Map<String, LocalDateTime> limitePorPedido = new HashMap<>();

        private Reparto(EstadoOperacion estado, Evaluador evaluador, int unidadesCandidatas) {
            this.estado = estado;
            this.evaluador = evaluador;
            this.unidadesCandidatas = unidadesCandidatas;
            this.inventario = new Inventario(estado.getAlmacenes(), estado.getReloj());

            for (Vehiculo unidad : estado.getVehiculos()) {
                if (!unidad.getEstado().admiteAsignacion()) {
                    continue;
                }
                disponibles.add(unidad);
                viajesPorUnidad.put(unidad.getId(), viajeInicialDe(unidad, estado));
                costoPorUnidad.put(unidad.getId(), 0.0);
                situacionPorUnidad.put(unidad.getId(), new Situacion(
                        unidad.getUbicacion(),
                        Evaluador.libreDesde(unidad, estado.getReloj())));
            }
        }

        /**
         * Asigna los pedidos de a uno, siempre el más comprometido.
         *
         * Tras cada asignación solo se recalculan los pedidos cuyo mejor o segundo destino
         * estaba en la unidad que acaba de recibir trabajo: para los demás nada cambió, porque
         * esa unidad solo puede haberse encarecido. Ese recálculo es el que va acercando a los
         * pedidos postergados a su límite efectivo y los empuja al frente de la fila.
         *
         * @return los pedidos que ninguna unidad pudo atender.
         */
        /**
         * Programa con el que arranca una unidad.
         *
         * Si trae producto encima —porque la planificación anterior la cortó después de cargar—
         * se le siembra un viaje vacío que ya sale cargado. Las entregas que se le inserten
         * salen de lo que lleva, sin pasar por ningún almacén. Si no trae nada, empieza sin
         * viajes y el primero que reciba la mandará a cargar.
         */
        private List<Ruta> viajeInicialDe(Vehiculo unidad, EstadoOperacion estado) {
            List<Ruta> programa = new ArrayList<>();
            if (unidad.getCargaABordo() > 0) {
                programa.add(new Ruta(
                        "C-" + unidad.getId(), estado.getAlmacenes().get(0), unidad, true));
            }
            return programa;
        }

        private List<Pedido> repartir(List<Pedido> pendientes) {
            List<Pedido> enOrden = new ArrayList<>(pendientes);
            enOrden.sort(Comparator.comparing(this::limiteDe));

            Map<Pedido, Alternativas> alternativas = new LinkedHashMap<>();
            for (Pedido pedido : enOrden) {
                alternativas.put(pedido, evaluarAlternativas(pedido));
            }

            List<Pedido> sinAsignar = new ArrayList<>();

            while (!alternativas.isEmpty()) {
                Pedido elegido = elMasComprometido(alternativas);
                if (elegido == null) {
                    // A ninguno le queda destino factible; el resto no se puede atender.
                    sinAsignar.addAll(alternativas.keySet());
                    break;
                }

                Opcion destino = alternativas.remove(elegido).mejor();
                aplicar(destino);

                String unidadTocada = destino.unidad().getId();
                for (Map.Entry<Pedido, Alternativas> entrada : alternativas.entrySet()) {
                    if (entrada.getValue().involucra(unidadTocada)) {
                        entrada.setValue(evaluarAlternativas(entrada.getKey()));
                    }
                }
            }

            return sinAsignar;
        }

        /**
         * El pedido cuya mejor entrega posible llegaría más justa a su límite efectivo —lo
         * primero entre su plazo y el cierre de su esquina—. A igual holgura pasa primero el
         * que más pierde si cede su mejor unidad.
         */
        private Pedido elMasComprometido(Map<Pedido, Alternativas> alternativas) {
            Pedido elegido = null;
            Alternativas mejores = null;

            for (Map.Entry<Pedido, Alternativas> entrada : alternativas.entrySet()) {
                Alternativas opciones = entrada.getValue();
                if (opciones.mejor() == null) {
                    continue;
                }
                if (elegido == null || leGanaEnPrioridad(opciones, mejores)) {
                    elegido = entrada.getKey();
                    mejores = opciones;
                }
            }
            return elegido;
        }

        private boolean leGanaEnPrioridad(Alternativas candidata, Alternativas actual) {
            double holguraCandidata = candidata.holguraHoras();
            double holguraActual = actual.holguraHoras();
            if (holguraCandidata != holguraActual) {
                return holguraCandidata < holguraActual;
            }
            return candidata.arrepentimiento() > actual.arrepentimiento();
        }

        private LocalDateTime limiteDe(Pedido pedido) {
            return limitePorPedido.computeIfAbsent(
                    pedido.getId(), id -> evaluador.limiteEfectivo(pedido, estado.getReloj()));
        }

        /** Mejor destino del pedido y mejor destino en otra unidad. */
        private Alternativas evaluarAlternativas(Pedido pedido) {
            Opcion mejor = null;
            Opcion segunda = null;

            for (Vehiculo unidad : candidatas(pedido)) {
                Opcion enSuUltimoViaje = evaluar(unidad, pedido, false);
                Opcion enViajeNuevo = evaluar(unidad, pedido, true);
                Opcion deLaUnidad = masBarata(enSuUltimoViaje, enViajeNuevo);
                if (deLaUnidad == null) {
                    continue;
                }

                if (mejor == null || deLaUnidad.incremento() < mejor.incremento()) {
                    segunda = mejor;
                    mejor = deLaUnidad;
                } else if (segunda == null || deLaUnidad.incremento() < segunda.incremento()) {
                    segunda = deLaUnidad;
                }
            }

            return new Alternativas(mejor, segunda, limiteDe(pedido));
        }

        private Opcion masBarata(Opcion una, Opcion otra) {
            if (una == null) {
                return otra;
            }
            if (otra == null) {
                return una;
            }
            return una.incremento() <= otra.incremento() ? una : otra;
        }

        /**
         * Evalúa una alternativa sin tocar el estado: se arma el programa que tendría la unidad
         * y se mide entero, porque una entrega intercalada retrasa todo lo que viene después.
         */
        private Opcion evaluar(Vehiculo unidad, Pedido pedido, boolean viajeNuevo) {
            List<Ruta> actuales = viajesPorUnidad.get(unidad.getId());
            List<Ruta> candidato = new ArrayList<>(actuales);
            Almacen almacen;

            if (viajeNuevo) {
                Situacion situacion = situacionPorUnidad.get(unidad.getId());
                almacen = almacenMasCercanoConStock(
                        situacion.posicion(), situacion.instante(), pedido.getCantidad());
                if (almacen == null) {
                    return null;
                }
                Ruta nuevo = new Ruta("candidato", almacen, unidad);
                nuevo.insertarPedido(0, pedido);
                candidato.add(nuevo);
            } else {
                if (actuales.isEmpty()) {
                    return null;
                }
                Ruta ultimo = actuales.get(actuales.size() - 1);
                if (ultimo.getCapacidadDisponible() < pedido.getCantidad()) {
                    return null;
                }
                almacen = ultimo.getAlmacen();
                Ruta ampliado = ultimo.copiar();
                ampliado.insertarPedido(ampliado.getPedidos().size(), pedido);
                candidato.set(candidato.size() - 1, ampliado);
            }

            List<MetricasRuta> metricas =
                    evaluador.evaluarPrograma(unidad, candidato, estado.getReloj());

            double costo = 0.0;
            List<CargaEnAlmacen> cargas = new ArrayList<>();
            for (int i = 0; i < candidato.size(); i++) {
                MetricasRuta medida = metricas.get(i);
                if (!medida.factible()) {
                    return null;
                }
                costo += medida.costoTotal();
                Ruta viaje = candidato.get(i);
                if (!viaje.estaVacia()) {
                    cargas.add(new CargaEnAlmacen(
                            viaje.getAlmacen(), medida.horaCarga(), viaje.getCargaTotal()));
                }
            }

            if (!cabeEnInventario(unidad.getId(), cargas)) {
                return null;
            }

            MetricasRuta ultima = metricas.get(metricas.size() - 1);
            return new Opcion(
                    unidad,
                    pedido,
                    almacen,
                    viajeNuevo,
                    costo - costoPorUnidad.get(unidad.getId()),
                    costo,
                    cargas,
                    new Situacion(ultima.posicionFinal(), ultima.horaFin()),
                    ultima.horasLlegada().get(pedido.getId())
            );
        }

        private void aplicar(Opcion elegida) {
            String id = elegida.unidad().getId();
            List<Ruta> viajes = viajesPorUnidad.get(id);

            if (elegida.viajeNuevo()) {
                Ruta nuevo = new Ruta(
                        "R-" + (viajes.size() + 1) + "-" + id, elegida.almacen(), elegida.unidad());
                nuevo.insertarPedido(0, elegida.pedido());
                viajes.add(nuevo);
            } else {
                Ruta ultimo = viajes.get(viajes.size() - 1);
                ultimo.insertarPedido(ultimo.getPedidos().size(), elegida.pedido());
            }

            for (CargaEnAlmacen previa : cargasPorUnidad.getOrDefault(id, List.of())) {
                inventario.liberar(previa.almacen(), previa.instante(), previa.unidades());
            }
            for (CargaEnAlmacen nueva : elegida.cargas()) {
                inventario.consumir(nueva.almacen(), nueva.instante(), nueva.unidades());
            }

            cargasPorUnidad.put(id, elegida.cargas());
            costoPorUnidad.put(id, elegida.costoDelPrograma());
            situacionPorUnidad.put(id, elegida.situacionFinal());
        }

        private void volcarEn(Solucion solucion) {
            for (List<Ruta> viajes : viajesPorUnidad.values()) {
                for (Ruta viaje : viajes) {
                    // El viaje sembrado para la carga a bordo puede quedar sin entregas si no
                    // había ningún cliente al que convenga llevársela; entonces no es un viaje.
                    if (!viaje.estaVacia()) {
                        solucion.agregarRuta(viaje);
                    }
                }
            }
        }

        /**
         * Las unidades más cercanas al destino con capacidad suficiente, tomando al menos una de
         * cada tipo.
         *
         * La cuota por tipo importa al arrancar un escenario: toda la flota parte del mismo
         * almacén, así que todas quedan empatadas en distancia y un recorte por cercanía a secas
         * se quedaría siempre con las mismas —las primeras de la lista— dejando fuera a los autos.
         */
        private List<Vehiculo> candidatas(Pedido pedido) {
            Map<String, List<Vehiculo>> porTipo = new LinkedHashMap<>();
            for (Vehiculo unidad : disponibles) {
                if (pedido.getCantidad() <= unidad.getCapacidad()) {
                    porTipo.computeIfAbsent(unidad.getTipo().name(), tipo -> new ArrayList<>())
                            .add(unidad);
                }
            }
            if (porTipo.isEmpty()) {
                return List.of();
            }

            Comparator<Vehiculo> porCercania = Comparator
                    .comparingDouble((Vehiculo unidad) -> pedido.getDestino()
                            .distanciaManhattanKm(situacionPorUnidad.get(unidad.getId()).posicion()))
                    .thenComparingDouble(Vehiculo::getCostoPorKm);

            int cuota = Math.max(1, unidadesCandidatas / porTipo.size());
            List<Vehiculo> elegidas = new ArrayList<>();
            List<Vehiculo> resto = new ArrayList<>();

            for (List<Vehiculo> delTipo : porTipo.values()) {
                delTipo.sort(porCercania);
                for (int i = 0; i < delTipo.size(); i++) {
                    if (i < cuota) {
                        elegidas.add(delTipo.get(i));
                    } else {
                        resto.add(delTipo.get(i));
                    }
                }
            }

            resto.sort(porCercania);
            for (Vehiculo unidad : resto) {
                if (elegidas.size() >= unidadesCandidatas) {
                    break;
                }
                elegidas.add(unidad);
            }
            return elegidas;
        }

        private Almacen almacenMasCercanoConStock(
                Ubicacion desde,
                LocalDateTime instante,
                int cantidad
        ) {
            Almacen mejor = null;
            double menorDistancia = Double.POSITIVE_INFINITY;

            for (Almacen almacen : estado.getAlmacenes()) {
                if (inventario.disponible(almacen, instante) < cantidad) {
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

        /** Lo que la unidad ya tenía reservado se devuelve antes de comparar: el programa lo reemplaza. */
        private boolean cabeEnInventario(String unidadId, List<CargaEnAlmacen> cargas) {
            Map<String, Integer> propio = new HashMap<>();
            for (CargaEnAlmacen carga : cargasPorUnidad.getOrDefault(unidadId, List.of())) {
                propio.merge(clave(carga), carga.unidades(), Integer::sum);
            }

            Map<String, CargaEnAlmacen> requerido = new LinkedHashMap<>();
            for (CargaEnAlmacen carga : cargas) {
                if (carga.almacen().esInventarioInfinito()) {
                    continue;
                }
                requerido.merge(clave(carga), carga, (una, otra) -> new CargaEnAlmacen(
                        una.almacen(), una.instante(), una.unidades() + otra.unidades()));
            }

            for (Map.Entry<String, CargaEnAlmacen> entrada : requerido.entrySet()) {
                CargaEnAlmacen carga = entrada.getValue();
                int disponible = inventario.disponible(carga.almacen(), carga.instante())
                        + propio.getOrDefault(entrada.getKey(), 0);
                if (carga.unidades() > disponible) {
                    return false;
                }
            }
            return true;
        }

        private String clave(CargaEnAlmacen carga) {
            return carga.almacen().getId() + "@" + Inventario.periodoDe(carga.instante());
        }
    }

    /** Mejor destino de un pedido, mejor destino en otra unidad y su límite efectivo. */
    private record Alternativas(Opcion mejor, Opcion segunda, LocalDateTime limite) {

        /**
         * Horas entre la llegada que conseguiría su mejor destino y su límite efectivo —el
         * primero entre su plazo y el cierre de su esquina—. Cuanto menor, más cerca está el
         * pedido de quedarse sin forma de ser entregado.
         */
        private double holguraHoras() {
            if (mejor == null) {
                return Double.POSITIVE_INFINITY;
            }
            return Evaluador.horasEntre(mejor.llegada(), limite);
        }

        /**
         * Cuánto se encarece atender el pedido si pierde su mejor unidad. Sin alternativa el
         * arrepentimiento es infinito: perder esa unidad significa no poder atenderlo.
         */
        private double arrepentimiento() {
            if (mejor == null) {
                return Double.NEGATIVE_INFINITY;
            }
            return segunda == null
                    ? Double.POSITIVE_INFINITY
                    : segunda.incremento() - mejor.incremento();
        }

        private boolean involucra(String unidadId) {
            return (mejor != null && mejor.unidad().getId().equals(unidadId))
                    || (segunda != null && segunda.unidad().getId().equals(unidadId));
        }
    }

    private record Situacion(Ubicacion posicion, LocalDateTime instante) {
    }

    private record Opcion(
            Vehiculo unidad,
            Pedido pedido,
            Almacen almacen,
            boolean viajeNuevo,
            double incremento,
            double costoDelPrograma,
            List<CargaEnAlmacen> cargas,
            Situacion situacionFinal,
            LocalDateTime llegada
    ) {
    }
}
