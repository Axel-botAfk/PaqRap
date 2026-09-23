package com.paqrap.datos;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Datos fijos de la operación de PaqRap, tomados del enunciado y de las respuestas
 * publicadas por el equipo docente.
 *
 * Reemplaza a los escenarios inventados de la primera iteración: las posiciones de los
 * almacenes, el tamaño de la flota y los códigos de las unidades ya no se improvisan en
 * cada demo.
 */
public final class DatosCaso {
    public static final Ubicacion UBICACION_CENTRAL = new Ubicacion(27, 14);
    public static final Ubicacion UBICACION_NOR_OESTE = new Ubicacion(12, 38);
    public static final Ubicacion UBICACION_ESTE = new Ubicacion(57, 27);

    public static final String ID_CENTRAL = "ALM-CENTRAL";
    public static final String ID_NOR_OESTE = "ALM-NOR-OESTE";
    public static final String ID_ESTE = "ALM-ESTE";

    /** Unidades por tipo: 10 autos, 15 motos y 12 bicicletas. */
    public static final Map<TipoVehiculo, Integer> UNIDADES_POR_TIPO = Map.of(
            TipoVehiculo.AUTO, 10,
            TipoVehiculo.MOTO, 15,
            TipoVehiculo.BICICLETA, 12
    );

    private DatosCaso() {
    }

    /** Central de inventario infinito y los dos intermedios con su capacidad completa. */
    public static List<Almacen> almacenes() {
        return List.of(
                Almacen.central(ID_CENTRAL, UBICACION_CENTRAL),
                Almacen.intermedioLleno(ID_NOR_OESTE, UBICACION_NOR_OESTE),
                Almacen.intermedioLleno(ID_ESTE, UBICACION_ESTE)
        );
    }

    /**
     * Flota completa al inicio de un escenario. Todas las unidades parten del almacén central,
     * según lo indicado para el arranque de cualquiera de los tres escenarios.
     */
    public static List<Vehiculo> flota() {
        return flotaEn(UBICACION_CENTRAL);
    }

    public static List<Vehiculo> flotaEn(Ubicacion ubicacionInicial) {
        List<Vehiculo> unidades = new ArrayList<>();
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            int cantidad = UNIDADES_POR_TIPO.getOrDefault(tipo, 0);
            for (int numero = 1; numero <= cantidad; numero++) {
                unidades.add(new Vehiculo(
                        tipo.codigoUnidad(numero),
                        tipo,
                        EstadoVehiculo.DISPONIBLE,
                        ubicacionInicial
                ));
            }
        }
        return List.copyOf(unidades);
    }

    /** Subconjunto de la flota, útil para instancias pequeñas de prueba. */
    public static List<Vehiculo> flotaReducida(int autos, int motos, int bicicletas) {
        Map<TipoVehiculo, Integer> cantidades = Map.of(
                TipoVehiculo.AUTO, autos,
                TipoVehiculo.MOTO, motos,
                TipoVehiculo.BICICLETA, bicicletas
        );

        List<Vehiculo> unidades = new ArrayList<>();
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            int cantidad = cantidades.getOrDefault(tipo, 0);
            if (cantidad > UNIDADES_POR_TIPO.get(tipo)) {
                throw new IllegalArgumentException(
                        "La flota del caso solo tiene " + UNIDADES_POR_TIPO.get(tipo)
                                + " unidades de tipo " + tipo + ".");
            }
            for (int numero = 1; numero <= cantidad; numero++) {
                unidades.add(new Vehiculo(
                        tipo.codigoUnidad(numero),
                        tipo,
                        EstadoVehiculo.DISPONIBLE,
                        UBICACION_CENTRAL
                ));
            }
        }
        return List.copyOf(unidades);
    }
}
