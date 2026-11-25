package com.enruta.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxConfigurationTest {

    private final OutboxConfiguration configuration = new OutboxConfiguration();

    @Test
    void exposesTheDatabaseLockProvider() {
        LockProvider provider = configuration.lockProvider(mock(DataSource.class));

        assertThat(provider).isNotNull();
    }

    @Test
    void wiresTheAppenderAndTheRelay() {
        OutboxRepository repository = mock(OutboxRepository.class);

        assertThat(configuration.outboxAppender(repository, new ObjectMapper())).isNotNull();
        assertThat(configuration.outboxRelay(repository, mock(KafkaTemplate.class))).isNotNull();
    }
}
