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
    public static long PLANES;
    public static long METRICAS;

    /**
     * Holgura a partir de la cual un pedido deja de considerarse urgente, en horas.
     *
     * Con mas de un dia de margen, posponer un pedido no compromete nada: entra sin problema en
     * cualquiera de las planificaciones que vienen. Por debajo, cada hora que pasa lo acerca a no
     * poder ser servido nunca.
     */
    public static final double HOLGURA_SIN_URGENCIA_HORAS = 24.0;

    /**
     * Cuanto llega a pesar un producto de un pedido sin holgura frente a uno holgado.
     *
     * Tiene que superar la cantidad tipica de un pedido -entre 1 y 10 unidades en el caso- para
     * que dejar afuera un pedido urgente de una unidad salga mas caro que dejar afuera uno
     * holgado de diez. Con 20, el urgente de uno pesa 20 y el holgado de diez pesa 10.
     */
    public static final double URGENCIA_MAXIMA = 20.0;

    /**
     * Escala de la espera, en horas: a partir de unas pocas veces este valor, aplazar mas da
     * igual. Se elige del orden de la ventana que el simulador llega a ejecutar.
     */
    public static final double HORAS_DE_REFERENCIA_DE_ESPERA = 1.0;

    /** Urgencias ya calculadas para el instante de planificacion en curso. */
    private final Map<String, Double> urgencias = new HashMap<>();
    private LocalDateTime relojDeLasUrgencias;

    /** Cuantos programas distintos se recuerdan antes de empezar a olvidar los mas viejos. */
    private static final int PROGRAMAS_EN_CACHE = 20_000;

    /**
     * Metricas ya calculadas, por programa de unidad.
     *
     * <h2>Por que hace falta</h2>
     *
     * Las fases de mejora puntuan miles de vecinos por planificacion, y cada puntuacion evalua el
     * plan entero. Pero un movimiento toca una o dos unidades: las otras treinta y cinco tienen el
     * mismo programa que en la puntuacion anterior y dan exactamente el mismo resultado. Medido
     * sobre una planificacion real de 57 pedidos, la busqueda tabu hacia 17 770 evaluaciones de
     * plan y 706 627 de viaje, casi todas repetidas.
     *
     * <h2>Por que por contenido y no por invalidacion</h2>
     *
     * Se podria anotar que unidades toca cada movimiento y olvidar solo esas. Pero hay seis tipos
     * de movimiento, mas la creacion de viajes, mas el deshacer de la evaluacion de vecinos, y un
     * solo olvido que falte da un costo equivocado sin que nada falle a la vista: la busqueda
     * elegiria mal y no habria forma de notarlo.
     *
     * La clave es el propio contenido del programa, asi que no hay nada que invalidar. Construirla
     * recorre los pedidos del programa, que es muchisimo mas barato que medirlo: medir consulta al
     * enrutador, y el enrutador hace busquedas en anchura sobre la reticula.
     */
    private final Map<String, List<MetricasRuta>> programasMedidos =
            new LinkedHashMap<>(PROGRAMAS_EN_CACHE * 2, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, List<MetricasRuta>> vieja) {
                    return size() > PROGRAMAS_EN_CACHE;
                }
            };
    private LocalDateTime relojDeLosProgramas;

    /**
     * Una hora por <b>pedido</b>, sin importar la cantidad de producto que se deja ni en cuantas
     * visitas se reparta. Ocupa a la unidad pero no cuenta contra el plazo comprometido con el
     * cliente.
     *
     * De un pedido partido la paga solo la primera parte.
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
     * <h2>La entrega tiene ventana, no solo plazo</h2>
     *
     * El plazo es el borde superior. El inferior es la fecha de registro: no se entrega un pedido
     * que el cliente todavía no hizo. Si la unidad llega antes, espera; el kilometraje no cambia
     * y el costo tampoco, pero la unidad queda ocupada ese rato y eso retrasa lo que venga
     * después en su programa, que es lo que corresponde.
     *
     * Con la lectura clásica —solo entran los pedidos ya llegados— el borde inferior nunca se
     * activa, porque la planificación arranca después del registro de todo lo que ve. Se vuelve
     * indispensable con la lectura por bloques, donde el planificador alcanza a ver pedidos que
     * se registrarán durante el bloque que está por ejecutarse.
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
        METRICAS++;
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

            // Borde inferior de la ventana: no se puede entregar un pedido que todavía no se
            // registró. Si la unidad llega antes, espera. Solo ocurre cuando la planificación
            // lee por bloques y alcanza a ver pedidos que llegarán durante el bloque.
            if (llegada.isBefore(pedido.getFechaRegistro())) {
                llegada = pedido.getFechaRegistro();
            }
            horasLlegada.put(pedido.getId(), llegada);

            if (llegada.isAfter(pedido.getFechaLimite())) {
                return MetricasRuta.infactible();
            }

            // Una hora por pedido y no por visita: de un pedido partido, solo la primera parte
            // ocupa a su unidad acondicionando.
            reloj = pedido.pagaAcondicionamiento()
                    ? Turnos.avanzar(llegada, HORAS_ACONDICIONAMIENTO_POR_ENTREGA)
                    : llegada;
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

    /**
     * Las metricas del programa de una unidad, reutilizandolas si ya se midio uno identico.
     *
     * Dos programas con la misma unidad, los mismos viajes en el mismo orden y los mismos pedidos
     * en las mismas posiciones dan el mismo resultado, porque nada mas entra en el calculo: el
     * instante de planificacion es fijo dentro de una corrida y el estado de la unidad va en la
     * clave.
     */
    public List<MetricasRuta> medidasDe(
            Vehiculo vehiculo,
            List<Ruta> viajes,
            LocalDateTime horaInicio
    ) {
        if (!horaInicio.equals(relojDeLosProgramas)) {
            programasMedidos.clear();
            relojDeLosProgramas = horaInicio;
        }
        return programasMedidos.computeIfAbsent(
                firmaDe(vehiculo, viajes), clave -> evaluarPrograma(vehiculo, viajes, horaInicio));
    }

    /**
     * Identifica un programa por todo lo que puede cambiar su medicion.
     *
     * De la unidad entran los cuatro datos que usa {@link #evaluarPrograma}: quien es -de ahi
     * salen velocidad y costo-, donde esta, desde cuando esta libre y cuanto lleva encima. De cada
     * viaje, el almacen del que sale, si sale ya cargado y la secuencia exacta de entregas.
     */
    private static String firmaDe(Vehiculo vehiculo, List<Ruta> viajes) {
        StringBuilder firma = new StringBuilder(64);
        firma.append(vehiculo.getId()).append('@').append(vehiculo.getUbicacion())
                .append('/').append(vehiculo.getDisponibleDesde())
                .append('/').append(vehiculo.getCargaABordo())
                .append('/').append(vehiculo.getEstado());
        for (Ruta viaje : viajes) {
            firma.append('|').append(viaje.getAlmacen().getId())
                    .append(viaje.saleYaCargado() ? '#' : '-');
            for (Pedido pedido : viaje.getPedidos()) {
                firma.append(',').append(pedido.getId());
            }
        }
        return firma.toString();
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
        PLANES++;
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

            List<MetricasRuta> medidas = medidasDe(vehiculo, susViajes, estado.getReloj());
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
     *   objetivo = costo de operación + penalidadNoAsignado x productos que nadie atiende
     * </pre>
     *
     * El orden entre los dos criterios no es negociable: primero cubrir, después abaratar. Entre
     * dos planes que atienden a los mismos clientes gana siempre el más barato, y ningún ahorro
     * compra dejar un producto afuera.
     *
     * <h2>Se cuentan productos, no pedidos</h2>
     *
     * La unidad de la operación es el producto: es lo que ocupa capacidad, lo que descuenta el
     * inventario y lo que un pedido partido reparte entre varias unidades. Penalizar por pedido
     * haría que dejar sin atender uno de quince unidades costara lo mismo que dejar uno de una, y
     * al planificador le convendría sacrificar siempre el grande, porque cuesta igual y libera
     * quince veces más capacidad. Con productos, dejar afuera al grande cuesta quince veces más,
     * que es lo que corresponde.
     *
     * <h2>Y se ponderan por urgencia</h2>
     *
     * Contar productos a secas deja el objetivo ciego al plazo, y eso acorta la operación. Dentro
     * de una planificación todo lo asignado llega a tiempo, porque el plazo es restricción dura;
     * la única decisión real es <b>qué se deja afuera</b>. Para durar hay que dejar afuera lo que
     * todavía se puede servir después, o sea lo holgado. Sin ponderar, la búsqueda elige por
     * tamaño, que no dice nada de la urgencia: un movimiento que saca un pedido urgente de una
     * unidad y mete uno holgado de diez aparecía como una mejora.
     *
     * Por eso cada producto sin atender pesa por {@link #urgencia}, que vale 1 con holgura
     * sobrada y sube hasta {@link #URGENCIA_MAXIMA} cuando ya no queda margen. La holgura se mide
     * contra el <b>límite efectivo</b> y no contra el plazo, así que un cliente cuya esquina se
     * cierra en una hora cuenta como urgente aunque su plazo nominal sea largo.
     *
     * La penalidad funciona como orden lexicográfico y no como un canje real porque domina por
     * un orden de magnitud lo que se juega en el margen: sumar una entrega más a un plan cuesta
     * unos cientos de soles —el desvío hasta el cliente y lo que retrasa al resto del viaje—,
     * contra los 10 000 por producto de dejarla sin atender. Lo que importa es esa comparación
     * marginal, no el costo total del plan.
     *
     * Devuelve infinito si el plan viola alguna restricción, de modo que la búsqueda nunca puede
     * elegir un plan inviable por barato que parezca.
     */
    public double objetivo(Solucion solucion, EstadoOperacion estado, Parametros parametros) {
        ResultadoPlan resultado = evaluarPlan(solucion, estado);
        if (!resultado.factible()) {
            return Double.POSITIVE_INFINITY;
        }
        double espera = 0.0;
        List<Ruta> viajes = solucion.getRutas();
        for (int i = 0; i < viajes.size(); i++) {
            espera += esperaPonderada(
                    viajes.get(i), resultado.metricasPorRuta().get(i), estado.getReloj());
        }

        return resultado.costoOperacion()
                + penalidadPorNoAsignados(solucion, estado, parametros)
                + parametros.getPenalidadEspera() * espera;
    }

    /**
     * Cuanto se hace esperar a los clientes de este viaje, en producto-hora ponderado por urgencia.
     *
     * Es la suma de los tiempos de entrega, que es el termino que impide que el planificador
     * aplace: un plan que consolida todo en pocos viajes recorre menos kilometros pero entrega
     * mucho mas tarde, y el simulador solo llega a ejecutar el principio de cada plan.
     */
    public double esperaPonderada(Ruta viaje, MetricasRuta medida, LocalDateTime reloj) {
        if (medida == null || !medida.factible()) {
            return 0.0;
        }
        double espera = 0.0;
        for (Pedido pedido : viaje.getPedidos()) {
            LocalDateTime llegada = medida.horasLlegada().get(pedido.getId());
            if (llegada == null) {
                continue;
            }
            espera += esperaDe(pedido, llegada, reloj);
        }
        return espera;
    }

    /**
     * Lo que pesa hacer esperar a un cliente concreto hasta la hora indicada.
     *
     * Lo usan tambien los constructivos para valorar una insercion suelta, de modo que todos
     * midan la espera con la misma regla.
     */
    public double esperaDe(Pedido pedido, LocalDateTime llegada, LocalDateTime reloj) {
        return pedido.getCantidad()
                * urgencia(pedido, reloj)
                * valorDeLaEspera(horasEntre(reloj, llegada));
    }

    /**
     * Cuanto pesa una espera de tantas horas, entre 0 y 1.
     *
     * <h2>Por que decae en vez de crecer sin limite</h2>
     *
     * La version lineal -cada hora cuesta lo mismo- premia igual adelantar una entrega de la hora
     * ocho a la siete que de la hora uno a la cero. Operativamente solo lo segundo cambia algo: el
     * simulador ejecuta media hora de cada plan y rehace el resto, asi que lo que ocurre a partir
     * de la tercera o cuarta hora es una intencion que se va a reescribir de todos modos.
     *
     * Con la version lineal se veia el sintoma: al darle mas presupuesto a la busqueda, los
     * kilometros bajaban y las entregas <b>tambien</b>. El optimizador perseguia con mas eficacia
     * una meta que no era la nuestra.
     *
     * Con esta forma, la diferencia se concentra donde de verdad decide. Con la escala de una
     * hora, una entrega a los quince minutos pesa 0,22; a la media hora, 0,39; a las dos horas,
     * 0,86; y de las cuatro en adelante practicamente 1, o sea que aplazarla mas ya no cambia
     * nada. Es acotada, asi que el termino no puede dominar al costo por muy largo que sea el
     * horizonte.
     */
    public static double valorDeLaEspera(double horas) {
        return 1.0 - Math.exp(-Math.max(0.0, horas) / HORAS_DE_REFERENCIA_DE_ESPERA);
    }

    /**
     * Lo que cuesta la parte del plan que nadie atiende: productos sin asignar, cada uno pesado
     * por la urgencia de su pedido.
     *
     * Vive aparte de {@link #objetivo} porque ALNS calcula el costo de operación por su cuenta
     * -lo cachea por unidad- y necesita este término sin volver a evaluar el plan entero.
     */
    public double penalidadPorNoAsignados(
            Solucion solucion,
            EstadoOperacion estado,
            Parametros parametros
    ) {
        double penalidad = 0.0;
        for (Pedido pedido : solucion.getPedidosNoAsignados()) {
            penalidad += pedido.getCantidad() * urgencia(pedido, estado.getReloj());
        }
        return parametros.getPenalidadNoAsignado() * penalidad;
    }

    /**
     * Cuánto pesa de más un producto de este pedido por lo poco que le queda de margen.
     *
     * Vale 1 con {@link #HOLGURA_SIN_URGENCIA_HORAS} o más de holgura y sube linealmente hasta
     * {@link #URGENCIA_MAXIMA} cuando la holgura llega a cero. Es continua a propósito: con un
     * escalón, dos pedidos casi iguales pesarían muy distinto y la búsqueda saltaría entre ellos
     * sin que el plan mejore de verdad.
     *
     * Se calcula contra el límite efectivo, que ya descuenta el cierre de la esquina del cliente.
     */
    public double urgencia(Pedido pedido, LocalDateTime reloj) {
        if (!reloj.equals(relojDeLasUrgencias)) {
            urgencias.clear();
            relojDeLasUrgencias = reloj;
        }
        return urgencias.computeIfAbsent(pedido.getId(), id -> {
            double holgura = horasEntre(reloj, limiteEfectivo(pedido, reloj));
            if (holgura >= HOLGURA_SIN_URGENCIA_HORAS) {
                return 1.0;
            }
            if (holgura <= 0) {
                return URGENCIA_MAXIMA;
            }
            double cercania = 1.0 - holgura / HOLGURA_SIN_URGENCIA_HORAS;
            return 1.0 + (URGENCIA_MAXIMA - 1.0) * cercania;
        });
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
