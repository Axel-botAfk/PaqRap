package com.paqrap.api;

import com.paqrap.api.ApiModels.Algoritmo;
import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.api.ApiModels.Escenario;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarcapasosTest {
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Test
    void cincoDiasConParametrosPorDefectoDuranEntre30Y60Minutos() throws Exception {
        RitmoEscenario ritmo = RitmoEscenario.para(solicitud(Escenario.PERIODO_5_DIAS, null), 5);
        long ms = recorrer(ritmo, Duration.ofDays(5), 40);
        assertEquals(120, ritmo.ventanas(Duration.ofDays(5)));
        assertEquals(Duration.ofHours(1), ritmo.saltoDelConsumo());
        assertTrue(ms >= Duration.ofMinutes(30).toMillis() && ms <= Duration.ofMinutes(60).toMillis(),
                "duró " + ms + " ms");
        assertEquals(Duration.ofMinutes(40).toMillis(), ms, 1_000);
    }

    @Test
    void laDuracionObjetivoSeRespetaEnLosExtremos() throws Exception {
        for (int objetivo : new int[] {30, 45, 60}) {
            RitmoEscenario ritmo = RitmoEscenario.para(solicitud(Escenario.PERIODO_5_DIAS, objetivo), 5);
            long ms = recorrer(ritmo, Duration.ofDays(5), 300);
            assertEquals(Duration.ofMinutes(objetivo).toMillis(), ms, ritmo.saMs(),
                    "objetivo " + objetivo + " min");
        }
        assertThrows(ApiException.class,
                () -> RitmoEscenario.para(solicitud(Escenario.PERIODO_5_DIAS, 29), 5));
        assertThrows(ApiException.class,
                () -> RitmoEscenario.para(solicitud(Escenario.PERIODO_5_DIAS, 61), 5));
    }

    @Test
    void elRitmoManualDeCincoDiasDebeCaerEnElRango() {
        // K = 300 min por ventana y Sa = 90 s: 24 ventanas, 36 minutos.
        RitmoEscenario ritmo = RitmoEscenario.para(new CrearEjecucionRequest(
                Escenario.PERIODO_5_DIAS, Algoritmo.GRASP, INICIO, null, null, null,
                null, null, 300, 90), 5);
        assertEquals(24, ritmo.ventanas(Duration.ofDays(5)));
        assertEquals(Duration.ofMinutes(36), ritmo.duracionEstimada(Duration.ofDays(5)));
        assertThrows(ApiException.class, () -> RitmoEscenario.para(new CrearEjecucionRequest(
                Escenario.PERIODO_5_DIAS, Algoritmo.GRASP, INICIO, null, null, null,
                null, null, 300, 10), 5));
    }

    @Test
    void elDiaADiaVaALaVelocidadElegida() {
        RitmoEscenario tiempoReal = RitmoEscenario.para(new CrearEjecucionRequest(
                Escenario.OPERACION_DIARIA, Algoritmo.GRASP, INICIO, null, null, null,
                null, 1, null, null), 1);
        assertEquals(Duration.ofSeconds(20), tiempoReal.saltoDelConsumo());
        assertEquals(Duration.ofDays(1), tiempoReal.duracionEstimada(Duration.ofDays(1)));
        RitmoEscenario rapido = RitmoEscenario.para(solicitud(Escenario.OPERACION_DIARIA, null), 1);
        assertEquals(Duration.ofMinutes(20), rapido.saltoDelConsumo());
        assertEquals(Duration.ofMinutes(24), rapido.duracionEstimada(Duration.ofDays(1)));
        assertThrows(ApiException.class, () -> RitmoEscenario.para(new CrearEjecucionRequest(
                Escenario.OPERACION_DIARIA, Algoritmo.GRASP, INICIO, null, null, null,
                null, 7, null, null), 1));
    }

    @Test
    void elColapsoTieneRitmoPropio() {
        RitmoEscenario ritmo = RitmoEscenario.para(solicitud(Escenario.COLAPSO, null), 30);
        assertEquals(Duration.ofHours(2), ritmo.saltoDelConsumo());
        assertEquals(10_000, ritmo.saMs());
        assertEquals(Duration.ofHours(1), ritmo.duracionEstimada(Duration.ofDays(30)));
    }

    @Test
    void laPausaNoSeDescuentaDelSalto() throws Exception {
        RelojSimulado reloj = new RelojSimulado();
        Marcapasos marcapasos = new Marcapasos(reloj, 20_000);
        long inicio = reloj.ahoraMs();
        marcapasos.publicar();
        reloj.avanzar(5_000);
        marcapasos.pausar();
        assertEquals(5_000L, marcapasos.transcurridoAlPausarMs());
        long finPausa = reloj.ahoraMs() + 60_000;
        reloj.alDormir = () -> { if (reloj.ahoraMs() >= finPausa) marcapasos.reanudar(); };
        marcapasos.esperarTurno();
        assertNull(marcapasos.transcurridoAlPausarMs());
        // 20 s del salto más el minuto en pausa, en pasos de espera de 200 ms.
        assertEquals(80_000, reloj.ahoraMs() - inicio, 200);
        assertEquals(inicio + 60_000, marcapasos.publicadoMs(), 200);
    }

    @Test
    void unCalculoLentoNoHaceSaltarElRelojHaciaAdelante() throws Exception {
        RelojSimulado reloj = new RelojSimulado();
        Marcapasos marcapasos = new Marcapasos(reloj, 10_000);
        long anterior = marcapasos.publicar();
        marcapasos.esperarTurno();
        reloj.avanzar(25_000); // el cálculo rebasa el salto
        long tarde = marcapasos.publicar();
        assertEquals(35_000, tarde - anterior);
        assertEquals(1, marcapasos.rebasadas());
        marcapasos.esperarTurno();
        long siguiente = marcapasos.publicar();
        // No intenta recuperar: la ventana tardía se ve al menos medio salto y a lo sumo uno.
        assertTrue(siguiente - tarde >= 5_000 && siguiente - tarde <= 10_000,
                "salió " + (siguiente - tarde) + " ms después");
    }

    @Test
    void unaVentanaQueCruzaVariosLimitesSeVeNVecesSa() throws Exception {
        RelojSimulado reloj = new RelojSimulado();
        Marcapasos marcapasos = new Marcapasos(reloj, 20_000);
        long inicio = marcapasos.publicar(90); // media hora simulada a tiempo real
        assertEquals(1_800_000, marcapasos.duracionMs());
        marcapasos.esperarCierre();
        assertEquals(1_800_000, reloj.ahoraMs() - inicio);
    }

    /** Simula una corrida completa: cálculo de Ta, publicación y espera, ventana por ventana. */
    private static long recorrer(RitmoEscenario ritmo, Duration horizonte, long taMs) throws Exception {
        RelojSimulado reloj = new RelojSimulado();
        Marcapasos marcapasos = new Marcapasos(reloj, ritmo.saMs());
        long inicio = reloj.ahoraMs();
        long ventanas = ritmo.ventanas(horizonte);
        for (long i = 0; i < ventanas; i++) {
            reloj.avanzar(taMs);
            marcapasos.publicar();
            if (i < ventanas - 1) marcapasos.esperarTurno();
        }
        marcapasos.esperarCierre();
        assertEquals(0, marcapasos.rebasadas());
        return reloj.ahoraMs() - inicio;
    }

    private static CrearEjecucionRequest solicitud(Escenario escenario, Integer duracion) {
        return new CrearEjecucionRequest(escenario, Algoritmo.GRASP, INICIO, null, null, null,
                duracion, null, null, null);
    }
}
