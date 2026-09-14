package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.domain.throttle.FailureCount;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottle;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

@Repository
class JdbcLoginThrottle implements LoginThrottle {

    static final String TABLE = "auth_sessions.login_throttles";

    private final JdbcTemplate jdbc;

    JdbcLoginThrottle(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FailureCount failuresOf(ThrottleKey key) {
        return jdbc.query("SELECT consecutive_failures, last_failure_at FROM " + TABLE + " WHERE key_hash = ?",
                        (row, index) -> new FailureCount(row.getInt(1), row.getTimestamp(2).toInstant()), hash(key))
                .stream().findFirst().orElse(FailureCount.NONE);
    }

    @Override
    public FailureCount recordFailure(ThrottleKey key, Instant at) {
        jdbc.update("""
                INSERT INTO %s (key_hash, consecutive_failures, last_failure_at) VALUES (?, 1, ?)
                ON DUPLICATE KEY UPDATE consecutive_failures = consecutive_failures + 1, last_failure_at = VALUES(last_failure_at)"""
                .formatted(TABLE), hash(key), Timestamp.from(at));
        return failuresOf(key);
    }

    @Override
    public void clear(ThrottleKey key) {
        jdbc.update("DELETE FROM " + TABLE + " WHERE key_hash = ?", hash(key));
    }

    static String hash(ThrottleKey key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.identity().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
