package com.paqrap.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargasDePedidosTest {
    private static final YearMonth FEB = YearMonth.of(2026, 2);

    @Test void dosMesesCargadosNoRepitenElIdDelPedido(@TempDir Path carpeta) {
        var cargas = new CargasDePedidos("");
        cargas.guardar(YearMonth.of(2031, 7), "ventas.203107.txt", "31d08h00m:1,1,c1,1,36");
        cargas.guardar(YearMonth.of(2031, 8), "ventas.203108.txt", "01d08h00m:1,1,c2,1,36");
        var datos = new DatosConCargas(new DatosService(carpeta.toString()), cargas);
        var ventas = datos.cargar(LocalDateTime.of(2031, 7, 31, 0, 0), 5).ventas();
        assertEquals(2, ventas.size());
        assertEquals(2, ventas.stream().map(com.paqrap.modelo.Pedido::getId).distinct().count());
        assertEquals("203107-P-00001", ventas.get(0).getId());
        assertEquals("203108-P-00001", ventas.get(1).getId());
    }

    @Test void limitaCamposYNormalizaSinAceptarPedidosInvalidos() {
        var cargas = new CargasDePedidos("");
        var carga = cargas.guardar(FEB, "ventas.202602.txt", "\uFEFF01d08h00m:1,1,c1,100000,744\r"
                + "01d08h00m:1,1,c1,100001,36\r01d08h00m:1,1,c1,1,745\r"
                + "01d08h00m:1,1,   ,1,36\r01d08h00m:1,1,c\t1,1,36\r");
        assertEquals(1, carga.pedidos().size());
        assertEquals(4, carga.errores().size());
        assertThrows(ApiException.class, () -> cargas.guardar(FEB, "ventas.202603.txt", ARCHIVO));
        assertThrows(ApiException.class, () -> cargas.guardar(FEB, "x", "a".repeat(CargasDePedidos.MAX_CARACTERES + 1)));
        assertThrows(ApiException.class, () -> cargas.guardar(FEB, "x", "\n".repeat(CargasDePedidos.MAX_LINEAS)));
        assertEquals(1, cargas.de(FEB).orElseThrow().pedidos().size());
    }

    @Test void falloAlGuardarOEliminarNoCambiaElEstado(@TempDir Path carpeta) throws Exception {
        var cargas = new CargasDePedidos(carpeta.toString());
        cargas.guardar(FEB, "ventas.202602.txt", ARCHIVO);
        Path destino = carpeta.resolve("ventas.202602.txt");
        Files.delete(destino);
        Files.createDirectory(destino);
        Files.writeString(destino.resolve("ocupado"), "x");
        assertThrows(ApiException.class, () -> cargas.guardar(FEB, "x", "01d08h00m:1,1,c1,1,36"));
        assertEquals(2, cargas.de(FEB).orElseThrow().pedidos().size());
        assertThrows(ApiException.class, () -> cargas.eliminar(FEB));
        assertEquals(2, cargas.de(FEB).orElseThrow().pedidos().size());
    }
    private static final String ARCHIVO = String.join("\r\n",
            "# comentario",
            "01d08h05m:10,20,c0001,5,36",
            "30d10h00m:10,20,c0002,5,36",      // febrero no tiene día 30
            "02d24h00m:10,20,c0003,5,36",      // hora inválida
            "02d09h00m:71,20,c0004,5,36",      // fuera de la retícula
            "02d09h30m:10,20,c0005,0,36",      // cantidad cero
            "esto no es un pedido",
            "",
            "28d23h59m:70,50,c0006,12,4");

    @Test
    void validaTodasLasLineasYConservaElCorrelativo() {
        var v = CargasDePedidos.validar(ARCHIVO.replace("\r\n", "\n"), FEB);
        assertEquals(7, v.leidas());
        assertEquals(2, v.pedidos().size());
        assertEquals(5, v.errores().size());
        assertEquals("P-00001", v.pedidos().get(0).getId());
        // La última es la séptima línea leída: el correlativo cuenta también las rechazadas.
        assertEquals("P-00007", v.pedidos().get(1).getId());
        assertEquals(LocalDateTime.of(2026, 2, 28, 23, 59), v.pedidos().get(1).getFechaRegistro());
        assertEquals(3, v.errores().get(0).numero());
        assertTrue(v.errores().get(0).motivo().contains("día 30"));
    }

    @Test
    void guardaEnDiscoYSeRecuperaAlArrancar(@TempDir Path carpeta) throws Exception {
        var cargas = new CargasDePedidos(carpeta.toString());
        var carga = cargas.guardar(FEB, "../../etc/ventas.txt", ARCHIVO);
        assertTrue(carga.persistente());
        assertEquals(2, carga.pedidos().size());
        assertEquals("....etcventas.txt", carga.nombre());
        assertTrue(Files.isRegularFile(carpeta.resolve("ventas.202602.txt")));

        var reiniciada = new CargasDePedidos(carpeta.toString());
        assertEquals(2, reiniciada.de(FEB).orElseThrow().pedidos().size());
        assertTrue(reiniciada.eliminar(FEB));
        assertFalse(Files.exists(carpeta.resolve("ventas.202602.txt")));
        assertTrue(new CargasDePedidos(carpeta.toString()).todas().isEmpty());
    }

    @Test
    void sinCarpetaQuedaEnMemoriaYRechazaArchivosSinPedidos() {
        var cargas = new CargasDePedidos("");
        assertFalse(cargas.guardar(FEB, "x", ARCHIVO).persistente());
        assertThrows(ApiException.class, () -> cargas.guardar(FEB, "x", "basura\notra"));
        assertThrows(ApiException.class, () -> cargas.guardar(FEB, "x", "  "));
    }

    @Test
    void unMesCargadoReemplazaLasVentasPeroConservaLosBloqueosDeLaBase() {
        Path reales = Path.of("..", "codigo", "paqrap", "datos", "reales");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(reales));
        var base = new DatosService(reales.toString());
        var cargas = new CargasDePedidos("");
        var datos = new DatosConCargas(base, cargas);
        YearMonth sep = YearMonth.of(2026, 9);
        int bloqueosBase = base.periodo("202609").bloqueos();
        cargas.guardar(sep, "ventas.202609.txt", "01d08h00m:10,20,c1,5,36\n01d09h00m:11,21,c2,3,8\n");

        var periodo = datos.periodo("202609");
        assertEquals(2, periodo.pedidos());
        assertEquals(bloqueosBase, periodo.bloqueos());
        var entrada = datos.cargar(LocalDateTime.of(2026, 9, 1, 0, 0), 1);
        assertEquals(2, entrada.ventas().size());
        assertFalse(entrada.bloqueos().isEmpty());

        // Un mes que la base no tiene aparece en el catálogo, sin bloqueos.
        cargas.guardar(YearMonth.of(2031, 5), "ventas.203105.txt", "03d10h00m:5,5,c9,4,36\n");
        assertTrue(datos.periodos().contains("203105"));
        assertEquals(0, datos.periodo("203105").bloqueos());
        assertEquals(1, datos.cargar(LocalDateTime.of(2031, 5, 3, 0, 0), 1).ventas().size());
        assertThrows(ApiException.class, () -> datos.cargar(LocalDateTime.of(2031, 5, 10, 0, 0), 1));
    }
}
