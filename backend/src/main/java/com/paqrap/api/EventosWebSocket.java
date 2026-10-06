package com.paqrap.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paqrap.api.ApiModels.EventoVista;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Difunde copias JSON; un cliente lento no bloquea la simulacion. */
@Component
final class EventosWebSocket {
    private final ObjectMapper json;
    private final ConcurrentMap<UUID, Set<WebSocketSession>> suscriptores =
            new ConcurrentHashMap<>();

    EventosWebSocket(ObjectMapper json) { this.json = json; }

    void registrar(UUID id, WebSocketSession sesion) {
        suscriptores.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(sesion);
    }

    void quitar(UUID id, WebSocketSession sesion) {
        Set<WebSocketSession> sesiones = suscriptores.get(id);
        if (sesiones != null) {
            sesiones.remove(sesion);
            if (sesiones.isEmpty()) suscriptores.remove(id, sesiones);
        }
    }

    void publicar(EventoVista evento) {
        Set<WebSocketSession> sesiones = suscriptores.get(evento.ejecucion().id());
        if (sesiones == null || sesiones.isEmpty()) return;
        String texto;
        try {
            texto = json.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            return;
        }
        for (WebSocketSession sesion : sesiones) {
            if (!enviarTexto(sesion, texto)) quitar(evento.ejecucion().id(), sesion);
        }
    }

    void enviar(WebSocketSession sesion, EventoVista evento) throws IOException {
        enviarTexto(sesion, json.writeValueAsString(evento));
    }

    private boolean enviarTexto(WebSocketSession sesion, String texto) {
        if (!sesion.isOpen()) return false;
        try {
            synchronized (sesion) {
                sesion.sendMessage(new TextMessage(texto));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }
}
