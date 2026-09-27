package com.paqrap.planificador.alns;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.CargaEnAlmacen;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Inventario;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.RepartoParcial;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Solución en construcción, con lo necesario para sacarle pedidos y volver a metérselos.
 *
 * Es el objeto sobre el que trabajan los operadores de destrucción y reparación. Guarda la
 * solución, el inventario que le corresponde y el costo vigente de cada unidad.
 *
 * <h2>Por qué el costo se cachea por unidad y no por plan</h2>
 *
 * Medir una inserción candidata exige evaluar el programa completo de la unidad, porque una
 * entrega intercalada retrasa todo lo que viene después. Evaluar en cambio el plan entero por
 * cada candidato costaría el doble sin aportar nada: las demás unidades no se enteran de lo que
 * pasa en ésta.
 *
 * El caché se invalida por unidad en cuanto algo cambia en sus viajes, que es la única forma de
 * que no se desincronice: no hay manera de modificar un viaje sin pasar por los métodos de aquí.
 */
final class Plan {
    private final EstadoOperacion estado;
    private final Evaluador evaluador;
    private final Solucion solucion;
    private final Inventario inventario;
    private final List<Vehiculo> disponibles;

    /** Costo del programa de cada unidad; se borra la entrada al tocarle un viaje. */
    /**
     * Cuanto cuesta por producto y por hora que una entrega se programe mas tarde.
     *
     * Vive en el plan y no se le pide a los parametros en cada consulta porque entra en el costo
     * cacheado por unidad: el termino depende solo de los viajes de esa unidad, igual que los
     * kilometros, de modo que se guarda junto con ellos.
     */
    private final double penalidadEspera;

    private final Map<String, Double> esperaVigente = new HashMap<>();
    private Map<String, List<Integer>> indicesPorUnidad;
    private final Map<String, Double> costoVigente = new HashMap<>();

    private Plan(
            EstadoOperacion estado,
            Evaluador evaluador,
            Solucion solucion,
            Inventario inventario,
            List<Vehiculo> disponibles,
            double penalidadEspera
    ) {
        this.estado = estado;
        this.evaluador = evaluador;
        this.solucion = solucion;
        this.inventario = inventario;
        this.disponibles = disponibles;
        this.penalidadEspera = penalidadEspera;
    }

    /**
     * Plan sin viajes, con todos los pedidos pendientes fuera.
     *
     * Las unidades que traen producto encima reciben de entrada un viaje que ya sale cargado: el
     * producto salió del almacén en una planificación anterior y volver a por él sería hacerlas
     * cruzar la ciudad para recoger lo que ya llevan.
     */
    static Plan vacio(EstadoOperacion estado, Evaluador evaluador, double penalidadEspera) {
        Solucion solucion = new Solucion();

        List<Vehiculo> disponibles = new ArrayList<>();
        for (Vehiculo unidad : estado.getVehiculos()) {
            if (unidad.getEstado().admiteAsignacion()) {
                disponibles.add(unidad);
            }
        }
        for (Vehiculo unidad : disponibles) {
            if (unidad.getCargaABordo() > 0) {
                solucion.agregarRuta(new Ruta(
                        "C-" + unidad.getId(), estado.getAlmacenes().get(0), unidad, true));
            }
        }

        // Entran todos los pedidos que el simulador considere de esta planificación, incluidos
        // los que todavía no se registran cuando la lectura es por bloques. Descartarlos aquí los
        // haría desaparecer: no se planificarían y tampoco figurarían como no asignados. Que no se
        // puedan entregar antes de existir lo garantiza el borde inferior de la ventana de tiempo,
        // en Evaluador.calcularMetricas, y no un filtro en la entrada.
        solucion.agregarPedidosNoAsignados(estado.getPedidos());

        return new Plan(estado, evaluador, solucion,
                new Inventario(estado.getAlmacenes(), estado.getReloj()), disponibles,
                penalidadEspera);
    }

    /** Copia independiente: destruir sobre ella no toca a la original. */
    Plan copiar() {
        Plan copia = new Plan(
                estado, evaluador, solucion.copiar(), inventario.copiar(), disponibles,
                penalidadEspera);
        copia.costoVigente.putAll(costoVigente);
        copia.esperaVigente.putAll(esperaVigente);
        return copia;
    }

    Solucion solucion() {
        return solucion;
    }

    List<Pedido> noAsignados() {
        return solucion.getPedidosNoAsignados();
    }

