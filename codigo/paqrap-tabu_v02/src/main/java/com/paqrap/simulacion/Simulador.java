package com.paqrap.simulacion;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.CalculadorDistancia;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Inventario;
import com.paqrap.planificador.MapaBloqueos;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Operación simulada con replanificación periódica.
 *
 * El caso pide resolver tres escenarios con el mismo planificador: el día a día, la simulación
 * de cinco días y la simulación hasta el colapso. Los tres son la misma mecánica con distinto
 * horizonte y distinta demanda, así que aquí vive una sola: el reloj avanza a saltos fijos y,
 * en cada salto, se vuelve a planificar con lo que se sabe en ese momento.
 *
 * <h2>Cuándo se replanifica</h2>
 *
 * No en una grilla fija sino cuando pasa algo que puede cambiar la decisión:
 *
 * <ul>
 *   <li>llega un pedido nuevo, que es lo que permite reaccionar a un encargo urgente sin
 *       esperar al siguiente turno de reloj;</li>
 *   <li>empieza o termina un bloqueo, porque una calle que se cierra puede dejar sin camino a
 *       una ruta ya planificada;</li>
 *   <li>y, si no ocurre nada de lo anterior, un latido cada {@code intervaloMaximo}.</li>
 * </ul>
 *
 * Entre dos replanificaciones se respeta un {@code intervaloMinimo}: sin él, una ráfaga de
 * pedidos dispararía decenas de planificaciones seguidas sin que la operación avance un metro.
 *
 * <h2>Qué se ejecuta en cada salto</h2>
 *
 * El plan no se ejecuta completo: solo hasta donde alcanza el reloj. Una unidad avanza por su
 * programa mientras haya salido hacia el punto siguiente antes del corte, de modo que un tramo
 * ya iniciado se completa aunque termine después. Lo que todavía no había empezado se descarta
 * y vuelve a decidirse en la siguiente planificación, que es lo que permite reaccionar a los
 * pedidos nuevos.
 *
 * <h2>Colapso</h2>
 *
 * El planificador nunca acepta una entrega fuera de plazo, así que un pedido que no alcanza a
 * ser atendido se queda esperando. Cuando su fecha límite pasa sin que se haya entregado, la
 * operación colapsó.
 */
public final class Simulador {
    /** Tiempo máximo sin replanificar, aunque no ocurra ningún evento. */
    public static final Duration INTERVALO_POR_DEFECTO = Duration.ofMinutes(30);

    /** Tiempo mínimo entre dos replanificaciones, para no rehacer el plan sin avanzar. */
    public static final Duration INTERVALO_MINIMO_POR_DEFECTO = Duration.ofMinutes(5);

    /**
     * Cuántos pedidos entran a cada planificación, empezando por los de plazo más apretado.
     *
     * Una cola de miles de pedidos no se replanifica entera cada media hora, ni en la operación
     * real ni aquí: se atiende lo más urgente y el resto espera al siguiente salto. El recorte
     * no falsea el colapso, porque los pedidos que están por vencer son justamente los primeros
     * de la cola y nunca quedan fuera.
     */
    public static final int PEDIDOS_POR_ITERACION = 200;

    private final Planificador planificador;
    private final Evaluador evaluador;
    private final CalculadorDistancia distancias;
    private final Parametros parametros;
    private final List<Almacen> almacenes;
    private final Duration intervalo;
    private final boolean detenerAlColapsar;
    private final int pedidosPorIteracion;
    private PlanMantenimiento mantenimiento = PlanMantenimiento.vacio();
    private String pedidoEnSeguimiento;
    private MapaBloqueos bloqueos = MapaBloqueos.vacio();
    private Duration intervaloMinimo = INTERVALO_MINIMO_POR_DEFECTO;

    public Simulador(
            Planificador planificador,
            CalculadorDistancia distancias,
            List<Almacen> almacenes,
            Parametros parametros
    ) {
        this(planificador, distancias, almacenes, parametros, INTERVALO_POR_DEFECTO, false);
    }

