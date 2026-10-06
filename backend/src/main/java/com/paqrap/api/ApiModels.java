package com.paqrap.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** JSON publico; ninguna clase mutable del planificador se serializa directamente. */
public final class ApiModels {
    private ApiModels() { }

    public enum Escenario { OPERACION_DIARIA, PERIODO_5_DIAS, COLAPSO }
    public enum Algoritmo { GRASP, TABU }
    public enum Estado { PENDIENTE, EN_CURSO, TERMINADA, COLAPSADA, FALLIDA, CANCELADA }

    public record CrearEjecucionRequest(
            @NotNull Escenario escenario,
            @NotNull Algoritmo algoritmo,
            @NotNull LocalDateTime inicio,
            Integer horizonteDias,
            Long semilla
    ) { }

    public record Coordenada(int x, int y) { }

    public record PedidoVista(
            String id, String idOriginal, Coordenada destino, int cantidad,
            LocalDateTime fechaRegistro, LocalDateTime fechaLimite, String estado,
            String motivo
    ) { }

    public record VehiculoVista(
            String id, String tipo, Coordenada ubicacion, String estado, int capacidad,
            int cargaAbordo
    ) { }

    public record RutaVista(
            String id, String vehiculoId, String tipoVehiculo, String almacenId,
            Coordenada origen, int carga, double distanciaKm, double costo,
            List<PedidoVista> paradas
    ) { }

    public record AvanceVista(
            int pedidosEntregados, int productosEntregados, int pedidosVencidos,
            int productosVencidos, int productosAbordo, int pedidosEnCola,
            long milisegundosPlanificacion
    ) { }

    public record ResumenVista(
            LocalDateTime inicio, LocalDateTime fin, int pedidosRecibidos,
            int pedidosOriginalesCompletos, int pedidosOriginalesEnPlazo,
            int pedidosPendientes, int productosRecibidos, double distanciaTotalKm,
            double costoTotal, int iteracionesPlanificacion, boolean huboColapso,
            LocalDateTime instanteColapso, String pedidoQueColapso
    ) { }

    public record EjecucionVista(
            UUID id, Estado estado, Escenario escenario, Algoritmo algoritmo,
            LocalDateTime inicio, LocalDateTime reloj, LocalDateTime actualizado,
            AvanceVista avance, List<VehiculoVista> vehiculos, List<RutaVista> rutas,
            List<PedidoVista> noAsignados, ResumenVista resumen,
            String codigoError, String mensajeError
    ) { }

    public record EventoVista(String tipo, EjecucionVista ejecucion) { }
    public record ErrorVista(String codigo, String mensaje) { }

    public record PeriodoVista(
            String periodo, int pedidos, int bloqueos, int jornadasMantenimiento,
            boolean mantenimientoDisponible
    ) { }
}
