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

@SpringBootTest(properties = {"paqrap.upload-dir=", "paqrap.data-dir=../codigo/paqrap/datos/reales"})
@AutoConfigureMockMvc
class CargaVentasApiTest {
    @Autowired MockMvc mvc;

    @Test void registroIndividualEsConsultableYNoReemplazaLaCarga() throws Exception {
        String clave = java.util.UUID.randomUUID().toString();
        String json = "{\"solicitudId\":\"" + clave + "\",\"cliente\":\"cAPI\",\"destino\":{\"x\":70,\"y\":50},"
                + "\"cantidad\":2,\"horasPlazo\":36,\"fechaRegistro\":\"2031-08-01T08:00\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/datos/pedidos").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("MAN-" + clave))
                    .andExpect(jsonPath("$.persistente").value(false))
                    .andExpect(jsonPath("$.fechaRegistro").value("2031-08-01T08:00:00"));
        }
        mvc.perform(get("/api/datos/pedidos").param("periodo", "203108"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/datos/periodos/203108"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pedidos").value(1));
        mvc.perform(post("/api/datos/ventas").param("periodo", "203108")
                        .contentType(MediaType.TEXT_PLAIN).content("01d08h00m:1,1,cCarga,1,36"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/datos/periodos/203108")).andExpect(jsonPath("$.pedidos").value(2));
        mvc.perform(delete("/api/datos/ventas/203108")).andExpect(status().isNoContent());
        mvc.perform(get("/api/datos/periodos/203108")).andExpect(jsonPath("$.pedidos").value(1));
    }

    @Test void erroresDeAltaYDeCargaNoDevuelvenExito() throws Exception {
        mvc.perform(post("/api/datos/pedidos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/datos/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitudId\":\"" + java.util.UUID.randomUUID() + "\",\"cliente\":\"cAPI\","
                                + "\"destino\":{\"x\":71,\"y\":0},\"cantidad\":1,\"horasPlazo\":36,"
                                + "\"fechaRegistro\":\"2031-08-01T08:00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PEDIDO_INVALIDO"));
        mvc.perform(get("/api/datos/pedidos")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/datos/ventas").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/datos/ventas").param("periodo", "203107")
                        .param("nombre", "ventas.203108.txt").contentType(MediaType.TEXT_PLAIN)
                        .content("01d08h00m:1,1,c1,1,36"))
                .andExpect(status().isBadRequest());
    }

    @Test void noInventaCoordenadasNiTruncaDecimalesDelPedido() throws Exception {
        for (String destino : new String[]{"{}", "{\"x\":1}", "{\"x\":null,\"y\":2}", "{\"x\":1.5,\"y\":2}"}) {
            mvc.perform(post("/api/datos/pedidos").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"solicitudId\":\"" + java.util.UUID.randomUUID() + "\",\"cliente\":\"c1\","
                            + "\"destino\":" + destino + ",\"cantidad\":1,\"horasPlazo\":36,"
                            + "\"fechaRegistro\":\"2031-08-01T08:00\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

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
