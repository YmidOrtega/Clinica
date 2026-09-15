package com.ClinicaDeYmid.api_gateway.domain.ratelimit;

import java.time.Duration;

public sealed interface RateLimitDecision {

    record Allowed(int limit, long remaining, Duration reset) implements RateLimitDecision {
    }

    record Limited(int limit, Duration retryAfter) implements RateLimitDecision {
    }

    record Unknown() implements RateLimitDecision {
    }
}
