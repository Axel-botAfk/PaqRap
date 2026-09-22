package com.paqrap.simulacion;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoAlmacen;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.modelo.VentanaIndisponibilidad;
import com.paqrap.planificador.Grasp;
import com.paqrap.planificador.Parametros;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Simulador multi-día para GRASP. No es un port del simulador del proyecto de búsqueda tabú del
 * equipo (esa clase se apoya en información —hora de llegada por parada, reenrutamiento ante
 * bloqueos— que el modelo de este proyecto no tiene); se construyó específicamente para lo que
 * GRASP sí puede ofrecer.
 *
 * En cada "latido" (instante de replanificación) reúne los pedidos todavía pendientes y las
 * unidades que ya quedaron libres, llama a {@link Grasp#construir} una vez, y aplica el plan
 * resultante al estado de la operación para el siguiente latido. Antes de esta clase, todos los
 * demos de GRASP llamaban a Grasp una sola vez con una lista fija de pedidos: no existía ninguna
 * forma de correr varios días seguidos ni de reutilizar vehículos entre plani­ficaciones.
 *
 * Decisiones de diseño (documentadas porque el caso no las cierra y no hay una única forma
 * "correcta" de extender GRASP a varios días con el modelo actual):
 *
 * <ol>
 *   <li><b>Cadencia de replanificación configurable, no continua.</b> {@code Ruta} no expone la
 *   hora de llegada de cada parada individual, solo la duración total de la ruta (ver su propio
 *   comentario de diseño): una unidad se marca libre recién cuando termina toda su ruta, nunca a
 *   media ruta. Elegir un latido de una hora (el valor por defecto que usa {@code DemoDatosReales})
 *   en vez de replanificar segundo a segundo alcanza para que los pedidos con plazo corto (4h)
 *   tengan una oportunidad real de asignarse, sin disparar el número de llamadas a GRASP.</li>
 *   <li><b>Entre latidos, una unidad vuelve a su almacén de origen.</b> El README del proyecto
 *   documenta que GRASP no factura ni modela el trayecto de retorno dentro del costo/duración de
 *   UNA ruta, y eso se respeta: esta clase no le suma distancia ni tiempo de vuelta a la ruta. Pero
 *   para que la misma unidad pueda recibir una ruta nueva en un latido posterior, su posición
 *   registrada para ese siguiente latido sí vuelve a ser el almacén desde el que partió, en vez de
 *   quedar en el destino de su última entrega. La alternativa (dejar a la unidad "varada" en la
 *   última parada) rompe la simulación multi-día por una regla propia de Grasp.construir: una
 *   unidad solo puede iniciar una ruta nueva si está físicamente en la ubicación de un almacén
 *   (Grasp.java, generarInsercionesFactibles); sin este reinicio, cada unidad quedaría inutilizable
 *   después de su primera ruta y la flota se iría vaciando día a día (fue exactamente lo que se
 *   observó al probar esta clase por primera vez contra los datos reales, antes de este ajuste).</li>
 *   <li><b>Reabastecimiento de almacenes intermedios.</b> El caso no especifica una cadencia de
 *   reabastecimiento desde el almacén central. A falta de ese dato, cada almacén intermedio
 *   vuelve a su capacidad máxima (1000) en cada latido; es una simplificación explícita, igual de
 *   provisional que la duración de mantenimiento preventivo que ya documenta LectorMantenimiento,
 *   no un valor confirmado por el profesor.</li>
 *   <li><b>Bloqueos de calles quedan fuera de esta simulación</b>, igual que en el resto de GRASP
 *   (decisión de alcance ya documentada en el README del proyecto): esta clase no lee ni aplica
 *   los archivos bloqueo.*.txt.</li>
 *   <li><b>Un pedido que nunca logra una inserción factible antes de su fecha límite</b> se marca
 *   "vencido" en el latido en que se detecta y deja de intentarse en latidos futuros.</li>
 * </ol>
 */
public final class Simulador {
    private final Grasp grasp;
    private final List<Almacen> almacenesBase;
    private final Parametros parametros;
    private final Duration intervaloEntreLatidos;
    private final Map<String, List<VentanaIndisponibilidad>> indisponibilidadesPorVehiculo;

    public Simulador(
            Grasp grasp,
            List<Almacen> almacenesBase,
            Parametros parametros,
            Duration intervaloEntreLatidos
    ) {
        this(grasp, almacenesBase, parametros, intervaloEntreLatidos, Map.of());
    }

    /**
     * @param indisponibilidadesPorVehiculo ventanas de avería/mantenimiento por id de vehículo
     *                                       (por ejemplo, el resultado de
     *                                       {@code LectorMantenimiento.leer(...)}); se aplican en
     *                                       cada latido igual que ya lo hace {@code Grasp} para
     *                                       una sola planificación.
     */
    public Simulador(
            Grasp grasp,
            List<Almacen> almacenesBase,
            Parametros parametros,
            Duration intervaloEntreLatidos,
            Map<String, List<VentanaIndisponibilidad>> indisponibilidadesPorVehiculo
    ) {
        this.grasp = Objects.requireNonNull(grasp);
        this.almacenesBase = List.copyOf(almacenesBase);
        this.parametros = Objects.requireNonNull(parametros);
        if (intervaloEntreLatidos.isZero() || intervaloEntreLatidos.isNegative()) {
            throw new IllegalArgumentException("El intervalo entre latidos debe ser positivo.");
        }
        this.intervaloEntreLatidos = intervaloEntreLatidos;
        this.indisponibilidadesPorVehiculo = Map.copyOf(indisponibilidadesPorVehiculo);
    }

    public ResumenSimulacion correr(
            LocalDateTime inicio,
            Duration horizonte,
            List<Pedido> pedidosDelPeriodo,
            List<Vehiculo> flotaInicial
    ) {
        Objects.requireNonNull(inicio);
        Objects.requireNonNull(horizonte);
        LocalDateTime fin = inicio.plus(horizonte);

        List<Pedido> porRegistrar = new ArrayList<>(pedidosDelPeriodo);
        porRegistrar.sort(Comparator.comparing(Pedido::getFechaRegistro));

        Map<String, Pedido> pendientes = new LinkedHashMap<>();
        Map<String, Ubicacion> ubicacionActual = new LinkedHashMap<>();
        Map<String, LocalDateTime> ocupadoHasta = new LinkedHashMap<>();

        int recibidos = 0;
        int entregados = 0;
        int vencidos = 0;
        double distanciaTotalKm = 0.0;
        double costoTotal = 0.0;
        int cantidadDeLatidosConPlan = 0;

        Map<LocalDate, Integer> recibidosPorDia = new TreeMap<>();
        Map<LocalDate, Integer> entregasPorDia = new TreeMap<>();
        Map<LocalDate, Integer> colaAlCierreDelDia = new TreeMap<>();

        int cursor = 0;
        LocalDateTime reloj = inicio;
        long antes = System.currentTimeMillis();

        while (!reloj.isAfter(fin)) {
            // 1) Incorporar a la cola los pedidos ya registrados hasta este latido.
            while (cursor < porRegistrar.size()
                    && !porRegistrar.get(cursor).getFechaRegistro().isAfter(reloj)) {
                Pedido nuevo = porRegistrar.get(cursor);
                pendientes.put(nuevo.getId(), nuevo);
                recibidos++;
                recibidosPorDia.merge(nuevo.getFechaRegistro().toLocalDate(), 1, Integer::sum);
                cursor++;
            }

            // 2) Vencer los pedidos cuya fecha límite ya pasó sin haberse asignado.
            List<String> idsAVencer = new ArrayList<>();
            for (Pedido pendiente : pendientes.values()) {
                if (pendiente.getFechaLimite().isBefore(reloj)) {
                    idsAVencer.add(pendiente.getId());
                }
            }
            for (String id : idsAVencer) {
                pendientes.remove(id);
                vencidos++;
            }

            // 3) Armar el estado de este latido: unidades libres en su última posición
            //    conocida, y almacenes intermedios recién reabastecidos (ver clase, punto 3).
            List<Vehiculo> vehiculosDisponibles = new ArrayList<>();
            for (Vehiculo base : flotaInicial) {
                LocalDateTime libreDesde = ocupadoHasta.get(base.getId());
                if (libreDesde != null && libreDesde.isAfter(reloj)) {
                    continue;
                }
                Ubicacion ubicacion = ubicacionActual.getOrDefault(base.getId(), base.getUbicacion());
                List<VentanaIndisponibilidad> indisponibilidades =
                        indisponibilidadesPorVehiculo.getOrDefault(base.getId(), List.of());
                vehiculosDisponibles.add(new Vehiculo(
                        base.getId(), base.getTipo(), EstadoVehiculo.DISPONIBLE, ubicacion, indisponibilidades));
            }

            List<Almacen> almacenesLatido = new ArrayList<>();
            for (Almacen almacen : almacenesBase) {
                if (almacen.getTipo() == TipoAlmacen.INTERMEDIO) {
                    almacenesLatido.add(Almacen.intermedio(
                            almacen.getId(), almacen.getUbicacion(), Almacen.CAPACIDAD_MAXIMA_INTERMEDIO));
                } else {
                    almacenesLatido.add(almacen);
                }
            }

            // 4) Planificar solo si hay algo que planificar: pedidos pendientes y unidades libres.
            if (!pendientes.isEmpty() && !vehiculosDisponibles.isEmpty()) {
                Solucion plan = grasp.construir(
                        reloj,
                        new ArrayList<>(pendientes.values()),
                        almacenesLatido,
                        vehiculosDisponibles,
                        parametros
                );
                cantidadDeLatidosConPlan++;

                for (Ruta ruta : plan.getRutas()) {
                    String vehiculoId = ruta.getVehiculo().getId();
                    ocupadoHasta.put(vehiculoId, sumarHoras(reloj, ruta.getDuracionHoras()));

                    // La unidad vuelve a su almacén de origen para el siguiente latido (ver
                    // punto 2 del comentario de la clase): el costo/duración de la ruta ya
                    // calculado por Grasp NO incluye ese regreso, solo se reinicia la posición
                    // registrada para que la unidad pueda volver a asignarse.
                    ubicacionActual.put(vehiculoId, ruta.getAlmacen().getUbicacion());

                    distanciaTotalKm += ruta.getDistanciaTotalKm();
                    costoTotal += ruta.getCostoTotal();

                    for (Pedido entregado : ruta.getPedidos()) {
                        pendientes.remove(entregado.getId());
                        entregados++;
                        entregasPorDia.merge(reloj.toLocalDate(), 1, Integer::sum);
                    }
                }
            }

            LocalDateTime siguienteReloj = reloj.plus(intervaloEntreLatidos);
            if (siguienteReloj.toLocalDate().isAfter(reloj.toLocalDate())) {
                colaAlCierreDelDia.put(reloj.toLocalDate(), pendientes.size());
            }
            reloj = siguienteReloj;
        }

        long milisegundosDeComputo = System.currentTimeMillis() - antes;

        return new ResumenSimulacion(
                inicio,
                fin,
                recibidos,
                entregados,
                pendientes.size(),
                vencidos,
                distanciaTotalKm,
                costoTotal,
                cantidadDeLatidosConPlan,
                milisegundosDeComputo,
                recibidosPorDia,
                entregasPorDia,
                colaAlCierreDelDia
        );
    }

    private static LocalDateTime sumarHoras(LocalDateTime fecha, double horas) {
        long nanos = Math.round(horas * 3_600_000_000_000L);
        return fecha.plusNanos(nanos);
    }
}
