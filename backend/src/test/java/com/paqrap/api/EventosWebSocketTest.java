package com.paqrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paqrap.api.ApiModels.EjecucionVista;
import com.paqrap.api.ApiModels.EventoVista;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EventosWebSocketTest {
    @Test
    void dosVisualizadoresRecibenLaMismaPublicacionYUnoPuedeSalir() throws Exception {
        UUID id = UUID.randomUUID();
        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(any())).thenReturn("{\"tipo\":\"AVANCE\"}");
        EjecucionVista vista = mock(EjecucionVista.class);
        when(vista.id()).thenReturn(id);
        EventoVista avance = new EventoVista("AVANCE", vista);
        WebSocketSession primero = mock(WebSocketSession.class);
        WebSocketSession segundo = mock(WebSocketSession.class);
        when(primero.isOpen()).thenReturn(true);
        when(segundo.isOpen()).thenReturn(true);
        EventosWebSocket eventos = new EventosWebSocket(json);

        eventos.registrar(id, primero);
        eventos.registrar(id, segundo);
        eventos.publicar(avance);
        ArgumentCaptor<TextMessage> mensajePrimero = ArgumentCaptor.forClass(TextMessage.class);
        ArgumentCaptor<TextMessage> mensajeSegundo = ArgumentCaptor.forClass(TextMessage.class);
        verify(primero).sendMessage(mensajePrimero.capture());
        verify(segundo).sendMessage(mensajeSegundo.capture());
        assertEquals(mensajePrimero.getValue().getPayload(), mensajeSegundo.getValue().getPayload());

        eventos.quitar(id, primero);
        eventos.publicar(avance);
        verify(primero, times(1)).sendMessage(any(TextMessage.class));
        verify(segundo, times(2)).sendMessage(any(TextMessage.class));
    }
}
