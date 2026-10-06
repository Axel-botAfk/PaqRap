package com.paqrap.api;

import com.paqrap.api.ApiModels.CrearEjecucionRequest;
import com.paqrap.api.ApiModels.EjecucionVista;
import com.paqrap.api.ApiModels.PeriodoVista;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class ApiController {
    private final DatosService datos;
    private final EjecucionesService ejecuciones;

    ApiController(DatosService datos, EjecucionesService ejecuciones) {
        this.datos = datos;
        this.ejecuciones = ejecuciones;
    }

    @GetMapping("/salud")
    Map<String, String> salud() { return Map.of("estado", "OK"); }

    @GetMapping("/datos/periodos")
    List<String> periodos() { return datos.periodos(); }

    @GetMapping("/datos/periodos/{aaaamm}")
    PeriodoVista periodo(@PathVariable String aaaamm) { return datos.periodo(aaaamm); }

    @PostMapping("/ejecuciones")
    ResponseEntity<EjecucionVista> crear(@Valid @RequestBody CrearEjecucionRequest solicitud) {
        EjecucionVista creada = ejecuciones.crear(solicitud);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/ejecuciones/" + creada.id())).body(creada);
    }

    @GetMapping("/ejecuciones/{id}")
    EjecucionVista consultar(@PathVariable UUID id) { return ejecuciones.consultar(id); }

    @PostMapping("/ejecuciones/{id}/cancelar")
    EjecucionVista cancelar(@PathVariable UUID id) { return ejecuciones.cancelar(id); }
}
