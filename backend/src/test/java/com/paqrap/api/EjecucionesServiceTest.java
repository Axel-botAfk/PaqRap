package com.paqrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paqrap.api.ApiModels.Algoritmo;
import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.api.ApiModels.Escenario;
import com.paqrap.api.ApiModels.Estado;
import com.paqrap.api.ApiModels.RegistrarAveriaRequest;
import com.paqrap.modelo.TipoAveria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EjecucionesServiceTest {
    @TempDir Path datosTemporales;
    EjecucionesService servicio;
    final RelojSimulado reloj = new RelojSimulado();

    @AfterEach
    void cerrar() {
        if (servicio != null) servicio.detener();
    }

    @Test
    void cincoDiasTerminaConArchivosDelCurso() throws Exception {
        prepararDatosMinimos();
        servicio = crearServicio();
        long arranque = reloj.ahoraMs();
        UUID id = servicio.crear(new CrearEjecucionRequest(Escenario.PERIODO_5_DIAS,
                Algoritmo.GRASP, LocalDateTime.of(2026, 9, 1, 0, 0), null, 7L)).id();
        var fin = esperar(id);
        assertEquals(Estado.TERMINADA, fin.estado());
        // Con el ritmo por defecto, 5 días toman entre 30 y 60 minutos de reloj de pared.
        long transcurrido = reloj.ahoraMs() - arranque;
        assertTrue(transcurrido >= 30 * 60_000L && transcurrido <= 60 * 60_000L,
                "duró " + transcurrido + " ms");
        assertEquals(120, fin.ritmo().ventanas());
        assertEquals(120, fin.ritmo().ventana());
        assertEquals(20_000, fin.ritmo().saMs());
        assertEquals(60.0, fin.ritmo().minutosPorVentana());
        assertEquals(false, fin.calculandoRutas());
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
        assertTrue(historial.stream().allMatch(f -> f.inventario() != null
                && f.reloj().equals(f.inventario().reloj())),
                "cada fotograma conserva el inventario observado en su propio corte");
        assertTrue(fin.ritmo().inicioRealMs() > 0);
        assertTrue(fin.ritmo().finRealMs() >= fin.ritmo().inicioRealMs());
        assertEquals(fin.reloj(), historial.get(historial.size() - 1).reloj());
        assertEquals(fin.vehiculos(), historial.get(historial.size() - 1).vehiculos());
        verificarPosicionesPublicadas(id, fin);
        var planes = servicio.planes(id, 0);
        assertTrue(planes.completo());
        assertTrue(planes.planes().size() > 0);
        assertTrue(planes.planes().stream().allMatch(p ->
                !p.reloj().isBefore(fin.inicio()) && !p.reloj().isAfter(fin.reloj())));
        for (int i = 1; i < planes.planes().size(); i++) {
            assertEquals(planes.planes().get(i - 1).indice() + 1,
                    planes.planes().get(i).indice());
            assertTrue(!planes.planes().get(i).reloj()
                    .isBefore(planes.planes().get(i - 1).reloj()));
        }
        assertThrows(ApiException.class, () -> servicio.planes(id, -1));
        verificarTrayectos(planes.planes(), historial);
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
        verificarPosicionesPublicadas(id, fin);
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
        verificarPosicionesPublicadas(id, fin);
    }

    @Test
    void averiaEnMarchaSeAplicaYElInventarioTieneRelojYStock() throws Exception {
        prepararDatosMinimos();
        servicio = crearServicio();
        UUID id = servicio.crear(new CrearEjecucionRequest(Escenario.OPERACION_DIARIA,
                Algoritmo.GRASP, LocalDateTime.of(2026, 9, 1, 0, 0), null, 7L, 20)).id();
        for (int intento = 0; intento < 100 && servicio.consultar(id).estado() == Estado.PENDIENTE;
                intento++) Thread.sleep(10);
        String unidad = new DatosService(datosTemporales.toString()).flota().get(0).getId();
        var averia = servicio.registrarAveria(id,
                new RegistrarAveriaRequest(unidad, TipoAveria.TIPO_3));
        assertEquals("REGISTRADA", averia.estado());
        var inventarioInicial = servicio.inventario(id);
        assertNotNull(inventarioInicial.reloj());
        assertEquals(3, inventarioInicial.almacenes().size());
        assertTrue(inventarioInicial.almacenes().stream().anyMatch(a -> a.ilimitado()
                && a.disponible() == null));
        var fin = esperar(id);
        assertEquals(Estado.TERMINADA, fin.estado());
        assertEquals("APLICADA", servicio.averias(id).get(0).estado());
        assertEquals(fin.reloj(), servicio.inventario(id).reloj());
        assertThrows(ApiException.class, () -> servicio.registrarAveria(id,
                new RegistrarAveriaRequest(unidad, TipoAveria.TIPO_1)));
    }

    @Test
    void elRitmoDePantallaNoCambiaLasDecisionesConDatosReales() throws Exception {
        Path reales = Path.of("..", "codigo", "paqrap", "datos", "reales");
        Assumptions.assumeTrue(Files.isDirectory(reales), "sin datos reales del curso");
        servicio = new EjecucionesService(new DatosService(reales.toString()),
                new EventosWebSocket(new ObjectMapper()), reloj);
        LocalDateTime inicio = LocalDateTime.of(2026, 9, 1, 0, 0);
        var rapida = esperar(servicio.crear(new CrearEjecucionRequest(Escenario.OPERACION_DIARIA,
                Algoritmo.GRASP, inicio, null, 7L, null, null, 240, null, null)).id());
        var lenta = esperar(servicio.crear(new CrearEjecucionRequest(Escenario.OPERACION_DIARIA,
                Algoritmo.GRASP, inicio, null, 7L, null, null, 10, null, null)).id());
        assertEquals(18, rapida.ritmo().ventanas());
        assertEquals(432, lenta.ritmo().ventanas());
        assertEquals(rapida.estado(), lenta.estado());
        assertEquals(rapida.resumen(), lenta.resumen());
        assertEquals(rapida.vehiculos(), lenta.vehiculos());
    }

    @Test
    void pausaReanudaYDetieneConEstadosClaros() throws Exception {
        prepararDatosMinimos();
        servicio = crearServicio();
        UUID id = servicio.crear(new CrearEjecucionRequest(Escenario.OPERACION_DIARIA,
                Algoritmo.GRASP, LocalDateTime.of(2026, 9, 1, 0, 0), null, 7L, 20)).id();
        esperarEstado(id, Estado.EN_CURSO);
        var pausada = servicio.pausar(id);
        assertEquals(Estado.PAUSADA, pausada.estado());
        assertNotNull(pausada.ritmo().transcurridoAlPausarMs());
        Thread.sleep(150);
        int ventana = servicio.consultar(id).ritmo().ventana();
        Thread.sleep(300);
        var sigue = servicio.consultar(id);
        assertEquals(Estado.PAUSADA, sigue.estado());
        assertEquals(ventana, sigue.ritmo().ventana(), "no avanza mientras está en pausa");
        assertEquals(Estado.EN_CURSO, servicio.reanudar(id).estado());
        assertThrows(ApiException.class, () -> servicio.reanudar(UUID.randomUUID()));
        assertEquals(Estado.CANCELADA, servicio.cancelar(id).estado());
        assertEquals(Estado.CANCELADA, esperar(id).estado());
        assertThrows(ApiException.class, () -> servicio.pausar(id));
    }

    @Test
    void averiaDeUnaUnidadConPedidosLosReplanifica() throws Exception {
        // Un destino lejano: la unidad sigue en camino cuando se registra la avería.
        Files.writeString(datosTemporales.resolve("ventas.202609.txt"),
                "01d00h26m:69,49,c0497,06,36\n");
        Files.writeString(datosTemporales.resolve("bloqueo.2609.txt"), "");
        servicio = crearServicio();
        AtomicReference<UUID> ejecucion = new AtomicReference<>();
        AtomicReference<String> averiada = new AtomicReference<>();
        // Se interviene desde la espera del marcapasos: la ventana ya se publicó y la siguiente
        // todavía no se calcula, igual que cuando alguien pulsa el botón en el visualizador.
        reloj.alDormir = () -> {
            UUID id = ejecucion.get();
            if (id == null || averiada.get() != null) return;
            servicio.planes(id, 0).planes().stream()
                    .flatMap(p -> p.rutas().stream()).findFirst().ifPresent(ruta -> {
                        averiada.set(ruta.vehiculoId());
                        servicio.registrarAveria(id,
                                new RegistrarAveriaRequest(ruta.vehiculoId(), TipoAveria.TIPO_3));
                    });
        };
        UUID id = servicio.crear(new CrearEjecucionRequest(Escenario.COLAPSO, Algoritmo.GRASP,
                LocalDateTime.of(2026, 9, 1, 0, 0), 1, 7L, null, null, null, 1, 1)).id();
        ejecucion.set(id);
        var fin = esperar(id);
        assertNotNull(averiada.get(), "el plan nunca asignó el pedido");
        var eventos = servicio.eventosOperacion(id, 0).eventos();
        assertTrue(eventos.stream().anyMatch(e -> e.tipo().equals("AVERIA_APLICADA")
                && e.vehiculoId().equals(averiada.get())), eventos.toString());
        var replanificacion = eventos.stream()
                .filter(e -> e.tipo().equals("REPLANIFICACION")).findFirst().orElseThrow();
        assertEquals(averiada.get(), replanificacion.vehiculoId());
        assertEquals("P-00001", replanificacion.pedidoId());
        assertTrue(replanificacion.vehiculoNuevoId() == null
                || !replanificacion.vehiculoNuevoId().equals(averiada.get()));
        var averia = servicio.averias(id).get(0);
        assertEquals("APLICADA", averia.estado());
        assertEquals(1, averia.pedidosAfectados());
        assertEquals(Estado.TERMINADA, fin.estado());
    }

    @Test
    void colapsoInformaPedidoInstanteYMetricas() throws Exception {
        // Plazo de una hora a 77 km del central: nadie llega a tiempo.
        Files.writeString(datosTemporales.resolve("ventas.202609.txt"),
                "01d00h26m:69,49,c0001,06,1\n");
        Files.writeString(datosTemporales.resolve("bloqueo.2609.txt"), "");
        servicio = crearServicio();
        UUID id = servicio.crear(new CrearEjecucionRequest(Escenario.COLAPSO, Algoritmo.TABU,
                LocalDateTime.of(2026, 9, 1, 0, 0), 2, 7L)).id();
        var fin = esperar(id);
        assertEquals(Estado.COLAPSADA, fin.estado());
        var colapso = fin.colapso();
        assertNotNull(colapso);
        assertEquals("P-00001", colapso.pedido().id());
        assertEquals(LocalDateTime.of(2026, 9, 1, 1, 26), colapso.pedido().fechaLimite());
        assertNotNull(colapso.instante());
        assertTrue(colapso.ventana() >= 1);
        assertEquals(1, colapso.pedidosVencidos());
        assertTrue(colapso.vehiculosDisponibles() > 0);
        assertTrue(fin.reloj().isBefore(LocalDateTime.of(2026, 9, 3, 0, 0)),
                "la corrida se corta en el colapso");
        assertTrue(servicio.eventosOperacion(id, 0).eventos().stream()
                .anyMatch(e -> e.tipo().equals("COLAPSO") && "P-00001".equals(e.pedidoId())));
        // El historial guarda los sin asignar de cada plan, no solo los del último estado.
        assertTrue(servicio.planes(id, 0).planes().stream().anyMatch(plan ->
                plan.noAsignados().stream().anyMatch(p -> p.id().equals("P-00001"))));
    }

    /** Cada recorrido avanza en el tiempo y termina donde el fotograma deja a la unidad. */
    private void verificarPosicionesPublicadas(UUID id, ApiModels.EjecucionVista fin) {
        ApiModels.FotogramaVista primero = null;
        ApiModels.FotogramaVista ultimo = null;
        var historial = new ArrayList<ApiModels.FotogramaVista>();
        long desde = 0;
        ApiModels.FotogramasVista pagina;
        do {
            pagina = servicio.fotogramas(id, desde);
            for (ApiModels.FotogramaVista foto : pagina.fotogramas()) {
                historial.add(foto);
                if (primero == null) primero = foto;
                ultimo = foto;
                assertTrue(foto.vehiculos().stream().allMatch(v ->
                        v.ubicacion().x() >= 0 && v.ubicacion().x() <= 70
                                && v.ubicacion().y() >= 0 && v.ubicacion().y() <= 50));
            }
            desde = pagina.siguiente();
        } while (pagina.fotogramas().size() == 100);
        assertNotNull(primero);
        assertNotNull(ultimo);
        assertEquals(fin.inicio(), primero.reloj());
        assertEquals(fin.reloj(), ultimo.reloj());
        assertEquals(fin.vehiculos(), ultimo.vehiculos());

        var planes = new ArrayList<ApiModels.PlanVista>();
        desde = 0;
        ApiModels.PlanesVista paginaPlanes;
        do {
            paginaPlanes = servicio.planes(id, desde);
            planes.addAll(paginaPlanes.planes());
            desde = paginaPlanes.siguiente();
        } while (paginaPlanes.planes().size() == 100);
        verificarTrayectos(planes, historial);
    }

    private static void verificarTrayectos(List<ApiModels.PlanVista> planes,
                                           List<ApiModels.FotogramaVista> historial) {
        boolean alguno = false;
        for (var plan : planes) {
            assertNotNull(plan.hasta());
            var fotograma = historial.stream().filter(f -> f.reloj().equals(plan.hasta()))
                    .findFirst().orElseThrow();
            for (var trayecto : plan.trayectos()) {
                alguno = true;
                for (int i = 1; i < trayecto.pasos().size(); i++) {
                    assertTrue(!trayecto.pasos().get(i).instante()
                            .isBefore(trayecto.pasos().get(i - 1).instante()));
                }
                assertTrue(trayecto.pasos().stream().allMatch(p -> p.cargaAbordo() >= 0));
                var unidad = fotograma.vehiculos().stream()
                        .filter(v -> v.id().equals(trayecto.vehiculoId())).findFirst().orElseThrow();
                assertEquals(unidad.ubicacion(),
                        trayecto.pasos().get(trayecto.pasos().size() - 1).ubicacion());
                assertEquals(unidad.cargaAbordo(),
                        trayecto.pasos().get(trayecto.pasos().size() - 1).cargaAbordo());
            }
        }
        assertTrue(alguno, "ninguna unidad registró recorrido");
    }

    private void esperarEstado(UUID id, Estado estado) throws Exception {
        for (int intento = 0; intento < 500 && servicio.consultar(id).estado() != estado; intento++) {
            Thread.sleep(10);
        }
        assertEquals(estado, servicio.consultar(id).estado());
    }

    private void prepararDatosMinimos() throws Exception {
        Files.writeString(datosTemporales.resolve("ventas.202609.txt"),
                "01d00h26m:26,15,c0497,06,36\n");
        Files.writeString(datosTemporales.resolve("bloqueo.2609.txt"), "");
    }

    private EjecucionesService crearServicio() {
        return new EjecucionesService(new DatosService(datosTemporales.toString()),
                new EventosWebSocket(new ObjectMapper()), reloj);
    }

    private ApiModels.EjecucionVista esperar(UUID id) throws Exception {
        for (int intento = 0; intento < 9000; intento++) {
            var vista = servicio.consultar(id);
            if (vista.estado() == Estado.TERMINADA || vista.estado() == Estado.COLAPSADA
                    || vista.estado() == Estado.FALLIDA || vista.estado() == Estado.CANCELADA) {
                return vista;
            }
            Thread.sleep(20);
        }
        fail("La simulacion no termino en 180 segundos");
        return null;
    }
}