    public Simulador(
            Planificador planificador,
            CalculadorDistancia distancias,
            List<Almacen> almacenes,
            Parametros parametros,
            Duration intervalo,
            boolean detenerAlColapsar
    ) {
        this(planificador, distancias, almacenes, parametros, intervalo, detenerAlColapsar,
                PEDIDOS_POR_ITERACION);
    }

    public Simulador(
            Planificador planificador,
            CalculadorDistancia distancias,
            List<Almacen> almacenes,
            Parametros parametros,
            Duration intervalo,
            boolean detenerAlColapsar,
            int pedidosPorIteracion
    ) {
        if (pedidosPorIteracion <= 0) {
            throw new IllegalArgumentException("pedidosPorIteracion debe ser mayor que cero.");
        }
        this.pedidosPorIteracion = pedidosPorIteracion;
        this.planificador = Objects.requireNonNull(planificador);
        this.distancias = Objects.requireNonNull(distancias);
        this.evaluador = new Evaluador(distancias);
        this.almacenes = List.copyOf(almacenes);
        this.parametros = Objects.requireNonNull(parametros);
        this.intervalo = Objects.requireNonNull(intervalo);
        this.detenerAlColapsar = detenerAlColapsar;
    }

    /**
     * Plan de mantenimiento preventivo a respetar. Una unidad programada no recibe rutas durante
     * ese día.
     *
     * Queda pendiente la parte prospectiva: hoy la unidad simplemente deja de estar disponible
     * al llegar el día, en lugar de evitarse desde antes que se comprometa con entregas que
     * crucen esa fecha.
     */
    public Simulador conMantenimiento(PlanMantenimiento plan) {
        this.mantenimiento = Objects.requireNonNull(plan);
        return this;
    }

    /**
     * Bloqueos cuyos cambios disparan una replanificación.
     *
     * Sin esto la operación solo reacciona en el latido: una calle que se cierra puede dejar a
     * una unidad comprometida con un camino que ya no existe hasta media hora después.
     */
    public Simulador conEventosDeBloqueo(MapaBloqueos mapa) {
        this.bloqueos = Objects.requireNonNull(mapa);
        return this;
    }

    /** Tiempo mínimo entre replanificaciones. */
    public Simulador conIntervaloMinimo(Duration minimo) {
        this.intervaloMinimo = Objects.requireNonNull(minimo);
        return this;
    }

    /**
     * Sigue a un pedido a lo largo de la corrida e informa, en cada replanificación, si entró a
     * la planificación, si el plan lo asignó, a qué unidad y en qué lugar de su programa.
     *
     * Sirve para entender por qué un pedido concreto termina venciendo: casi nunca es que no
     * hubiera forma de atenderlo, sino que el plan lo deja siempre para más adelante.
     */
    public Simulador siguiendoA(String pedidoId) {
        this.pedidoEnSeguimiento = pedidoId;
        return this;
    }

