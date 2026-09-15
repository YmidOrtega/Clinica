package com.ClinicaDeYmid.api_gateway.domain.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPolicyTest {

    private final RateLimitPolicy policy = new RateLimitPolicy("user", 3, Duration.ofMinutes(1));

    @Test
    void allowsUpToTheLimitAndThenAsksToWaitForTheWindow() {
        assertThat(policy.decide(3, Duration.ofSeconds(20))).isEqualTo(new RateLimitDecision.Allowed(3, 0, Duration.ofSeconds(20)));
        assertThat(policy.decide(4, Duration.ofSeconds(20))).isEqualTo(new RateLimitDecision.Limited(3, Duration.ofSeconds(20)));
        assertThat(policy.decide(9, Duration.ZERO)).isEqualTo(new RateLimitDecision.Limited(3, Duration.ofMinutes(1)));
    }

    @Test
    void rejectsLimitsThatCannotWork() {
        assertThatThrownBy(() -> new RateLimitPolicy("user", 0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitPolicy("user", 1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
