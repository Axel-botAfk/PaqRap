package com.paqrap.api;

import com.paqrap.simulacion.MedicionDePlanificacion;
import java.util.List;
import java.util.UUID;

/** Serie de llamadas reales, no muestras obtenidas por sondeo del visualizador. */
public record RendimientoVista(UUID id, long inicioRealMs, Long finRealMs,
        long cantidad, long sumaTaMs, double promedioTaMs, long minimoTaMs,
        long maximoTaMs, long percentil95TaMs, List<MedicionDePlanificacion> mediciones) {
    static RendimientoVista de(UUID id, long inicio, Long fin,
                               List<MedicionDePlanificacion> serie) {
        List<MedicionDePlanificacion> copia = List.copyOf(serie);
        long[] tiempos = copia.stream().mapToLong(MedicionDePlanificacion::milisegundos)
                .sorted().toArray();
        long suma = copia.stream().mapToLong(MedicionDePlanificacion::milisegundos).sum();
        int n = tiempos.length;
        return new RendimientoVista(id, inicio, fin, n, suma,
                n == 0 ? 0 : (double) suma / n, n == 0 ? 0 : tiempos[0],
                n == 0 ? 0 : tiempos[n - 1],
                n == 0 ? 0 : tiempos[(int) Math.ceil(n * .95) - 1], copia);
    }
}
