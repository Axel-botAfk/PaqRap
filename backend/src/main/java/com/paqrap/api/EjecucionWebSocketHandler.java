package com.paqrap.api;

import com.paqrap.api.ApiModels.EventoVista;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Component
final class EjecucionWebSocketHandler extends TextWebSocketHandler {
    private final EjecucionesService ejecuciones;
    private final EventosWebSocket eventos;

    EjecucionWebSocketHandler(EjecucionesService ejecuciones, EventosWebSocket eventos) {
        this.ejecuciones = ejecuciones;
        this.eventos = eventos;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) throws Exception {
        UUID id = idDe(sesion);
        if (id == null) {
            sesion.close(CloseStatus.BAD_DATA);
            return;
        }
        try {
            var vista = ejecuciones.consultar(id);
            eventos.registrar(id, sesion);
            eventos.enviar(sesion, new EventoVista("INSTANTANEA", vista));
        } catch (ApiException e) {
            sesion.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje)
            throws Exception {
        sesion.close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        UUID id = idDe(sesion);
        if (id != null) eventos.quitar(id, sesion);
    }

    private static UUID idDe(WebSocketSession sesion) {
        if (sesion.getUri() == null) return null;
        String ruta = sesion.getUri().getPath();
        if (ruta == null || !ruta.startsWith("/ws/ejecuciones/")) return null;
        try {
            return UUID.fromString(ruta.substring("/ws/ejecuciones/".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
