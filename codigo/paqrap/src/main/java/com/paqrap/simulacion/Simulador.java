package com.paqrap.simulacion;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Ciudad;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Averia;
import com.paqrap.modelo.PlanAverias;
import com.paqrap.modelo.PlanMantenimiento;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Turnos;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.ruteo.CalculadorDistancia;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.CargaEnAlmacen;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Inventario;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
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
 * <h2>El ritmo: Ta, Sa y K</h2>
 *
 * Por defecto la corrida va a fondo: se planifica, se ejecuta y se salta al evento siguiente sin
 * esperar nada. Sirve para medir, no para mirar.
 *
 * Con {@link #conRitmo(Ritmo)} la corrida pasa a ir al paso de un reloj de verdad, que es lo que
 * hace falta para dibujar el mapa: cada salto dura {@code Sa} de tiempo real y consume
 * exactamente {@code Sc = Sa × K} de tiempo de la operación, sin que ningún evento lo adelante
 * —si lo adelantara, K dejaría de cumplirse—. El tiempo de ejecución del planificador,
 * {@code Ta}, se mide siempre —con ritmo o sin él— y queda en el resumen junto al tamaño de cola
 * de cada corrida, porque Ta no es un número sino un rango que crece con la cola.
 *
 * Fijar el ritmo no cambia ni una decisión del planificador: solo decide cuánto tiempo de
 * operación se resuelve por vez y cuánto se espera entre una corrida y la siguiente.
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
 * <h2>Qué pedidos ve cada planificación</h2>
 *
 * Por defecto, los que ya llegaron y siguen sin entregarse. Un pedido que se registra dentro de
 * un salto espera al corte siguiente, de modo que la latencia de planificación es igual al salto.
 *
 * Con {@link #leyendoPorBloques()} entran además los que se registrarán durante el tramo que está
 * por ejecutarse. La latencia baja a cero y el planificador puede agrupar una entrega con otra
 * cercana en el mismo viaje, en vez de mandar una unidad ahora y otra un salto después. Verlos no
 * es poder entregarlos antes de tiempo: el borde inferior de la ventana lo impide y la unidad que
 * llega temprano espera.
 *
 * Las dos formas dependen del tamaño del salto y en direcciones opuestas —sin bloques un salto
 * grande hace esperar, con bloques regala futuro— así que convergen cuando el salto es chico. Con
 * los saltos del caso, de diez a veinte minutos, la diferencia es de uno a tres pedidos por
 * planificación.
 *
 * <h2>Qué se ejecuta en cada salto</h2>
 *
 * El plan no se ejecuta completo: solo hasta donde alcanza el reloj, y eso quiere decir
 * literalmente eso. Si al llegar el corte la unidad va a mitad de camino, se queda en la última
 * esquina que alcanzó y desde ahí se la vuelve a planificar.
 *
 * Una entrega solo cuenta cuando la unidad <b>llegó</b> antes del corte. Lo único que se arrastra
 * más allá es la hora de acondicionamiento de una entrega ya hecha: la unidad está dentro de las
 * instalaciones del cliente y de ahí no se la puede mover.
 *
 * Nada más queda comprometido. Una unidad que salió hacia un cliente puede ser redirigida hacia
 * otro en la planificación siguiente, que es lo que el caso describe al hablar de reasignar
 * productos en camino hacia el cliente más crítico, y lo que la hoja de respuestas pide al
 * indicar que toda unidad, en el almacén o en ruta, se replanifica en cada iteración.
 *
 * <h2>La carga a bordo</h2>
 *
 * Lo que la unidad cargó y todavía no entregó viaja con ella entre planificaciones. Sin eso, una
 * unidad interrumpida a mitad de viaje volvería al almacén a recoger un producto que ya tiene, y
 * el plan siguiente la mandaría a dar la vuelta entera.
 *
 * Como el producto P es uno solo y es fungible, la carga es una cantidad y no una lista de
 * pedidos: los paquetes que lleva sirven para cualquier cliente. Eso es justamente lo que el
 * caso describe al hablar de reasignar productos en camino hacia el cliente más crítico.
 *
 * <h2>Entregas parciales</h2>
 *
 * Un pedido que ninguna unidad pudo tomar entero se parte, y sus mitades compiten por separado
 * en la planificación siguiente. Es lo que permite que un pedido de diez productos lo atiendan
 * una moto y una bicicleta cuando los autos están ocupados.
 *
 * Se parte tarde y solo ante el fracaso, nunca por adelantado: cada parte es una visita más al
 * cliente y cada visita cuesta su hora de acondicionamiento, así que partir un pedido que sí
 * cabía entero es puro desperdicio. Que el planificador lo haya dejado sin asignar es
 * justamente la señal de que la capacidad es lo que estorba.
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

    private final Planificador planificador;
    private final Evaluador evaluador;
    private final CalculadorDistancia distancias;
    private final Parametros parametros;
    private final List<Almacen> almacenes;
    private Duration intervalo;
    private final boolean detenerAlColapsar;
    private PlanMantenimiento mantenimiento = PlanMantenimiento.vacio();
    private PlanAverias averias = PlanAverias.vacio();
    private boolean permiteEntregasParciales = true;

    /** Hasta dónde ya se revisaron las averías; evita aplicar dos veces la misma. */
    private LocalDateTime ultimoCorteAplicado;
    private String pedidoEnSeguimiento;
    private Ritmo ritmo;
    private Observador observador = (reloj, estado, plan, medicion, avance) -> { };
    private MapaBloqueos bloqueos = MapaBloqueos.vacio();
    private Duration intervaloMinimo = INTERVALO_MINIMO_POR_DEFECTO;
    private boolean leePorBloques;

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
        this.planificador = Objects.requireNonNull(planificador);
        this.distancias = Objects.requireNonNull(distancias);
        this.evaluador = new Evaluador(distancias);
        this.almacenes = List.copyOf(almacenes);
        this.parametros = Objects.requireNonNull(parametros);
        this.intervalo = Objects.requireNonNull(intervalo);
        this.detenerAlColapsar = detenerAlColapsar;
    }

    /**
     * Lee los pedidos por bloques: cada planificación ve también los que se registrarán durante
     * el tramo de operación que está por ejecutarse.
     *
     * <h2>Qué problema resuelve</h2>
     *
     * Con la lectura clásica, un pedido que llega justo después de un corte espera un bloque
     * entero antes de que alguien lo mire. Esa latencia es igual al bloque, de modo que la
     * calidad del plan queda atada al tamaño del bloque, que es un parámetro de presentación.
     * Leer por bloques la lleva a cero: todo pedido se planifica en el bloque durante el cual
     * llega.
     *
     * No es adivinación gratuita. El planificador ve el pedido pero no puede entregarlo antes de
     * que exista: el borde inferior de la ventana de tiempo lo impide y la unidad, si llega
     * temprano, espera. Lo que gana es poder <b>agrupar</b> esa entrega con otra cercana en el
     * mismo viaje, en lugar de mandar una unidad ahora y otra un bloque después.
     *
     * <h2>Lo que cuesta</h2>
     *
     * El bloque pasa a tener tamaño fijo. No puede definirse por la llegada siguiente —como hace
     * la corrida por eventos— porque entonces nunca habría nada dentro del bloque que anticipar.
     * Los incidentes sí lo acortan: enterarse tarde de una avería es peor que un bloque corto.
     *
     * El tamaño es el intervalo del simulador, o el salto del consumo si hay ritmo.
     *
     * <h2>Por qué es un interruptor y no el comportamiento único</h2>
     *
     * Porque las dos variantes son comparables y la diferencia entre ellas es un resultado: con
     * el mismo Sc, cuánto se corre la fecha de colapso al anticipar dice cuánto del punto de
     * quiebre era flota y cuánto era información.
     */
    public Simulador leyendoPorBloques() {
        this.leePorBloques = true;
        return this;
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
     * Si un pedido que nadie pudo tomar entero se parte en dos entregas.
     *
     * Se puede apagar para medir cuánto aporta, o para reproducir el comportamiento anterior.
     */
    public Simulador conEntregasParciales(boolean permitir) {
        this.permiteEntregasParciales = permitir;
        return this;
    }

    /**
     * Averías que van a ocurrir durante la corrida.
     *
     * El caso no dice cómo se originan, así que el simulador no las inventa: se le entregan ya
     * decididas. Quien las produzca —un archivo, un sorteo o alguien que las dispara desde el
     * visualizador— queda fuera de aquí.
     */
    public Simulador conAverias(PlanAverias plan) {
        this.averias = Objects.requireNonNull(plan);
        return this;
    }

    /**
     * Ritmo de reloj de la corrida.
     *
     * El salto del consumo del ritmo reemplaza al latido: es el tiempo de operación que avanza
     * en cada salto. Y entre corrida y corrida se espera lo que falte para completar el salto
     * del algoritmo, de modo que la operación se vea avanzar a velocidad constante.
     */
    public Simulador conRitmo(Ritmo ritmo) {
        this.ritmo = Objects.requireNonNull(ritmo);
        this.intervalo = ritmo.saltoDelConsumo();
        return this;
    }

    /** Qué hacer con cada plan recién calculado; es el enganche para dibujar el mapa. */
    public Simulador observadoPor(Observador observador) {
        this.observador = Objects.requireNonNull(observador);
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

        List<MedicionDePlanificacion> mediciones = new ArrayList<>();
        Map<String, CargaEnAlmacen> cargaDeLaUnidad = new LinkedHashMap<>();
        Map<String, Averia> averiadas = new LinkedHashMap<>();
        List<Averia> averiasOcurridas = new ArrayList<>();
        Acumulado acumulado = new Acumulado();
        int recibidos = 0;
        int productosRecibidos = 0;
        // Cuánto pidió cada cliente, anotado al entrar y antes de cualquier partición: es contra
        // esto que se decide si un pedido quedó servido por completo.
        Map<String, Integer> cantidadPorPedidoOriginal = new LinkedHashMap<>();
        int iteraciones = 0;
        long computo = 0L;
        LocalDateTime reloj = inicio;
        ultimoCorteAplicado = inicio.minusNanos(1);

        while (reloj.isBefore(fin)) {
            long arranqueDelSalto = System.currentTimeMillis();

            // Primero entran los pedidos que ya llegaron: recién entonces la cabeza de la cola
            // es la próxima llegada de verdad, que es el evento que dispara el corte siguiente.
            while (!porLlegar.isEmpty() && !porLlegar.get(0).getFechaRegistro().isAfter(reloj)) {
                Pedido llegado = porLlegar.remove(0);
                pendientes.add(llegado);
                recibidos++;
                productosRecibidos += llegado.getCantidad();
                cantidadPorPedidoOriginal.merge(
                        llegado.getIdOriginal(), llegado.getCantidad(), Integer::sum);
                recibidosPorDia.merge(llegado.getFechaRegistro().toLocalDate(), 1, Integer::sum);
            }

            aplicarAverias(reloj, unidades, cargaDeLaUnidad, averiadas, averiasOcurridas);
            reincorporarReparadas(reloj, unidades, averiadas);

            LocalDateTime corte = proximoCorte(reloj, fin, porLlegar);

            // Con lectura por bloques entran además los pedidos que se registrarán durante el
            // tramo que está por ejecutarse. No se admite nada más allá del fin de la corrida:
            // un pedido que se registra después no pertenece a este escenario.
            if (leePorBloques) {
                while (!porLlegar.isEmpty()
                        && !porLlegar.get(0).getFechaRegistro().isAfter(corte)
                        && !porLlegar.get(0).getFechaRegistro().isAfter(fin)) {
                    Pedido delBloque = porLlegar.remove(0);
                    pendientes.add(delBloque);
                    recibidos++;
                    productosRecibidos += delBloque.getCantidad();
                    cantidadPorPedidoOriginal.merge(
                            delBloque.getIdOriginal(), delBloque.getCantidad(), Integer::sum);
                    recibidosPorDia.merge(delBloque.getFechaRegistro().toLocalDate(), 1, Integer::sum);
                }
            }

            if (!pendientes.isEmpty()) {
                EstadoOperacion estado = new EstadoOperacion(
                        reloj,
                        List.copyOf(pendientes),
                        almacenesEn(reloj, inventario),
                        disponiblesEn(reloj, unidades)
                );

                long antes = System.currentTimeMillis();
                Solucion plan = planificador.planificar(estado, parametros);
                long ta = System.currentTimeMillis() - antes;
                computo += ta;
                iteraciones++;

                MedicionDePlanificacion medicion =
                        new MedicionDePlanificacion(
                                reloj,
                                pendientes.size(),
                                productosEn(pendientes),
                                ta);
                mediciones.add(medicion);
                int productosABordo = 0;
                for (Vehiculo unidad : unidades.values()) {
                    productosABordo += unidad.getCargaABordo();
                }
                observador.alPlanificar(reloj, estado, plan, medicion,
                        new AvanceDeLaOperacion(
                                entregas.size(), productosEn(entregados(entregas)),
                                vencidos.size(), productosEn(vencidos),
                                productosABordo));

                if (pedidoEnSeguimiento != null) {
                    informarSeguimiento(reloj, estado, plan, pendientes);
                }

                // El planificador pudo partir pedidos que la cola tiene enteros. Se reconcilia
                // antes de ejecutar: si no, la entrega de una parte no encontraria su pedido en
                // la cola, no lo sacaria de pendientes y quedaria contado dos veces.
                reconciliarParticiones(plan, pendientes);

                int antesDeEjecutar = entregas.size();
                ejecutar(plan, reloj, corte, unidades, inventario, cargaDeLaUnidad,
                        pendientes, entregas, acumulado);
                for (int i = antesDeEjecutar; i < entregas.size(); i++) {
                    entregasPorDia.merge(entregas.get(i).llegada().toLocalDate(), 1, Integer::sum);
                }

                partirLosQueNadiePudoTomar(plan, pendientes, unidades);
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
            esperarElSalto(arranqueDelSalto);

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
                productosRecibidos,
                enPlazo,
                acumulado.kilometros,
                acumulado.costo,
                iteraciones,
                computo,
                List.copyOf(entregas),
                List.copyOf(mediciones),
                List.copyOf(averiasOcurridas),
                ritmo,
                primerVencido,
                primerVencido == null ? null : primerVencido.getFechaLimite(),
                List.copyOf(vencidos),
                List.copyOf(inalcanzables),
                Map.copyOf(recibidosPorDia),
                Map.copyOf(entregasPorDia),
                Map.copyOf(colaAlCierreDelDia),
                Map.copyOf(cantidadPorPedidoOriginal)
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
            Map<String, CargaEnAlmacen> cargaDeLaUnidad,
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
            int aBordo = unidad.getCargaABordo();

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

                if (!viaje.saleYaCargado()) {
                    Avance haciaElAlmacen = avanzar(
                            posicion, viaje.getAlmacen().getUbicacion(), libre, corte, unidad);
                    acumulado.sumar(haciaElAlmacen.kilometros(), unidad.getCostoPorKm());
                    posicion = haciaElAlmacen.posicion();
                    libre = haciaElAlmacen.instante();
                    if (!haciaElAlmacen.llego()) {
                        // El corte la encontró camino al almacén; sigue desde esa esquina.
                        break;
                    }

                    // Si llegaba con producto de un viaje anterior, el plan le dio uno que
                    // empieza cargando de cero, así que ese sobrante se devuelve. Se acredita al
                    // almacén y al periodo de los que salió, no al que está visitando ahora:
                    // devolverlo aquí le regalaría stock a un almacén que nunca lo entregó.
                    CargaEnAlmacen previa = cargaDeLaUnidad.remove(unidad.getId());
                    if (aBordo > 0 && previa != null) {
                        inventario.liberar(previa.almacen(), previa.instante(), aBordo);
                    }
                    aBordo = 0;

                    // Se carga el viaje entero de una vez, que es lo que ocurre en el almacén.
                    // Descontarlo entrega por entrega dejaba en el aire qué llevaba la unidad
                    // cuando el reloj la cortaba a mitad de camino.
                    if (!inventario.tieneStock(
                            viaje.getAlmacen(), medida.horaCarga(), viaje.getCargaTotal())) {
                        break;
                    }
                    inventario.consumir(
                            viaje.getAlmacen(), medida.horaCarga(), viaje.getCargaTotal());
                    aBordo = viaje.getCargaTotal();
                    cargaDeLaUnidad.put(unidad.getId(), new CargaEnAlmacen(
                            viaje.getAlmacen(), medida.horaCarga(), viaje.getCargaTotal()));
                }

                boolean interrumpido = false;
                for (Pedido pedido : viaje.getPedidos()) {
                    if (libre.isAfter(corte)) {
                        interrumpido = true;
                        break;
                    }

                    Avance haciaElCliente = avanzar(
                            posicion, pedido.getDestino(), libre, corte, unidad);
                    acumulado.sumar(haciaElCliente.kilometros(), unidad.getCostoPorKm());
                    posicion = haciaElCliente.posicion();

                    if (!haciaElCliente.llego()) {
                        // Va en camino con el producto encima: no hay entrega que registrar, el
                        // pedido sigue pendiente y la carga viaja con ella hasta la próxima
                        // planificación, que decidirá si sigue a este cliente o a otro.
                        libre = haciaElCliente.instante();
                        interrumpido = true;
                        break;
                    }

                    LocalDateTime llegada = haciaElCliente.instante();
                    entregas.add(new Entrega(
                            pedido, unidad, viaje.getAlmacen(), llegada,
                            haciaElCliente.kilometros(),
                            haciaElCliente.kilometros() * unidad.getCostoPorKm()));
                    pendientes.remove(pedido);
                    aBordo -= pedido.getCantidad();

                    // El acondicionamiento sí se pasa del corte: la unidad está dentro de las
                    // instalaciones del cliente y no se la puede mandar a otra parte.
                    libre = pedido.pagaAcondicionamiento()
                            ? Turnos.avanzar(
                                    llegada, Evaluador.HORAS_ACONDICIONAMIENTO_POR_ENTREGA)
                            : llegada;
                }

                if (interrumpido) {
                    break;
                }
            }

            int restante = Math.max(0, aBordo);
            if (restante == 0) {
                cargaDeLaUnidad.remove(unidad.getId());
            }
            unidades.put(unidad.getId(), unidad.tras(posicion, libre, restante));
        }
    }

    /**
     * Mueve la unidad hacia un punto sin pasarse del corte.
     *
     * Si alcanza a llegar devuelve el destino y la hora de llegada. Si el corte la sorprende en
     * camino devuelve la última esquina que llegó a pisar, y desde ahí la vuelve a planificar la
     * iteración siguiente. Eso es lo que permite redirigir a una unidad que ya salió, que es lo
     * que el caso describe y lo que la hoja de respuestas pide al decir que toda unidad, en el
     * almacén o en ruta, se replanifica en cada iteración.
     *
     * El camino solo se pide cuando hace falta ubicarla a mitad de tramo. Mientras el tramo
     * quepa entero antes del corte basta con su longitud, que el enrutador responde desde su
     * caché; pedir el recorrido esquina por esquina en cada tramo multiplicaría el costo.
     */
    private Avance avanzar(
            Ubicacion origen,
            Ubicacion destino,
            LocalDateTime salida,
            LocalDateTime corte,
            Vehiculo unidad
    ) {
        if (origen.equals(destino)) {
            return new Avance(origen, salida, 0.0, true);
        }

        double kilometros = recorrido(origen, destino, salida, unidad);
        if (Double.isInfinite(kilometros)) {
            // Sin camino abierto no se mueve; espera donde está a la siguiente planificación.
            return new Avance(origen, salida, 0.0, false);
        }

        LocalDateTime llegada = Turnos.avanzar(salida, kilometros / unidad.getVelocidadKmH());
        if (!llegada.isAfter(corte)) {
            return new Avance(destino, llegada, kilometros, true);
        }
        return hastaDondeLlego(origen, destino, salida, corte, unidad);
    }

    /** Recorre el camino esquina por esquina y se detiene en la última que alcanzó antes del corte. */
    private Avance hastaDondeLlego(
            Ubicacion origen,
            Ubicacion destino,
            LocalDateTime salida,
            LocalDateTime corte,
            Vehiculo unidad
    ) {
        List<Ubicacion> camino = distancias.camino(
                origen, destino, salida, unidad.getVelocidadKmH());
        if (camino.isEmpty()) {
            return new Avance(origen, salida, 0.0, false);
        }

        double horasPorCuadra = Ciudad.KM_POR_ARISTA / unidad.getVelocidadKmH();
        Ubicacion posicion = camino.get(0);
        LocalDateTime instante = salida;
        double kilometros = 0.0;

        for (int i = 1; i < camino.size(); i++) {
            LocalDateTime siguiente = Turnos.avanzar(instante, horasPorCuadra);
            if (siguiente.isAfter(corte)) {
                break;
            }
            posicion = camino.get(i);
            instante = siguiente;
            kilometros += Ciudad.KM_POR_ARISTA;
        }

        boolean llego = posicion.equals(destino);
        return new Avance(posicion, llego ? instante : corte, kilometros, llego);
    }

    /** Hasta dónde y hasta cuándo avanzó una unidad dentro de un salto del reloj. */
    private record Avance(
            Ubicacion posicion,
            LocalDateTime instante,
            double kilometros,
            boolean llego
    ) {
    }

    /**
     * Parte en dos los pedidos que el planificador dejó sin asignar y que no caben enteros en
     * ninguna unidad de la flota.
     *
     * El corte se hace en la mayor capacidad que alguna unidad pueda ofrecer por debajo de lo
     * que pide el pedido: así la primera mitad entra justa en la unidad más grande que quedaba y
     * el resto queda chico, que es el reparto que menos visitas genera.
     *
     * Un pedido sin asignar por falta de tiempo y no de espacio no se toca: partirlo solo le
     * sumaría otra hora de acondicionamiento y lo alejaría más de su plazo.
     */
    private void partirLosQueNadiePudoTomar(
            Solucion plan,
            List<Pedido> pendientes,
            Map<String, Vehiculo> unidades
    ) {
        if (!permiteEntregasParciales || plan.getPedidosNoAsignados().isEmpty()) {
            return;
        }

        int mayorCapacidad = 0;
        for (Vehiculo unidad : unidades.values()) {
            if (unidad.getEstado().admiteAsignacion()) {
                mayorCapacidad = Math.max(mayorCapacidad, unidad.getCapacidad());
            }
        }
        if (mayorCapacidad == 0) {
            return;
        }

        for (Pedido sinAsignar : plan.getPedidosNoAsignados()) {
            if (sinAsignar.getCantidad() <= mayorCapacidad || !pendientes.contains(sinAsignar)) {
                continue;
            }
            pendientes.remove(sinAsignar);
            pendientes.addAll(sinAsignar.partirEn(mayorCapacidad));
        }
    }

    /**
     * Saca de servicio a las unidades que se averiaron desde el corte anterior.
     *
     * Los pedidos no se pierden: solo salen de la cola cuando se entregan, así que los que esa
     * unidad tenía planificados siguen pendientes y la próxima planificación los reparte entre
     * las demás.
     *
     * El producto que llevaba encima sí desaparece de la unidad en las averías 2 y 3, porque se
     * va con ella al almacén central, tal como indica el caso. En la avería menor se queda a
     * bordo: la unidad se arregla en el sitio y sigue repartiendo lo mismo.
     *
     * Lo que <b>no</b> está modelado es el trasvase a otra unidad durante las cuatro horas que
     * permanece en el lugar. El caso lo menciona pero no lo especifica: no hay tiempo de
     * trasvase publicado ni regla sobre qué unidad acude ni si continúa su ruta o se devuelve.
     *
     * La unidad queda donde se averió durante su permanencia y, en los tipos 2 y 3, aparece
     * después en el central. Como en esos dos casos el reingreso siempre cae más tarde que el
     * fin de la permanencia, basta con dejarla ya en el central: nadie puede usarla mientras
     * tanto.
     */
    private void aplicarAverias(
            LocalDateTime reloj,
            Map<String, Vehiculo> unidades,
            Map<String, CargaEnAlmacen> cargaDeLaUnidad,
            Map<String, Averia> averiadas,
            List<Averia> ocurridas
    ) {
        for (Averia averia : averias.entre(ultimoCorteAplicado, reloj)) {
            Vehiculo unidad = unidades.get(averia.unidadId());
            if (unidad == null || averiadas.containsKey(averia.unidadId())) {
                continue;
            }

            boolean alCentral = averia.tipo().regresaAlCentral();
            Ubicacion donde = alCentral
                    ? almacenes.get(0).getUbicacion()
                    : unidad.getUbicacion();

            // Los paquetes que no alcanzaron a trasvasarse se van con la unidad al central, así
            // que deja de llevarlos encima. No se acreditan a ningún almacén: salieron del que
            // los despachó y terminan en el central, que tiene inventario infinito.
            //
            // En la avería menor no hay traslado: la unidad se arregla donde está, sigue con su
            // carga y retoma el reparto.
            int carga = alCentral ? 0 : unidad.getCargaABordo();
            if (alCentral) {
                cargaDeLaUnidad.remove(averia.unidadId());
            }

            unidades.put(averia.unidadId(), unidad.con(EstadoVehiculo.AVERIADO)
                    .tras(donde, averia.reingreso(), carga));
            averiadas.put(averia.unidadId(), averia);
            ocurridas.add(averia);
        }
        ultimoCorteAplicado = reloj;
    }

    /** Devuelve al servicio a las unidades cuya avería ya venció. */
    private void reincorporarReparadas(
            LocalDateTime reloj,
            Map<String, Vehiculo> unidades,
            Map<String, Averia> averiadas
    ) {
        for (Map.Entry<String, Averia> entrada : List.copyOf(averiadas.entrySet())) {
            if (entrada.getValue().reingreso().isAfter(reloj)) {
                continue;
            }
            Vehiculo unidad = unidades.get(entrada.getKey());
            unidades.put(entrada.getKey(), unidad.con(EstadoVehiculo.DISPONIBLE));
            averiadas.remove(entrada.getKey());
        }
    }

    /**
     * Espera lo que falte para completar el salto del algoritmo.
     *
     * Sin ritmo fijado no se espera nada y la corrida va a fondo. Con ritmo, si la planificación
     * ya consumió todo el salto no se espera —no se puede recuperar el tiempo perdido— y el
     * resumen lo contabiliza como un salto rebasado.
     */
    private void esperarElSalto(long arranqueDelSalto) {
        if (ritmo == null) {
            return;
        }
        long restante = ritmo.milisegundosDelSalto() - (System.currentTimeMillis() - arranqueDelSalto);
        if (restante <= 0) {
            return;
        }
        try {
            Thread.sleep(restante);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Hasta cuándo avanza el reloj antes de volver a planificar.
     *
     * Se toma el primer evento que ocurra después del intervalo mínimo —la llegada de un pedido
     * o un cambio en los bloqueos— y, si no hay ninguno cerca, el latido.
     *
     * <h2>Con ritmo fijado no hay eventos</h2>
     *
     * La planificación programada avanza exactamente el salto del consumo, sin adelantarse por
     * nada. No es una simplificación sino la definición: si un pedido que llega pudiera adelantar
     * el corte, cada salto consumiría menos de {@code Sc} de operación y la proporcionalidad K
     * dejaría de cumplirse. La corrida duraría más de lo anunciado en pantalla y el eje de tiempo
     * del mapa avanzaría a saltos irregulares.
     *
     * Los pedidos que llegan dentro de un salto entran igual: se incorporan a la cola al empezar
     * la vuelta siguiente, que es como funciona la planificación programada.
     */
    private LocalDateTime proximoCorte(
            LocalDateTime reloj,
            LocalDateTime fin,
            List<Pedido> porLlegar
    ) {
        if (ritmo != null) {
            LocalDateTime corteFijo = reloj.plus(ritmo.saltoDelConsumo());
            return corteFijo.isAfter(fin) ? fin : corteFijo;
        }

        LocalDateTime piso = reloj.plus(intervaloMinimo);
        LocalDateTime corte = reloj.plus(intervalo);

        // La llegada de un pedido adelanta el corte solo en la lectura clásica. Con bloques no
        // puede: si el corte se parara en cada llegada, el bloque vendría siempre vacío y no
        // habría nada que anticipar.
        if (!leePorBloques && !porLlegar.isEmpty()) {
            LocalDateTime llegada = porLlegar.get(0).getFechaRegistro();
            if (llegada.isAfter(reloj) && llegada.isBefore(corte)) {
                corte = llegada;
            }
        }

        LocalDateTime cambioDeBloqueo = bloqueos.proximoCambioDesde(reloj.plusNanos(1));
        if (cambioDeBloqueo != null && cambioDeBloqueo.isBefore(corte)) {
            corte = cambioDeBloqueo;
        }

        // Una avería deja a la operación con una unidad menos y con su carga sin repartir: no
        // tiene sentido esperar al latido para enterarse.
        LocalDateTime proximaAveria = averias.proximaDespuesDe(reloj);
        if (proximaAveria != null && proximaAveria.isBefore(corte)) {
            corte = proximaAveria;
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
     * Adopta en la cola las particiones que decidio el planificador.
     *
     * Los tres algoritmos pueden repartir un pedido entre varios huecos cuando no cabe entero en
     * ninguno. El plan devuelve entonces las partes, con identificadores derivados del original
     * -una letra al final- mientras la cola del simulador sigue teniendo el pedido completo.
     *
     * Se empareja por prefijo y no por {@code idOriginal}: cuando un pedido ya venia partido de
     * una iteracion anterior, la cola tiene varias partes del mismo original y solo el prefijo
     * dice de cual de ellas salio cada trozo nuevo.
     *
     * La sustitucion solo se hace si las partes suman exactamente lo que pedia el pedido que
     * reemplazan. Si no cuadrara habria producto inventado o perdido, y es preferible detenerse a
     * seguir con una contabilidad rota.
     */
    private void reconciliarParticiones(Solucion plan, List<Pedido> pendientes) {
        Set<String> enLaCola = new HashSet<>();
        for (Pedido pendiente : pendientes) {
            enLaCola.add(pendiente.getId());
        }

        List<Pedido> delPlan = new ArrayList<>();
        for (Ruta viaje : plan.getRutas()) {
            delPlan.addAll(viaje.getPedidos());
        }
        delPlan.addAll(plan.getPedidosNoAsignados());

        Map<String, List<Pedido>> partesPorPadre = new LinkedHashMap<>();
        for (Pedido pedido : delPlan) {
            if (enLaCola.contains(pedido.getId())) {
                continue;
            }
            String padre = padreEnLaCola(pedido.getId(), enLaCola);
            if (padre != null) {
                partesPorPadre.computeIfAbsent(padre, id -> new ArrayList<>()).add(pedido);
            }
        }

        for (Map.Entry<String, List<Pedido>> entrada : partesPorPadre.entrySet()) {
            Pedido padre = null;
            for (Pedido pendiente : pendientes) {
                if (pendiente.getId().equals(entrada.getKey())) {
                    padre = pendiente;
                    break;
                }
            }
            if (padre == null) {
                continue;
            }

            int suman = 0;
            for (Pedido parte : entrada.getValue()) {
                suman += parte.getCantidad();
            }
            if (suman != padre.getCantidad()) {
                throw new IllegalStateException(
                        "Las partes de " + padre.getId() + " suman " + suman
                                + " y el pedido pedia " + padre.getCantidad() + ".");
            }

            pendientes.remove(padre);
            pendientes.addAll(entrada.getValue());
        }
    }

    /** El pedido de la cola del que salio este trozo: el prefijo mas largo que esta en ella. */
    private static String padreEnLaCola(String idDelTrozo, Set<String> enLaCola) {
        String mejor = null;
        for (String candidato : enLaCola) {
            if (idDelTrozo.length() > candidato.length() && idDelTrozo.startsWith(candidato)
                    && (mejor == null || candidato.length() > mejor.length())) {
                mejor = candidato;
            }
        }
        return mejor;
    }

    /** Los pedidos de una lista de entregas, para poder contar su producto. */
    private static List<Pedido> entregados(List<Entrega> entregas) {
        List<Pedido> pedidos = new ArrayList<>(entregas.size());
        for (Entrega entrega : entregas) {
            pedidos.add(entrega.pedido());
        }
        return pedidos;
    }

    /** Cuánto producto suman estos pedidos. */
    private static int productosEn(List<Pedido> pedidos) {
        int unidades = 0;
        for (Pedido pedido : pedidos) {
            unidades += pedido.getCantidad();
        }
        return unidades;
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
