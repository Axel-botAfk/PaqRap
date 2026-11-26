package com.paqrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PedidosEnVivoTest {
    @TempDir Path carpeta;

    @Test void pedidoEnMarchaSePlanificaUnaVezEnLosTresEscenariosYAmbosAlgoritmos() throws Exception {
        Files.writeString(carpeta.resolve("ventas.203107.txt"), "01d00h00m:27,15,cBase,1,36\n");
        Files.writeString(carpeta.resolve("bloqueo.3107.txt"), "");
        for (var escenario : ApiModels.Escenario.values()) {
            for (var algoritmo : ApiModels.Algoritmo.values()) {
                var reloj = new RelojConBarrera();
                var servicio = new EjecucionesService(new DatosService(carpeta.toString()),
                        new EventosWebSocket(new ObjectMapper()), reloj);
                try {
                    UUID id = servicio.crear(new ApiModels.CrearEjecucionRequest(escenario, algoritmo,
                            LocalDateTime.of(2031, 7, 1, 0, 0), escenario == ApiModels.Escenario.COLAPSO ? 1 : null, 7L)).id();
                    assertTrue(reloj.publicado.await(20, TimeUnit.SECONDS));
                    var fecha = servicio.consultar(id).ritmo().relojHasta().withSecond(0).withNano(0).plusMinutes(1);
                    var manuales = new PedidosManuales("");
                    var solicitud = PedidosManualesTest.solicitud(UUID.randomUUID(), fecha, id);
                    var alta = servicio.registrarPedido(id, solicitud, manuales);
                    assertEquals(alta.id(), servicio.registrarPedido(id, solicitud, manuales).id());
                    assertEquals(1, manuales.todos().size());
                    var pasado = PedidosManualesTest.solicitud(UUID.randomUUID(), fecha.minusDays(1), id);
                    assertEquals("FECHA_PEDIDO_FUERA_DE_CORRIDA", assertThrows(ApiException.class,
                            () -> servicio.registrarPedido(id, pasado, manuales)).codigo());
                    var fuera = PedidosManualesTest.solicitud(UUID.randomUUID(), fecha.plusDays(10), id);
                    assertThrows(ApiException.class, () -> servicio.registrarPedido(id, fuera, manuales));
                    assertEquals(1, manuales.todos().size());
                    reloj.continuar.countDown();
                    ApiModels.EjecucionVista fin = null;
                    for (int i = 0; i < 1500; i++) {
                        fin = servicio.consultar(id);
                        if (fin.estado() == ApiModels.Estado.TERMINADA || fin.estado() == ApiModels.Estado.FALLIDA
                                || fin.estado() == ApiModels.Estado.COLAPSADA) break;
                        Thread.sleep(10);
                    }
                    assertNotNull(fin);
                    assertEquals(ApiModels.Estado.TERMINADA, fin.estado(), escenario + " " + algoritmo + ": " + fin.mensajeError());
                    assertEquals(2, fin.resumen().pedidosRecibidos());
                    assertTrue(servicio.planes(id, 0).planes().stream().flatMap(p -> p.rutas().stream())
                            .flatMap(r -> r.paradas().stream()).anyMatch(p -> p.id().equals(alta.id())));
                    assertEquals(1, servicio.eventosOperacion(id, 0).eventos().stream()
                            .filter(e -> e.tipo().equals("PEDIDO_REGISTRADO")).count());
                    assertEquals(alta.id(), servicio.registrarPedido(id, solicitud, manuales).id());
                    assertThrows(ApiException.class, () -> servicio.registrarPedido(id,
                            PedidosManualesTest.solicitud(UUID.randomUUID(), fecha, id), manuales));
                } finally {
                    reloj.continuar.countDown();
                    servicio.detener();
                }
            }
        }
    }

    private static class RelojConBarrera implements Marcapasos.Reloj {
        final CountDownLatch publicado = new CountDownLatch(1);
        final CountDownLatch continuar = new CountDownLatch(1);
        long ahora = 1_700_000_000_000L;
        @Override public synchronized long ahoraMs() { return ahora; }
        @Override public void dormir(long ms) throws InterruptedException {
            publicado.countDown();
            continuar.await();
            synchronized (this) { ahora += ms; }
        }
    }
}
