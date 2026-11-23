package com.enruta.dispatch.config;

import com.enruta.common.messaging.ProcessedEventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ConsumerConfig {

    @Bean
    public ProcessedEventStore processedEventStore(JdbcTemplate jdbcTemplate) {
        return new ProcessedEventStore(jdbcTemplate);
    }
}
