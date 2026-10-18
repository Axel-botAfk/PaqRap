package com.paqrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paqrap.api.ApiModels.Algoritmo;
import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.api.ApiModels.Escenario;
import com.paqrap.api.ApiModels.Estado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EjecucionesServiceTest {
    @TempDir Path datosTemporales;
    EjecucionesService servicio;

    @AfterEach
    void cerrar() {
        if (servicio != null) servicio.detener();
    }

    @Test
    void cincoDiasTerminaConArchivosDelCurso() throws Exception {
        prepararDatosMinimos();
        servicio = crearServicio();
        UUID id = servicio.crear(new CrearEjecucionRequest(Escenario.PERIODO_5_DIAS,
                Algoritmo.GRASP, LocalDateTime.of(2026, 9, 1, 0, 0), null, 7L)).id();
        var fin = esperar(id);
        assertEquals(Estado.TERMINADA, fin.estado());
        assertEquals(LocalDateTime.of(2026, 9, 6, 0, 0), fin.reloj());
        assertNotNull(fin.resumen());
        assertEquals(1, fin.resumen().pedidosRecibidos());
        var historial = new ArrayList<ApiModels.FotogramaVista>();
        long desde = 0;
        ApiModels.FotogramasVista pagina;
        do {
            pagina = servicio.fotogramas(id, desde);
            historial.addAll(pagina.fotogramas());
            desde = pagina.siguiente();
        } while (pagina.fotogramas().size() == 100);
        assertThrows(ApiException.class, () -> servicio.fotogramas(id, -1));
        assertTrue(pagina.completo());
        assertTrue(historial.size() >= 2);
        assertEquals(0, historial.get(0).indice());
        assertEquals(fin.reloj(), historial.get(historial.size() - 1).reloj());
        assertEquals(fin.vehiculos(), historial.get(historial.size() - 1).vehiculos());
        assertTrue(historial.stream().anyMatch(f -> f.vehiculos().stream()
                .anyMatch(v -> !v.ubicacion().equals(
                        historial.get(0).vehiculos().stream()
                                .filter(inicial -> inicial.id().equals(v.id()))
                                .findFirst().orElseThrow().ubicacion()))));
    }

    @Test
    void escenarioColapsoUsaElMismoMotorSinInventarUnColapso() throws Exception {
        prepararDatosMinimos();
        servicio = crearServicio();
        UUID id = servicio.crear(new CrearEjecucionRequest(Escenario.COLAPSO,
                Algoritmo.TABU, LocalDateTime.of(2026, 9, 1, 0, 0), 1, 7L)).id();
        var fin = esperar(id);
        assertEquals(Estado.TERMINADA, fin.estado());
        assertNotNull(fin.resumen());
        assertEquals(false, fin.resumen().huboColapso());
    }

    @Test
    void operacionDiariaRegistraPosicionesEjecutadas() throws Exception {
        prepararDatosMinimos();
        servicio = crearServicio();
        UUID id = servicio.crear(new CrearEjecucionRequest(Escenario.OPERACION_DIARIA,
                Algoritmo.GRASP, LocalDateTime.of(2026, 9, 1, 0, 0), null, 7L)).id();
        var fin = esperar(id);
        assertEquals(Estado.TERMINADA, fin.estado());
        assertEquals(LocalDateTime.of(2026, 9, 2, 0, 0), fin.reloj());
        var pagina = servicio.fotogramas(id, 0);
        assertTrue(pagina.fotogramas().size() > 1);
        assertEquals(0, pagina.fotogramas().get(0).indice());
    }

    private void prepararDatosMinimos() throws Exception {
        Files.writeString(datosTemporales.resolve("ventas.202609.txt"),
                "01d00h26m:26,15,c0497,06,36\n");
        Files.writeString(datosTemporales.resolve("bloqueo.2609.txt"), "");
    }

    private EjecucionesService crearServicio() {
        return new EjecucionesService(new DatosService(datosTemporales.toString()),
                new EventosWebSocket(new ObjectMapper()));
    }

    private ApiModels.EjecucionVista esperar(UUID id) throws Exception {
        for (int intento = 0; intento < 1000; intento++) {
            var vista = servicio.consultar(id);
            if (vista.estado() == Estado.TERMINADA || vista.estado() == Estado.COLAPSADA
                    || vista.estado() == Estado.FALLIDA || vista.estado() == Estado.CANCELADA) {
                return vista;
            }
            Thread.sleep(20);
        }
        fail("La simulacion minima no termino en 20 segundos");
        return null;
    }
}
