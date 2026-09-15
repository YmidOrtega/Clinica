package com.ClinicaDeYmid.api_gateway.domain.ratelimit;

public interface RateLimiter {

    RateLimitDecision acquire(RateLimitPolicy policy, String subject);
}