    /**
     * Valor a minimizar, con las mismas reglas que los otros dos algoritmos: costo de operación
     * más la penalidad por cada <b>producto</b> sin atender, pesado por la urgencia de su pedido.
     *
     * El costo se suma desde el caché por unidad en vez de reevaluar el plan entero; el término de
     * los no asignados se le pide al evaluador, que es quien conoce la urgencia, para que los tres
     * algoritmos midan exactamente lo mismo.
     */
    double objetivo(Parametros parametros) {
        double costo = 0.0;
        double espera = 0.0;
        for (Vehiculo unidad : disponibles) {
            costo += costoDe(unidad);
            espera += esperaDe(unidad);
        }
        return costo
                + penalidadEspera * espera
                + evaluador.penalidadPorNoAsignados(solucion, estado, parametros);
    }

    /**
     * Reparte por partes lo que no cupo entero en ninguna unidad.
     *
     * El reparto toca las rutas por fuera del plan, asi que despues hay que olvidar los costos
     * cacheados: seguirian siendo los de antes de meter los trozos.
     */
    void repartirLoQueNoCupo(RepartoParcial reparto) {
        if (reparto.repartir(solucion, estado) > 0) {
            costoVigente.clear();
            esperaVigente.clear();
            olvidarIndices();
        }
    }

    // ---------------------------------------------------------------- destruir

    /** Saca la entrega indicada y la devuelve a la bolsa de pendientes. */
    Pedido quitar(int indiceRuta, int posicion) {
        Ruta viaje = solucion.getRuta(indiceRuta);
        Pedido pedido = viaje.retirarPedido(posicion);
        solucion.agregarPedidoNoAsignado(pedido);
        costoVigente.remove(viaje.getVehiculo().getId());
        esperaVigente.remove(viaje.getVehiculo().getId());
        return pedido;
    }

    /** Todas las posiciones ocupadas del plan, como pares (viaje, posición). */
    List<int[]> ubicaciones() {
        List<int[]> ubicaciones = new ArrayList<>();
        for (int i = 0; i < solucion.getCantidadRutas(); i++) {
            for (int p = 0; p < solucion.getRuta(i).getPedidos().size(); p++) {
                ubicaciones.add(new int[]{i, p});
            }
        }
        return ubicaciones;
    }

    Pedido pedidoEn(int indiceRuta, int posicion) {
        return solucion.getRuta(indiceRuta).getPedidos().get(posicion);
    }

    int cantidadDeViajes() {
        return solucion.getCantidadRutas();
    }

    /** Cuántas entregas tiene el viaje indicado. */
    int ubicacionesDe(int indiceRuta) {
        return solucion.getRuta(indiceRuta).getPedidos().size();
    }

    /**
     * Cuánto se ahorraría el plan si esta entrega no estuviera.
     *
     * Es lo que mira la remoción del peor: la entrega que más cuesta mantener es la primera
     * candidata a estar en otro sitio.
     */
    double ahorroAlQuitar(int indiceRuta, int posicion) {
        Ruta viaje = solucion.getRuta(indiceRuta);
        Vehiculo unidad = viaje.getVehiculo();
        double antes = costoDe(unidad);

        Pedido pedido = viaje.retirarPedido(posicion);
        costoVigente.remove(unidad.getId());
        esperaVigente.remove(unidad.getId());
        double despues = costoDe(unidad);

        viaje.insertarPedido(posicion, pedido);
        costoVigente.remove(unidad.getId());
        esperaVigente.remove(unidad.getId());
        return antes - despues;
    }

    /** Deja el plan sin viajes vacíos, que aparecen al vaciar una ruta entera. */
    void depurar() {
        solucion.depurarRutasVacias();
        costoVigente.clear();
        esperaVigente.clear();
        olvidarIndices();
    }

    // ---------------------------------------------------------------- reparar

