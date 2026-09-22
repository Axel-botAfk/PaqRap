package com.paqrap.planificador;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Vehiculo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * GRASP para PaqRap: construcción golosa aleatorizada más búsqueda local, repetidas.
 *
 * Cada iteración arma una solución completa desde cero con una lista restringida de candidatos
 * gobernada por alfa, y después la lleva a un óptimo local con {@link BusquedaLocal}. Al final se
 * devuelve la mejor de todas las iteraciones. Las dos fases por iteración son lo que distingue a
 * GRASP de un constructivo goloso corrido varias veces: sin la búsqueda local, más presupuesto
 * solo significa volver a muestrear la misma distribución.
 *
 * Es independiente de la búsqueda tabú: no comparte con ella ni el punto de partida ni la
 * maquinaria de movimientos. Lo único común es el {@link Evaluador}, para que las dos midan con
 * la misma regla y la comparación tenga sentido.
 *
 * Alcance:
 * - inserciones factibles pedido/almacén/unidad/posición;
 * - viajes encadenados: una unidad puede hacer varios a lo largo del horizonte, recargando en
 *   cualquier almacén que tenga producto;
 * - capacidad por viaje, stock por periodo de reposición, unidad disponible y plazo;
 * - costo incremental sobre el programa completo de la unidad;
 * - holgura;
 * - LRC controlada por alfa;
 * - selección aleatoria reproducible por semilla;
 * - búsqueda local por reubicación, intercambio e incorporación de pedidos sin asignar;
 * - pedidos no asignados.
 *
 * Aún no se implementan entregas parciales, turnos ni averías.
 */
public final class Grasp implements Planificador {
    public static final String NOMBRE = "GRASP";

    private final Evaluador evaluador;
    private final BusquedaLocal busquedaLocal;

    public Grasp(CalculadorDistancia calculadorDistancia) {
        this(new Evaluador(calculadorDistancia));
    }

    public Grasp(Evaluador evaluador) {
        this.evaluador = Objects.requireNonNull(evaluador);
        this.busquedaLocal = new BusquedaLocal(this.evaluador);
    }

    public Evaluador getEvaluador() {
        return evaluador;
    }

