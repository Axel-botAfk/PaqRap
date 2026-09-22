package com.paqrap.simulacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Resultado agregado de una corrida de {@link Simulador}.
 *
 * A diferencia del ResumenSimulacion del proyecto de búsqueda tabú del equipo, este no trae
 * métricas por pedido individual (hora exacta de entrega, cuál pedido causó un colapso, etc.):
 * el modelo de {@code Ruta} de este proyecto no expone esa granularidad, solo la duración total
 * de cada ruta (ver el comentario de diseño en {@link Simulador}). Lo que sí puede calcularse de
 * forma confiable es el agregado por día.
 */
public record ResumenSimulacion(
        LocalDateTime inicio,
        LocalDateTime fin,
        int pedidosRecibidos,
        int pedidosEntregados,
        int pedidosPendientesAlCierre,
        int pedidosVencidos,
        double distanciaTotalKm,
        double costoTotal,
        int cantidadDeLatidosConPlan,
        long milisegundosDeComputo,
        Map<LocalDate, Integer> recibidosPorDia,
        Map<LocalDate, Integer> entregasPorDia,
        Map<LocalDate, Integer> colaAlCierreDelDia
) {
    public double porcentajeEntregado() {
        return pedidosRecibidos == 0 ? 0.0 : 100.0 * pedidosEntregados / pedidosRecibidos;
    }

    public double porcentajeVencido() {
        return pedidosRecibidos == 0 ? 0.0 : 100.0 * pedidosVencidos / pedidosRecibidos;
    }

    public double costoPromedioPorPedidoEntregado() {
        return pedidosEntregados == 0 ? 0.0 : costoTotal / pedidosEntregados;
    }

    public double kmPromedioPorPedidoEntregado() {
        return pedidosEntregados == 0 ? 0.0 : distanciaTotalKm / pedidosEntregados;
    }
}
