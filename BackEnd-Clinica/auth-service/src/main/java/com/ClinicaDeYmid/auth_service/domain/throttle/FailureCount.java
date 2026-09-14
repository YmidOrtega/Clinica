package com.ClinicaDeYmid.auth_service.domain.throttle;

import java.time.Instant;

public record FailureCount(int consecutiveFailures, Instant lastFailureAt) {

    public static final FailureCount NONE = new FailureCount(0, null);

    public FailureCount {
        if (consecutiveFailures < 0 || (consecutiveFailures > 0) != (lastFailureAt != null)) {
            throw new IllegalArgumentException("A failure count needs the time of its last failure");
        }
    }
}
