package com.paqrap.api;

import com.paqrap.simulacion.MedicionDePlanificacion;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RendimientoVistaTest {
    @Test void agregaCadaLlamadaYNoPromediaConsultas() {
        var reloj = LocalDateTime.of(2026, 9, 1, 0, 0);
        var serie = List.of(new MedicionDePlanificacion(reloj, 1, 2, 10),
                new MedicionDePlanificacion(reloj.plusMinutes(15), 2, 3, 30),
                new MedicionDePlanificacion(reloj.plusMinutes(30), 3, 4, 80));
        var vista = RendimientoVista.de(UUID.randomUUID(), 100, 200L, serie);
        assertEquals(3, vista.cantidad());
        assertEquals(120, vista.sumaTaMs());
        assertEquals(40, vista.promedioTaMs());
        assertEquals(10, vista.minimoTaMs());
        assertEquals(80, vista.maximoTaMs());
        assertEquals(80, vista.percentil95TaMs());
        assertThrows(UnsupportedOperationException.class, () -> vista.mediciones().clear());
    }
    @Test void sinLlamadasTieneContadorCero() {
        var vista = RendimientoVista.de(UUID.randomUUID(), 0, null, List.of());
        assertEquals(0, vista.cantidad());
        assertEquals(0, vista.promedioTaMs());
        assertNull(vista.finRealMs());
    }
}
