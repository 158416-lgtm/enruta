package com.enruta.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ProcessedEventStoreTest {

    @Mock private JdbcTemplate jdbc;

    @Test
    void firstTimeReturnsTrue() {
        UUID eventId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        boolean isNew = new ProcessedEventStore(jdbc).markIfNew(eventId, "dispatch");

        assertThat(isNew).isTrue();

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("ON CONFLICT DO NOTHING"), args.capture());
        assertThat(args.getValue()).containsExactly(eventId, "dispatch");
    }

    @Test
    void duplicateReturnsFalse() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        boolean isNew = new ProcessedEventStore(jdbc).markIfNew(UUID.randomUUID(), "shipment");

        assertThat(isNew).isFalse();
    }
}
