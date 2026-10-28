package com.paqrap.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void saludNoExponeConfiguracion() throws Exception {
        mvc.perform(get("/api/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OK"))
                .andExpect(jsonPath("$.fuente").value("ARCHIVOS"));
    }

    @Test
    void rechazaPeriodoConTraversal() throws Exception {
        mvc.perform(get("/api/datos/periodos/no-es-periodo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    @Test
    void rechazaPeriodoInvalidoEnMapa() throws Exception {
        mvc.perform(get("/api/datos/mapa/2029xx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    /**
     * El día a día en tiempo real se manda sin inicio y lo fija el servidor.
     *
     * Va por el controlador y no por `EjecucionesService` porque el fallo que cubre estaba en la
     * validación del contrato: con `@NotNull` en `inicio`, la solicitud se rechazaba con 400
     * antes de llegar al servicio, de modo que la pantalla no podía abrir la operación. Las
     * pruebas que llaman al servicio directamente no lo veían.
     */
    @Test
    void elDiaADiaSinInicioLoFijaElServidor() throws Exception {
        String cuerpo = mvc.perform(post("/api/ejecuciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"escenario\":\"OPERACION_DIARIA\",\"algoritmo\":\"TABU\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.inicio").isNotEmpty())
                .andExpect(jsonPath("$.ritmo.velocidad").value(1))
                .andReturn().getResponse().getContentAsString();

        // La corrida en tiempo real dura 24 h: se cancela para no dejarla corriendo tras la prueba.
        mvc.perform(post("/api/ejecuciones/"
                + com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id") + "/cancelar"));
    }

    @Test
    void rechazaAlgoritmoNoPermitido() throws Exception {
        mvc.perform(post("/api/ejecuciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"escenario\":\"OPERACION_DIARIA\",\"algoritmo\":\"SQL\",\"inicio\":\"2026-09-01T00:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void rechazaCamposObligatoriosAusentes() throws Exception {
        mvc.perform(post("/api/ejecuciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void rechazaPausaFueraDeClaseValida() throws Exception {
        mvc.perform(post("/api/ejecuciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"escenario\":\"OPERACION_DIARIA\",\"algoritmo\":\"GRASP\",\"inicio\":\"2026-09-01T00:00:00\",\"pausaMs\":501}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAUSA_INVALIDA"));
    }

    @Test
    void rechazaDuracionDeCincoDiasFueraDeRango() throws Exception {
        mvc.perform(post("/api/ejecuciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"escenario\":\"PERIODO_5_DIAS\",\"algoritmo\":\"GRASP\",\"inicio\":\"2026-09-01T00:00:00\",\"duracionObjetivoMin\":90}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RITMO_INVALIDO"));
    }

    @Test
    void controlesDeUnaEjecucionInexistenteDevuelven404() throws Exception {
        String base = "/api/ejecuciones/00000000-0000-0000-0000-000000000001";
        mvc.perform(post(base + "/pausar")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("EJECUCION_NO_ENCONTRADA"));
        mvc.perform(post(base + "/reanudar")).andExpect(status().isNotFound());
        mvc.perform(get(base + "/eventos")).andExpect(status().isNotFound());
    }

    @Test
    void rechazaAveriaSinTipoYConsultaInexistente() throws Exception {
        mvc.perform(post("/api/ejecuciones/00000000-0000-0000-0000-000000000001/averias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unidadId\":\"V1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/ejecuciones/00000000-0000-0000-0000-000000000001/inventario"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ejecuciones/00000000-0000-0000-0000-000000000001/planes"))
                .andExpect(status().isNotFound());
    }
}