    /**
     * Mejor forma de meter el pedido, y la mejor en otra unidad.
     *
     * Se consideran dos familias, igual que haría cualquiera: intercalarlo en un viaje que ya
     * existe, o estrenar uno cargando en algún almacén. En ambos casos el costo se mide sobre el
     * programa completo de la unidad.
     */
    Alternativas alternativasPara(Pedido pedido, ParametrosAlns ajustes) {
        Opcion mejor = null;
        Opcion segunda = null;

        for (int indice : viajesCercanos(pedido, ajustes.viajesCandidatos())) {
            Ruta viaje = solucion.getRuta(indice);
            if (viaje.getCapacidadDisponible() < pedido.getCantidad()) {
                continue;
            }
            for (int posicion = 0; posicion <= viaje.getPedidos().size(); posicion++) {
                Opcion candidata = medirEnViajeExistente(pedido, indice, posicion);
                if (candidata == null) {
                    continue;
                }
                if (mejor == null || candidata.incremento() < mejor.incremento()) {
                    segunda = mejor;
                    mejor = candidata;
                } else if (segunda == null || candidata.incremento() < segunda.incremento()) {
                    segunda = candidata;
                }
            }
        }

        for (Vehiculo unidad : unidadesCercanas(pedido, ajustes.unidadesCandidatas())) {
            for (Almacen almacen : estado.getAlmacenes()) {
                Opcion candidata = medirEnViajeNuevo(pedido, unidad, almacen);
                if (candidata == null) {
                    continue;
                }
                if (mejor == null || candidata.incremento() < mejor.incremento()) {
                    segunda = mejor;
                    mejor = candidata;
                } else if (segunda == null || candidata.incremento() < segunda.incremento()) {
                    segunda = candidata;
                }
            }
        }

        return new Alternativas(mejor, segunda);
    }

    /** Lleva a cabo la inserción elegida. */
    void aplicar(Opcion opcion) {
        if (opcion.viajeNuevo()) {
            Ruta nuevo = new Ruta(
                    "R-" + (solucion.getCantidadRutas() + 1) + "-" + opcion.unidad().getId(),
                    opcion.almacen(), opcion.unidad());
            nuevo.insertarPedido(0, opcion.pedido());
            solucion.agregarRuta(nuevo);
            olvidarIndices();
        } else {
            solucion.getRuta(opcion.indiceRuta()).insertarPedido(opcion.posicion(), opcion.pedido());
        }
        solucion.removerPedidoNoAsignado(opcion.pedido());
        costoVigente.remove(opcion.unidad().getId());
        esperaVigente.remove(opcion.unidad().getId());
    }

    // ---------------------------------------------------------------- interno

    private Opcion medirEnViajeExistente(Pedido pedido, int indiceRuta, int posicion) {
        Ruta viaje = solucion.getRuta(indiceRuta);
        Vehiculo unidad = viaje.getVehiculo();

        List<Ruta> programa = programaDe(unidad);
        int posicionEnPrograma = indicesDe(unidad).indexOf(indiceRuta);
        if (posicionEnPrograma < 0) {
            return null;
        }

        Ruta modificado = viaje.copiar();
        modificado.insertarPedido(posicion, pedido);
        programa.set(posicionEnPrograma, modificado);

        Double costo = medirPrograma(unidad, programa);
        if (costo == null) {
            return null;
        }
        return new Opcion(pedido, unidad, viaje.getAlmacen(), indiceRuta, posicion, false,
                costo - costoDe(unidad) + penalidadEspera * esperaPropia(unidad, programa, pedido));
    }

    private Opcion medirEnViajeNuevo(Pedido pedido, Vehiculo unidad, Almacen almacen) {
        if (pedido.getCantidad() > unidad.getCapacidad()) {
            return null;
        }
        List<Ruta> programa = programaDe(unidad);
        Ruta nuevo = new Ruta("candidato", almacen, unidad);
        nuevo.insertarPedido(0, pedido);
        programa.add(nuevo);

        Double costo = medirPrograma(unidad, programa);
        if (costo == null) {
            return null;
        }
        return new Opcion(pedido, unidad, almacen, -1, 0, true,
                costo - costoDe(unidad) + penalidadEspera * esperaPropia(unidad, programa, pedido));
    }

    /**
     * Costo del programa, o {@code null} si alguna parte resulta inviable: plazo incumplido,
     * capacidad excedida, destino sin camino abierto o almacén sin producto.
     */
    /** Lo que se hace esperar al pedido que se esta intentando colocar, y a nadie mas. */
    private double esperaPropia(Vehiculo unidad, List<Ruta> programa, Pedido pedido) {
        List<MetricasRuta> metricas = evaluador.medidasDe(unidad, programa, estado.getReloj());
        for (MetricasRuta medida : metricas) {
            LocalDateTime llegada = medida.horasLlegada().get(pedido.getId());
            if (llegada != null) {
                return evaluador.esperaDe(pedido, llegada, estado.getReloj());
            }
        }
        return 0.0;
    }

