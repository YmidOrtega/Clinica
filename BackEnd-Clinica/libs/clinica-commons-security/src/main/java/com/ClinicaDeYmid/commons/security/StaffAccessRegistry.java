package com.ClinicaDeYmid.commons.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffAccessRegistry {

    static final String ACTIVE = "ACTIVE";

    public record StaffAccess(long version, String status, Instant tokensNotBefore) {
    }

    private final Map<UUID, StaffAccess> access = new ConcurrentHashMap<>();
    private volatile boolean caughtUp;

    public void record(UUID userUuid, StaffAccess latest) {
        access.merge(userUuid, latest, (known, candidate) -> candidate.version() >= known.version() ? candidate : known);
    }

    public boolean revoked(UUID userUuid, Instant issuedAt) {
        StaffAccess known = access.get(userUuid);
        if (known == null) {
            return false;
        }
        return !ACTIVE.equals(known.status()) || known.tokensNotBefore().truncatedTo(ChronoUnit.SECONDS).isAfter(issuedAt);
    }

    public Optional<StaffAccess> of(UUID userUuid) {
        return Optional.ofNullable(access.get(userUuid));
    }

    public int size() {
        return access.size();
    }

    public boolean caughtUp() {
        return caughtUp;
    }

    void markCaughtUp() {
        caughtUp = true;
    }
}
