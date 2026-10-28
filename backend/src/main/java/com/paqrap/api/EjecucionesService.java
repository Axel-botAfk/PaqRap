package com.paqrap.api;

import com.paqrap.api.ApiModels.Algoritmo;
import com.paqrap.api.ApiModels.AvanceVista;
import com.paqrap.api.ApiModels.ColapsoVista;
import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.api.ApiModels.EjecucionVista;
import com.paqrap.api.ApiModels.Escenario;
import com.paqrap.api.ApiModels.Estado;
import com.paqrap.api.ApiModels.EventoOperacionVista;
import com.paqrap.api.ApiModels.EventosOperacionVista;
import com.paqrap.api.ApiModels.EventoVista;
import com.paqrap.api.ApiModels.FotogramaVista;
import com.paqrap.api.ApiModels.FotogramasVista;
import com.paqrap.api.ApiModels.PedidoVista;
import com.paqrap.api.ApiModels.PlanVista;
import com.paqrap.api.ApiModels.PlanesVista;
import com.paqrap.api.ApiModels.RitmoVista;
import com.paqrap.api.ApiModels.RutaVista;
import com.paqrap.api.ApiModels.RegistrarAveriaRequest;
import com.paqrap.api.ApiModels.AveriaVista;
import com.paqrap.api.ApiModels.InventarioVista;
import com.paqrap.api.ApiModels.TrayectoVista;
import com.paqrap.api.ApiModels.VehiculoVista;
import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.Averia;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.Pedido;
import com.paqrap.modelo.Ruta;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.EstadoOperacion;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;
import com.paqrap.simulacion.Observador;
import com.paqrap.simulacion.MedicionDePlanificacion;
import com.paqrap.simulacion.AvanceDeLaOperacion;
import com.paqrap.simulacion.Trayecto;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Una ejecucion es un trabajo acotado, no una peticion HTTP bloqueante.
 *
 * El simulador conserva su propia cadencia de replanificación (latido de 30 min, mínimo de 15
 * y cortes por llegadas, bloqueos y averías), de modo que la velocidad de pantalla no cambia
 * ninguna decisión. Lo que marca el {@link Marcapasos} es la publicación: las iteraciones se
 * juntan hasta cruzar el siguiente límite de una grilla fija de Sc minutos simulados, la ventana
 * se publica entera (planes, recorridos, posiciones e inventario) y se espera n × Sa antes de
 * seguir, con n los límites cruzados. Así N límites duran N × Sa.
 */
