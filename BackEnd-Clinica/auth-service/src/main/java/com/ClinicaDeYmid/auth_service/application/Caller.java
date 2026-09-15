package com.ClinicaDeYmid.auth_service.application;

import com.ClinicaDeYmid.auth_service.domain.user.Actor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Caller(Actor actor, Instant authenticatedAt, boolean multiFactor) {

    public static final Duration RECENT_AUTHENTICATION = Duration.ofMinutes(5);

    public Caller {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
    }

    public UUID uuid() {
        return actor.uuid();
    }

    public void requireRecentAuthentication(Clock clock) {
        if (!multiFactor || authenticatedAt.plus(RECENT_AUTHENTICATION).isBefore(Instant.now(clock))) {
            throw new StepUpRequired(RECENT_AUTHENTICATION);
        }
    }
}
