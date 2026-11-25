package com.enruta.tracking.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.enruta.tracking.web.LocationBatchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class GpsProducerTest {

    @Mock private KafkaTemplate<String, String> kafka;

    @Test
    void everyPointBecomesOneKafkaMessage() {
        UUID driverId = UUID.randomUUID();
        GpsProducer producer = new GpsProducer(kafka, new ObjectMapper().findAndRegisterModules());

        producer.publish(
                driverId,
                List.of(
                        new LocationBatchRequest.Point(4.34, -74.36),
                        new LocationBatchRequest.Point(4.35, -74.37)));

        verify(kafka, times(2)).send(eq("tracking.gps.raw"), eq(driverId.toString()), anyString());
    }

    @Test
    void anEmptyBatchSendsNothing() {
        GpsProducer producer = new GpsProducer(kafka, new ObjectMapper().findAndRegisterModules());

        producer.publish(UUID.randomUUID(), List.of());

        verify(kafka, org.mockito.Mockito.never()).send(anyString(), anyString(), anyString());
    }
}