    /**
     * @param ventas pedidos con su fecha de llegada; los futuros se incorporan cuando el reloj
     *               los alcanza, igual que en la operación real.
     */
    public ResumenSimulacion correr(
            LocalDateTime inicio,
            Duration horizonte,
            List<Pedido> ventas,
            List<Vehiculo> flota
    ) {
        LocalDateTime fin = inicio.plus(horizonte);

        List<Pedido> porLlegar = new ArrayList<>(ventas);
        porLlegar.sort((uno, otro) -> uno.getFechaRegistro().compareTo(otro.getFechaRegistro()));

        Map<String, Vehiculo> unidades = new LinkedHashMap<>();
        for (Vehiculo unidad : flota) {
            unidades.put(unidad.getId(), unidad);
        }

        Inventario inventario = new Inventario(almacenes, inicio);
        List<Pedido> pendientes = new ArrayList<>();
        List<Entrega> entregas = new ArrayList<>();
        List<Pedido> vencidos = new ArrayList<>();
        List<Pedido> inalcanzables = new ArrayList<>();

        Map<LocalDate, Integer> recibidosPorDia = new LinkedHashMap<>();
        Map<LocalDate, Integer> entregasPorDia = new LinkedHashMap<>();
        Map<LocalDate, Integer> colaAlCierreDelDia = new LinkedHashMap<>();

        Acumulado acumulado = new Acumulado();
        int recibidos = 0;
        int iteraciones = 0;
        long computo = 0L;
        LocalDateTime reloj = inicio;

        while (reloj.isBefore(fin)) {
            // Primero entran los pedidos que ya llegaron: recién entonces la cabeza de la cola
            // es la próxima llegada de verdad, que es el evento que dispara el corte siguiente.
            while (!porLlegar.isEmpty() && !porLlegar.get(0).getFechaRegistro().isAfter(reloj)) {
                Pedido llegado = porLlegar.remove(0);
                pendientes.add(llegado);
                recibidos++;
                recibidosPorDia.merge(llegado.getFechaRegistro().toLocalDate(), 1, Integer::sum);
            }

            LocalDateTime corte = proximoCorte(reloj, fin, porLlegar);

            if (!pendientes.isEmpty()) {
                EstadoOperacion estado = new EstadoOperacion(
                        reloj,
                        losMasUrgentes(pendientes, reloj),
                        almacenesEn(reloj, inventario),
                        disponiblesEn(reloj, unidades)
                );

                long antes = System.currentTimeMillis();
                Solucion plan = planificador.planificar(estado, parametros);
                computo += System.currentTimeMillis() - antes;
                iteraciones++;

                if (pedidoEnSeguimiento != null) {
                    informarSeguimiento(reloj, estado, plan, pendientes);
                }

                int antesDeEjecutar = entregas.size();
                ejecutar(plan, reloj, corte, unidades, inventario, pendientes, entregas, acumulado);
                for (int i = antesDeEjecutar; i < entregas.size(); i++) {
                    entregasPorDia.merge(entregas.get(i).llegada().toLocalDate(), 1, Integer::sum);
                }
            }

            for (Pedido pendiente : List.copyOf(pendientes)) {
                if (pendiente.getFechaLimite().isBefore(corte)) {
                    vencidos.add(pendiente);
                    if (!huboMomentoDeEntregarlo(pendiente, inicio)) {
                        inalcanzables.add(pendiente);
                    }
                    pendientes.remove(pendiente);
                }
            }

            colaAlCierreDelDia.put(corte.toLocalDate(), pendientes.size());
            reloj = corte;

            if (detenerAlColapsar && !vencidos.isEmpty()) {
                break;
            }
        }

        int enPlazo = 0;
        for (Entrega entrega : entregas) {
            if (entrega.enPlazo()) {
                enPlazo++;
            }
        }

        Pedido primerVencido = vencidos.isEmpty() ? null : vencidos.get(0);
        return new ResumenSimulacion(
                inicio,
                reloj,
                recibidos,
                entregas.size(),
                pendientes.size(),
                enPlazo,
                acumulado.kilometros,
                acumulado.costo,
                iteraciones,
                computo,
                primerVencido,
                primerVencido == null ? null : primerVencido.getFechaLimite(),
                List.copyOf(vencidos),
                List.copyOf(inalcanzables),
                Map.copyOf(recibidosPorDia),
                Map.copyOf(entregasPorDia),
                Map.copyOf(colaAlCierreDelDia)
        );
    }