    @Override
    public Solucion planificar(EstadoOperacion estado, Parametros parametros) {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(parametros);

        Random random = new Random(parametros.getSemilla());
        Map<String, LocalDateTime> limites = limitesEfectivos(estado);
        Solucion mejor = null;
        double valorMejor = Double.POSITIVE_INFINITY;

        for (int iteracion = 0; iteracion < parametros.getMaxIteraciones(); iteracion++) {
            Solucion candidata = construirUnaSolucion(estado, parametros, random, limites);
            double valor = evaluador.objetivo(candidata, estado, parametros);
            if (mejor == null || valor < valorMejor) {
                mejor = candidata;
                valorMejor = valor;
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

    /**
     * Hasta cuándo se le puede entregar de verdad a cada pedido: lo primero entre su plazo y el
     * cierre de la esquina de su destino. Se calcula una vez por planificación, porque solo
     * depende del destino y del reloj, y todas las iteraciones de GRASP comparten el resultado.
     */
    private Map<String, LocalDateTime> limitesEfectivos(EstadoOperacion estado) {
        Map<String, LocalDateTime> limites = new HashMap<>();
        for (Pedido pedido : estado.getPedidos()) {
            limites.put(pedido.getId(), evaluador.limiteEfectivo(pedido, estado.getReloj()));
        }
        return limites;
    }

    private Solucion construirUnaSolucion(
            EstadoOperacion estado,
            Parametros parametros,
            Random random,
            Map<String, LocalDateTime> limites
    ) {
        double alfa = parametros.getAlfa();
        Solucion solucion = new Solucion();
        solucion.setAlgoritmo(NOMBRE);

        List<Pedido> pendientes = new ArrayList<>();
        for (Pedido pedido : estado.getPedidos()) {
            if (!pedido.getFechaRegistro().isAfter(estado.getReloj())) {
                pendientes.add(pedido);
            }
        }
        pendientes.sort(Comparator.comparing(pedido -> limites.get(pedido.getId())));

        Contexto contexto = new Contexto(estado, evaluador);

        while (!pendientes.isEmpty()) {
            // Solo compiten los pedidos con el plazo más apretado, que son los únicos que la
            // lista restringida puede elegir. Generar candidatos para todos los pendientes en
            // cada inserción volvía la construcción cuadrática con cientos de pedidos en cola.
            List<Pedido> masUrgentes = grupoMasUrgente(pendientes, limites);
            List<Insercion> inserciones =
                    generarInsercionesFactibles(solucion, masUrgentes, estado, contexto, parametros);

            if (inserciones.isEmpty()) {
                // Solo estos quedan sin atender; el resto de la cola puede seguir siendo viable.
                solucion.agregarPedidosNoAsignados(masUrgentes);
                pendientes.removeAll(masUrgentes);
                continue;
            }

            // La urgencia la define el pedido, no la inserción: primero los plazos más
            // apretados y, dentro de un mismo pedido, la forma más barata de atenderlo.
            //
            // Ordenar por la holgura resultante sería contraproducente ahora que una unidad
            // puede trasladarse hasta cualquier almacén: justamente las inserciones que cruzan
            // la ciudad llegan más tarde, dejan menos holgura y quedarían primeras. La holgura
            // sigue usándose para desempatar, pero prefiriendo la entrega con más margen.
            inserciones.sort(
                    Comparator.comparing(
                                    (Insercion insercion) -> limites.get(insercion.pedido().getId()))
                            .thenComparingDouble(Insercion::costoIncremental)
                            .thenComparing(Comparator.comparingDouble(Insercion::holguraHoras).reversed())
            );

            List<Insercion> lrc = construirListaRestringida(inserciones, alfa, limites);
            Insercion elegida = lrc.get(random.nextInt(lrc.size()));

            aplicarInsercion(solucion, elegida, contexto);
            pendientes.remove(elegida.pedido());
        }

        // Segunda fase de la iteración GRASP: llevar la solución recién construida a un óptimo
        // local. Reubicar la última entrega de un viaje lo deja vacío, así que se depuran antes
        // de medir; y las métricas hay que volcarlas de nuevo porque la búsqueda movió entregas
        // y las que dejó el contexto quedaron viejas.
        busquedaLocal.mejorar(solucion, estado, parametros);
        solucion.depurarRutasVacias();
        evaluador.sincronizarMetricas(solucion, estado);
        return solucion;
    }

    /**
     * Dos familias de candidatos por cada pedido pendiente: sumarlo a un viaje ya planificado,
     * o estrenar un viaje con alguna unidad desde algún almacén. En ambos casos el costo se
     * mide sobre el programa completo de la unidad, porque un viaje nuevo arrastra el traslado
     * desde donde la unidad quedó, y una entrega intercalada retrasa todo lo que viene después.
     */
    private List<Insercion> generarInsercionesFactibles(
            Solucion solucion,
            List<Pedido> pendientes,
            EstadoOperacion estado,
            Contexto contexto,
            Parametros parametros
    ) {
        List<Insercion> resultado = new ArrayList<>();

        for (Pedido pedido : pendientes) {
            for (int indiceRuta : viajesCercanos(solucion, pedido,
                    parametros.getViajesCandidatosPorPedido())) {
                Ruta viaje = solucion.getRuta(indiceRuta);
                if (viaje.getCargaTotal() + pedido.getCantidad() > viaje.getVehiculo().getCapacidad()) {
                    continue;
                }
                for (int posicion = 0; posicion <= viaje.getPedidos().size(); posicion++) {
                    Insercion candidata = contexto.evaluarEnViajeExistente(
                            solucion, indiceRuta, posicion, pedido);
                    if (candidata != null) {
                        resultado.add(candidata);
                    }
                }
            }

            for (Vehiculo vehiculo : unidadesCercanas(estado, pedido,
                    parametros.getUnidadesCandidatasPorPedido())) {
                for (Almacen almacen : estado.getAlmacenes()) {
                    Insercion candidata = contexto.evaluarEnViajeNuevo(
                            solucion, vehiculo, almacen, pedido);
                    if (candidata != null) {
                        resultado.add(candidata);
                    }
                }
            }
        }

        return resultado;
    }

    /**
     * Pedidos que comparten el límite efectivo más apretado de la cola, que viene ordenada por
     * ese límite. No es la fecha límite del cliente: si la esquina del destino se cierra antes,
     * manda el cierre, porque es entonces cuando el pedido deja de poder entregarse.
     */
    private List<Pedido> grupoMasUrgente(
            List<Pedido> pendientes,
            Map<String, LocalDateTime> limites
    ) {
        LocalDateTime limite = limites.get(pendientes.get(0).getId());
        List<Pedido> grupo = new ArrayList<>();
        for (Pedido pedido : pendientes) {
            if (!limites.get(pedido.getId()).equals(limite)) {
                break;
            }
            grupo.add(pedido);
        }
        return grupo;
    }

    /**
     * Índices de los viajes más cercanos al destino del pedido. La cercanía se mide en línea
     * recta sobre la retícula contra el almacén del viaje y contra sus entregas: es una cota
     * inferior del recorrido real, suficiente para descartar los viajes que operan lejos.
     */
    private List<Integer> viajesCercanos(Solucion solucion, Pedido pedido, int cuantos) {
        int total = solucion.getCantidadRutas();
        if (total <= cuantos) {
            List<Integer> todos = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                todos.add(i);
            }
            return todos;
        }

        List<Integer> indices = new ArrayList<>(total);
        Map<Integer, Double> distancia = new HashMap<>();
        for (int i = 0; i < total; i++) {
            indices.add(i);
            distancia.put(i, cercaniaDe(solucion.getRuta(i), pedido));
        }
        indices.sort(Comparator.comparingDouble(distancia::get));
        return indices.subList(0, cuantos);
    }

    private double cercaniaDe(Ruta viaje, Pedido pedido) {
        double menor = pedido.getDestino().distanciaManhattanKm(viaje.getAlmacen().getUbicacion());
        for (Pedido entrega : viaje.getPedidos()) {
            menor = Math.min(menor, pedido.getDestino().distanciaManhattanKm(entrega.getDestino()));
        }
        return menor;
    }

    /**
     * Unidades con capacidad suficiente más cercanas a la entrega, tomando al menos una cuota
     * de cada tipo.
     *
     * La cuota por tipo es lo que importa: al arrancar un escenario toda la flota está en el
     * mismo almacén, así que todas quedan empatadas en distancia y un recorte por cercanía a
     * secas se queda siempre con las mismas. Ordenando además por costo se quedaba con las doce
     * bicicletas y los diez autos nunca llegaban a ser candidatos, de modo que la flota operaba
     * a menos de la mitad de su capacidad mientras se vencían plazos.
     */
    private List<Vehiculo> unidadesCercanas(EstadoOperacion estado, Pedido pedido, int cuantas) {
        Map<TipoVehiculo, List<Vehiculo>> porTipo = new LinkedHashMap<>();
        for (Vehiculo vehiculo : estado.getVehiculos()) {
            if (vehiculo.getEstado().admiteAsignacion()
                    && pedido.getCantidad() <= vehiculo.getCapacidad()) {
                porTipo.computeIfAbsent(vehiculo.getTipo(), tipo -> new ArrayList<>()).add(vehiculo);
            }
        }
        if (porTipo.isEmpty()) {
            return List.of();
        }

        Comparator<Vehiculo> porCercania = Comparator
                .comparingDouble((Vehiculo unidad) ->
                        pedido.getDestino().distanciaManhattanKm(unidad.getUbicacion()))
                .thenComparingDouble(Vehiculo::getCostoPorKm);

        int cuota = Math.max(1, cuantas / porTipo.size());
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
            if (elegidas.size() >= cuantas) {
                break;
            }
            elegidas.add(unidad);
        }
        return elegidas;
    }

    /**
     * Lista restringida de candidatos.
     *
     * Compiten solo las inserciones del pedido con el límite efectivo más apretado: esa es la
     * priorización por holgura del caso, aplicada donde corresponde, al elegir a quién se
     * atiende. Entre ellas entran a la lista las que no se alejan del mejor costo más de alfa
     * veces el rango de costos, de modo que alfa=0 deja únicamente la mejor y alfa=1 las deja
     * todas.
     *
     * El corte por valor reemplaza al corte por cantidad, que tomaba una fracción fija de
     * todos los candidatos de todos los pedidos: con las unidades ya libres de trasladarse a
     * cualquier almacén, el conjunto de candidatos creció tanto que esa fracción terminaba
     * incluyendo inserciones que cruzaban la ciudad.
     */
    private List<Insercion> construirListaRestringida(
            List<Insercion> ordenadas,
            double alfa,
            Map<String, LocalDateTime> limites
    ) {
        if (ordenadas.isEmpty()) {
            return List.of();
        }

        LocalDateTime masApretado = limites.get(ordenadas.get(0).pedido().getId());
        List<Insercion> urgentes = new ArrayList<>();
        for (Insercion candidata : ordenadas) {
            if (!limites.get(candidata.pedido().getId()).equals(masApretado)) {
                break;
            }
            urgentes.add(candidata);
        }

        double menorCosto = urgentes.get(0).costoIncremental();
        double mayorCosto = menorCosto;
        for (Insercion candidata : urgentes) {
            mayorCosto = Math.max(mayorCosto, candidata.costoIncremental());
        }
        double umbral = menorCosto + alfa * (mayorCosto - menorCosto);

        // El umbral por valor se estira cuando entre los candidatos hay alguno muy caro: basta
        // una inserción que cruce la ciudad para que el rango crezca y el corte deje pasar
        // opciones que no deberían competir. Por eso además se limita cuántas entran, contando
        // desde la mejor, que es el corte clásico de GRASP.
        int cupo = Math.max(1, (int) Math.ceil(alfa * urgentes.size()));

        List<Insercion> lrc = new ArrayList<>();
        for (Insercion candidata : urgentes) {
            if (lrc.size() >= cupo || candidata.costoIncremental() > umbral + 1e-9) {
                break;
            }
            lrc.add(candidata);
        }
        return lrc;
    }

    private void aplicarInsercion(Solucion solucion, Insercion insercion, Contexto contexto) {
        if (insercion.indiceRuta() == Insercion.VIAJE_NUEVO) {
            Ruta nuevo = new Ruta(
                    "R-" + (solucion.getCantidadRutas() + 1),
                    insercion.almacen(),
                    insercion.vehiculo()
            );
            nuevo.insertarPedido(0, insercion.pedido());
            solucion.agregarRuta(nuevo);
        } else {
            solucion.getRuta(insercion.indiceRuta())
                    .insertarPedido(insercion.posicion(), insercion.pedido());
        }

        contexto.refrescar(solucion, insercion.vehiculo());
    }

    private record Insercion(
            Pedido pedido,
            Vehiculo vehiculo,
            Almacen almacen,
            int indiceRuta,
            int posicion,
            double costoIncremental,
            double holguraHoras
    ) {
        private static final int VIAJE_NUEVO = -1;
    }

    /**
     * Estado interno de UNA construcción GRASP: qué viajes tiene asignados cada unidad, cuánto
     * cuesta su programa y cuánto producto ha retirado de cada almacén.
     *
     * Trabaja sobre su propia copia del inventario para no alterar el de la operación cuando se
     * realizan varias iteraciones constructivas con la misma información.
     */
    private static final class Contexto {
        private final EstadoOperacion estado;
        private final Evaluador evaluador;
        private final Inventario inventario;

        private final Map<String, List<Integer>> viajesPorVehiculo = new LinkedHashMap<>();
        private final Map<String, Double> costoPorVehiculo = new HashMap<>();
        private final Map<String, List<CargaEnAlmacen>> cargasPorVehiculo = new HashMap<>();

        private Contexto(EstadoOperacion estado, Evaluador evaluador) {
            this.estado = estado;
            this.evaluador = evaluador;
            this.inventario = new Inventario(estado.getAlmacenes(), estado.getReloj());
        }

        /** Candidato: intercalar el pedido en un viaje ya planificado. */
        private Insercion evaluarEnViajeExistente(
                Solucion solucion,
                int indiceRuta,
                int posicion,
                Pedido pedido
        ) {
            Ruta viaje = solucion.getRuta(indiceRuta);
            Vehiculo vehiculo = viaje.getVehiculo();

            List<Ruta> programa = viajesDe(solucion, vehiculo);
            int posicionEnPrograma = viajesPorVehiculo
                    .getOrDefault(vehiculo.getId(), List.of())
                    .indexOf(indiceRuta);

            Ruta modificado = viaje.copiar();
            modificado.insertarPedido(posicion, pedido);
            programa.set(posicionEnPrograma, modificado);

            return medir(vehiculo, programa, posicionEnPrograma, pedido, indiceRuta, posicion,
                    viaje.getAlmacen());
        }

        /** Candidato: estrenar un viaje al final del programa de la unidad. */
        private Insercion evaluarEnViajeNuevo(
                Solucion solucion,
                Vehiculo vehiculo,
                Almacen almacen,
                Pedido pedido
        ) {
            List<Ruta> programa = viajesDe(solucion, vehiculo);

            Ruta nuevo = new Ruta("candidato", almacen, vehiculo);
            nuevo.insertarPedido(0, pedido);
            programa.add(nuevo);

            return medir(vehiculo, programa, programa.size() - 1, pedido,
                    Insercion.VIAJE_NUEVO, 0, almacen);
        }

        /**
         * Evalúa el programa completo de la unidad con el candidato incorporado y devuelve la
         * inserción si respeta plazos, capacidad y stock. El costo informado es el incremento
         * sobre lo que esa unidad ya costaba.
         */
        private Insercion medir(
                Vehiculo vehiculo,
                List<Ruta> programa,
                int posicionEnPrograma,
                Pedido pedido,
                int indiceRuta,
                int posicion,
                Almacen almacen
        ) {
            List<MetricasRuta> metricas =
                    evaluador.evaluarPrograma(vehiculo, programa, estado.getReloj());

            double costo = 0.0;
            List<CargaEnAlmacen> cargas = new ArrayList<>();
            for (int i = 0; i < programa.size(); i++) {
                MetricasRuta medida = metricas.get(i);
                if (!medida.factible()) {
                    return null;
                }
                costo += medida.costoTotal();
                Ruta viaje = programa.get(i);
                if (!viaje.estaVacia()) {
                    cargas.add(new CargaEnAlmacen(
                            viaje.getAlmacen(), medida.horaCarga(), viaje.getCargaTotal()));
                }
            }

            if (!cabeEnInventario(vehiculo.getId(), cargas)) {
                return null;
            }

            LocalDateTime llegada = metricas.get(posicionEnPrograma).horasLlegada().get(pedido.getId());
            double holgura = Evaluador.horasEntre(llegada, pedido.getFechaLimite());
            double incremento = costo - costoPorVehiculo.getOrDefault(vehiculo.getId(), 0.0);

            return new Insercion(pedido, vehiculo, almacen, indiceRuta, posicion, incremento, holgura);
        }

        /** Recalcula el programa de la unidad sobre la solución ya modificada. */
        private void refrescar(Solucion solucion, Vehiculo vehiculo) {
            String id = vehiculo.getId();
            viajesPorVehiculo.clear();
            viajesPorVehiculo.putAll(Evaluador.indicesPorVehiculo(solucion.getRutas()));

            List<Ruta> programa = viajesDe(solucion, vehiculo);
            List<MetricasRuta> metricas =
                    evaluador.evaluarPrograma(vehiculo, programa, estado.getReloj());

            double costo = 0.0;
            List<CargaEnAlmacen> cargas = new ArrayList<>();
            for (int i = 0; i < programa.size(); i++) {
                costo += metricas.get(i).costoTotal();
                Ruta viaje = programa.get(i);
                if (!viaje.estaVacia()) {
                    cargas.add(new CargaEnAlmacen(
                            viaje.getAlmacen(), metricas.get(i).horaCarga(), viaje.getCargaTotal()));
                }
                viaje.actualizarMetricas(
                        metricas.get(i).distanciaTotalKm(),
                        metricas.get(i).costoTotal(),
                        metricas.get(i).duracionHoras()
                );
            }

            for (CargaEnAlmacen anterior : cargasPorVehiculo.getOrDefault(id, List.of())) {
                inventario.liberar(anterior.almacen(), anterior.instante(), anterior.unidades());
            }
            for (CargaEnAlmacen nueva : cargas) {
                inventario.consumir(nueva.almacen(), nueva.instante(), nueva.unidades());
            }

            cargasPorVehiculo.put(id, cargas);
            costoPorVehiculo.put(id, costo);
        }

        /** Copia modificable de los viajes que la unidad tiene asignados, en orden. */
        private List<Ruta> viajesDe(Solucion solucion, Vehiculo vehiculo) {
            List<Ruta> programa = new ArrayList<>();
            for (int indice : viajesPorVehiculo.getOrDefault(vehiculo.getId(), List.of())) {
                programa.add(solucion.getRuta(indice));
            }
            return programa;
        }

        /**
         * ¿Alcanza el producto para este programa? Lo que la unidad ya tenía reservado se
         * devuelve antes de comparar, porque el programa candidato lo reemplaza por completo.
         */
        private boolean cabeEnInventario(String vehiculoId, List<CargaEnAlmacen> cargas) {
            Map<String, Integer> propio = new HashMap<>();
            for (CargaEnAlmacen carga : cargasPorVehiculo.getOrDefault(vehiculoId, List.of())) {
                propio.merge(clave(carga), carga.unidades(), Integer::sum);
            }

            Map<String, CargaEnAlmacen> requerido = new LinkedHashMap<>();
            for (CargaEnAlmacen carga : cargas) {
                if (carga.almacen().esInventarioInfinito()) {
                    continue;
                }
                requerido.merge(clave(carga), carga, (unaCarga, otra) -> new CargaEnAlmacen(
                        unaCarga.almacen(), unaCarga.instante(), unaCarga.unidades() + otra.unidades()));
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
}
