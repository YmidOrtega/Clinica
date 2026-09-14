package com.ClinicaDeYmid.patient_service.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Component
class OutboxPurger {

    private static final Logger log = LoggerFactory.getLogger(OutboxPurger.class);

    private static final String DELETE_EXPIRED = "DELETE FROM " + JdbcPatientEventOutbox.TABLE
            + " WHERE created_at < ? ORDER BY created_at LIMIT ?";

    private final JdbcTemplate jdbc;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxPurger(JdbcTemplate jdbc, OutboxProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${clinica.patient.outbox.purge-interval:PT1H}")
    int purgeExpired() {
        Timestamp threshold = Timestamp.from(Instant.now(clock).minus(properties.retention()));
        int purged = 0;
        int batch;
        do {
            batch = jdbc.update(DELETE_EXPIRED, threshold, properties.purgeBatchSize());
            purged += batch;
        } while (batch == properties.purgeBatchSize());
        if (purged > 0) {
            log.info("Purged {} outbox events older than {}", purged, properties.retention());
        }
        return purged;
    }
}
