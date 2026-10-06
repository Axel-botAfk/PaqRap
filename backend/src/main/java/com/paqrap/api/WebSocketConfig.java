package com.paqrap.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {
    private final EjecucionWebSocketHandler handler;

    WebSocketConfig(EjecucionWebSocketHandler handler) { this.handler = handler; }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Sin CORS comodin: el navegador debe conectarse desde el mismo origen.
        registry.addHandler(handler, "/ws/ejecuciones/*");
    }
}