    /**
     * Avanza cada unidad por su programa hasta donde llega el reloj.
     *
     * La regla es la de una operación real: una unidad se compromete con el punto al que ya
     * salió. Si en el instante de corte todavía estaba en camino, ese tramo se completa; lo que
     * no había empezado se deja para la siguiente planificación.
     */
    private void ejecutar(
            Solucion plan,
            LocalDateTime reloj,
            LocalDateTime corte,
            Map<String, Vehiculo> unidades,
            Inventario inventario,
            List<Pedido> pendientes,
            List<Entrega> entregas,
            Acumulado acumulado
    ) {
        List<Ruta> viajesDelPlan = plan.getRutas();

        for (Map.Entry<String, List<Integer>> programa
                : Evaluador.indicesPorVehiculo(viajesDelPlan).entrySet()) {

            List<Ruta> viajes = new ArrayList<>();
            for (int indice : programa.getValue()) {
                viajes.add(viajesDelPlan.get(indice));
            }

            Vehiculo unidad = viajes.get(0).getVehiculo();
            List<MetricasRuta> metricas = evaluador.evaluarPrograma(unidad, viajes, reloj);

            Ubicacion posicion = unidad.getUbicacion();
            LocalDateTime libre = Evaluador.libreDesde(unidad, reloj);

            for (int i = 0; i < viajes.size(); i++) {
                MetricasRuta medida = metricas.get(i);
                Ruta viaje = viajes.get(i);
                if (!medida.factible() || viaje.estaVacia()) {
                    break;
                }
                if (libre.isAfter(corte)) {
                    // Al llegar el corte la unidad aún no había salido hacia este almacén.
                    break;
                }

                double hastaElAlmacen = recorrido(
                        posicion, viaje.getAlmacen().getUbicacion(), libre, unidad);
                if (Double.isInfinite(hastaElAlmacen)) {
                    break;
                }
                acumulado.sumar(hastaElAlmacen, unidad.getCostoPorKm());
                posicion = viaje.getAlmacen().getUbicacion();
                libre = medida.horaCarga();

                boolean interrumpido = false;
                for (Pedido pedido : viaje.getPedidos()) {
                    if (libre.isAfter(corte)) {
                        interrumpido = true;
                        break;
                    }
                    if (!inventario.tieneStock(viaje.getAlmacen(), medida.horaCarga(), pedido.getCantidad())) {
                        interrumpido = true;
                        break;
                    }

                    double tramo = recorrido(posicion, pedido.getDestino(), libre, unidad);
                    if (Double.isInfinite(tramo)) {
                        interrumpido = true;
                        break;
                    }

                    inventario.consumir(viaje.getAlmacen(), medida.horaCarga(), pedido.getCantidad());
                    acumulado.sumar(tramo, unidad.getCostoPorKm());

                    LocalDateTime llegada = medida.horasLlegada().get(pedido.getId());
                    entregas.add(new Entrega(
                            pedido, unidad, viaje.getAlmacen(), llegada,
                            tramo, tramo * unidad.getCostoPorKm()));
                    pendientes.remove(pedido);

                    posicion = pedido.getDestino();
                    libre = llegada.plusHours((long) Evaluador.HORAS_ACONDICIONAMIENTO_POR_ENTREGA);
                }

                if (interrumpido) {
                    break;
                }
            }

            unidades.put(unidad.getId(), unidad.tras(posicion, libre));
        }
    }

    /**
     * Hasta cuándo avanza el reloj antes de volver a planificar.
     *
     * Se toma el primer evento que ocurra después del intervalo mínimo —la llegada de un pedido
     * o un cambio en los bloqueos— y, si no hay ninguno cerca, el latido.
     */
    private LocalDateTime proximoCorte(
            LocalDateTime reloj,
            LocalDateTime fin,
            List<Pedido> porLlegar
    ) {
        LocalDateTime piso = reloj.plus(intervaloMinimo);
        LocalDateTime corte = reloj.plus(intervalo);

        if (!porLlegar.isEmpty()) {
            LocalDateTime llegada = porLlegar.get(0).getFechaRegistro();
            if (llegada.isAfter(reloj) && llegada.isBefore(corte)) {
                corte = llegada;
            }
        }

        LocalDateTime cambioDeBloqueo = bloqueos.proximoCambioDesde(reloj.plusNanos(1));
        if (cambioDeBloqueo != null && cambioDeBloqueo.isBefore(corte)) {
            corte = cambioDeBloqueo;
        }

        if (corte.isBefore(piso)) {
            corte = piso;
        }
        return corte.isAfter(fin) ? fin : corte;
    }

