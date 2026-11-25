package com.enruta.tracking.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.enruta.tracking.service.PositionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GpsConsumerTest {

    @Mock private PositionService positionService;

    @Test
    void aValidPingIsProcessed() {
        GpsConsumer consumer =
                new GpsConsumer(positionService, new ObjectMapper().findAndRegisterModules());
        String ping =
                """
        {"driverId":"%s","lat":4.34,"lng":-74.36,"recordedAt":"2026-11-25T10:00:00Z"}
        """
                        .formatted(UUID.randomUUID());

        consumer.onPing(ping);

        verify(positionService).handle(any());
    }

    @Test
    void aBrokenPingIsDiscardedWithoutThrowing() {
        GpsConsumer consumer =
                new GpsConsumer(positionService, new ObjectMapper().findAndRegisterModules());

        consumer.onPing("no-es-json");
        consumer.onPing("{\"driverId\":\"no-uuid\"}");
    }
}
