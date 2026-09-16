package com.ClinicaDeYmid.contracting_service.infrastructure.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Component
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxPurger {

    private static final Logger log = LoggerFactory.getLogger(OutboxPurger.class);
    private static final String DELETE = "DELETE FROM " + JdbcContractingOutbox.TABLE + " WHERE created_at < ? LIMIT ?";

    private final JdbcTemplate jdbc;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxPurger(JdbcTemplate jdbc, OutboxProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${clinica.contracting.outbox.purge-cron:0 20 3 * * *}")
    void purge() {
        Instant limit = Instant.now(clock).minus(properties.retention());
        int removed;
        int total = 0;
        do {
            removed = jdbc.update(DELETE, Timestamp.from(limit), properties.purgeBatchSize());
            total += removed;
        } while (removed == properties.purgeBatchSize());
        if (total > 0) {
            log.info("Purged {} published contracting events older than {}", total, limit);
        }
    }
}
