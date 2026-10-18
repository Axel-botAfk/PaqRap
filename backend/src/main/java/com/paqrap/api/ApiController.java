package com.paqrap.api;

import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.api.ApiModels.EjecucionVista;
import com.paqrap.api.ApiModels.FotogramasVista;
import com.paqrap.api.ApiModels.PlanesVista;
import com.paqrap.api.ApiModels.PeriodoVista;
import com.paqrap.api.ApiModels.MapaDatosVista;
import com.paqrap.api.ApiModels.RegistrarAveriaRequest;
import com.paqrap.api.ApiModels.AveriaVista;
import com.paqrap.api.ApiModels.InventarioVista;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    ApiController(DatosFuente datos, EjecucionesService ejecuciones) {
        this.datos = datos;
        this.ejecuciones = ejecuciones;
    }

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
