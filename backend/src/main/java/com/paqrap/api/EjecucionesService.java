package com.paqrap.api;

import com.paqrap.api.ApiModels.Algoritmo;
import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.api.ApiModels.EjecucionVista;
import com.paqrap.api.ApiModels.Escenario;
import com.paqrap.api.ApiModels.Estado;
import com.paqrap.api.ApiModels.EventoVista;
import com.paqrap.datos.DatosCaso;
import com.paqrap.modelo.Solucion;
import com.paqrap.planificador.Evaluador;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.Planificador;
import com.paqrap.planificador.grasp.Grasp;
import com.paqrap.planificador.ruteo.EnrutadorBloqueos;
import com.paqrap.planificador.ruteo.MapaBloqueos;
import com.paqrap.planificador.tabu.BusquedaTabu;
import com.paqrap.simulacion.ResumenSimulacion;
import com.paqrap.simulacion.Simulador;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Una ejecucion es un trabajo acotado, no una peticion HTTP bloqueante. */
@Service
final class EjecucionesService {
    private static final int MAX_REGISTROS = 32;
    private final DatosService datos;
    private final EventosWebSocket eventos;
    private final ConcurrentMap<UUID, Ejecucion> ejecuciones = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2),
            trabajo -> {
                Thread hilo = new Thread(trabajo, "paqrap-simulacion");
                hilo.setDaemon(true);
                return hilo;
            }, new ThreadPoolExecutor.AbortPolicy());

    EjecucionesService(DatosService datos, EventosWebSocket eventos) {
        this.datos = datos;
        this.eventos = eventos;
    }

    EjecucionVista crear(CrearEjecucionRequest request) {
        if (request == null || request.escenario() == null || request.algoritmo() == null
                || request.inicio() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA",
                    "Escenario, algoritmo e inicio son obligatorios.");
        }
        int dias = dias(request);
        DatosService.DatosEntrada entrada = datos.cargar(request.inicio(), dias);
        limpiarAntiguas();
        if (ejecuciones.size() >= MAX_REGISTROS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CAPACIDAD_AGOTADA",
                    "Hay demasiadas ejecuciones registradas. Intente más tarde.");
        }

        UUID id = UUID.randomUUID();
        Ejecucion ejecucion = new Ejecucion(new EjecucionVista(id, Estado.PENDIENTE,
                request.escenario(), request.algoritmo(), request.inicio(), request.inicio(),
                LocalDateTime.now(), null, List.of(), List.of(), List.of(), null, null, null));
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

    EjecucionVista consultar(UUID id) {
        Ejecucion ejecucion = ejecuciones.get(id);
        if (ejecucion == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EJECUCION_NO_ENCONTRADA",
                    "No existe una ejecución con ese identificador.");
        }
        return ejecucion.vista;
    }

    EjecucionVista cancelar(UUID id) {
        Ejecucion ejecucion = ejecuciones.get(id);
        if (ejecucion == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EJECUCION_NO_ENCONTRADA",
                    "No existe una ejecución con ese identificador.");
        }
        synchronized (ejecucion) {
            if (ejecucion.vista.estado() == Estado.PENDIENTE
                    || ejecucion.vista.estado() == Estado.EN_CURSO) {
                ejecucion.vista = cambiarEstado(ejecucion.vista, Estado.CANCELADA, null, null);
                if (ejecucion.tarea != null) ejecucion.tarea.cancel(true);
                eventos.publicar(new EventoVista("CANCELADA", ejecucion.vista));
            }
        }
        return ejecucion.vista;
    }

    private void correr(Ejecucion ejecucion, CrearEjecucionRequest request, int dias,
                        DatosService.DatosEntrada entrada) {
        try {
            if (ejecucion.vista.estado() == Estado.CANCELADA) return;
            actualizar(ejecucion, cambiarEstado(ejecucion.vista, Estado.EN_CURSO, null, null),
                    "INICIADA");

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

            Simulador simulador = new Simulador(planificador, ruteo,
                    DatosCaso.almacenes(), parametros, Duration.ofMinutes(30),
                    request.escenario() == Escenario.COLAPSO)
                    .conMantenimiento(entrada.mantenimiento())
                    .conEventosDeBloqueo(mapa)
                    .conIntervaloMinimo(Duration.ofMinutes(15))
                    .observadoPor((reloj, estado, plan, medicion, avance) -> {
                        if (Thread.currentThread().isInterrupted()
                                || ejecucion.vista.estado() == Estado.CANCELADA) {
                            throw new CancellationException("Ejecución cancelada");
                        }
                        EjecucionVista anterior = ejecucion.vista;
                        Solucion copia = plan;
                        EjecucionVista siguiente = new EjecucionVista(anterior.id(),
                                Estado.EN_CURSO, anterior.escenario(), anterior.algoritmo(),
                                anterior.inicio(), reloj, LocalDateTime.now(),
                                Vistas.avance(avance, medicion),
                                estado.getVehiculos().stream().map(Vistas::vehiculo).toList(),
                                copia.getRutas().stream().map(Vistas::ruta).toList(),
                                Vistas.pedidos(copia.getPedidosNoAsignados(), "NO_ASIGNADO",
                                        "SIN_RUTA_FACTIBLE"),
                                null, null, null);
                        actualizar(ejecucion, siguiente, copia.getRutas().isEmpty()
                                && !copia.getPedidosNoAsignados().isEmpty()
                                        ? "PLAN_SIN_ASIGNACIONES" : "PROGRESO");
                    });

            ResumenSimulacion resumen = simulador.correr(request.inicio(),
                    Duration.ofDays(dias), entrada.ventas(), DatosCaso.flota());
            if (ejecucion.vista.estado() == Estado.CANCELADA) return;
            EjecucionVista anterior = ejecucion.vista;
            Estado finalEstado = resumen.huboColapso() ? Estado.COLAPSADA : Estado.TERMINADA;
            EjecucionVista finalVista = new EjecucionVista(anterior.id(), finalEstado,
                    anterior.escenario(), anterior.algoritmo(), anterior.inicio(), resumen.fin(),
                    LocalDateTime.now(), anterior.avance(), anterior.vehiculos(),
                    anterior.rutas(), anterior.noAsignados(), Vistas.resumen(resumen), null, null);
            actualizar(ejecucion, finalVista, resumen.huboColapso() ? "COLAPSO" : "TERMINADA");
        } catch (CancellationException e) {
            actualizar(ejecucion, cambiarEstado(ejecucion.vista, Estado.CANCELADA, null, null),
                    "CANCELADA");
        } catch (RuntimeException e) {
            actualizar(ejecucion, cambiarEstado(ejecucion.vista, Estado.FALLIDA,
                    "ERROR_DE_EJECUCION", "La simulación no pudo completarse."), "FALLIDA");
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

    private static EjecucionVista cambiarEstado(EjecucionVista anterior, Estado estado,
                                                String codigo, String mensaje) {
        return new EjecucionVista(anterior.id(), estado, anterior.escenario(),
                anterior.algoritmo(), anterior.inicio(), anterior.reloj(), LocalDateTime.now(),
                anterior.avance(), anterior.vehiculos(), anterior.rutas(),
                anterior.noAsignados(), anterior.resumen(), codigo, mensaje);
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
            return vista.estado() != Estado.PENDIENTE && vista.estado() != Estado.EN_CURSO
                    && vista.actualizado().isBefore(limite);
        });
    }

    @PreDestroy
    void detener() { executor.shutdownNow(); }

    private static final class Ejecucion {
        volatile EjecucionVista vista;
        volatile Future<?> tarea;
        Ejecucion(EjecucionVista vista) { this.vista = vista; }
    }
}
