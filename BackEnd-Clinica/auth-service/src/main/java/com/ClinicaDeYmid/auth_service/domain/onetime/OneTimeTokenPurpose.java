package com.ClinicaDeYmid.auth_service.domain.onetime;

import java.time.Duration;

public enum OneTimeTokenPurpose {

    ACTIVATION(Duration.ofHours(72)),
    PASSWORD_RESET(Duration.ofMinutes(30));

    private final Duration lifetime;

    OneTimeTokenPurpose(Duration lifetime) {
        this.lifetime = lifetime;
    }

    public Duration lifetime() {
        return lifetime;
    }
}
