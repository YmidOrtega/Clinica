package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
class SessionDataPurger {

    private static final Logger log = LoggerFactory.getLogger(SessionDataPurger.class);
    private static final Duration GRACE = Duration.ofDays(1);
    private static final Duration KEEP_SENT_MAIL = Duration.ofDays(7);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    SessionDataPurger(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "${clinica.auth.session-purge-interval:PT15M}")
    int purgeExpired() {
        Instant now = Instant.now(clock);
        Timestamp threshold = Timestamp.from(now.minus(GRACE));
        int purged = jdbc.update("""
                DELETE FROM auth_sessions.authorizations
                WHERE updated_at < ?
                  AND NOT EXISTS (SELECT 1 FROM auth_sessions.authorization_tokens t
                                  WHERE t.authorization_id = authorizations.id AND (t.expires_at IS NULL OR t.expires_at > ?))""",
                threshold, Timestamp.from(now));
        purged += jdbc.update("DELETE FROM auth_sessions.rotated_refresh_tokens WHERE rotated_at < ?", Timestamp.from(now.minus(Duration.ofDays(2))));
        purged += jdbc.update("DELETE FROM auth_sessions.one_time_tokens WHERE expires_at < ?", threshold);
        purged += jdbc.update("DELETE FROM auth_sessions.mail_outbox WHERE status = 'SENT' AND sent_at < ?", Timestamp.from(now.minus(KEEP_SENT_MAIL)));
        if (purged > 0) {
            log.info("Purged {} expired authorizations, rotated refresh tokens, links and sent mails", purged);
        }
        return purged;
    }
}
