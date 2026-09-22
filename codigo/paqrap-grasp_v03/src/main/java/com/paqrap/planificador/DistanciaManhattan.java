package com.paqrap.planificador;

import com.paqrap.modelo.Ubicacion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementación de {@link CalculadorDistancia} basada en distancia Manhattan sobre una
 * cuadrícula (x,y), pensada para los datos reales del caso (los archivos de ventas traen
 * posX,posY de cada cliente sobre una cuadrícula, tal como los usa el proyecto de búsqueda
 * tabú del equipo).
 *
 * {@link Ubicacion} sigue siendo, a propósito, un identificador lógico opaco (ver su propio
 * comentario de diseño): esta clase no cambia esa decisión, solo interpreta el id como "x,y"
 * para las ubicaciones que ella misma construyó con {@link #deCoordenadas(int, int)}. No
 * reemplaza a {@link MatrizDistancias}, que sigue siendo válida para escenarios con
 * identificadores arbitrarios (los usados en los demos y verificaciones existentes).
 */
public final class DistanciaManhattan implements CalculadorDistancia {
    private static final Pattern COORDENADAS = Pattern.compile("^(-?\\d+),(-?\\d+)$");

    @Override
    public double calcularKm(Ubicacion origen, Ubicacion destino) {
        int[] o = coordenadasDe(origen);
        int[] d = coordenadasDe(destino);
        return Math.abs(o[0] - d[0]) + Math.abs(o[1] - d[1]);
    }

    /** Construye una Ubicacion cuyo id codifica la coordenada (x,y), lista para esta clase. */
    public static Ubicacion deCoordenadas(int x, int y) {
        return new Ubicacion(x + "," + y);
    }

    private static int[] coordenadasDe(Ubicacion ubicacion) {
        Matcher coincidencia = COORDENADAS.matcher(ubicacion.getId());
        if (!coincidencia.matches()) {
            throw new IllegalArgumentException(
                    "DistanciaManhattan requiere ubicaciones creadas con deCoordenadas(x,y); recibido: "
                            + ubicacion.getId());
        }
        return new int[] {
                Integer.parseInt(coincidencia.group(1)),
                Integer.parseInt(coincidencia.group(2))
        };
    }
}
