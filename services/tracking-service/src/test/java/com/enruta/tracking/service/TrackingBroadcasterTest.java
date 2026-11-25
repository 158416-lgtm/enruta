package com.enruta.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class TrackingBroadcasterTest {

    @Mock private WebSocketSession session;

    private TrackingBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new TrackingBroadcaster(new ObjectMapper().findAndRegisterModules());
        org.mockito.Mockito.lenient().when(session.isOpen()).thenReturn(true);
    }

    @Test
    void subscribersAreCountedPerShipment() {
        broadcaster.subscribe("s1", session);
        broadcaster.subscribe("s1", session);
        broadcaster.subscribe("s2", session);

        assertThat(broadcaster.subscribers("s1")).isEqualTo(1);
        assertThat(broadcaster.subscribers("s2")).isEqualTo(1);
        assertThat(broadcaster.subscribers("s3")).isZero();
    }

    @Test
    void aPositionIsSentToTheSubscribers() throws Exception {
        broadcaster.subscribe("s1", session);

        broadcaster.position(
                "s1", new PositionService.Position(UUID.randomUUID(), 4.34, -74.36, Instant.now()));

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void aStatusMessageIsSent() throws Exception {
        broadcaster.subscribe("s1", session);

        broadcaster.status("s1", "DELIVERED");

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void unsubscribingRemovesTheSession() {
        broadcaster.subscribe("s1", session);

        broadcaster.unsubscribe("s1", session);

        assertThat(broadcaster.subscribers("s1")).isZero();
    }

    @Test
    void nobodySubscribedMeansNoMessages() {
        broadcaster.position(
                "s1", new PositionService.Position(UUID.randomUUID(), 1, 1, Instant.now()));

        assertThat(broadcaster.subscribers("s1")).isZero();
    }

    @Test
    void closedSessionsReceiveNothing() throws Exception {
        org.mockito.Mockito.when(session.isOpen()).thenReturn(false);
        broadcaster.subscribe("s1", session);

        broadcaster.status("s1", "DELIVERED");

        verify(session, org.mockito.Mockito.never()).sendMessage(any(TextMessage.class));
        assertThat(broadcaster.subscribers("s1")).isEqualTo(1);
    }

    @Test
    void aFailedSendDropsTheSession() throws Exception {
        org.mockito.Mockito.doThrow(new java.io.IOException("roto"))
                .when(session)
                .sendMessage(any(TextMessage.class));
        broadcaster.subscribe("s1", session);

        broadcaster.status("s1", "DELIVERED");

        assertThat(broadcaster.subscribers("s1")).isZero();
    }

    @Test
    void aSerializationFailureIsSwallowed() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper brokenMapper =
                org.mockito.Mockito.mock(com.fasterxml.jackson.databind.ObjectMapper.class);
        org.mockito.Mockito.when(brokenMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("roto") {});
        TrackingBroadcaster broken = new TrackingBroadcaster(brokenMapper);
        broken.subscribe("s1", session);

        broken.status("s1", "DELIVERED");

        verify(session, org.mockito.Mockito.never()).sendMessage(any(TextMessage.class));
        assertThat(broken.subscribers("s1")).isEqualTo(1);
    }

    @Test
    void removingASessionDropsItFromEveryShipment() {
        broadcaster.subscribe("s1", session);
        broadcaster.subscribe("s2", session);

        broadcaster.removeSession(session);

        assertThat(broadcaster.subscribers("s1")).isZero();
        assertThat(broadcaster.subscribers("s2")).isZero();
    }

    @Test
    void unsubscribingAnUnknownShipmentIsANoOp() {
        broadcaster.unsubscribe("desconocido", session);

        assertThat(broadcaster.subscribers("desconocido")).isZero();
    }
}
