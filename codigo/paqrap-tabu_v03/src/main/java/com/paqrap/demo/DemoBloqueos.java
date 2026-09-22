package com.paqrap.demo;

import com.paqrap.datos.LectorBloqueos;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.Solucion;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import com.paqrap.planificador.EnrutadorBloqueos;
import com.paqrap.planificador.MapaBloqueos;
import com.paqrap.planificador.Parametros;
import com.paqrap.planificador.tabu.BusquedaTabu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Efecto de los tramos cerrados sobre el mismo plan: se resuelve la instancia con la ciudad
 * despejada y luego con los bloqueos del archivo mensual.
 *
 * Ejecutar con:
 *   java -cp target/classes com.paqrap.demo.DemoBloqueos [ruta/al/aaaamm.bloqueadas]
 */
public final class DemoBloqueos {
    private static final Path ARCHIVO_POR_DEFECTO = Path.of("datos", "202609.bloqueadas");

    private DemoBloqueos() {
    }

    public static void main(String[] args) throws IOException {
        LocalDateTime ahora = LocalDateTime.of(2026, 9, 8, 8, 0);
        Path archivo = args.length > 0 ? Path.of(args[0]) : ARCHIVO_POR_DEFECTO;

        if (!Files.exists(archivo)) {
            System.out.println("No se encontro el archivo de bloqueos: " + archivo.toAbsolutePath());
            System.out.println("Ejecute la demo desde la raiz del proyecto o pase la ruta como argumento.");
            return;
        }

        List<Bloqueo> bloqueos = LectorBloqueos.leer(archivo);
        MapaBloqueos mapa = new MapaBloqueos(bloqueos);

        System.out.println("=== EFECTO DE LOS BLOQUEOS ===");
        System.out.println("Archivo: " + archivo);
        System.out.println("Registros leidos: " + bloqueos.size());
        for (Bloqueo bloqueo : bloqueos) {
            System.out.println("  " + bloqueo);
        }
        System.out.println("Nodos cerrados a las " + ahora + ": "
                + mapa.nodosBloqueadosEn(ahora).size());

        EscenarioDemo despejada = EscenarioDemo.mediano(ahora);
        EscenarioDemo conBloqueos = despejada.conDistancias(new EnrutadorBloqueos(mapa));

        Parametros parametros = Parametros.constructor(50, 0.30, 20260908L)
                .iteracionesTabu(300)
                .tamanoMuestraVecindario(60)
                .limiteMilisegundos(3_000L)
                .construir();

        Solucion sinBloqueos = new BusquedaTabu(despejada.distancias())
                .planificar(despejada.estado(), parametros);
        Solucion conDesvios = new BusquedaTabu(conBloqueos.distancias())
                .planificar(conBloqueos.estado(), parametros);

        Reporte.imprimir("CIUDAD DESPEJADA", sinBloqueos);
        Reporte.imprimir("CON BLOQUEOS VIGENTES", conDesvios);

        System.out.printf(
                "%nCiudad despejada: S/ %.2f en %.2f km%nCon bloqueos:      S/ %.2f en %.2f km%n",
                sinBloqueos.getCostoTotal(), sinBloqueos.getDistanciaTotalKm(),
                conDesvios.getCostoTotal(), conDesvios.getDistanciaTotalKm()
        );

        mostrarUnDesvio(mapa, ahora);
    }

    /** Camino concreto que tendria que seguir una unidad para esquivar un tramo cerrado. */
    private static void mostrarUnDesvio(MapaBloqueos mapa, LocalDateTime ahora) {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
        Ubicacion origen = new Ubicacion(27, 14);
        Ubicacion destino = new Ubicacion(27, 20);
        double velocidad = TipoVehiculo.MOTO.getEspecificacionDelCaso().velocidadKmH();

        System.out.println();
        System.out.println("Desvio de ejemplo " + origen + " -> " + destino + " en moto");
        System.out.printf("  sin bloqueos: %.0f km%n", origen.distanciaManhattanKm(destino));
        System.out.printf("  con bloqueos: %.0f km%n",
                enrutador.calcularKm(origen, destino, ahora, velocidad));
        System.out.println("  recorrido:    " + enrutador.camino(origen, destino, ahora, velocidad));
    }
}