    private Double medirPrograma(Vehiculo unidad, List<Ruta> programa) {
        List<MetricasRuta> metricas = evaluador.medidasDe(unidad, programa, estado.getReloj());

        double costo = 0.0;
        List<CargaEnAlmacen> cargas = new ArrayList<>();
        for (int i = 0; i < programa.size(); i++) {
            MetricasRuta medida = metricas.get(i);
            if (!medida.factible()) {
                return null;
            }
            costo += medida.costoTotal();
            Ruta viaje = programa.get(i);
            if (!viaje.estaVacia() && !viaje.saleYaCargado()) {
                cargas.add(new CargaEnAlmacen(
                        viaje.getAlmacen(), medida.horaCarga(), viaje.getCargaTotal()));
            }
        }
        return cabeEnInventario(unidad, cargas) ? costo : null;
    }

    /**
     * Lo que la unidad ya tenía reservado se devuelve antes de comparar, porque el programa
     * candidato lo reemplaza entero y no se suma al anterior.
     */
    private boolean cabeEnInventario(Vehiculo unidad, List<CargaEnAlmacen> cargas) {
        Map<String, Integer> propio = new HashMap<>();
        for (Ruta viaje : programaDe(unidad)) {
            if (viaje.estaVacia() || viaje.saleYaCargado()) {
                continue;
            }
            MetricasRuta medida = evaluador.evaluarRuta(viaje, estado.getReloj());
            if (medida.factible()) {
                propio.merge(clave(viaje.getAlmacen(), medida.horaCarga()),
                        viaje.getCargaTotal(), Integer::sum);
            }
        }

        Map<String, Integer> requerido = new LinkedHashMap<>();
        Map<String, CargaEnAlmacen> donde = new LinkedHashMap<>();
        for (CargaEnAlmacen carga : cargas) {
            if (carga.almacen().esInventarioInfinito()) {
                continue;
            }
            String clave = clave(carga.almacen(), carga.instante());
            requerido.merge(clave, carga.unidades(), Integer::sum);
            donde.putIfAbsent(clave, carga);
        }

        for (Map.Entry<String, Integer> entrada : requerido.entrySet()) {
            CargaEnAlmacen carga = donde.get(entrada.getKey());
            int disponible = inventario.disponible(carga.almacen(), carga.instante())
                    + propio.getOrDefault(entrada.getKey(), 0);
            if (entrada.getValue() > disponible) {
                return false;
            }
        }
        return true;
    }

    private String clave(Almacen almacen, LocalDateTime instante) {
        return almacen.getId() + "@" + Inventario.periodoDe(instante);
    }

    /**
     * Lo que se hace esperar a los clientes de una unidad, cacheado igual que su costo.
     *
     * Va separado del costo a proposito. El objetivo necesita la espera de todo el plan, pero el
     * incremento con el que se eligen las inserciones no puede incluir el retraso que la nueva
     * entrega causa a las demas: al sumarlo, intercalar en medio de una ruta empuja a todas las
     * siguientes y el incremento se dispara, de modo que insertar al final sale siempre mas
     * barato. Medido sobre un dia de datos reales, mezclarlas le costaba a ALNS veintiocho
     * entregas y lo volvia dieciocho veces mas lento.
     */
    private double esperaDe(Vehiculo unidad) {
        return esperaVigente.computeIfAbsent(unidad.getId(), id -> {
            List<Ruta> programa = programaDe(unidad);
            if (programa.isEmpty()) {
                return 0.0;
            }
            List<MetricasRuta> metricas =
                    evaluador.medidasDe(unidad, programa, estado.getReloj());
            double espera = 0.0;
            for (int i = 0; i < programa.size(); i++) {
                espera += evaluador.esperaPonderada(
                        programa.get(i), metricas.get(i), estado.getReloj());
            }
            return espera;
        });
    }

    private double costoDe(Vehiculo unidad) {
        return costoVigente.computeIfAbsent(unidad.getId(), id -> {
            List<Ruta> programa = programaDe(unidad);
            if (programa.isEmpty()) {
                return 0.0;
            }
            List<MetricasRuta> metricas =
                    evaluador.medidasDe(unidad, programa, estado.getReloj());
            double costo = 0.0;
            for (MetricasRuta medida : metricas) {
                if (!medida.factible()) {
                    return Double.POSITIVE_INFINITY;
                }
                costo += medida.costoTotal();
            }
            return costo;
        });
    }