@Service
final class EjecucionesService {
    private static final int MAX_REGISTROS = 32;
    private static final int MAX_SIMULTANEAS = 3;
    private static final int MAX_FOTOGRAMAS = 2048;
    private static final int MAX_PLANES = 2048;
    private static final int MAX_EVENTOS = 4096;
    private static final int PAGINA_FOTOGRAMAS = 100;
    /** Velocidad del día a día cuando la operación va en tiempo real: K = 1. */
    private static final int VELOCIDAD_TIEMPO_REAL = 1;
    private final DatosFuente datos;
    private final EventosWebSocket eventos;
    private final Marcapasos.Reloj reloj;
    /**
     * Interruptor de la operación en tiempo real ({@code paqrap.operacion.tiempo-real}).
     *
     * En falso el día a día se comporta como siempre: inicio elegido en la pantalla y 60×. En
     * verdadero arranca en el minuto presente, avanza 1× y admite pedidos por la API mientras
     * corre. Es el único punto del backend que hay que tocar para volver atrás.
     */
    private final boolean operacionEnTiempoReal;
    private final ConcurrentMap<UUID, Ejecucion> ejecuciones = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            MAX_SIMULTANEAS, MAX_SIMULTANEAS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2),
            trabajo -> {
                Thread hilo = new Thread(trabajo, "paqrap-simulacion");
                hilo.setDaemon(true);
                return hilo;
            }, new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    EjecucionesService(DatosFuente datos, EventosWebSocket eventos,
            @Value("${paqrap.operacion.tiempo-real:false}") boolean operacionEnTiempoReal) {
        this(datos, eventos, Marcapasos.SISTEMA, operacionEnTiempoReal);
    }

    EjecucionesService(DatosFuente datos, EventosWebSocket eventos, Marcapasos.Reloj reloj) {
        this(datos, eventos, reloj, false);
    }

    EjecucionesService(DatosFuente datos, EventosWebSocket eventos, Marcapasos.Reloj reloj,
            boolean operacionEnTiempoReal) {
        this.datos = datos;
        this.eventos = eventos;
        this.reloj = reloj;
        this.operacionEnTiempoReal = operacionEnTiempoReal;
    }

    EjecucionVista crear(CrearEjecucionRequest solicitud) {
        if (solicitud == null || solicitud.escenario() == null || solicitud.algoritmo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA",
                    "Escenario y algoritmo son obligatorios.");
        }
        boolean tiempoReal = operacionEnTiempoReal
                && solicitud.escenario() == Escenario.OPERACION_DIARIA;
        CrearEjecucionRequest request = tiempoReal ? anclarAlPresente(solicitud) : solicitud;
        if (request.inicio() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA",
                    "Escenario, algoritmo e inicio son obligatorios.");
        }
        int dias = dias(request);
        if (request.pausaMs() != null && (request.pausaMs() < 0 || request.pausaMs() > 500)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAUSA_INVALIDA",
                    "La pausa de demostración debe estar entre 0 y 500 ms.");
        }
        RitmoEscenario ritmo = RitmoEscenario.para(request, dias);
        // En tiempo real la jornada empieza ahora y su demanda todavía no existe: la cola vacía
        // es el estado legítimo de arranque, no un error de selección de fechas.
        DatosFuente.DatosEntrada entrada = datos.cargar(request.inicio(), dias, !tiempoReal);
        limpiarAntiguas();
        if (ejecuciones.size() >= MAX_REGISTROS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CAPACIDAD_AGOTADA",
                    "Hay demasiadas ejecuciones registradas. Intente más tarde.");
        }

        UUID id = UUID.randomUUID();
        Ejecucion ejecucion = new Ejecucion(ritmo, new Marcapasos(reloj, ritmo.saMs()),
                Duration.ofDays(dias), request.inicio());
        ejecucion.vista = new EjecucionVista(id, Estado.PENDIENTE,
                request.escenario(), request.algoritmo(), request.inicio(), request.inicio(),
                LocalDateTime.now(), null, List.of(), List.of(), List.of(), null, null, null,
                false, ritmoVista(ejecucion), List.of(), null);
        ejecucion.inventario = new InventarioVista(request.inicio(),
                datos.almacenes().stream().map(Vistas::stock).toList());
        ejecuciones.put(id, ejecucion);
        try {
            ejecucion.tarea = executor.submit(() -> correr(ejecucion, request, dias, entrada));
        } catch (RejectedExecutionException e) {
            ejecuciones.remove(id);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EJECUCIONES_OCUPADAS",
                    "El servidor ya está procesando el máximo de simulaciones.");
        }
        return ejecucion.vista;
    }

    /**
     * El día a día en tiempo real no elige su inicio: arranca en el minuto presente y a 1×.
     *
     * Se trunca al minuto porque es la precisión que piden los criterios de tiempo y porque el
     * reloj de la operación se compara con fechas de registro al minuto. Lo que la pantalla haya
     * mandado como inicio o velocidad se descarta: en tiempo real no son del usuario.
     *
     * Sa no cambia (20 s), así que el planificador se sigue invocando una vez cada 20 s reales
     * igual que a 60×; lo único que se encoge es Sc, el tramo de operación que consume cada
     * ventana, de 20 min a 20 s.
     */
    private CrearEjecucionRequest anclarAlPresente(CrearEjecucionRequest solicitud) {
        LocalDateTime ahora = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        return new CrearEjecucionRequest(solicitud.escenario(), solicitud.algoritmo(), ahora,
                solicitud.horizonteDias(), solicitud.semilla(), solicitud.pausaMs(),
                solicitud.duracionObjetivoMin(), VELOCIDAD_TIEMPO_REAL,
                solicitud.minutosPorVentana(), solicitud.segundosPorVentana());
    }

    /**
     * Alta de un pedido en una corrida en curso.
     *
     * Es la puerta que le faltaba al día a día en tiempo real: sin ella la operación solo puede
     * mostrar demanda que ya estaba en la fuente al arrancar, y un pedido hecho a las 10:05 no
     * aparecería nunca. La fecha de registro la pone el reloj de la corrida, no el cliente, para
     * que ningún pedido se cuele antes del instante ya publicado.
     *
     * El pedido no adelanta el corte: entra a la cola en la vuelta siguiente, igual que los
     * leídos del archivo, de modo que K se sigue cumpliendo.
     */
    PedidoVista registrarPedido(UUID id, ApiModels.RegistrarPedidoRequest solicitud) {
        Ejecucion ejecucion = obtener(id);
        synchronized (ejecucion) {
            Estado estado = ejecucion.vista.estado();
            if ((estado != Estado.EN_CURSO && estado != Estado.PAUSADA)
                    || ejecucion.simulador == null) {
                throw new ApiException(HttpStatus.CONFLICT, "EJECUCION_NO_ACTIVA",
                        "Solo se admiten pedidos durante una ejecución activa.");
            }
            LocalDateTime instante = ejecucion.inventario.reloj();
            String codigo = "EV-" + (++ejecucion.pedidosEnVivo);
            Pedido pedido = new Pedido(codigo, solicitud.cliente().trim(),
                    new Ubicacion(solicitud.x(), solicitud.y()), solicitud.cantidad(),
                    instante, solicitud.plazoHoras());
            ejecucion.simulador.registrarPedidoEnVivo(pedido);
            registrarEvento(ejecucion, "PEDIDO_REGISTRADO", instante, null, codigo, null,
                    "Pedido " + codigo + " registrado en vivo; entra en el siguiente corte.");
            // Todavía no tiene ruta: la tendrá recién cuando el planificador corra en el corte
            // siguiente. Devolverlo como planificado sería anunciar una asignación inexistente.
            return Vistas.pedido(pedido, "NO_ASIGNADO",
                    "Registrado en vivo; entra al planificador en el siguiente corte.");
        }
    }

    /** Estado actual con el ancla del reloj al día, para retomar tras recargar la página. */
    EjecucionVista consultar(UUID id) {
        Ejecucion ejecucion = obtener(id);
        synchronized (ejecucion) {
            return conRitmo(ejecucion.vista, ritmoVista(ejecucion));
        }
    }

    InventarioVista inventario(UUID id) {
        Ejecucion ejecucion = obtener(id);
        return ejecucion.inventario;
    }

    List<AveriaVista> averias(UUID id) {
        Ejecucion ejecucion = obtener(id);
        synchronized (ejecucion) { return List.copyOf(ejecucion.averias); }
    }

    AveriaVista registrarAveria(UUID id, RegistrarAveriaRequest solicitud) {
        Ejecucion ejecucion = obtener(id);
        synchronized (ejecucion) {
            Estado estado = ejecucion.vista.estado();
            if ((estado != Estado.EN_CURSO && estado != Estado.PAUSADA)
                    || ejecucion.simulador == null) {
                throw new ApiException(HttpStatus.CONFLICT, "EJECUCION_NO_ACTIVA",
                        "Solo se admiten averías durante una ejecución activa.");
            }
            String unidadId = solicitud.unidadId().trim();
            if (datos.flota().stream().noneMatch(v -> v.getId().equals(unidadId))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNIDAD_DESCONOCIDA",
                        "La unidad no pertenece a la flota de la corrida.");
            }
            if (ejecucion.averias.stream().anyMatch(a -> a.unidadId().equals(unidadId))) {
                throw new ApiException(HttpStatus.CONFLICT, "AVERIA_DUPLICADA",
                        "Ya se registró una avería para esta unidad en la corrida.");
            }
            LocalDateTime instante = ejecucion.inventario.reloj();
            Averia averia = new Averia(unidadId, instante, solicitud.tipo());
            ejecucion.simulador.registrarAveriaEnVivo(averia);
            AveriaVista vista = new AveriaVista(unidadId, solicitud.tipo(), instante, "REGISTRADA");
            ejecucion.averias.add(vista);
            registrarEvento(ejecucion, "AVERIA_REGISTRADA", instante, unidadId, null, null,
                    "Avería " + solicitud.tipo() + " registrada; se aplica en el siguiente corte.");
            return vista;
        }
    }

    EjecucionVista pausar(UUID id) {
        Ejecucion ejecucion = obtener(id);
        EjecucionVista vista;
        synchronized (ejecucion) {
            Estado estado = ejecucion.vista.estado();
            if (estado == Estado.PAUSADA) return consultar(id);
            if (estado != Estado.EN_CURSO) {
                throw new ApiException(HttpStatus.CONFLICT, "EJECUCION_NO_PAUSABLE",
                        "Solo se puede pausar una ejecución en curso.");
            }
            ejecucion.marcapasos.pausar();
            vista = cambiarEstado(ejecucion.vista, Estado.PAUSADA, null, null);
            ejecucion.vista = conRitmo(vista, ritmoVista(ejecucion));
            vista = ejecucion.vista;
        }
        eventos.publicar(new EventoVista("PAUSADA", vista));
        return vista;
    }

    EjecucionVista reanudar(UUID id) {
        Ejecucion ejecucion = obtener(id);
        EjecucionVista vista;
        synchronized (ejecucion) {
            Estado estado = ejecucion.vista.estado();
            if (estado == Estado.EN_CURSO) return consultar(id);
            if (estado != Estado.PAUSADA) {
                throw new ApiException(HttpStatus.CONFLICT, "EJECUCION_NO_PAUSADA",
                        "Solo se puede reanudar una ejecución en pausa.");
            }
            ejecucion.marcapasos.reanudar();
            vista = cambiarEstado(ejecucion.vista, Estado.EN_CURSO, null, null);
            ejecucion.vista = conRitmo(vista, ritmoVista(ejecucion));
            vista = ejecucion.vista;
        }
        eventos.publicar(new EventoVista("REANUDADA", vista));
        return vista;
    }

    private Ejecucion obtener(UUID id) {
        Ejecucion ejecucion = ejecuciones.get(id);
        if (ejecucion == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EJECUCION_NO_ENCONTRADA",
                    "No existe una ejecución con ese identificador.");
        }
        return ejecucion;
    }

    FotogramasVista fotogramas(UUID id, long desde) {
        Ejecucion ejecucion = obtener(id);
        validarDesde(desde);
        synchronized (ejecucion) {
            long primero = ejecucion.fotogramas.isEmpty() ? ejecucion.siguienteIndice
                    : ejecucion.fotogramas.peekFirst().indice();
            long inicio = Math.max(desde, primero);
            List<FotogramaVista> pagina = new ArrayList<>();
            for (FotogramaVista fotograma : ejecucion.fotogramas) {
                if (fotograma.indice() >= inicio && pagina.size() < PAGINA_FOTOGRAMAS) {
                    pagina.add(fotograma);
                }
            }
            long siguiente = pagina.isEmpty() ? inicio : pagina.get(pagina.size() - 1).indice() + 1;
            return new FotogramasVista(primero, siguiente, esFinal(ejecucion.vista.estado()),
                    List.copyOf(pagina));
        }
    }

    PlanesVista planes(UUID id, long desde) {
        Ejecucion ejecucion = obtener(id);
        validarDesde(desde);
        synchronized (ejecucion) {
            long primero = ejecucion.planes.isEmpty() ? ejecucion.siguientePlan
                    : ejecucion.planes.peekFirst().indice();
            long inicio = Math.max(desde, primero);
            List<PlanVista> pagina = new ArrayList<>();
            for (PlanVista plan : ejecucion.planes) {
                if (plan.indice() >= inicio && pagina.size() < PAGINA_FOTOGRAMAS) {
                    pagina.add(plan);
                }
            }
            long siguiente = pagina.isEmpty() ? inicio : pagina.get(pagina.size() - 1).indice() + 1;
            return new PlanesVista(primero, siguiente, esFinal(ejecucion.vista.estado()),
                    List.copyOf(pagina));
        }
    }

    EventosOperacionVista eventosOperacion(UUID id, long desde) {
        Ejecucion ejecucion = obtener(id);
        validarDesde(desde);
        synchronized (ejecucion) {
            long primero = ejecucion.eventos.isEmpty() ? ejecucion.siguienteEvento
                    : ejecucion.eventos.peekFirst().indice();
            long inicio = Math.max(desde, primero);
            List<EventoOperacionVista> pagina = new ArrayList<>();
            for (EventoOperacionVista evento : ejecucion.eventos) {
                if (evento.indice() >= inicio && pagina.size() < PAGINA_FOTOGRAMAS) {
                    pagina.add(evento);
                }
            }
            long siguiente = pagina.isEmpty() ? inicio : pagina.get(pagina.size() - 1).indice() + 1;
            return new EventosOperacionVista(primero, siguiente, esFinal(ejecucion.vista.estado()),
                    List.copyOf(pagina));
        }
    }

    EjecucionVista cancelar(UUID id) {
        Ejecucion ejecucion = obtener(id);
        synchronized (ejecucion) {
            Estado estado = ejecucion.vista.estado();
            if (estado == Estado.PENDIENTE || estado == Estado.EN_CURSO || estado == Estado.PAUSADA) {
                ejecucion.finRealMs = reloj.ahoraMs();
                ejecucion.vista = cambiarEstado(ejecucion.vista, Estado.CANCELADA, null, null);
                if (ejecucion.tarea != null) ejecucion.tarea.cancel(true);
                eventos.publicar(new EventoVista("CANCELADA", ejecucion.vista));
            }
        }
        return ejecucion.vista;
    }

    private void correr(Ejecucion ejecucion, CrearEjecucionRequest request, int dias,
                        DatosFuente.DatosEntrada entrada) {
        try {
            if (ejecucion.vista.estado() == Estado.CANCELADA) return;
            MapaBloqueos mapa = new MapaBloqueos(entrada.bloqueos());
            EnrutadorBloqueos ruteo = new EnrutadorBloqueos(mapa);
            Evaluador evaluador = new Evaluador(ruteo);
            Planificador planificador = request.algoritmo() == Algoritmo.GRASP
                    ? new Grasp(evaluador) : new BusquedaTabu(evaluador);
            Parametros parametros = Parametros.constructor(8, 0.30,
                            request.semilla() == null ? 20260901L : request.semilla())
                    .penalidadEspera(80.0)
                    .iteracionesTabu(120)
                    .tenenciaTabu(8)
                    .tamanoMuestraVecindario(40)
                    .iteracionesSinMejora(25)
                    .construir();

            List<Vehiculo> flota = datos.flota();
            ejecucion.inicioRealMs = reloj.ahoraMs();
            registrarFotograma(ejecucion, request.inicio(),
                    flota.stream().map(Vistas::vehiculo).toList(), ejecucion.inventario);
            Simulador simulador = new Simulador(planificador, ruteo,
                    datos.almacenes(), parametros, Duration.ofMinutes(30),
                    request.escenario() == Escenario.COLAPSO)
                    .conMantenimiento(entrada.mantenimiento())
                    .conEventosDeBloqueo(mapa)
                    .conIntervaloMinimo(Duration.ofMinutes(15))
                    .observadoPor(new Observador() {
                    @Override
                    public void alIniciarPlanificacion(LocalDateTime reloj) {
                        verificarCancelacion(ejecucion);
                    }

                    @Override
                    public void alPlanificar(LocalDateTime reloj, EstadoOperacion estado,
                                            Solucion plan, MedicionDePlanificacion medicion,
                                            AvanceDeLaOperacion avance) {
                        verificarCancelacion(ejecucion);
                        Ventana ventana = ejecucion.ventanaEnCurso;
                        ventana.relojPlan = reloj;
                        ventana.rutas = plan.getRutas().stream().map(Vistas::ruta).toList();
                        ventana.avance = Vistas.avance(avance, medicion);
                        ventana.noAsignados = Vistas.pedidos(plan.getPedidosNoAsignados(),
                                "NO_ASIGNADO", "SIN_RUTA_FACTIBLE");
                        ventana.trayectosIteracion = List.of();
                        ventana.huboPlan = true;
                        detectarReplanificaciones(ejecucion, reloj, estado, plan);
                    }

                    @Override
                    public void alEjecutar(LocalDateTime desde, LocalDateTime hasta,
                                           List<Trayecto> trayectos) {
                        Ventana ventana = ejecucion.ventanaEnCurso;
                        ventana.trayectosIteracion = trayectos.stream().map(Vistas::trayecto).toList();
                        for (TrayectoVista trayecto : ventana.trayectosIteracion) {
                            ventana.trayectos.computeIfAbsent(trayecto.vehiculoId(),
                                    clave -> new ArrayList<>()).addAll(trayecto.pasos());
                        }
                    }

                    @Override
                    public void alAvanzar(LocalDateTime reloj, List<Vehiculo> vehiculos) {
                        Ventana ventana = ejecucion.ventanaEnCurso;
                        if (ventana.desde == null) ventana.desde = ejecucion.ventanaHasta;
                        List<VehiculoVista> posiciones = vehiculos.stream().map(Vistas::vehiculo).toList();
                        if (ventana.relojPlan != null) {
                            ventana.planes.add(new PlanVista(-1, ventana.relojPlan, ventana.rutas,
                                    reloj, ventana.trayectosIteracion, ventana.noAsignados));
                            ventana.relojPlan = null;
                        } else if (!ventana.huboPlan) {
                            // Sin pedidos en cola no hubo plan: nada queda vigente.
                            ventana.rutas = List.of();
                            ventana.noAsignados = List.of();
                        }
                        ventana.huboPlan = false;
                        ventana.fotogramas.add(new FotogramaVista(-1, reloj, posiciones, null));
                        ventana.hasta = reloj;
                        ventana.vehiculos = posiciones;
                    }

                    @Override
                    public void alActualizarInventario(LocalDateTime reloj, List<Almacen> almacenes) {
                        Ventana ventana = ejecucion.ventanaEnCurso;
                        ventana.inventario = new InventarioVista(reloj,
                                almacenes.stream().map(Vistas::stock).toList());
                        int ultimo = ventana.fotogramas.size() - 1;
                        if (ultimo >= 0 && ventana.fotogramas.get(ultimo).reloj().equals(reloj)) {
                            FotogramaVista fotograma = ventana.fotogramas.get(ultimo);
                            ventana.fotogramas.set(ultimo, new FotogramaVista(-1, reloj,
                                    fotograma.vehiculos(), ventana.inventario));
                        }
                        long limites = limitesHasta(ejecucion, reloj);
                        if (limites <= ejecucion.limitesPublicados) return;
                        publicarVentana(ejecucion, limites);
                        try {
                            if (request.pausaMs() != null && request.pausaMs() > 0) {
                                Thread.sleep(request.pausaMs());
                            }
                            ejecucion.marcapasos.esperarTurno();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new CancellationException("Ejecución interrumpida");
                        }
                        verificarCancelacion(ejecucion);
                    }
                    });

            ejecucion.simulador = simulador;
            actualizar(ejecucion, conRitmo(cambiarEstado(ejecucion.vista, Estado.EN_CURSO,
                    null, null), ritmoVista(ejecucion)), "INICIADA");

            ResumenSimulacion resumen = simulador.correr(request.inicio(),
                    Duration.ofDays(dias), entrada.ventas(), flota);
            if (ejecucion.vista.estado() == Estado.CANCELADA) return;
            // Lo que quedó sin publicar: el colapso corta antes del siguiente límite, que cuenta
            // como cruzado para que ese tramo también se vea.
            LocalDateTime pendienteHasta = ejecucion.ventanaEnCurso.hasta;
            if (pendienteHasta != null) {
                long sc = ejecucion.ritmo.saltoDelConsumo().toMillis();
                long simulado = Duration.between(ejecucion.inicio, pendienteHasta).toMillis();
                long alcanzados = Math.min(ejecucion.ritmo.ventanas(ejecucion.horizonte),
                        (simulado + sc - 1) / sc);
                publicarVentana(ejecucion, Math.max(ejecucion.limitesPublicados + 1, alcanzados));
            }
            synchronized (ejecucion) {
                for (int i = 0; i < ejecucion.averias.size(); i++) {
                    AveriaVista averia = ejecucion.averias.get(i);
                    boolean aplicada = resumen.averiasOcurridas().stream().anyMatch(a ->
                            a.unidadId().equals(averia.unidadId())
                                    && a.instante().equals(averia.instante()));
                    ejecucion.averias.set(i, new AveriaVista(averia.unidadId(), averia.tipo(),
                            averia.instante(), aplicada ? "APLICADA" : "NO_APLICADA",
                            averia.pedidosAfectados()));
                }
            }
            ColapsoVista colapso = resumen.huboColapso() ? colapso(ejecucion, resumen) : null;
            if (colapso != null) {
                registrarEvento(ejecucion, "COLAPSO", colapso.instante(), null,
                        colapso.pedido() == null ? null : colapso.pedido().id(), null,
                        "Primer pedido vencido sin entregar: la operación colapsó.");
            }
            // La última ventana también se reproduce entera antes de declarar el fin.
            ejecucion.marcapasos.esperarCierre();
            if (ejecucion.vista.estado() == Estado.CANCELADA) return;
            EjecucionVista anterior = ejecucion.vista;
            Estado finalEstado = resumen.huboColapso() ? Estado.COLAPSADA : Estado.TERMINADA;
            List<VehiculoVista> posicionesFinales;
            synchronized (ejecucion) {
                FotogramaVista ultimo = ejecucion.fotogramas.peekLast();
                posicionesFinales = ultimo == null ? anterior.vehiculos() : ultimo.vehiculos();
            }
            ejecucion.finRealMs = reloj.ahoraMs();
            EjecucionVista finalVista = new EjecucionVista(anterior.id(), finalEstado,
                    anterior.escenario(), anterior.algoritmo(), anterior.inicio(), resumen.fin(),
                    LocalDateTime.now(), anterior.avance(), posicionesFinales,
                    anterior.rutas(), anterior.noAsignados(), Vistas.resumen(resumen), null, null,
                    false, ritmoVista(ejecucion), anterior.trayectos(), colapso);
            actualizar(ejecucion, finalVista, resumen.huboColapso() ? "COLAPSO" : "TERMINADA");
        } catch (CancellationException e) {
            if (ejecucion.finRealMs == null) ejecucion.finRealMs = reloj.ahoraMs();
            actualizar(ejecucion, cambiarEstado(ejecucion.vista, Estado.CANCELADA, null, null),
                    "CANCELADA");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (ejecucion.finRealMs == null) ejecucion.finRealMs = reloj.ahoraMs();
            actualizar(ejecucion, cambiarEstado(ejecucion.vista, Estado.CANCELADA, null, null),
                    "CANCELADA");
        } catch (RuntimeException e) {
            ejecucion.finRealMs = reloj.ahoraMs();
            actualizar(ejecucion, cambiarEstado(ejecucion.vista, Estado.FALLIDA,
                    "ERROR_DE_EJECUCION", "La simulación no pudo completarse."), "FALLIDA");
        }
    }

    /** Límites de la grilla de Sc alcanzados al llegar el reloj a {@code reloj}. */
    private static long limitesHasta(Ejecucion ejecucion, LocalDateTime reloj) {
        long total = ejecucion.ritmo.ventanas(ejecucion.horizonte);
        if (!reloj.isBefore(ejecucion.inicio.plus(ejecucion.horizonte))) return total;
        long sc = ejecucion.ritmo.saltoDelConsumo().toMillis();
        return Math.min(total, Duration.between(ejecucion.inicio, reloj).toMillis() / sc);
    }

    /**
     * Saca a la luz lo calculado hasta alcanzar el límite {@code limites}: fotogramas, planes con
     * sus recorridos y el estado. Se verá durante tantos saltos como límites haya cruzado.
     */
    private void publicarVentana(Ejecucion ejecucion, long limites) {
        Ventana ventana = ejecucion.ventanaEnCurso;
        ejecucion.ventanaEnCurso = new Ventana();
        if (ventana.hasta == null || ventana.vehiculos == null) return;
        EjecucionVista siguiente;
        synchronized (ejecucion) {
            if (ejecucion.vista.estado() == Estado.CANCELADA) return;
            ejecucion.marcapasos.publicar((int) Math.max(1, limites - ejecucion.limitesPublicados));
            ejecucion.limitesPublicados = limites;
            ejecucion.ventanaDesde = ventana.desde;
            ejecucion.ventanaHasta = ventana.hasta;
            for (FotogramaVista fotograma : ventana.fotogramas) {
                registrarFotograma(ejecucion, fotograma.reloj(), fotograma.vehiculos(),
                        fotograma.inventario());
            }
            for (PlanVista plan : ventana.planes) {
                ejecucion.planes.addLast(new PlanVista(ejecucion.siguientePlan++, plan.reloj(),
                        plan.rutas(), plan.hasta(), plan.trayectos(), plan.noAsignados()));
                if (ejecucion.planes.size() > MAX_PLANES) ejecucion.planes.removeFirst();
            }
            if (ventana.inventario != null) ejecucion.inventario = ventana.inventario;
            for (int i = 0; i < ejecucion.averias.size(); i++) {
                AveriaVista averia = ejecucion.averias.get(i);
                if (ventana.vehiculos.stream().anyMatch(v -> v.id().equals(averia.unidadId())
                        && v.estado().equals(EstadoVehiculo.AVERIADO.name()))) {
                    ejecucion.averias.set(i, new AveriaVista(averia.unidadId(), averia.tipo(),
                            averia.instante(), "APLICADA", averia.pedidosAfectados()));
                }
            }
            EjecucionVista anterior = ejecucion.vista;
            List<TrayectoVista> trayectos = ventana.trayectos.entrySet().stream()
                    .map(e -> new TrayectoVista(e.getKey(), List.copyOf(e.getValue()))).toList();
            siguiente = new EjecucionVista(anterior.id(),
                    ejecucion.marcapasos.pausada() ? Estado.PAUSADA : Estado.EN_CURSO,
                    anterior.escenario(), anterior.algoritmo(), anterior.inicio(), ventana.hasta,
                    LocalDateTime.now(), ventana.avance != null ? ventana.avance : anterior.avance(),
                    ventana.vehiculos, ventana.rutas == null ? anterior.rutas() : ventana.rutas,
                    ventana.noAsignados == null ? anterior.noAsignados() : ventana.noAsignados,
                    null, null, null, false, ritmoVista(ejecucion), trayectos, null);
        }
        actualizar(ejecucion, siguiente, siguiente.rutas().isEmpty()
                && !siguiente.noAsignados().isEmpty() ? "PLAN_SIN_ASIGNACIONES" : "PROGRESO");
    }

    /**
     * Compara el plan nuevo con el anterior: los pedidos que tenía una unidad que ahora está
     * averiada se reasignaron a otra o volvieron a quedar por planificar.
     */
    private void detectarReplanificaciones(Ejecucion ejecucion, LocalDateTime reloj,
                                           EstadoOperacion estado, Solucion plan) {
        Set<String> averiadas = new HashSet<>();
        for (Vehiculo unidad : estado.getVehiculos()) {
            if (unidad.getEstado() == EstadoVehiculo.AVERIADO) averiadas.add(unidad.getId());
        }
        for (String unidad : averiadas) {
            if (ejecucion.averiadasConocidas.add(unidad)) {
                registrarEvento(ejecucion, "AVERIA_APLICADA", reloj, unidad, null, null,
                        "La unidad sale de servicio; sus pedidos se replanifican.");
            }
        }
        ejecucion.averiadasConocidas.retainAll(averiadas);

        Map<String, String> asignacion = new LinkedHashMap<>();
        for (Ruta ruta : plan.getRutas()) {
            for (Pedido pedido : ruta.getPedidos()) {
                asignacion.put(pedido.getId(), ruta.getVehiculo().getId());
            }
        }
        Set<String> enCola = new HashSet<>();
        for (Pedido pedido : estado.getPedidos()) enCola.add(pedido.getId());

        Map<String, Integer> afectadosPorUnidad = new LinkedHashMap<>();
        for (Map.Entry<String, String> previa : ejecucion.asignacionAnterior.entrySet()) {
            String pedidoId = previa.getKey();
            String unidad = previa.getValue();
            if (!averiadas.contains(unidad) || !enCola.contains(pedidoId)
                    || !ejecucion.replanificados.add(pedidoId + "|" + unidad)) {
                continue;
            }
            String nueva = asignacion.get(pedidoId);
            registrarEvento(ejecucion, "REPLANIFICACION", reloj, unidad, pedidoId, nueva,
                    nueva == null
                            ? "Avería de " + unidad + ": el pedido vuelve a por planificar."
                            : "Avería de " + unidad + ": el pedido pasa a " + nueva + ".");
            ejecucion.replanificaciones++;
            afectadosPorUnidad.merge(unidad, 1, Integer::sum);
        }
        ejecucion.asignacionAnterior = asignacion;
        if (afectadosPorUnidad.isEmpty()) return;
        synchronized (ejecucion) {
            for (int i = 0; i < ejecucion.averias.size(); i++) {
                AveriaVista averia = ejecucion.averias.get(i);
                Integer extra = afectadosPorUnidad.get(averia.unidadId());
                if (extra != null) {
                    ejecucion.averias.set(i, new AveriaVista(averia.unidadId(), averia.tipo(),
                            averia.instante(), averia.estado(), averia.pedidosAfectados() + extra));
                }
            }
        }
    }

    private ColapsoVista colapso(Ejecucion ejecucion, ResumenSimulacion resumen) {
        Pedido pedido = resumen.pedidoQueColapso();
        PedidoVista vista = pedido == null ? null
                : Vistas.pedido(pedido, "VENCIDO", "Plazo vencido sin entrega");
        List<VehiculoVista> vehiculos = ejecucion.vista.vehiculos();
        int averiados = (int) vehiculos.stream()
                .filter(v -> v.estado().equals(EstadoVehiculo.AVERIADO.name())).count();
        int disponibles = (int) vehiculos.stream()
                .filter(v -> v.estado().equals(EstadoVehiculo.DISPONIBLE.name())).count();
        AvanceVista avance = ejecucion.vista.avance();
        return new ColapsoVista(resumen.instanteDelColapso(), vista, (int) ejecucion.limitesPublicados,
                reloj.ahoraMs() - ejecucion.inicioRealMs,
                avance == null ? resumen.pedidosOriginalesCompletos() : avance.pedidosEntregados(),
                resumen.vencidos().size(), resumen.pedidosPendientes(), disponibles, averiados,
                ejecucion.replanificaciones);
    }

    private static void verificarCancelacion(Ejecucion ejecucion) {
        if (Thread.currentThread().isInterrupted()
                || ejecucion.vista.estado() == Estado.CANCELADA) {
            throw new CancellationException("Ejecución cancelada");
        }
    }

    private void actualizar(Ejecucion ejecucion, EjecucionVista vista, String tipo) {
        synchronized (ejecucion) {
            if (ejecucion.vista.estado() == Estado.CANCELADA && vista.estado() != Estado.CANCELADA)
                return;
            ejecucion.vista = vista;
        }
        eventos.publicar(new EventoVista(tipo, vista));
    }

    private static void registrarEvento(Ejecucion ejecucion, String tipo, LocalDateTime reloj,
                                        String vehiculoId, String pedidoId, String vehiculoNuevo,
                                        String detalle) {
        synchronized (ejecucion) {
            ejecucion.eventos.addLast(new EventoOperacionVista(ejecucion.siguienteEvento++, tipo,
                    reloj, vehiculoId, pedidoId, vehiculoNuevo, detalle));
            if (ejecucion.eventos.size() > MAX_EVENTOS) ejecucion.eventos.removeFirst();
        }
    }

    private static void registrarFotograma(Ejecucion ejecucion, LocalDateTime reloj,
                                           List<VehiculoVista> posiciones,
                                           InventarioVista inventario) {
        synchronized (ejecucion) {
            if (ejecucion.vista.estado() == Estado.CANCELADA) return;
            ejecucion.fotogramas.addLast(new FotogramaVista(
                    ejecucion.siguienteIndice++, reloj, posiciones, inventario));
            if (ejecucion.fotogramas.size() > MAX_FOTOGRAMAS) {
                ejecucion.fotogramas.removeFirst();
            }
        }
    }

    private RitmoVista ritmoVista(Ejecucion ejecucion) {
        RitmoEscenario ritmo = ejecucion.ritmo;
        Duration sc = ritmo.saltoDelConsumo();
        long publicado = ejecucion.marcapasos.publicadoMs();
        long duracion = ejecucion.marcapasos.duracionMs();
        long simulado = Duration.between(ejecucion.ventanaDesde, ejecucion.ventanaHasta).toMillis();
        // K de la ventana visible: lo simulado entre sus cortes repartido en su duración real.
        double k = simulado > 0 ? (double) simulado / duracion : (double) sc.toMillis() / ritmo.saMs();
        return new RitmoVista(ritmo.saMs(), sc.toMillis() / 60_000.0, k,
                ritmo.duracionObjetivoMin(), ritmo.velocidad(), (int) ejecucion.limitesPublicados,
                (int) ritmo.ventanas(ejecucion.horizonte), ejecucion.ventanaDesde,
                ejecucion.ventanaHasta, publicado < 0 ? reloj.ahoraMs() : publicado,
                ejecucion.marcapasos.transcurridoAlPausarMs(), reloj.ahoraMs(),
                ejecucion.marcapasos.rebasadas(),
                ritmo.duracionEstimada(ejecucion.horizonte).toMillis(), duracion,
                ejecucion.inicioRealMs, ejecucion.finRealMs);
    }

    private static EjecucionVista conRitmo(EjecucionVista a, RitmoVista ritmo) {
        return new EjecucionVista(a.id(), a.estado(), a.escenario(), a.algoritmo(), a.inicio(),
                a.reloj(), a.actualizado(), a.avance(), a.vehiculos(), a.rutas(),
                a.noAsignados(), a.resumen(), a.codigoError(), a.mensajeError(),
                a.calculandoRutas(), ritmo, a.trayectos(), a.colapso());
    }

    private static EjecucionVista cambiarEstado(EjecucionVista anterior, Estado estado,
                                                String codigo, String mensaje) {
        return new EjecucionVista(anterior.id(), estado, anterior.escenario(),
                anterior.algoritmo(), anterior.inicio(), anterior.reloj(), LocalDateTime.now(),
                anterior.avance(), anterior.vehiculos(), anterior.rutas(),
                anterior.noAsignados(), anterior.resumen(), codigo, mensaje, false,
                anterior.ritmo(), anterior.trayectos(), anterior.colapso());
    }

    private static boolean esFinal(Estado estado) {
        return estado == Estado.TERMINADA || estado == Estado.COLAPSADA
                || estado == Estado.FALLIDA || estado == Estado.CANCELADA;
    }

    private static void validarDesde(long desde) {
        if (desde < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INDICE_INVALIDO",
                    "El índice inicial no puede ser negativo.");
        }
    }

    private static int dias(CrearEjecucionRequest request) {
        int esperado = switch (request.escenario()) {
            case OPERACION_DIARIA -> 1;
            case PERIODO_5_DIAS -> 5;
            case COLAPSO -> request.horizonteDias() == null ? 30 : request.horizonteDias();
        };
        if (esperado < 1 || esperado > 60 || request.escenario() != Escenario.COLAPSO
                && request.horizonteDias() != null && request.horizonteDias() != esperado) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HORIZONTE_INVALIDO",
                    "El horizonte debe ser 1 día, 5 días o entre 1 y 60 para colapso.");
        }
        return esperado;
    }

    private void limpiarAntiguas() {
        LocalDateTime limite = LocalDateTime.now().minusHours(2);
        ejecuciones.entrySet().removeIf(entrada -> {
            EjecucionVista vista = entrada.getValue().vista;
            return esFinal(vista.estado()) && vista.actualizado().isBefore(limite);
        });
    }

    @PreDestroy
    void detener() { executor.shutdownNow(); }

    /**
     * Lo que se va juntando de una ventana mientras se calcula, iteración por iteración del
     * simulador; se publica de una vez al cruzar el límite de la grilla.
     */
    private static final class Ventana {
        LocalDateTime desde;
        LocalDateTime hasta;
        LocalDateTime relojPlan;
        boolean huboPlan;
        List<RutaVista> rutas;
        List<PedidoVista> noAsignados;
        AvanceVista avance;
        List<TrayectoVista> trayectosIteracion = List.of();
        final Map<String, List<ApiModels.PasoVista>> trayectos = new LinkedHashMap<>();
        final List<PlanVista> planes = new ArrayList<>();
        final List<FotogramaVista> fotogramas = new ArrayList<>();
        List<VehiculoVista> vehiculos;
        InventarioVista inventario;
    }

    private static final class Ejecucion {
        final RitmoEscenario ritmo;
        final Marcapasos marcapasos;
        final Duration horizonte;
        final LocalDateTime inicio;
        volatile EjecucionVista vista;
        volatile Future<?> tarea;
        volatile Simulador simulador;
        volatile InventarioVista inventario;
        volatile Ventana ventanaEnCurso = new Ventana();
        final List<AveriaVista> averias = new ArrayList<>();
        final ArrayDeque<FotogramaVista> fotogramas = new ArrayDeque<>();
        final ArrayDeque<PlanVista> planes = new ArrayDeque<>();
        final ArrayDeque<EventoOperacionVista> eventos = new ArrayDeque<>();
        final Set<String> averiadasConocidas = new HashSet<>();
        final Set<String> replanificados = new HashSet<>();
        Map<String, String> asignacionAnterior = Map.of();
        long siguienteIndice;
        long siguientePlan;
        long siguienteEvento;
        int pedidosEnVivo;
        long inicioRealMs;
        Long finRealMs;
        long limitesPublicados;
        int replanificaciones;
        LocalDateTime ventanaDesde;
        LocalDateTime ventanaHasta;

        Ejecucion(RitmoEscenario ritmo, Marcapasos marcapasos, Duration horizonte,
                  LocalDateTime inicio) {
            this.ritmo = ritmo;
            this.marcapasos = marcapasos;
            this.horizonte = horizonte;
            this.inicio = inicio;
            this.ventanaDesde = inicio;
            this.ventanaHasta = inicio;
        }
    }
}
