package com.ClinicaDeYmid.auth_service.domain.onetime;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokens {

    String issue(UUID userUuid, OneTimeTokenPurpose purpose, Instant now);

    Optional<UUID> consume(String token, OneTimeTokenPurpose purpose, Instant now);
}