    /**
     * ¿Existió algún momento de su ventana en que una unidad hubiera podido entregarlo?
     *
     * Se recorren los instantes de replanificación entre la llegada del pedido y su fecha
     * límite, y en cada uno se comprueba si desde algún almacén se alcanzaba el destino a
     * tiempo, usando la unidad más rápida de la flota. Si la respuesta es que no en todos, el
     * destino estuvo cerrado o aislado durante toda la ventana y el vencimiento no es
     * atribuible a la operación: ninguna unidad habría llegado.
     *
     * Es una cota optimista a propósito —ignora si la unidad estaba libre y si tenía carga—,
     * porque solo busca descartar lo físicamente imposible.
     */
    private boolean huboMomentoDeEntregarlo(Pedido pedido, LocalDateTime inicioDeLaCorrida) {
        double velocidadMaxima = 0.0;
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            velocidadMaxima = Math.max(
                    velocidadMaxima, tipo.getEspecificacionDelCaso().velocidadKmH());
        }

        LocalDateTime instante = pedido.getFechaRegistro().isBefore(inicioDeLaCorrida)
                ? inicioDeLaCorrida
                : pedido.getFechaRegistro();

        while (!instante.isAfter(pedido.getFechaLimite())) {
            for (Almacen almacen : almacenes) {
                double km = distancias.calcularKm(
                        almacen.getUbicacion(), pedido.getDestino(), instante, velocidadMaxima);
                if (Double.isInfinite(km)) {
                    continue;
                }
                LocalDateTime llegada = Evaluador.sumarHoras(instante, km / velocidadMaxima);
                if (!llegada.isAfter(pedido.getFechaLimite())) {
                    return true;
                }
            }
            instante = instante.plus(intervalo);
        }
        return false;
    }

    /** Qué pasa con el pedido seguido en esta replanificación. */
    private void informarSeguimiento(
            LocalDateTime reloj,
            EstadoOperacion estado,
            Solucion plan,
            List<Pedido> pendientes
    ) {
        Pedido seguido = null;
        for (Pedido pendiente : pendientes) {
            if (pendiente.getId().equals(pedidoEnSeguimiento)) {
                seguido = pendiente;
                break;
            }
        }
        if (seguido == null) {
            return;
        }

        boolean entroAPlanificar = estado.getPedidos().contains(seguido);
        String ubicacionEnElPlan = "no asignado";

        List<Ruta> viajes = plan.getRutas();
        Map<String, List<Integer>> porVehiculo = Evaluador.indicesPorVehiculo(viajes);
        buscar:
        for (Map.Entry<String, List<Integer>> programa : porVehiculo.entrySet()) {
            int lugar = 0;
            for (int indice : programa.getValue()) {
                for (Pedido entrega : viajes.get(indice).getPedidos()) {
                    lugar++;
                    if (entrega.getId().equals(seguido.getId())) {
                        ubicacionEnElPlan = programa.getKey() + ", entrega " + lugar
                                + " de su programa";
                        break buscar;
                    }
                }
            }
        }

        System.out.printf(
                "  [seguimiento %s] %s | cola=%d | planificado=%s | %s%s%n",
                seguido.getId(), reloj, pendientes.size(),
                entroAPlanificar ? "si" : "NO (quedo fuera del corte)",
                ubicacionEnElPlan,
                ubicacionEnElPlan.equals("no asignado") ? porQueNoSeAsigna(seguido, reloj, estado) : "");
    }

    /**
     * Para un pedido que quedó sin asignar: cuál es la entrega más temprana que alguna unidad
     * podría lograr y por cuánto se pasa del plazo. Deja ver si el problema es que la flota está
     * ocupada, que el destino es inalcanzable, o que simplemente no da el tiempo.
     */
    private String porQueNoSeAsigna(Pedido pedido, LocalDateTime reloj, EstadoOperacion estado) {
        LocalDateTime masTemprana = null;
        String mejorUnidad = null;
        boolean algunCaminoAbierto = false;

        for (Vehiculo unidad : estado.getVehiculos()) {
            if (!unidad.getEstado().admiteAsignacion()
                    || pedido.getCantidad() > unidad.getCapacidad()) {
                continue;
            }
            LocalDateTime libre = Evaluador.libreDesde(unidad, reloj);
            double velocidad = unidad.getVelocidadKmH();

            for (Almacen almacen : almacenes) {
                double alAlmacen = distancias.calcularKm(
                        unidad.getUbicacion(), almacen.getUbicacion(), libre, velocidad);
                if (Double.isInfinite(alAlmacen)) {
                    continue;
                }
                LocalDateTime carga = Evaluador.sumarHoras(libre, alAlmacen / velocidad);
                double alCliente = distancias.calcularKm(
                        almacen.getUbicacion(), pedido.getDestino(), carga, velocidad);
                if (Double.isInfinite(alCliente)) {
                    continue;
                }
                algunCaminoAbierto = true;
                LocalDateTime llegada = Evaluador.sumarHoras(carga, alCliente / velocidad);
                if (masTemprana == null || llegada.isBefore(masTemprana)) {
                    masTemprana = llegada;
                    mejorUnidad = unidad.getId();
                }
            }
        }

        if (!algunCaminoAbierto) {
            return " | destino inalcanzable: ningun camino abierto";
        }
        long minutosTarde = java.time.Duration.between(pedido.getFechaLimite(), masTemprana).toMinutes();
        return String.format(" | lo antes posible %s con %s (%+d min respecto del plazo)",
                masTemprana.toLocalTime(), mejorUnidad, minutosTarde);
    }

    /**
     * La flota tal como está el día de hoy: las unidades con mantenimiento programado se marcan
     * fuera de servicio, de modo que el planificador no les asigne rutas.
     */
    private List<Vehiculo> disponiblesEn(LocalDateTime reloj, Map<String, Vehiculo> unidades) {
        List<Vehiculo> vigentes = new ArrayList<>(unidades.size());
        for (Vehiculo unidad : unidades.values()) {
            vigentes.add(mantenimiento.enMantenimiento(unidad.getId(), reloj)
                    ? unidad.con(EstadoVehiculo.EN_MANTENIMIENTO)
                    : unidad);
        }
        return vigentes;
    }

    /**
     * Los pedidos más apremiantes, que son los que entran a esta planificación.
     *
     * El recorte va por límite efectivo y no por plazo: un pedido cuya esquina se cierra dentro
     * de una hora es más urgente que uno de plazo más corto al que se puede llegar todo el día,
     * y dejarlo fuera del recorte es perderlo.
     */
    private List<Pedido> losMasUrgentes(List<Pedido> pendientes, LocalDateTime reloj) {
        if (pendientes.size() <= pedidosPorIteracion) {
            return List.copyOf(pendientes);
        }
        Map<String, LocalDateTime> limites = new HashMap<>();
        for (Pedido pedido : pendientes) {
            limites.put(pedido.getId(), evaluador.limiteEfectivo(pedido, reloj));
        }
        List<Pedido> ordenados = new ArrayList<>(pendientes);
        ordenados.sort((uno, otro) ->
                limites.get(uno.getId()).compareTo(limites.get(otro.getId())));
        return List.copyOf(ordenados.subList(0, pedidosPorIteracion));
    }

    /** Stock realmente disponible en cada almacén al momento de planificar. */
    private List<Almacen> almacenesEn(LocalDateTime reloj, Inventario inventario) {
        List<Almacen> vigentes = new ArrayList<>(almacenes.size());
        for (Almacen almacen : almacenes) {
            vigentes.add(almacen.esInventarioInfinito()
                    ? almacen
                    : Almacen.intermedio(
                            almacen.getId(),
                            almacen.getUbicacion(),
                            inventario.disponible(almacen, reloj)));
        }
        return vigentes;
    }

    private double recorrido(Ubicacion origen, Ubicacion destino, LocalDateTime salida, Vehiculo unidad) {
        return origen.equals(destino)
                ? 0.0
                : distancias.calcularKm(origen, destino, salida, unidad.getVelocidadKmH());
    }

    /** Kilómetros y costo realmente incurridos, incluidos los traslados sin carga. */
    private static final class Acumulado {
        private double kilometros;
        private double costo;

        private void sumar(double kilometros, double costoPorKm) {
            this.kilometros += kilometros;
            this.costo += kilometros * costoPorKm;
        }
    }
}
