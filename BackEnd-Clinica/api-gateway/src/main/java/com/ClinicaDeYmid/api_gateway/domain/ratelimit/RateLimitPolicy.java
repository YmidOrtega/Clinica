package com.ClinicaDeYmid.api_gateway.domain.ratelimit;

import java.time.Duration;
import java.util.Objects;

public record RateLimitPolicy(String name, int limit, Duration window) {

    public RateLimitPolicy {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(window, "window");
        if (limit < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("A rate limit needs a positive limit and window");
        }
    }

    public RateLimitDecision decide(long requestsInWindow, Duration untilWindowEnds) {
        if (requestsInWindow <= limit) {
            return new RateLimitDecision.Allowed(limit, limit - requestsInWindow, untilWindowEnds);
        }
        return new RateLimitDecision.Limited(limit, untilWindowEnds.isNegative() || untilWindowEnds.isZero() ? window : untilWindowEnds);
    }
}
