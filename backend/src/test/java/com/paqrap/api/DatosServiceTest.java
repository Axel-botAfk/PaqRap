package com.paqrap.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatosServiceTest {
    @TempDir Path carpeta;

    @Test
    void exponeAlmacenesBloqueosYPrimeraFechaReal() throws Exception {
        Files.writeString(carpeta.resolve("ventas.202609.txt"),
                "15d08h00m:26,15,c0497,06,36\n");
        Files.writeString(carpeta.resolve("bloqueo.2609.txt"),
                "15d09h00m-15d12h00m:35,25,35,29\n");
        DatosService datos = new DatosService(carpeta.toString());

        assertEquals(LocalDate.of(2026, 9, 15), datos.periodo("202609").primeraFechaPedido());
        assertEquals(3, datos.mapa("202609").almacenes().size());
        assertEquals(1, datos.mapa("202609").bloqueos().size());
        assertEquals(5, datos.mapa("202609").bloqueos().get(0).nodos().size());
    }
}
