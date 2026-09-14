package com.ClinicaDeYmid.auth_service.domain.throttle;

import java.time.Duration;

public sealed interface LoginAttemptDecision {

    record Allowed() implements LoginAttemptDecision {
    }

    record Delayed(Duration retryAfter) implements LoginAttemptDecision {
    }

    record Locked() implements LoginAttemptDecision {
    }
}
