package com.ClinicaDeYmid.auth_service.domain.throttle;

import java.time.Instant;

public interface LoginThrottle {

    FailureCount failuresOf(ThrottleKey key);

    FailureCount recordFailure(ThrottleKey key, Instant at);

    void clear(ThrottleKey key);
}