    /**
     * Que viajes del plan son de esta unidad, por posicion en la lista de rutas.
     *
     * El mapa se memoriza porque construirlo recorre todas las rutas, y esto se consulta dos veces
     * por cada candidato de insercion -una para el programa con el pedido dentro y otra para el
     * costo vigente-, o sea millones de veces por planificacion.
     *
     * Solo se olvida cuando cambia la <b>estructura</b> de rutas: al anadir un viaje nuevo, al
     * depurar los vacios y al repartir por partes. Quitar una entrega o meterla en un viaje que ya
     * existe no mueve ningun indice, asi que el mapa sigue valiendo.
     */
    private List<Integer> indicesDe(Vehiculo unidad) {
        if (indicesPorUnidad == null) {
            indicesPorUnidad = Evaluador.indicesPorVehiculo(solucion.getRutas());
        }
        return indicesPorUnidad.getOrDefault(unidad.getId(), List.of());
    }

    /** La lista de rutas cambio de tamano: el mapa de indices ya no vale. */
    private void olvidarIndices() {
        indicesPorUnidad = null;
    }

    private List<Ruta> programaDe(Vehiculo unidad) {
        List<Ruta> programa = new ArrayList<>();
        for (int indice : indicesDe(unidad)) {
            programa.add(solucion.getRuta(indice));
        }
        return programa;
    }

    /** Viajes cuyo radio de operación queda cerca del destino del pedido. */
    private List<Integer> viajesCercanos(Pedido pedido, int cuantos) {
        int total = solucion.getCantidadRutas();
        List<Integer> indices = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            indices.add(i);
        }
        if (total <= cuantos) {
            return indices;
        }
        double[] cercania = new double[total];
        for (int i = 0; i < total; i++) {
            cercania[i] = cercaniaDe(solucion.getRuta(i), pedido.getDestino());
        }
        indices.sort(Comparator.comparingDouble(i -> cercania[i]));
        return indices.subList(0, cuantos);
    }

    private double cercaniaDe(Ruta viaje, Ubicacion destino) {
        double menor = destino.distanciaManhattanKm(viaje.getAlmacen().getUbicacion());
        for (Pedido entrega : viaje.getPedidos()) {
            menor = Math.min(menor, destino.distanciaManhattanKm(entrega.getDestino()));
        }
        return menor;
    }

    /**
     * Unidades con capacidad suficiente más cercanas, tomando al menos una cuota de cada tipo.
     *
     * La cuota importa al arrancar: toda la flota parte del mismo almacén, así que todas empatan
     * en distancia y un recorte por cercanía a secas se quedaría siempre con las primeras de la
     * lista, dejando fuera a los autos.
     */
    private List<Vehiculo> unidadesCercanas(Pedido pedido, int cuantas) {
        Map<String, List<Vehiculo>> porTipo = new LinkedHashMap<>();
        for (Vehiculo unidad : disponibles) {
            if (pedido.getCantidad() <= unidad.getCapacidad()) {
                porTipo.computeIfAbsent(unidad.getTipo().name(), t -> new ArrayList<>()).add(unidad);
            }
        }
        if (porTipo.isEmpty()) {
            return List.of();
        }

        Comparator<Vehiculo> porCercania = Comparator.comparingDouble(
                (Vehiculo u) -> pedido.getDestino().distanciaManhattanKm(u.getUbicacion()))
                .thenComparing(Vehiculo::getId);

        int cuota = Math.max(1, cuantas / porTipo.size());
        List<Vehiculo> elegidas = new ArrayList<>();
        List<Vehiculo> resto = new ArrayList<>();
        for (List<Vehiculo> delTipo : porTipo.values()) {
            delTipo.sort(porCercania);
            for (int i = 0; i < delTipo.size(); i++) {
                (i < cuota ? elegidas : resto).add(delTipo.get(i));
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

    /** Mejor destino de un pedido y el siguiente, para calcular el arrepentimiento. */
    record Alternativas(Opcion mejor, Opcion segunda) {

        double arrepentimiento() {
            if (mejor == null) {
                return Double.NEGATIVE_INFINITY;
            }
            return segunda == null
                    ? Double.POSITIVE_INFINITY
                    : segunda.incremento() - mejor.incremento();
        }
    }

    /** Una forma concreta de meter un pedido en el plan, con lo que encarece a su unidad. */
    record Opcion(
            Pedido pedido,
            Vehiculo unidad,
            Almacen almacen,
            int indiceRuta,
            int posicion,
            boolean viajeNuevo,
            double incremento
    ) {
    }
}
