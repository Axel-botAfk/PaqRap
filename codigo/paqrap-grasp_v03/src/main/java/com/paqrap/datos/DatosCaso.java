package com.paqrap.datos;

import com.paqrap.modelo.Almacen;
import com.paqrap.modelo.EstadoVehiculo;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.modelo.Vehiculo;
import com.paqrap.planificador.DistanciaManhattan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Datos fijos de la operación de PaqRap, tomados del enunciado y de las respuestas publicadas
 * por el equipo docente (las mismas coordenadas y el mismo tamaño de flota que usa el proyecto
 * de búsqueda tabú del equipo para este caso).
 *
 * Antes de esto, los demos de GRASP inventaban sus propios almacenes/flota de prueba
 * (ver DemoGrasp, DemoGraspDataset, etc.), lo cual sigue siendo válido para esos escenarios
 * pequeños. Esta clase es, en cambio, la fuente única para correr GRASP contra los datos
 * reales del caso.
 */
public final class DatosCaso {
    public static final Ubicacion UBICACION_CENTRAL = DistanciaManhattan.deCoordenadas(27, 14);
    public static final Ubicacion UBICACION_NOR_OESTE = DistanciaManhattan.deCoordenadas(12, 38);
    public static final Ubicacion UBICACION_ESTE = DistanciaManhattan.deCoordenadas(57, 27);

    public static final String ID_CENTRAL = "ALM-CENTRAL";
    public static final String ID_NOR_OESTE = "ALM-NOR-OESTE";
    public static final String ID_ESTE = "ALM-ESTE";

    /** Unidades por tipo: 10 autos, 15 motos y 12 bicicletas (37 en total). */
    public static final Map<TipoVehiculo, Integer> UNIDADES_POR_TIPO = Map.of(
            TipoVehiculo.AUTO, 10,
            TipoVehiculo.MOTO, 15,
            TipoVehiculo.BICICLETA, 12
    );

    /**
     * Prefijo de código de unidad por tipo (formato TTNN del archivo de mantenimiento
     * preventivo, pregunta 19: p.e. "TA01" para el auto número 1). TipoVehiculo no trae este
     * prefijo, así que se define aquí junto con el resto de datos fijos del caso.
     */
    private static final Map<TipoVehiculo, String> PREFIJO_POR_TIPO = Map.of(
            TipoVehiculo.AUTO, "TA",
            TipoVehiculo.MOTO, "TM",
            TipoVehiculo.BICICLETA, "TB"
    );

    private DatosCaso() {
    }

    /** Central de inventario infinito y los dos intermedios a capacidad completa (1000). */
    public static List<Almacen> almacenes() {
        return List.of(
                Almacen.central(ID_CENTRAL, UBICACION_CENTRAL),
                Almacen.intermedio(ID_NOR_OESTE, UBICACION_NOR_OESTE, Almacen.CAPACIDAD_MAXIMA_INTERMEDIO),
                Almacen.intermedio(ID_ESTE, UBICACION_ESTE, Almacen.CAPACIDAD_MAXIMA_INTERMEDIO)
        );
    }

    /** Flota completa (37 unidades), todas partiendo del almacén central. */
    public static List<Vehiculo> flota() {
        List<Vehiculo> unidades = new ArrayList<>();
        for (TipoVehiculo tipo : TipoVehiculo.values()) {
            int cantidad = UNIDADES_POR_TIPO.getOrDefault(tipo, 0);
            String prefijo = PREFIJO_POR_TIPO.get(tipo);
            for (int numero = 1; numero <= cantidad; numero++) {
                unidades.add(new Vehiculo(
                        String.format("%s%02d", prefijo, numero),
                        tipo,
                        EstadoVehiculo.DISPONIBLE,
                        UBICACION_CENTRAL
                ));
            }
        }
        return List.copyOf(unidades);
    }
}
