package com.ClinicaDeYmid.auth_service.domain.secondfactor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RecoveryCodes {

    int CODES_PER_USER = 10;

    List<String> replaceAll(UUID userUuid, Instant now);

    boolean use(UUID userUuid, String code, Instant now);

    int remaining(UUID userUuid);

    void revokeAll(UUID userUuid, Instant now);
}
