package com.paqrap.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PedidosManualesTest {
    @TempDir Path carpeta;

    static ApiModels.NuevoPedidoRequest solicitud(UUID clave, LocalDateTime fecha, UUID corrida) {
        return new ApiModels.NuevoPedidoRequest(clave, "cPrueba", new ApiModels.NodoPedidoRequest(28, 14),
                2, 36, fecha, corrida);
    }

    @Test void guardaRecuperaYReintentaSinDuplicar() {
        var pedidos = new PedidosManuales(carpeta.toString());
        var r = solicitud(UUID.randomUUID(), LocalDateTime.of(2031, 7, 1, 8, 0), null);
        var primero = pedidos.guardar(r);
        assertEquals(primero.getId(), pedidos.guardar(r).getId());
        assertEquals(1, pedidos.todos().size());
        var reiniciado = new PedidosManuales(carpeta.toString());
        assertEquals(primero.getId(), reiniciado.todos().get(0).getId());
        assertEquals(1, reiniciado.todos().size());
        assertTrue(reiniciado.persistente());
        var cambiado = new ApiModels.NuevoPedidoRequest(r.solicitudId(), r.cliente(), r.destino(), 3,
                r.horasPlazo(), r.fechaRegistro(), null);
        assertEquals("SOLICITUD_REUTILIZADA", assertThrows(ApiException.class,
                () -> reiniciado.guardar(cambiado)).codigo());
    }

    @Test void rechazaDatosInvalidosSinGuardar() {
        var pedidos = new PedidosManuales("");
        var fecha = LocalDateTime.of(2031, 7, 1, 8, 0);
        for (var cliente : new String[]{" ", "a,b", "a\nb", "a".repeat(33)}) {
            assertThrows(ApiException.class, () -> pedidos.guardar(new ApiModels.NuevoPedidoRequest(
                    UUID.randomUUID(), cliente, new ApiModels.NodoPedidoRequest(1, 1), 2, 36, fecha, null)));
        }
        for (int cantidad : new int[]{0, -1, 100001}) {
            assertThrows(ApiException.class, () -> pedidos.guardar(new ApiModels.NuevoPedidoRequest(
                    UUID.randomUUID(), "c1", new ApiModels.NodoPedidoRequest(1, 1), cantidad, 36, fecha, null)));
        }
        assertThrows(ApiException.class, () -> pedidos.guardar(solicitud(UUID.randomUUID(), fecha.plusSeconds(1), null)));
        assertTrue(pedidos.todos().isEmpty());
        assertFalse(pedidos.persistente());
    }

    @Test void falloDeDiscoNoConfirmaNiDejaPedidosFantasma() throws Exception {
        Path noDirectorio = carpeta.resolve("archivo");
        Files.writeString(noDirectorio, "ocupado");
        var pedidos = new PedidosManuales(noDirectorio.toString());
        assertEquals("CARGA_NO_GUARDADA", assertThrows(ApiException.class, () -> pedidos.guardar(
                solicitud(UUID.randomUUID(), LocalDateTime.of(2031, 7, 1, 8, 0), null))).codigo());
        assertTrue(pedidos.todos().isEmpty());
    }

    @Test void altaNoReemplazaVentasYUnaCargaMasivaNoBorraAltas() throws Exception {
        Files.writeString(carpeta.resolve("ventas.203107.txt"), "01d08h00m:27,15,cBase,3,36\n");
        Files.writeString(carpeta.resolve("bloqueo.3107.txt"), "");
        var base = new DatosService(carpeta.toString());
        var cargas = new CargasDePedidos("");
        var manuales = new PedidosManuales("");
        var datos = new DatosConPedidosManuales(new DatosConCargas(base, cargas), manuales);
        var fecha = LocalDateTime.of(2031, 7, 1, 8, 0);
        var alta = manuales.guardar(solicitud(UUID.randomUUID(), fecha, null));
        assertEquals(2, datos.periodo("203107").pedidos());
        assertEquals(2, datos.cargar(fecha, 1).ventas().size());
        cargas.guardar(YearMonth.of(2031, 7), "ventas.203107.txt", "01d09h00m:27,15,cCarga,4,36\n");
        var ventas = datos.cargar(fecha, 1).ventas();
        assertEquals(2, ventas.size());
        assertTrue(ventas.stream().anyMatch(p -> p.getId().equals(alta.getId())));
        assertTrue(ventas.stream().anyMatch(p -> p.getClienteId().equals("cCarga")));
        cargas.eliminar(YearMonth.of(2031, 7));
        assertTrue(datos.cargar(fecha, 1).ventas().stream().anyMatch(p -> p.getClienteId().equals("cBase")));
        manuales.guardar(solicitud(UUID.randomUUID(), fecha.plusMonths(1), null));
        assertTrue(datos.periodos().contains("203108"));
        assertEquals(1, datos.periodo("203108").pedidos());
        assertEquals(1, datos.cargar(fecha.plusMonths(1), 1).ventas().size());
    }
}
