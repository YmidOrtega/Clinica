package com.ClinicaDeYmid.auth_service.application.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffSessions {

    record StaffSession(String id, String clientId, Instant startedAt, Instant lastRefreshedAt, Instant expiresAt) {
    }

    int revokeAll(UUID userUuid);

    List<StaffSession> of(UUID userUuid);

    boolean revoke(UUID userUuid, String sessionId);

    Optional<String> sessionOfAccessToken(String accessToken);
}
