package com.enruta.tracking.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TrackingWebSocketHandler handler;

    public WebSocketConfig(TrackingWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // En demo el mapa se abre por un tunel (https://*.trycloudflare.com) y desde el movil en
        // la LAN, asi que no se puede fijar el host. El handshake sigue exigiendo JWT valido.
        registry.addHandler(handler, "/ws/tracking").setAllowedOriginPatterns("*");
    }
}
