package com.ClinicaDeYmid.auth_service.application.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MailOutbox {

    void enqueue(MailKind kind, UUID userUuid, Instant now);

    boolean hasPending(MailKind kind, UUID userUuid);

    List<PendingMail> claimDue(Instant now, int limit, Duration lease);

    void markSent(UUID id, Instant at);

    void retryLater(UUID id, String error, Instant nextAttemptAt);

    void markFailed(UUID id, String error);

    record PendingMail(UUID id, MailKind kind, UUID userUuid, int attempts) {
    }
}
