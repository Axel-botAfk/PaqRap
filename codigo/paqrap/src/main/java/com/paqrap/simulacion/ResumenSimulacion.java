package com.paqrap.simulacion;

import com.paqrap.modelo.Averia;
import com.paqrap.modelo.Pedido;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resultado de una corrida: qué tanto se atendió, cuánto costó y si la operación colapsó.
 *
 * El colapso logístico se declara cuando vence el plazo de un pedido sin que se haya
 * entregado. Con los planes que produce el planificador —que solo acepta entregas dentro del
 * plazo— eso ocurre cuando la flota ya no alcanza a comprometerse con un pedido y su fecha
 * límite pasa mientras sigue esperando.
 */
public record ResumenSimulacion(
        LocalDateTime inicio,
        LocalDateTime fin,
        int pedidosRecibidos,
        int pedidosEntregados,
        int pedidosPendientes,
        int entregasEnPlazo,
        double distanciaTotalKm,
        double costoTotal,
        int iteracionesDePlanificacion,
        long milisegundosDeComputo,
        List<Entrega> entregas,
        List<MedicionDePlanificacion> medicionesDePlanificacion,
        List<Averia> averiasOcurridas,
        Ritmo ritmo,
        Pedido pedidoQueColapso,
        LocalDateTime instanteDelColapso,
        List<Pedido> vencidos,
        List<Pedido> vencidosPorDestinoInalcanzable,
        Map<LocalDate, Integer> recibidosPorDia,
        Map<LocalDate, Integer> entregasPorDia,
        Map<LocalDate, Integer> colaAlCierreDelDia
) {
    public boolean huboColapso() {
        return pedidoQueColapso != null;
    }

    /**
     * Pedidos distintos que el cliente hizo, sin contar dos veces los que se partieron.
     *
     * Los contadores crudos cuentan entregas: un pedido partido en dos aparece como dos. Para
     * informar cobertura hay que agrupar por el pedido original, que es lo que el cliente
     * reconoce como suyo.
     */
    public int pedidosOriginalesEntregados() {
        Set<String> originales = new HashSet<>();
        for (Entrega entrega : entregas) {
            originales.add(entrega.pedido().getIdOriginal());
        }
        return originales.size();
    }

    /** Entregas parciales realizadas: visitas de más que costó partir pedidos. */
    public int entregasParciales() {
        int parciales = 0;
        for (Entrega entrega : entregas) {
            if (entrega.pedido().esParteDeOtro()) {
                parciales++;
            }
        }
        return parciales;
    }

    /** Pedidos del cliente que se llegaron a partir. */
    public int pedidosPartidos() {
        Set<String> partidos = new HashSet<>();
        for (Entrega entrega : entregas) {
            if (entrega.pedido().esParteDeOtro()) {
                partidos.add(entrega.pedido().getIdOriginal());
            }
        }
        return partidos.size();
    }

    /** Ta más corto observado, en milisegundos. */
    public long taMinimoMs() {
        return medicionesDePlanificacion.stream()
                .mapToLong(MedicionDePlanificacion::milisegundos).min().orElse(0L);
    }

    /** Ta más largo observado: es el que tiene que caber en el salto del algoritmo. */
    public long taMaximoMs() {
        return medicionesDePlanificacion.stream()
                .mapToLong(MedicionDePlanificacion::milisegundos).max().orElse(0L);
    }

    public long taPromedioMs() {
        return Math.round(medicionesDePlanificacion.stream()
                .mapToLong(MedicionDePlanificacion::milisegundos).average().orElse(0.0));
    }

    /**
     * Cola más larga que llegó a planificarse. Junto con {@link #taMaximoMs()} es lo que define
     * el peor caso que el salto del algoritmo tiene que aguantar.
     */
    public int colaMaximaPlanificada() {
        return medicionesDePlanificacion.stream()
                .mapToInt(MedicionDePlanificacion::pedidosEnCola).max().orElse(0);
    }

    /**
     * Planificaciones que tardaron más que el salto del algoritmo.
     *
     * Cada una es un momento en que la corrida siguiente habría arrancado sobre una que no
     * terminó. Con cero, el ritmo elegido aguanta; con más de cero, hay que agrandar Sa o
     * acotar el tiempo del planificador.
     */
    public int planificacionesQueRebasaronElSalto() {
        if (ritmo == null) {
            return 0;
        }
        int rebasadas = 0;
        for (MedicionDePlanificacion medicion : medicionesDePlanificacion) {
            if (medicion.milisegundos() > ritmo.milisegundosDelSalto()) {
                rebasadas++;
            }
        }
        return rebasadas;
    }

    /** Ta contra tamaño de cola, agrupado en tramos, que es como se dimensiona el salto. */
    public Map<String, long[]> taPorTamanoDeCola() {
        int[] cortes = {10, 25, 50, 100, 200, Integer.MAX_VALUE};
        String[] nombres = {"1-10", "11-25", "26-50", "51-100", "101-200", "201+"};

        Map<String, long[]> porTramo = new LinkedHashMap<>();
        for (int i = 0; i < nombres.length; i++) {
            for (MedicionDePlanificacion medicion : medicionesDePlanificacion) {
                if (medicion.pedidosEnCola() > cortes[i]) {
                    continue;
                }
                if (i > 0 && medicion.pedidosEnCola() <= cortes[i - 1]) {
                    continue;
                }
                // {cantidad, minimo, maximo, suma}
                long[] acumulado = porTramo.computeIfAbsent(
                        nombres[i], clave -> new long[]{0, Long.MAX_VALUE, 0, 0});
                acumulado[0]++;
                acumulado[1] = Math.min(acumulado[1], medicion.milisegundos());
                acumulado[2] = Math.max(acumulado[2], medicion.milisegundos());
                acumulado[3] += medicion.milisegundos();
            }
        }
        return porTramo;
    }

    /**
     * Vencimientos atribuibles a la operación, sin contar los pedidos cuyo destino estuvo
     * cerrado por la municipalidad durante toda su ventana de entrega.
     *
     * La distinción importa para leer el resultado: que una calle esté cerrada sobre la puerta
     * del cliente no es un colapso logístico de la empresa, porque ninguna unidad habría podido
     * llegar. Los dos números se informan por separado y ninguno se oculta.
     */
    public int vencidosPorLaOperacion() {
        return vencidos.size() - vencidosPorDestinoInalcanzable.size();
    }

    public double porcentajeAtendido() {
        return pedidosRecibidos == 0 ? 100.0 : 100.0 * pedidosEntregados / pedidosRecibidos;
    }

    /** Entregas diarias sostenidas: el techo real de la flota con este planificador. */
    public double entregasDiariasPromedio() {
        if (entregasPorDia().isEmpty()) {
            return 0.0;
        }
        int total = 0;
        for (int entregas : entregasPorDia().values()) {
            total += entregas;
        }
        return (double) total / entregasPorDia().size();
    }

    public double milisegundosPorIteracion() {
        return iteracionesDePlanificacion == 0
                ? 0.0
                : (double) milisegundosDeComputo / iteracionesDePlanificacion;
    }
}
