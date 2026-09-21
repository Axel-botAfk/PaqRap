package com.paqrap.simulacion;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.CalculadorDistancia;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Inventario;
import com.paqrap.planificador.MetricasRuta;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    /** Cada cuánto se vuelve a planificar, si no se indica otra cosa. */
    public static final Duration INTERVALO_POR_DEFECTO = Duration.ofMinutes(30);

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

        Map<LocalDate, Integer> recibidosPorDia = new LinkedHashMap<>();
        Map<LocalDate, Integer> entregasPorDia = new LinkedHashMap<>();
        Map<LocalDate, Integer> colaAlCierreDelDia = new LinkedHashMap<>();

        Acumulado acumulado = new Acumulado();
        int recibidos = 0;
        int iteraciones = 0;
        long computo = 0L;
        LocalDateTime reloj = inicio;

        while (reloj.isBefore(fin)) {
            LocalDateTime corte = reloj.plus(intervalo);
            if (corte.isAfter(fin)) {
                corte = fin;
            }

            while (!porLlegar.isEmpty() && !porLlegar.get(0).getFechaRegistro().isAfter(reloj)) {
                Pedido llegado = porLlegar.remove(0);
                pendientes.add(llegado);
                recibidos++;
                recibidosPorDia.merge(llegado.getFechaRegistro().toLocalDate(), 1, Integer::sum);
            }

            if (!pendientes.isEmpty()) {
                EstadoOperacion estado = new EstadoOperacion(
                        reloj,
                        losMasUrgentes(pendientes),
                        almacenesEn(reloj, inventario),
                        List.copyOf(unidades.values())
                );

                long antes = System.currentTimeMillis();
                Solucion plan = planificador.planificar(estado, parametros);
                computo += System.currentTimeMillis() - antes;
                iteraciones++;

                int antesDeEjecutar = entregas.size();
                ejecutar(plan, reloj, corte, unidades, inventario, pendientes, entregas, acumulado);
                for (int i = antesDeEjecutar; i < entregas.size(); i++) {
                    entregasPorDia.merge(entregas.get(i).llegada().toLocalDate(), 1, Integer::sum);
                }
            }

            for (Pedido pendiente : List.copyOf(pendientes)) {
                if (pendiente.getFechaLimite().isBefore(corte)) {
                    vencidos.add(pendiente);
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

    /** Los pedidos de plazo más apretado, que son los que entran a esta planificación. */
    private List<Pedido> losMasUrgentes(List<Pedido> pendientes) {
        if (pendientes.size() <= pedidosPorIteracion) {
            return List.copyOf(pendientes);
        }
        List<Pedido> ordenados = new ArrayList<>(pendientes);
        ordenados.sort((uno, otro) -> uno.getFechaLimite().compareTo(otro.getFechaLimite()));
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
