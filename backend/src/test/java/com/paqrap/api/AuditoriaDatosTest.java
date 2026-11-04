package com.paqrap.api;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.time.*;
import com.paqrap.datos.DatosReales;
import static org.junit.jupiter.api.Assertions.*;

class AuditoriaDatosTest {
    @Test void mismosConteosNoOcultanContenidoDistintoNiDuplicados() {
        assertFalse(AuditoriaDatosController.comparar(List.of("A","B"),List.of("A","A")).mismoContenido());
        assertTrue(AuditoriaDatosController.comparar(List.of("A","B"),List.of("B","A")).mismoContenido());
    }
    @Test void elFinExclusivoNoExigeUnArchivoDelMesSiguiente() {
        assertEquals(List.of(YearMonth.of(2026,9)), DatosReales.mesesQueCubren(
                LocalDateTime.of(2026,9,1,0,0),30));
    }
}
