package com.enruta.tracking.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.tracking.service.TrackingBroadcaster;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class TrackingWebSocketHandlerTest {

    @Mock private JwtDecoder jwtDecoder;
    @Mock private TrackingBroadcaster broadcaster;
    @Mock private StringRedisTemplate redis;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private WebSocketSession session;

    private TrackingWebSocketHandler handler;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        handler = new TrackingWebSocketHandler(jwtDecoder, broadcaster, redis);
        attributes = new HashMap<>();
        org.mockito.Mockito.lenient().when(session.getAttributes()).thenReturn(attributes);
    }

    @Test
    void aValidTokenSubscribesAndSendsTheSnapshot() throws Exception {
        when(session.getUri()).thenReturn(URI.create("ws://x/ws/tracking?shipmentId=s1&token=t"));
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-1");
        when(jwtDecoder.decode("t")).thenReturn(jwt);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("shipment:s1", "driverId")).thenReturn(null);

        handler.afterConnectionEstablished(session);

        verify(broadcaster).subscribe("s1", session);
        verify(session).sendMessage(any(TextMessage.class));
        assertThat(attributes).containsEntry("shipmentId", "s1").containsEntry("subject", "user-1");
    }

    @Test
    void aMissingTokenClosesTheConnection() throws Exception {
        when(session.getUri()).thenReturn(URI.create("ws://x/ws/tracking?shipmentId=s1"));

        handler.afterConnectionEstablished(session);

        verify(session).close(any(CloseStatus.class));
        verify(broadcaster, never()).subscribe(anyString(), any());
    }

    @Test
    void anInvalidTokenClosesTheConnection() throws Exception {
        when(session.getUri())
                .thenReturn(URI.create("ws://x/ws/tracking?shipmentId=s1&token=malo"));
        when(jwtDecoder.decode("malo")).thenThrow(new IllegalArgumentException("firma invalida"));

        handler.afterConnectionEstablished(session);

        verify(session).close(any(CloseStatus.class));
        verify(broadcaster, never()).subscribe(anyString(), any());
    }

    @Test
    void closingTheSocketUnsubscribes() {
        attributes.put("shipmentId", "s1");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(broadcaster).unsubscribe("s1", session);
        verify(broadcaster).removeSession(session);
    }

    @Test
    void queryParamsAreParsed() {
        Map<String, String> params =
                TrackingWebSocketHandler.queryParams(
                        URI.create("ws://x/ws/tracking?shipmentId=s1&token=abc.def.ghi"));

        assertThat(params).containsEntry("shipmentId", "s1").containsEntry("token", "abc.def.ghi");
        assertThat(TrackingWebSocketHandler.queryParams(URI.create("ws://x/ws/tracking")))
                .isEmpty();
        assertThat(TrackingWebSocketHandler.queryParams(null)).isEmpty();
    }
}
