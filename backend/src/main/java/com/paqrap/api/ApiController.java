package com.paqrap.api;

import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.api.ApiModels.EjecucionVista;
import com.paqrap.api.ApiModels.Escenario;
import com.paqrap.api.ApiModels.CorridaActivaVista;
import com.paqrap.api.ApiModels.FotogramasVista;
import com.paqrap.api.ApiModels.PlanesVista;
import com.paqrap.api.ApiModels.PeriodoVista;
import com.paqrap.api.ApiModels.MapaDatosVista;
import com.paqrap.api.ApiModels.RegistrarAveriaRequest;
import com.paqrap.api.ApiModels.AveriaVista;
import com.paqrap.api.ApiModels.InventarioVista;
import com.paqrap.api.ApiModels.EventosOperacionVista;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class ApiController {
    private final DatosFuente datos;
    private final EjecucionesService ejecuciones;
    private final FlotaService flota;
    private final CargasDePedidos cargas;

    ApiController(DatosFuente datos, EjecucionesService ejecuciones, FlotaService flota,
                  CargasDePedidos cargas) {
        this.datos = datos;
        this.ejecuciones = ejecuciones;
        this.flota = flota;
        this.cargas = cargas;
    }

    /** Carga masiva del archivo mensual de ventas: reemplaza los pedidos de ese mes. */
    @GetMapping("/datos/ventas")
    List<ApiModels.CargaVentasVista> cargas() {
        return cargas.todas().stream().map(ApiController::vista).toList();
    }

    @PostMapping(value = "/datos/ventas", consumes = "text/plain")
    ResponseEntity<ApiModels.CargaVentasVista> cargarVentas(@RequestParam String periodo,
            @RequestParam(required = false) String nombre, @RequestBody String contenido) {
        var mes = DatosService.parsearPeriodo(periodo);
        boolean habiaDatos;
        try { habiaDatos = datos.periodos().contains(periodo); }
        catch (ApiException e) { habiaDatos = false; }
        var carga = cargas.guardar(mes, nombre, contenido);
        var vista = vista(carga);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiModels.CargaVentasVista(
                vista.periodo(), vista.nombre(), vista.leidas(), vista.registros(),
                vista.descartados(), vista.errores(), vista.primerPedido(), vista.ultimoPedido(),
                vista.cargadoEn(), vista.persistente(), habiaDatos));
    }

    @DeleteMapping("/datos/ventas/{aaaamm}")
    ResponseEntity<Void> eliminarVentas(@PathVariable String aaaamm) {
        if (!cargas.eliminar(DatosService.parsearPeriodo(aaaamm))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CARGA_NO_ENCONTRADA",
                    "No hay una carga de ventas para ese periodo.");
        }
        return ResponseEntity.noContent().build();
    }

    private static ApiModels.CargaVentasVista vista(CargasDePedidos.Carga carga) {
        var registros = carga.pedidos().stream().map(p -> p.getFechaRegistro()).sorted().toList();
        return new ApiModels.CargaVentasVista(DatosConCargas.aaaamm(carga.periodo()), carga.nombre(),
                carga.leidas(), carga.pedidos().size(), carga.errores().size(),
                CargasDePedidos.primeros(carga.errores()).stream().map(e ->
                        new ApiModels.LineaRechazadaVista(e.numero(), e.linea(), e.motivo())).toList(),
                registros.isEmpty() ? null : registros.get(0),
                registros.isEmpty() ? null : registros.get(registros.size() - 1),
                carga.cargadoEn(), carga.persistente(), null);
    }

    /** Mantenimiento de unidades de transporte; aplica a las corridas que se inicien después. */
    @GetMapping("/flota")
    ApiModels.FlotaVista flota() { return flota.consultar(); }

    @PutMapping("/flota/tipos/{tipo}")
    ApiModels.FlotaVista actualizarTipo(@PathVariable String tipo,
            @RequestBody ApiModels.ActualizarTipoFlotaRequest cambios) {
        return flota.actualizarTipo(tipo, cambios);
    }

    @PostMapping("/flota/unidades")
    ResponseEntity<ApiModels.FlotaVista> agregarUnidad(
            @Valid @RequestBody ApiModels.NuevaUnidadRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flota.agregar(solicitud));
    }

    @PutMapping("/flota/unidades/{id}")
    ApiModels.FlotaVista actualizarUnidad(@PathVariable String id,
            @RequestBody ApiModels.ActualizarUnidadRequest cambios) {
        return flota.actualizarUnidad(id, cambios);
    }

    @DeleteMapping("/flota/unidades/{id}")
    ApiModels.FlotaVista eliminarUnidad(@PathVariable String id) { return flota.eliminar(id); }

    @PostMapping("/flota/restablecer")
    ApiModels.FlotaVista restablecerFlota() { return flota.restablecer(); }

    @GetMapping("/salud")
    Map<String, String> salud() { return Map.of("estado", "OK", "fuente", datos.tipo()); }

    @GetMapping("/datos/periodos")
    List<String> periodos() { return datos.periodos(); }

    @GetMapping("/datos/periodos/{aaaamm}")
    PeriodoVista periodo(@PathVariable String aaaamm) { return datos.periodo(aaaamm); }

    @GetMapping("/datos/mapa/{aaaamm}")
    MapaDatosVista mapa(@PathVariable String aaaamm) { return datos.mapa(aaaamm); }

    @PostMapping("/ejecuciones")
    ResponseEntity<EjecucionVista> crear(@Valid @RequestBody CrearEjecucionRequest solicitud) {
        EjecucionVista creada = ejecuciones.crear(solicitud);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/ejecuciones/" + creada.id())).body(creada);
    }

    @GetMapping("/ejecuciones/{id}")
    EjecucionVista consultar(@PathVariable UUID id) { return ejecuciones.consultar(id); }

    @GetMapping("/ejecuciones/activas")
    List<CorridaActivaVista> activas(@RequestParam(required = false) Escenario escenario) {
        return ejecuciones.activas(escenario);
    }

    @GetMapping("/ejecuciones/{id}/rendimiento")
    RendimientoVista rendimiento(@PathVariable UUID id) { return ejecuciones.rendimiento(id); }

    @GetMapping("/ejecuciones/{id}/fotogramas")
    FotogramasVista fotogramas(@PathVariable UUID id,
                              @RequestParam(defaultValue = "0") long desde) {
        return ejecuciones.fotogramas(id, desde);
    }

    @GetMapping("/ejecuciones/{id}/planes")
    PlanesVista planes(@PathVariable UUID id,
                      @RequestParam(defaultValue = "0") long desde) {
        return ejecuciones.planes(id, desde);
    }

    @PostMapping("/ejecuciones/{id}/cancelar")
    EjecucionVista cancelar(@PathVariable UUID id) { return ejecuciones.cancelar(id); }

    @PostMapping("/ejecuciones/{id}/pausar")
    EjecucionVista pausar(@PathVariable UUID id) { return ejecuciones.pausar(id); }

    @PostMapping("/ejecuciones/{id}/reanudar")
    EjecucionVista reanudar(@PathVariable UUID id) { return ejecuciones.reanudar(id); }

    @GetMapping("/ejecuciones/{id}/eventos")
    EventosOperacionVista eventos(@PathVariable UUID id,
                                  @RequestParam(defaultValue = "0") long desde) {
        return ejecuciones.eventosOperacion(id, desde);
    }

    @GetMapping("/ejecuciones/{id}/inventario")
    InventarioVista inventario(@PathVariable UUID id) { return ejecuciones.inventario(id); }

    @GetMapping("/ejecuciones/{id}/averias")
    List<AveriaVista> averias(@PathVariable UUID id) { return ejecuciones.averias(id); }

    @PostMapping("/ejecuciones/{id}/averias")
    ResponseEntity<AveriaVista> registrarAveria(@PathVariable UUID id,
            @Valid @RequestBody RegistrarAveriaRequest solicitud) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ejecuciones.registrarAveria(id, solicitud));
    }
}
