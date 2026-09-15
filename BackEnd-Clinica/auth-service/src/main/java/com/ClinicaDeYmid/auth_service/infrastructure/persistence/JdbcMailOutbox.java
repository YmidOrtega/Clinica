package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.application.mail.MailKind;
import com.ClinicaDeYmid.auth_service.application.mail.MailOutbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcMailOutbox implements MailOutbox {

    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbc;

    JdbcMailOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(MailKind kind, UUID userUuid, Instant now) {
        jdbc.update("""
                INSERT INTO auth_sessions.mail_outbox (id, kind, user_uuid, status, attempts, next_attempt_at, created_at)
                VALUES (?, ?, ?, 'PENDING', 0, ?, ?)""", UUID.randomUUID().toString(), kind.name(), userUuid.toString(), Timestamp.from(now),
                Timestamp.from(now));
    }

    @Override
    public boolean hasPending(MailKind kind, UUID userUuid) {
        Boolean pending = jdbc.queryForObject("""
                SELECT COUNT(*) > 0 FROM auth_sessions.mail_outbox WHERE user_uuid = ? AND kind = ? AND status = 'PENDING'""",
                Boolean.class, userUuid.toString(), kind.name());
        return Boolean.TRUE.equals(pending);
    }

    @Override
    public List<PendingMail> claimDue(Instant now, int limit, Duration lease) {
        List<PendingMail> due = jdbc.query("""
                SELECT id, kind, user_uuid, attempts FROM auth_sessions.mail_outbox
                WHERE status = 'PENDING' AND next_attempt_at <= ?
                ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED""",
                (row, index) -> new PendingMail(UUID.fromString(row.getString("id")), MailKind.valueOf(row.getString("kind")),
                        UUID.fromString(row.getString("user_uuid")), row.getInt("attempts")), Timestamp.from(now), limit);
        due.forEach(mail -> jdbc.update("UPDATE auth_sessions.mail_outbox SET next_attempt_at = ? WHERE id = ?",
                Timestamp.from(now.plus(lease)), mail.id().toString()));
        return due;
    }

    @Override
    public void markSent(UUID id, Instant at) {
        jdbc.update("UPDATE auth_sessions.mail_outbox SET status = 'SENT', sent_at = ?, last_error = NULL WHERE id = ?", Timestamp.from(at),
                id.toString());
    }

    @Override
    public void retryLater(UUID id, String error, Instant nextAttemptAt) {
        jdbc.update("UPDATE auth_sessions.mail_outbox SET attempts = attempts + 1, last_error = ?, next_attempt_at = ? WHERE id = ?",
                truncate(error), Timestamp.from(nextAttemptAt), id.toString());
    }

    @Override
    public void markFailed(UUID id, String error) {
        jdbc.update("UPDATE auth_sessions.mail_outbox SET status = 'FAILED', attempts = attempts + 1, last_error = ? WHERE id = ?",
                truncate(error), id.toString());
    }

    private static String truncate(String error) {
        return error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
