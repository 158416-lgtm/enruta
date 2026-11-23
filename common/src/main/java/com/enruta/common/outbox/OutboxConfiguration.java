package com.enruta.common.outbox;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa el relay del outbox solo donde se pide ({@code enruta.outbox.enabled=true}).
 *
 * <p>ShedLock evita que dos replicas del mismo servicio publiquen el mismo evento: solo la que
 * consigue el lock ejecuta el tick.
 */
@Configuration
@ConditionalOnProperty(name = "enruta.outbox.enabled", havingValue = "true")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class OutboxConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("shedlock")
                        .build());
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxAppender outboxAppender(
            OutboxRepository repository, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new OutboxAppender(repository, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(
            OutboxRepository repository,
            org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxRelay(repository, kafkaTemplate);
    }
}
