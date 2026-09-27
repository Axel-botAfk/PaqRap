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
        int productosRecibidos,
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
        Map<LocalDate, Integer> colaAlCierreDelDia,
        Map<String, Integer> cantidadPorPedidoOriginal
) {
    public boolean huboColapso() {
        return pedidoQueColapso != null;
    }

    /**
     * Pedidos del cliente que recibieron <b>al menos una</b> entrega.
     *
     * No dice que el pedido esté servido: un pedido de diez del que llegaron cuatro aparece aquí
     * igual que uno entregado entero. Para saber si el cliente tiene lo que pidió está
     * {@link #pedidosOriginalesCompletos()}, y esa es la cifra que corresponde informar como
     * cobertura.
     */
    public int pedidosOriginalesConAlgunaEntrega() {
        return entregadoPorPedidoOriginal().size();
    }

    /** Cuánto producto llegó a cada pedido del cliente, sumando sus partes. */
    private Map<String, Integer> entregadoPorPedidoOriginal() {
        Map<String, Integer> porOriginal = new LinkedHashMap<>();
        for (Entrega entrega : entregas) {
            porOriginal.merge(
                    entrega.pedido().getIdOriginal(), entrega.pedido().getCantidad(), Integer::sum);
        }
        return porOriginal;
    }

    /**
     * Pedidos del cliente servidos por completo: llegó todo lo que pidió.
     *
     * Un pedido partido se sirve en varias visitas, posiblemente en unidades distintas y a horas
     * distintas. Está completo cuando la suma de lo entregado alcanza la cantidad que el cliente
     * pidió, no cuando llegó la primera parte.
     */
    public int pedidosOriginalesCompletos() {
        int completos = 0;
        for (Map.Entry<String, Integer> entrada : entregadoPorPedidoOriginal().entrySet()) {
            Integer pedido = cantidadPorPedidoOriginal.get(entrada.getKey());
            if (pedido != null && entrada.getValue() >= pedido) {
                completos++;
            }
        }
        return completos;
    }

    /** Pedidos que recibieron algo pero no todo: el cliente sigue esperando el resto. */
    public int pedidosOriginalesIncompletos() {
        return pedidosOriginalesConAlgunaEntrega() - pedidosOriginalesCompletos();
    }

    /**
     * Pedidos servidos por completo y <b>dentro del plazo</b>.
     *
     * El plazo se mide contra la <b>última</b> parte que llegó, no contra la primera: el cliente
     * no tiene su pedido hasta que llegó todo. Cada parte hereda la fecha de registro y el plazo
     * del pedido del que salió, de modo que todas comparten la misma fecha límite.
     *
     * Es la cifra de servicio que corresponde presentar. {@code entregasEnPlazo} cuenta visitas,
     * y tres visitas puntuales de un pedido al que le falta una cuarta no son un cliente servido.
     */
    public int pedidosOriginalesEnPlazo() {
        Map<String, Integer> entregado = new LinkedHashMap<>();
        Map<String, LocalDateTime> ultimaLlegada = new LinkedHashMap<>();
        Map<String, LocalDateTime> limite = new LinkedHashMap<>();

        for (Entrega entrega : entregas) {
            String original = entrega.pedido().getIdOriginal();
            entregado.merge(original, entrega.pedido().getCantidad(), Integer::sum);
            limite.put(original, entrega.pedido().getFechaLimite());
            LocalDateTime previa = ultimaLlegada.get(original);
            if (previa == null || entrega.llegada().isAfter(previa)) {
                ultimaLlegada.put(original, entrega.llegada());
            }
        }

        int enPlazo = 0;
        for (Map.Entry<String, Integer> entrada : entregado.entrySet()) {
            Integer pedido = cantidadPorPedidoOriginal.get(entrada.getKey());
            if (pedido == null || entrada.getValue() < pedido) {
                continue;
            }
            if (!ultimaLlegada.get(entrada.getKey()).isAfter(limite.get(entrada.getKey()))) {
                enPlazo++;
            }
        }
        return enPlazo;
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

    /**
     * Cobertura en pedidos: cuántos de los que llegaron se entregaron.
     *
     * Se informa junto a {@link #porcentajeAtendidoEnProductos()} y no en su lugar, porque las
     * dos cifras responden preguntas distintas y pueden separarse bastante: dejar sin atender
     * diez pedidos de una unidad no es lo mismo que dejar uno de diez, aunque en pedidos lo
     * primero se vea diez veces peor y en productos las dos den igual.
     */
    public double porcentajeAtendido() {
        return pedidosRecibidos == 0 ? 100.0 : 100.0 * pedidosEntregados / pedidosRecibidos;
    }

    /** Productos efectivamente puestos en manos del cliente. */
    public int productosEntregados() {
        int unidades = 0;
        for (Entrega entrega : entregas) {
            unidades += entrega.pedido().getCantidad();
        }
        return unidades;
    }

    /** Productos de pedidos que vencieron sin entregarse. */
    public int productosVencidos() {
        int unidades = 0;
        for (Pedido pedido : vencidos) {
            unidades += pedido.getCantidad();
        }
        return unidades;
    }

    /** Productos que siguen en cola al terminar la corrida. */
    public int productosPendientes() {
        return productosRecibidos - productosEntregados() - productosVencidos();
    }

    /**
     * Cobertura en productos, que es la unidad con la que mide la función objetivo.
     *
     * Es la cifra que corresponde comparar entre algoritmos: el planificador minimiza productos
     * sin atender, de modo que informar solo el porcentaje de pedidos mediría algo distinto de
     * lo que la búsqueda optimiza.
     */
    public double porcentajeAtendidoEnProductos() {
        return productosRecibidos == 0
                ? 100.0
                : 100.0 * productosEntregados() / productosRecibidos;
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
