package com.paqrap.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import com.paqrap.modelo.TipoAveria;

import java.time.LocalDateTime;
import java.time.LocalDate;
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
            Long semilla,
            Integer pausaMs
    ) {
        public CrearEjecucionRequest(Escenario escenario, Algoritmo algoritmo,
                LocalDateTime inicio, Integer horizonteDias, Long semilla) {
            this(escenario, algoritmo, inicio, horizonteDias, semilla, null);
        }
    }

    public record RegistrarAveriaRequest(@NotBlank String unidadId, @NotNull TipoAveria tipo) { }
    public record AveriaVista(String unidadId, TipoAveria tipo, LocalDateTime instante,
                             String estado) { }
    public record StockAlmacenVista(String id, String tipo, Coordenada ubicacion,
                                   Integer disponible, boolean ilimitado) { }
    public record InventarioVista(LocalDateTime reloj, List<StockAlmacenVista> almacenes) { }

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

    /** Posición observada después de ejecutar un salto del simulador. */
    public record FotogramaVista(long indice, LocalDateTime reloj,
                                List<VehiculoVista> vehiculos) { }

    public record FotogramasVista(long primero, long siguiente, boolean completo,
                                 List<FotogramaVista> fotogramas) { }

    /** Plan que estuvo vigente desde este instante hasta la siguiente replanificación. */
    public record PlanVista(long indice, LocalDateTime reloj, List<RutaVista> rutas) { }
    public record PlanesVista(long primero, long siguiente, boolean completo,
                             List<PlanVista> planes) { }

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
            String codigoError, String mensajeError, boolean calculandoRutas
    ) {
        public EjecucionVista(UUID id, Estado estado, Escenario escenario, Algoritmo algoritmo,
                LocalDateTime inicio, LocalDateTime reloj, LocalDateTime actualizado,
                AvanceVista avance, List<VehiculoVista> vehiculos, List<RutaVista> rutas,
                List<PedidoVista> noAsignados, ResumenVista resumen,
                String codigoError, String mensajeError) {
            this(id, estado, escenario, algoritmo, inicio, reloj, actualizado, avance,
                    vehiculos, rutas, noAsignados, resumen, codigoError, mensajeError, false);
        }
    }

    public record EventoVista(String tipo, EjecucionVista ejecucion) { }
    public record ErrorVista(String codigo, String mensaje) { }

    public record PeriodoVista(
            String periodo, int pedidos, int bloqueos, int jornadasMantenimiento,
            boolean mantenimientoDisponible, LocalDate primeraFechaPedido
    ) { }

    public record AlmacenVista(String id, String tipo, Coordenada ubicacion,
                               int stockInicial) { }
    public record BloqueoVista(LocalDateTime inicio, LocalDateTime fin,
                               List<Coordenada> nodos) { }
    public record MapaDatosVista(List<AlmacenVista> almacenes,
                                 List<BloqueoVista> bloqueos) { }
}
