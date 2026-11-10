package com.paqrap.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "paqrap.upload-dir=")
@AutoConfigureMockMvc
class CargaVentasApiTest {
    @Autowired MockMvc mvc;

    @Test
    void cargaConsultaYQuitaUnMes() throws Exception {
        mvc.perform(post("/api/datos/ventas").param("periodo", "203107").param("nombre", "ventas.203107.txt")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("01d08h00m:10,20,c1,5,36\nmal\n02d09h00m:11,21,c2,3,8\n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registros").value(2))
                .andExpect(jsonPath("$.descartados").value(1))
                .andExpect(jsonPath("$.errores[0].numero").value(2))
                .andExpect(jsonPath("$.persistente").value(false))
                .andExpect(jsonPath("$.reemplazo").value(false));
        mvc.perform(get("/api/datos/periodos"))
                .andExpect(jsonPath("$[?(@ == '203107')]").exists());
        mvc.perform(get("/api/datos/periodos/203107"))
                .andExpect(jsonPath("$.pedidos").value(2));
        mvc.perform(get("/api/datos/ventas")).andExpect(jsonPath("$[0].periodo").value("203107"));
        mvc.perform(delete("/api/datos/ventas/203107")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/datos/ventas/203107")).andExpect(status().isNotFound());
        mvc.perform(get("/api/datos/periodos/203107")).andExpect(status().isNotFound());
    }

    @Test
    void rechazaPeriodoInvalidoYArchivoSinPedidos() throws Exception {
        mvc.perform(post("/api/datos/ventas").param("periodo", "../x")
                        .contentType(MediaType.TEXT_PLAIN).content("01d08h00m:10,20,c1,5,36"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
        mvc.perform(post("/api/datos/ventas").param("periodo", "203107")
                        .contentType(MediaType.TEXT_PLAIN).content("nada valido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_INVALIDO"));
    }
}
