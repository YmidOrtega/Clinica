package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Component
class LoginThrottlePurger {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottlePurger.class);

    private static final String DELETE_STALE = "DELETE FROM " + JdbcLoginThrottle.TABLE
            + " WHERE last_failure_at < ? AND consecutive_failures < ? ORDER BY last_failure_at LIMIT ?";

    private final JdbcTemplate jdbc;
    private final LoginThrottleProperties properties;
    private final Clock clock;

    LoginThrottlePurger(JdbcTemplate jdbc, LoginThrottleProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "${clinica.auth.login-throttle.purge-interval:PT1H}")
    int purgeStale() {
        Timestamp threshold = Timestamp.from(Instant.now(clock).minus(properties.retention()));
        int purged = 0;
        int batch;
        do {
            batch = jdbc.update(DELETE_STALE, threshold, properties.accountLockThreshold(), properties.purgeBatchSize());
            purged += batch;
        } while (batch == properties.purgeBatchSize());
        if (purged > 0) {
            log.info("Purged {} stale login throttle counters", purged);
        }
        return purged;
    }
}
