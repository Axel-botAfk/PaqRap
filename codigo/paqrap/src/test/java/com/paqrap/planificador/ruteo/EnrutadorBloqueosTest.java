package com.paqrap.planificador.ruteo;

import com.paqrap.datos.LectorBloqueos;
import com.paqrap.modelo.Bloqueo;
import com.paqrap.modelo.TipoVehiculo;
import com.paqrap.modelo.Ubicacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnrutadorBloqueosTest {
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);
    private static final LocalDateTime AHORA = PERIODO.atDay(8).atTime(8, 0);

    private static final Ubicacion CENTRAL = new Ubicacion(27, 14);
    private static final Ubicacion AL_NORTE = new Ubicacion(27, 20);

    private static final double BICICLETA =
            TipoVehiculo.BICICLETA.getEspecificacionDelCaso().velocidadKmH();
    private static final double AUTO =
            TipoVehiculo.AUTO.getEspecificacionDelCaso().velocidadKmH();

    /** Muro horizontal en y=17 entre x=25 y x=29: obliga a cruzar por x=24 o por x=30. */
    private static MapaBloqueos muro() {
        return new MapaBloqueos(List.of(Bloqueo.dePoligonal(
                PERIODO.atDay(8).atTime(6, 0),
                PERIODO.atDay(8).atTime(15, 0),
                List.of(new Ubicacion(25, 17), new Ubicacion(29, 17))
        )));
    }

    @Test
    void sinBloqueosDebeCoincidirConLaDistanciaManhattan() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(MapaBloqueos.vacio());

        assertEquals(6.0, enrutador.calcularKm(CENTRAL, AL_NORTE, AHORA, BICICLETA));
        assertEquals(
                new DistanciaManhattan().calcularKm(CENTRAL, new Ubicacion(70, 50), AHORA, BICICLETA),
                enrutador.calcularKm(CENTRAL, new Ubicacion(70, 50), AHORA, BICICLETA)
        );
    }

    @Test
    void debeRodearElTramoCerrado() {
        assertEquals(12.0, new EnrutadorBloqueos(muro()).calcularKm(CENTRAL, AL_NORTE, AHORA, BICICLETA));
    }

    @Test
    void elBloqueoSoloDebeRegirDentroDeSuVentana() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(muro());

        assertEquals(6.0,
                enrutador.calcularKm(CENTRAL, AL_NORTE, PERIODO.atDay(8).atTime(4, 0), BICICLETA));
        assertEquals(12.0,
                enrutador.calcularKm(CENTRAL, AL_NORTE, PERIODO.atDay(8).atTime(6, 0), BICICLETA));
        assertEquals(6.0,
                enrutador.calcularKm(CENTRAL, AL_NORTE, PERIODO.atDay(8).atTime(15, 0), BICICLETA));
    }

    @Test
    void elCaminoNoDebePisarNodosCerrados() {
        MapaBloqueos mapa = muro();
        List<Ubicacion> camino = new EnrutadorBloqueos(mapa).camino(CENTRAL, AL_NORTE, AHORA, BICICLETA);

        assertEquals(CENTRAL, camino.get(0));
        assertEquals(AL_NORTE, camino.get(camino.size() - 1));
        assertEquals(13, camino.size());

        for (int i = 1; i < camino.size(); i++) {
            assertEquals(1.0, camino.get(i - 1).distanciaManhattanKm(camino.get(i)));
        }
        for (Ubicacion nodo : camino) {
            assertFalse(mapa.estaBloqueado(nodo, AHORA), "El camino pasa por " + nodo);
        }
    }

    @Test
    void unDestinoAisladoDebeSerInalcanzable() {
        MapaBloqueos mapa = new MapaBloqueos(List.of(Bloqueo.dePoligonal(
                PERIODO.atDay(8).atStartOfDay(),
                PERIODO.atDay(9).atStartOfDay(),
                List.of(new Ubicacion(1, 0), new Ubicacion(1, 1), new Ubicacion(0, 1))
        )));
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);

        assertTrue(Double.isInfinite(
                enrutador.calcularKm(CENTRAL, new Ubicacion(0, 0), AHORA, AUTO)));
        assertTrue(enrutador.camino(CENTRAL, new Ubicacion(0, 0), AHORA, AUTO).isEmpty());
    }

    /**
     * La unidad sale con la calle abierta pero llegaría a esa esquina después del cierre:
     * el recorrido tiene que rodearla igual.
     */
    @Test
    void debeRodearUnTramoQueSeCierraDuranteElTrayecto() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(muro());
        LocalDateTime salida = PERIODO.atDay(8).atTime(5, 50);

        // A 12 km/h la unidad llegaría al muro a las 06:05, diez minutos despues del cierre.
        assertEquals(12.0, enrutador.calcularKm(CENTRAL, AL_NORTE, salida, BICICLETA));

        for (Ubicacion nodo : enrutador.camino(CENTRAL, AL_NORTE, salida, BICICLETA)) {
            assertTrue(nodo.y() != 17 || nodo.x() < 25 || nodo.x() > 29,
                    "El camino pasa por el muro en " + nodo);
        }
    }

    /** Con el mismo instante de salida, un auto termina el tramo antes del cierre. */
    @Test
    void noDebeDesviarseSiCompletaElTramoAntesDelCierre() {
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(muro());
        LocalDateTime salida = PERIODO.atDay(8).atTime(5, 50);

        assertEquals(6.0, enrutador.calcularKm(CENTRAL, AL_NORTE, salida, AUTO));
    }

    @Test
    void debeLeerElFormatoDelArchivoDeBloqueos() {
        List<Bloqueo> bloqueos = LectorBloqueos.leerLineas(
                List.of("# comentario", "", "01d06h00m-01d15h00m:31,21,34,21"),
                PERIODO
        );

        assertEquals(1, bloqueos.size());
        Bloqueo bloqueo = bloqueos.get(0);
        assertEquals(PERIODO.atDay(1).atTime(6, 0), bloqueo.inicio());
        assertEquals(PERIODO.atDay(1).atTime(15, 0), bloqueo.fin());
        assertEquals(4, bloqueo.nodos().size());
        assertTrue(bloqueo.nodos().contains(new Ubicacion(32, 21)));
        assertEquals(PERIODO, LectorBloqueos.periodoDelNombre("202609.bloqueadas"));
    }

    /**
     * Un cliente ubicado sobre el tramo cerrado debe poder recibir su pedido: por el nodo
     * bloqueado no se pasa, pero se entra a entregar y se sale dando media vuelta. El tramo
     * sigue sin servir de paso, así que cruzarlo obliga a rodearlo.
     */
    @Test
    void unClienteSobreElTramoCerradoDebeRecibirSuPedido() {
        MapaBloqueos mapa = new MapaBloqueos(List.of(Bloqueo.dePoligonal(
                PERIODO.atDay(8).atTime(6, 0),
                PERIODO.atDay(8).atTime(15, 0),
                List.of(new Ubicacion(8, 23), new Ubicacion(20, 23))
        )));
        EnrutadorBloqueos enrutador = new EnrutadorBloqueos(mapa);
        Ubicacion cliente = new Ubicacion(15, 23);

        assertTrue(mapa.estaBloqueado(cliente, AHORA));
        assertFalse(mapa.estaAislado(cliente, AHORA));
        assertEquals(
                CENTRAL.distanciaManhattanKm(cliente),
                enrutador.calcularKm(CENTRAL, cliente, AHORA, AUTO)
        );

        List<Ubicacion> camino = enrutador.camino(CENTRAL, cliente, AHORA, AUTO);
        assertEquals(cliente, camino.get(camino.size() - 1));
        for (int i = 0; i < camino.size() - 1; i++) {
            assertFalse(mapa.estaBloqueado(camino.get(i), AHORA),
                    "El camino atraviesa " + camino.get(i));
        }

        assertEquals(14.0, enrutador.calcularKm(
                new Ubicacion(15, 22), new Ubicacion(15, 24), AHORA, AUTO));
    }

    @Test
    void debeRechazarUnTramoDiagonal() {
        assertThrows(IllegalArgumentException.class, () -> Bloqueo.dePoligonal(
                PERIODO.atDay(1).atTime(6, 0),
                PERIODO.atDay(1).atTime(7, 0),
                List.of(new Ubicacion(10, 10), new Ubicacion(12, 12))
        ));
    }
}
